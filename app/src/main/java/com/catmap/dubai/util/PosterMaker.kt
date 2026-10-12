package com.catmap.dubai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.ContactMethod
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.dateTime
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

/**
 * Draws a printable, shareable poster (1080×1350, the Instagram/WhatsApp portrait size) for a post:
 * a bold headline in the category colour, the cover photo, key details, reward and how to get in touch.
 * Text is laid out with StaticLayout, so Arabic flows right to left correctly.
 */
object PosterMaker {
    private const val W = 1080
    private const val H = 1350
    private const val M = 64f

    fun make(context: Context, post: Post, photo: ByteArray?): Bitmap {
        val bmp = createBitmap(W, H)
        val c = Canvas(bmp)
        val category = labeledFrom(post.category, PostCategory.OTHER)
        val accent = category.color.toInt()
        val animal = context.getString(labeledFrom(post.animalType, AnimalType.OTHER).label)
        val headline = context.getString(category.headline, animal).uppercase(Locale.getDefault())
        val rtl = Locale.getDefault().language == "ar"

        // Background and header band.
        c.drawColor(Color.rgb(255, 251, 247))
        c.drawRect(0f, 0f, W.toFloat(), 230f, Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), 230f, accent, darker(accent), Shader.TileMode.CLAMP)
        })
        // Arabic line metrics are taller, so the help line goes under the headline's measured height.
        val headlineHeight = text(c, headline, M, 34f, W - 2 * M, 92f, Color.WHITE, bold = true, center = true, maxLines = 1)
        text(c, context.getString(R.string.poster_help_line), M, 34f + headlineHeight + 4f, W - 2 * M, 34f, Color.argb(230, 255, 255, 255), center = true, maxLines = 1)

        // Photo.
        val photoRect = RectF(M, 260f, W - M, 860f)
        val clip = Path().apply { addRoundRect(photoRect, 36f, 36f, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        val image = photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (image != null) {
            c.drawBitmap(image, centerCrop(image, photoRect), photoRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            c.drawRect(photoRect, Paint().apply { color = lighter(accent) })
            val emoji = labeledFrom(post.animalType, AnimalType.OTHER).emoji
            text(c, emoji, photoRect.left, photoRect.centerY() - 110f, photoRect.width(), 200f, Color.BLACK, center = true)
        }
        c.restore()
        if (post.reward > 0) {
            val label = context.getString(R.string.reward_aed, NumberFormat.getIntegerInstance().format(post.reward)).uppercase(Locale.getDefault())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; typeface = Typeface.DEFAULT_BOLD }
            val w = p.measureText(label) + 64f
            val r = RectF(W - M - w - 20f, 800f, W - M - 20f, 880f)
            c.drawRoundRect(r, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 194, 51) })
            c.drawText(label, r.left + 32f, r.centerY() + 16f, p.apply { color = Color.rgb(58, 42, 0) })
        }

        // Details.
        var y = 900f
        val name = post.name.ifBlank { context.getString(category.headline, animal) }
        y += text(c, name, M, y, W - 2 * M, 64f, Color.rgb(34, 26, 21), bold = true, maxLines = 1, rtl = rtl)
        val facts = listOfNotNull(
            post.color.takeIf { it.isNotBlank() },
            post.breed.takeIf { it.isNotBlank() },
            labeledFrom(post.sex, CatSex.UNKNOWN).takeIf { it != CatSex.UNKNOWN }?.let { context.getString(it.label) },
        ).joinToString(" · ")
        if (facts.isNotBlank()) y += text(c, facts, M, y + 6f, W - 2 * M, 38f, Color.rgb(82, 68, 60), maxLines = 1, rtl = rtl) + 6f
        val seen = context.getString(
            R.string.poster_last_seen, Dubai.nameOf(post.neighborhood), dateTime(post.lastSeenAt.takeIf { it > 0 } ?: post.createdAt),
        )
        y += text(c, seen, M, y + 10f, W - 2 * M, 38f, accent, bold = true, maxLines = 2, rtl = rtl) + 10f
        val details = post.features.ifBlank { post.description }
        if (details.isNotBlank()) y += text(c, details, M, y + 10f, W - 2 * M, 34f, Color.rgb(60, 50, 44), maxLines = 3, rtl = rtl) + 10f

        // Contact footer.
        c.drawRect(0f, H - 150f, W.toFloat(), H.toFloat(), Paint().apply { color = Color.rgb(34, 26, 21) })
        val contact = when {
            post.contactMethod == ContactMethod.PHONE.key && post.contactPhone.isNotBlank() -> context.getString(R.string.poster_call, post.contactPhone)
            post.contactMethod == ContactMethod.EMAIL.key && post.contactEmail.isNotBlank() -> context.getString(R.string.poster_email, post.contactEmail)
            else -> context.getString(R.string.poster_in_app, post.ownerName)
        }
        text(c, contact, M, H - 128f, W - 2 * M, 40f, Color.WHITE, bold = true, center = true, maxLines = 1)
        text(c, context.getString(R.string.poster_footer), M, H - 68f, W - 2 * M, 30f, Color.argb(200, 255, 255, 255), center = true, maxLines = 1)
        return bmp
    }

    /** Draws wrapped text and returns its height. */
    private fun text(
        c: Canvas, value: String, x: Float, y: Float, width: Float, size: Float, color: Int,
        bold: Boolean = false, center: Boolean = false, maxLines: Int = Int.MAX_VALUE, rtl: Boolean = false,
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        // Forcing the paragraph direction keeps Arabic and Latin lines (names, breeds) on the same edge.
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt())
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return layout.height.toFloat()
    }

    private fun centerCrop(src: Bitmap, dst: RectF): Rect {
        val scale = max(dst.width() / src.width, dst.height() / src.height)
        val w = (dst.width() / scale).toInt()
        val h = (dst.height() / scale).toInt()
        val left = (src.width - w) / 2
        val top = (src.height - h) / 2
        return Rect(left, top, left + w, top + h)
    }

    private fun darker(color: Int) = Color.rgb((Color.red(color) * 0.7f).toInt(), (Color.green(color) * 0.7f).toInt(), (Color.blue(color) * 0.7f).toInt())
    private fun lighter(color: Int) = Color.rgb(
        Color.red(color) + (255 - Color.red(color)) * 3 / 4,
        Color.green(color) + (255 - Color.green(color)) * 3 / 4,
        Color.blue(color) + (255 - Color.blue(color)) * 3 / 4,
    )
}
