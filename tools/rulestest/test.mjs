// Replays the app's Firestore writes/queries against the emulator to check firestore.rules.
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import {
  doc, setDoc, getDoc, getDocs, updateDoc, deleteDoc, writeBatch, collection, query, where, orderBy, limit,
  serverTimestamp, increment, arrayUnion, arrayRemove, Bytes, runTransaction, collectionGroup,
} from "firebase/firestore";
import { readFileSync } from "node:fs";

const emulatorAddress = new URL(`http://${process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080"}`);
const env = await initializeTestEnvironment({
  // This suite clears its database. Never use the app's saved demo-catmap project.
  projectId: "demo-catmap-rules-test",
  firestore: {
    host: emulatorAddress.hostname.replace(/^\[|\]$/g, ""),
    port: Number(process.env.FIRESTORE_TEST_PORT || emulatorAddress.port),
    rules: readFileSync(process.argv[2] || "../../firestore.rules", "utf8"),
  },
});
await env.clearFirestore();

let passed = 0, failed = 0;
async function check(name, promise) {
  try { await promise; passed++; console.log("  ok  ", name); }
  catch (e) { failed++; console.log("  FAIL", name, "-", e.message.split("\n")[0]); }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const now = () => Date.now();

const ctx = (uid, verified = true) => env.authenticatedContext(uid, { email: `${uid}@x.test`, email_verified: verified }).firestore();
const anon = env.unauthenticatedContext().firestore();

const profile = (username) => ({
  username, displayName: username, bio: "", photoId: "", role: "user", volunteerStatus: "none",
  stats: {}, points: 0, neighborhoods: [], badges: [], followedCats: [], showStatsPublicly: true,
  lastPostAt: null, createdAt: now(),
});

async function register(uid, verified = false) {
  const db = ctx(uid, verified);
  const b = writeBatch(db);
  b.set(doc(db, "usernames", uid), { uid });
  b.set(doc(db, "users", uid), profile(uid));
  b.set(doc(db, "users", uid, "private", "account"), { email: `${uid}@x.test`, createdAt: now() });
  await b.commit();
  await setDoc(doc(db, "users", uid, "notifications", "welcome"), { type: "welcome", params: { user: uid }, route: "", fromUid: uid, read: false, createdAt: now() });
}
const setRole = (uid, role) => env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), "users", uid), { role }));

const catDoc = (uid) => ({
  name: "Zafran", aliases: [], description: "", distinctiveMarks: "", coatColor: "orange", pattern: "tabby", size: "medium", sex: "unknown",
  breedHints: ["arabian_mau"], photoIds: ["img1"], coverPhotoId: "img1", createdBy: uid, createdByName: uid, contributors: [uid], followers: [uid],
  firstSeen: now(), lastSeen: now(), sightingCount: 1, lat: 25.27, lng: 55.30, neighborhood: "deira", neighborhoods: ["deira"],
  tnrStatus: "unknown", tnrVerified: false, healthStatus: "unknown", features: [0.1, 0.2], mergedInto: "", hidden: false, createdAt: now(), updatedAt: now(),
});
const sightingDoc = (uid, catId) => ({
  catId, catName: "Zafran", userId: uid, username: uid, photoIds: ["img1"], seenAt: now(), lat: 25.27, lng: 55.30, neighborhood: "deira",
  locationSource: "gps", coatColor: "orange", pattern: "tabby", size: "medium", healthTags: [], behaviorTags: [], earTipped: "unsure",
  possiblyNotNeutered: false, needsMedical: false, notes: "", newCat: true, matchScore: 0, aiSummary: "", suggestedCatIds: ["c9"], rejectedCatIds: [],
  status: "pending", createdAt: now(), updatedAt: now(),
});
const userStats = (b, db, uid, stats, points, hood, rate) => {
  const u = {};
  for (const [k, v] of Object.entries(stats)) u[`stats.${k}`] = increment(v);
  u.points = increment(points);
  if (hood) u.neighborhoods = arrayUnion(hood);
  if (rate) u.lastPostAt = serverTimestamp();
  b.update(doc(db, "users", uid), u);
};

console.log("Accounts");
await check("registration batch while unverified", register("alice"));
await check("second user registers", register("bob"));
await register("vol"); await register("mod"); await register("admin");
await setRole("vol", "volunteer"); await setRole("mod", "moderator"); await setRole("admin", "admin");
await check("username lookup before sign-in", assertSucceeds(getDoc(doc(anon, "usernames", "alice"))));
await check("username taken cannot be overwritten", assertFails(setDoc(doc(ctx("bob"), "usernames", "alice"), { uid: "bob" })));
await check("email is private to others", assertFails(getDoc(doc(ctx("bob"), "users", "alice", "private", "account"))));
await check("user cannot make themselves admin", assertFails(updateDoc(doc(ctx("alice"), "users", "alice"), { role: "admin" })));
await check("profile edit allowed", assertSucceeds(updateDoc(doc(ctx("alice"), "users", "alice"), { bio: "hi", displayName: "Alice" })));
{
  // The demo admin account (custom claim catmapAdmin) may create or promote its own profile; nobody else can.
  const demo = (uid, claims) => env.authenticatedContext(uid, { email: `${uid}@demo.catmap.test`, email_verified: true, ...claims }).firestore();
  const DA = demo("demoadmin", { catmapAdmin: true }), DU = demo("demouser", { catmapDemo: true });
  await check("user cannot create an admin profile", assertFails(setDoc(doc(DU, "users", "demouser"), { ...profile("demouser"), role: "admin" })));
  // Before its admin profile exists, the claim alone gives no power over other accounts.
  await check("demo admin claim cannot write others' role", assertFails(updateDoc(doc(DA, "users", "alice"), { role: "admin" })));
  await check("demo admin claim creates admin profile", assertSucceeds(setDoc(doc(DA, "users", "demoadmin"), { ...profile("demoadmin"), role: "admin" })));
  await check("demo user claim stays a user", assertSucceeds(setDoc(doc(DU, "users", "demouser"), profile("demouser"))));
  await check("demo user cannot promote itself", assertFails(updateDoc(doc(DU, "users", "demouser"), { role: "admin" })));
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), "users", "demoadmin"), { role: "user" }));
  await check("demo admin role change cannot ride along other fields", assertFails(updateDoc(doc(DA, "users", "demoadmin"), { role: "admin", points: 999 })));
  await check("demo admin claim restores its admin role", assertSucceeds(updateDoc(doc(DA, "users", "demoadmin"), { role: "admin" })));
}

console.log("AI server jobs");
{
  const W = env.authenticatedContext("worker", { email: "ai-worker@demo.catmap.test", email_verified: true, catmapAiWorker: true }).firestore();
  const job = (uid, extra = {}) => ({ uid, kind: "compare", status: "pending", query: Bytes.fromUint8Array(new Uint8Array(2000)),
    images: [Bytes.fromUint8Array(new Uint8Array(2000))], ids: ["p1"], labels: ["Simba"], requestId: "r1", createdAt: serverTimestamp(), ...extra });
  await check("member creates own AI job", assertSucceeds(setDoc(doc(ctx("alice"), "aiJobs", "alice"), job("alice"))));
  await check("member cannot create a job for someone else", assertFails(setDoc(doc(ctx("bob"), "aiJobs", "alice"), job("alice"))));
  await check("unverified member cannot create a job", assertFails(setDoc(doc(ctx("carl", false), "aiJobs", "carl"), job("carl"))));
  await check("job cannot arrive already answered", assertFails(setDoc(doc(ctx("bob"), "aiJobs", "bob"), job("bob", { status: "done" }))));
  await check("job query image is size-limited", assertFails(setDoc(doc(ctx("bob"), "aiJobs", "bob"), job("bob", { query: Bytes.fromUint8Array(new Uint8Array(160000)) }))));
  await check("job rejects extra fields", assertFails(setDoc(doc(ctx("bob"), "aiJobs", "bob"), job("bob", { results: [] }))));
  await check("new job right away is throttled", assertFails(setDoc(doc(ctx("alice"), "aiJobs", "alice"), job("alice", { requestId: "r2" }))));
  await check("others cannot read a job", assertFails(getDoc(doc(ctx("bob"), "aiJobs", "alice"))));
  await check("worker reads jobs", assertSucceeds(getDoc(doc(W, "aiJobs", "alice"))));
  await check("worker lists pending jobs", assertSucceeds(getDocs(query(collection(W, "aiJobs"), where("status", "==", "pending")))));
  await check("worker writes the result", assertSucceeds(updateDoc(doc(W, "aiJobs", "alice"), { status: "done", results: [{ id: "p1", similarity: 0.9, verdict: "same" }], model: "m", provider: "cpu", ms: 5, doneAt: serverTimestamp(), answeredRequestId: "r1" })));
  await check("worker cannot change the request", assertFails(updateDoc(doc(W, "aiJobs", "alice"), { status: "done", ids: ["x"] })));
  await check("member cannot fake a result", assertFails(updateDoc(doc(ctx("alice"), "aiJobs", "alice"), { status: "done", results: [] })));
  await check("worker cannot write posts", assertFails(setDoc(doc(W, "posts", "w1"), { ownerId: "worker" })));
  await check("worker writes the heartbeat", assertSucceeds(setDoc(doc(W, "config", "aiWorker"), { online: true, lastSeen: serverTimestamp(), model: "m", provider: "cpu", same: 0.75, maybe: 0.6 })));
  await check("member reads the heartbeat", assertSucceeds(getDoc(doc(ctx("bob"), "config", "aiWorker"))));
  await check("member cannot fake the heartbeat", assertFails(setDoc(doc(ctx("admin"), "config", "aiWorker"), { online: true, lastSeen: serverTimestamp() })));
  // Photo check: a new post's photos ("is it an animal?") plus the nearby comparison, in one job.
  const photos = (n) => Array.from({ length: n }, () => Bytes.fromUint8Array(new Uint8Array(2000)));
  await check("member creates a photo-check job", assertSucceeds(setDoc(doc(ctx("dave"), "aiJobs", "dave"), job("dave", { kind: "check", photos: photos(2) }))));
  const alone = job("erin", { kind: "check", photos: photos(1), images: [], ids: [], labels: [] });
  delete alone.query;
  await check("photo check without nearby candidates", assertSucceeds(setDoc(doc(ctx("erin"), "aiJobs", "erin"), alone)));
  await check("photo check is limited to 6 photos", assertFails(setDoc(doc(ctx("frank"), "aiJobs", "frank"), job("frank", { kind: "check", photos: photos(7) }))));
  await check("photo check photos must be a list", assertFails(setDoc(doc(ctx("frank"), "aiJobs", "frank"), job("frank", { kind: "check", photos: "x" }))));
  await check("unknown job kind rejected", assertFails(setDoc(doc(ctx("frank"), "aiJobs", "frank"), job("frank", { kind: "classify" }))));
  await check("worker writes the photo classification", assertSucceeds(updateDoc(doc(W, "aiJobs", "dave"), { status: "done", classification: [{ kind: "object", animalType: "", phrase: "a coffee cup", score: 0.99 }], results: [], model: "m", provider: "cpu", ms: 5, doneAt: serverTimestamp(), answeredRequestId: "r1" })));
  await check("member cannot fake a classification", assertFails(updateDoc(doc(ctx("dave"), "aiJobs", "dave"), { status: "done", classification: [{ kind: "animal" }] })));
}

