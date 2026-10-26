package com.catmap.dubai.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.catmap.dubai.BuildConfig
import com.catmap.dubai.R
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Short videos live in Firebase Storage under videos/{uid}/{id}. Storage needs the Blaze plan on
 * real Firebase, so it is only switched on when [BuildConfig.STORAGE_ENABLED] (always on with the
 * local emulators, which are free).
 */
class VideoRepository(private val context: Context) {
    val enabled: Boolean get() = BuildConfig.STORAGE_ENABLED

    private val storage by lazy {
        FirebaseStorage.getInstance().apply {
            if (BuildConfig.EMULATOR_HOST.isNotEmpty()) useEmulator(BuildConfig.EMULATOR_HOST, 9199)
        }
    }

    /** Uploads a picked video and returns its storage path. */
    suspend fun upload(uid: String, uri: Uri): String {
        if (!enabled) throw AppException(R.string.err_video_unavailable)
        val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        } ?: -1L
        if (size > MAX_BYTES) throw AppException(R.string.err_video_too_big)
        val type = context.contentResolver.getType(uri) ?: "video/mp4"
        if (!type.startsWith("video/")) throw AppException(R.string.err_video_type)
        val path = "videos/$uid/${UUID.randomUUID()}"
        FirebaseFirestore.getInstance().collection("videoUploads").document(path.substringAfterLast('/'))
            .set(mapOf("ownerId" to uid, "path" to path, "status" to "staged", "expiresAt" to now() + 7L * 86_400_000)).await()
        storage.reference.child(path).putFile(uri, StorageMetadata.Builder().setContentType(type).build()).await()
        return path
    }

    suspend fun url(path: String): Uri? =
        if (!enabled || path.isBlank()) null else runCatching { storage.reference.child(path).downloadUrl.await() }.getOrNull()

    suspend fun delete(path: String) {
        if (enabled && path.isNotBlank()) runCatching { storage.reference.child(path).delete().await() }
            .onFailure { android.util.Log.i("CatMap", "Video cleanup failed", it) }
    }

    suspend fun deleteRequired(path: String) {
        if (path.isBlank()) return
        if (!enabled) throw AppException(R.string.err_video_unavailable)
        try { storage.reference.child(path).delete().await() }
        catch (error: com.google.firebase.storage.StorageException) {
            if (error.errorCode != com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND) throw error
        }
    }
    suspend fun retain(path: String) {
        if (path.isBlank()) return
        val db = FirebaseFirestore.getInstance()
        db.runTransaction { tx ->
            val lease = tx.get(db.collection("videoUploads").document(path.substringAfterLast('/')))
            if (lease.exists()) {
                check(lease.getString("status") in listOf("staged", "linked")) { "Staged video expired; select it again" }
                tx.update(lease.reference, "expiresAt", now() + 7L * 86_400_000)
            }
        }.await()
    }

    companion object {
        const val MAX_BYTES = 30L * 1024 * 1024
    }
}
