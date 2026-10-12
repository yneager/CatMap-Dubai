// Creates the five demo accounts and demo content in the LOCAL Firebase emulators only.
//
//   node tools/seed/seed.mjs            (emulators must be running: auth 9099, firestore 8080)
//
// Passwords are read from tools/seed/demo-accounts.local.json, which is git-ignored. Copy
// demo-accounts.example.json to that name and fill in passwords (Firebase needs at least 6 characters).
// The script refuses to touch anything but a localhost emulator with a "demo-" project id, so demo
// accounts can never reach the real (production) Firebase project.

import { readFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const PROJECT = process.env.CATMAP_PROJECT || "demo-catmap";
const AUTH = process.env.AUTH_HOST || "127.0.0.1:9099";
const STORE = process.env.FIRESTORE_HOST || "127.0.0.1:8080";

for (const host of [AUTH, STORE]) {
  if (!/^(127\.0\.0\.1|localhost|\[::1\]):\d+$/.test(host)) throw new Error(`Refusing non-local host ${host}`);
}
if (!PROJECT.startsWith("demo-")) throw new Error(`Refusing project "${PROJECT}": demo data only goes into demo- projects`);

const accountsFile = join(here, "demo-accounts.local.json");
if (!existsSync(accountsFile)) throw new Error("Missing tools/seed/demo-accounts.local.json (copy demo-accounts.example.json and set passwords)");
const accounts = JSON.parse(readFileSync(accountsFile, "utf8"));

const owner = { Authorization: "Bearer owner", "Content-Type": "application/json" };
const now = Date.now();
const hours = (h) => now - h * 3600_000;

// ---------- Firestore REST helpers (typed values) ----------
function value(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (v instanceof Uint8Array) return { bytesValue: Buffer.from(v).toString("base64") };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(value) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, value(x)])) } };
}
async function put(path, data) {
  const url = `http://${STORE}/v1/projects/${PROJECT}/databases/(default)/documents/${path}`;
  const res = await fetch(url, { method: "PATCH", headers: owner, body: JSON.stringify({ fields: value(data).mapValue.fields }) });
  if (!res.ok) throw new Error(`PATCH ${path}: ${res.status} ${await res.text()}`);
}

// ---------- Auth emulator helpers ----------
async function authCall(path, body, headers = { "Content-Type": "application/json" }) {
  const res = await fetch(`http://${AUTH}/identitytoolkit.googleapis.com/v1/${path}?key=demo`, { method: "POST", headers, body: JSON.stringify(body) });
  return { ok: res.ok, json: await res.json() };
}
async function ensureUser(email, password) {
  let r = await authCall("accounts:signUp", { email, password, returnSecureToken: true });
  if (!r.ok && r.json?.error?.message?.startsWith("EMAIL_EXISTS")) {
    r = await authCall("accounts:signInWithPassword", { email, password, returnSecureToken: true });
  }
  if (!r.ok) throw new Error(`Auth for ${email}: ${JSON.stringify(r.json.error)}`);
  const uid = r.json.localId;
  // Mark the address verified (demo accounts have no inbox).
  const v = await authCall(`projects/${PROJECT}/accounts:update`, { localId: uid, emailVerified: true }, owner);
  if (!v.ok) throw new Error(`Verify ${email}: ${JSON.stringify(v.json.error)}`);
  return uid;
}

// ---------- photos ----------
const photo = (name) => new Uint8Array(readFileSync(join(here, "photos", name + ".jpg")));
async function uploadPhoto(id, name, ownerId) {
  await put(`images/${id}`, { data: photo(name), ownerId, createdAt: now });
  await put(`images/${id}_t`, { data: photo(name + "_t"), ownerId, createdAt: now });
  return id;
}

