package com.catmap.dubai.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LocationPrivacyTest {
    @Test fun sameSightingHasStablePublicLocation() {
        assertEquals(LocationPrivacy.fuzz(25.2048, 55.2708, "standard", "s1"), LocationPrivacy.fuzz(25.2048, 55.2708, "standard", "s1"))
    }
    @Test fun exactPositionWithinOneCellIsNotExposed() {
        assertEquals(LocationPrivacy.fuzz(25.2041, 55.2701, "standard", "s1"), LocationPrivacy.fuzz(25.2059, 55.2719, "standard", "s1"))
    }
    @Test fun publicLocationIsBoundedForBothPrivacyLevels() {
        for (level in listOf("standard", "high")) for (seed in 0..100) {
            val (lat, lng) = LocationPrivacy.fuzz(25.2048, 55.2708, level, seed.toString())
            assertTrue(abs(lat - 25.2048) <= LocationPrivacy.cellSize(level) * 0.81)
            assertTrue(abs(lng - 55.2708) <= LocationPrivacy.cellSize(level) * 0.81)
        }
    }
    @Test fun highPrivacyUsesLargerCells() {
        assertTrue(LocationPrivacy.approxRadiusMeters("high") > LocationPrivacy.approxRadiusMeters("standard"))
    }
    @Test fun dubaiBoundsRejectNonFiniteAndOutsideCoordinates() {
        assertTrue(Dubai.contains(Dubai.CENTER_LAT, Dubai.CENTER_LNG))
        assertFalse(Dubai.contains(Double.NaN, 55.0))
        assertFalse(Dubai.contains(37.0, -122.0))
    }
    @Test fun neighbourhoodSearchAcceptsArabicAndEnglish() {
        assertEquals("deira", Dubai.search("Deira").first().key)
        assertEquals("deira", Dubai.search("ديرة").first().key)
    }
    @Test fun geographicDistanceHandlesSamePointAndAntipodes() {
        assertEquals(0.0, distanceKm(25.0, 55.0, 25.0, 55.0), 0.000001)
        assertEquals(Math.PI * 6371, distanceKm(25.0, 55.0, -25.0, -125.0), 0.001)
    }
}