console.log("Sightings");
const A = ctx("alice"), B = ctx("bob");
await check("unverified user cannot post", assertFails((async () => {
  const db = ctx("alice", false); const b = writeBatch(db);
  b.set(doc(db, "cats", "c0"), catDoc("alice")); b.set(doc(db, "sightings", "s0"), sightingDoc("alice", "c0"));
  userStats(b, db, "alice", { sightings: 1 }, 10, "deira", true); await b.commit();
})()));
await check("image upload", assertSucceeds(setDoc(doc(A, "images", "img1"), { data: Bytes.fromUint8Array(new Uint8Array(1000)), ownerId: "alice", createdAt: now() })));
await check("oversized image rejected", assertFails(setDoc(doc(A, "images", "big"), { data: Bytes.fromUint8Array(new Uint8Array(1_020_000)), ownerId: "alice", createdAt: now() })));
await check("new cat + sighting + exact location + stats batch", assertSucceeds((async () => {
  const b = writeBatch(A);
  b.set(doc(A, "cats", "c1"), catDoc("alice")); b.set(doc(A, "sightings", "s1"), sightingDoc("alice", "c1"));
  b.set(doc(A, "privateLocations", "s1"), { ownerId: "alice", lat: 25.2712, lng: 55.3001, accuracy: 8 });
  userStats(b, A, "alice", { sightings: 1, photos: 1, catsRegistered: 1 }, 27, "deira", true); await b.commit();
})()));
await register("zed", true);
await check("sighting with too many match ids rejected", assertFails((async () => {
  const Z = ctx("zed"); const b = writeBatch(Z);
  b.set(doc(Z, "cats", "cz"), catDoc("zed")); b.set(doc(Z, "sightings", "sz"), { ...sightingDoc("zed", "cz"), rejectedCatIds: ["1", "2", "3", "4", "5", "6", "7"] });
  userStats(b, Z, "zed", { sightings: 1 }, 10, "deira", true); await b.commit();
})()));
await check("sighting with match feedback accepted", assertSucceeds((async () => {
  const Z = ctx("zed"); const b = writeBatch(Z);
  b.set(doc(Z, "cats", "cz"), catDoc("zed")); b.set(doc(Z, "sightings", "sz"), { ...sightingDoc("zed", "cz"), rejectedCatIds: ["c9"] });
  userStats(b, Z, "zed", { sightings: 1 }, 10, "deira", true); await b.commit();
})()));
await check("posting again immediately is rate limited", assertFails((async () => {
  const b = writeBatch(A); b.set(doc(A, "sightings", "s2"), sightingDoc("alice", "c1"));
  userStats(b, A, "alice", { sightings: 1 }, 10, "deira", true); await b.commit();
})()));
await check("posting cooldown cannot be reset on profile", assertFails(updateDoc(doc(A, "users", "alice"), { lastPostAt: null })));
await check("posting cooldown cannot be deleted", assertFails(setDoc(doc(A, "users", "alice"), profile("alice"))));
await check("faking lastPostAt is rejected", assertFails((async () => {
  const b = writeBatch(A); b.set(doc(A, "sightings", "s2"), sightingDoc("alice", "c1"));
  b.update(doc(A, "users", "alice"), { lastPostAt: new Date(0) }); await b.commit();
})()));
await check("bob adds a sighting to alice's cat (community update)", assertSucceeds((async () => {
  const b = writeBatch(B);
  b.update(doc(B, "cats", "c1"), { sightingCount: increment(1), contributors: arrayUnion("bob"), neighborhoods: arrayUnion("naif"), updatedAt: now(),
    tnrStatus: "ear_tipped", healthStatus: "healthy", photoIds: arrayUnion("img2"), lastSeen: now(), lat: 25.28, lng: 55.31, neighborhood: "naif", features: [0.2, 0.2] });
  b.set(doc(B, "sightings", "s3"), { ...sightingDoc("bob", "c1"), newCat: false });
  userStats(b, B, "bob", { sightings: 1 }, 10, "naif", true); await b.commit();
})()));
await check("follower notification from bob", assertSucceeds(setDoc(doc(B, "users", "alice", "notifications", "n1"), { type: "followed_cat", params: {}, route: "cat/c1", fromUid: "bob", read: false, createdAt: now() })));
await check("forged notification sender rejected", assertFails(setDoc(doc(B, "users", "alice", "notifications", "n2"), { type: "x", fromUid: "alice", read: false, createdAt: now() })));
await check("bob cannot rename alice's cat", assertFails(updateDoc(doc(B, "cats", "c1"), { name: "Hacked" })));
await check("bob cannot set verified TNR", assertFails(updateDoc(doc(B, "cats", "c1"), { tnrStatus: "neutered_verified" })));
await check("new cat cannot forge verified TNR status", assertFails(setDoc(doc(A, "cats", "forged-tnr"), { ...catDoc("alice"), tnrStatus: "neutered_verified" })));
await check("alice edits her cat", assertSucceeds(updateDoc(doc(A, "cats", "c1"), { name: "Zafran", description: "Loves the souq", coatColor: "orange", updatedAt: now() })));
await check("alice cannot hide her cat (mod only)", assertFails(updateDoc(doc(A, "cats", "c1"), { hidden: true })));
await check("follow cat", assertSucceeds((async () => {
  const b = writeBatch(B); b.update(doc(B, "cats", "c1"), { followers: arrayUnion("bob") }); b.update(doc(B, "users", "bob"), { followedCats: arrayUnion("c1") }); await b.commit();
})()));
await check("add alias", assertSucceeds(updateDoc(doc(B, "cats", "c1"), { aliases: arrayUnion("Mishmish"), updatedAt: now() })));
await check("bob cannot read alice's exact location", assertFails(getDoc(doc(B, "privateLocations", "s1"))));
await check("volunteer reads exact location", assertSucceeds(getDoc(doc(ctx("vol"), "privateLocations", "s1"))));
await check("other user cannot take ownership of exact location", assertFails(setDoc(doc(B, "privateLocations", "s1"), { ownerId: "bob", lat: 25.1, lng: 55.1, accuracy: 0 })));
await check("owner cannot reassign exact location", assertFails(updateDoc(doc(A, "privateLocations", "s1"), { ownerId: "bob" })));
await check("orphan exact location rejected", assertFails(setDoc(doc(A, "privateLocations", "orphan"), { ownerId: "alice", lat: 25.1, lng: 55.1, accuracy: 0 })));
await check("exact location cannot attach to another user's sighting", assertFails(setDoc(doc(A, "privateLocations", "s3"), { ownerId: "alice", lat: 25.1, lng: 55.1, accuracy: 0 })));
await check("owner can edit exact location", assertSucceeds(updateDoc(doc(A, "privateLocations", "s1"), { lat: 25.2701, accuracy: 5 })));
await check("alice edits own sighting", assertSucceeds(updateDoc(doc(A, "sightings", "s1"), { notes: "near bakery", healthTags: ["thin"], updatedAt: now() })));
await check("alice cannot self-approve", assertFails(updateDoc(doc(A, "sightings", "s1"), { status: "approved" })));
await check("bob cannot edit alice's sighting", assertFails(updateDoc(doc(B, "sightings", "s1"), { notes: "x" })));
await check("sightings for cat query", assertSucceeds(getDocs(query(collection(B, "sightings"), where("catId", "==", "c1")))));
await check("cats map query", assertSucceeds(getDocs(query(collection(B, "cats"), orderBy("lastSeen", "desc"), limit(800)))));
await check("mod approves sighting + bumps stats", assertSucceeds((async () => {
  const M = ctx("mod"); const b = writeBatch(M);
  b.update(doc(M, "sightings", "s3"), { status: "approved", updatedAt: now() });
  userStats(b, M, "bob", { approvedSightings: 1 }, 5); await b.commit();
})()));
await check("mod action log", assertSucceeds(setDoc(doc(ctx("mod"), "modActions", "a1"), { moderatorId: "mod", moderatorName: "mod", action: "sighting_approved", targetType: "sighting", targetId: "s3", targetLabel: "", note: "", at: now() })));
await check("user cannot read mod log", assertFails(getDocs(collection(B, "modActions"))));
await check("verified medical record by volunteer + TNR verified", assertSucceeds((async () => {
  const V = ctx("vol"); const b = writeBatch(V);
  b.set(doc(V, "medicalRecords", "m1"), { catId: "c1", type: "tnr_confirmed", note: "", byUid: "vol", byName: "vol", at: now() });
  b.update(doc(V, "cats", "c1"), { tnrStatus: "neutered_verified", tnrVerified: true, updatedAt: now() }); await b.commit();
})()));
await check("regular user cannot add medical record", assertFails(setDoc(doc(B, "medicalRecords", "m2"), { catId: "c1", type: "note", byUid: "bob", at: now() })));
await check("owner cannot downgrade verified TNR", assertFails(updateDoc(doc(A, "cats", "c1"), { tnrStatus: "unknown" })));
await check("delete own sighting (incl. missing exact location)", assertSucceeds((async () => {
  const b = writeBatch(B);
  b.delete(doc(B, "sightings", "s3")); b.delete(doc(B, "privateLocations", "s3"));
  b.update(doc(B, "cats", "c1"), { sightingCount: increment(-1), updatedAt: now() });
  userStats(b, B, "bob", { sightings: -1 }, -10); await b.commit();
})()));

