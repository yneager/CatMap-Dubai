package com.catmap.dubai.util

import org.junit.Assert.*
import org.junit.Test

class AppRoutesTest {
    @Test fun routesAlertsUseAreAllowed() {
        listOf(
            "post/abc123", "cat/x_y-z", "sighting/s1", "rescue/r1", "station/st1", "sitter/uid1", "user/uid1",
            "match/lost__sighting", "chat/a_b", "adoption-requests", "achievements", "profile", "bookings",
            "bookings?tab=sitter", "bookings?tab=owner", "notifications",
        ).forEach { assertEquals(it, it, AppRoutes.alert(it)) }
    }

    @Test fun otherScreensAndInjectedRoutesAreRefused() {
        listOf(
            null, "", "settings", "admin", "admin-reports", "profile-edit", "chat-with/uid1", "post-edit/abc",
            "post/", "post/a/b", "post/a?x=1", "bookings?tab=admin", "post/a b", "post/a%2F..", "user/a\nadmin",
            "post/" + "a".repeat(301), "post/..", "post/.", "chat/.._x", "user/-x",
        ).forEach { assertNull(it.toString(), AppRoutes.alert(it)) }
    }
}
