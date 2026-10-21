package com.catmap.dubai.notifications

import android.app.Application
import android.content.Intent
import com.catmap.dubai.data.LocalWipe
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Threat model S10: another app can't open a CatMap screen by sending a route to the exported MainActivity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationRouteTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun intent(route: String, key: String?) = Intent().putExtra(SystemNotifications.EXTRA_ROUTE, route).apply {
        if (key != null) putExtra("route_key", key)
    }

    @Test fun ownNotificationOpensItsScreen() {
        val key = SystemNotifications.routeKey(context)
        assertEquals(key, SystemNotifications.routeKey(context))
        assertEquals(64, key.length)
        assertEquals("chat/a_b", SystemNotifications.routeFrom(context, intent("chat/a_b", key)))
    }

    @Test fun routeFromAnotherAppIsIgnored() {
        assertNull(SystemNotifications.routeFrom(context, intent("settings", null)))
        assertNull(SystemNotifications.routeFrom(context, intent("chat/a_b", null)))
        assertNull(SystemNotifications.routeFrom(context, intent("chat/a_b", "0".repeat(64))))
        assertNull(SystemNotifications.routeFrom(context, null))
    }

    @Test fun evenWithTheKeyOnlyAlertScreensOpen() {
        val key = SystemNotifications.routeKey(context)
        assertNull(SystemNotifications.routeFrom(context, intent("admin", key)))
        assertNull(SystemNotifications.routeFrom(context, intent("chat-with/uid?postId=x", key)))
    }

    @Test fun logoutFlagsSurviveUntilDone() {
        val wipe = LocalWipe(context)
        assertFalse(wipe.signOutPending || wipe.cachePending)
        wipe.request()
        assertTrue(LocalWipe(context).signOutPending && LocalWipe(context).cachePending)
        wipe.signOutDone()
        assertFalse(LocalWipe(context).signOutPending)
        assertTrue("a failed cache clear is retried on the next start", LocalWipe(context).cachePending)
        wipe.cacheDone()
        assertFalse(LocalWipe(context).cachePending)
    }
}
