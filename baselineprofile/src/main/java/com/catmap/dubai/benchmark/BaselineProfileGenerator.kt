package com.catmap.dubai.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule=BaselineProfileRule()
    @Test fun startupAndExplore() = rule.collect(
        packageName="com.catmap.dubai.staging",
        includeInStartupProfile=true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        device.findObject(androidx.test.uiautomator.By.text("Explore"))?.click()
        device.waitForIdle()
    }
}
