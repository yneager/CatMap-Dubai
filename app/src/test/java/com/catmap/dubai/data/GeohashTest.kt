package com.catmap.dubai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same vectors are in cat-server/tests/test_geocells.py: the phone, the web app and the PC must agree. */
class GeohashTest {
    @Test
    fun knownVectors() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
        assertEquals("thrr3g", Geohash.encode(25.1972, 55.2744, 6))
        assertEquals("thrr3s", Geohash.encode(25.2048, 55.2708, 6))
        assertEquals("r3gx2f", Geohash.encode(-33.8688, 151.2093, 6))
    }

    @Test
    fun cellsArePrefixesAtThreePrecisions() {
        assertEquals(listOf("thrr", "thrr3", "thrr3s"), Geohash.cells(25.2048, 55.2708))
        assertEquals(emptyList<String>(), Geohash.cells(0.0, 0.0))
    }

    @Test
    fun originAndSizeDescribeTheCell() {
        val (lat, lng) = Geohash.origin("thrr3s")
        val (h, w) = Geohash.cellSize(6)
        assertEquals("thrr3s", Geohash.encode(lat + h / 2, lng + w / 2, 6))
        assertTrue(25.2048 in lat..(lat + h) && 55.2708 in lng..(lng + w))
    }

    @Test
    fun coveringIncludesCornersAndCentreOnce() {
        val s = 25.05; val w = 55.10; val n = 25.25; val e = 55.35
        val cells = Geohash.covering(s, w, n, e, 5)
        assertEquals(cells.size, cells.toSet().size)
        listOf(s to w, s to e, n to w, n to e, (s + n) / 2 to (w + e) / 2).forEach { (lat, lng) ->
            assertTrue(Geohash.encode(lat, lng, 5) in cells)
        }
    }

    @Test
    fun coveringStopsPastTheMaximum() {
        assertEquals(7, Geohash.covering(24.75, 54.90, 25.40, 55.65, 6, max = 6).size)
    }
}
