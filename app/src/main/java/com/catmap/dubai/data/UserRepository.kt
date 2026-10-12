package com.catmap.dubai.data

import com.catmap.dubai.R
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.gamification.Badge
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class UserRepository(private val db: FirebaseFirestore, private val notifications: NotificationRepository) {
    fun ref(uid: String) = db.collection(Col.USERS).document(uid)

    fun profileFlow(uid: String): Flow<UserProfile?> = ref(uid).objectFlow()

    suspend fun profile(uid: String): UserProfile? =
        runCatching { ref(uid).get().await().toObject(UserProfile::class.java) }.getOrNull()

    fun usersWithRole(role: String): Flow<List<UserProfile>> =
        db.collection(Col.USERS).whereEqualTo("role", role).listFlow()

    suspend fun uidsWithRoles(vararg roles: String): List<String> =
        db.collection(Col.USERS).whereIn("role", roles.toList()).get().await().documents.map { it.id }

    suspend fun findByUsername(username: String): UserProfile? {
        val key = username.trim().removePrefix("@").lowercase()
        if (key.isEmpty()) return null
        val uid = db.collection(Col.USERNAMES).document(key).get().await().getString("uid") ?: return null
        return profile(uid)
    }

    suspend fun isUsernameFree(username: String): Boolean =
        !db.collection(Col.USERNAMES).document(username.trim().lowercase()).get().await().exists()

    suspend fun updateProfile(current: UserProfile, username: String, displayName: String, bio: String, photoId: String) {
        val newName = username.trim()
        validateUsername(newName)
        val changingName = !newName.equals(current.username, ignoreCase = true)
        if (changingName && !isUsernameFree(newName)) throw AppException(R.string.err_username_taken)
        db.runBatch { b ->
            if (changingName) {
                b.set(db.collection(Col.USERNAMES).document(newName.lowercase()), mapOf("uid" to current.uid))
                if (current.username.isNotBlank()) {
                    b.delete(db.collection(Col.USERNAMES).document(current.username.lowercase()))
                }
            }
            b.update(
                ref(current.uid),
                mapOf(
                    "username" to newName,
                    "displayName" to displayName.trim().take(60),
                    "bio" to bio.trim().take(300),
                    "photoId" to photoId,
                ),
            )
        }.await()
    }

    suspend fun setShowStats(uid: String, show: Boolean) {
        ref(uid).update("showStatsPublicly", show).await()
    }

    suspend fun setFollowing(uid: String, catId: String, follow: Boolean) {
        val op = if (follow) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
        val catOp = if (follow) FieldValue.arrayUnion(catId) else FieldValue.arrayRemove(catId)
        db.runBatch { b ->
            b.update(db.collection(Col.CATS).document(catId), "followers", op)
            b.update(ref(uid), "followedCats", catOp)
        }.await()
    }

    /**
     * Adds counter increments to [batch]. [rateLimited] also stamps `lastPostAt` with the server
     * time, which the security rules use to reject posts sent less than a few seconds apart.
     */
    fun addStats(
        batch: WriteBatch,
        uid: String,
        stats: Map<String, Long>,
        points: Long,
        neighborhood: String? = null,
        rateLimited: Boolean = false,
    ) {
        val updates = mutableMapOf<String, Any>()
        stats.filterValues { it != 0L }.forEach { (k, v) -> updates["stats.$k"] = FieldValue.increment(v) }
        if (points != 0L) updates["points"] = FieldValue.increment(points)
        if (!neighborhood.isNullOrBlank() && neighborhood != "other") {
            updates["neighborhoods"] = FieldValue.arrayUnion(neighborhood)
        }
        if (rateLimited) updates["lastPostAt"] = FieldValue.serverTimestamp()
        if (updates.isNotEmpty()) batch.update(ref(uid), updates)
    }

    /** Called whenever the signed-in user's profile changes; awards any badge they just reached. */
    suspend fun awardNewBadges(profile: UserProfile) {
        val earned = Badge.newlyEarned(profile)
        if (earned.isEmpty()) return
        ref(profile.uid).update("badges", FieldValue.arrayUnion(*earned.map { it.key }.toTypedArray())).await()
        earned.forEach { badge ->
            notifications.send(listOf(profile.uid), NotifType.ACHIEVEMENT, mapOf("badge" to badge.key), "achievements")
        }
    }

    suspend fun setRole(uid: String, role: String, volunteerStatus: String? = null) {
        val updates = mutableMapOf<String, Any>("role" to role)
        if (volunteerStatus != null) updates["volunteerStatus"] = volunteerStatus
        ref(uid).update(updates).await()
    }

    companion object {
        private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_.]{3,20}$")

        fun validateUsername(name: String) {
            if (!USERNAME_PATTERN.matches(name)) throw AppException(R.string.err_username_format)
        }
    }
}
