// Firestore read baseline (Phase 1 of docs/done/MAJOR_UPDATE_PLAN.md).
//
// Fills a throw-away emulator database with synthetic CatMap data at two sizes, then runs the exact queries the
// Android app runs (same collection, filters, order and limit as app/src/main/java/com/catmap/dubai/data/*) as a
// signed-in, verified user under the real firestore.rules. Firestore bills one read per document a query returns
// (and one read for a query that returns nothing), so the documents returned here are the reads a phone would be
// billed for a fresh, uncached run. The script also measures each document's stored size with Firestore's
// documented size formula, which approximates the download per read.
//
// Run (uses the rules-test emulator config and its own project id; never the app's demo data or production):
//   npm --prefix tools/rulestest ci
//   npx.cmd -y firebase-tools@latest emulators:exec --config firebase.rules-test.json --only firestore ^
//     --project demo-catmap-cost "node tools/cost/baseline.mjs"
//
// Output: a table on the console and docs/cost/baseline-results.json.
import { createRequire } from "node:module";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..", "..");
const require = createRequire(join(root, "tools", "rulestest", "package.json"));
const { initializeTestEnvironment } = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, getDocs, collection, query, where, orderBy, limit, limitToLast, writeBatch, Bytes, documentId,
} = require("firebase/firestore");

const host = new URL(`http://${process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8180"}`);
const env = await initializeTestEnvironment({
  projectId: "demo-catmap-cost",
  firestore: {
    host: host.hostname.replace(/^\[|\]$/g, ""),
    port: Number(host.port),
    rules: readFileSync(join(root, "firestore.rules"), "utf8"),
  },
});

// ---------- Firestore storage size (https://firebase.google.com/docs/firestore/storage-size) ----------
const strSize = (s) => Buffer.byteLength(s, "utf8") + 1;
function valueSize(v) {
  if (v === null || v === undefined) return 1;
  if (typeof v === "boolean") return 1;
  if (typeof v === "number") return 8;
  if (typeof v === "string") return strSize(v);
  if (v instanceof Date) return 8;
  if (v && typeof v.toUint8Array === "function") return v.toUint8Array().length;
  if (Array.isArray(v)) return v.reduce((s, x) => s + valueSize(x), 0);
  if (typeof v === "object") return Object.entries(v).reduce((s, [k, x]) => s + strSize(k) + valueSize(x), 0);
  return 8;
}
function docSize(path, data) {
  const name = path.split("/").reduce((s, part) => s + strSize(part), 16);
  return name + valueSize(data) + 32;
}

// ---------- map areas (Phase 4): the same geohash and plan as data/Geohash.kt and data/MapArea.kt ----------
const B32 = "0123456789bcdefghjkmnpqrstuvwxyz";
function geohash(lat, lng, precision) {
  let la = [-90, 90], ln = [-180, 180], out = "", even = true, bit = 0, ch = 0;
  while (out.length < precision) {
    const r = even ? ln : la, v = even ? lng : lat, mid = (r[0] + r[1]) / 2;
    if (v >= mid) { ch = ch * 2 + 1; r[0] = mid; } else { ch *= 2; r[1] = mid; }
    even = !even;
    if (++bit === 5) { out += B32[ch]; bit = 0; ch = 0; }
  }
  return out;
}
const geoCells = (lat, lng) => { const g = geohash(lat, lng, 6); return [g.slice(0, 4), g.slice(0, 5), g]; };
function cellSize(p) { const bits = p * 5; return [180 / 2 ** Math.floor(bits / 2), 360 / 2 ** Math.ceil(bits / 2)]; }
function covering(s, w, n, e, p, max) {
  const [h, cw] = cellSize(p), out = new Set();
  for (let lat = s; ; lat = Math.min(lat + h, n)) {
    for (let lng = w; ; lng = Math.min(lng + cw, e)) { out.add(geohash(lat, lng, p)); if (out.size > max) return [...out]; if (lng >= e) break; }
    if (lat >= n) break;
  }
  return [...out];
}
function plan(s, w, n, e) {
  for (const p of [6, 5, 4]) { const cells = covering(s, w, n, e, p, 6); if (cells.length <= 6) return { cells, perCell: Math.min(150, Math.max(40, Math.floor(300 / cells.length))) }; }
  return { cells: covering(s, w, n, e, 4, 64).slice(0, 6), perCell: 50 };
}
// Search words (Phase 5): the same rule as data/SearchTerms.kt.
function searchTerms(words) {
  const out = new Set(words);
  words.forEach((w, i) => {
    if (i < 25 || i >= words.length - 6) for (let k = 3; k <= Math.min(w.length - 1, 7); k++) out.add(w.slice(0, k));
  });
  return [...out].slice(0, 200);
}
function around(lat, lng, km) {
  const dLat = km / 111, dLng = km / (111 * Math.cos((lat * Math.PI) / 180));
  for (const p of [6, 5, 4]) { const c = covering(lat - dLat, lng - dLng, lat + dLat, lng + dLng, p, 6); if (c.length <= 6) return c; }
  return covering(lat - dLat, lng - dLng, lat + dLat, lng + dLng, 4, 64);
}

