// How close the heaviest real writes come to Firestore's limit of 1,000 evaluated rule expressions per request.
// For each write it finds the spare budget: the most always-true terms that can be added to the first rule the
// write meets before the emulator refuses it (a binary search on freshly loaded rules). A write that is refused
// with nothing added shows "over".
// Usage, with a demo Firestore emulator running (firebase.rules-test.json, port 8180):
//   node tools/rulestest/budget.mjs [rules file]     (BUDGET_ONLY=<part of a name> runs only matching writes)
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc, writeBatch, increment, arrayUnion, serverTimestamp, Timestamp } from "firebase/firestore";
import { readFileSync } from "node:fs";

const host = new URL(`http://${process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8180"}`);
if (!["localhost", "127.0.0.1"].includes(host.hostname)) throw new Error("Loopback emulator required");
const projectId = "demo-catmap-budget";
const rules = readFileSync(process.argv[2] || new URL("../../firestore.rules", import.meta.url), "utf8");
const now = () => Date.now();
const ctx = (env, uid) => env.authenticatedContext(uid, { email: `${uid}@x.test`, email_verified: true }).firestore();
const profile = (username, extra = {}) => ({ username, displayName: username, bio: "", photoId: "", role: "user", volunteerStatus: "none",
  stats: {}, points: 0, neighborhoods: [], badges: [], followedCats: [], showStatsPublicly: true, lastPostAt: null, createdAt: now(), ...extra });
const catDoc = (uid, extra = {}) => ({ counterSourceId: "", name: "Zafran", aliases: [], description: "", distinctiveMarks: "", coatColor: "orange",
  pattern: "tabby", size: "medium", sex: "unknown", breedHints: ["arabian_mau"], photoIds: ["img1"], coverPhotoId: "img1", createdBy: uid,
  createdByName: uid, contributors: [uid], followers: [uid], firstSeen: now(), lastSeen: now(), sightingCount: 1, lat: 25.27, lng: 55.30,
  neighborhood: "deira", neighborhoods: ["deira"], tnrStatus: "unknown", tnrVerified: false, healthStatus: "unknown", features: [0.1, 0.2],
  mergedInto: "", hidden: false, createdAt: now(), updatedAt: now(), ...extra });
const sightingDoc = (uid, catId, extra = {}) => ({ catId, catName: "Zafran", userId: uid, username: uid, photoIds: ["img1"], seenAt: now(),
  lat: 25.27, lng: 55.30, neighborhood: "deira", locationSource: "gps", coatColor: "orange", pattern: "tabby", size: "medium", healthTags: [],
  behaviorTags: [], earTipped: "unsure", possiblyNotNeutered: false, needsMedical: false, notes: "", newCat: false, matchScore: 0, aiSummary: "",
  suggestedCatIds: ["c9"], rejectedCatIds: [], status: "pending", createdAt: now(), updatedAt: now(), ...extra });
const postDoc = (uid, extra = {}) => ({ ownerId: uid, ownerName: uid, ownerPhotoId: "", animalType: "dog", category: "lost", status: "open",
  name: "Rex", description: "Brown dog with a red collar", color: "brown", breed: "saluki", features: "red collar", sex: "male", ageGroup: "adult",
  photoIds: ["img1"], videoPath: "", lat: 25.08, lng: 55.14, approximate: false, areaRadiusM: 500, neighborhood: "dubai_marina",
  lastSeenAt: now(), reward: 500, contactMethod: "in_app", contactPhone: "", contactEmail: "", adoptionStatus: "available", health: "",
  vaccinated: false, neutered: false, updatesCount: 0, hidden: false, keywords: ["rex", "brown"], createdAt: now(), updatedAt: now(),
  resolvedAt: 0, ...extra });
const maxPost = (uid) => postDoc(uid, { name: "N".repeat(60), description: "D".repeat(2000), color: "c".repeat(60), breed: "b".repeat(60),
  features: "f".repeat(400), contactPhone: "1".repeat(30), contactEmail: "e".repeat(70) + "@x.test", health: "h".repeat(400),
  photoIds: ["1", "2", "3", "4", "5", "6"], keywords: Array.from({ length: 80 }, (_, i) => "word" + i), fingerprint: Array(27).fill(0.5),
  lastActivityAt: now(), microchip: "unknown", resolution: "", resolvedMatchId: "", geoCells: ["thrq", "thrq5", "thrq5x"] });
