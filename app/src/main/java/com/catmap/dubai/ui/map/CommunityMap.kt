package com.catmap.dubai.ui.map

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.MapMode
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.toNumber
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

/** Camera position remembered for the session, so returning to a map doesn't reset it. */
object MapMemory {
    var lat: Double = Dubai.CENTER_LAT
    var lng: Double = Dubai.CENTER_LNG
    var zoom: Double = 11.2
    var tilt: Double = 0.0
    var bearing: Double = 0.0
}

/** "Show this on the map": other screens ask the Map tab to fly somewhere and select a marker. */
object MapFocus {
    data class Request(val lat: Double, val lng: Double, val zoom: Double, val postId: String? = null)

    val request = kotlinx.coroutines.flow.MutableStateFlow<Request?>(null)
}

/** A shaded circle drawn on the map, e.g. a lost animal's search area. */
data class AreaCircle(val lat: Double, val lng: Double, val radiusM: Double, val color: Long = 0xFFE53935)

/** Lets screens move the camera of a [CommunityMap]. */
class MapController {
    internal var map: MapLibreMap? = null

    val target: LatLng? get() = map?.cameraPosition?.target

    fun flyTo(lat: Double, lng: Double, zoom: Double? = null, durationMs: Int = 900) {
        val m = map ?: return
        val z = zoom ?: m.cameraPosition.zoom
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), z), durationMs)
    }

    fun zoomBy(delta: Double) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(delta), 300)
    }

    fun tilt(degrees: Double) {
        map?.easeCamera(CameraUpdateFactory.tiltTo(degrees), 700)
    }
}

private const val SRC_POINTS = "cm-points-src"
private const val SRC_HEAT = "cm-heat-src"
private const val SRC_AREA = "cm-area-src"
private const val L_HEAT = "cm-heat"
private const val L_AREA_FILL = "cm-area-fill"
private const val L_AREA_LINE = "cm-area-line"
private const val L_PULSE = "cm-pulse"
private const val L_CLUSTER_HALO = "cm-cluster-halo"
private const val L_CLUSTERS = "cm-clusters"
private const val L_COUNT = "cm-count"
private const val L_POINTS = "cm-points"

/** Mutable bits of one map instance that live outside composition. */
private class MapHolder(context: Context) {
    val markers = MarkerFactory(context)
    var map: MapLibreMap? = null
    /** Only a fully loaded style: touching one that a newer setStyle replaced throws. */
    var style: Style? = null
        get() = field?.takeIf { it.isFullyLoaded }
    var features: List<MapFeature> = emptyList()
    var pulse: ValueAnimator? = null
    var bounce: ValueAnimator? = null
}

/**
 * The interactive community map: vector tiles, clustered markers with counts, a pulsing halo on lost and
 * urgent posts, an optional heatmap and search-area circle, and the user's location puck.
 */
