package com.catmap.dubai.ui.map

import com.catmap.dubai.BuildConfig
import com.catmap.dubai.data.MapMode
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import java.util.Locale

/**
 * Map looks. Vector tiles come from OpenFreeMap (free, no key, OpenStreetMap data), so the map is sharp at
 * every zoom level and pixel density. Satellite imagery is MapTiler's and only offered when a key is set.
 *
 * - Classic: OpenFreeMap "Liberty" (light) or "Dark" — a clean, detailed street map.
 * - Modern: Liberty recoloured at load time into a vivid daytime palette or a neon night palette, with tilted
 *   3D buildings, in the spirit of Waze and Snap Map.
 */
object MapStyles {
    private const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    private const val DARK = "https://tiles.openfreemap.org/styles/dark"
    const val GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
    const val FONT_BOLD = "Noto Sans Bold"

    val satelliteAvailable: Boolean get() = BuildConfig.MAPTILER_KEY.isNotEmpty()

    fun builder(mode: MapMode, dark: Boolean, satellite: Boolean): Style.Builder = when {
        satellite && satelliteAvailable -> Style.Builder().fromJson(satelliteJson())
        mode == MapMode.CLASSIC && dark -> Style.Builder().fromUri(DARK)
        else -> Style.Builder().fromUri(LIBERTY)
    }

    /** The uri used for static snapshots (detail screens). */
    fun snapshotUri(dark: Boolean) = if (dark) DARK else LIBERTY

    private fun satelliteJson(): String = """
        {
          "version": 8,
          "glyphs": "$GLYPHS",
          "sources": {
            "satellite": {
              "type": "raster",
              "tiles": ["https://api.maptiler.com/tiles/satellite-v2/{z}/{x}/{y}.jpg?key=${BuildConfig.MAPTILER_KEY}"],
              "tileSize": 512,
              "maxzoom": 20,
              "attribution": "© MapTiler © OpenStreetMap contributors"
            }
          },
          "layers": [{ "id": "satellite", "type": "raster", "source": "satellite" }]
        }
    """.trimIndent()

    /** Applies the language and, for Modern, the palette. Runs once each time a style finishes loading. */
    fun customize(style: Style, mode: MapMode, dark: Boolean, satellite: Boolean) {
        localizeLabels(style)
        if (mode == MapMode.MODERN && !(satellite && satelliteAvailable)) recolor(style, if (dark) NIGHT else DAY)
    }

    /** Shows place names in the app language instead of the bilingual Latin + Arabic default. */
    private fun localizeLabels(style: Style) {
        val arabic = Locale.getDefault().language == "ar"
        val name: Expression = if (arabic) {
            coalesce(get("name:ar"), get("name:nonlatin"), get("name"))
        } else {
            coalesce(get("name:en"), get("name_en"), get("name:latin"), get("name"))
        }
        style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
            // Some layers use an expression, others a plain "{name}" template.
            val field = runCatching { layer.textField.expression?.toString() ?: layer.textField.value?.toString() }.getOrNull() ?: return@forEach
            if ("name" in field && "ref" !in field) runCatching { layer.setProperties(PropertyFactory.textField(name)) }
        }
    }

    private class Palette(
        val background: String,
        val water: String,
        val park: String,
        val sand: String,
        val residential: String,
        val building: String,
        val building3d: String,
        val motorway: String,
        val major: String,
        val secondary: String,
        val minor: String,
        val casing: String,
        val label: String,
        val halo: String,
        val rail: String,
    )

    private val DAY = Palette(
        background = "#F4F1FF", water = "#5EC8F8", park = "#93E07A", sand = "#FFE7A6", residential = "#EEE8FB",
        building = "#E2DAF6", building3d = "#D9CCF5", motorway = "#FF8A4C", major = "#FFC94D", secondary = "#FFFFFF",
        minor = "#FFFFFF", casing = "#D6CCEB", label = "#2B2350", halo = "#FFFFFF", rail = "#B9AEDB",
    )

    private val NIGHT = Palette(
        background = "#121A2E", water = "#1C4F8F", park = "#1D5A3A", sand = "#3B3420", residential = "#18213A",
        building = "#25304F", building3d = "#2E3B63", motorway = "#FF7A3D", major = "#FFC247", secondary = "#7D8CFF",
        minor = "#33406A", casing = "#0B1124", label = "#E9EDFF", halo = "#0B1020", rail = "#46527A",
    )

    private fun recolor(style: Style, p: Palette) {
        for (layer in style.layers) {
            val id = layer.id
            runCatching {
                when (layer) {
                    is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(p.background))
                    is FillExtrusionLayer -> layer.setProperties(
                        PropertyFactory.fillExtrusionColor(p.building3d),
                        PropertyFactory.fillExtrusionOpacity(0.9f),
                    )
                    is FillLayer -> when {
                        id == "water" || id.startsWith("water") -> layer.setProperties(PropertyFactory.fillColor(p.water))
                        id.contains("park") || id.contains("grass") || id.contains("wood") || id.contains("pitch") ||
                            id.contains("cemetery") -> layer.setProperties(PropertyFactory.fillColor(p.park), PropertyFactory.fillOpacity(0.85f))
                        id.contains("sand") -> layer.setProperties(PropertyFactory.fillColor(p.sand))
                        id.contains("residential") -> layer.setProperties(PropertyFactory.fillColor(p.residential))
                        id == "building" -> layer.setProperties(PropertyFactory.fillColor(p.building))
                        else -> Unit
                    }
                    is LineLayer -> when {
                        id.contains("waterway") -> layer.setProperties(PropertyFactory.lineColor(p.water))
                        id.contains("casing") -> layer.setProperties(PropertyFactory.lineColor(p.casing))
                        id.contains("rail") -> layer.setProperties(PropertyFactory.lineColor(p.rail))
                        id.contains("motorway") -> layer.setProperties(PropertyFactory.lineColor(p.motorway))
                        id.contains("trunk") || id.contains("primary") -> layer.setProperties(PropertyFactory.lineColor(p.major))
                        id.contains("secondary") || id.contains("tertiary") -> layer.setProperties(PropertyFactory.lineColor(p.secondary))
                        id.contains("minor") || id.contains("street") || id.contains("link") || id.contains("service") ->
                            layer.setProperties(PropertyFactory.lineColor(p.minor))
                        id.contains("park_outline") -> layer.setProperties(PropertyFactory.lineColor(p.park))
                        else -> Unit
                    }
                    is SymbolLayer -> {
                        // Fewer shop and transit icons keep the vivid map calm; place and road names stay.
                        if (id.startsWith("poi_r7") || id.startsWith("poi_r1") || id.startsWith("poi_transit")) {
                            layer.setProperties(PropertyFactory.visibility("none"))
                        } else {
                            layer.setProperties(
                                PropertyFactory.textColor(p.label),
                                PropertyFactory.textHaloColor(p.halo),
                                PropertyFactory.textHaloWidth(1.4f),
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
        // Buildings rise smoothly as you zoom in.
        style.getLayerAs<FillExtrusionLayer>("building-3d")?.let { layer ->
            runCatching {
                layer.setProperties(
                    PropertyFactory.fillExtrusionHeight(
                        interpolate(linear(), zoom(), stop(14, literal(0)), stop(15.5, get("render_height"))),
                    ),
                )
            }
        }
    }
}
