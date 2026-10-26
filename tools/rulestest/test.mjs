// Replays the app's Firestore writes/queries against the emulator to check firestore.rules.
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import {
  doc, setDoc, getDoc, getDocs, updateDoc, deleteDoc, writeBatch, collection, query, where, orderBy, limit,
  serverTimestamp, increment, arrayUnion, arrayRemove, Bytes, runTransaction, collectionGroup, getCountFromServer,
  documentId, startAfter, startAt, endAt,
} from "firebase/firestore";
import { readFileSync } from "node:fs";

const emulatorAddress = new URL(`http://${process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080"}`);
const storageHost = process.env.FIREBASE_STORAGE_EMULATOR_HOST;
const storageAddress = storageHost ? new URL(`http://${storageHost}`) : null;
if (!['localhost','127.0.0.1','[::1]'].includes(emulatorAddress.hostname)) throw new Error('Loopback emulator required');
if (storageAddress && !['localhost','127.0.0.1','[::1]'].includes(storageAddress.hostname)) throw new Error('Loopback Storage required');
const env = await initializeTestEnvironment({
  ...(storageAddress ? { storage: { host: storageAddress.hostname, port: Number(storageAddress.port),
    rules: readFileSync(new URL("../../storage.rules", import.meta.url), "utf8") } } : {}),
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
  // RULES_DEBUG=1 prints the emulator's whole reason (rule lines, "false for ... @ L123").
  catch (e) { failed++; console.log("  FAIL", name, "-", process.env.RULES_DEBUG ? e.message.replace(/\s+/g, " ").slice(0, 1500) : e.message.split("\n")[0]); }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const now = () => Date.now();
// A photo as the apps upload it: JPEG bytes (the rules check the JPEG header).
const jpegBytes = (n) => { const b = new Uint8Array(n); b.set([0xff, 0xd8, 0xff, 0xe0]); return Bytes.fromUint8Array(b); };

const ctx = (uid, verified = true) => env.authenticatedContext(uid, { email: `${uid}@x.test`, email_verified: verified }).firestore();
const anon = env.unauthenticatedContext().firestore();

const profile = (username) => ({
  username, displayName: username, bio: "", photoId: "", role: "user", volunteerStatus: "none",
  stats: {}, points: 0, neighborhoods: [], badges: [], followedCats: [], showStatsPublicly: true,
  lastPostAt: null, createdAt: now(),
});

async function register(uid, verified = false) {
  const db = ctx(uid, verified);
  // Shared actors appear in several sections; their canonical registration is created once.
  if ((await getDoc(doc(db, 'users', uid))).exists()) return;
  const b = writeBatch(db);
  b.set(doc(db, "usernames", uid), { uid });
  b.set(doc(db, "users", uid), profile(uid));
  b.set(doc(db, "userLimits", uid), {favorites:0,blocked:0,unreadCount:0});
  b.set(doc(db, "users", uid, "private", "account"), { email: `${uid}@x.test`, createdAt: now() });
  await b.commit();
  if(verified) await alertWrite(db, uid, 'welcome', {type:'welcome',params:{user:uid},route:'',fromUid:uid,read:false,createdAt:now(),sourceId:''});
}
const setRole = (uid, role) => env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), "users", uid), { role }));

