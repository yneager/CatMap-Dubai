package com.catmap.dubai.data

import com.catmap.dubai.data.model.AppNotification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** Notification types. Their text is formatted on the receiving phone, in its own language. */
object NotifType {
    const val STALE_POST = "stale_post"
    const val FOLLOWED_CAT = "followed_cat"
    const val RESCUE_UPDATE = "rescue_update"
    const val RESCUE_NEW_VOLUNTEER = "rescue_volunteer"
    const val RESCUE_NEW_MOD = "rescue_moderation"
    const val ACHIEVEMENT = "achievement"
    const val MODERATION = "moderation"
    const val VOLUNTEER_DECISION = "volunteer_decision"
    const val MAINTENANCE_UPDATE = "maintenance_update"
    const val WELCOME = "welcome"
    const val MESSAGE = "message"
    const val ADOPTION_REQUEST = "adoption_request"
    const val ADOPTION_DECISION = "adoption_decision"
    const val BOOKING_REQUEST = "booking_request"
    const val BOOKING_UPDATE = "booking_update"
    const val REVIEW = "review"
    const val POST_UPDATE = "post_update"
    const val LOST_NEARBY = "lost_nearby"
    const val POSSIBLE_MATCH = "possible_match"
    /** A sighting may be the owner's lost animal (to the owner). */
    const val MATCH_FOUND = "match_found"
    /** The person's sighting may be a lost animal (to the reporter). */
    const val MATCH_REPORTER = "match_reporter"
    /** The owner asked the reporter for more details. */
    const val MATCH_INFO = "match_info"
    /** The owner marked the animal reunited thanks to the reporter's sighting. */
    const val MATCH_REUNITED = "match_reunited"
}

class NotificationRepository(private val db: FirebaseFirestore, private val auth: FirebaseAuth) {
    private fun col(uid: String) = db.collection(Col.USERS).document(uid).collection(Col.NOTIFICATIONS)

    /** The newest [WINDOW] alerts, live (the bell, the inbox, alerts shown while the app is open). */
    fun notifications(uid: String): Flow<List<AppNotification>> =
        col(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(WINDOW).listFlow()

    // Same ordered window as the live listener; an unordered unread limit can hide new alerts.
    suspend fun latest(uid: String): List<AppNotification> =
        col(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(WINDOW).get().await().safeObjects<AppNotification>()

    /** Older alerts, read a page at a time below the live window (the inbox's "Load more"). */
    fun olderQuery(uid: String, before: Long): Query =
        col(uid).orderBy("createdAt", Query.Direction.DESCENDING).startAfter(before)

    /**
     * Only the alerts created after [sinceMillis]: what the background check needs. When nothing is new this costs one
     * Firestore read, where [latest] costs up to 100 every 15 minutes.
     */
    suspend fun since(uid: String, sinceMillis: Long): List<AppNotification> =
        col(uid).whereGreaterThan("createdAt", sinceMillis).orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .get().await().safeObjects<AppNotification>()

    suspend fun markRead(uid: String, id: String) {
        col(uid).document(id).update("read", true).await()
    }

    suspend fun markAllRead(uid: String, ids: List<String>) {
        ids.chunked(400).forEach { chunk ->
            db.runBatch { b -> chunk.forEach { b.update(col(uid).document(it), "read", true) } }.await()
        }
    }

    /** Removes an alert if it exists (used when a chat is opened, so the next message alerts again). */
    suspend fun dismiss(uid: String, id: String) {
        runCatching { col(uid).document(id).delete().await() }
    }

    suspend fun delete(uid: String, id: String) {
        col(uid).document(id).delete().await()
    }

    suspend fun clearAll(uid: String) {
        col(uid).get().await().documents.chunked(400).forEach { chunk ->
            db.runBatch { b -> chunk.forEach { b.delete(it.reference) } }.await()
        }
    }

    /**
     * Writes a notification into each recipient's inbox. Failures are swallowed: a notification
     * that can't be delivered must never undo the action that triggered it.
     */
    suspend fun send(recipients: Collection<String>, type: String, params: Map<String, String>, route: String = "", dedupeKey: String? = null) {
        val me = auth.currentUser?.uid ?: return
        val targets = recipients.filter { it.isNotBlank() }.toSet()
        if (targets.isEmpty()) return
        // One write per person, never one batch: the rules refuse an alert to someone who blocked the sender, and in a
        // batch that one refusal would stop everyone else's alert too. A fixed id ([dedupeKey]) makes repeats
        // harmless: writing over an existing alert is an update, which the rules refuse, so each person gets one
        // alert per key until they read and remove it.
        coroutineScope {
            targets.map { uid ->
                async {
                    runCatching {
                        val ref = if (dedupeKey != null) col(uid).document(dedupeKey) else col(uid).document()
                        ref.set(AppNotification(type = type, params = params, route = route, fromUid = me, createdAt = now())).await()
                    }.onFailure { android.util.Log.w("CatMap", "Notification to one person not delivered", it) }
                }
            }.awaitAll()
        }
    }

    companion object {
        /** How many of the newest alerts stay live; the inbox loads older ones on demand. */
        const val WINDOW = 30L
    }
}
