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
class ImageRepository(context: Context, private val db: FirebaseFirestore?, private val byteSource:(suspend (String)->ByteArray?)?=null) {
    private val disk = ImageDiskCache(File(context.cacheDir, "images"))
    private val loads = Array(32) { Mutex() }
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).coerceAtMost(64 * 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private fun docId(id: String, thumb: Boolean) = if (thumb) "${id}_t" else id

    private fun memoryKey(id:String,thumb:Boolean,target:Int)=docId(id,thumb)+if(target>0) "@$target" else ""
    fun cached(id: String, thumb: Boolean, target:Int=0): Bitmap? = if (id.isBlank()) null else memory.get(memoryKey(id,thumb,target))

    suspend fun bytes(id: String, thumb: Boolean = false): ByteArray? = withContext(Dispatchers.IO) {
        if (id.isBlank()) return@withContext null
        val key = docId(id, thumb)
        disk.read(key)?.let { return@withContext it }
        val data = try { byteSource?.let { it(key) } ?: requireNotNull(db).collection(Col.IMAGES).document(key).get().await().getBlob("data")?.toBytes() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            ?: if (thumb) return@withContext bytes(id, thumb = false) else return@withContext null
        data.also { disk.write(key, it) }
    }

    suspend fun bitmap(id: String, thumb: Boolean, target:Int=0): Bitmap? {
        cached(id, thumb,target)?.let { return it }
        val key = docId(id, thumb)
        return loads[(key.hashCode() and Int.MAX_VALUE) % loads.size].withLock {
            cached(id, thumb,target)?.let { return@withLock it }
            val data = bytes(id, thumb) ?: return@withLock null
            withContext(Dispatchers.Default) {
                val options=BitmapFactory.Options()
                if(target>0) {
                    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    BitmapFactory.decodeByteArray(data,0,data.size,bounds)
                    var sample=1
                    while(maxOf(bounds.outWidth,bounds.outHeight)/(sample*2)>=target) sample*=2
                    options.inSampleSize=sample
                }
                BitmapFactory.decodeByteArray(data, 0, data.size,options)?.also { memory.put(memoryKey(id,thumb,target), it) }
            }
        }
    }

    /** Uploads one photo and returns its id. */
    suspend fun upload(ownerId: String, photo: ProcessedPhoto): String {
        val db=requireNotNull(db)
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
        val db=requireNotNull(db)
        db.runBatch { batch ->
            batch.delete(db.collection(Col.IMAGES).document(id))
            batch.delete(db.collection(Col.IMAGES).document("${id}_t"))
        }.await()
        memory.remove(id)
        memory.remove("${id}_t")
        memory.snapshot().keys.filter {it.startsWith("$id@")||it.startsWith("${id}_t@")} .forEach(memory::remove)
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