// ---------- accounts ----------
const uids = {};
for (const a of accounts) {
  if (!/^[a-z0-9_.]{3,20}$/.test(a.username)) throw new Error(`Bad username ${a.username}`);
  if (!a.password || a.password.length < 6) throw new Error(`Password for ${a.username} must have 6+ characters`);
  const email = `${a.username}@demo.catmap.test`;
  const uid = await ensureUser(email, a.password);
  uids[a.username] = uid;
  await put(`users/${uid}`, {
    username: a.username, displayName: a.displayName, bio: a.bio || "", photoId: "", role: a.role,
    volunteerStatus: a.role === "user" ? "none" : "approved", stats: {}, points: a.points || 0,
    neighborhoods: [], badges: [], followedCats: [], showStatsPublicly: true, suspended: false, lastPostAt: null,
    createdAt: hours(24 * 30),
  });
  // loginEmail lets the demo accounts sign in with just their username (rules stop the app from setting it).
  await put(`usernames/${a.username}`, { uid, loginEmail: email });
  await put(`users/${uid}/private/account`, { email, createdAt: hours(24 * 30) });
  console.log(`account ${a.username} (${a.role}) ready`);
}
const U = (name) => ({ uid: uids[name], name });

// ---------- demo posts ----------
function post(id, who, p) {
  const keywords = [p.name, p.color, p.breed, p.features, p.description].join(" ").toLowerCase().split(/[^\p{L}\p{N}]+/u).filter((w) => w.length >= 2);
  return put(`posts/${id}`, {
    ownerId: who.uid, ownerName: who.name, ownerPhotoId: "", status: "open", sex: "unknown", ageGroup: "unknown",
    videoPath: "", approximate: false, areaRadiusM: 0, reward: 0, contactMethod: "in_app", contactPhone: "", contactEmail: "",
    adoptionStatus: "available", health: "", vaccinated: false, neutered: false, updatesCount: 0, hidden: false,
    color: "", breed: "", features: "", resolvedAt: 0, ...p, keywords: [...new Set(keywords)].slice(0, 80),
    createdAt: p.createdAt, updatedAt: p.createdAt,
  });
}

const ali = U("ali"), mohammad = U("mohammad"), anirudh = U("anirudh"), hassan = U("hassan"), admin = U("admin");

