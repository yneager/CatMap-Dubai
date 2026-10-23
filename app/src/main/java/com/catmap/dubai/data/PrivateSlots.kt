package com.catmap.dubai.data

import com.catmap.dubai.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Fixed account capacity is checked in the same transaction as its source write. */
class PrivateSlots(private val db: FirebaseFirestore) {
    suspend fun set(uid: String, bucket: String, id: String, value: Map<String, Any>?) {
        require(bucket in listOf("favorites", "blocked"))
        db.runTransaction { tx ->
            val target = db.collection(Col.USERS).document(uid).collection(bucket).document(id)
            val limits = db.collection("userLimits").document(uid)
            val current = tx.get(target)
            val quota = tx.get(limits)
            if (current.exists() != (value != null)) {
                check(quota.exists()) { "Account limits must be migrated before rollout" }
                val count = (quota.getLong(bucket) ?: 0L) + if (value == null) -1 else 1
                if (count !in 0L..500L) throw AppException(R.string.err_too_many_requests)
                tx.update(limits, mapOf(bucket to count, "${bucket}Source" to id))
                if (value == null) tx.delete(target) else tx.set(target, value)
            }
        }.await()
    }
}