// What a ~400 x 800 dp phone screen shows (MapLibre, 512-px tiles) around a point at a zoom level.
function screen(lat, lng, zoom) {
  const w = (360 * 400) / (512 * 2 ** zoom), h = w * 2 * Math.cos((lat * Math.PI) / 180);
  return [lat - h / 2, lng - w / 2, lat + h / 2, lng + w / 2];
}

// ---------- synthetic data shaped like the app's models ----------
const now = Date.now();
const DAY = 86_400_000;
const areas = ["al_barsha", "jlt", "dubai_marina", "deira", "al_quoz", "jumeirah", "business_bay", "mirdif"];
const pick = (arr, i) => arr[i % arr.length];
const words = "orange tabby friendly shy collar white paws green eyes seen near the park every evening".split(" ");
const text = (n, i) => Array.from({ length: n }, (_, k) => words[(i + k * 7) % words.length]).join(" ");
const fingerprint = (i) => Array.from({ length: 27 }, (_, k) => ((i * 31 + k * 17) % 100) / 100);

const userDoc = (uid, i) => ({
  username: uid, displayName: `User ${i}`, bio: text(12, i), photoId: `img_u${i}`, role: "user",
  volunteerStatus: "none", stats: { posts: i % 9, sightings: i % 5, checkins: i % 3 }, points: (i * 13) % 400,
  neighborhoods: [pick(areas, i)], badges: ["first_whiskers"], followedCats: [], showStatsPublicly: true,
  suspended: false, lastPostAt: null, createdAt: now - (i % 300) * DAY,
});
const postDoc = (i, ownerId) => ({
  ownerId, ownerName: ownerId, ownerPhotoId: "", animalType: pick(["cat", "cat", "cat", "dog", "bird"], i),
  category: pick(["street", "lost", "found", "spotted", "needs_help", "adoption", "street", "other"], i),
  status: "open", name: i % 3 ? `Pet ${i}` : "", description: text(35, i), color: "orange", breed: "",
  features: text(6, i), sex: "unknown", ageGroup: "adult", photoIds: [`img_p${i}a`, `img_p${i}b`], videoPath: "",
  lat: 25.05 + (i % 97) / 1000, lng: 55.15 + (i % 89) / 1000, approximate: i % 4 === 0, areaRadiusM: 300,
  neighborhood: pick(areas, i), lastSeenAt: now - (i % 60) * DAY, reward: 0, contactMethod: "chat",
  contactPhone: "", contactEmail: "", adoptionStatus: "available", health: "", vaccinated: "unknown",
  neutered: "unknown", updatesCount: i % 4, hidden: false, keywords: [...new Set(text(8, i).split(" "))],
  searchTerms: searchTerms([...new Set(text(8, i).split(" "))]),
  createdAt: now - i * 3_600_000, updatedAt: now - i * 3_600_000, resolvedAt: 0, lastActivityAt: now - i * 3_600_000,
  fingerprint: fingerprint(i), microchip: "unknown", resolution: "", resolvedMatchId: "",
  geoCells: geoCells(25.05 + (i % 97) / 1000, 55.15 + (i % 89) / 1000),
});
const catDoc = (i) => ({
  name: i % 2 ? `Cat ${i}` : "", aliases: [], description: text(25, i), distinctiveMarks: text(5, i),
  coatColor: "orange", pattern: "tabby", size: "medium", sex: "unknown", breedHints: ["tabby"],
  photoIds: [`img_c${i}a`, `img_c${i}b`, `img_c${i}c`], coverPhotoId: `img_c${i}a`, createdBy: `u${i % 300}`,
  createdByName: `u${i % 300}`, contributors: [`u${i % 300}`, `u${(i + 7) % 300}`], followers: [`u${(i + 3) % 300}`],
  firstSeen: now - (i % 400) * DAY, lastSeen: now - (i % 90) * DAY, sightingCount: 1 + (i % 12),
  lat: 25.05 + (i % 101) / 1000, lng: 55.15 + (i % 83) / 1000, neighborhood: pick(areas, i),
  neighborhoods: [pick(areas, i)], tnrStatus: "unknown", tnrVerified: false, healthStatus: "healthy",
  features: fingerprint(i), hidden: false, mergedInto: "", createdAt: now - (i % 400) * DAY, updatedAt: now - (i % 90) * DAY,
  geoCells: geoCells(25.05 + (i % 101) / 1000, 55.15 + (i % 83) / 1000),
});
const notifDoc = (i) => ({
  type: pick(["lost_nearby", "message", "post_update", "match_found"], i), fromUid: `u${i % 300}`,
  params: { name: `Pet ${i}`, area: pick(areas, i), type: "cat", category: "lost" }, route: `post/p${i}`,
  read: i > 5, createdAt: now - i * 1_800_000,
});
const chatDoc = (me, other, i) => ({
  members: [me, other].sort(), names: { [me]: me, [other]: other }, photos: { [me]: "", [other]: "" },
  lastText: text(8, i), lastSender: other, lastAt: now - i * 3_600_000, unread: { [me]: i % 3, [other]: 0 },
});
const messageDoc = (sender, i) => ({ senderId: sender, text: text(10, i), createdAt: now - i * 60_000 });

