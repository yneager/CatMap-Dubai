package com.catmap.dubai.data

import com.catmap.dubai.R
import com.catmap.dubai.data.model.AdoptionRequest
import com.catmap.dubai.data.model.AdoptionStatus
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.RequestStatus
import com.catmap.dubai.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AdoptionRepository(
    private val db: FirebaseFirestore,
    private val notifications: NotificationRepository,
) {
    private val requests = db.collection(Col.ADOPTION_REQUESTS)

    fun requestId(postId: String, uid: String) = "${postId}_$uid"

    fun requestsQuery(uid: String, sent: Boolean): Query = requests.whereEqualTo(if (sent) "requesterId" else "ownerId", uid).orderBy("createdAt", Query.Direction.DESCENDING)

    fun received(uid: String): Flow<List<AdoptionRequest>> =
        requestsQuery(uid, false).limit(50).listFlow<AdoptionRequest>().map { l -> l.sortedByDescending { it.createdAt } }

    fun sent(uid: String): Flow<List<AdoptionRequest>> =
        requestsQuery(uid, true).limit(50).listFlow<AdoptionRequest>().map { l -> l.sortedByDescending { it.createdAt } }

    fun myRequestFor(postId: String, uid: String): Flow<AdoptionRequest?> = requests.document(requestId(postId, uid)).objectFlow()

    suspend fun request(post: Post, message: String, home: String, me: UserProfile) {
        if (post.ownerId == me.uid) throw AppException(R.string.err_own_listing)
        requests.document(requestId(post.id, me.uid)).set(
            AdoptionRequest(
                postId = post.id, postName = post.name, postPhotoId = post.photoIds.firstOrNull().orEmpty(),
                ownerId = post.ownerId, requesterId = me.uid, requesterName = me.username,
                message = message.trim().take(1000), home = home.trim().take(600), createdAt = now(),
            ),
        ).await()
        notifications.send(
            listOf(post.ownerId), NotifType.ADOPTION_REQUEST, mapOf("user" to me.username, "name" to post.name), "adoption-requests", sourceId = requestId(post.id, me.uid),
        )
    }

    /** Approving also marks the listing as pending, so others can see someone is being considered. */
    suspend fun decide(request: AdoptionRequest, approve: Boolean, note: String) {
        db.runBatch { b ->
            b.update(
                requests.document(request.id),
                mapOf(
                    "status" to if (approve) RequestStatus.APPROVED.key else RequestStatus.DECLINED.key,
                    "ownerNote" to note.trim().take(500), "decidedAt" to now(),
                ),
            )
            if (approve) {
                b.update(db.collection(Col.POSTS).document(request.postId), mapOf("adoptionStatus" to AdoptionStatus.PENDING.key, "updatedAt" to now()))
            }
        }.await()
        notifications.send(
            listOf(request.requesterId), NotifType.ADOPTION_DECISION,
            mapOf("name" to request.postName, "decision" to if (approve) "approved" else "declined"), "post/${request.postId}", sourceId = request.id,
        )
    }

    suspend fun delete(request: AdoptionRequest) {
        requests.document(request.id).delete().await()
    }

    suspend fun withdraw(request: AdoptionRequest) {
        requests.document(request.id).update("status", RequestStatus.WITHDRAWN.key).await()
    }
}