const catDoc = (uid, counterSourceId = "") => ({
  counterSourceId,
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

async function alertWrite(db, recipient, id, data) {
  const value={sourceId:'',...data}; const b=writeBatch(db);
  b.set(doc(db,'users',recipient,'notifications',id),value);
  b.set(doc(db,'users',recipient,'notificationReceipts',id),{fromUid:value.fromUid,sourceId:value.sourceId,createdAt:value.createdAt});
  return b.commit();
}
async function uploadImage(db,id,data) {
  const rootId=id.replace(/_t$/,''),value={...data,rootId};const b=writeBatch(db);
  b.set(doc(db,'images',id),value);
  b.set(doc(db,'mediaUploads',rootId),{ownerId:value.ownerId,chatId:value.chatId || '',audience:value.audience || 'community',status:'staged',expiresAt:now()+7*86400000});
  return b.commit();
}
async function slotWrite(db, uid, bucket, id, value) {
  const b=writeBatch(db);const source=doc(db,'users',uid,bucket,id);
  if(value)b.set(source,value);else b.delete(source);
  b.update(doc(db,'userLimits',uid),{[bucket]:increment(value?1:-1),[bucket+'Source']:id});return b.commit();
}
async function resetChat(db,uid,id,anonymize=false) {
  const source=doc(db,'chats',id),data=(await getDoc(source)).data(),count=data.unread[uid] || 0,b=writeBatch(db);
  const changes={[`unread.${uid}`]:0};
  if(anonymize) {
    changes[`names.${uid}`]='';changes[`photos.${uid}`]='';
    if(data.lastSender===uid)Object.assign(changes,{lastText:'',lastSender:'',lastMessageId:''});
  }
  b.update(source,changes);
  if(count)b.update(doc(db,'userLimits',uid),{unreadCount:increment(-count),unreadChatId:id});
  await b.commit();
}
async function createCat(db,uid,id,extra={}) {
  const source=id+'_first',b=writeBatch(db);
  b.set(doc(db,'cats',id),{...catDoc(uid,source),...extra});
  b.set(doc(db,'sightings',source),sightingDoc(uid,id));
  b.update(doc(db,'users',uid),{lastPostAt:serverTimestamp()});return b.commit();
}
const imageDoc=(uid,id,data)=>({data,ownerId:uid,createdAt:now(),audience:'community',chatId:'',rootId:id.replace(/_t$/,''),messageId:''});
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
  await check("demo admin claim creates admin profile", assertSucceeds((async()=>{const b=writeBatch(DA);b.set(doc(DA,'usernames','demoadmin'),{uid:'demoadmin'});b.set(doc(DA,'users','demoadmin'),{...profile('demoadmin'),role:'admin'});await b.commit()})()));
  await check("demo user claim stays a user", assertSucceeds((async()=>{const b=writeBatch(DU);b.set(doc(DU,'usernames','demouser'),{uid:'demouser'});b.set(doc(DU,'users','demouser'),profile('demouser'));await b.commit()})()));
  await check("demo user cannot promote itself", assertFails(updateDoc(doc(DU, "users", "demouser"), { role: "admin" })));
  await env.withSecurityRulesDisabled((c) => updateDoc(doc(c.firestore(), "users", "demoadmin"), { role: "user" }));
  await check("demo admin role change cannot ride along other fields", assertFails(updateDoc(doc(DA, "users", "demoadmin"), { role: "admin", points: 999 })));
  await check("demo admin claim restores its admin role", assertSucceeds(updateDoc(doc(DA, "users", "demoadmin"), { role: "admin" })));
}

console.log("AI server jobs");
{
  for (const actor of ['carl', 'dave', 'erin', 'frank']) await register(actor);
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
  // Photo review (docs/MATCHING_REVIEW_V1.md): one photo plus 1-8 cat ids; the PC reads the cats' photos itself.
  for (const actor of ['gail', 'hugo']) await register(actor);
  const review = (uid, extra = {}) => job(uid, { kind: "review", images: [], photos: [], ids: ["cat1", "cat2"], labels: ["Tom", "Ash"], ...extra });
  await check("member creates a photo review job", assertSucceeds(setDoc(doc(ctx("gail"), "aiJobs", "gail"), review("gail"))));
  await check("photo review can't carry other photos", assertFails(setDoc(doc(ctx("hugo"), "aiJobs", "hugo"), review("hugo", { images: photos(1) }))));
  await check("photo review can't carry post photos", assertFails(setDoc(doc(ctx("hugo"), "aiJobs", "hugo"), review("hugo", { photos: photos(1) }))));
  await check("photo review is limited to 8 cats", assertFails(setDoc(doc(ctx("hugo"), "aiJobs", "hugo"), review("hugo", { ids: Array.from({ length: 9 }, (_, i) => `c${i}`) }))));
  await check("photo review needs at least one cat", assertFails(setDoc(doc(ctx("hugo"), "aiJobs", "hugo"), review("hugo", { ids: [] }))));
  const noQuery = review("hugo");
  delete noQuery.query;
  await check("photo review needs its photo", assertFails(setDoc(doc(ctx("hugo"), "aiJobs", "hugo"), noQuery)));
  await check("photo review photo is size-limited", assertFails(setDoc(doc(ctx("hugo"), "aiJobs", "hugo"), review("hugo", { query: Bytes.fromUint8Array(new Uint8Array(160000)) }))));
  await check("worker writes the photo review", assertSucceeds(updateDoc(doc(W, "aiJobs", "gail"), { status: "done", review: { protocol: "review-v1", space: "s", ranked: [{ id: "cat1", score: 0.31, photoIds: ["a", "b"] }], unavailable: [{ id: "cat2", reason: "too_few_photos" }] }, model: "review-candidate-v1", provider: "dml", ms: 900, doneAt: serverTimestamp(), answeredRequestId: "r1" })));
  await check("member cannot fake a photo review", assertFails(updateDoc(doc(ctx("dave"), "aiJobs", "dave"), { status: "done", review: { ranked: [] } })));
  await check("worker heartbeat lists photo review", assertSucceeds(setDoc(doc(W, "config", "aiWorker"), { online: true, lastSeen: serverTimestamp(), model: "m", provider: "cpu", same: 0.75, maybe: 0.6, capabilities: ["review-v1"] })));
  await check("heartbeat capabilities must be a short list", assertFails(setDoc(doc(W, "config", "aiWorker"), { online: true, lastSeen: serverTimestamp(), capabilities: "review-v1" })));
  await check("heartbeat refuses unknown keys", assertFails(setDoc(doc(W, "config", "aiWorker"), { online: true, lastSeen: serverTimestamp(), debug: true })));
}

console.log("Sightings");
const A = ctx("alice"), B = ctx("bob");
await check("unverified user cannot post", assertFails((async () => {
  const db = ctx("alice", false); const b = writeBatch(db);
  b.set(doc(db, "cats", "c0"), catDoc("alice", "s0")); b.set(doc(db, "sightings", "s0"), sightingDoc("alice", "c0"));
  userStats(b, db, "alice", { sightings: 1 }, 10, "deira", true); await b.commit();
})()));
await check("image upload", assertSucceeds(uploadImage(A,'img1', imageDoc("alice","img1",jpegBytes(1000)))));
await check("oversized image rejected", assertFails(uploadImage(A,'big', imageDoc("alice","big",jpegBytes(1_020_000)))));
await check("new cat + sighting + exact location + stats batch", assertSucceeds((async () => {
  const b = writeBatch(A);
  b.set(doc(A, "cats", "c1"), catDoc("alice", "s1")); b.set(doc(A, "sightings", "s1"), sightingDoc("alice", "c1"));
  b.set(doc(A, "privateLocations", "s1"), { ownerId: "alice", lat: 25.2712, lng: 55.3001, accuracy: 8 });
  userStats(b, A, "alice", { sightings: 1, photos: 1, catsRegistered: 1 }, 27, "deira", true); await b.commit();
})()));
await register("zed", true);
await check("sighting with too many match ids rejected", assertFails((async () => {
  const Z = ctx("zed"); const b = writeBatch(Z);
  b.set(doc(Z, "cats", "cz"), catDoc("zed", "sz")); b.set(doc(Z, "sightings", "sz"), { ...sightingDoc("zed", "cz"), rejectedCatIds: ["1", "2", "3", "4", "5", "6", "7"] });
  userStats(b, Z, "zed", { sightings: 1 }, 10, "deira", true); await b.commit();
})()));
await check("sighting with match feedback accepted", assertSucceeds((async () => {
  const Z = ctx("zed"); const b = writeBatch(Z);
  b.set(doc(Z, "cats", "cz"), catDoc("zed", "sz")); b.set(doc(Z, "sightings", "sz"), { ...sightingDoc("zed", "cz"), rejectedCatIds: ["c9"] });
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
  b.update(doc(B, "cats", "c1"), { counterSourceId: 's3', sightingCount: increment(1), contributors: arrayUnion("bob"), neighborhoods: arrayUnion("naif"), updatedAt: now(),
    tnrStatus: "ear_tipped", healthStatus: "healthy", photoIds: arrayUnion("img2"), lastSeen: now(), lat: 25.28, lng: 55.31, neighborhood: "naif", features: [0.2, 0.2] });
  b.set(doc(B, "sightings", "s3"), { ...sightingDoc("bob", "c1"), newCat: false });
  userStats(b, B, "bob", { sightings: 1 }, 10, "naif", true); await b.commit();
})()));
await check("follower notification from bob", assertSucceeds(alertWrite(B,'alice','followed_cat_s3_', {type:'followed_cat',sourceId:'s3',params:{cat:'Zafran',user:'bob',area:'deira'},route:'cat/c1',fromUid:'bob',read:false,createdAt:now()})));
await check("forged notification sender rejected", assertFails(alertWrite(B,"alice","n2", { type: "x", fromUid: "alice", read: false, createdAt: now() })));
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
  b.update(doc(B, "cats", "c1"), { sightingCount: increment(-1), counterSourceId: 's3', updatedAt: now() });
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
// Check-ins are rate limited like posts (both apps stamp lastPostAt), and alice just reported a rescue.
await check("check-in right after another post is rate limited", assertFails((async () => {
  const b = writeBatch(A);
  b.set(doc(A, "checkins", "ck0"), { stationId: "st1", stationName: "Station", userId: "alice", username: "alice", catsSeen: 1, catIds: [], foodLevel: "low", waterLevel: "full", note: "", at: now() });
  b.update(doc(A, "stations", "st1"), { counterSourceId: 'ck0', checkinCount: increment(1), lastCheckinAt: now(), lastCheckinBy: "alice" });
  userStats(b, A, "alice", { checkins: 1 }, 5, "al_barsha", true); await b.commit();
})()));
await sleep(11_000);
await check("check-in batch", assertSucceeds((async () => {
  const b = writeBatch(A);
  b.set(doc(A, "checkins", "ck1"), { stationId: "st1", stationName: "Station", userId: "alice", username: "alice", catsSeen: 3, catIds: [], foodLevel: "low", waterLevel: "full", note: "", at: now() });
  b.update(doc(A, "stations", "st1"), { counterSourceId: 'ck1', checkinCount: increment(1), lastCheckinAt: now(), lastCheckinBy: "alice" });
  userStats(b, A, "alice", { checkins: 1, iftarCheckins: 0 }, 5, "al_barsha", true); await b.commit();
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
await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'cats','c2'),{...catDoc('bob'),name:'Ginger'}));
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
await check("mark read", assertSucceeds(updateDoc(doc(A, "users", "alice", "notifications", "followed_cat_s3_"), { read: true })));
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
  await uploadImage(A,'delete-photo', imageDoc("alice","delete-photo",jpegBytes(100)));
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
    tx.update(catRef, { sightingCount: Math.max(0, cat.data().sightingCount - 1), counterSourceId: 's1' });
  });
  if ((await getDoc(catRef)).data().sightingCount !== Math.max(0, before - 1)) throw Error("count changed twice");
})()));
await check("station check-in deletion updates count and last author", assertSucceeds((async () => {
  const checkinRef = doc(A, "checkins", "ck1"), stationRef = doc(A, "stations", "st1");
  await runTransaction(A, async (tx) => {
    const source = await tx.get(checkinRef), station = await tx.get(stationRef);
    tx.delete(source.ref); tx.update(station.ref, { counterSourceId: 'ck1', checkinCount: Math.max(0, station.data().checkinCount - 1), lastCheckinAt: 0, lastCheckinBy: "" });
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
// Later sections exercise this actor again with an unrelated synthetic profile.
await env.withSecurityRulesDisabled(async c=>{await setDoc(doc(c.firestore(),'users','alice'),profile('alice'));await setDoc(doc(c.firestore(),'usernames','alice'),{uid:'alice'});});

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
  b.update(doc(B, "posts", "p1"), { counterSourceId:'u1', updatesCount: increment(1), updatedAt: now() });
  userStats(b, B, "bob", { postUpdates: 1 }, 5, null, true);
  await b.commit();
})()));
await check("counter cannot jump by more than one", assertFails(updateDoc(doc(D, "posts", "p1"), { updatesCount: increment(5), updatedAt: now() })));
await check("post owner removes an update and the counter", assertSucceeds((async () => {
  const b = writeBatch(C);
  b.delete(doc(C, "posts", "p1", "updates", "u1"));
  b.update(doc(C, "posts", "p1"), { updatesCount: increment(-1), counterSourceId:'u1' });
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
await check("ratings outside 1ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“5 are refused", assertFails(reviewTx(B, "bob", 9)));
await check("owner reviews the completed booking (review + rating + flag)", assertSucceeds(reviewTx(B, "bob", 5)));
await check("a booking can only be reviewed once", assertFails(reviewTx(B, "bob", 4)));
await check("sitter rating reflects exactly one review", assertSucceeds((async () => {
  const s = (await getDoc(doc(B, "sitters", "carol"))).data();
  if (s.ratingCount !== 1 || s.ratingSum !== 5) throw Error(`got ${s.ratingSum}/${s.ratingCount}`);
})()));

console.log("Messages");
const chatDoc = (a, b) => ({ lastMessageId:'', lastDeletedMessageId:'', members: [a, b], names: { [a]: a, [b]: b }, photos: { [a]: "", [b]: "" }, lastText: "", lastSender: "", lastAt: now(), unread: { [a]: 0, [b]: 0 } });
await check("checking whether a chat exists is allowed", assertSucceeds(getDoc(doc(B, "chats", "bob_carol"))));
await check("member starts a chat", assertSucceeds(setDoc(doc(B, "chats", "bob_carol"), chatDoc("bob", "carol"))));
await check("chat id must be the sorted member ids", assertFails(setDoc(doc(B, "chats", "carol_bob"), chatDoc("bob", "carol"))));
await check("cannot create a chat between two other people", assertFails(setDoc(doc(D, "chats", "carol_erin"), chatDoc("carol", "erin"))));
await check("outsiders cannot read a chat", assertFails(getDoc(doc(D, "chats", "bob_carol"))));
const send = (db, uid, other, id) => (async () => {
  const b = writeBatch(db), at=now(), text="Hi! Is Luna still available?";
  b.set(doc(db, "chats", "bob_carol", "messages", id), { senderId: uid, text, imageId: "", postId: "a1", postTitle: "Luna", postPhotoId: "", createdAt: at });
  b.update(doc(db, "chats", "bob_carol"), { lastMessageId:id,lastText:text,lastSender:uid,lastAt:at,[`unread.${other}`]:increment(1) });
  b.update(doc(db,'userLimits',other),{unreadCount:increment(1),unreadChatId:'bob_carol',unreadMessageId:id});
  await b.commit();
})();
await check("member sends a message", assertSucceeds(send(B, "bob", "carol", "m1")));
await check("outsiders cannot post into a chat", assertFails(send(D, "dave", "carol", "m2")));
await check("outsiders cannot read messages", assertFails(getDocs(collection(D, "chats", "bob_carol", "messages"))));
await check("members list their chats", assertSucceeds(getDocs(query(collection(C, "chats"), where("members", "array-contains", "carol")))));
await check("chat members cannot be changed", assertFails(updateDoc(doc(B, "chats", "bob_carol"), { members: ["bob", "dave"] })));
await check("reader clears their unread count", assertSucceeds(resetChat(C,'carol','bob_carol')));
await check("person blocks a sender", assertSucceeds(slotWrite(C,'carol','blocked','bob',{at:now()})));
await check("blocked sender cannot message them", assertFails(send(B, "bob", "carol", "m3")));
await check("block list is private", assertFails(getDoc(doc(B, "users", "carol", "blocked", "bob"))));
await check("unblock", assertSucceeds(slotWrite(C,'carol','blocked','bob',null)));
await check("messages flow again after unblocking", assertSucceeds(send(B, "bob", "carol", "m4")));
await check("sender deletes their own message", assertSucceeds(deleteDoc(doc(B, "chats", "bob_carol", "messages", "m4"))));
await check("cannot delete the other person's message", assertFails(deleteDoc(doc(C, "chats", "bob_carol", "messages", "m1"))));

console.log("Alerts, favourites and usernames");
const alert = (from) => ({ type: "message",sourceId:'m1', params: { user: from, text:'Hi! Is Luna still available?' }, route: "chat/bob_carol", fromUid: from, read: false, createdAt: now() });
await check("first alert with a fixed id is delivered", assertSucceeds(alertWrite(B,'carol','msg_bob_carol_m1',alert('bob'))));
await check("a repeat alert with the same id is refused (no spam)", assertFails(alertWrite(B,"carol","msg_bob_carol_m1", alert("bob"))));
await check("recipient dismisses the alert", assertSucceeds(deleteDoc(doc(C, "users", "carol", "notifications", "msg_bob_carol_m1"))));
await check("alerts cannot be sent in someone else's name", assertFails(alertWrite(B,"carol","fake", alert("dave"))));
await check("save a favourite", assertSucceeds(slotWrite(B,'bob','favorites','a1',{postId:'a1',savedAt:now()})));
await check("favourites are private", assertFails(getDocs(collection(C, "users", "bob", "favorites"))));
await check("follow an area for lost-pet alerts", assertSucceeds(setDoc(doc(C, "areaFollows", "carol"), { neighborhoods: ["jlt", "dubai_marina"], updatedAt: now() })));
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
// Each number's [0, 1] range is checked by the readers (PostAppearance.valid, fingerprint.valid); the rules keep the
// type and a bound on the sum, which is what keeps every reader able to load the feed (26 Oct 2026, rules budget).
await check("fingerprint adding up to more than 27 rejected", assertFails(updateDoc(doc(C,"posts","p1"),{fingerprint:Array(27).fill(2)})));
await check("negative fingerprint rejected", assertFails(updateDoc(doc(C,"posts","p1"),{fingerprint:Array(27).fill(-1)})));
await check("non-numeric fingerprint rejected", assertFails(updateDoc(doc(C,"posts","p1"),{fingerprint:[...fingerprint.slice(0,26),"invalid"]})));
await check("non-finite fingerprint rejected", assertFails(updateDoc(doc(C,"posts","p1"),{fingerprint:[NaN,...fingerprint.slice(1)]})));
await check("foreign fingerprint edit rejected", assertFails(updateDoc(doc(B,"posts","p1"),{fingerprint})));
for(const microchip of ["yes","no","unknown"]) await check(`microchip ${microchip} accepted`,assertSucceeds(updateDoc(doc(C,"posts","p1"),{microchip})));
await check("public chip number rejected",assertFails(updateDoc(doc(C,"posts","p1"),{microchipNumber:"123456789"})));
await check("chip enum cannot contain a number",assertFails(updateDoc(doc(C,"posts","p1"),{microchip:"123456789"})));
await check("foreign microchip edit rejected",assertFails(updateDoc(doc(B,"posts","p1"),{microchip:"yes"})));
const staleTime=now()-15*86400000,staleBucket=Math.floor(now()/(14*86400000)),staleId='stale_p1_'+staleBucket;
await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'posts','p1'),{createdAt:staleTime,updatedAt:staleTime,lastActivityAt:staleTime}));
const stale={type:'stale_post',sourceId:'p1',params:{name:'Rex'},route:'post/p1',fromUid:'carol',read:false,createdAt:now()};
await check("one reminder per deterministic window",assertSucceeds(alertWrite(C,'carol',staleId,stale)));
await check("same reminder cannot refresh its timestamp",assertFails(alertWrite(C,"carol",staleId,{...stale,createdAt:stale.createdAt+1})));
await check("reminder can be marked read",assertSucceeds(updateDoc(doc(C,"users","carol","notifications",staleId),{read:true})));
await check("recipient cannot forge reminder content",assertFails(updateDoc(doc(C,"users","carol","notifications",staleId),{params:{name:"changed"}})));
await check("future reminder window refused",assertFails(alertWrite(C,'carol','stale_p1_'+(staleBucket+1),stale)));
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
  tx.delete(source.ref);tx.update(parent,{updatesCount:Math.max(0,post.data().updatesCount-1),counterSourceId:'own-export'});
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
  await check("worker announces a match to the owner", assertSucceeds(alertWrite(W,"owner1","pm_L1__S1",
    { type: "match_found", sourceId:"L1__S1", params: { name: "Simba" }, route: "match/L1__S1", fromUid: "worker", read: false, createdAt: now() })));
  await check("worker can't send other alerts", assertFails(alertWrite(W,"owner1","pm_L1__S3",
    { type: "message", params: {}, route: "match/L1__S3", fromUid: "worker", read: false, createdAt: now() })));
  await check("worker alert must point at its match", assertFails(alertWrite(W,"owner1","pm_L1__S3",
    { type: "match_found", params: {}, route: "chat/x", fromUid: "worker", read: false, createdAt: now() })));
  await check("the same match alert can't be sent twice", assertFails(alertWrite(W,"owner1","pm_L1__S1",
    { type: "match_found", sourceId:"L1__S1", params: { name: "Simba" }, route: "match/L1__S1", fromUid: "worker", read: false, createdAt: now() })));
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

// ---------- Phase 2 hardening (docs/security/THREAT_MODEL.md S1-S13) ----------
// Every attack from tools/rulestest/phase1-findings.mjs must now be refused, and the heaviest writes the app really
// makes must still fit inside the rules' 1,000-step budget.
console.log("Phase 2 hardening");
{
  const asOwner = (fn) => env.withSecurityRulesDisabled((c) => fn(c.firestore()));
  for (const u of ["h_sus", "h_del", "h_att", "h_vic", "h_bot", "h_big", "h_imp", "h_fut", "h_heavy", "h_badge"]) await register(u, u !== "h_bot");
  const ATT = ctx("h_att"), BOT = ctx("h_bot", false), SUS = ctx("h_sus");

  // S1: a suspended member can't wipe the suspension by deleting and re-creating their profile.
  await asOwner((db) => updateDoc(doc(db, "users", "h_sus"), { suspended: true }));
  await check("suspended member cannot delete their profile", assertFails(deleteDoc(doc(SUS, "users", "h_sus"))));
  await check("a member who isn't suspended can still delete their profile", assertSucceeds(deleteDoc(doc(ctx("h_del"), "users", "h_del"))));

  // S8: reputation can't be self-awarded.
  await check("member cannot set their own points", assertFails(updateDoc(doc(ATT, "users", "h_att"), { points: 99999 })));
  await check("member cannot jump a counter", assertFails(updateDoc(doc(ATT, "users", "h_att"), { "stats.sightings": increment(500) })));
  await check("member cannot add a badge they haven't earned", assertFails(updateDoc(doc(ATT, "users", "h_att"), { badges: arrayUnion("tnr_champion") })));
  await check("member cannot add several areas at once (badge farming)", assertFails(updateDoc(doc(ATT, "users", "h_att"), { neighborhoods: ["a", "b", "c", "d"] })));
  await asOwner((db) => updateDoc(doc(db, "users", "h_badge"), { "stats.sightings": 1 }));
  await check("member adds a badge their counters earned", assertSucceeds(updateDoc(doc(ctx("h_badge"), "users", "h_badge"), { badges: arrayUnion("first_whiskers") })));

  // S4: notifications into other people's inboxes.
  const note = (extra = {}) => ({ type: "message", params: { user: "h_att", text: "hi" }, route: "chat/h_att_h_vic", fromUid: "h_att", read: false, createdAt: now(), ...extra });
  await check("legacy unbound write refused: member sends a message alert", assertFails(alertWrite(ATT,"h_vic","n1", note())));
  await check("member cannot send a moderation alert", assertFails(alertWrite(ATT,"h_vic","n2", note({ type: "moderation", params: { target: "account", decision: "suspended" }, route: "profile" }))));
  await check("member cannot send a welcome/achievement alert to others", assertFails(alertWrite(ATT,"h_vic","n3", note({ type: "achievement", params: { badge: "x" }, route: "achievements" }))));
  await check("alert with an unknown route rejected", assertFails(alertWrite(ATT,"h_vic","n4", note({ route: "https://evil.example" }))));
  await check("alert with extra fields rejected", assertFails(alertWrite(ATT,"h_vic","n5", note({ title: "Free prize" }))));
  await check("alert with an oversized parameter rejected", assertFails(alertWrite(ATT,"h_vic","n6", note({ params: { user: "x", text: "y".repeat(400) } }))));
  await check("alert dated in the future rejected", assertFails(alertWrite(ATT,"h_vic","n7", note({ createdAt: now() + 3 * 3600000 }))));
  await check("moderator sends a moderation alert", assertSucceeds(alertWrite(M,'h_vic','n8',{ ...note({type:'moderation',params:{target:'post',label:'Rex',decision:'removed'},route:''}),fromUid:'mod'})));

  // S7: someone else's cat.
  await asOwner((db) => setDoc(doc(db, "cats", "hc1"), { ...catDoc("h_vic"), followers: ["h_vic"], contributors: ["h_vic"], photoIds: ["v1"], coverPhotoId: "v1", sightingCount: 4, features: Array(117).fill(0.5) }));
  await check("member cannot move a cat without a sighting", assertFails(updateDoc(doc(ATT, "cats", "hc1"), { lat: 24.0, lng: 54.0, updatedAt: now() })));
  await check("member cannot replace a cat's photos", assertFails(updateDoc(doc(ATT, "cats", "hc1"), { photoIds: ["spam"], coverPhotoId: "spam" })));
  await check("member cannot remove other followers", assertFails(updateDoc(doc(ATT, "cats", "hc1"), { followers: [] })));
  await check("member cannot reset the sighting count", assertFails(updateDoc(doc(ATT, "cats", "hc1"), { sightingCount: 0 })));
  await check("member follows and unfollows a cat", assertSucceeds((async () => {
    await updateDoc(doc(ATT, "cats", "hc1"), { followers: arrayUnion("h_att") });
    await updateDoc(doc(ATT, "cats", "hc1"), { followers: arrayRemove("h_att") });
  })()));

  // Heaviest real sighting of an existing cat: 6 photos, full 117-number features, new area, plus the stats batch.
  await check("heaviest sighting batch on someone else's cat fits the rules budget", assertSucceeds((async () => {
    const H = ctx("h_heavy"); const b = writeBatch(H);
    b.set(doc(H, "sightings", "hs1"), { ...sightingDoc("h_heavy", "hc1"), photoIds: ["p1", "p2", "p3", "p4", "p5", "p6"], notes: "n".repeat(500), healthTags: ["thin", "limping"], behaviorTags: ["friendly"] });
    b.update(doc(H, "cats", "hc1"), {
      counterSourceId:'hs1', sightingCount: increment(1), contributors: arrayUnion("h_heavy"), neighborhoods: arrayUnion("jlt"), updatedAt: now(),
      tnrStatus: "ear_tipped", healthStatus: "needs_care", photoIds: arrayUnion("p1", "p2", "p3", "p4", "p5", "p6"),
      lastSeen: now(), lat: 25.07, lng: 55.14, neighborhood: "jlt", features: Array(117).fill(0.4),
      geoCells: ["thrq", "thrq5", "thrq5x"],
    });
    b.update(doc(H, "users", "h_heavy"), {
      "stats.sightings": increment(1), "stats.photos": increment(6), "stats.tnrReports": increment(1), "stats.nightSightings": increment(1),
      "stats.souqSightings": increment(1), "stats.marinaSightings": increment(1), points: increment(22), neighborhoods: arrayUnion("jlt"),
      lastPostAt: serverTimestamp(),
    });
    await b.commit();
  })()));

  // S9: posts.
  const maxPost = (uid) => ({ ...postDoc(uid), name: "N".repeat(60), description: "D".repeat(2000), color: "c".repeat(60), breed: "b".repeat(60),
    features: "f".repeat(400), contactPhone: "1".repeat(30), contactEmail: "e".repeat(70) + "@x.test", health: "h".repeat(400),
    photoIds: ["1", "2", "3", "4", "5", "6"], keywords: Array.from({ length: 80 }, (_, i) => "word" + i), fingerprint: Array(27).fill(0.5),
    lastActivityAt: now(), microchip: "unknown", resolution: "", resolvedMatchId: "", geoCells: ["thrq", "thrq5", "thrq5x"] });
  await check("largest possible real post fits the rules budget", assertSucceeds(createPost(ctx("h_big"), "h_big", "hp_big", maxPost("h_big"))));
  await check("post dated in the future rejected", assertFails(createPost(ctx("h_fut"), "h_fut", "hp_fut", { createdAt: now() + 365 * 86400000 })));
  await check("post under another person's name rejected", assertFails(createPost(ctx("h_imp"), "h_imp", "hp_imp", { ownerName: "CatMap Team" })));
  await check("post with a 900 KB field rejected", assertFails(createPost(ctx("h_imp"), "h_imp", "hp_imp2", { features: "x".repeat(900_000) })));
  await check("owner edits the fingerprint of the largest post", assertSucceeds(updateDoc(doc(ctx("h_big"), "posts", "hp_big"), { fingerprint: Array(27).fill(0.25), description: "E".repeat(2000), updatedAt: now() })));
  await check("owner edits fingerprint, text and place of the largest post", assertSucceeds(updateDoc(doc(ctx("h_big"), "posts", "hp_big"), { fingerprint: Array(27).fill(0.3), description: "F".repeat(2000), lat: 25.2, lng: 55.27, neighborhood: "downtown", geoCells: ["thrr", "thrr3", "thrr3s"], keywords: Array.from({ length: 80 }, (_, i) => "w" + i), updatedAt: now() })));
  await check("update counter cannot be pumped without an update", assertFails(updateDoc(doc(ATT, "posts", "hp_big"), { updatesCount: 1, updatedAt: now() })));

  // S3: reads need a verified email; photos can't be paged through.
  await check("unverified account reads its own profile", assertSucceeds(getDoc(doc(BOT, "users", "h_bot"))));
  await check("unverified account cannot read other profiles", assertFails(getDoc(doc(BOT, "users", "h_vic"))));
  await check("unverified account cannot list posts", assertFails(getDocs(query(collection(BOT, "posts"), where("hidden", "==", false)))));
  await check("unverified account cannot list cats", assertFails(getDocs(collection(BOT, "cats"))));
  await check("unverified account cannot list photos", assertFails(getDocs(collection(BOT, "images"))));
  await check("verified member cannot list every photo", assertFails(getDocs(collection(ATT, "images"))));
  await check("member lists their own photos (account deletion)", assertSucceeds(getDocs(query(collection(A, "images"), where("ownerId", "==", "alice")))));
  await check("verified member still opens a photo by id", assertSucceeds(getDoc(doc(ATT, "images", "img1"))));

  // S13: rescue history only grows; volunteers take cases on themselves only.
  await asOwner((db) => setDoc(doc(db, "rescues", "hr1"), { reporterId: "h_att", status: "approved", type: "injured", neighborhood: "deira",
    updates: [{ status: "approved", note: "Vet visit booked", byUid: "mod", byName: "mod", at: now() }], updatedAt: now(), createdAt: now() }));
  await check("reporter cannot rewrite the moderator's note", assertFails(updateDoc(doc(ATT, "rescues", "hr1"), { updates: [{ status: "approved", note: "Nothing to do", byUid: "mod", byName: "mod", at: now() }], updatedAt: now() })));
  await check("reporter appends their own note", assertSucceeds(updateDoc(doc(ATT, "rescues", "hr1"), { updates: arrayUnion({ status: "approved", note: "Still there", byUid: "h_att", byName: "h_att", at: now() }), updatedAt: now() })));
  await check("volunteer cannot assign a case to someone else", assertFails(updateDoc(doc(ctx("vol"), "rescues", "hr1"), { status: "in_progress", assignedTo: "h_vic", assignedName: "h_vic", updates: arrayUnion({ status: "in_progress", note: "", byUid: "vol", byName: "vol", at: now() }), updatedAt: now() })));
}

// ---------- Phase 3 cost changes: the cheaper queries must be allowed exactly where the old ones were ----------
console.log("Phase 3 cost changes");
{
  const WK = env.authenticatedContext("worker", { email: "ai-worker@demo.catmap.test", email_verified: true, catmapAiWorker: true }).firestore();
  await check("moderator counts matches by strength (admin analytics)", assertSucceeds(getCountFromServer(query(collection(M, "matches"), where("tier", "==", "strong")))));
  await check("moderator counts verified reunions", assertSucceeds(getCountFromServer(query(collection(M, "matches"), where("ownerState", "==", "reunited"), where("verifiedBy", ">", "")))));
  await check("members can't count everyone's matches", assertFails(getCountFromServer(query(collection(A, "matches"), where("tier", "==", "strong")))));
  await check("background check asks only for alerts newer than its last run", assertSucceeds(getDocs(query(collection(A, "users", "alice", "notifications"), where("createdAt", ">", now() - 3600000), orderBy("createdAt", "desc"), limit(50)))));
  await check("nobody reads someone else's recent alerts", assertFails(getDocs(query(collection(B, "users", "alice", "notifications"), where("createdAt", ">", now() - 3600000), orderBy("createdAt", "desc"), limit(50)))));
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "aiJobs", "alice"), { uid: "alice", kind: "check", status: "done", images: [], ids: [], requestId: "r9", createdAt: now(), doneAt: new Date(now() - 3600000) }));
  await check("worker lists answered jobs (doneAt only) for the sweep", assertSucceeds(getDocs(query(collection(WK, "aiJobs"), where("status", "in", ["done", "error"])))));
  await check("worker deletes an old answered job", assertSucceeds(deleteDoc(doc(WK, "aiJobs", "alice"))));
  await check("members can't list other people's jobs", assertFails(getDocs(query(collection(A, "aiJobs"), where("status", "in", ["done", "error"])))));
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "aiJobs", "bob"), { uid: "bob", kind: "check", status: "done", images: [], ids: [], requestId: "r8", createdAt: now() }));
  await check("a phone deletes its own job once answered", assertSucceeds(deleteDoc(doc(B, "aiJobs", "bob"))));
}