const SIZES = {
  // Roughly the size the app has today (a few test users and demo posts).
  today: { users: 30, posts: 60, cats: 40, myPosts: 3, notifications: 25, chats: 4, messages: 40 },
  // A grown community: every list cap in the app is reached.
  grown: { users: 300, posts: 1200, cats: 1500, myPosts: 20, notifications: 150, chats: 30, messages: 400 },
};

async function seed(size) {
  await env.clearFirestore();
  const docs = [];
  const me = "me";
  docs.push([`users/${me}`, userDoc(me, 0)]);
  for (let i = 1; i < size.users; i++) docs.push([`users/u${i}`, userDoc(`u${i}`, i)]);
  for (let i = 0; i < size.posts; i++) docs.push([`posts/p${i}`, postDoc(i, i < size.myPosts ? me : `u${1 + (i % (size.users - 1))}`)]);
  for (let i = 0; i < size.cats; i++) docs.push([`cats/c${i}`, catDoc(i)]);
  for (let i = 0; i < size.notifications; i++) docs.push([`users/${me}/notifications/n${i}`, notifDoc(i)]);
  for (let i = 0; i < size.chats; i++) {
    const other = `u${1 + i}`;
    const id = [me, other].sort().join("_");
    docs.push([`chats/${id}`, chatDoc(me, other, i)]);
    if (i === 0) for (let m = 0; m < size.messages; m++) docs.push([`chats/${id}/messages/m${m}`, messageDoc(m % 2 ? me : other, m)]);
  }
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (let i = 0; i < docs.length; i += 400) {
      const b = writeBatch(db);
      for (const [path, data] of docs.slice(i, i + 400)) b.set(doc(db, path), data);
      await b.commit();
    }
  });
  return docs.length;
}

