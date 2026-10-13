// Creates the CatMap demo accounts in Firebase Authentication: "admin" plus four demo users
// (see DemoAccounts.kt). Run it yourself, once:
//
//   node tools/demo/create-demo-accounts.mjs            (asks for the password, then imports)
//
// All five accounts share the password you type here. It's never written to the repository: it's
// hashed (scrypt) into a temporary file, imported with `firebase auth:import`, and the file is deleted.
// The script then signs in once with the app's API key (from app/google-services.json) to prove the
// password works; if Firebase rejects that hash format it re-imports with the next one. The accounts are created already verified, with fixed ids, and carry a custom claim
// (catmapAdmin / catmapDemo). The security rules only let the account with `catmapAdmin` create a
// profile with the admin role, so the claim, not the password, is what makes "admin" an admin.
// Importing again (for example with a new password) updates the same five accounts.
//
// It also creates the AI worker account the PC's CatMap AI Server signs in with (claim catmapAiWorker: it may only
// read AI jobs and write their results). Its password is random, never shown, and saved only on this PC in
// worker.local.json in the AI server's folder: cat-server next to this project (Desktop\cat-server), or the folder
// in the CATMAP_SERVER_DIR environment variable.
//
// Options: --project <id> (default: catmap-dubai); --worker-only (only the AI worker account, no password to type).
// Needs `firebase login` (already done on this PC).

