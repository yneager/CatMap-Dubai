package com.catmap.dubai.data

import com.catmap.dubai.R
import com.catmap.dubai.data.model.Booking
import com.catmap.dubai.data.model.BookingStatus
import com.catmap.dubai.data.model.Review
import com.catmap.dubai.data.model.SitterProfile
import com.catmap.dubai.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SittingRepository(
    private val db: FirebaseFirestore,
    private val notifications: NotificationRepository,
    private val moderation: ModerationRepository,
    private val scope: CoroutineScope,
) {
    private val sitters = db.collection(Col.SITTERS)
    private val bookings = db.collection(Col.BOOKINGS)
    private val reviews = db.collection(Col.REVIEWS)

    fun activeSitters(): Flow<List<SitterProfile>> =
        sitters.whereEqualTo("active", true).limit(500).listFlow()

    /** Every sitter profile for moderators, read a page at a time. */
    fun allSittersQuery(): Query = sitters.orderBy(com.google.firebase.firestore.FieldPath.documentId())

    private data class SharedSitter(val scope: CoroutineScope, val values: SharedFlow<SitterProfile?>, var refs: Int = 0)
    private val sitterStreams = HashMap<String, SharedSitter>()
    fun sitterFlow(uid: String): Flow<SitterProfile?> = flow {
        val owner = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val key = "$owner/$uid"
        val entry = synchronized(sitterStreams) {
            sitterStreams.getOrPut(key) {
                val child = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
                SharedSitter(child, sitters.document(uid).objectFlow<SitterProfile>().shareIn(child, SharingStarted.WhileSubscribed(0), 1))
            }.also { it.refs++ }
        }
        try { emitAll(entry.values) }
        finally { synchronized(sitterStreams) {
            entry.refs--
            if (entry.refs == 0) { sitterStreams.remove(key); entry.scope.cancel() }
        } }
    }

    suspend fun setVerified(sitter: SitterProfile, verified: Boolean, admin: UserProfile) {
        sitters.document(sitter.uid).update(mapOf("verified" to verified, "updatedAt" to now())).await()
        moderation.log(admin, if(verified) "sitter_verified" else "sitter_unverified", "sitter", sitter.uid, sitter.name, "")
    }

    suspend fun saveSitter(me: UserProfile, profile: SitterProfile) {
        val existing = runCatching { sitters.document(me.uid).get().await().toObject(SitterProfile::class.java) }.getOrNull()
        val hood = Dubai.nearest(profile.lat, profile.lng).key
        val data = mapOf(
            "name" to me.username, "photoId" to me.photoId,
            "headline" to profile.headline.trim().take(80), "description" to profile.description.trim().take(1500),
            "experience" to profile.experience.trim().take(600), "petTypes" to profile.petTypes, "services" to profile.services,
            "availability" to profile.availability.trim().take(300), "rate" to profile.rate.coerceIn(0, 99_999),
            "rateUnit" to profile.rateUnit, "lat" to profile.lat, "lng" to profile.lng, "neighborhood" to hood,
            "active" to profile.active, "updatedAt" to now(),
        )
        if (existing == null) {
            sitters.document(me.uid).set(data + mapOf("ratingSum" to 0L, "ratingCount" to 0L, "lastReviewId" to "", "createdAt" to now())).await()
        } else {
            sitters.document(me.uid).update(data).await()
        }
    }

    suspend fun setActive(uid: String, active: Boolean) {
        sitters.document(uid).update(mapOf("active" to active, "updatedAt" to now())).await()
    }

    suspend fun moderatorRemove(sitter: SitterProfile, moderator: UserProfile, note: String) {
        sitters.document(sitter.uid).delete().await()
        moderation.log(moderator, "sitter_removed", "sitter", sitter.uid, sitter.name, note)
        notifications.send(listOf(sitter.uid), NotifType.MODERATION, mapOf("target" to "sitter", "label" to sitter.name, "decision" to "removed"))
    }

    // ---------- bookings ----------

    fun bookingsQuery(uid: String, asSitter: Boolean): Query = bookings.whereEqualTo(if (asSitter) "sitterId" else "ownerId", uid).orderBy("createdAt", Query.Direction.DESCENDING)
    fun reviewsQuery(uid: String): Query = reviews.whereEqualTo("sitterId", uid).orderBy("createdAt", Query.Direction.DESCENDING)

    fun asOwner(uid: String): Flow<List<Booking>> =
        bookingsQuery(uid, false).limit(50).listFlow<Booking>().map { l -> l.sortedByDescending { it.createdAt } }

    fun asSitter(uid: String): Flow<List<Booking>> =
        bookingsQuery(uid, true).limit(50).listFlow<Booking>().map { l -> l.sortedByDescending { it.createdAt } }


    fun bookingFlow(id: String): Flow<Booking?> = bookings.document(id).objectFlow()

    suspend fun requestBooking(sitter: SitterProfile, petTypes: List<String>, petCount: Long, startAt: Long, endAt: Long, notes: String, me: UserProfile): String {
        if (sitter.uid == me.uid) throw AppException(R.string.err_own_listing)
        if (endAt <= startAt) throw AppException(R.string.err_dates)
        val ref = bookings.document()
        db.runBatch { b ->
            b.set(
                ref,
                Booking(
                    ownerId = me.uid, ownerName = me.username, sitterId = sitter.uid, sitterName = sitter.name,
                    petTypes = petTypes, petCount = petCount.coerceIn(1, 20), startAt = startAt, endAt = endAt,
                    notes = notes.trim().take(1000), createdAt = now(), updatedAt = now(),
                ),
            )
            b.update(db.collection(Col.USERS).document(me.uid), "lastPostAt", com.google.firebase.firestore.FieldValue.serverTimestamp())
        }.await()
        notifications.send(listOf(sitter.uid), NotifType.BOOKING_REQUEST, mapOf("user" to me.username), "bookings?tab=sitter", sourceId = ref.id)
        return ref.id
    }

    suspend fun setStatus(booking: Booking, status: BookingStatus, me: UserProfile, note: String = "") {
        val updates = mutableMapOf<String, Any>("status" to status.key, "updatedAt" to now())
        if (me.uid == booking.sitterId && note.isNotBlank()) updates["sitterNote"] = note.trim().take(500)
        bookings.document(booking.id).update(updates).await()
        val other = if (me.uid == booking.sitterId) booking.ownerId else booking.sitterId
        val route = if (other == booking.sitterId) "bookings?tab=sitter" else "bookings"
        notifications.send(listOf(other), NotifType.BOOKING_UPDATE, mapOf("user" to me.username, "status" to status.key), route, sourceId = booking.id)
    }

    // ---------- reviews ----------

    fun reviewsFor(sitterId: String): Flow<List<Review>> =
        reviewsQuery(sitterId).limit(50).listFlow<Review>().map { l -> l.sortedByDescending { it.createdAt } }

    /** One review per completed booking; the sitter's average is updated in the same batch. */
    suspend fun review(booking: Booking, rating: Int, text: String, me: UserProfile) {
        val sitterRef = sitters.document(booking.sitterId)
        db.runTransaction { tx ->
            val sitter = tx.get(sitterRef)
            tx.set(
                reviews.document(booking.id),
                Review(sitterId = booking.sitterId, authorId = me.uid, authorName = me.username, rating = rating.toLong().coerceIn(1, 5), text = text.trim().take(800), createdAt = now()),
            )
            if (sitter.exists()) {
                tx.update(
                    sitterRef,
                    mapOf(
                        "ratingSum" to (sitter.getLong("ratingSum") ?: 0) + rating.coerceIn(1, 5),
                        "ratingCount" to (sitter.getLong("ratingCount") ?: 0) + 1,
                        "lastReviewId" to booking.id,
                        "updatedAt" to now(),
                    ),
                )
            }
            tx.update(bookings.document(booking.id), mapOf("reviewed" to true, "updatedAt" to now()))
        }.await()
        notifications.send(listOf(booking.sitterId), NotifType.REVIEW, mapOf("user" to me.username, "rating" to rating.toString()), "sitter/${booking.sitterId}", sourceId = booking.id)
    }
}
