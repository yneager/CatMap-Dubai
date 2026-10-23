package com.catmap.dubai.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object MediaLease {
    suspend fun retain(db: FirebaseFirestore, ids: List<String>) {
        for (chunk in ids.filter { it.isNotBlank() }.distinct().chunked(6)) db.runTransaction { tx ->
            val leases = chunk.map { tx.get(db.collection("mediaUploads").document(it)) }
            for (lease in leases) if (lease.exists()) {
                check(lease.getString("status") in listOf("staged", "linked")) { "Staged photo expired; select it again" }
                tx.update(lease.reference, "expiresAt", now() + 7L * 86_400_000)
            }
        }.await()
    }
}