// ---------- Phase 4: the map asks for one area at a time (data/MapRepository.kt) ----------
console.log("Phase 4 map areas");
{
  const WK = env.authenticatedContext("worker", { email: "ai-worker@demo.catmap.test", email_verified: true, catmapAiWorker: true }).firestore();
  const G = ["thrr", "thrr3", "thrr3s"];
  for (const u of ["g_a", "g_b", "g_c", "g_d"]) await register(u);
  const GA = ctx("g_a"), GB = ctx("g_b"), GC = ctx("g_c"), GD = ctx("g_d");
  await check("post with map-area codes accepted", assertSucceeds(createPost(GA, "g_a", "gp1", { geoCells: G })));
  await check("post with four area codes rejected", assertFails(createPost(GB, "g_b", "gpx", { geoCells: [...G, "thrr3sx"] })));
  await check("post with an over-long area code rejected", assertFails(createPost(GB, "g_b", "gpx", { geoCells: ["thrr", "thrr3", "thrr3sxxxxxxxx"] })));
  await check("post with numbers as area codes rejected", assertFails(createPost(GB, "g_b", "gpx", { geoCells: [1, 2, 3] })));
  await check("post with numbers shaped like area codes rejected", assertFails(createPost(GB, "g_b", "gpx", { geoCells: [1234, 12345, 123456] })));
  await check("post with letters geohashes never use rejected", assertFails(createPost(GB, "g_b", "gpx", { geoCells: ["thrr", "thrr3", "thrr3a"] })));
  await check("post without area codes still accepted (v0.0.9)", assertSucceeds(createPost(GB, "g_b", "gp2")));
  await check("owner moves a post with new area codes", assertSucceeds(updateDoc(doc(GA, "posts", "gp1"), { lat: 25.07, lng: 55.14, geoCells: ["thrq", "thrq5", "thrq5x"], updatedAt: now() })));
  await check("owner cannot save bad area codes", assertFails(updateDoc(doc(GA, "posts", "gp1"), { geoCells: "thrq", updatedAt: now() })));
  await check("worker fills in the area codes of an old post", assertSucceeds(updateDoc(doc(WK, "posts", "gp2"), { geoCells: ["thrq", "thrq5", "thrq5x"] })));
  await check("worker cannot change anything else on a post", assertFails(updateDoc(doc(WK, "posts", "gp2"), { geoCells: G, name: "Spam" })));
  await check("worker cannot save bad area codes", assertFails(updateDoc(doc(WK, "posts", "gp2"), { geoCells: [...G, "x"] })));
  await check("member cannot move someone else's post between areas", assertFails(updateDoc(doc(GC, "posts", "gp2"), { geoCells: G })));

  await check("cat profile with area codes accepted", assertSucceeds(createCat(GC,'g_c','gc1',{geoCells:G})));
  await check("cat profile with bad area codes rejected", assertFails(setDoc(doc(GC, "cats", "gc2"), { ...catDoc("g_c"), geoCells: [...G, "thrr3sx"] })));
  await check("member cannot move someone else's cat between areas", assertFails(updateDoc(doc(GD, "cats", "gc1"), { geoCells: ["thrq", "thrq5", "thrq5x"], updatedAt: now() })));
  await check("worker fills in a cat's area codes", assertSucceeds(updateDoc(doc(WK, "cats", "gc1"), { geoCells: ["thrq", "thrq5", "thrq5x"] })));
  await check("worker cannot rename a cat", assertFails(updateDoc(doc(WK, "cats", "gc1"), { geoCells: G, name: "Spam" })));

  const area = (db) => query(collection(db, "posts"), where("hidden", "==", false), where("geoCells", "array-contains", "thrr3"), orderBy("updatedAt", "desc"), limit(150));
  await check("member asks for the posts of one map area", assertSucceeds(getDocs(area(GD))));
  await check("member asks for the cats of one map area", assertSucceeds(getDocs(query(collection(GD, "cats"), where("geoCells", "array-contains", "thrr3"), orderBy("updatedAt", "desc"), limit(150)))));
  await check("unverified account cannot ask for a map area", assertFails(getDocs(area(ctx("g_d", false)))));
  await check("member asks for posts changed since the last sync", assertSucceeds(getDocs(query(collection(GD, "posts"), where("hidden", "==", false), where("updatedAt", ">", now() - 3600000), orderBy("updatedAt", "desc"), limit(300)))));
  await check("changes query must leave hidden posts out", assertFails(getDocs(query(collection(GD, "posts"), where("updatedAt", ">", now() - 3600000), orderBy("updatedAt", "desc"), limit(300)))));
  await check("member asks for cats changed since the last sync", assertSucceeds(getDocs(query(collection(GD, "cats"), where("updatedAt", ">", now() - 3600000), orderBy("updatedAt", "desc"), limit(300)))));
}

