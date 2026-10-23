package com.catmap.dubai.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Remove authored community data; preserve the counterpart's booking/review history without names. */
class CommunityAccountCleanup(private val db:FirebaseFirestore, private val videos: VideoRepository) {
    private suspend fun delete(collection:String,uid:String,field:String) {
        db.collection(collection).whereEqualTo(field,uid).get().await().documents.chunked(200).forEach { docs ->
            db.runBatch {batch -> docs.forEach {batch.delete(it.reference)}}.await()
        }
    }
    suspend fun clean(uid:String) {
        val user=db.collection(Col.USERS).document(uid)
        val authored=db.collectionGroup(Col.UPDATES).whereEqualTo("byUid",uid).get().await().documents
        for(update in authored.filter {it.reference.path.split('/').let {parts ->parts.size==4 && parts.first()==Col.POSTS}}) {
            db.runTransaction {tx ->
                val fresh=tx.get(update.reference)
                val parent=update.reference.parent.parent ?: return@runTransaction
                val post=tx.get(parent)
                if(fresh.exists()) {
                    tx.delete(update.reference)
                    if(post.exists()) tx.update(parent,"updatesCount",((post.getLong("updatesCount") ?: 0)-1).coerceAtLeast(0))
                }
            }.await()
        }
        val posts=db.collection(Col.POSTS).whereEqualTo("ownerId",uid).get().await().documents
        for(post in posts) {
            post.reference.collection(Col.UPDATES).get().await().documents.chunked(200).forEach { docs -> db.runBatch {batch ->docs.forEach {batch.delete(it.reference)}}.await() }
            post.reference.delete().await()
        }
        runCatching {
            db.collection(Col.MATCHES).whereArrayContains("members", uid).get().await().documents.forEach {
                runCatching { it.reference.delete().await() }.onFailure { error -> android.util.Log.i("CatMap", "Account match cleanup failed", error) }
            }
        }.onFailure { android.util.Log.i("CatMap", "Account match lookup failed", it) }
        for (post in posts) {
            runCatching { videos.delete(post.getString("videoPath").orEmpty()) }
                .onFailure { android.util.Log.i("CatMap", "Account video cleanup failed", it) }
            runCatching { db.collection(Col.POST_EMBEDDINGS).document(post.id).delete().await() }
                .onFailure { android.util.Log.i("CatMap", "Embedding cleanup failed", it) }
        }
        delete(Col.ADOPTION_REQUESTS,uid,"requesterId")
        delete(Col.ADOPTION_REQUESTS,uid,"ownerId")
        for((field,name,note) in listOf(Triple("ownerId","ownerName","notes"),Triple("sitterId","sitterName","sitterNote"))) {
            db.collection(Col.BOOKINGS).whereEqualTo(field,uid).get().await().documents.forEach {it.reference.update(mapOf(name to "",note to "")).await()}
        }
        db.collection(Col.REVIEWS).whereEqualTo("authorId",uid).get().await().documents.forEach {it.reference.update(mapOf("authorName" to "","text" to "")).await()}
        val chats=db.collection(Col.CHATS).whereArrayContains("members",uid).get().await().documents
        for(chat in chats) {
            chat.reference.collection(Col.MESSAGES).whereEqualTo("senderId",uid).get().await().documents.chunked(200).forEach {docs ->db.runBatch {batch ->docs.forEach {batch.delete(it.reference)}}.await()}
            val fields=mutableMapOf<String,Any>("names.$uid" to "","photos.$uid" to "","unread.$uid" to 0L)
            if(chat.getString("lastSender")==uid) {fields["lastText"]=""; fields["lastSender"]=""}
            chat.reference.update(fields).await()
        }
        db.collection(Col.SITTERS).document(uid).get().await().takeIf {it.exists()}?.reference?.delete()?.await()
        db.collection(Col.AREA_FOLLOWS).document(uid).delete().await()
        for(collection in listOf(Col.FAVORITES,Col.BLOCKED,Col.PRIVATE)) {
            user.collection(collection).get().await().documents.chunked(200).forEach {docs ->db.runBatch {batch ->docs.forEach {batch.delete(it.reference)}}.await()}
        }
    }
}