console.log("Rescue");
await sleep(11_000); // wait out the rate limit
await check("submit rescue report", assertSucceeds((async () => {
  const b = writeBatch(A);
  b.set(doc(A, "rescues", "r1"), { type: "injured", urgency: "high", description: "limping", landmark: "", photoIds: [], lat: 25.27, lng: 55.30,
    neighborhood: "deira", catId: "c1", catName: "Zafran", reporterId: "alice", reporterName: "alice", status: "submitted", assignedTo: "", assignedName: "",
    updates: [{ status: "submitted", note: "", byUid: "alice", byName: "alice", at: now() }], createdAt: now(), updatedAt: now() });
  userStats(b, A, "alice", { rescueReports: 1 }, 20, "deira", true); await b.commit();
})()));
await check("other users cannot see it", assertFails(getDoc(doc(B, "rescues", "r1"))));
await check("volunteers cannot see unapproved report", assertFails(getDoc(doc(ctx("vol"), "rescues", "r1"))));
await check("reporter's own list", assertSucceeds(getDocs(query(collection(A, "rescues"), where("reporterId", "==", "alice")))));
await check("non-mod cannot list all reports", assertFails(getDocs(collection(B, "rescues"))));
await check("mod lists pending", assertSucceeds(getDocs(query(collection(ctx("mod"), "rescues"), where("status", "==", "submitted")))));
await check("staff lookup for alerts", assertSucceeds(getDocs(query(collection(A, "users"), where("role", "in", ["moderator", "admin"])))));
await check("reporter cannot approve", assertFails(updateDoc(doc(A, "rescues", "r1"), { status: "approved" })));
await check("mod approves", assertSucceeds(updateDoc(doc(ctx("mod"), "rescues", "r1"), { status: "approved", updates: arrayUnion({ status: "approved", note: "", byUid: "mod", byName: "mod", at: now() }), updatedAt: now() })));
await check("volunteer board query", assertSucceeds(getDocs(query(collection(ctx("vol"), "rescues"), where("status", "in", ["approved", "in_progress", "resolved"])))));
await check("volunteer takes the case", assertSucceeds(updateDoc(doc(ctx("vol"), "rescues", "r1"), { status: "in_progress", assignedTo: "vol", assignedName: "vol", updates: arrayUnion({ status: "in_progress", note: "on my way", byUid: "vol", byName: "vol", at: now() }), updatedAt: now() })));
await check("volunteer cannot reject", assertFails(updateDoc(doc(ctx("vol"), "rescues", "r1"), { status: "rejected" })));
await check("reporter adds an update", assertSucceeds(updateDoc(doc(A, "rescues", "r1"), { updates: arrayUnion({ status: "in_progress", note: "still there", byUid: "alice", byName: "alice", at: now() }), updatedAt: now() })));
await check("volunteer resolves", assertSucceeds(updateDoc(doc(ctx("vol"), "rescues", "r1"), { status: "resolved", updates: arrayUnion({ status: "resolved", note: "at vet", byUid: "vol", byName: "vol", at: now() }), updatedAt: now() })));

console.log("Volunteers & roles");
await check("apply as volunteer", assertSucceeds((async () => {
  const b = writeBatch(B);
  b.set(doc(B, "volunteerApplications", "bob"), { userId: "bob", username: "bob", motivation: "love cats", experience: "", area: "deira", status: "pending", reviewedBy: "", createdAt: now() });
  b.update(doc(B, "users", "bob"), { volunteerStatus: "applied" }); await b.commit();
})()));
await check("applicant cannot approve self", assertFails(updateDoc(doc(B, "users", "bob"), { volunteerStatus: "approved" })));
await check("moderator cannot change roles", assertFails(updateDoc(doc(ctx("mod"), "users", "bob"), { role: "volunteer" })));
await check("admin lists applications", assertSucceeds(getDocs(query(collection(ctx("admin"), "volunteerApplications"), where("status", "==", "pending")))));
await check("admin approves volunteer", assertSucceeds((async () => {
  const D = ctx("admin"); const b = writeBatch(D);
  b.update(doc(D, "volunteerApplications", "bob"), { status: "approved", reviewedBy: "admin" });
  b.update(doc(D, "users", "bob"), { volunteerStatus: "approved", role: "volunteer" }); await b.commit();
})()));

console.log("Feeding stations");
const station = { name: "Station", description: "", operatorName: "", schedule: "", foodType: "", hasWater: true, lat: 25.1, lng: 55.2, neighborhood: "al_barsha",
  verified: true, active: true, sample: false, checkinCount: 0, lastCheckinAt: 0, lastCheckinBy: "", createdAt: now(), updatedAt: now() };
await check("user cannot create station", assertFails(setDoc(doc(B, "stations", "st1"), station)));
await check("admin creates station", assertSucceeds(setDoc(doc(ctx("admin"), "stations", "st1"), station)));
await check("check-in batch", assertSucceeds((async () => {
  const b = writeBatch(A);
  b.set(doc(A, "checkins", "ck1"), { stationId: "st1", stationName: "Station", userId: "alice", username: "alice", catsSeen: 3, catIds: [], foodLevel: "low", waterLevel: "full", note: "", at: now() });
  b.update(doc(A, "stations", "st1"), { checkinCount: increment(1), lastCheckinAt: now(), lastCheckinBy: "alice" });
  userStats(b, A, "alice", { checkins: 1, iftarCheckins: 0 }, 5, "al_barsha"); await b.commit();
})()));
await check("user cannot rename station", assertFails(updateDoc(doc(A, "stations", "st1"), { name: "x" })));
await sleep(11_000);
await check("maintenance report", assertSucceeds((async () => {
  const b = writeBatch(A);
  b.set(doc(A, "maintenance", "mt1"), { stationId: "st1", stationName: "Station", userId: "alice", username: "alice", issue: "no_water", description: "", status: "open", resolutionNote: "", resolvedBy: "", createdAt: now(), updatedAt: now() });
  userStats(b, A, "alice", { maintenanceReports: 1 }, 5, null, true); await b.commit();
})()));
await check("open maintenance query", assertSucceeds(getDocs(query(collection(ctx("mod"), "maintenance"), where("status", "in", ["open", "in_progress"])))));
await check("admin resolves maintenance", assertSucceeds(updateDoc(doc(ctx("admin"), "maintenance", "mt1"), { status: "resolved", resolutionNote: "refilled", resolvedBy: "admin", updatedAt: now() })));

