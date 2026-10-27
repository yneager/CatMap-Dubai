// Runs after tools/migrate/test_schema_backfill_emulator.py, on the data it migrated (project demo-catmap-backfill):
// accounts made with v0.1.0 can do what the 23 Oct rules require of them once the backfill has run. Replays the
// apps' own writes (ChatRepository.send / markRead, PrivateSlots, AreaFollow) as those accounts.
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, getDoc, getDocs, writeBatch, increment, collection, query, where, limit } from "firebase/firestore";
import { readFileSync } from "node:fs";

const address = new URL(`http://${process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8180"}`);
if (!["localhost", "127.0.0.1", "[::1]"].includes(address.hostname)) throw new Error("Loopback emulator required");
const env = await initializeTestEnvironment({
  projectId: process.env.GCLOUD_PROJECT || "demo-catmap-backfill",
  firestore: { host: address.hostname, port: Number(address.port), rules: readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8") },
});
if (!env.projectId.startsWith("demo-")) throw new Error("demo-* project required");
const ctx = (uid) => env.authenticatedContext(uid, { email: `${uid}@x.test`, email_verified: true }).firestore();
let passed = 0, failed = 0;
async function check(name, promise) {
  try { await promise; passed++; console.log("  ok  ", name); }
  catch (e) { failed++; console.log("  FAIL", name, "-", e.message.split("\n")[0]); }
}

const now = Date.now();
// bob sends alice a message (ChatRepository.send): message, chat preview and unread, alice's unread total.
{
  const db = ctx("bob");
  const b = writeBatch(db);
  b.set(doc(db, "chats/alice_bob/messages/x1"), { senderId: "bob", text: "hello", imageId: "", postId: "", postTitle: "", postPhotoId: "", matchId: "", matchPhotoId: "", createdAt: now });
  b.update(doc(db, "chats/alice_bob"), { lastText: "hello", lastSender: "bob", lastMessageId: "x1", lastAt: now, "unread.alice": increment(1), "names.bob": "bob", "photos.bob": "" });
  b.update(doc(db, "userLimits/alice"), { unreadCount: increment(1), unreadChatId: "alice_bob", unreadMessageId: "x1" });
  await check("a migrated account can receive a message", assertSucceeds(b.commit()));
}
// alice opens the chat (ChatRepository.markRead).
{
  const db = ctx("alice");
  const b = writeBatch(db);
  b.update(doc(db, "chats/alice_bob"), { "unread.alice": 0 });
  b.update(doc(db, "userLimits/alice"), { unreadCount: increment(-1), unreadChatId: "alice_bob" });
  await check("and mark it read", assertSucceeds(b.commit()));
}
// carol, whose chat had no unread count for her, messages bob (his total came from two chats).
{
  const db = ctx("carol");
  const b = writeBatch(db);
  b.set(doc(db, "chats/bob_carol/messages/y1"), { senderId: "carol", text: "hi", imageId: "", postId: "", postTitle: "", postPhotoId: "", matchId: "", matchPhotoId: "", createdAt: now });
  b.update(doc(db, "chats/bob_carol"), { lastText: "hi", lastSender: "carol", lastMessageId: "y1", lastAt: now, "unread.bob": increment(1), "names.carol": "carol", "photos.carol": "" });
  b.update(doc(db, "userLimits/bob"), { unreadCount: increment(1), unreadChatId: "bob_carol", unreadMessageId: "y1" });
  await check("a chat with a filled-in unread count works", assertSucceeds(b.commit()));
}
// Saving and un-saving a post (PrivateSlots).
{
  const db = ctx("carol");
  const b = writeBatch(db);
  b.set(doc(db, "users/carol/favorites/p1"), { postId: "p1", savedAt: now });
  b.update(doc(db, "userLimits/carol"), { favorites: increment(1), favoritesSource: "p1" });
  await check("a migrated account can save a post", assertSucceeds(b.commit()));
}
{
  const db = ctx("alice");
  const b = writeBatch(db);
  b.delete(doc(db, "users/alice/favorites/p1"));
  b.update(doc(db, "userLimits/alice"), { favorites: increment(-1), favoritesSource: "p1" });
  await check("and remove a post saved before the backfill", assertSucceeds(b.commit()));
}
{
  const db = ctx("alice");
  const b = writeBatch(db);
  b.delete(doc(db, "users/alice/blocked/dave"));
  b.update(doc(db, "userLimits/alice"), { blocked: increment(-1), blockedSource: "dave" });
  await check("and unblock someone blocked before the backfill", assertSucceeds(b.commit()));
}
// Photos.
await check("others see a community photo", assertSucceeds(getDoc(doc(ctx("carol"), "images/pa"))));
await check("others see a post photo", assertSucceeds(getDoc(doc(ctx("carol"), "images/pp1_t"))));
let m1;
await env.withSecurityRulesDisabled(async (c) => { m1 = (await getDoc(doc(c.firestore(), "chats/alice_bob/messages/m1"))).data(); });
await check("the other chat member sees a moved chat photo", assertSucceeds(getDoc(doc(ctx("bob"), `images/${m1.imageId}`))));
await check("someone outside the chat doesn't", assertFails(getDoc(doc(ctx("carol"), `images/${m1.imageId}`))));
await check("an unreferenced old photo stays private", assertFails(getDoc(doc(ctx("bob"), "images/orphan"))));
await check("its owner still sees it", assertSucceeds(getDoc(doc(ctx("alice"), "images/orphan"))));
// The map's cat query and the new area-follow shape.
await check("the map's cat query finds an old cat", (async () => {
  const s = await getDocs(query(collection(ctx("bob"), "cats"), where("hidden", "==", false), where("mergedInto", "==", ""), limit(10)));
  if (!s.docs.some((d) => d.id === "c1")) throw new Error("c1 missing");
})());
await check("a migrated area follow can be updated", assertSucceeds((async () => {
  const db = ctx("alice"); const b = writeBatch(db);
  b.set(doc(db, "areaFollows/alice"), { neighborhoods: ["deira", "karama"], updatedAt: now });
  await b.commit();
})()));
await check("the private centre can be read by its owner", assertSucceeds(getDoc(doc(ctx("alice"), "users/alice/private/areaFollow"))));
await check("and only by its owner", assertFails(getDoc(doc(ctx("bob"), "users/alice/private/areaFollow"))));

await env.cleanup();
console.log(`\nAfter backfill: ${passed} passed, ${failed} failed`);
if (failed) process.exit(1);