// ---------- Phase 5: search words and paged lists (data/SearchTerms.kt, data/Pager.kt) ----------
console.log("Phase 5 search and paging");
{
  const WK = env.authenticatedContext("worker", { email: "ai-worker@demo.catmap.test", email_verified: true, catmapAiWorker: true }).firestore();
  const T = ["simba", "orange", "sim", "simb", "ora", "oran", "orang"];
  for (const u of ["s_a", "s_b", "s_c"]) await register(u);
  const SA = ctx("s_a"), SB = ctx("s_b"), SC = ctx("s_c");
  await check("post with search words accepted", assertSucceeds(createPost(SA, "s_a", "sp1", { searchTerms: T })));
  await check("post with 201 search words rejected", assertFails(createPost(SB, "s_b", "spx", { searchTerms: Array.from({ length: 201 }, (_, i) => "w" + i) })));
  await check("post with oversized search words rejected", assertFails(createPost(SB, "s_b", "spx", { searchTerms: ["x".repeat(4001)] })));
  await check("post with search words as text rejected", assertFails(createPost(SB, "s_b", "spx", { searchTerms: "simba" })));
  await check("post without search words still accepted (v0.0.9)", assertSucceeds(createPost(SB, "s_b", "sp2")));
  await check("owner edits a post with new search words", assertSucceeds(updateDoc(doc(SA, "posts", "sp1"), { name: "Leo", keywords: ["leo"], searchTerms: ["leo"], updatedAt: now() })));
  await check("worker fills in search words of an old post", assertSucceeds(updateDoc(doc(WK, "posts", "sp2"), { searchTerms: ["rex"], geoCells: ["thrq", "thrq5", "thrq5x"] })));
  await check("worker cannot change a post's text with search words", assertFails(updateDoc(doc(WK, "posts", "sp2"), { searchTerms: ["spam"], description: "Spam" })));
  await check("member cannot change someone else's search words", assertFails(updateDoc(doc(SC, "posts", "sp2"), { searchTerms: ["spam"] })));
  await check("cat profile with search words accepted", assertSucceeds(createCat(SC,'s_c','sc1',{searchTerms:['zafran','zaf','zafr','zafra']})));
  await check("member adds an alias with its search words", assertSucceeds(updateDoc(doc(SB, "cats", "sc1"), { aliases: arrayUnion("Ginger"), searchTerms: ["zafran", "ginger", "zaf", "zafr", "zafra", "gin", "ging", "ginge"], updatedAt: now() })));
  await check("cat with 201 search words rejected", assertFails(updateDoc(doc(SC, "cats", "sc1"), { searchTerms: Array.from({ length: 201 }, (_, i) => "w" + i), updatedAt: now() })));
  await check("worker fills in a cat's search words", assertSucceeds(updateDoc(doc(WK, "cats", "sc1"), { searchTerms: ["zafran"] })));

  // Queries the app now runs instead of "the newest 400 posts" and "the newest 800 cats".
  await check("member searches posts by word", assertSucceeds(getDocs(query(collection(SB, "posts"), where("hidden", "==", false), where("searchTerms", "array-contains-any", ["simba", "simb"]), orderBy("createdAt", "desc"), limit(25)))));
  await check("search must leave hidden posts out", assertFails(getDocs(query(collection(SB, "posts"), where("searchTerms", "array-contains-any", ["simba"]), orderBy("createdAt", "desc"), limit(25)))));
  await check("member pages through some categories", assertSucceeds(getDocs(query(collection(SB, "posts"), where("hidden", "==", false), where("category", "in", ["lost", "found"]), orderBy("createdAt", "desc"), limit(25)))));
  await check("member reads a saved post that was since deleted (favourites read one by one)", assertSucceeds(getDoc(doc(SB, "posts", "sp_deleted"))));
  await check("member reads followed cats by id", assertSucceeds(getDocs(query(collection(SB, "cats"), where(documentId(), "in", ["sc1", "sc_gone"])))));
  await check("member searches cats by name", assertSucceeds(getDocs(query(collection(SB, "cats"), where("searchTerms", "array-contains-any", ["zafran"]), orderBy("lastSeen", "desc"), limit(25)))));
  await check("member lists one neighbourhood's cats", assertSucceeds(getDocs(query(collection(SB, "cats"), where("neighborhoods", "array-contains", "deira"), orderBy("lastSeen", "desc"), limit(25)))));
  await check("unverified account cannot search", assertFails(getDocs(query(collection(ctx("s_b", false), "posts"), where("hidden", "==", false), where("searchTerms", "array-contains-any", ["simba"]), limit(25)))));
  await check("moderator pages through hidden posts", assertSucceeds(getDocs(query(collection(M, "posts"), where("hidden", "==", true), orderBy("createdAt", "desc"), limit(25)))));
  await check("members cannot list hidden posts", assertFails(getDocs(query(collection(SB, "posts"), where("hidden", "==", true), orderBy("createdAt", "desc"), limit(25)))));
  await check("admin looks members up by username prefix", assertSucceeds(getDocs(query(collection(AD, "users"), orderBy("username"), startAt("s_"), endAt("s_ÃƒÂ¯Ã‚Â£Ã‚Â¿"), limit(25)))));
  await check("moderator pages through the moderation log", assertSucceeds(getDocs(query(collection(M, "modActions"), orderBy("at", "desc"), limit(40)))));
  await check("members cannot read the moderation log", assertFails(getDocs(query(collection(SB, "modActions"), orderBy("at", "desc"), limit(40)))));
  await check("member reads their older alerts", assertSucceeds(getDocs(query(collection(SA, "users", "s_a", "notifications"), orderBy("createdAt", "desc"), startAfter(now()), limit(25)))));
  await check("nobody reads someone else's older alerts", assertFails(getDocs(query(collection(SB, "users", "s_a", "notifications"), orderBy("createdAt", "desc"), startAfter(now()), limit(25)))));
  await check("member reads older updates of a post", assertSucceeds(getDocs(query(collection(SB, "posts", "sp1", "updates"), orderBy("createdAt", "desc"), startAfter(now()), limit(25)))));
}