console.log("Moderation");
await setDoc(doc(B, "cats", "c2"), { ...catDoc("bob"), name: "Ginger" }).catch(() => {});
await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "sightings", "s9"), sightingDoc("bob", "c2")));
await check("suggest merge", assertSucceeds((async () => {
  const b = writeBatch(A);
  b.set(doc(A, "mergeRequests", "mr1"), { primaryCatId: "c1", primaryName: "Zafran", duplicateCatId: "c2", duplicateName: "Ginger", requestedBy: "alice", requestedByName: "alice",
    reason: "same scar", similarity: 0.8, status: "pending", reviewedBy: "", reviewedByName: "", reviewNote: "", createdAt: now(), reviewedAt: 0 });
  userStats(b, A, "alice", { mergeSuggestions: 1 }, 5); await b.commit();
})()));
await check("user cannot approve merge", assertFails(updateDoc(doc(A, "mergeRequests", "mr1"), { status: "approved" })));
await check("mod performs merge", assertSucceeds((async () => {
  const M = ctx("mod"); const b = writeBatch(M);
  b.update(doc(M, "sightings", "s9"), { catId: "c1", catName: "Zafran", newCat: false });
  b.update(doc(M, "cats", "c1"), { sightingCount: increment(1), aliases: arrayUnion("Ginger"), followers: arrayUnion("bob"), updatedAt: now() });
  b.update(doc(M, "cats", "c2"), { mergedInto: "c1", hidden: true, updatedAt: now() });
  b.update(doc(M, "mergeRequests", "mr1"), { status: "approved", reviewedBy: "mod", reviewedByName: "mod", reviewNote: "", reviewedAt: now() });
  await b.commit();
})()));
// A merge also repoints the duplicate's followers' own follow lists at the kept cat.
await check("mod repoints followers after merge", assertSucceeds(updateDoc(doc(ctx("mod"), "users", "bob"), { followedCats: ["c1"] })));
await check("user cannot edit another member's follow list", assertFails(updateDoc(doc(A, "users", "bob"), { followedCats: [] })));
await sleep(11_000);
await check("report content", assertSucceeds((async () => {
  const b = writeBatch(B);
  b.set(doc(B, "contentReports", "cr1"), { targetType: "cat", targetId: "c1", targetLabel: "Zafran", reason: "spam", details: "", reporterId: "bob", reporterName: "bob", status: "open", resolution: "", resolvedBy: "", createdAt: now() });
  b.update(doc(B, "users", "bob"), { lastPostAt: serverTimestamp() }); await b.commit();
})()));
await check("others cannot read reports", assertFails(getDoc(doc(A, "contentReports", "cr1"))));
await check("mod lists open reports", assertSucceeds(getDocs(query(collection(ctx("mod"), "contentReports"), where("status", "==", "open")))));
await check("mod hides cat", assertSucceeds(updateDoc(doc(ctx("mod"), "cats", "c1"), { hidden: false, updatedAt: now() })));
await check("mod removes photo", assertSucceeds(deleteDoc(doc(ctx("mod"), "images", "img1"))));

console.log("Notifications & account");
await check("inbox query", assertSucceeds(getDocs(query(collection(A, "users", "alice", "notifications"), orderBy("createdAt", "desc"), limit(100)))));
await check("unread query", assertSucceeds(getDocs(query(collection(A, "users", "alice", "notifications"), where("read", "==", false)))));
await check("others cannot read inbox", assertFails(getDocs(collection(B, "users", "alice", "notifications"))));
await check("mark read", assertSucceeds(updateDoc(doc(A, "users", "alice", "notifications", "n1"), { read: true })));
await check("background poll uses latest ordered inbox", assertSucceeds(getDocs(query(collection(A, "users", "alice", "notifications"), orderBy("createdAt", "desc"), limit(100)))));

console.log("Deletion cleanup and retry");
await check("owner can query all uploaded images", assertSucceeds(getDocs(query(collection(A, "images"), where("ownerId", "==", "alice")))));
await check("owner can query own rescue and moderation data", assertSucceeds((async () => {
  for (const [col, field] of [["rescues", "reporterId"], ["maintenance", "userId"], ["contentReports", "reporterId"], ["mergeRequests", "requestedBy"]]) {
    await getDocs(query(collection(A, col), where(field, "==", "alice")));
  }
})()));
await check("others cannot delete rescue", assertFails(deleteDoc(doc(B, "rescues", "r1"))));
await check("others cannot delete maintenance", assertFails(deleteDoc(doc(B, "maintenance", "mt1"))));
await check("others cannot delete merge suggestion", assertFails(deleteDoc(doc(B, "mergeRequests", "mr1"))));
await check("others cannot delete content report", assertFails(deleteDoc(doc(A, "contentReports", "cr1"))));
await check("others cannot delete volunteer application", assertFails(deleteDoc(doc(A, "volunteerApplications", "bob"))));
await check("owner deletes own rescue, maintenance and merge", assertSucceeds((async () => {
  const b = writeBatch(A);
  b.delete(doc(A, "rescues", "r1")); b.delete(doc(A, "maintenance", "mt1")); b.delete(doc(A, "mergeRequests", "mr1"));
  await b.commit();
})()));
await check("owner deletes content report and volunteer application", assertSucceeds((async () => {
  const b = writeBatch(B);
  b.delete(doc(B, "contentReports", "cr1")); b.delete(doc(B, "volunteerApplications", "bob")); await b.commit();
})()));
await check("missing own volunteer application can be deleted", assertSucceeds(deleteDoc(doc(A, "volunteerApplications", "alice"))));
await check("image references detach without changing verified TNR", assertSucceeds((async () => {
  await setDoc(doc(A, "images", "delete-photo"), { data: Bytes.fromUint8Array(new Uint8Array(100)), ownerId: "alice", createdAt: now() });
  await updateDoc(doc(A, "cats", "c1"), { photoIds: arrayUnion("delete-photo"), coverPhotoId: "delete-photo" });
  const cats = await getDocs(query(collection(A, "cats"), where("photoIds", "array-contains-any", ["delete-photo"])));
  for (const c of cats.docs) await runTransaction(A, async (tx) => {
    const fresh = await tx.get(c.ref); const remaining = fresh.data().photoIds.filter((id) => id !== "delete-photo");
    tx.update(c.ref, { photoIds: remaining, coverPhotoId: remaining[0] || "" });
  });
  await deleteDoc(doc(A, "images", "delete-photo"));
  const cat = (await getDoc(doc(A, "cats", "c1"))).data();
  if (cat.photoIds.includes("delete-photo") || cat.coverPhotoId === "delete-photo") throw Error("dangling image reference");
})()));
await check("sighting deletion retry decrements cat count once", assertSucceeds((async () => {
  const catRef = doc(A, "cats", "c1"), sightingRef = doc(A, "sightings", "s1");
  const before = (await getDoc(catRef)).data().sightingCount;
  for (let retry = 0; retry < 2; retry++) await runTransaction(A, async (tx) => {
    const source = await tx.get(sightingRef);
    if (!source.exists()) return;
    const cat = await tx.get(catRef);
    tx.delete(sightingRef); tx.delete(doc(A, "privateLocations", "s1"));
    tx.update(catRef, { sightingCount: Math.max(0, cat.data().sightingCount - 1) });
  });
  if ((await getDoc(catRef)).data().sightingCount !== Math.max(0, before - 1)) throw Error("count changed twice");
})()));
await check("station check-in deletion updates count and last author", assertSucceeds((async () => {
  const checkinRef = doc(A, "checkins", "ck1"), stationRef = doc(A, "stations", "st1");
  await runTransaction(A, async (tx) => {
    const source = await tx.get(checkinRef), station = await tx.get(stationRef);
    tx.delete(source.ref); tx.update(station.ref, { checkinCount: Math.max(0, station.data().checkinCount - 1), lastCheckinAt: 0, lastCheckinBy: "" });
  });
})()));
await check("account cleanup removes follower and contributor membership", assertSucceeds((async () => {
  for (const field of ["followers", "contributors"]) {
    const cats = await getDocs(query(collection(A, "cats"), where(field, "array-contains", "alice")));
    for (const c of cats.docs) await updateDoc(c.ref, { [field]: arrayRemove("alice") });
  }
})()));
await check("account deletion", assertSucceeds((async () => {
  const sightings = await getDocs(query(collection(A, "sightings"), where("userId", "==", "alice")));
  const locs = await getDocs(query(collection(A, "privateLocations"), where("ownerId", "==", "alice")));
  const checkins = await getDocs(query(collection(A, "checkins"), where("userId", "==", "alice")));
  const notes = await getDocs(collection(A, "users", "alice", "notifications"));
  const b = writeBatch(A);
  [...sightings.docs, ...locs.docs, ...checkins.docs, ...notes.docs].forEach((d) => b.delete(d.ref));
  b.delete(doc(A, "users", "alice", "private", "account")); b.delete(doc(A, "usernames", "alice")); b.delete(doc(A, "users", "alice"));
  await b.commit();
})()));

