package com.catmap.dubai.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewPolicyTest {
    @Test fun firstInstallDoesNotShowNotes() {
        assertFalse(WhatsNewPolicy.shouldShow("", "0.0.4", 10_000, 10_000))
    }
    @Test fun firstUpgradeFromAnOlderAppShowsNotes() {
        assertTrue(WhatsNewPolicy.shouldShow("", "0.0.4", 10_000, 20_000))
    }
    @Test fun versionChangeShowsNotes() {
        assertTrue(WhatsNewPolicy.shouldShow("0.0.3", "0.0.4", 10_000, 20_000))
    }
    @Test fun dismissedVersionDoesNotShowAgain() {
        assertFalse(WhatsNewPolicy.shouldShow("0.0.4", "0.0.4", 10_000, 20_000))
    }
}
