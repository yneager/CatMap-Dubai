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
}

class NotificationRepository(private val db: FirebaseFirestore, private val auth: FirebaseAuth) {
    private fun col(uid: String) = db.collection(Col.USERS).document(uid).collection(Col.NOTIFICATIONS)

    fun notifications(uid: String): Flow<List<AppNotification>> =
        col(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(100).listFlow()

    // Same ordered window as the live listener; an unordered unread limit can hide new alerts.
    suspend fun latest(uid: String): List<AppNotification> =
        col(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(100).get().await().toObjects(AppNotification::class.java)

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
        if (dedupeKey != null) {
            // A fixed document id makes repeats harmless: writing over an existing alert is an update, which the
            // rules refuse, so each person gets one alert per key until they read and remove it.
            coroutineScope {
                targets.map { uid ->
                    async {
                        runCatching {
                            col(uid).document(dedupeKey).set(
                                AppNotification(type = type, params = params, route = route, fromUid = me, createdAt = now()),
                            ).await()
                        }
                    }
                }.awaitAll()
            }
            return
        }
        runCatching {
            targets.chunked(400).forEach { chunk ->
                db.runBatch { b ->
                    chunk.forEach { uid ->
                        b.set(
                            col(uid).document(),
                            AppNotification(type = type, params = params, route = route, fromUid = me, createdAt = now()),
                        )
                    }
                }.await()
            }
        }.onFailure { android.util.Log.w("CatMap", "Notification delivery failed", it) }
    }
}