// ---------------- 2.0 community features ----------------
console.log("Posts");
await register("carol"); await register("dave"); await register("erin");
// Registration happens unverified; these accounts post, so mark them verified from here on.
const C = ctx("carol"), D = ctx("dave"), E = ctx("erin"), M = ctx("mod"), AD = ctx("admin");
const postDoc = (uid, extra = {}) => ({
  ownerId: uid, ownerName: uid, ownerPhotoId: "", animalType: "dog", category: "lost", status: "open", name: "Rex",
  description: "Brown dog with a red collar", color: "brown", breed: "saluki", features: "red collar", sex: "male", ageGroup: "adult",
  photoIds: ["img1"], videoPath: "", lat: 25.08, lng: 55.14, approximate: false, areaRadiusM: 500, neighborhood: "dubai_marina",
  lastSeenAt: now(), reward: 500, contactMethod: "in_app", contactPhone: "", contactEmail: "", adoptionStatus: "available",
  health: "", vaccinated: false, neutered: false, updatesCount: 0, hidden: false, keywords: ["rex", "brown"],
  createdAt: now(), updatedAt: now(), resolvedAt: 0, ...extra,
});
async function createPost(db, uid, id, extra = {}) {
  const b = writeBatch(db);
  b.set(doc(db, "posts", id), postDoc(uid, extra));
  userStats(b, db, uid, { posts: 1 }, 10, "dubai_marina", true);
  await b.commit();
}
await check("member creates a lost post (rate-limited batch)", assertSucceeds(createPost(C, "carol", "p1")));
await check("a second post within 10 seconds is refused", assertFails(createPost(C, "carol", "p1b")));
await check("unverified account cannot post", assertFails(createPost(ctx("erin", false), "erin", "px")));
await check("cannot post as someone else", assertFails(createPost(D, "carol", "px")));
await check("new posts cannot start hidden", assertFails(createPost(D, "dave", "px", { hidden: true })));
await check("new posts cannot fake an updates count", assertFails(createPost(D, "dave", "px", { updatesCount: 7 })));
await check("unknown category is refused", assertFails(createPost(D, "dave", "px", { category: "selling" })));
await check("negative reward is refused", assertFails(createPost(D, "dave", "px", { reward: -5 })));
await check("too many photos is refused", assertFails(createPost(D, "dave", "px", { photoIds: ["1", "2", "3", "4", "5", "6", "7"] })));
await check("public feed query (visible posts, newest first)", assertSucceeds(getDocs(query(collection(B, "posts"), where("hidden", "==", false), orderBy("createdAt", "desc"), limit(50)))));
await check("unfiltered post list refused for members", assertFails(getDocs(query(collection(B, "posts"), orderBy("createdAt", "desc")))));
await check("unfiltered post list allowed for moderators", assertSucceeds(getDocs(query(collection(M, "posts"), orderBy("createdAt", "desc")))));
await check("someone else's post cannot be edited", assertFails(updateDoc(doc(B, "posts", "p1"), { description: "spam" })));
await check("owner edits own post", assertSucceeds(updateDoc(doc(C, "posts", "p1"), { description: "Brown saluki, red collar, very shy", updatedAt: now() })));
await check("owner cannot hide or unhide their post", assertFails(updateDoc(doc(C, "posts", "p1"), { hidden: true })));
await check("owner cannot fake the updates counter", assertFails(updateDoc(doc(C, "posts", "p1"), { updatesCount: 3, description: "x" })));
await check("someone else's post cannot be deleted", assertFails(deleteDoc(doc(B, "posts", "p1"))));
await sleep(10500); // bob posted in the earlier sections; respect the posting gap.
await check("sighting update + counter + rate limit in one batch", assertSucceeds((async () => {
  const b = writeBatch(B);
  b.set(doc(B, "posts", "p1", "updates", "u1"), { postId: "p1", kind: "sighting", text: "Saw him near the tram", photoId: "", lat: 0, lng: 0, neighborhood: "", byUid: "bob", byName: "bob", createdAt: now() });
  b.update(doc(B, "posts", "p1"), { updatesCount: increment(1), updatedAt: now() });
  userStats(b, B, "bob", { postUpdates: 1 }, 5, null, true);
  await b.commit();
})()));
await check("counter cannot jump by more than one", assertFails(updateDoc(doc(D, "posts", "p1"), { updatesCount: increment(5), updatedAt: now() })));
await check("post owner removes an update and the counter", assertSucceeds((async () => {
  const b = writeBatch(C);
  b.delete(doc(C, "posts", "p1", "updates", "u1"));
  b.update(doc(C, "posts", "p1"), { updatesCount: increment(-1) });
  await b.commit();
})()));
await check("moderator hides a post", assertSucceeds(updateDoc(doc(M, "posts", "p1"), { hidden: true, updatedAt: now() })));
await check("hidden post is not readable by others", assertFails(getDoc(doc(B, "posts", "p1"))));
await check("a deleted post reads as missing, not as an error", assertSucceeds(getDoc(doc(B, "posts", "never-existed"))));
await check("hidden post stays readable by its author", assertSucceeds(getDoc(doc(C, "posts", "p1"))));
await check("moderator restores the post", assertSucceeds(updateDoc(doc(M, "posts", "p1"), { hidden: false })));
await check("own visible posts for a profile", assertSucceeds(getDocs(query(collection(B, "posts"), where("ownerId", "==", "carol"), where("hidden", "==", false)))));

console.log("Suspension");
await check("moderator cannot suspend accounts", assertFails(updateDoc(doc(M, "users", "dave"), { suspended: true })));
await check("admin suspends an account", assertSucceeds(updateDoc(doc(AD, "users", "dave"), { suspended: true })));
await check("suspended account cannot post", assertFails(createPost(D, "dave", "pd")));
await check("suspended account cannot lift its own suspension", assertFails(updateDoc(doc(D, "users", "dave"), { suspended: false })));
await check("suspended account can still read", assertSucceeds(getDoc(doc(D, "posts", "p1"))));
await check("admin restores the account", assertSucceeds(updateDoc(doc(AD, "users", "dave"), { suspended: false })));
await check("new profiles cannot start suspended", assertFails(setDoc(doc(ctx("zed"), "users", "zed"), { ...profile("zed"), suspended: true })));

console.log("Adoption");
await sleep(10500);
await check("member lists an animal for adoption", assertSucceeds(createPost(C, "carol", "a1", { category: "adoption", name: "Luna", animalType: "cat", reward: 0, areaRadiusM: 0 })));
const requestDoc = (uid, extra = {}) => ({ postId: "a1", postName: "Luna", postPhotoId: "", ownerId: "carol", requesterId: uid, requesterName: uid, message: "I have a big balcony", home: "Flat", status: "pending", ownerNote: "", createdAt: now(), decidedAt: 0, ...extra });
await check("checking for my not-yet-sent request is allowed", assertSucceeds(getDoc(doc(B, "adoptionRequests", "a1_bob"))));
await check("member sends an adoption request", assertSucceeds(setDoc(doc(B, "adoptionRequests", "a1_bob"), requestDoc("bob"))));
await check("request id must match the post and requester", assertFails(setDoc(doc(D, "adoptionRequests", "a1_bob2"), requestDoc("dave"))));
await check("request must name the real listing owner", assertFails(setDoc(doc(D, "adoptionRequests", "a1_dave"), requestDoc("dave", { ownerId: "dave" }))));
await check("cannot request your own listing", assertFails(setDoc(doc(C, "adoptionRequests", "a1_carol"), requestDoc("carol"))));
await check("others cannot read a request", assertFails(getDoc(doc(D, "adoptionRequests", "a1_bob"))));
await check("requester cannot approve their own request", assertFails(updateDoc(doc(B, "adoptionRequests", "a1_bob"), { status: "approved" })));
await check("owner approves and marks the listing pending", assertSucceeds((async () => {
  const b = writeBatch(C);
  b.update(doc(C, "adoptionRequests", "a1_bob"), { status: "approved", ownerNote: "Come meet her!", decidedAt: now() });
  b.update(doc(C, "posts", "a1"), { adoptionStatus: "pending", updatedAt: now() });
  await b.commit();
})()));
await check("an approved request cannot be withdrawn and re-opened", assertFails(updateDoc(doc(B, "adoptionRequests", "a1_bob"), { status: "withdrawn" })));
await check("owner lists received requests", assertSucceeds(getDocs(query(collection(C, "adoptionRequests"), where("ownerId", "==", "carol")))));

