package com.catmap.dubai.notifications

import com.catmap.dubai.data.model.AppNotification
import org.junit.Assert.*
import org.junit.Test

class NotificationDeliveryTest {
    private fun n(id: String, at: Long = 100, read: Boolean = false) = AppNotification(id = id, createdAt = at, read = read)
    @Test fun firstSnapshotSuppressesHistoricalAlerts() {
        assertTrue(NotificationDelivery.fresh(listOf(n("old")), null).isEmpty())
        assertEquals(setOf("old"), NotificationDelivery.checkpoint(listOf(n("old")), null))
    }
    @Test fun emptyFirstInboxStillEnablesNextAlert() {
        val baseline = NotificationDelivery.checkpoint(emptyList(), null)
        assertEquals(listOf(n("first")), NotificationDelivery.fresh(listOf(n("first")), baseline))
    }
    @Test fun sameTimestampDoesNotLoseSecondAlert() {
        assertEquals(listOf(n("b")), NotificationDelivery.fresh(listOf(n("a"), n("b")), setOf("a")))
    }
    @Test fun clockSkewDoesNotHideAlert() {
        assertEquals(listOf(n("new", 1)), NotificationDelivery.fresh(listOf(n("old", 1000), n("new", 1)), setOf("old")))
    }
    @Test fun readAndPreviouslyDeliveredAlertsAreNotReplayed() {
        assertTrue(NotificationDelivery.fresh(listOf(n("a"), n("b", read = true)), setOf("a")).isEmpty())
    }
    @Test fun accountsHaveIndependentBaselines() {
        assertEquals(1, NotificationDelivery.fresh(listOf(n("b")), setOf("a")).size)
        assertTrue(NotificationDelivery.fresh(listOf(n("b")), setOf("b")).isEmpty())
    }
    @Test fun checkpointIsBoundedAndKeepsCurrentInbox() {
        val seen = (0..1000).map { it.toString() }.toSet()
        val checkpoint = NotificationDelivery.checkpoint(listOf(n("0"), n("new")), seen)
        assertEquals(NotificationDelivery.MAX_REMEMBERED, checkpoint.size)
        assertTrue("0" in checkpoint && "new" in checkpoint)
    }
}
