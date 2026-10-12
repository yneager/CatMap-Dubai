// Run with the Firestore emulator up: FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GCLOUD_PROJECT=demo-catmap npm test
// FCM is replaced by a stub, so nothing leaves the machine.
const path = require("path").join(__dirname, "..");
const assert = require("assert");
const fnRequire = require("module").createRequire(path + "/index.js");
const sent = [];
fnRequire("firebase-admin/messaging").getMessaging = () => ({
  sendEachForMulticast: async (m) => {
    sent.push(m);
    return { successCount: 1, failureCount: 1, responses: m.tokens.map((t) => t === "dead" ? { success: false, error: { code: "messaging/registration-token-not-registered" } } : { success: true }) };
  },
});
const f = fnRequire("./index.js");
const { getFirestore } = fnRequire("firebase-admin/firestore");
(async () => {
  const db = getFirestore();
  const uid = "fn-test-user";
  await db.doc(`users/${uid}/private/push`).set({ tokens: ["good", "dead"] });
  await f.pushOnNotification.run({ params: { uid, notificationId: "n1" }, data: { data: () => ({ type: "message", params: { text: "secret" } }) } });
  assert.strictEqual(sent.length, 1);
  assert.deepStrictEqual(sent[0].tokens, ["good", "dead"]);
  assert.deepStrictEqual(sent[0].data, { kind: "inbox", id: "n1", type: "message" });
  assert.ok(!JSON.stringify(sent[0]).includes("secret"), "no content in the push");
  const left = (await db.doc(`users/${uid}/private/push`).get()).get("tokens");
  assert.deepStrictEqual(left, ["good"]);
  // No tokens: nothing sent.
  await f.pushOnNotification.run({ params: { uid: "nobody", notificationId: "n2" }, data: { data: () => ({ type: "x" }) } });
  assert.strictEqual(sent.length, 1);
  await db.recursiveDelete(db.doc(`users/${uid}`));
  console.log("functions test: 6 checks passed");
})().catch((e) => { console.error(e); process.exit(1); });