console.log("Pet sitting");
const sitterDoc = (extra = {}) => ({
  name: "carol", photoId: "", headline: "Cat lover in JLT", description: "", experience: "", petTypes: ["cat"], services: ["home_visits"],
  availability: "", rate: 60, rateUnit: "visit", lat: 25.07, lng: 55.14, neighborhood: "jlt", active: true,
  ratingSum: 0, ratingCount: 0, lastReviewId: "", createdAt: now(), updatedAt: now(), ...extra,
});
await check("sitter profiles cannot start with ratings", assertFails(setDoc(doc(C, "sitters", "carol"), sitterDoc({ ratingCount: 9, ratingSum: 45 }))));
await check("member creates their sitter profile", assertSucceeds(setDoc(doc(C, "sitters", "carol"), sitterDoc())));
await check("cannot create a sitter profile for someone else", assertFails(setDoc(doc(B, "sitters", "carol2"), sitterDoc())));
await check("sitter cannot edit their own rating", assertFails(updateDoc(doc(C, "sitters", "carol"), { ratingSum: 50, ratingCount: 10 })));
const bookingDoc = (owner, sitter, extra = {}) => ({ ownerId: owner, ownerName: owner, sitterId: sitter, sitterName: sitter, petTypes: ["cat"], petCount: 1, startAt: now(), endAt: now() + 86400000, notes: "", sitterNote: "", status: "requested", reviewed: false, createdAt: now(), updatedAt: now(), ...extra });
async function book(db, owner, sitter, id, extra = {}) {
  const b = writeBatch(db);
  b.set(doc(db, "bookings", id), bookingDoc(owner, sitter, extra));
  b.update(doc(db, "users", owner), { lastPostAt: serverTimestamp() });
  await b.commit();
}
await sleep(10500);
await check("owner requests a booking", assertSucceeds(book(B, "bob", "carol", "b1")));
await check("booking needs an existing sitter profile", assertFails(book(E, "erin", "nobody", "b2")));
await check("booking cannot start accepted", assertFails(book(E, "erin", "carol", "b3", { status: "accepted" })));
await check("others cannot read the booking", assertFails(getDoc(doc(D, "bookings", "b1"))));
await check("owner cannot accept their own request", assertFails(updateDoc(doc(B, "bookings", "b1"), { status: "accepted" })));
await check("sitter accepts", assertSucceeds(updateDoc(doc(C, "bookings", "b1"), { status: "accepted", updatedAt: now() })));
await check("owner marks it completed", assertSucceeds(updateDoc(doc(B, "bookings", "b1"), { status: "completed", updatedAt: now() })));
await check("a completed booking cannot be reopened", assertFails(updateDoc(doc(C, "bookings", "b1"), { status: "accepted" })));
const reviewTx = (db, uid, rating, bump = 0) => runTransaction(db, async (tx) => {
  const sitterRef = doc(db, "sitters", "carol");
  const sitter = await tx.get(sitterRef);
  tx.set(doc(db, "reviews", "b1"), { sitterId: "carol", authorId: uid, authorName: uid, rating, text: "Lovely", createdAt: now() });
  tx.update(sitterRef, { ratingSum: sitter.data().ratingSum + rating + bump, ratingCount: sitter.data().ratingCount + 1, lastReviewId: "b1", updatedAt: now() });
  tx.update(doc(db, "bookings", "b1"), { reviewed: true, updatedAt: now() });
});
await check("someone outside the booking cannot review it", assertFails(reviewTx(D, "dave", 5)));
await check("rating cannot be inflated alongside the review", assertFails(reviewTx(B, "bob", 5, 10)));
await check("ratings outside 1–5 are refused", assertFails(reviewTx(B, "bob", 9)));
await check("owner reviews the completed booking (review + rating + flag)", assertSucceeds(reviewTx(B, "bob", 5)));
await check("a booking can only be reviewed once", assertFails(reviewTx(B, "bob", 4)));
await check("sitter rating reflects exactly one review", assertSucceeds((async () => {
  const s = (await getDoc(doc(B, "sitters", "carol"))).data();
  if (s.ratingCount !== 1 || s.ratingSum !== 5) throw Error(`got ${s.ratingSum}/${s.ratingCount}`);
})()));

console.log("Messages");
const chatDoc = (a, b) => ({ members: [a, b], names: { [a]: a, [b]: b }, photos: { [a]: "", [b]: "" }, lastText: "", lastSender: "", lastAt: now(), unread: { [a]: 0, [b]: 0 } });
await check("checking whether a chat exists is allowed", assertSucceeds(getDoc(doc(B, "chats", "bob_carol"))));
await check("member starts a chat", assertSucceeds(setDoc(doc(B, "chats", "bob_carol"), chatDoc("bob", "carol"))));
await check("chat id must be the sorted member ids", assertFails(setDoc(doc(B, "chats", "carol_bob"), chatDoc("bob", "carol"))));
await check("cannot create a chat between two other people", assertFails(setDoc(doc(D, "chats", "carol_erin"), chatDoc("carol", "erin"))));
await check("outsiders cannot read a chat", assertFails(getDoc(doc(D, "chats", "bob_carol"))));
const send = (db, uid, other, id) => (async () => {
  const b = writeBatch(db);
  b.set(doc(db, "chats", "bob_carol", "messages", id), { senderId: uid, text: "Hi! Is Luna still available?", imageId: "", postId: "a1", postTitle: "Luna", postPhotoId: "", createdAt: now() });
  b.update(doc(db, "chats", "bob_carol"), { lastText: "Hi!", lastSender: uid, lastAt: now(), [`unread.${other}`]: increment(1) });
  await b.commit();
})();
await check("member sends a message", assertSucceeds(send(B, "bob", "carol", "m1")));
await check("outsiders cannot post into a chat", assertFails(send(D, "dave", "carol", "m2")));
await check("outsiders cannot read messages", assertFails(getDocs(collection(D, "chats", "bob_carol", "messages"))));
await check("members list their chats", assertSucceeds(getDocs(query(collection(C, "chats"), where("members", "array-contains", "carol")))));
await check("chat members cannot be changed", assertFails(updateDoc(doc(B, "chats", "bob_carol"), { members: ["bob", "dave"] })));
await check("reader clears their unread count", assertSucceeds(updateDoc(doc(C, "chats", "bob_carol"), { "unread.carol": 0 })));
await check("person blocks a sender", assertSucceeds(setDoc(doc(C, "users", "carol", "blocked", "bob"), { at: now() })));
await check("blocked sender cannot message them", assertFails(send(B, "bob", "carol", "m3")));
await check("block list is private", assertFails(getDoc(doc(B, "users", "carol", "blocked", "bob"))));
await check("unblock", assertSucceeds(deleteDoc(doc(C, "users", "carol", "blocked", "bob"))));
await check("messages flow again after unblocking", assertSucceeds(send(B, "bob", "carol", "m4")));
await check("sender deletes their own message", assertSucceeds(deleteDoc(doc(B, "chats", "bob_carol", "messages", "m4"))));
await check("cannot delete the other person's message", assertFails(deleteDoc(doc(C, "chats", "bob_carol", "messages", "m1"))));

console.log("Alerts, favourites and usernames");
const alert = (from) => ({ type: "message", params: { user: from }, route: "chat/bob_carol", fromUid: from, read: false, createdAt: now() });
await check("first alert with a fixed id is delivered", assertSucceeds(setDoc(doc(B, "users", "carol", "notifications", "msg_bob_carol"), alert("bob"))));
await check("a repeat alert with the same id is refused (no spam)", assertFails(setDoc(doc(B, "users", "carol", "notifications", "msg_bob_carol"), alert("bob"))));
await check("recipient dismisses the alert", assertSucceeds(deleteDoc(doc(C, "users", "carol", "notifications", "msg_bob_carol"))));
await check("alerts cannot be sent in someone else's name", assertFails(setDoc(doc(B, "users", "carol", "notifications", "fake"), alert("dave"))));
await check("save a favourite", assertSucceeds(setDoc(doc(B, "users", "bob", "favorites", "a1"), { postId: "a1", savedAt: now() })));
await check("favourites are private", assertFails(getDocs(collection(C, "users", "bob", "favorites"))));
await check("follow an area for lost-pet alerts", assertSucceeds(setDoc(doc(C, "areaFollows", "carol"), { neighborhoods: ["jlt", "dubai_marina"], lat: 25.07, lng: 55.14, radiusKm: 3, updatedAt: now() })));
await check("cannot follow areas for someone else", assertFails(setDoc(doc(B, "areaFollows", "carol"), { neighborhoods: [] })));
await check("posters can find area followers", assertSucceeds(getDocs(query(collection(B, "areaFollows"), where("neighborhoods", "array-contains", "jlt")))));
await check("app cannot set a username's login email", assertFails(setDoc(doc(ctx("yan"), "usernames", "yan"), { uid: "yan", loginEmail: "someone@else.test" })));

