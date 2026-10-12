/**
 * CatMap push delivery (needs the Firebase Blaze plan).
 *
 * The app already writes every alert to users/{uid}/notifications. This function watches that
 * collection and sends a small, content-free FCM message to the person's phones. The app wakes up,
 * reads the inbox itself, and shows the alert with the person's own notification settings, so no
 * names, messages or locations ever pass through FCM.
 *
 * Phones register their tokens in users/{uid}/private/push { tokens: [...] } (see PushService.kt).
 * Tokens that FCM reports as gone are removed here.
 *
 * Deploy:  firebase deploy --only functions
 */
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");

initializeApp();

// Keep this the same as the Firestore database location (Firebase console → Firestore → Settings).
const REGION = process.env.CATMAP_REGION || "us-central1";

const GONE = new Set(["registration-token-not-registered", "invalid-registration-token", "invalid-argument"]);

exports.pushOnNotification = onDocumentCreated(
  { document: "users/{uid}/notifications/{notificationId}", region: REGION, retry: false, maxInstances: 10 },
  async (event) => {
    const { uid, notificationId } = event.params;
    const data = event.data?.data();
    if (!data) return;

    const db = getFirestore();
    const pushRef = db.doc(`users/${uid}/private/push`);
    const tokens = ((await pushRef.get()).get("tokens") || []).filter((t) => typeof t === "string" && t.length > 0);
    if (tokens.length === 0) return;

    const response = await getMessaging().sendEachForMulticast({
      tokens: tokens.slice(0, 500),
      // Only ids: the app reads the alert itself from Firestore.
      data: { kind: "inbox", id: String(notificationId), type: String(data.type || "") },
      android: { priority: "high", ttl: 6 * 60 * 60 * 1000, collapseKey: "inbox" },
    });

    const gone = [];
    response.responses.forEach((r, i) => {
      const code = (r.error && r.error.code) || "";
      if (!r.success && GONE.has(code.replace(/^messaging\//, ""))) gone.push(tokens[i]);
    });
    if (gone.length > 0) await pushRef.update({ tokens: FieldValue.arrayRemove(...gone) });
    logger.info("push sent", { uid, ok: response.successCount, failed: response.failureCount, removed: gone.length });
  },
);
