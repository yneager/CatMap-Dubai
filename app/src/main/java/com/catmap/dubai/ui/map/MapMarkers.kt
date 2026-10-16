package com.catmap.dubai.ui.map

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import androidx.core.graphics.withTranslation
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.labeledFrom

/** What a map point stands for; decides where a tap leads. */
enum class PointKind { POST, CAT, STATION, SITTER, RESCUE }

@androidx.compose.runtime.Immutable
data class MapFeature(
    val id: String,
    val kind: PointKind,
    val lat: Double,
    val lng: Double,
    /** Marker image key, see [MarkerFactory]. */
    val icon: String,
    /** Lost and urgent posts get a soft pulsing halo. */
    val pulse: Boolean = false,
)

/** Builds marker icon keys; the factory turns a key back into a bitmap on demand. */
object MarkerIcons {
    fun post(category: String, animal: String) = "p:$category:$animal"
    /** A lost pet that was reunited in the last few days: green, with a tick. */
    fun reunited(animal: String) = "r:$animal"
    fun cat(coat: CoatColor, alert: Boolean) = "c:${coat.key}:$alert"
    const val STATION = "station"
    const val SITTER = "sitter"
    const val RESCUE = "rescue"
}

/**
 * Draws all map markers. Post markers are round "bubbles" with the animal's vector icon, ringed in the
 * category colour; community cats keep their coat-coloured cat-head pins. Bitmaps are cached per key.
 */
class MarkerFactory(context: Context) {
    private val context = context
    private val d = context.resources.displayMetrics.density
    private val cache = HashMap<String, Bitmap>()

    fun bitmap(key: String): Bitmap = cache.getOrPut(key) {
        val parts = key.split(":")
        when (parts.first()) {
            "p" -> bubble(
                labeledFrom(parts.getOrNull(1), PostCategory.OTHER).color.toInt(),
                labeledFrom(parts.getOrNull(2), AnimalType.OTHER).icon,
            )
            "c" -> catPin(labeledFrom(parts.getOrNull(1), CoatColor.UNKNOWN), parts.getOrNull(2) == "true")
            "r" -> bubble(Color.rgb(46, 125, 50), labeledFrom(parts.getOrNull(1), AnimalType.OTHER).icon).also { tick(it) }
            MarkerIcons.STATION -> bubble(Color.rgb(0, 137, 123), CatMapIcons.STATION)
            MarkerIcons.SITTER -> bubble(Color.rgb(94, 53, 177), CatMapIcons.SITTER)
            MarkerIcons.RESCUE -> bubble(Color.rgb(198, 40, 40), CatMapIcons.RESCUE)
            else -> bubble(Color.GRAY, CatMapIcons.PAW)
        }
    }

    /** A white circle with an vector icon, a coloured ring and a small pointer at the bottom. */
    private fun bubble(ring: Int, icon: Int): Bitmap {
        val size = 46 * d
        val tail = 8 * d
        val bmp = createBitmap(size.toInt(), (size + tail).toInt())
        val c = Canvas(bmp)
        val cx = size / 2
        val r = size / 2 - 3 * d
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
        c.drawCircle(cx, cx + 2 * d, r, shadow)
        val pointer = Path().apply {
            moveTo(cx - 7 * d, size - 7 * d)
            lineTo(cx, size + tail - 1 * d)
            lineTo(cx + 7 * d, size - 7 * d)
            close()
        }
        c.drawPath(pointer, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ring })
        c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ring })
        c.drawCircle(cx, cx, r - 3.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        androidx.appcompat.content.res.AppCompatResources.getDrawable(context, icon)?.mutate()?.let { drawable ->
            val inset = (11 * d).toInt()
            drawable.setBounds(inset, inset, (size - inset).toInt(), (size - inset).toInt())
            drawable.draw(c)
        }
        return bmp
    }

    /** A small green tick badge in the top corner of a reunited marker. */
    private fun tick(bmp: Bitmap) {
        val c = Canvas(bmp)
        val r = 8 * d
        val cx = bmp.width - r - 1 * d
        val cy = r + 1 * d
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 125, 50) })
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * d; color = Color.WHITE })
        val check = Path().apply { moveTo(cx - r * 0.45f, cy); lineTo(cx - r * 0.1f, cy + r * 0.35f); lineTo(cx + r * 0.5f, cy - r * 0.35f) }
        c.drawPath(check, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = Color.WHITE; strokeCap = Paint.Cap.ROUND })
    }

    /** A cat head (with ears) painted in the cat's coat colours, inside a map pin. */
    private fun catPin(coat: CoatColor, alert: Boolean): Bitmap {
        val w = 38 * d
        val h = 48 * d
        val bmp = createBitmap(w.toInt(), h.toInt())
        val canvas = Canvas(bmp)
        val r = w / 2 - 2 * d
        val pin = Path().apply {
            addCircle(w / 2, w / 2, r, Path.Direction.CW)
            moveTo(w / 2 - r * 0.55f, w / 2 + r * 0.8f)
            lineTo(w / 2, h - 1.5f * d)
            lineTo(w / 2 + r * 0.55f, w / 2 + r * 0.8f)
            close()
        }
        canvas.withTranslation(0f, 1.5f * d) {
            drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) })
        }
        canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawPath(
            pin,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * d
                color = if (alert) Color.rgb(229, 57, 53) else Color.rgb(141, 110, 99)
            },
        )
        val cx = w / 2
        val cy = w / 2 + 2 * d
        val hr = w * 0.25f
        val head = Path().apply {
            addCircle(cx, cy, hr, Path.Direction.CW)
            moveTo(cx - hr * 0.95f, cy - hr * 0.2f)
            lineTo(cx - hr * 0.85f, cy - hr * 1.55f)
            lineTo(cx - hr * 0.15f, cy - hr * 0.85f)
            close()
            moveTo(cx + hr * 0.95f, cy - hr * 0.2f)
            lineTo(cx + hr * 0.85f, cy - hr * 1.55f)
            lineTo(cx + hr * 0.15f, cy - hr * 0.85f)
            close()
        }
        val colors = listOfNotNull(coat.primary, coat.secondary, coat.tertiary).map { it.toInt() }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        if (colors.distinct().size == 1) {
            fill.color = colors.first()
        } else {
            // Hard colour bands for bicolour, calico and tortie coats.
            val stops = colors.flatMap { listOf(it, it) }.toIntArray()
            val positions = FloatArray(stops.size) { i -> ((i + 1) / 2).toFloat() / colors.size }
            fill.shader = LinearGradient(cx - hr, cy - hr, cx + hr, cy + hr, stops, positions, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(head, fill)
        canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = Color.rgb(60, 40, 30) })
        val darkCoat = Color.luminance(colors.first()) < 0.35f
        val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (darkCoat) Color.rgb(240, 200, 60) else Color.rgb(40, 30, 20) }
        canvas.drawCircle(cx - hr * 0.38f, cy - hr * 0.05f, hr * 0.14f, eye)
        canvas.drawCircle(cx + hr * 0.38f, cy - hr * 0.05f, hr * 0.14f, eye)
        canvas.drawCircle(cx, cy + hr * 0.3f, hr * 0.09f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 130, 130) })
        return bmp
    }

    /** A pin for static snapshots: the same bitmap, drawn so its tip lands on (x, y). */
    fun drawAt(canvas: Canvas, key: String, x: Float, y: Float) {
        val bmp = bitmap(key)
        canvas.drawBitmap(bmp, null, RectF(x - bmp.width / 2f, y - bmp.height, x + bmp.width / 2f, y), null)
    }
}