console.log("October improvements: schema, reminders, directory and verified sitters");
await check("owner records fresh activity", assertSucceeds(updateDoc(doc(C,"posts","p1"),{lastActivityAt:now()})));
await check("another member cannot bump owner activity", assertFails(updateDoc(doc(B,"posts","p1"),{lastActivityAt:now()})));
await check("negative activity rejected", assertFails(updateDoc(doc(C,"posts","p1"),{lastActivityAt:-1})));
const fingerprint=Array(27).fill(0.01);
await check("bounded numeric fingerprint accepted", assertSucceeds(updateDoc(doc(C,"posts","p1"),{fingerprint})));
await check("oversized fingerprint rejected", assertFails(updateDoc(doc(C,"posts","p1"),{fingerprint:Array(65).fill(0)})));
await check("fingerprint values outside range rejected", assertFails(updateDoc(doc(C,"posts","p1"),{fingerprint:[2,...fingerprint.slice(1)]})));
await check("non-numeric fingerprint rejected", assertFails(updateDoc(doc(C,"posts","p1"),{fingerprint:[...fingerprint.slice(0,26),"invalid"]})));
await check("non-finite fingerprint rejected", assertFails(updateDoc(doc(C,"posts","p1"),{fingerprint:[NaN,...fingerprint.slice(1)]})));
await check("foreign fingerprint edit rejected", assertFails(updateDoc(doc(B,"posts","p1"),{fingerprint})));
for(const microchip of ["yes","no","unknown"]) await check(`microchip ${microchip} accepted`,assertSucceeds(updateDoc(doc(C,"posts","p1"),{microchip})));
await check("public chip number rejected",assertFails(updateDoc(doc(C,"posts","p1"),{microchipNumber:"123456789"})));
await check("chip enum cannot contain a number",assertFails(updateDoc(doc(C,"posts","p1"),{microchip:"123456789"})));
await check("foreign microchip edit rejected",assertFails(updateDoc(doc(B,"posts","p1"),{microchip:"yes"})));
const stale={type:"stale_post",params:{name:"Rex"},route:"post/p1",fromUid:"carol",read:false,createdAt:now()};
await check("one reminder per deterministic window",assertSucceeds(setDoc(doc(C,"users","carol","notifications","stale_p1_1"),stale)));
await check("same reminder cannot refresh its timestamp",assertFails(setDoc(doc(C,"users","carol","notifications","stale_p1_1"),{...stale,createdAt:stale.createdAt+1})));
await check("reminder can be marked read",assertSucceeds(updateDoc(doc(C,"users","carol","notifications","stale_p1_1"),{read:true})));
await check("recipient cannot forge reminder content",assertFails(updateDoc(doc(C,"users","carol","notifications","stale_p1_1"),{params:{name:"changed"}})));
await check("new window reminder accepted",assertSucceeds(setDoc(doc(C,"users","carol","notifications","stale_p1_2"),stale)));
await check("sitter cannot self-verify at creation",assertFails(setDoc(doc(E,"sitters","erin"),sitterDoc({name:"erin",verified:true}))));
await check("unverified sitter can be created",assertSucceeds(setDoc(doc(E,"sitters","erin"),sitterDoc({name:"erin",verified:false}))));
await check("sitter cannot self-verify by edit",assertFails(updateDoc(doc(C,"sitters","carol"),{verified:true})));
await check("moderator cannot grant verification",assertFails(updateDoc(doc(M,"sitters","carol"),{verified:true})));
await check("admin grants verification",assertSucceeds(updateDoc(doc(AD,"sitters","carol"),{verified:true})));
await check("sitter edits preserve verification",assertSucceeds(updateDoc(doc(C,"sitters","carol"),{headline:"Updated profile"})));
await check("sitter cannot remove verification",assertFails(updateDoc(doc(C,"sitters","carol"),{verified:false})));
await check("admin revokes verification",assertSucceeds(updateDoc(doc(AD,"sitters","carol"),{verified:false})));
await check("verification must be boolean even for admins",assertFails(updateDoc(doc(AD,"sitters","carol"),{verified:"yes"})));
const contact={name:"Demo veterinary contact",kind:"vet",phone:"",address:"Demo area",website:"",note:"Fictional test entry",demo:true,updatedAt:now()};
await check("member cannot create directory entry",assertFails(setDoc(doc(C,"helpDirectory","demo"),contact)));
await check("moderator cannot create directory entry",assertFails(setDoc(doc(M,"helpDirectory","demo"),contact)));
await check("admin creates directory entry",assertSucceeds(setDoc(doc(AD,"helpDirectory","demo"),contact)));
await check("directory readable without login",assertSucceeds(getDocs(collection(anon,"helpDirectory"))));
await check("empty directory name rejected",assertFails(updateDoc(doc(AD,"helpDirectory","demo"),{name:""})));
await check("insecure directory website rejected",assertFails(updateDoc(doc(AD,"helpDirectory","demo"),{website:"http://example.test"})));
await check("unexpected directory fields rejected",assertFails(updateDoc(doc(AD,"helpDirectory","demo"),{secret:"unexpected"})));
await check("member cannot edit directory",assertFails(updateDoc(doc(C,"helpDirectory","demo"),{note:"edited"})));
await check("admin edits directory",assertSucceeds(updateDoc(doc(AD,"helpDirectory","demo"),{note:"Updated fictional note"})));
await check("member cannot delete directory",assertFails(deleteDoc(doc(C,"helpDirectory","demo"))));
await check("admin deletes directory",assertSucceeds(deleteDoc(doc(AD,"helpDirectory","demo"))));
await check("admin weekly count queries",assertSucceeds(getDocs(query(collection(AD,"posts"),where("createdAt",">=",0),where("createdAt","<",now()+1)))));
await check("admin reunion cohort query",assertSucceeds(getDocs(query(collection(AD,"posts"),where("category","in",["lost","found"]),where("status","==","reunited"),where("createdAt",">=",0)))));
await check("moderators cannot publish chip numbers",assertFails(updateDoc(doc(M,"posts","p1"),{microchipNumber:"123456789"})));
await check("booking owner can erase their own name and notes",assertSucceeds(updateDoc(doc(B,"bookings","b1"),{ownerName:"",notes:""})));
await check("sitter can erase their own booking name and notes",assertSucceeds(updateDoc(doc(C,"bookings","b1"),{sitterName:"",sitterNote:""})));
await check("outsider cannot redact someone else's booking",assertFails(updateDoc(doc(D,"bookings","b1"),{ownerName:"",notes:""})));
await check("booking redaction cannot change the participant uid",assertFails(updateDoc(doc(B,"bookings","b1"),{ownerName:"",notes:"",ownerId:"dave"})));
await check("review author can erase name and review text",assertSucceeds(updateDoc(doc(B,"reviews","b1"),{authorName:"",text:""})));
await check("review privacy cleanup cannot change rating",assertFails(updateDoc(doc(B,"reviews","b1"),{authorName:"",text:"",rating:1})));
await check("another user cannot redact a review",assertFails(updateDoc(doc(D,"reviews","b1"),{authorName:"",text:""})));
await check("listing owner deletes received adoption request",assertSucceeds(deleteDoc(doc(C,"adoptionRequests","a1_bob"))));
await env.withSecurityRulesDisabled(async(c)=>{
  await setDoc(doc(c.firestore(),"posts","p1","updates","own-export"),{postId:"p1",byUid:"bob",text:"Own contribution",createdAt:now()});
  await updateDoc(doc(c.firestore(),"posts","p1"),{updatesCount:1});
});
await check("own updates can be exported across posts",assertSucceeds(getDocs(query(collectionGroup(B,"updates"),where("byUid","==","bob")))));
await check("foreign updates cannot be queried as own data",assertFails(getDocs(query(collectionGroup(E,"updates"),where("byUid","==","bob")))));
await check("own update removal decrements the surviving post",assertSucceeds(runTransaction(B,async(tx)=>{
  const comment=doc(B,"posts","p1","updates","own-export"),parent=doc(B,"posts","p1");
  const source=await tx.get(comment),post=await tx.get(parent);
  tx.delete(source.ref);tx.update(parent,{updatesCount:Math.max(0,post.data().updatesCount-1)});
})));

