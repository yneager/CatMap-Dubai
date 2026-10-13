package com.catmap.dubai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo ready for upload: two JPEGs plus a small bitmap for the on-device AI. */
class ProcessedPhoto(val full: ByteArray, val thumb: ByteArray, val analysis: Bitmap)

object ImageUtils {
    private const val FULL_MAX = 1280
    private const val THUMB_MAX = 360
    private const val ANALYSIS_MAX = 512

    /** Firestore documents are limited to 1 MiB, so photos are kept comfortably below that. */
    private const val FULL_TARGET_BYTES = 650_000

    /**
     * Decodes, rotates upright and re-encodes a picked photo. Re-encoding through [Bitmap.compress]
     * writes a bare JPEG, so every EXIF tag of the original – including GPS coordinates, camera
     * model and timestamps – is dropped before anything leaves the phone.
     */
    fun process(context: Context, uri: Uri): ProcessedPhoto {
        val source = decodeUpright(context, uri, FULL_MAX * 2)
        val fullBitmap = scaleToMax(source, FULL_MAX)
        val full = compressUnder(fullBitmap, FULL_TARGET_BYTES)
        val thumb = compress(scaleToMax(source, THUMB_MAX), 72)
        val analysis = scaleToMax(source, ANALYSIS_MAX)
        return ProcessedPhoto(full, thumb, analysis)
    }

    /** The same analysis-sized, upright bitmap that [process] gives the cat analyser, without encoding JPEGs. */
    fun analysisBitmap(context: Context, uri: Uri): Bitmap = scaleToMax(decodeUpright(context, uri, FULL_MAX * 2), ANALYSIS_MAX)

    fun decodeUpright(context: Context, uri: Uri, maxDim: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null, so success is judged by the stream opening.
        val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image: $uri")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Cannot decode $uri")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return applyOrientation(bitmap, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun scaleToMax(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / largest
        return bitmap.scale(
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private fun compressUnder(bitmap: Bitmap, maxBytes: Int): ByteArray {
        var current = bitmap
        var quality = 85
        while (true) {
            val bytes = compress(current, quality)
            if (bytes.size <= maxBytes) return bytes
            if (quality > 55) {
                quality -= 10
            } else {
                current = scaleToMax(current, (max(current.width, current.height) * 0.8f).roundToInt())
                quality = 80
            }
        }
    }
}
