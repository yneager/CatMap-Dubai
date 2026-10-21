package com.catmap.dubai

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds: App Check with Play Integrity. Monitoring only: Firestore isn't set to enforce it, because APKs
 * installed from GitHub (not Google Play) may not pass (docs/security/APP_CHECK.md).
 */
internal object AppCheckSetup {
    fun factory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
