package com.catmap.dubai.data

import com.catmap.dubai.data.model.Post
import org.junit.Assert.*
import org.junit.Test

class PostStalenessTest {
    private val at = 200 * PostStaleness.DAY
    @Test fun reminderStartsAtFourteenDays() {
        val p=Post(id="abc",category="lost",createdAt=at-14*PostStaleness.DAY)
        assertTrue(PostStaleness.needsReminder(p,at))
        assertFalse(PostStaleness.needsReminder(p,at-1))
    }
    @Test fun newActivitySuppressesReminderAndLegacyTimestampWorks() {
        val p=Post(category="found",createdAt=1,updatedAt=at)
        assertFalse(PostStaleness.needsReminder(p,at))
        assertFalse(PostStaleness.hiddenByDefault(p,at))
    }
    @Test fun oldOpenPostsAreHiddenButResolvedPostsRemain() {
        val p=Post(createdAt=at-90*PostStaleness.DAY)
        assertTrue(PostStaleness.hiddenByDefault(p,at))
        assertFalse(PostStaleness.hiddenByDefault(p.copy(status="resolved"),at))
    }
    @Test fun reminderIdIsStableWithinWindowAndChangesNextWindow() {
        val p=Post(id="p1")
        assertEquals(PostStaleness.reminderId(p,at),PostStaleness.reminderId(p,at+1))
        assertNotEquals(PostStaleness.reminderId(p,at),PostStaleness.reminderId(p,at+14*PostStaleness.DAY))
    }
}
