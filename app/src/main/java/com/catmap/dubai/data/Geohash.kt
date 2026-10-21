package com.catmap.dubai.data

import kotlin.math.max
import kotlin.math.min

/**
 * Standard geohashes, used to ask Firestore for one area of the map at a time. The same code exists in
 * `cat-server/geocells.py` (the PC worker and the backfill) and must stay identical: a post written by one is
 * found by the others' queries.
 */
object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** The precisions stored in a document's `geoCells`: about 39×20 km, 4.9×4.9 km and 1.2×0.6 km. */
    val CELL_PRECISIONS = listOf(4, 5, 6)

    fun encode(lat: Double, lng: Double, precision: Int): String {
        var latLo = -90.0; var latHi = 90.0
        var lngLo = -180.0; var lngHi = 180.0
        val out = StringBuilder(precision)
        var even = true
        var bit = 0
        var ch = 0
        while (out.length < precision) {
            if (even) {
                val mid = (lngLo + lngHi) / 2
                if (lng >= mid) { ch = ch * 2 + 1; lngLo = mid } else { ch *= 2; lngHi = mid }
            } else {
                val mid = (latLo + latHi) / 2
                if (lat >= mid) { ch = ch * 2 + 1; latLo = mid } else { ch *= 2; latHi = mid }
            }
            even = !even
            if (++bit == 5) {
                out.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return out.toString()
    }

    /** The `geoCells` field for a position; empty for a missing position (0, 0). */
    fun cells(lat: Double, lng: Double): List<String> {
        if (lat == 0.0 && lng == 0.0) return emptyList()
        val full = encode(lat, lng, CELL_PRECISIONS.last())
        return CELL_PRECISIONS.map { full.take(it) }
    }

    /** The cells (finest precision with at most [maxCells]) covering a circle of [radiusKm] around a point. */
    fun around(lat: Double, lng: Double, radiusKm: Double, maxCells: Int = 6): List<String> {
        val dLat = radiusKm / 111.0
        val dLng = radiusKm / (111.0 * kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        for (precision in CELL_PRECISIONS.reversed()) {
            val cells = covering(lat - dLat, lng - dLng, lat + dLat, lng + dLng, precision, maxCells)
            if (cells.size <= maxCells) return cells
        }
        return covering(lat - dLat, lng - dLng, lat + dLat, lng + dLng, CELL_PRECISIONS.first(), 64)
    }

    /** Size of one cell in degrees (height, width) at [precision]. */
    fun cellSize(precision: Int): Pair<Double, Double> {
        val bits = precision * 5
        val lngBits = (bits + 1) / 2
        val latBits = bits / 2
        return 180.0 / (1L shl latBits) to 360.0 / (1L shl lngBits)
    }

    /** South-west corner of a cell. */
    fun origin(hash: String): Pair<Double, Double> {
        var latLo = -90.0; var latHi = 90.0
        var lngLo = -180.0; var lngHi = 180.0
        var even = true
        for (c in hash) {
            val v = BASE32.indexOf(c)
            require(v >= 0) { "not a geohash: $hash" }
            for (i in 4 downTo 0) {
                val on = (v shr i) and 1 == 1
                if (even) { val mid = (lngLo + lngHi) / 2; if (on) lngLo = mid else lngHi = mid }
                else { val mid = (latLo + latHi) / 2; if (on) latLo = mid else latHi = mid }
                even = !even
            }
        }
        return latLo to lngLo
    }

    /** Every cell of [precision] that touches the box, in rows from the south-west. */
    fun covering(south: Double, west: Double, north: Double, east: Double, precision: Int, max: Int = 64): List<String> {
        val (h, w) = cellSize(precision)
        val s = max(min(south, north), -89.999999)
        val n = min(max(south, north), 89.999999)
        val out = LinkedHashSet<String>()
        var lat = s
        while (true) {
            var lng = west
            while (true) {
                out += encode(lat, lng, precision)
                if (out.size > max) return out.toList()
                if (lng >= east) break
                lng = min(lng + w, east)
            }
            if (lat >= n) break
            lat = min(lat + h, n)
        }
        return out.toList()
    }
}
