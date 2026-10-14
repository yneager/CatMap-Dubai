// Puts CatMap Web live on Firebase Hosting (https://catmap-dubai.web.app). The owner runs it, through publish.cmd:
//   1. registers the "CatMap Web" app in the Firebase project, once (reuses it afterwards);
//   2. writes its public web config into web/.env.local (git-ignored), with emulator mode off;
//   3. installs packages if needed and builds the site;
//   4. deploys Hosting only. Rules, indexes and functions are not touched.
import { spawnSync } from 'node:child_process';
import {
  closeSync,
  existsSync,
  mkdtempSync,
  openSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROJECT = 'catmap-dubai';
const APP_NAME = 'CatMap Web';
const SITE = `https://${PROJECT}.web.app`;
const web = join(dirname(fileURLToPath(import.meta.url)), '..');
const root = join(web, '..');
const win = process.platform === 'win32';

function run(cmd, args, cwd = root) {
  const r = spawnSync(win ? `${cmd}.cmd` : cmd, args, { cwd, shell: win, stdio: 'inherit' });
  if (r.status !== 0) throw new Error(`${cmd} ${args.join(' ')} failed`);
}

/**
 * Runs a Firebase CLI command with --json and returns its `result`. On Windows, Node 24 can crash with
 * "Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)" while the CLI exits, after the work is done. So the
 * output goes to a file (not a pipe), and the CLI's own `"status": "success"` decides, not the exit code.
 */
function firebaseJson(args, { retry = true } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'catmap-publish-'));
  const file = join(dir, 'out.json');
  try {
    for (let attempt = 1; ; attempt++) {
      const fd = openSync(file, 'w');
      spawnSync(
        win ? 'npx.cmd' : 'npx',
        ['-y', 'firebase-tools@latest', ...args, '--project', PROJECT, '--json'],
        { cwd: root, shell: win, stdio: ['inherit', fd, 'inherit'] },
      );
      closeSync(fd);
      const text = readFileSync(file, 'utf8');
      let out = null;
      try {
        out = JSON.parse(text.slice(text.indexOf('{')));
      } catch {
        // No complete JSON: the CLI died before reporting.
      }
      if (out?.status === 'success') return out.result;
      if (!retry || attempt >= 3)
        throw new Error(
          `firebase ${args.join(' ')} failed: ${out?.error ?? text.trim().slice(-400)}`,
        );
      console.log(`(the Firebase tool stopped early; trying again, attempt ${attempt + 1} of 3)`);
    }
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const findApp = () =>
  (firebaseJson(['apps:list', 'WEB']) ?? []).find((a) => a.displayName === APP_NAME);

console.log('\n=== 1/4  Finding or registering the CatMap Web app in Firebase ===');
let app = findApp();
if (!app) {
  // Not retried blindly: a create that crashed after succeeding would otherwise make a second app.
  try {
    // With shell: true on Windows the arguments are joined into one command line, so the name needs quotes.
    app = firebaseJson(['apps:create', 'WEB', win ? `"${APP_NAME}"` : APP_NAME], { retry: false });
  } catch (e) {
    app = findApp();
    if (!app) throw e;
  }
}
console.log(`Using web app ${app.appId}`);

console.log('\n=== 2/4  Saving its public config to web/.env.local ===');
const sdk = firebaseJson(['apps:sdkconfig', 'WEB', app.appId]);
const cfg = sdk.sdkConfig ?? JSON.parse(sdk.fileContents.match(/\{[\s\S]*\}/)[0]);
const envFile = join(web, '.env.local');
const env = new Map();
if (existsSync(envFile))
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const i = line.indexOf('=');
    if (i > 0 && !line.startsWith('#')) env.set(line.slice(0, i).trim(), line.slice(i + 1).trim());
  }
for (const [k, v] of Object.entries({
  VITE_FIREBASE_API_KEY: cfg.apiKey,
  VITE_FIREBASE_AUTH_DOMAIN: cfg.authDomain,
  VITE_FIREBASE_PROJECT_ID: cfg.projectId,
  VITE_FIREBASE_APP_ID: cfg.appId,
  VITE_FIREBASE_MESSAGING_SENDER_ID: cfg.messagingSenderId,
  VITE_FIREBASE_STORAGE_BUCKET: cfg.storageBucket ?? '',
  VITE_USE_EMULATORS: 'false',
  VITE_BLAZE: 'false',
}))
  env.set(k, v ?? '');
if (!env.get('VITE_PUBLIC_URL')) env.set('VITE_PUBLIC_URL', SITE);
writeFileSync(envFile, [...env].map(([k, v]) => `${k}=${v}`).join('\n') + '\n');
console.log('Saved (public web config only; web/.env.local is git-ignored).');

console.log('\n=== 3/4  Building the website ===');
if (!existsSync(join(web, 'node_modules'))) run('npm', ['ci'], web);
// Test-suite overrides must not leak into the real build.
for (const k of Object.keys(process.env))
  if (/^VITE_(USE_EMULATORS|\w*EMULATOR\w*)$/.test(k)) delete process.env[k];
run('npm', ['run', 'build'], web);

console.log('\n=== 4/4  Uploading to Firebase Hosting (about a minute) ===');
// Re-uploading the same build is harmless, so this one may be retried.
firebaseJson(['deploy', '--only', 'hosting']);

console.log(`\nDone. CatMap Web is live at ${SITE}`);
console.log('On your iPhone: open that link in Safari, tap Share, then "Add to Home Screen".');