// ---------- Phase 6: photos and privacy ----------
console.log("Phase 6 photos and privacy");
{
  for (const u of ["x_a", "x_b"]) await register(u);
  const XA = ctx("x_a"), XB = ctx("x_b");
  const jpeg = (n) => { const b = new Uint8Array(n); b.set([0xff, 0xd8, 0xff, 0xe0]); return Bytes.fromUint8Array(b); };
  const png = (n) => { const b = new Uint8Array(n); b.set([0x89, 0x50, 0x4e, 0x47]); return Bytes.fromUint8Array(b); };
  const img = (data, extra = {}) => ({ ...imageDoc('x_a','xi1',data), ...extra });
  await check("largest allowed photo (just under 1 MB) uploads", assertSucceeds(uploadImage(XA,'xi1', img(jpeg(999_000)))));
  await check("its thumbnail uploads", assertSucceeds(uploadImage(XA,'xi1_t', img(jpeg(30_000)))));
  await check("a thumbnail over 200 KB is refused", assertFails(uploadImage(XA,'xi2_t', img(jpeg(250_000)))));
  await check("a 1 MB photo is refused", assertFails(uploadImage(XA,'xi3', img(jpeg(1_000_000)))));
  await check("a PNG (not re-encoded by the app) is refused", assertFails(uploadImage(XA,'xi4', img(png(50_000)))));
  await check("random bytes are refused", assertFails(uploadImage(XA,'xi5', img(Bytes.fromUint8Array(new Uint8Array(5000))))));
  await check("a photo dated in the future is refused", assertFails(uploadImage(XA,'xi6', img(jpeg(5000), { createdAt: now() + 86400000 * 30 }))));
  await check("a photo can't claim someone else as owner", assertFails(uploadImage(XA,'xi7', img(jpeg(5000), { ownerId: "x_b" }))));

  const note = (from) => ({ type: "message", params: { user: from }, route: "chat/x", fromUid: from, read: false, createdAt: now() });
  await check("legacy unbound write refused: member alerts someone who hasn't blocked them", assertFails(alertWrite(XA,"x_b","xn1", note("x_a"))));
  await check("an alert route to an id of dots (would crash the app) is refused", assertFails(alertWrite(XA,"x_b","xn9", { ...note("x_a"), route: "post/.." })));
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "users", "x_b", "blocked", "x_a"), { at: now() }));
  await check("a blocked member can't write into the blocker's inbox", assertFails(alertWrite(XA,"x_b","xn2", note("x_a"))));
  await check("legacy unbound write refused: the blocker can still alert the blocked member", assertFails(alertWrite(XB,"x_a","xn3", note("x_b"))));
}