const stats = (b, db, uid, s, points, hood) => {
  const u = { points: increment(points), lastPostAt: serverTimestamp() };
  for (const [k, v] of Object.entries(s)) u[`stats.${k}`] = increment(v);
  if (hood) u.neighborhoods = arrayUnion(hood);
  b.update(doc(db, "users", uid), u);
};
const old = () => Timestamp.fromMillis(now() - 3600000);
async function seedUsers(db, ...uids) {
  for (const uid of uids) {
    await setDoc(doc(db, "users", uid), profile(uid, { lastPostAt: old() }));
    await setDoc(doc(db, "usernames", uid), { uid });
  }
}

const scenarios = {
  "sighting on someone else's cat (test suite)": {
    seed: async (db) => { await seedUsers(db, "alice", "bob"); await setDoc(doc(db, "cats", "c1"), catDoc("alice")); },
    write: (db) => { const b = writeBatch(db);
      b.update(doc(db, "cats", "c1"), { counterSourceId: "s3", sightingCount: increment(1), contributors: arrayUnion("bob"), neighborhoods: arrayUnion("naif"),
        updatedAt: now(), tnrStatus: "ear_tipped", healthStatus: "healthy", photoIds: arrayUnion("img2"), lastSeen: now(), lat: 25.28, lng: 55.31,
        neighborhood: "naif", features: [0.2, 0.2] });
      b.set(doc(db, "sightings", "s3"), sightingDoc("bob", "c1"));
      stats(b, db, "bob", { sightings: 1 }, 10, "naif"); return b.commit(); },
    as: "bob",
  },
  "heaviest sighting on someone else's cat": {
    seed: async (db) => { await seedUsers(db, "h_vic", "h_heavy");
      await setDoc(doc(db, "cats", "hc1"), catDoc("h_vic", { photoIds: ["v1"], coverPhotoId: "v1", sightingCount: 4, features: Array(117).fill(0.5) })); },
    write: (db) => { const b = writeBatch(db);
      b.set(doc(db, "sightings", "hs1"), sightingDoc("h_heavy", "hc1", { photoIds: ["p1", "p2", "p3", "p4", "p5", "p6"], notes: "n".repeat(500),
        healthTags: ["thin", "limping"], behaviorTags: ["friendly"] }));
      b.update(doc(db, "cats", "hc1"), { counterSourceId: "hs1", sightingCount: increment(1), contributors: arrayUnion("h_heavy"),
        neighborhoods: arrayUnion("jlt"), updatedAt: now(), tnrStatus: "ear_tipped", healthStatus: "needs_care",
        photoIds: arrayUnion("p1", "p2", "p3", "p4", "p5", "p6"), lastSeen: now(), lat: 25.07, lng: 55.14, neighborhood: "jlt",
        features: Array(117).fill(0.4), geoCells: ["thrq", "thrq5", "thrq5x"] });
      stats(b, db, "h_heavy", { sightings: 1, photos: 6, tnrReports: 1, nightSightings: 1, souqSightings: 1, marinaSightings: 1 }, 22, "jlt");
      return b.commit(); },
    as: "h_heavy",
  },
  "station check-in": {
    seed: async (db) => { await seedUsers(db, "alice");
      await setDoc(doc(db, "stations", "st1"), { name: "Station", description: "", operatorName: "", schedule: "", foodType: "", hasWater: true,
        lat: 25.1, lng: 55.2, neighborhood: "al_barsha", verified: true, active: true, sample: false, checkinCount: 0, lastCheckinAt: 0,
        lastCheckinBy: "", createdAt: now(), updatedAt: now() }); },
    write: (db) => { const b = writeBatch(db);
      b.set(doc(db, "checkins", "ck1"), { stationId: "st1", stationName: "Station", userId: "alice", username: "alice", catsSeen: 3, catIds: [],
        foodLevel: "low", waterLevel: "full", note: "", at: now() });
      b.update(doc(db, "stations", "st1"), { counterSourceId: "ck1", checkinCount: increment(1), lastCheckinAt: now(), lastCheckinBy: "alice" });
      stats(b, db, "alice", { checkins: 1, iftarCheckins: 0 }, 5, "al_barsha"); return b.commit(); },
    as: "alice",
  },
  "station check-in by a long-time member": {
    seed: async (db) => {
      await seedUsers(db, "alice");
      // Every counter in use, many areas, badges and followed cats: the most a profile carries.
      const stats = Object.fromEntries(["sightings", "catsRegistered", "approvedSightings", "photos", "rescueReports", "checkins",
        "maintenanceReports", "mergeSuggestions", "tnrReports", "nightSightings", "iftarCheckins", "souqSightings", "marinaSightings",
        "posts", "reunited", "postUpdates"].map((k, i) => [k, 40 + i]));
      await updateDoc(doc(db, "users", "alice"), { stats, points: 4000, badges: ["first_whiskers", "abra_rider", "ehsan_hero"],
        neighborhoods: Array.from({ length: 60 }, (_, i) => "area" + i), followedCats: Array.from({ length: 40 }, (_, i) => "cat" + i) });
      await setDoc(doc(db, "stations", "st1"), { name: "Station", description: "", operatorName: "", schedule: "", foodType: "", hasWater: true,
        lat: 25.1, lng: 55.2, neighborhood: "al_barsha", verified: true, active: true, sample: false, checkinCount: 7, lastCheckinAt: 0,
        lastCheckinBy: "", createdAt: now(), updatedAt: now() });
    },
    write: (db) => { const b = writeBatch(db);
      b.set(doc(db, "checkins", "ck1"), { stationId: "st1", stationName: "Station", userId: "alice", username: "alice", catsSeen: 3, catIds: [],
        foodLevel: "low", waterLevel: "full", note: "", at: now() });
      b.update(doc(db, "stations", "st1"), { counterSourceId: "ck1", checkinCount: increment(1), lastCheckinAt: now(), lastCheckinBy: "alice" });
      stats(b, db, "alice", { checkins: 1, iftarCheckins: 1 }, 5, "al_barsha"); return b.commit(); },
    as: "alice",
  },
  "largest post, created": {
    seed: async (db) => seedUsers(db, "h_big"),
    write: (db) => { const b = writeBatch(db); b.set(doc(db, "posts", "hp_big"), maxPost("h_big"));
      stats(b, db, "h_big", { posts: 1 }, 10, "dubai_marina"); return b.commit(); },
    as: "h_big",
  },
  "largest post, fingerprint edit": {
    seed: async (db) => { await seedUsers(db, "h_big"); await setDoc(doc(db, "posts", "hp_big"), maxPost("h_big")); },
    write: (db) => updateDoc(doc(db, "posts", "hp_big"), { fingerprint: Array(27).fill(0.25), description: "E".repeat(2000), updatedAt: now() }),
    as: "h_big",
  },
  "largest post, fingerprint, text and place edit": {
    seed: async (db) => { await seedUsers(db, "h_big"); await setDoc(doc(db, "posts", "hp_big"), maxPost("h_big")); },
    write: (db) => updateDoc(doc(db, "posts", "hp_big"), { fingerprint: Array(27).fill(0.3), description: "F".repeat(2000), lat: 25.2, lng: 55.27,
      neighborhood: "downtown", geoCells: ["thrr", "thrr3", "thrr3s"], keywords: Array.from({ length: 80 }, (_, i) => "w" + i), updatedAt: now() }),
    as: "h_big",
  },
  "ordinary post, created": {
    seed: async (db) => seedUsers(db, "carol"),
    write: (db) => { const b = writeBatch(db); b.set(doc(db, "posts", "p1"), postDoc("carol"));
      stats(b, db, "carol", { posts: 1 }, 10, "dubai_marina"); return b.commit(); },
    as: "carol",
  },
};

