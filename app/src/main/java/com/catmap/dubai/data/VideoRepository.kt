package com.catmap.dubai.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.catmap.dubai.BuildConfig
import com.catmap.dubai.R
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
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
        storage.reference.child(path).putFile(uri, StorageMetadata.Builder().setContentType(type).build()).await()
        return path
    }

    suspend fun url(path: String): Uri? =
        if (!enabled || path.isBlank()) null else runCatching { storage.reference.child(path).downloadUrl.await() }.getOrNull()

    suspend fun delete(path: String) {
        if (enabled && path.isNotBlank()) runCatching { storage.reference.child(path).delete().await() }
    }

    companion object {
        const val MAX_BYTES = 30L * 1024 * 1024
        const val MAX_SECONDS = 30
    }
}