// ---------- Phase 9: abuse limits and size caps ----------
console.log("Phase 9 abuse limits and size caps");
{
  for (const u of ["q_a", "q_b", "q_c", "q_d", "q_e", "q_f", "q_g", "q_h", "q_v"]) await register(u);
  await setRole("q_v", "volunteer");
  const QA = ctx("q_a"), QB = ctx("q_b"), QV = ctx("q_v");
  const big = (n) => "x".repeat(n);
  // A rate-limited write (posting timer stamped in the same batch), from a fresh account so the timer never blocks it.
  const stamped = (db, uid, path, data) => {
    const b = writeBatch(db);
    b.set(doc(db, ...path), data);
    b.update(doc(db, "users", uid), { lastPostAt: serverTimestamp() });
    return b.commit();
  };

  // Chats: the app's fields only, short previews, a member edits only their own name and photo.
  await check("chat with an extra field is refused", assertFails(setDoc(doc(QA, "chats", "q_a_q_b"), { ...chatDoc("q_a", "q_b"), junk: big(10) })));
  await check("chat with a name for an outsider is refused", assertFails(setDoc(doc(QA, "chats", "q_a_q_b"), { ...chatDoc("q_a", "q_b"), names: { q_a: "q_a", q_b: "q_b", q_z: "z" } })));
  await check("member starts a chat (Phase 9 shape)", assertSucceeds(setDoc(doc(QA, "chats", "q_a_q_b"), chatDoc("q_a", "q_b"))));
  const sendQ = (db, uid, other, text) => {
    const b = writeBatch(db);
    const message=doc(collection(db,'chats','q_a_q_b','messages')),at=now();
    b.set(message, { senderId:uid,text,imageId:'',postId:'',postTitle:'',postPhotoId:'',createdAt:at });
    b.update(doc(db,'chats','q_a_q_b'),{lastMessageId:message.id,lastText:text.slice(0,120),lastSender:uid,lastAt:at,[`unread.${other}`]:increment(1),[`names.${uid}`]:uid,[`photos.${uid}`]:''});
    b.update(doc(db,'userLimits',other),{unreadCount:increment(1),unreadChatId:'q_a_q_b',unreadMessageId:message.id});
    return b.commit();
  };
  await check("a message as the app sends it", assertSucceeds(sendQ(QA, "q_a", "q_b", "Hello, is the cat still there?")));
  await check("two messages right after each other (offline queue) both go through", assertSucceeds(Promise.all([sendQ(QB, "q_b", "q_a", "yes"), sendQ(QB, "q_b", "q_a", "come by")])));
  await check("a 301-character chat preview is refused", assertFails(updateDoc(doc(QA, "chats", "q_a_q_b"), { lastText: big(301) })));
  await check("a member can't rename the other person", assertFails(updateDoc(doc(QA, "chats", "q_a_q_b"), { "names.q_b": "fake" })));
  await check("a member can't set the other person's photo", assertFails(updateDoc(doc(QA, "chats", "q_a_q_b"), { "photos.q_b": "img9" })));
  await check("an over-long own name in a chat is refused", assertFails(updateDoc(doc(QA, "chats", "q_a_q_b"), { "names.q_a": big(41) })));
  await check("a member can't name someone else as the last sender", assertFails(updateDoc(doc(QA, "chats", "q_a_q_b"), { lastSender: "q_z" })));
  await check("unread counts only for members", assertFails(updateDoc(doc(QA, "chats", "q_a_q_b"), { "unread.q_z": 5 })));
  await check("account deletion blanks own fields while preserving the counterpart preview",assertSucceeds(resetChat(QA,'q_a','q_a_q_b',true)));

  // AI jobs: a pending job can't be deleted at once (that would skip the one-second gap).
  const qjob = (uid, extra = {}) => ({ uid, kind: "check", status: "pending", images: [], ids: [], photos: [], requestId: "q1", createdAt: serverTimestamp(), ...extra });
  await check("member creates an AI job", assertSucceeds(setDoc(doc(QA, "aiJobs", "q_a"), qjob("q_a"))));
  await check("a pending job can't be deleted right away", assertFails(deleteDoc(doc(QA, "aiJobs", "q_a"))));
  await check("so deleting and re-creating can't skip the gap", assertFails(setDoc(doc(QA, "aiJobs", "q_a"), qjob("q_a", { requestId: "q2" }))));
  const W = env.authenticatedContext("catmap-ai-worker", { email: "w@x.test", email_verified: true, catmapAiWorker: true }).firestore();
  await check("the PC answers the job", assertSucceeds(updateDoc(doc(W, "aiJobs", "q_a"), { status: "done", results: [], model: "m", provider: "cpu", ms: 3, doneAt: serverTimestamp(), answeredRequestId: "q1" })));
  await check("an answered job is deleted by its owner", assertSucceeds(deleteDoc(doc(QA, "aiJobs", "q_a"))));
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "aiJobs", "q_b"), { uid: "q_b", kind: "check", status: "pending", images: [], ids: [], requestId: "q3", createdAt: new Date(now() - 20000) }));
  await check("an unanswered job is deleted after the phone's wait", assertSucceeds(deleteDoc(doc(QB, "aiJobs", "q_b"))));

  // Reports, rescues, maintenance: the app's fields and bounded text (real reports still pass: see the checks above).
  const rescue = (uid, extra = {}) => ({ type: "injured", urgency: "high", description: "limping", landmark: "", photoIds: [], lat: 25.27, lng: 55.30,
    neighborhood: "deira", catId: "", catName: "", reporterId: uid, reporterName: uid, status: "submitted", assignedTo: "", assignedName: "",
    updates: [{ status: "submitted", note: "", byUid: uid, byName: uid, at: now() }], createdAt: now(), updatedAt: now(), ...extra });
  await check("a rescue report with an extra field is refused", assertFails(stamped(ctx("q_c"), "q_c", ["rescues", "qr1"], rescue("q_c", { junk: 1 }))));
  await check("a rescue report with 5,000 characters of text is refused", assertFails(stamped(ctx("q_c"), "q_c", ["rescues", "qr1"], rescue("q_c", { description: big(5000) }))));
  await check("a long but real rescue description (3,000 characters) still goes through", assertSucceeds(stamped(ctx("q_c"), "q_c", ["rescues", "qr1"], rescue("q_c", { description: big(3000) }))));
  const report = (uid, extra = {}) => ({ targetType: "post", targetId: "p1", targetLabel: "Luna", reason: "spam", details: "", reporterId: uid, reporterName: uid, status: "open", resolution: "", resolvedBy: "", createdAt: now(), ...extra });
  await check("a content report with an extra field is refused", assertFails(stamped(ctx("q_d"), "q_d", ["contentReports", "qc1"], report("q_d", { junk: big(10) }))));
  await check("a content report with 4,000 characters of details is refused", assertFails(stamped(ctx("q_d"), "q_d", ["contentReports", "qc1"], report("q_d", { details: big(4000) }))));
  await check("a content report with real details goes through", assertSucceeds(stamped(ctx("q_d"), "q_d", ["contentReports", "qc1"], report("q_d", { details: big(1500) }))));
  const maint = (uid, extra = {}) => ({ stationId: "st1", stationName: "Station", userId: uid, username: uid, issue: "no_water", description: "", status: "open", resolutionNote: "", resolvedBy: "", createdAt: now(), updatedAt: now(), ...extra });
  await check("a maintenance report with an extra field is refused", assertFails(stamped(ctx("q_e"), "q_e", ["maintenance", "qm1"], maint("q_e", { junk: 1 }))));
  await check("a maintenance report with a number for text is refused", assertFails(stamped(ctx("q_e"), "q_e", ["maintenance", "qm1"], maint("q_e", { description: 5 }))));

  // Merge suggestions, volunteer applications, medical notes, exact locations.
  const merge = (extra = {}) => ({ primaryCatId: "c1", primaryName: "Zafran", duplicateCatId: "c2", duplicateName: "Ginger", requestedBy: "q_a", requestedByName: "q_a",
    reason: "same cat", similarity: 0.9, status: "pending", reviewedBy: "", reviewedByName: "", reviewNote: "", createdAt: now(), reviewedAt: 0, ...extra });
  await check("a merge suggestion with an extra field is refused", assertFails(setDoc(doc(QA, "mergeRequests", "qmr1"), merge({ junk: 1 }))));
  await check("a merge suggestion with 4,000 characters of reason is refused", assertFails(setDoc(doc(QA, "mergeRequests", "qmr1"), merge({ reason: big(4000) }))));
  await check("a merge suggestion as the app writes it", assertSucceeds(setDoc(doc(QA, "mergeRequests", "qmr1"), merge())));
  const app = (extra = {}) => ({ userId: "q_b", username: "q_b", motivation: "love cats", experience: "", area: "deira", status: "pending", reviewedBy: "", createdAt: now(), ...extra });
  await check("a volunteer application with an extra field is refused", assertFails(setDoc(doc(QB, "volunteerApplications", "q_b"), app({ role: "admin" }))));
  await check("a volunteer application with 5,000 characters is refused", assertFails(setDoc(doc(QB, "volunteerApplications", "q_b"), app({ motivation: big(5000) }))));
  await check("a medical note with an extra field is refused", assertFails(setDoc(doc(QV, "medicalRecords", "qmd1"), { catId: "c1", type: "note", note: "", byUid: "q_v", byName: "q_v", at: now(), junk: 1 })));
  await check("a 3,000-character medical note is refused", assertFails(setDoc(doc(QV, "medicalRecords", "qmd1"), { catId: "c1", type: "note", note: big(3000), byUid: "q_v", byName: "q_v", at: now() })));
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "sightings", "qs1"), { ...sightingDoc("q_a", "c1") }));
  await check("an exact location with an extra field is refused", assertFails(setDoc(doc(QA, "privateLocations", "qs1"), { ownerId: "q_a", lat: 25.27, lng: 55.30, accuracy: 5, note: big(100) })));
  await check("an exact location with text for a coordinate is refused", assertFails(setDoc(doc(QA, "privateLocations", "qs1"), { ownerId: "q_a", lat: "25.27", lng: 55.30, accuracy: 5 })));
  await check("an exact location as the app writes it", assertSucceeds(setDoc(doc(QA, "privateLocations", "qs1"), { ownerId: "q_a", lat: 25.27, lng: 55.30, accuracy: 5 })));

  // Adoption requests, sitter profiles, bookings.
  await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "posts", "qp1"), { ownerId:'q_b',category:'adoption',hidden:false,name:'Luna',status:'open',adoptionStatus:'available' }));
  const adopt = (extra = {}) => ({ postId: "qp1", postName: "Luna", postPhotoId: "", ownerId: "q_b", requesterId: "q_a", requesterName: "q_a", message: "I'd love to adopt her",
    home: "flat", status: "pending", ownerNote: "", createdAt: now(), decidedAt: 0, ...extra });
  await check("an adoption request with an extra field is refused", assertFails(setDoc(doc(QA, "adoptionRequests", "qp1_q_a"), adopt({ junk: 1 }))));
  await check("an adoption request with 4,000 characters is refused", assertFails(setDoc(doc(QA, "adoptionRequests", "qp1_q_a"), adopt({ message: big(4000) }))));
  await check("an adoption request as the app writes it", assertSucceeds(setDoc(doc(QA, "adoptionRequests", "qp1_q_a"), adopt())));
  await check("the post owner can't attach a 2,000-character note", assertFails(updateDoc(doc(QB, "adoptionRequests", "qp1_q_a"), { status: "approved", ownerNote: big(2000), decidedAt: now() })));
  const sitter = (uid, extra = {}) => ({ name: uid, photoId: "", headline: "Cat lover", description: "", experience: "", petTypes: ["cat"], services: ["visits"], availability: "",
    rate: 50, rateUnit: "visit", lat: 25.2, lng: 55.3, neighborhood: "deira", active: true, ratingSum: 0, verified: false, ratingCount: 0, lastReviewId: "", createdAt: now(), updatedAt: now(), ...extra });
  await check("a sitter profile with an extra field is refused", assertFails(setDoc(doc(ctx("q_f"), "sitters", "q_f"), sitter("q_f", { junk: 1 }))));
  await check("a sitter profile with 6,000 characters is refused", assertFails(setDoc(doc(ctx("q_f"), "sitters", "q_f"), sitter("q_f", { description: big(6000) }))));
  await check("a sitter profile as the app writes it", assertSucceeds(setDoc(doc(ctx("q_f"), "sitters", "q_f"), sitter("q_f"))));
  await check("a sitter can't grow their profile past the limit later", assertFails(updateDoc(doc(ctx("q_f"), "sitters", "q_f"), { description: big(6000) })));
  const booking = (extra = {}) => ({ ownerId: "q_g", ownerName: "q_g", sitterId: "q_f", sitterName: "q_f", petTypes: ["cat"], petCount: 1, startAt: now(), endAt: now() + 86400000,
    notes: "", sitterNote: "", status: "requested", reviewed: false, createdAt: now(), updatedAt: now(), ...extra });
  await check("a booking with an extra field is refused", assertFails(stamped(ctx("q_g"), "q_g", ["bookings", "qb1"], booking({ junk: 1 }))));
  await check("a booking with 4,000 characters of notes is refused", assertFails(stamped(ctx("q_g"), "q_g", ["bookings", "qb1"], booking({ notes: big(4000) }))));
  await check("a booking as the app writes it", assertSucceeds(stamped(ctx("q_g"), "q_g", ["bookings", "qb1"], booking())));
  await check("the sitter can't attach a 2,000-character note", assertFails(updateDoc(doc(ctx("q_f"), "bookings", "qb1"), { status: "accepted", sitterNote: big(2000), updatedAt: now() })));
}

