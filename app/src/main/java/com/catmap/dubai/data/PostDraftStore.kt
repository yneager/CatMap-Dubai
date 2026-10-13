package com.catmap.dubai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64

private val Context.draftDataStore by preferencesDataStore("post_drafts")

data class SavedPostDraft(val fields: Map<String, String>, val photos: List<String>)

/** Versioned encoding with bounded reads; no Android or Firebase dependencies. */
object PostDraftCodec {
    fun encode(draft: SavedPostDraft): String {
        require(draft.fields.size <= 64 && draft.photos.size <= 6)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(draft.fields.size)
            draft.fields.toSortedMap().forEach { (key, value) -> out.writeUTF(key); out.writeUTF(value) }
            out.writeInt(draft.photos.size)
            draft.photos.forEach { out.writeUTF(it) }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(value: String): SavedPostDraft? = runCatching {
        require(value.length <= 200_000)
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(value))).use { input ->
            require(input.readInt() == 1)
            val count = input.readInt().also { require(it in 0..64) }
            val fields = buildMap { repeat(count) { put(input.readUTF().also { require(it.length <= 60) }, input.readUTF().also { require(it.length <= 20_000) }) } }
            val photos = List(input.readInt().also { require(it in 0..6) }) { input.readUTF().also { require(it.matches(Regex("(local|remote):[A-Za-z0-9_-]{1,128}"))) } }
            require(input.available() == 0)
            SavedPostDraft(fields, photos)
        }
    }.getOrNull()
}

class PostDraftStore(private val context: Context) {
    private val mutex = Mutex()
    private fun key(uid: String) = stringPreferencesKey("draft_$uid")
    private fun directory(uid: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "drafts/$hash")
    }

    suspend fun load(uid: String): SavedPostDraft? = context.draftDataStore.data.first()[key(uid)]?.let(PostDraftCodec::decode)

    suspend fun save(uid: String, draft: SavedPostDraft, photos: Map<String, ByteArray>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = directory(uid).apply { mkdirs() }
            photos.forEach { (id, bytes) ->
                require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
                val target = File(dir, "$id.jpg")
                if (!target.exists()) target.writeBytes(bytes)
            }
            val retained=draft.photos.filter {it.startsWith("local:")}.map {it.substringAfter(':')+".jpg"}.toSet()
            dir.listFiles()?.filter {it.isFile && it.name !in retained}?.forEach {it.delete()}
        }
        context.draftDataStore.edit { it[key(uid)] = PostDraftCodec.encode(draft) }
    }

    suspend fun photo(uid: String, id: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return@withContext null
        runCatching { File(directory(uid), "$id.jpg").takeIf { it.isFile }?.readBytes() }.getOrNull()
    }

    suspend fun clear(uid: String) = mutex.withLock {
        context.draftDataStore.edit { it.remove(key(uid)) }
        withContext(Dispatchers.IO) { directory(uid).listFiles()?.filter { it.isFile }?.forEach { it.delete() } }
    }
}
