package com.catmap.dubai.data

import com.catmap.dubai.R
import com.catmap.dubai.data.model.Chat
import com.catmap.dubai.data.model.Message
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** One-to-one messaging. Chats are only readable by their two members (see firestore.rules). */
class ChatRepository(
    private val db: FirebaseFirestore,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
) {
    private val chats = db.collection(Col.CHATS)

    fun chatId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    fun chatsFor(uid: String): Flow<List<Chat>> =
        chats.whereArrayContains("members", uid).listFlow<Chat>().map { l -> l.sortedByDescending { it.lastAt } }

    fun chatFlow(id: String): Flow<Chat?> = chats.document(id).objectFlow()

    fun messages(chatId: String): Flow<List<Message>> =
        chats.document(chatId).collection(Col.MESSAGES).orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(300).listFlow()

    /** Returns the chat between the two people, creating it the first time. */
    suspend fun open(me: UserProfile, otherUid: String): String {
        if (otherUid == me.uid || otherUid.isBlank()) throw AppException(R.string.err_message_self)
        val id = chatId(me.uid, otherUid)
        val ref = chats.document(id)
        // The rules let anyone check that a chat id is unused, so a missing chat reads as "does not exist".
        // Any other failure (e.g. offline) propagates rather than risk overwriting an existing chat.
        val exists = ref.get().await().exists()
        if (!exists) {
            val other = users.profile(otherUid)
            val members = listOf(me.uid, otherUid).sorted()
            ref.set(
                Chat(
                    members = members,
                    names = mapOf(me.uid to me.username, otherUid to (other?.username ?: "")),
                    photos = mapOf(me.uid to me.photoId, otherUid to (other?.photoId ?: "")),
                    lastAt = now(), unread = members.associateWith { 0L },
                ),
            ).await()
        }
        return id
    }

    suspend fun send(chat: Chat, me: UserProfile, text: String, imageId: String = "", post: Post? = null) {
        val clean = text.trim().take(2000)
        if (clean.isEmpty() && imageId.isEmpty() && post == null) return
        val other = chat.other(me.uid)
        val messageRef = chats.document(chat.id).collection(Col.MESSAGES).document()
        val preview = when {
            clean.isNotEmpty() -> clean.take(120)
            imageId.isNotEmpty() -> "📷"
            else -> "🐾 " + post?.name.orEmpty()
        }
        db.runBatch { b ->
            b.set(
                messageRef,
                Message(
                    senderId = me.uid, text = clean, imageId = imageId,
                    postId = post?.id.orEmpty(), postTitle = post?.name.orEmpty(), postPhotoId = post?.photoIds?.firstOrNull().orEmpty(),
                    createdAt = now(),
                ),
            )
            b.update(
                chats.document(chat.id),
                mapOf(
                    "lastText" to preview, "lastSender" to me.uid, "lastAt" to now(),
                    "unread.$other" to FieldValue.increment(1),
                    "names.${me.uid}" to me.username, "photos.${me.uid}" to me.photoId,
                ),
            )
        }.await()
        notifications.send(listOf(other), NotifType.MESSAGE, mapOf("user" to me.username, "text" to preview), "chat/${chat.id}", dedupeKey = "msg_${chat.id}")
    }

    suspend fun markRead(chatId: String, uid: String) {
        runCatching { chats.document(chatId).update("unread.$uid", 0L).await() }
        notifications.dismiss(uid, "msg_$chatId")
    }

    suspend fun deleteMessage(chatId: String, message: Message) {
        chats.document(chatId).collection(Col.MESSAGES).document(message.id).delete().await()
    }

    // ---------- blocking ----------

    private fun blockedCol(uid: String) = db.collection(Col.USERS).document(uid).collection(Col.BLOCKED)

    fun blocked(uid: String): Flow<Set<String>> = blockedCol(uid).listFlow<BlockEntry>().map { l -> l.map { it.id }.toSet() }

    suspend fun block(me: String, other: String) {
        blockedCol(me).document(other).set(BlockEntry(at = now())).await()
    }

    suspend fun unblock(me: String, other: String) {
        blockedCol(me).document(other).delete().await()
    }
}

data class BlockEntry(
    @com.google.firebase.firestore.DocumentId var id: String = "",
    var at: Long = 0,
)