import { createHash, pbkdf2Sync, randomBytes, scryptSync } from "node:crypto";
import { existsSync, readFileSync, writeFileSync, rmSync, mkdtempSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { createInterface } from "node:readline";

const DOMAIN = "demo.catmap.test";
const ACCOUNTS = [
  { uid: "catmap-demo-admin", username: "admin", name: "CatMap Admin", claims: { catmapAdmin: true, catmapDemo: true } },
  { uid: "catmap-demo-ali", username: "demo_ali", name: "Ali", claims: { catmapDemo: true } },
  { uid: "catmap-demo-mohammad", username: "demo_mohammad", name: "Mohammad", claims: { catmapDemo: true } },
  { uid: "catmap-demo-anirudh", username: "demo_anirudh", name: "Anirudh", claims: { catmapDemo: true } },
  { uid: "catmap-demo-hassan", username: "demo_hassan", name: "Hassan", claims: { catmapDemo: true } },
];
const WORKER = { uid: "catmap-ai-worker", username: "ai-worker", name: "CatMap AI Server", claims: { catmapAiWorker: true } };
const serverDir = process.env.CATMAP_SERVER_DIR || resolve(fileURLToPath(new URL("../../../cat-server", import.meta.url)));
const workerFile = join(serverDir, "worker.local.json");
// Keep the worker's existing password on a re-run, so a running AI server keeps working.
const workerPassword = (() => {
  try { return JSON.parse(readFileSync(workerFile, "utf8")).password; } catch { return randomBytes(24).toString("base64url"); }
})();

const args = process.argv.slice(2);
const project = args.includes("--project") ? args[args.indexOf("--project") + 1] : "catmap-dubai";

// Asks every question on one readline, closed once at the end: opening a second readline on stdin after closing the
// first crashes Node on Windows ("Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)").
async function askHidden(questions) {
  const rl = createInterface({ input: process.stdin, output: process.stdout, terminal: true });
  let current = "";
  rl._writeToOutput = (s) => { if (s.startsWith(current)) rl.output.write(s); };
  const lines = [];
  let waiting = null;
  let closed = false;
  rl.on("line", (l) => { if (waiting) { const w = waiting; waiting = null; w(l); } else lines.push(l); });
  rl.on("close", () => { closed = true; if (waiting) waiting(""); });
  const answers = [];
  for (const q of questions) {
    current = q;
    rl.setPrompt(q);
    rl.prompt();
    answers.push(lines.length ? lines.shift() : closed ? "" : await new Promise((done) => { waiting = done; }));
    rl.output.write("\n");
  }
  rl.close();
  await new Promise((done) => setImmediate(done));
  return answers;
}

// --worker-only: just (re)create the AI worker account for the PC's server, with no password to type.
const workerOnly = args.includes("--worker-only");
let password = "";
if (!workerOnly) {
  const fromEnv = process.env.CATMAP_DEMO_PASSWORD;
  const [first, again] = fromEnv ? [fromEnv, fromEnv] : await askHidden(["Password for the admin and demo accounts: ", "Type it again: "]);
  if (first.length < 8) { console.error("Use at least 8 characters."); process.exit(1); }
  if (again !== first) { console.error("The two passwords don't match."); process.exit(1); }
  password = first;
}

// Hash formats Firebase can import, tried in order until a test sign-in succeeds.
const FORMATS = [
  { flags: ["--hash-algo=STANDARD_SCRYPT", "--mem-cost=16384", "--block-size=8", "--parallelization=1", "--dk-len=64"],
    hash: (pw, salt) => scryptSync(pw, salt, 64, { N: 16384, r: 8, p: 1 }) },
  { flags: ["--hash-algo=PBKDF2_SHA256", "--rounds=100000"], hash: (pw, salt) => pbkdf2Sync(pw, salt, 100000, 32, "sha256") },
  { flags: ["--hash-algo=SHA256", "--rounds=1", "--hash-input-order=SALT_FIRST"],
    hash: (pw, salt) => createHash("sha256").update(Buffer.concat([salt, Buffer.from(pw)])).digest() },
];

function apiKey() {
  const f = new URL("../../app/google-services.json", import.meta.url);
  if (!existsSync(f)) return null;
  try { return JSON.parse(readFileSync(f, "utf8")).client?.[0]?.api_key?.[0]?.current_key || null; } catch { return null; }
}

async function canSignIn(key) {
  const [who, pw] = workerOnly ? [WORKER, workerPassword] : [ACCOUNTS[0], password];
  const r = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${key}`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: `${who.username}@${DOMAIN}`, password: pw, returnSecureToken: false }),
  });
  if (r.ok) return true;
  const msg = (await r.json().catch(() => ({})))?.error?.message || String(r.status);
  if (/INVALID_PASSWORD|INVALID_LOGIN_CREDENTIALS/.test(msg)) return false;
  throw new Error(`test sign-in failed: ${msg}`);
}

function importWith(format) {
  const users = (workerOnly ? [WORKER] : [...ACCOUNTS, WORKER]).map((a) => {
    const salt = randomBytes(16);
    return {
      localId: a.uid,
      email: `${a.username}@${DOMAIN}`,
      emailVerified: true,
      displayName: a.name,
      passwordHash: format.hash(a === WORKER ? workerPassword : password, salt).toString("base64"),
      salt: salt.toString("base64"),
      customAttributes: JSON.stringify(a.claims),
    };
  });
  const dir = mkdtempSync(join(tmpdir(), "catmap-demo-"));
  const file = join(dir, "users.json");
  try {
    writeFileSync(file, JSON.stringify({ users }), { mode: 0o600 });
    const cli = ["-y", "firebase-tools@latest", "auth:import", file, ...format.flags, "--project", project];
    const npx = process.platform === "win32" ? "npx.cmd" : "npx";
    const r = spawnSync(npx, cli, { stdio: "inherit", shell: process.platform === "win32" });
    if (r.status !== 0) process.exit(r.status ?? 1);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const key = apiKey();
let ok = false;
for (const format of FORMATS) {
  importWith(format);
  if (!key) { console.log("\n(No app/google-services.json here, so the sign-in test was skipped.)"); ok = true; break; }
  if (await canSignIn(key)) { ok = true; break; }
  console.log("Firebase didn't accept that hash format; trying the next one...");
}
if (!ok) { console.error("None of the hash formats worked. Nothing else was changed; tell Claude."); process.exit(1); }
mkdirSync(serverDir, { recursive: true });
writeFileSync(workerFile, JSON.stringify({ email: `${WORKER.username}@${DOMAIN}`, password: workerPassword }, null, 2), { mode: 0o600 });
console.log(`Saved the AI worker sign-in for the PC in ${workerFile}.`);
if (workerOnly) {
  console.log("\nDone. Start the AI server with start.cmd in that folder.");
} else {
  console.log(`\nDone. Sign in to the app with username "admin" and the password you typed.`);
  console.log(`Then open Settings > Demo accounts to switch between the four demo users.`);
}
