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
import com.catmap.dubai.ui.theme.CatMapTheme
import com.catmap.dubai.ui.theme.isDark

// AppCompatActivity (rather than ComponentActivity) gives per-app language switching on all Android versions.
class MainActivity : AppCompatActivity() {
    /** Screen requested by a tapped notification. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch reads the tapped notification. On rotation or a language change the activity is
        // re-created with the same intent, and reading it again would open that screen a second time.
        if (savedInstanceState == null) pendingRoute.value = intent?.getStringExtra(SystemNotifications.EXTRA_ROUTE)
        val container = (application as CatMapApp).container

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
                            AskForNotificationPermission()
                            AppNavHost(pendingRoute)
                        }
                        // Offered on every screen, signed in or not: a build without Firebase (or a signed-out
                        // user) must still be able to receive the update that fixes things.
                        UpdatePrompt()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(SystemNotifications.EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }
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
