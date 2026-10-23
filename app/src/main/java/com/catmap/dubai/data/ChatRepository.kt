package com.catmap.dubai.data

import com.catmap.dubai.R
import com.catmap.dubai.data.model.Chat
import com.catmap.dubai.data.model.Message
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** One-to-one messaging. Chats are only readable by their two members (see firestore.rules). */
class ChatRepository(
    private val db: FirebaseFirestore,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val images: ImageRepository,
) {
    private val chats = db.collection(Col.CHATS)

    fun chatId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    fun chatsFor(uid: String): Flow<List<Chat>> =
        chats.whereArrayContains("members", uid).listFlow<Chat>().map { l -> l.sortedByDescending { it.lastAt } }

    fun chatFlow(id: String): Flow<Chat?> = chats.document(id).objectFlow()

    /** The newest [MESSAGE_WINDOW] messages, live; older ones come from [olderMessages]. */
    fun messages(chatId: String): Flow<List<Message>> =
        chats.document(chatId).collection(Col.MESSAGES).orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(MESSAGE_WINDOW).listFlow()

    /** The [MESSAGE_WINDOW] messages before [beforeMillis], oldest first ("Show older messages"). */
    suspend fun olderMessages(chatId: String, beforeMillis: Long): List<Message> =
        chats.document(chatId).collection(Col.MESSAGES).orderBy("createdAt", Query.Direction.ASCENDING).endBefore(beforeMillis)
            .limitToLast(MESSAGE_WINDOW).get().await().safeObjects()

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
            imageId.isNotEmpty() -> "Photo"
            else -> "Post: " + post?.name.orEmpty()
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

    /** Whether this chat already has the card of [matchId] (so a match is introduced once per chat). */
    suspend fun hasMatchCard(chatId: String, matchId: String): Boolean =
        chats.document(chatId).collection(Col.MESSAGES).whereEqualTo("matchId", matchId).limit(1).get().await().isEmpty.not()

    /** A card with the lost animal's photo and the sighting's photo, which opens the match review. */
    suspend fun sendMatchCard(chatId: String, me: UserProfile, matchId: String, lost: Post?, sighting: Post?) {
        val chat = chats.document(chatId).get().await().toObject(Chat::class.java)?.apply { id = chatId } ?: return
        val other = chat.other(me.uid)
        val title = lost?.name.orEmpty()
        val messageRef = chats.document(chatId).collection(Col.MESSAGES).document()
        db.runBatch { b ->
            b.set(
                messageRef,
                Message(
                    senderId = me.uid, postId = lost?.id.orEmpty(), postTitle = title,
                    postPhotoId = lost?.photoIds?.firstOrNull().orEmpty(), matchId = matchId,
                    matchPhotoId = sighting?.photoIds?.firstOrNull().orEmpty(), createdAt = now(),
                ),
            )
            b.update(
                chats.document(chatId),
                mapOf(
                    "lastText" to "Possible match: $title".trim(), "lastSender" to me.uid, "lastAt" to now(),
                    "unread.$other" to FieldValue.increment(1), "names.${me.uid}" to me.username, "photos.${me.uid}" to me.photoId,
                ),
            )
        }.await()
    }

    suspend fun markRead(chatId: String, uid: String) {
        runCatching { chats.document(chatId).update("unread.$uid", 0L).await() }
        notifications.dismiss(uid, "msg_$chatId")
    }

    suspend fun deleteMessage(chatId: String, message: Message, uid: String) {
        val chatRef = chats.document(chatId)
        val messages = chatRef.collection(Col.MESSAGES)
        val before = runCatching { chatRef.get(Source.SERVER).await() }.getOrNull()
        val newest = runCatching {
            messages.orderBy("createdAt", Query.Direction.DESCENDING).limit(2).get(Source.SERVER).await().safeObjects<Message>()
        }.getOrDefault(emptyList())
        messages.document(message.id).delete().await()
        if (message.imageId.isNotBlank()) runCatching { images.delete(message.imageId) }
            .onFailure { android.util.Log.i("CatMap", "Message image cleanup failed", it) }
        if (before?.getString("lastSender") != uid || newest.firstOrNull()?.id != message.id) return
        // Updating only the preview satisfies chatUpdateOk; keep unread counts and ordering intact.
        runCatching {
            db.runTransaction { tx ->
                val fresh = tx.get(chatRef)
                if (fresh.getLong("lastAt") == before.getLong("lastAt") && fresh.getString("lastSender") == uid &&
                    fresh.getString("lastText") == before.getString("lastText")) {
                    tx.update(chatRef, "lastText", newest.getOrNull(1)?.let(::preview).orEmpty())
                }
            }.await()
        }.onFailure { android.util.Log.i("CatMap", "Message preview cleanup failed", it) }
    }

    private fun preview(message: Message): String = when {
        message.text.isNotBlank() -> message.text.take(120)
        message.imageId.isNotBlank() -> "Photo"
        message.matchId.isNotBlank() -> "Possible match: ${message.postTitle}".trim()
        else -> "Post: ${message.postTitle}"
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

    companion object {
        /** How many of the newest messages stay live in an open chat. */
        const val MESSAGE_WINDOW = 50L
    }
}

data class BlockEntry(
    @com.google.firebase.firestore.DocumentId var id: String = "",
    var at: Long = 0,
)