// ---------- the app's queries ----------
// Each step names the Android code it copies.
const steps = (db, me) => ({
  profile: { where: "Session.profile -> UserRepository.profileFlow", run: () => getDoc(doc(db, `users/${me}`)) },
  stalePosts: { where: "AppNavHost -> PostRepository.remindStalePosts (every launch)", run: () => getDocs(query(collection(db, "posts"), where("ownerId", "==", me))) },
  chatsBadge: { where: "AppNavHost unread badge -> ChatRepository.chatsFor (no limit)", run: () => getDocs(query(collection(db, "chats"), where("members", "array-contains", me))) },
  inbox: { where: "NotificationBell + CatMapApp -> NotificationRepository.notifications (limit 100)", run: () => getDocs(query(collection(db, `users/${me}/notifications`), orderBy("createdAt", "desc"), limit(100))) },
  feed: { where: "MapScreen/Explore -> PostRepository.feed (limit 400)", run: () => getDocs(query(collection(db, "posts"), where("hidden", "==", false), orderBy("createdAt", "desc"), limit(400))) },
  cats: { where: "MapScreen/Cats/Search -> CatRepository.allCats (limit 800)", run: () => getDocs(query(collection(db, "cats"), orderBy("lastSeen", "desc"), limit(800))) },
  workerPoll: { where: "Before Phase 3: NotificationWorker every 15 min -> NotificationRepository.latest (limit 100)", run: () => getDocs(query(collection(db, `users/${me}/notifications`), orderBy("createdAt", "desc"), limit(100))) },
  // Phase 3: only alerts newer than the previous run (15 minutes ago) with an hour of overlap.
  workerPollAfter: { where: "After Phase 3: NotificationRepository.since (last run - 1 h, limit 50)", run: () => getDocs(query(collection(db, `users/${me}/notifications`), where("createdAt", ">", Date.now() - 75 * 60_000), orderBy("createdAt", "desc"), limit(50))) },
  // Phase 4 (data/MapRepository.kt): the cells on screen plus the two "changed since" listeners.
  mapColdOpen: { where: "After Phase 4: first map open, nothing saved (zoom 11.2 over the busy area)", run: () => mapOpen(db, screen(25.10, 55.195, 11.2), now - 3_600_000) },
  mapReopenNoChanges: { where: "After Phase 4: map opened again, saved copy, nothing changed", run: () => changesSince(db, Date.now() + 3_600_000) },
  mapReopen5Changes: { where: "After Phase 4: map opened again an hour later, 5 posts changed meanwhile", run: async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const b = writeBatch(ctx.firestore());
      for (let i = 500; i < 505; i++) b.update(doc(ctx.firestore(), `posts/p${i % 60}`), { updatedAt: Date.now() });
      await b.commit();
    });
    // The last sync was an hour ago; the listener looks one more hour back for slow clocks.
    return changesSince(db, Date.now() - 3_600_000);
  } },
  mapZoomIn: { where: "After Phase 4: zoom to street level (16) inside the busy area: finer cells", run: () => mapOpen(db, screen(25.10, 55.195, 16), null) },
  // Phase 5: pages instead of whole lists, search on Firestore, nearby instead of the newest 400.
  exploreFirstPage: { where: "After Phase 5: Explore, first page (PostRepository.feedQuery, 25)", run: () => getDocs(query(collection(db, "posts"), where("hidden", "==", false), orderBy("createdAt", "desc"), limit(25))) },
  searchFirstPage: { where: "After Phase 5: search 'tabby' (searchTerms, 25) + cats (20)", run: async () => [
    await getDocs(query(collection(db, "posts"), where("hidden", "==", false), where("searchTerms", "array-contains-any", ["tabby"]), orderBy("createdAt", "desc"), limit(25))),
    await getDocs(query(collection(db, "cats"), where("searchTerms", "array-contains-any", ["tabby"]), orderBy("lastSeen", "desc"), limit(20))),
  ] },
  catsFirstPage: { where: "After Phase 5: Cats directory, first page (25)", run: () => getDocs(query(collection(db, "cats"), orderBy("lastSeen", "desc"), limit(25))) },
  favorites10: { where: "After Phase 5: Favourites with 10 saved posts (read one by one)", run: async () => ({ size: 10, docs: await Promise.all(Array.from({ length: 10 }, (_, i) => getDoc(doc(db, `posts/p${i * 7}`)))) }) },
  postFormChecks: { where: "After Phase 5: new street post, duplicate (3 km) + same-animal (1 km) checks", run: async () => {
    const out = [];
    for (const km of [3, 1]) for (const cell of around(25.10, 55.195, km)) out.push(await getDocs(query(collection(db, "posts"), where("hidden", "==", false), where("geoCells", "array-contains", cell), orderBy("updatedAt", "desc"), limit(30))));
    return out;
  } },
  deviceMatch: { where: "After Phase 5: phone's backup matching for a sighting (newest 200 lost reports)", run: () => getDocs(query(collection(db, "posts"), where("hidden", "==", false), where("category", "in", ["lost"]), orderBy("createdAt", "desc"), limit(200))) },
  inboxAfter: { where: "After Phase 5: inbox / bell (newest 30 live)", run: () => getDocs(query(collection(db, `users/${me}/notifications`), orderBy("createdAt", "desc"), limit(30))) },
  openChatAfter: { where: "After Phase 5: open a chat (newest 50 live)", run: async () => {
    const id = ["me", "u1"].sort().join("_");
    return getDocs(query(collection(db, `chats/${id}/messages`), orderBy("createdAt", "asc"), limitToLast(50)));
  } },
  openChat: { where: "ChatScreen -> ChatRepository.messages (limitToLast 300)", run: async () => {
    const chats = await getDocs(query(collection(db, "chats"), where("members", "array-contains", me)));
    const first = chats.docs.sort((a, b) => b.data().lastAt - a.data().lastAt).find((d) => d.id === ["me", "u1"].sort().join("_"));
    return getDocs(query(collection(db, `chats/${first.id}/messages`), orderBy("createdAt", "asc"), limitToLast(300)));
  } },
  openPost: { where: "PostDetailScreen -> postFlow + updates (limit 200)", run: async () => {
    const p = await getDoc(doc(db, "posts/p10"));
    const u = await getDocs(query(collection(db, "posts/p10/updates"), orderBy("createdAt", "desc"), limit(200)));
    // An empty updates list still bills one read.
    return { size: 1 + Math.max(1, u.size), docs: [p, ...u.docs] };
  } },
});

