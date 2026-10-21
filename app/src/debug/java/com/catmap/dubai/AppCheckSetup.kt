package com.catmap.dubai

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug and staging builds: App Check's debug provider. On first start it logs a debug token (logcat tag
 * "DebugAppCheckProvider"); register it in the Firebase console (App Check -> Apps -> Manage debug tokens) to see this
 * build's requests as verified. Monitoring only: nothing is refused without it (docs/security/APP_CHECK.md).
 */
internal object AppCheckSetup {
    fun factory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