await post("demo_lost_rex", ali, {
  animalType: "dog", category: "lost", name: "Rex", color: "Sandy brown", breed: "Saluki mix", sex: "male", ageGroup: "adult",
  features: "Red collar with a gold tag, very shy with strangers", description: "Rex slipped his lead near Marina Walk last night. He is friendly but scared – please don't chase him, just message me with where you saw him.",
  photoIds: [await uploadPhoto("demo_img_rex", "dog_saluki", ali.uid)], lat: 25.0781, lng: 55.1397, neighborhood: "dubai_marina",
  areaRadiusM: 800, reward: 500, lastSeenAt: hours(14), createdAt: hours(12), updatesCount: 1,
});
await put("posts/demo_lost_rex/updates/demo_upd_1", {
  postId: "demo_lost_rex", kind: "sighting", text: "I think I saw him near the JBR tram stop around 8am, heading towards the beach.",
  photoId: "", lat: 25.0770, lng: 55.1336, neighborhood: "jbr", byUid: mohammad.uid, byName: "mohammad", createdAt: hours(3),
});
await post("demo_found_tux", mohammad, {
  animalType: "cat", category: "found", name: "", color: "Black and white", breed: "Domestic shorthair", sex: "female", ageGroup: "young",
  features: "Tuxedo coat, white socks, very friendly – clearly someone's pet", description: "Found this sweet girl outside our building in Al Barsha 1. She's safe with us for now. Is she yours?",
  photoIds: [await uploadPhoto("demo_img_tux", "cat_tuxedo", mohammad.uid)], lat: 25.1112, lng: 55.2001, neighborhood: "al_barsha",
  lastSeenAt: hours(30), createdAt: hours(28),
});
await post("demo_spotted_husky", hassan, {
  animalType: "dog", category: "spotted", name: "", color: "Grey and white", breed: "Husky", ageGroup: "adult",
  features: "Blue collar, no tag", description: "A husky wandering alone in the heat near Business Bay bridge. Looked tired but calm. I gave him water.",
  photoIds: [await uploadPhoto("demo_img_husky", "dog_husky", hassan.uid)], lat: 25.1859, lng: 55.2653, neighborhood: "business_bay",
  lastSeenAt: hours(5), createdAt: hours(5),
});
await post("demo_help_kitten", anirudh, {
  animalType: "cat", category: "needs_help", name: "", color: "Grey", ageGroup: "baby",
  features: "Very small, limping on the back leg", description: "Tiny kitten hiding under a car in Al Karama, limping. I can't take her in tonight – can anyone help get her to a vet?",
  photoIds: [await uploadPhoto("demo_img_kgrey", "kitten_grey", anirudh.uid)], lat: 25.2445, lng: 55.3031, neighborhood: "al_karama",
  lastSeenAt: hours(2), createdAt: hours(2),
});
await post("demo_street_ginger", hassan, {
  animalType: "cat", category: "street", name: "Simba", color: "Orange tabby", ageGroup: "adult", sex: "male",
  features: "Ear-tipped (neutered), big round face", description: "Friendly community cat who lives around the Al Fahidi souq. Fed daily by the shop owners.",
  photoIds: [await uploadPhoto("demo_img_ginger", "cat_orange", hassan.uid)], lat: 25.2636, lng: 55.2988, neighborhood: "al_fahidi",
  createdAt: hours(70),
});
await post("demo_lost_parrot", anirudh, {
  animalType: "bird", category: "lost", name: "Kiwi", color: "Green and yellow", breed: "Indian ringneck",
  features: "Says 'hello habibi', clipped left wing", description: "Kiwi flew out of our balcony in JLT cluster D. She responds to whistling. Reward for safe return!",
  photoIds: [await uploadPhoto("demo_img_parrot", "bird_parrot", anirudh.uid)], lat: 25.0697, lng: 55.1428, neighborhood: "jlt",
  areaRadiusM: 1500, reward: 300, lastSeenAt: hours(26), createdAt: hours(25), contactMethod: "phone", contactPhone: "+971 50 000 0000",
});
await post("demo_adopt_luna", anirudh, {
  animalType: "cat", category: "adoption", name: "Luna", color: "Calico", sex: "female", ageGroup: "young",
  features: "Playful, litter trained, gets on with other cats", description: "Luna was rescued from Jumeirah 1 as a kitten and is now ready for her forever home. Indoor home only, please.",
  photoIds: [await uploadPhoto("demo_img_calico", "kitten_calico", anirudh.uid)], lat: 25.2201, lng: 55.2585, neighborhood: "jumeirah_1",
  approximate: true, health: "Healthy, dewormed, microchipped", vaccinated: true, neutered: true, createdAt: hours(48),
});
await post("demo_adopt_snow", ali, {
  animalType: "rabbit", category: "adoption", name: "Snow", color: "White", sex: "male", ageGroup: "adult",
  features: "Calm, loves leafy greens", description: "We're moving abroad and can't take Snow with us. Comes with his hutch and supplies.",
  photoIds: [await uploadPhoto("demo_img_rabbit", "rabbit_white", ali.uid)], lat: 25.2172, lng: 55.4215, neighborhood: "mirdif",
  approximate: true, health: "Healthy, vet check last month", vaccinated: true, createdAt: hours(80),
});
await post("demo_reunited_max", mohammad, {
  animalType: "dog", category: "lost", status: "reunited", name: "Max", color: "Golden", breed: "Golden retriever",
  description: "Max is home! Thank you to everyone in Arabian Ranches who shared his poster.", lat: 25.0553, lng: 55.2700,
  neighborhood: "arabian_ranches", lastSeenAt: hours(200), createdAt: hours(190), resolvedAt: hours(150),
});
console.log("demo posts ready");