@SuppressLint("MissingPermission")
@Composable
fun CommunityMap(
    modifier: Modifier,
    features: List<MapFeature>,
    mode: MapMode,
    dark: Boolean,
    controller: MapController,
    selectedId: String? = null,
    satellite: Boolean = false,
    heat: List<Pair<Double, Double>> = emptyList(),
    showHeat: Boolean = false,
    area: AreaCircle? = null,
    showUserLocation: Boolean = false,
    interactive: Boolean = true,
    initialLat: Double = MapMemory.lat,
    initialLng: Double = MapMemory.lng,
    initialZoom: Double = MapMemory.zoom,
    rememberCamera: Boolean = true,
    bottomInsetPx: Int = 0,
    onFeatureClick: (MapFeature) -> Unit = {},
    onMapClick: () -> Unit = {},
    onCameraIdle: (LatLng) -> Unit = {},
    onCameraMoveStarted: () -> Unit = {},
    /** The visible box (south, west, north, east) whenever the camera stops, and once when the map is ready. */
    onViewport: ((Double, Double, Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder(context) }
    var styleVersion by remember { mutableIntStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }
    val clickFeature by rememberUpdatedState(onFeatureClick)
    val clickMap by rememberUpdatedState(onMapClick)
    val idle by rememberUpdatedState(onCameraIdle)
    val moveStarted by rememberUpdatedState(onCameraMoveStarted)
    val viewport by rememberUpdatedState(onViewport)

    val mapView = remember {
        MapLibre.getInstance(context)
        val camera = CameraPosition.Builder()
            .target(LatLng(initialLat, initialLng))
            .zoom(initialZoom)
            .tilt(if (rememberCamera) MapMemory.tilt else 0.0)
            .bearing(if (rememberCamera) MapMemory.bearing else 0.0)
            .build()
        val options = MapLibreMapOptions.createFromAttributes(context)
            // A TextureView composites cleanly with Compose animations and rounded clips.
            .textureMode(true)
            .camera(camera)
            .logoEnabled(false)
            .compassEnabled(true)
            .attributionEnabled(true)
        MapView(context, options).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    holder.pulse?.resume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    holder.pulse?.pause()
                    mapView.onPause()
                }
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.pulse?.cancel()
            holder.bounce?.cancel()
            if (controller.map === holder.map) controller.map = null
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            holder.map = map
            controller.map = map
            map.uiSettings.apply {
                setAllGesturesEnabled(interactive)
                isLogoEnabled = false
                setAttributionGravity(Gravity.BOTTOM or Gravity.START)
                val d = resources.displayMetrics.density
                setAttributionMargins((8 * d).toInt(), 0, 0, (8 * d).toInt() + bottomInsetPx)
                setCompassMargins(0, (150 * d).toInt(), (12 * d).toInt(), 0)
            }
            map.setMinZoomPreference(8.5)
            map.setMaxZoomPreference(19.5)
            // Explicit, so the Modern look can tilt for 3D buildings whatever the defaults are.
            map.setMinPitchPreference(0.0)
            map.setMaxPitchPreference(60.0)
            map.setLatLngBoundsForCameraTarget(
                LatLngBounds.from(Dubai.NORTH + 0.25, Dubai.EAST + 0.25, Dubai.SOUTH - 0.25, Dubai.WEST - 0.25),
            )
            map.addOnMapClickListener { latLng ->
                val point = map.projection.toScreenLocation(latLng)
                val slop = 24 * resources.displayMetrics.density
                val hits = map.queryRenderedFeatures(
                    RectF(point.x - slop, point.y - slop * 1.6f, point.x + slop, point.y + slop * 0.4f), L_CLUSTERS, L_POINTS,
                )
                val cluster = hits.firstOrNull { it.hasProperty("point_count") }
                val single = hits.firstOrNull { !it.hasProperty("point_count") }
                when {
                    cluster != null -> {
                        val source = holder.style?.getSourceAs<GeoJsonSource>(SRC_POINTS)
                        val zoom = runCatching { source?.getClusterExpansionZoom(cluster) }.getOrNull()?.toDouble()
                            ?: (map.cameraPosition.zoom + 2)
                        val p = cluster.geometry() as? Point
                        if (p != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude(), p.longitude()), zoom + 0.3), 600)
                        true
                    }
                    single != null -> {
                        val id = single.getStringProperty("id")
                        holder.features.firstOrNull { it.id == id }?.let { clickFeature(it) }
                        true
                    }
                    else -> {
                        clickMap()
                        false
                    }
                }
            }
            map.addOnCameraMoveStartedListener { moveStarted() }
            map.addOnCameraIdleListener {
                val cam = map.cameraPosition
                val target = cam.target ?: return@addOnCameraIdleListener
                if (rememberCamera) {
                    MapMemory.lat = target.latitude
                    MapMemory.lng = target.longitude
                    MapMemory.zoom = cam.zoom
                    MapMemory.tilt = cam.tilt
                    MapMemory.bearing = cam.bearing
                }
                idle(target)
                viewport?.let { report -> map.reportViewport(report) }
            }
            mapView.addOnStyleImageMissingListener { id ->
                holder.style?.let { style -> runCatching { style.addImage(id, holder.markers.bitmap(id)) } }
            }
            mapReady = true
            viewport?.let { report -> map.reportViewport(report) }
        }
    }

    // A new style (mode, theme or satellite changed) needs our sources and layers added again.
    LaunchedEffect(mapReady, mode, dark, satellite) {
        val map = holder.map ?: return@LaunchedEffect
        holder.style = null
        map.setStyle(MapStyles.builder(mode, dark, satellite)) { style ->
            holder.style = style
            MapStyles.customize(style, mode, dark, satellite)
            addCommunityLayers(style, holder)
            styleVersion++
        }
    }

    // Modern tilts the camera for the 3D look; Classic is flat.
    LaunchedEffect(styleVersion, mode) {
        if (styleVersion == 0 || !interactive) return@LaunchedEffect
        val current = holder.map?.cameraPosition ?: return@LaunchedEffect
        val wanted = if (mode == MapMode.MODERN) 45.0 else 0.0
        if (kotlin.math.abs(current.tilt - wanted) > 1) controller.tilt(wanted)
    }

    LaunchedEffect(styleVersion, features) {
        holder.features = features
        val style = holder.style ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(SRC_POINTS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                features.map { f ->
                    Feature.fromGeometry(Point.fromLngLat(f.lng, f.lat)).apply {
                        addStringProperty("id", f.id)
                        addStringProperty("icon", f.icon)
                        addBooleanProperty("pulse", f.pulse)
                        addStringProperty("pulseColor", pulseColor(f.icon))
                    }
                },
            ),
        )
    }

    // The selected marker pops up a little with an overshoot, and draws above its neighbours.
    LaunchedEffect(styleVersion, selectedId) {
        val layer = holder.style?.getLayerAs<SymbolLayer>(L_POINTS) ?: return@LaunchedEffect
        holder.bounce?.cancel()
        fun apply(scale: Float) {
            val layer=holder.style?.getLayerAs<SymbolLayer>(L_POINTS) ?: return
            layer.setProperties(
                PropertyFactory.iconSize(switchCase(eq(get("id"), literal(selectedId ?: "")), literal(scale), literal(1f))),
                PropertyFactory.symbolSortKey(switchCase(eq(get("id"), literal(selectedId ?: "")), literal(1f), literal(0f))),
            )
        }
        if (selectedId == null) {
            apply(1f)
        } else {
            holder.bounce = ValueAnimator.ofFloat(1f, 1.35f).apply {
                duration = 380
                interpolator = OvershootInterpolator(3f)
                addUpdateListener { apply(it.animatedValue as Float) }
                start()
            }
        }
    }

    LaunchedEffect(styleVersion, heat, showHeat) {
        val style = holder.style ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(SRC_HEAT)?.setGeoJson(
            FeatureCollection.fromFeatures(heat.map { (lat, lng) -> Feature.fromGeometry(Point.fromLngLat(lng, lat)) }),
        )
        style.getLayer(L_HEAT)?.setProperties(PropertyFactory.visibility(if (showHeat) Property.VISIBLE else Property.NONE))
    }

    LaunchedEffect(styleVersion, area) {
        val style = holder.style ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(SRC_AREA) ?: return@LaunchedEffect
        if (area == null || area.radiusM <= 0) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(Feature.fromGeometry(circlePolygon(area.lat, area.lng, area.radiusM)))
            val color = colorHex(area.color)
            style.getLayer(L_AREA_FILL)?.setProperties(PropertyFactory.fillColor(color))
            style.getLayer(L_AREA_LINE)?.setProperties(PropertyFactory.lineColor(color))
        }
    }

    LaunchedEffect(styleVersion, showUserLocation) {
        val map = holder.map ?: return@LaunchedEffect
        val style = holder.style ?: return@LaunchedEffect
        runCatching {
            val component = map.locationComponent
            if (showUserLocation) {
                val options = LocationComponentOptions.builder(context)
                    .pulseEnabled(true)
                    .pulseColor(Color(0xFF2979FF).toArgb())
                    .accuracyAlpha(0.15f)
                    .build()
                component.activateLocationComponent(
                    LocationComponentActivationOptions.Builder(context, style)
                        .locationComponentOptions(options)
                        .useDefaultLocationEngine(true)
                        .build(),
                )
                component.isLocationComponentEnabled = true
                component.cameraMode = CameraMode.NONE
                component.renderMode = RenderMode.COMPASS
            } else if (component.isLocationComponentActivated) {
                component.isLocationComponentEnabled = false
            }
        }
    }

    // Lost and urgent posts breathe with a soft halo while the map is on screen.
    DisposableEffect(styleVersion) {
        holder.pulse?.cancel()
        val layer = holder.style?.getLayerAs<CircleLayer>(L_PULSE)
        holder.pulse = layer?.let {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { a ->
                    val t = a.animatedValue as Float
                    runCatching {
                        holder.style?.getLayerAs<CircleLayer>(L_PULSE)?.setProperties(
                            PropertyFactory.circleRadius(14f + 22f * t),
                            PropertyFactory.circleOpacity(0.45f * (1f - t)),
                        )
                    }
                }
                start()
            }
        }
        onDispose { holder.pulse?.cancel() }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addCommunityLayers(style: Style, holder: MapHolder) {
    style.addSource(GeoJsonSource(SRC_HEAT))
    style.addSource(GeoJsonSource(SRC_AREA))
    style.addSource(
        GeoJsonSource(
            SRC_POINTS,
            GeoJsonOptions().withCluster(true).withClusterRadius(46).withClusterMaxZoom(15),
        ),
    )

    style.addLayer(
        HeatmapLayer(L_HEAT, SRC_HEAT).withProperties(
            PropertyFactory.heatmapRadius(Expression.interpolate(Expression.linear(), Expression.zoom(), stop(9, 12), stop(16, 40))),
            PropertyFactory.heatmapOpacity(0.75f),
            PropertyFactory.heatmapIntensity(Expression.interpolate(Expression.linear(), Expression.zoom(), stop(9, 0.6), stop(16, 2.2))),
            PropertyFactory.visibility(Property.NONE),
        ),
    )
    style.addLayer(
        FillLayer(L_AREA_FILL, SRC_AREA).withProperties(PropertyFactory.fillOpacity(0.16f), PropertyFactory.fillColor("#E53935")),
    )
    style.addLayer(
        LineLayer(L_AREA_LINE, SRC_AREA).withProperties(
            PropertyFactory.lineWidth(2f), PropertyFactory.lineColor("#E53935"), PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
        ),
    )
    style.addLayer(
        CircleLayer(L_PULSE, SRC_POINTS)
            .withFilter(all(not(has("point_count")), eq(get("pulse"), literal(true))))
            .withProperties(
                PropertyFactory.circleColor(get("pulseColor")),
                PropertyFactory.circleRadius(14f),
                PropertyFactory.circleOpacity(0.4f),
                PropertyFactory.circleTranslate(arrayOf(0f, -26f)),
            ),
    )
    val count = toNumber(get("point_count"))
    style.addLayer(
        CircleLayer(L_CLUSTER_HALO, SRC_POINTS)
            .withFilter(has("point_count"))
            .withProperties(
                PropertyFactory.circleColor(step(count, literal("#7C4DFF"), stop(10, "#FF7043"), stop(40, "#E53935"))),
                PropertyFactory.circleRadius(step(count, literal(27f), stop(10, 32f), stop(40, 38f))),
                PropertyFactory.circleOpacity(0.25f),
            ),
    )
    style.addLayer(
        CircleLayer(L_CLUSTERS, SRC_POINTS)
            .withFilter(has("point_count"))
            .withProperties(
                PropertyFactory.circleColor(step(count, literal("#7C4DFF"), stop(10, "#FF7043"), stop(40, "#E53935"))),
                PropertyFactory.circleRadius(step(count, literal(19f), stop(10, 23f), stop(40, 28f))),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
            ),
    )
    style.addLayer(
        SymbolLayer(L_COUNT, SRC_POINTS)
            .withFilter(has("point_count"))
            .withProperties(
                PropertyFactory.textField(Expression.toString(get("point_count_abbreviated"))),
                PropertyFactory.textFont(arrayOf(MapStyles.FONT_BOLD)),
                PropertyFactory.textSize(14f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            ),
    )
    style.addLayer(
        SymbolLayer(L_POINTS, SRC_POINTS)
            .withFilter(not(has("point_count")))
            .withProperties(
                PropertyFactory.iconImage(get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
    )
    // Pre-register the icons we already know about; others are drawn when the map asks for them.
    holder.features.map { it.icon }.distinct().forEach { key -> runCatching { style.addImage(key, holder.markers.bitmap(key)) } }
}

private fun pulseColor(icon: String): String = when {
    icon.startsWith("p:lost") -> "#E53935"
    icon.startsWith("p:needs_help") -> "#FB8C00"
    icon == MarkerIcons.RESCUE || icon.endsWith(":true") -> "#E53935"
    else -> "#7C4DFF"
}

private fun MapLibreMap.reportViewport(report: (Double, Double, Double, Double) -> Unit) {
    val box = runCatching { projection.visibleRegion.latLngBounds }.getOrNull() ?: return
    report(box.latitudeSouth, box.longitudeWest, box.latitudeNorth, box.longitudeEast)
}

private fun colorHex(argb: Long): String = String.format("#%06X", argb and 0xFFFFFF)

/** A 64-point polygon approximating a circle of [radiusM] metres. */
private fun circlePolygon(lat: Double, lng: Double, radiusM: Double): Polygon {
    val points = (0..64).map { i ->
        val angle = Math.toRadians(i * 360.0 / 64)
        val dLat = radiusM / 111_320.0 * cos(angle)
        val dLng = radiusM / (111_320.0 * cos(Math.toRadians(lat))) * sin(angle)
        Point.fromLngLat(lng + dLng, lat + dLat)
    }
    return Polygon.fromLngLats(listOf(points))
}
