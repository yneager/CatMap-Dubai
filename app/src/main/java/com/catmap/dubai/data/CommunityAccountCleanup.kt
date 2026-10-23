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
            try { db.runTransaction {tx ->
                val fresh=tx.get(update.reference)
                val parent=update.reference.parent.parent ?: return@runTransaction
                val post=tx.get(parent)
                if(fresh.exists()) {
                    tx.delete(update.reference)
                    if(post.exists()) tx.update(parent, mapOf("updatesCount" to ((post.getLong("updatesCount") ?: 0)-1).coerceAtLeast(0), "counterSourceId" to update.id))
                }
            }.await() } catch (error: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (error.code != com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
                db.runBatch { b ->
                    b.delete(update.reference)
                    val postId=update.reference.path.split('/')[1]
                    b.set(db.collection("updateRepairs").document("${postId.toByteArray(Charsets.UTF_8).size}_${postId}_${update.id}"), mapOf("postId" to postId, "updateId" to update.id, "byUid" to uid))
                }.await()
            }
        }
        val posts=db.collection(Col.POSTS).whereEqualTo("ownerId",uid).get().await().documents
        for(post in posts) {
            DeletionJournal(db).post(post.id)
            // The durable worker job retains child photo references until their cleanup succeeds.
            post.reference.delete().await()
        }
        db.collection(Col.MATCHES).whereArrayContains("members", uid).get().await().documents.forEach { it.reference.delete().await() }
        // Include manifests from an earlier failed attempt, after their parents have already disappeared.
        for (post in db.collection("postDeletions").whereEqualTo("ownerId", uid).get().await().documents) {
            videos.deleteRequired(post.getString("videoPath").orEmpty())
            db.collection(Col.POST_EMBEDDINGS).document(post.id).delete().await()
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
            db.runTransaction { tx ->
                val fresh=tx.get(chat.reference)
                val count=((fresh.get("unread") as? Map<*,*>)?.get(uid) as? Number)?.toLong() ?: 0L
                val fields=mutableMapOf<String,Any>("names.$uid" to "","photos.$uid" to "","unread.$uid" to 0L)
                if(fresh.getString("lastSender")==uid) {fields["lastText"]=""; fields["lastSender"]=""; fields["lastMessageId"]=""}
                tx.update(chat.reference, fields)
                if(count>0) tx.update(db.collection("userLimits").document(uid), mapOf("unreadCount" to com.google.firebase.firestore.FieldValue.increment(-count), "unreadChatId" to chat.id))
            }.await()
        }
        for(lease in db.collection("videoUploads").whereEqualTo("ownerId",uid).get().await().documents) {
            videos.deleteRequired(lease.getString("path").orEmpty())
            lease.reference.delete().await()
        }
        db.collection("mediaUploads").whereEqualTo("ownerId",uid).get().await().documents.chunked(200).forEach { docs -> db.runBatch {b ->docs.forEach {b.delete(it.reference)}}.await() }
        db.collection(Col.SITTERS).document(uid).get().await().takeIf {it.exists()}?.reference?.delete()?.await()
        db.collection(Col.AREA_FOLLOWS).document(uid).delete().await()
        for(collection in listOf(Col.FAVORITES,Col.BLOCKED,Col.PRIVATE,"notificationReceipts")) {
            user.collection(collection).get().await().documents.chunked(200).forEach {docs ->db.runBatch {batch ->docs.forEach {batch.delete(it.reference)}}.await()}
        }
    }
}
