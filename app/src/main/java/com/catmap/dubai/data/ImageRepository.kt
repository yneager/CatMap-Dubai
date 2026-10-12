package com.catmap.dubai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.catmap.dubai.util.ProcessedPhoto
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Photos are stored as JPEG blobs in the `images` collection (full size under `{id}`, thumbnail
 * under `{id}_t`). Keeping them in Firestore means the app runs on Firebase's free plan without
 * Cloud Storage, and access is governed by the same security rules as everything else.
 */
class ImageRepository(context: Context, private val db: FirebaseFirestore) {
    private val disk = ImageDiskCache(File(context.cacheDir, "images"))
    private val loads = Array(32) { Mutex() }
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).coerceAtMost(64 * 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private fun docId(id: String, thumb: Boolean) = if (thumb) "${id}_t" else id

    fun cached(id: String, thumb: Boolean): Bitmap? = if (id.isBlank()) null else memory.get(docId(id, thumb))

    suspend fun bytes(id: String, thumb: Boolean = false): ByteArray? = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext null
        val key = docId(id, thumb)
        disk.read(key)?.let { return@withContext it }
        val blob = try { db.collection(Col.IMAGES).document(key).get().await().getBlob("data") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            ?: if (thumb) return@withContext bytes(id, thumb = false) else return@withContext null
        blob.toBytes().also { disk.write(key, it) }
    }

    suspend fun bitmap(id: String, thumb: Boolean): Bitmap? {
        cached(id, thumb)?.let { return it }
        val key = docId(id, thumb)
        return loads[(key.hashCode() and Int.MAX_VALUE) % loads.size].withLock {
            cached(id, thumb)?.let { return@withLock it }
            val data = bytes(id, thumb) ?: return@withLock null
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.also { memory.put(key, it) }
            }
        }
    }

    /** Uploads one photo and returns its id. */
    suspend fun upload(ownerId: String, photo: ProcessedPhoto): String {
        val ref = db.collection(Col.IMAGES).document()
        val created = now()
        db.runBatch { batch ->
            batch.set(ref, mapOf("data" to Blob.fromBytes(photo.full), "ownerId" to ownerId, "createdAt" to created))
            batch.set(
                db.collection(Col.IMAGES).document("${ref.id}_t"),
                mapOf("data" to Blob.fromBytes(photo.thumb), "ownerId" to ownerId, "createdAt" to created),
            )
        }.await()
        withContext(Dispatchers.IO) {
            disk.write(ref.id, photo.full)
            disk.write("${ref.id}_t", photo.thumb)
        }
        return ref.id
    }

    suspend fun delete(id: String) {
        db.runBatch { batch ->
            batch.delete(db.collection(Col.IMAGES).document(id))
            batch.delete(db.collection(Col.IMAGES).document("${id}_t"))
        }.await()
        memory.remove(id)
        memory.remove("${id}_t")
        withContext(Dispatchers.IO) {
            disk.remove(id)
            disk.remove("${id}_t")
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        memory.evictAll()
        disk.clear()
    }
}