// ---------- pet sitters, a booking and a review ----------
async function sitter(who, s) {
  await put(`sitters/${who.uid}`, {
    name: who.name, photoId: "", active: true, ratingSum: 0, ratingCount: 0, lastReviewId: "", createdAt: hours(400), updatedAt: hours(10), ...s,
  });
}
await sitter(hassan, {
  headline: "Cat whisperer in Downtown", description: "I've cared for cats for 10 years and fostered over 30 rescues. Home visits twice a day with photo updates.",
  experience: "Volunteer at a Dubai cat shelter; comfortable giving medication.", petTypes: ["cat", "rabbit"], services: ["home_visits", "medication"],
  availability: "Weekdays after 5pm, all weekend", rate: 60, rateUnit: "visit", lat: 25.1985, lng: 55.2757, neighborhood: "downtown",
  ratingSum: 5, ratingCount: 1, lastReviewId: "demo_booking_1",
});
await sitter(mohammad, {
  headline: "Dog walks and sleepovers in Al Barsha", description: "Big garden, two friendly dogs of my own. Happy to host small and medium dogs.",
  experience: "Grew up with dogs; first-aid trained.", petTypes: ["dog", "cat"], services: ["walking", "boarding", "overnight"],
  availability: "Most days", rate: 120, rateUnit: "night", lat: 25.1135, lng: 55.1972, neighborhood: "al_barsha",
});
await put("bookings/demo_booking_1", {
  ownerId: ali.uid, ownerName: "ali", sitterId: hassan.uid, sitterName: "hassan", petTypes: ["cat"], petCount: 2,
  startAt: hours(24 * 10), endAt: hours(24 * 7), notes: "Two cats, feed twice a day", sitterNote: "", status: "completed", reviewed: true,
  createdAt: hours(24 * 12), updatedAt: hours(24 * 6),
});
await put("reviews/demo_booking_1", {
  sitterId: hassan.uid, authorId: ali.uid, authorName: "ali", rating: 5, text: "Hassan sent photos every visit and the cats loved him!", createdAt: hours(24 * 6),
});
await put("bookings/demo_booking_2", {
  ownerId: anirudh.uid, ownerName: "anirudh", sitterId: mohammad.uid, sitterName: "mohammad", petTypes: ["dog"], petCount: 1,
  startAt: now + 24 * 3600_000 * 5, endAt: now + 24 * 3600_000 * 8, notes: "Friendly beagle, needs two walks a day", sitterNote: "",
  status: "requested", reviewed: false, createdAt: hours(6), updatedAt: hours(6),
});
console.log("sitters and bookings ready");

// ---------- an adoption request, a chat and an area alert ----------
await put(`adoptionRequests/demo_adopt_luna_${mohammad.uid}`, {
  postId: "demo_adopt_luna", postName: "Luna", postPhotoId: "demo_img_calico", ownerId: anirudh.uid, requesterId: mohammad.uid,
  requesterName: "mohammad", message: "We have a quiet flat and an older cat who loves company.", home: "2-bed flat in Al Barsha, balcony netted",
  status: "pending", ownerNote: "", createdAt: hours(20), decidedAt: 0,
});
const pair = [ali.uid, mohammad.uid].sort();
const chatId = `${pair[0]}_${pair[1]}`;
await put(`chats/${chatId}`, {
  members: pair, names: { [ali.uid]: "ali", [mohammad.uid]: "mohammad" }, photos: { [ali.uid]: "", [mohammad.uid]: "" },
  lastText: "Thank you so much, I'll head there now!", lastSender: ali.uid, lastAt: hours(2.5), unread: { [ali.uid]: 0, [mohammad.uid]: 1 },
});
const messages = [
  [mohammad.uid, "Hi Ali, I think I saw Rex near the JBR tram stop this morning.", hours(3), "demo_lost_rex"],
  [ali.uid, "Thank you so much, I'll head there now!", hours(2.5), ""],
];
for (const [i, [sender, text, at, postId]] of messages.entries()) {
  await put(`chats/${chatId}/messages/demo_msg_${i}`, {
    senderId: sender, text, imageId: "", postId, postTitle: postId ? "Rex" : "", postPhotoId: postId ? "demo_img_rex" : "", createdAt: at,
  });
}
await put(`areaFollows/${hassan.uid}`, { neighborhoods: ["dubai_marina", "jbr", "jlt"], lat: 25.078, lng: 55.14, radiusKm: 3, updatedAt: now });
await put(`users/${ali.uid}/notifications/demo_upd`, {
  type: "post_update", params: { user: "mohammad", kind: "sighting", name: "Rex", type: "dog" }, route: "post/demo_lost_rex",
  fromUid: mohammad.uid, read: false, createdAt: hours(3),
});
console.log(`done – ${accounts.length} demo accounts in ${PROJECT}`);
