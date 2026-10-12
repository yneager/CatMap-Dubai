package com.catmap.dubai.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.catmap.dubai.data.model.CoatColor
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume
import kotlin.math.cos

private val snapshots = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = (value.byteCount / 1024).coerceAtLeast(1)
}

/**
 * A small static map with one marker, used on detail screens. It is rendered once as an image, so many of
 * them can sit in scrolling lists without the cost of live map views.
 */
@Composable
fun MiniMap(
    lat: Double,
    lng: Double,
    icon: String,
    dark: Boolean,
    height: Dp = 170.dp,
    zoom: Double = 15.0,
    area: AreaCircle? = null,
    onClick: (() -> Unit)? = null,
) {
    if (lat == 0.0 && lng == 0.0) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val markers = remember { MarkerFactory(context) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val key = "$lat,$lng,$zoom,$dark,$widthPx,$heightPx,$icon,${area?.radiusM}"
        val bitmap by produceState(snapshots.get(key), key) {
            if (value != null) return@produceState
            value = runCatching {
                MapLibre.getInstance(context)
                val pixelRatio = density.density
                val options = MapSnapshotter.Options((widthPx / pixelRatio).toInt().coerceAtLeast(1), (heightPx / pixelRatio).toInt().coerceAtLeast(1))
                    .withStyleBuilder(org.maplibre.android.maps.Style.Builder().fromUri(MapStyles.snapshotUri(dark)))
                    .withCameraPosition(CameraPosition.Builder().target(LatLng(lat, lng)).zoom(zoom).build())
                    .withPixelRatio(pixelRatio)
                    .withLogo(false)
                    .withAttribution(true)
                val snapshotter = MapSnapshotter(context, options)
                suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { snapshotter.cancel() }
                    snapshotter.start(
                        { snap ->
                            val base = snap.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                            val canvas = Canvas(base)
                            if (area != null && area.radiusM > 0) {
                                // Metres per pixel at this zoom and latitude, for the search-area circle.
                                val mpp = 156_543.03392 * cos(Math.toRadians(lat)) / Math.pow(2.0, zoom) / pixelRatio
                                val r = (area.radiusM / mpp).toFloat()
                                val color = (area.color and 0xFFFFFF).toInt()
                                canvas.drawCircle(base.width / 2f, base.height / 2f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 40 })
                                canvas.drawCircle(
                                    base.width / 2f, base.height / 2f, r,
                                    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 200; style = Paint.Style.STROKE; strokeWidth = 2 * pixelRatio },
                                )
                            }
                            markers.drawAt(canvas, icon, base.width / 2f, base.height / 2f)
                            snapshots.put(key, base)
                            if (cont.isActive) cont.resume(base)
                        },
                        { if (cont.isActive) cont.resume(null) },
                    )
                }
            }.getOrNull()
        }
        AnimatedContent(bitmap, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "minimap") { b ->
            if (b != null) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}

/** Kept for the original screens: picks the marker from the point kind and coat. */
@Composable
fun MiniMap(lat: Double, lng: Double, kind: PointKind, dark: Boolean, coat: CoatColor = CoatColor.UNKNOWN, height: Dp = 170.dp, zoom: Double = 15.0) {
    val icon = when (kind) {
        PointKind.CAT -> MarkerIcons.cat(coat, false)
        PointKind.STATION -> MarkerIcons.STATION
        PointKind.RESCUE -> MarkerIcons.RESCUE
        PointKind.SITTER -> MarkerIcons.SITTER
        PointKind.POST -> MarkerIcons.post("other", "other")
    }
    MiniMap(lat, lng, icon, dark, height, zoom)
}
