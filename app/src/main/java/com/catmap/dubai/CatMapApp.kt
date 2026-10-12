package com.catmap.dubai

import android.app.Application
import com.catmap.dubai.data.AppErrors
import com.catmap.dubai.notifications.SystemNotifications
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import com.catmap.dubai.notifications.PushService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class CatMapApp : Application() {
    lateinit var container: AppContainer
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppErrors.init(this)

        // Vector maps: initialise once; tiles are cached by MapLibre itself.
        MapLibre.getInstance(this)

        SystemNotifications.createChannels(this)
        if (container.firebaseReady) {
            SystemNotifications.schedulePolling(this)
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
