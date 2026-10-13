package com.catmap.dubai.notifications

import org.junit.Assert.*
import org.junit.Test

class QuietHoursTest {
    @Test fun daytimeWindowHasInclusiveStartExclusiveEnd() {
        assertFalse(QuietHours.contains(599,600,720)); assertTrue(QuietHours.contains(600,600,720)); assertFalse(QuietHours.contains(720,600,720))
    }
    @Test fun midnightCrossingIncludesBothSides() {
        assertTrue(QuietHours.contains(23*60,22*60,7*60)); assertTrue(QuietHours.contains(0,22*60,7*60)); assertFalse(QuietHours.contains(12*60,22*60,7*60))
    }
    @Test fun equalEndpointsSilenceAllDay() { assertTrue(QuietHours.contains(500,0,0)); assertTrue(QuietHours.contains(0,100,100)) }
}