// Cleanup keeps the existing sender and changes only the bounded preview.
await check("legacy unbound write refused: a member repairs the last preview without impersonating the previous sender", assertFails(updateDoc(doc(B, "chats", "bob_carol"), { lastText: "Previous message" })));
await check("legacy unbound write refused: a member can clear an empty chat preview", assertFails(updateDoc(doc(B, "chats", "bob_carol"), { lastText: "" })));
await check("an outsider cannot repair another chat's preview", assertFails(updateDoc(doc(A, "chats", "bob_carol"), { lastText: "Forged" })));

if (storageAddress) {
  console.log("Video deletion permissions");
  for (const [id, role] of [["video_owner", "user"], ["video_other", "user"], ["video_mod", "moderator"], ["video_admin", "admin"]]) {
    await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "users", id), profile(id)));
    if (role !== "user") await setRole(id, role);
  }
  const video = (context, id) => context.storage("gs://demo-catmap-rules-test.appspot.com").ref(`videos/video_owner/${id}`);
  const owner = env.authenticatedContext("video_owner", { email_verified: true });
  const outsider = env.authenticatedContext("video_other", { email_verified: true });
  for (const id of ["own", "mod", "admin", "claimed", "refused", "anonymous"]) {
    await check(`owner uploads video ${id}`, assertSucceeds(video(owner, id).put(new Uint8Array([0, 1]), { contentType: "video/mp4" })));
  }
  await check("owner deletes their video", assertSucceeds(video(owner, "own").delete()));
  await check("moderator deletes a post video", assertSucceeds(video(env.authenticatedContext("video_mod"), "mod").delete()));
  await check("admin deletes a post video", assertSucceeds(video(env.authenticatedContext("video_admin"), "admin").delete()));
  await check("admin claim alone cannot delete a post video", assertFails(video(env.authenticatedContext("video_claim", { catmapAdmin: true }), "claimed").delete()));
  await check("another member cannot delete the video", assertFails(video(outsider, "refused").delete()));
  await check("signed-out person cannot delete the video", assertFails(video(env.unauthenticatedContext(), "anonymous").delete()));
  await check("moderator cannot upload into the owner's folder", assertFails(video(env.authenticatedContext("video_mod", { email_verified: true }), "foreign").put(new Uint8Array([0]), { contentType: "video/mp4" })));
  await check('verified active member reads video metadata',assertSucceeds(video(outsider,'refused').getMetadata()));
  const unverified=env.authenticatedContext('video_owner',{email_verified:false});
  await check('unverified read refused',assertFails(video(unverified,'refused').getMetadata()));
  await check('unverified upload refused',assertFails(video(unverified,'unverified').put(new Uint8Array([0]),{contentType:'video/mp4'})));
  await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'users','video_owner'),{suspended:true}));
  await check('suspended read refused',assertFails(video(owner,'refused').getMetadata()));
  await check('suspended upload refused',assertFails(video(owner,'suspended').put(new Uint8Array([0]),{contentType:'video/mp4'})));
  await check('suspended owner can delete for privacy',assertSucceeds(video(owner,'refused').delete()));
  await check('profile-less verified reader refused',assertFails(video(env.authenticatedContext('video_missing',{email_verified:true}),'claimed').getMetadata()));
} else console.log("Storage checks skipped: start with --config firebase.storage-test.json --only firestore,storage");

await (await import('./extras.mjs')).runExtras({env,check,ctx,profile,postDoc,catDoc,sightingDoc,userStats});
console.log(`\n${passed} passed, ${failed} failed`);
await env.cleanup();
process.exit(failed ? 1 : 0);
