package com.catmap.dubai.ui.startup

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.airbnb.lottie.LottieCompositionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class StartupIntroTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun everyStoryOpensInBothThemesAndLastsFiveSeconds() {
        val context = RuntimeEnvironment.getApplication()
        for (story in StartupStory.entries) for (raw in listOf(story.light, story.dark)) {
            val composition = LottieCompositionFactory.fromRawResSync(context, raw).value
            assertNotNull("$story did not open", composition)
            assertEquals("$story", 5_000f, composition!!.duration, 1f)
        }
    }

    @Test
    fun theDrawReachesEveryStoryEvenly() {
        val random = Random(42)
        val counts = List(4_000) { StartupStory.pick(random) }.groupingBy { it }.eachCount()
        assertEquals(StartupStory.entries.toSet(), counts.keys)
        assertTrue(counts.toString(), counts.values.all { it in 850..1150 })
    }

    @Test
    fun completesOnceEvenIfNothingElseHappens() {
        var completed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { StartupIntro(StartupStory.ORIGINAL, dark = false) { completed++ } }
        compose.mainClock.advanceTimeBy(8_000)
        compose.waitForIdle()
        assertEquals(1, completed)
    }

    @Test
    fun aTapSkipsIt() {
        var completed = 0
        compose.setContent { StartupIntro(StartupStory.NIGHT_PATROL, dark = true) { completed++ } }
        compose.onNodeWithContentDescription("CatMap Dubai").performClick()
        compose.onNodeWithContentDescription("CatMap Dubai").performClick()
        assertEquals(1, completed)
    }
}
