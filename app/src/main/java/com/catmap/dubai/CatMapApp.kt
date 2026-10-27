package com.catmap.dubai

import android.app.Application
import com.catmap.dubai.data.AppErrors
import com.catmap.dubai.notifications.SystemNotifications
import com.catmap.dubai.util.CrashReports
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import com.catmap.dubai.notifications.PushService
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.maplibre.android.MapLibre

class CatMapApp : Application() {
    lateinit var container: AppContainer
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // App Check first, before any Firebase call, so requests carry its token. Monitoring only; the local emulators
        // don't check it.
        if (container.firebaseReady && BuildConfig.EMULATOR_HOST.isEmpty()) {
            runCatching { FirebaseAppCheck.getInstance().installAppCheckProviderFactory(AppCheckSetup.factory()) }
        }
        container.finishPendingWipe()
        AppErrors.init(this)
        if (container.firebaseReady) {
            container.settings.settings.map { it.crashReports }.distinctUntilChanged()
                .onEach { CrashReports.setEnabled(it) }.launchIn(container.scope)
        }

        // The map initializes lazily on its UI thread in CommunityMap, when it is actually shown.
        container.scope.launch(Dispatchers.IO) {
            SystemNotifications.createChannels(this@CatMapApp)
            if(container.firebaseReady) SystemNotifications.schedulePolling(this@CatMapApp)
        }
        if (container.firebaseReady) {
            // While the app is running, inbox changes arrive in real time.
            container.session.user
                .flatMapLatest { user ->
                    if (user == null) emptyFlow()
                    else container.notifications.notifications(user.uid).map { user.uid to it }
                }
                .onEach { (uid, inbox) ->
                    try { SystemNotifications.deliver(container, uid, inbox) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { AppErrors.report(e) }
                }
                .launchIn(container.scope)
            // With the Blaze plan, register this phone for push so alerts arrive instantly.
            container.session.user
                .map { it?.uid }
                .distinctUntilChanged()
                .onEach { uid -> if (uid != null) container.scope.launch { runCatching { PushService.register() } } }
                .launchIn(container.scope)
        }
    }
}
