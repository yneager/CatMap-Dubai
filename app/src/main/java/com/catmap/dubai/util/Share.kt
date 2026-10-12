package com.catmap.dubai.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.catmap.dubai.AppContainer
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import android.graphics.Bitmap
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.CoatPattern
import com.catmap.dubai.data.model.TnrStatus
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.dateOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Share {
    fun catSummary(context: Context, cat: Cat): String {
        val coat = context.getString(labeledFrom(cat.coatColor, CoatColor.UNKNOWN).label)
        val pattern = context.getString(labeledFrom(cat.pattern, CoatPattern.UNKNOWN).label)
        val tnr = context.getString(labeledFrom(cat.tnrStatus, TnrStatus.UNKNOWN).label)
        return context.getString(
            R.string.share_cat_text,
            cat.name, coat, pattern, Dubai.nameOf(cat.neighborhood), cat.sightingCount,
            dateOnly(cat.firstSeen), dateOnly(cat.lastSeen), tnr,
        )
    }

    fun postSummary(context: Context, post: Post): String {
        val animal = context.getString(labeledFrom(post.animalType, AnimalType.OTHER).label)
        val headline = context.getString(labeledFrom(post.category, PostCategory.OTHER).headline, animal)
        return buildString {
            append(if (post.name.isNotBlank()) "$headline: ${post.name}" else headline)
            append('\n').append("📍 ").append(Dubai.nameOf(post.neighborhood))
            if (post.lastSeenAt > 0) append(" · ").append(dateOnly(post.lastSeenAt))
            if (post.reward > 0) append('\n').append("💰 ").append(context.getString(R.string.reward_aed, post.reward.toString()))
            val details = post.features.ifBlank { post.description }
            if (details.isNotBlank()) append('\n').append(details.take(280))
            append('\n').append('\n').append(context.getString(R.string.share_post_footer, post.ownerName))
        }
    }

    /** Shares a post as text plus its cover photo (WhatsApp and most apps show both). */
    suspend fun sharePost(context: Context, container: AppContainer, post: Post) {
        val photo = post.photoIds.firstOrNull()?.let { container.images.bytes(it) }
        val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, postSummary(context, post)) }
        if (photo != null) {
            val uri = cacheFile(context, "post_${post.id}.jpg", photo)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            intent.type = "text/plain"
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
    }

    /** Shares a generated poster image. */
    suspend fun shareImage(context: Context, bitmap: Bitmap, name: String, text: String) {
        val bytes = withContext(Dispatchers.Default) {
            java.io.ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.toByteArray()
            }
        }
        val uri = cacheFile(context, name, bytes)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_poster)))
    }

    private suspend fun cacheFile(context: Context, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = File(File(context.cacheDir, "share").apply { mkdirs() }, name).apply { writeBytes(bytes) }
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    /** Shares a text summary of a cat, with its cover photo when there is one. */
    suspend fun shareCat(context: Context, container: AppContainer, cat: Cat) {
        val text = catSummary(context, cat)
        val photo = if (cat.coverPhotoId.isNotBlank()) container.images.bytes(cat.coverPhotoId) else null
        val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, text) }
        if (photo != null) {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "share").apply { mkdirs() }.let { dir ->
                    File(dir, "cat_${cat.id}.jpg").apply { writeBytes(photo) }
                }
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            intent.type = "text/plain"
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_cat)))
    }
}
