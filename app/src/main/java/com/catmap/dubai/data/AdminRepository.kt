package com.catmap.dubai.data

import com.catmap.dubai.data.model.AdoptionStatus
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.data.model.UserProfile
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** Basic app statistics for the admin dashboard, computed with server-side count queries. */
data class AppStats(
    val users: Long = 0,
    val posts: Long = 0,
    val byCategory: Map<PostCategory, Long> = emptyMap(),
    val openLost: Long = 0,
    val reunited: Long = 0,
    val adoptionAvailable: Long = 0,
    val adopted: Long = 0,
    val sitters: Long = 0,
    val bookings: Long = 0,
    val cats: Long = 0,
    val openReports: Long = 0,
    val hiddenPosts: Long = 0,
    val suspendedUsers: Long = 0,
)

class AdminRepository(
    private val db: FirebaseFirestore,
    private val moderation: ModerationRepository,
    private val notifications: NotificationRepository,
) {
    /** A moderator records that a reunion credited to [match] was checked independently (e.g. with both people). */
    suspend fun verifyReunion(match: com.catmap.dubai.data.model.PostMatch, moderator: com.catmap.dubai.data.model.UserProfile) {
        db.collection(Col.MATCHES).document(match.id).update(mapOf("verifiedBy" to moderator.uid, "verifiedAt" to now())).await()
        moderation.log(moderator, "reunion_verified", "match", match.id, match.lostPostId, "")
    }

    private suspend fun count(q: Query): Long = runCatching { q.count().get(AggregateSource.SERVER).await().count }.getOrDefault(0)

    suspend fun stats(): AppStats = coroutineScope {
        val posts = db.collection(Col.POSTS)
        val categories = PostCategory.entries.associateWith { c -> async { count(posts.whereEqualTo("category", c.key)) } }
        val users = async { count(db.collection(Col.USERS)) }
        val total = async { count(posts) }
        val openLost = async { count(posts.whereEqualTo("category", PostCategory.LOST.key).whereEqualTo("status", PostStatus.OPEN.key)) }
        val reunited = async { count(posts.whereEqualTo("status", PostStatus.REUNITED.key)) }
        val available = async { count(posts.whereEqualTo("category", PostCategory.ADOPTION.key).whereEqualTo("adoptionStatus", AdoptionStatus.AVAILABLE.key)) }
        val adopted = async { count(posts.whereEqualTo("adoptionStatus", AdoptionStatus.ADOPTED.key)) }
        val sitters = async { count(db.collection(Col.SITTERS)) }
        val bookings = async { count(db.collection(Col.BOOKINGS)) }
        val cats = async { count(db.collection(Col.CATS)) }
        val reports = async { count(db.collection(Col.REPORTS).whereEqualTo("status", "open")) }
        val hidden = async { count(posts.whereEqualTo("hidden", true)) }
        val suspended = async { count(db.collection(Col.USERS).whereEqualTo("suspended", true)) }
        AppStats(
            users = users.await(), posts = total.await(), byCategory = categories.mapValues { it.value.await() },
            openLost = openLost.await(), reunited = reunited.await(), adoptionAvailable = available.await(), adopted = adopted.await(),
            sitters = sitters.await(), bookings = bookings.await(), cats = cats.await(), openReports = reports.await(),
            hiddenPosts = hidden.await(), suspendedUsers = suspended.await(),
        )
    }

    /** Members for the admin list: newest first, or by username prefix, or suspended ones only. */
    fun usersQuery(usernamePrefix: String, suspendedOnly: Boolean): Query {
        val users = db.collection(Col.USERS)
        return when {
            usernamePrefix.isNotEmpty() -> users.orderBy("username").startAt(usernamePrefix).endAt(usernamePrefix + "\uf8ff")
            suspendedOnly -> users.whereEqualTo("suspended", true)
            else -> users.orderBy("createdAt", Query.Direction.DESCENDING)
        }
    }

    suspend fun setSuspended(user: UserProfile, suspended: Boolean, admin: UserProfile, note: String) {
        db.collection(Col.USERS).document(user.uid).update("suspended", suspended).await()
        moderation.log(admin, if (suspended) "user_suspended" else "user_restored", "user", user.uid, user.username, note)
        notifications.send(
            listOf(user.uid), NotifType.MODERATION,
            mapOf("target" to "account", "label" to user.username, "decision" to if (suspended) "suspended" else "restored"),
        )
    }
}
