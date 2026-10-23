package com.catmap.dubai.data

import com.catmap.dubai.data.model.Favorite
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Saved posts, private to each person (users/{uid}/favorites/{postId}). */
class FavoritesRepository(private val db: FirebaseFirestore) {
    private fun col(uid: String) = db.collection(Col.USERS).document(uid).collection(Col.FAVORITES)

    fun favorites(uid: String): Flow<List<Favorite>> =
        col(uid).orderBy("savedAt", Query.Direction.DESCENDING).limit(500).listFlow()

    fun ids(uid: String): Flow<Set<String>> = favorites(uid).map { l -> l.map { it.id }.toSet() }

    suspend fun set(uid: String, postId: String, saved: Boolean) {
        PrivateSlots(db).set(uid, Col.FAVORITES, postId, if (saved) mapOf("postId" to postId, "savedAt" to now()) else null)
    }
}
