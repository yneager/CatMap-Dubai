package com.catmap.dubai.ui.startup

import android.provider.Settings
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.catmap.dubai.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * The five-second startup stories (the same Lottie files as web/src/assets/startup). One is drawn at random on each
 * fresh launch; the light and dark files of a story share their timing, so a theme change mid-way keeps its place.
 */
enum class StartupStory(@RawRes val light: Int, @RawRes val dark: Int) {
    ORIGINAL(R.raw.startup_original, R.raw.startup_original_dark),
    ROOFTOP_RESCUE(R.raw.startup_rooftop_rescue, R.raw.startup_rooftop_rescue_dark),
    NEIGHBORHOOD_RELAY(R.raw.startup_neighborhood_relay, R.raw.startup_neighborhood_relay_dark),
    NIGHT_PATROL(R.raw.startup_night_patrol, R.raw.startup_night_patrol_dark);

    companion object {
        fun pick(random: Random = Random.Default): StartupStory = entries[random.nextInt(entries.size)]
    }
}

/** How long the story file may take to open before the app is shown without it. */
private const val LOAD_TIMEOUT_MS = 2_000L
/** Never hold the app longer than this, whatever happens to playback. */
private const val DEADLINE_MS = 7_000L

/**
 * Plays [story] once over the app, which keeps starting underneath, then calls [onComplete] exactly once. A tap
 * skips it; a file that fails to open, or animations turned off in the system settings, end it straight away.
 */
@Composable
fun StartupIntro(story: StartupStory, dark: Boolean, onComplete: () -> Unit) {
    val context = LocalContext.current
    val latestComplete by rememberUpdatedState(onComplete)
    var completed by remember { mutableStateOf(false) }
    fun completeOnce() {
        if (!completed) {
            completed = true
            latestComplete()
        }
    }
    val result = rememberLottieComposition(LottieCompositionSpec.RawRes(if (dark) story.dark else story.light))
    val composition by result
    var progress by remember { mutableFloatStateOf(0f) }
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    LaunchedEffect(Unit) {
        if (animationsOff) return@LaunchedEffect completeOnce()
        val first = withTimeoutOrNull(LOAD_TIMEOUT_MS) { snapshotFlow { composition }.filterNotNull().first() }
            ?: return@LaunchedEffect completeOnce()
        // Wall-clock progress rather than Lottie's own animator, so swapping the light/dark file doesn't restart it.
        val durationNs = first.duration * 1_000_000f
        val start = withFrameNanos { it }
        while (progress < 1f) {
            val now = withFrameNanos { it }
            progress = ((now - start) / durationNs).coerceIn(0f, 1f)
        }
        completeOnce()
    }
    LaunchedEffect(Unit) {
        delay(DEADLINE_MS)
        completeOnce()
    }
    LaunchedEffect(result.isFailure) { if (result.isFailure) completeOnce() }
    val description = stringResource(R.string.app_name)
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF1A1410) else Color(0xFFFFFBF7))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { completeOnce() }
            .semantics { contentDescription = description },
    ) {
        LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.fillMaxSize())
    }
}