console.log("Lost report <-> sighting matches");
{
  const W = env.authenticatedContext("worker", { email: "ai-worker@demo.catmap.test", email_verified: true, catmapAiWorker: true }).firestore();
  for (const u of ["owner1", "seer1", "seer2", "nosy1"]) await register(u, true);
  await env.withSecurityRulesDisabled(async (c) => {
    const f = c.firestore();
    await setDoc(doc(f, "posts", "L1"), postDoc("owner1", { animalType: "cat", name: "Simba" }));
    await setDoc(doc(f, "posts", "L2"), postDoc("owner1", { animalType: "dog", name: "Rex2" }));
    await setDoc(doc(f, "posts", "S1"), postDoc("seer1", { animalType: "cat", category: "spotted", name: "", reward: 0 }));
    await setDoc(doc(f, "posts", "S2"), postDoc("seer2", { animalType: "cat", category: "street", name: "", reward: 0 }));
    await setDoc(doc(f, "posts", "S3"), postDoc("seer1", { animalType: "cat", category: "found", name: "", reward: 0 }));
    await setDoc(doc(f, "posts", "S4"), postDoc("owner1", { animalType: "cat", category: "spotted", name: "", reward: 0 }));
    await setDoc(doc(f, "posts", "S5"), postDoc("seer2", { animalType: "cat", category: "spotted", name: "", reward: 0 }));
  });
  const O = ctx("owner1"), R = ctx("seer1"), R2 = ctx("seer2"), N = ctx("nosy1");
  const m = (lost, sighting, lo, so, extra = {}) => ({
    lostPostId: lost, sightingPostId: sighting, lostOwnerId: lo, sightingOwnerId: so, members: [lo, so], animalType: "cat",
    score: 0.8, visual: 0.62, tier: "strong", method: "embedding", model: "pet-recognition-base+detr-resnet-50/v1",
    distanceKm: 1.2, daysApart: 2, reasons: ["photo_very_similar", "near", "recent"], source: "worker",
    ownerState: "new", reporterState: "new", createdBy: "worker", createdAt: now(), updatedAt: now(), notified: false, ...extra,
  });
  const device = (uid, extra = {}) => ({ source: "device", createdBy: uid, method: "fingerprint", model: "fingerprint27/v1", tier: "possible", ...extra });
  await check("worker suggests a match", assertSucceeds(setDoc(doc(W, "matches", "L1__S1"), m("L1", "S1", "owner1", "seer1"))));
  await check("match id must name the pair", assertFails(setDoc(doc(W, "matches", "L1__x"), m("L1", "S2", "owner1", "seer2"))));
  await check("match members must be the two post owners", assertFails(setDoc(doc(W, "matches", "L1__S2"), m("L1", "S2", "owner1", "nosy1"))));
  await check("a lost report can't match a different animal type", assertFails(setDoc(doc(W, "matches", "L2__S2"), m("L2", "S2", "owner1", "seer2", { animalType: "dog" }))));
  await check("two lost reports can't be paired", assertFails(setDoc(doc(W, "matches", "L1__L2"), m("L1", "L2", "owner1", "owner1"))));
  await check("own posts are never paired", assertFails(setDoc(doc(W, "matches", "L1__S4"), m("L1", "S4", "owner1", "owner1"))));
  await check("match rejects unknown fields", assertFails(setDoc(doc(W, "matches", "L1__S2"), m("L1", "S2", "owner1", "seer2", { probability: 0.99 }))));
  await check("phone (PC offline) suggests a colour match", assertSucceeds(setDoc(doc(R2, "matches", "L1__S2"), m("L1", "S2", "owner1", "seer2", device("seer2")))));
  await check("phone can't claim a strong match", assertFails(setDoc(doc(R, "matches", "L1__S3"), m("L1", "S3", "owner1", "seer1", device("seer1", { tier: "strong" })))));
  await check("phone can't claim the AI model", assertFails(setDoc(doc(R, "matches", "L1__S3"), m("L1", "S3", "owner1", "seer1", device("seer1", { method: "embedding" })))));
  await check("phone can't pretend to be the worker", assertFails(setDoc(doc(R, "matches", "L1__S3"), m("L1", "S3", "owner1", "seer1"))));
  await check("outsider can't pair other people's posts", assertFails(setDoc(doc(N, "matches", "L1__S3"), m("L1", "S3", "owner1", "seer1", device("nosy1")))));
  await check("reporter links their own sighting", assertSucceeds(setDoc(doc(R, "matches", "L1__S3"), m("L1", "S3", "owner1", "seer1", device("seer1", { reporterState: "confirmed" })))));
  await check("owner can't confirm on the reporter's behalf", assertFails(setDoc(doc(O, "matches", "L1__S5"), m("L1", "S5", "owner1", "seer2", device("owner1", { reporterState: "confirmed" })))));
  await check("an existing match can't be overwritten by a phone", assertFails(setDoc(doc(R, "matches", "L1__S1"), m("L1", "S1", "owner1", "seer1", device("seer1")))));
  await check("owner reads the match", assertSucceeds(getDoc(doc(O, "matches", "L1__S1"))));
  await check("reporter reads the match", assertSucceeds(getDoc(doc(R, "matches", "L1__S1"))));
  await check("outsider can't read a match", assertFails(getDoc(doc(N, "matches", "L1__S1"))));
  await check("owner lists their matches", assertSucceeds(getDocs(query(collection(O, "matches"), where("members", "array-contains", "owner1")))));
  await check("outsider can't list everyone's matches", assertFails(getDocs(query(collection(N, "matches"), where("lostPostId", "==", "L1")))));
  await check("missing match reads as missing (phone checks before creating)", assertSucceeds(getDoc(doc(N, "matches", "L9__S9"))));
  await check("owner says it might be theirs", assertSucceeds(updateDoc(doc(O, "matches", "L1__S1"), { ownerState: "maybe", reviewedAt: now(), updatedAt: now() })));
  await check("reporter can't answer for the owner", assertFails(updateDoc(doc(R, "matches", "L1__S1"), { ownerState: "not_mine" })));
  await check("owner can't change the AI's score", assertFails(updateDoc(doc(O, "matches", "L1__S1"), { tier: "strong", score: 1 })));
  await check("owner can't mark reunited without closing the report", assertFails(updateDoc(doc(O, "matches", "L1__S1"), { ownerState: "reunited" })));
  await check("reporter says it's a different animal", assertSucceeds(updateDoc(doc(R2, "matches", "L1__S2"), { reporterState: "not_same", updatedAt: now() })));
  await check("owner can't answer for the reporter", assertFails(updateDoc(doc(O, "matches", "L1__S2"), { reporterState: "confirmed" })));
  await check("worker re-scores a phone's match", assertSucceeds(updateDoc(doc(W, "matches", "L1__S2"), { tier: "weak", score: 0.3, visual: 0.4, method: "embedding", model: "m", source: "worker", updatedAt: now() })));
  await check("worker can't touch people's answers", assertFails(updateDoc(doc(W, "matches", "L1__S1"), { ownerState: "not_mine", updatedAt: now() })));
  await check("owner rejects a look-alike", assertSucceeds(updateDoc(doc(O, "matches", "L1__S2"), { ownerState: "not_mine", reviewedAt: now(), updatedAt: now() })));
  await check("a rejected pair can't be revived by the worker", assertFails(updateDoc(doc(W, "matches", "L1__S2"), { tier: "strong", score: 0.9, source: "worker", updatedAt: now() })));
  await check("member can't delete a match while both posts exist", assertFails(deleteDoc(doc(O, "matches", "L1__S2"))));
  await check("worker announces a match to the owner", assertSucceeds(setDoc(doc(W, "users", "owner1", "notifications", "pm_L1__S1"),
    { type: "match_found", params: { name: "Simba" }, route: "match/L1__S1", fromUid: "worker", read: false, createdAt: now() })));
  await check("worker can't send other alerts", assertFails(setDoc(doc(W, "users", "owner1", "notifications", "pm_L1__S3"),
    { type: "message", params: {}, route: "match/L1__S3", fromUid: "worker", read: false, createdAt: now() })));
  await check("worker alert must point at its match", assertFails(setDoc(doc(W, "users", "owner1", "notifications", "pm_L1__S3"),
    { type: "match_found", params: {}, route: "chat/x", fromUid: "worker", read: false, createdAt: now() })));
  await check("the same match alert can't be sent twice", assertFails(setDoc(doc(W, "users", "owner1", "notifications", "pm_L1__S1"),
    { type: "match_found", params: { name: "Simba" }, route: "match/L1__S1", fromUid: "worker", read: false, createdAt: now() })));
  const emb = { postId: "L1", ownerId: "owner1", animalType: "cat", model: "m", signature: "s", photoIds: ["img1"],
    vectors: [Bytes.fromUint8Array(new Uint8Array(2048))], detected: [true], animals: [1], lowQuality: [false], createdAt: serverTimestamp() };
  await check("worker stores photo embeddings", assertSucceeds(setDoc(doc(W, "postEmbeddings", "L1"), emb)));
  await check("members can't plant embeddings", assertFails(setDoc(doc(O, "postEmbeddings", "S9"), { ...emb, postId: "S9" })));
  await check("members can't read embeddings", assertFails(getDoc(doc(R, "postEmbeddings", "L1"))));
  await check("moderators can read embeddings", assertSucceeds(getDoc(doc(ctx("mod"), "postEmbeddings", "L1"))));
  await check("post owner removes their post's embeddings", assertSucceeds(deleteDoc(doc(O, "postEmbeddings", "L1"))));
  // Reunion: the report closes and the helping match is marked in one batch.
  await check("unknown resolution rejected", assertFails(updateDoc(doc(O, "posts", "L1"), { status: "reunited", resolution: "magic" })));
  {
    const b = writeBatch(O);
    b.update(doc(O, "posts", "L1"), { status: "reunited", resolution: "catmap", resolvedMatchId: "L1__S1", resolvedAt: now(), updatedAt: now() });
    b.update(doc(O, "matches", "L1__S1"), { ownerState: "reunited", reviewedAt: now(), updatedAt: now() });
    await check("owner marks reunited through the match", assertSucceeds(b.commit()));
  }
  await check("reunion can't be credited to another match", assertFails(updateDoc(doc(O, "matches", "L1__S3"), { ownerState: "reunited" })));
  await check("reporter can't close the owner's report", assertFails(updateDoc(doc(R, "posts", "L1"), { status: "open", resolution: "" })));
  await check("moderator records an independent check", assertSucceeds(updateDoc(doc(ctx("mod"), "matches", "L1__S1"), { verifiedBy: "mod", verifiedAt: now() })));
  await check("members can't mark their own reunion verified", assertFails(updateDoc(doc(O, "matches", "L1__S1"), { verifiedBy: "owner1", verifiedAt: now() })));
  await env.withSecurityRulesDisabled((c) => deleteDoc(doc(c.firestore(), "posts", "S2")));
  await check("match cleaned up after a post is deleted", assertSucceeds(deleteDoc(doc(O, "matches", "L1__S2"))));
}

console.log(`\n${passed} passed, ${failed} failed`);
await env.cleanup();
process.exit(failed ? 1 : 0);