async function changesSince(db, lastSync) {
  const since = lastSync - 3_600_000;
  const p = await getDocs(query(collection(db, "posts"), where("hidden", "==", false), where("updatedAt", ">", since), orderBy("updatedAt", "desc"), limit(300)));
  const c = await getDocs(query(collection(db, "cats"), where("updatedAt", ">", since), orderBy("updatedAt", "desc"), limit(300)));
  return [p, c];
}
async function mapOpen(db, box, lastSync) {
  const { cells, perCell } = plan(...box);
  const snaps = lastSync === null ? [] : await changesSince(db, lastSync + 3_600_000);
  for (const cell of cells) {
    snaps.push(await getDocs(query(collection(db, "posts"), where("hidden", "==", false), where("geoCells", "array-contains", cell), orderBy("updatedAt", "desc"), limit(perCell))));
    snaps.push(await getDocs(query(collection(db, "cats"), where("geoCells", "array-contains", cell), orderBy("updatedAt", "desc"), limit(perCell))));
  }
  return snaps;
}

function billed(snap) {
  if (Array.isArray(snap)) return snap.map(billed).reduce((a, b) => ({ reads: a.reads + b.reads, bytes: a.bytes + b.bytes }), { reads: 0, bytes: 0 });
  if (snap && typeof snap.exists === "function") return { reads: 1, bytes: snap.exists() ? docSize(snap.ref.path, snap.data()) : 0 };
  const list = snap.docs ?? [];
  const bytes = list.reduce((s, d) => s + (d.exists?.() === false ? 0 : docSize(d.ref.path, d.data())), 0);
  return { reads: Math.max(1, snap.size ?? list.length), bytes };
}

