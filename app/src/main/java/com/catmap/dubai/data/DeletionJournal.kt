package com.catmap.dubai.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Capture cleanup IDs and close child creation before removing a parent. */
class DeletionJournal(private val db: FirebaseFirestore) {
    suspend fun post(id: String) {
        val target = db.collection("postDeletions").document(id)
        db.runTransaction { tx ->
            if (!tx.get(target).exists()) {
                val source = tx.get(db.collection(Col.POSTS).document(id))
                if (source.exists()) {
                    tx.set(target, mapOf("ownerId" to source.getString("ownerId").orEmpty(),
                        "actorId" to FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                        "photoIds" to (source.get("photoIds") as? List<*>).orEmpty(),
                        "videoPath" to source.getString("videoPath").orEmpty(), "createdAt" to now(), "status" to "pending"))
                    tx.update(source.reference, "hidden", true)
                }
            }
        }.await()
    }
}