// Where the padding goes: the start of the first rule each kind of document meets. `budgetPad()` is always true, so
// it changes only the number of expressions evaluated.
const PADS = {
  users: [/\(signedIn\(\) && uid\(\) == userId && selfProfileUpdateOk\(\)\)/, "(budgetPad() && signedIn() && uid() == userId && selfProfileUpdateOk())"],
  cats: [/allow update: if catUpdateOk\(/, "allow update: if budgetPad() && catUpdateOk("],
  sightings: [/(match \/sightings\/\{sightingId\} \{\s*allow read: if member\(\);\s*allow create: if )verified\(\)/, "$1budgetPad() && verified()"],
  stations: [/allow update: if stationCheckinStep\(/, "allow update: if budgetPad() && stationCheckinStep("],
  checkins: [/(match \/checkins\/\{checkinId\} \{\s*allow read: if member\(\);\s*allow create: if )verified\(\)/, "$1budgetPad() && verified()"],
  postCreate: [/allow create: if newPostOk\(/, "allow create: if budgetPad() && newPostOk("],
  postUpdate: [/allow update: if postUpdateOk\(/, "allow update: if budgetPad() && postUpdateOk("],
};
for (const [s, pad] of [["sighting on someone else's cat (test suite)", "cats"], ["heaviest sighting on someone else's cat", "cats"],
  ["station check-in", "stations"], ["station check-in by a long-time member", "stations"], ["largest post, created", "postCreate"], ["largest post, fingerprint edit", "postUpdate"],
  ["largest post, fingerprint, text and place edit", "postUpdate"], ["ordinary post, created", "postCreate"]]) scenarios[s].pad = pad;

function padded(k, where) {
  const [pattern, replacement] = PADS[where];
  if (!pattern.test(rules)) throw new Error(`padding point for ${where} not found`);
  // Long `&&` chains don't compile ("too complex"), so the terms form a shallow tree: leaves of up to 16 terms,
  // and functions that call up to 8 others.
  const lines = [];
  let level = [];
  for (let left = Math.max(1, k); left > 0; left -= 16) {
    const name = `budgetLeaf${level.length}`;
    lines.push(`    function ${name}() { return ${Array(Math.min(16, left)).fill("true").join(" && ")}; }\n`);
    level.push(name);
  }
  for (let depth = 0; level.length > 1; depth++) {
    const next = [];
    for (let i = 0; i < level.length; i += 8) {
      const name = `budgetNode${depth}_${next.length}`;
      lines.push(`    function ${name}() { return ${level.slice(i, i + 8).map((f) => f + "()").join(" && ")}; }\n`);
      next.push(name);
    }
    level = next;
  }
  const pad = lines.join("") + `    function budgetPad() { return ${level[0]}(); }\n`;
  const result = rules.replace(pattern, replacement).replace(/( *\/\/ ---------- helpers ----------)/, pad + "$1");
  if (!result.includes("function budgetPad()")) throw new Error("padding function not inserted");
  return result;
}

async function allowed(s, rulesText) {
  const env = await initializeTestEnvironment({ projectId, firestore: { host: host.hostname, port: Number(host.port), rules: rulesText } });
  try {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled((c) => s.seed(c.firestore()));
    await s.write(ctx(env, s.as));
    return true;
  } catch (e) {
    if (!/PERMISSION_DENIED/.test(e.message)) throw e;
    return /maximum of 1000 expressions/.test(e.message) ? false : e.message.replace(/\s+/g, " ").slice(0, 200);
  } finally {
    await env.cleanup();
  }
}

const only = process.env.BUDGET_ONLY;
const where = process.env.BUDGET_PAD;
for (const [name, s] of Object.entries(scenarios)) {
  if (only && !name.includes(only)) continue;
  const target = where || s.pad;
  const base = await allowed(s, padded(1, target));
  if (base !== true) {
    console.log(`  over  ${name}${base === false ? "" : " (refused for another reason: " + base + ")"}`);
    continue;
  }
  let lo = 1, hi = 1024;
  while (hi - lo > 4) {
    const mid = Math.floor((lo + hi) / 2);
    if ((await allowed(s, padded(mid, target))) === true) lo = mid; else hi = mid;
  }
  console.log(`${String(lo).padStart(6)}  ${name} (spare, padded at ${target})`);
}
process.exit(0);