const results = {};
for (const [name, size] of Object.entries(SIZES)) {
  const seeded = await seed(size);
  const db = env.authenticatedContext("me", { email: "me@x.test", email_verified: true }).firestore();
  const s = steps(db, "me");
  const out = {};
  for (const [step, def] of Object.entries(s)) {
    const t0 = performance.now();
    const snap = await def.run();
    out[step] = { ...billed(snap), ms: Math.round(performance.now() - t0), where: def.where };
  }
  const openKeys = ["profile", "stalePosts", "chatsBadge", "inbox", "feed", "cats"];
  out.appOpenTotal = {
    reads: openKeys.reduce((sum, k) => sum + out[k].reads, 0),
    bytes: openKeys.reduce((sum, k) => sum + out[k].bytes, 0),
  };
  // Phase 3 moved the stale-post check to every 12 h; Phase 4 replaced the feed and cats with the map's area loading.
  const afterKeys = ["profile", "chatsBadge", "inbox", "mapColdOpen"];
  const after5Keys = ["profile", "chatsBadge", "inboxAfter", "mapColdOpen"];
  out.appOpenTotalAfterPhase5 = {
    reads: after5Keys.reduce((sum, k) => sum + out[k].reads, 0),
    bytes: after5Keys.reduce((sum, k) => sum + out[k].bytes, 0),
  };
  out.appOpenTotalAfterPhase4 = {
    reads: afterKeys.reduce((sum, k) => sum + out[k].reads, 0),
    bytes: afterKeys.reduce((sum, k) => sum + out[k].bytes, 0),
  };
  out.backgroundPerDay = { reads: out.workerPoll.reads * 96, bytes: out.workerPoll.bytes * 96 };
  out.backgroundPerDayAfter = { reads: out.workerPollAfter.reads * 96, bytes: out.workerPollAfter.bytes * 96 };
  results[name] = { dataset: size, documentsSeeded: seeded, steps: out };
}

// ---------- report ----------
const kb = (b) => (b / 1024).toFixed(1) + " KB";
const rows = Object.keys(results.today.steps);
console.log("\nStep".padEnd(20), "| today reads | today data | grown reads | grown data");
for (const r of rows) {
  const a = results.today.steps[r], b = results.grown.steps[r];
  console.log(r.padEnd(19), "|", String(a.reads).padStart(11), "|", kb(a.bytes).padStart(10), "|", String(b.reads).padStart(11), "|", kb(b.bytes).padStart(10));
}
mkdirSync(join(root, "docs", "cost"), { recursive: true });
writeFileSync(join(root, "docs", "cost", "baseline-results.json"), JSON.stringify({ measuredAt: new Date().toISOString(), method: "emulator replay of the Android queries; reads = documents returned (min 1 per query); bytes = Firestore storage-size formula", results }, null, 2) + "\n");
await env.cleanup();
console.log("\nWrote docs/cost/baseline-results.json");
