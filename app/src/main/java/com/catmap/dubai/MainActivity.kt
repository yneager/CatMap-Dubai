package com.catmap.dubai

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catmap.dubai.data.AppSettings
import com.catmap.dubai.notifications.SystemNotifications
import com.catmap.dubai.ui.auth.SetupRequiredScreen
import com.catmap.dubai.update.UpdatePrompt
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.LocalSnackbar
import com.catmap.dubai.ui.navigation.AppNavHost
import com.catmap.dubai.ui.startup.StartupIntro
import com.catmap.dubai.ui.startup.StartupStory
import com.catmap.dubai.util.AppRestart
import com.catmap.dubai.util.PostLinks
import com.catmap.dubai.ui.theme.CatMapTheme
import com.catmap.dubai.ui.theme.isDark

// AppCompatActivity (rather than ComponentActivity) gives per-app language switching on all Android versions.
class MainActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        com.catmap.dubai.data.ForegroundRefresh.refresh()
    }
    /** Screen requested by a tapped notification. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch reads the tapped notification. On rotation or a language change the activity is
        // re-created with the same intent, and reading it again would open that screen a second time.
        pendingRoute.value = savedInstanceState?.getString("pending_route") ?: routeFrom(intent)
        val container = (application as CatMapApp).container
        // A startup story plays only on a fresh launch from the launcher: not after rotation or process restore
        // (savedInstanceState), not when a notification or link asked for a screen, and not on the logout restart.
        val intro = mutableStateOf(
            StartupStory.pick().takeIf {
                savedInstanceState == null && pendingRoute.value == null && container.firebaseReady &&
                    !intent.getBooleanExtra(AppRestart.EXTRA_SKIP_INTRO, false)
            },
        )

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val dark = isDark(settings.theme)
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            CatMapTheme(dark = dark, largeText = settings.largeText) {
                val snackbar = remember { SnackbarHostState() }
                CompositionLocalProvider(
                    LocalContainer provides container,
                    LocalSnackbar provides snackbar,
                    LocalIsDark provides dark,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (!container.firebaseReady) {
                            SetupRequiredScreen()
                        } else {
                            // The system dialog would cover the startup story, so it waits until the story ends.
                            if (intro.value == null) AskForNotificationPermission()
                            AppNavHost(pendingRoute)
                        }
                        // Offered on every screen, signed in or not: a build without Firebase (or a signed-out
                        // user) must still be able to receive the update that fixes things.
                        UpdatePrompt()
                        // Drawn above everything while the app loads underneath it.
                        intro.value?.let { story -> StartupIntro(story, dark) { intro.value = null } }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFrom(intent)?.let { pendingRoute.value = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_route", pendingRoute.value)
        super.onSaveInstanceState(outState)
    }

    /** A catmap://post link from anywhere, or a screen from CatMap's own notifications ([SystemNotifications.routeFrom]). */
    private fun routeFrom(intent: Intent?): String? =
        PostLinks.parse(intent?.dataString)?.let { "post/$it" } ?: SystemNotifications.routeFrom(this, intent)
}

@androidx.compose.runtime.Composable
private fun AskForNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!SystemNotifications.canPost(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
