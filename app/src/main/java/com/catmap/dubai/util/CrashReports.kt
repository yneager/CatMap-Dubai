package com.catmap.dubai.util

import com.catmap.dubai.BuildConfig
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/**
 * Crash reports through Firebase Crashlytics: the stack trace, phone model, Android and app version, never posts,
 * messages, photos, places or the account. Only release builds send them, and only while the person leaves
 * "Send crash reports" on in Settings. Everything here is a no-op when Firebase isn't set up.
 */
object CrashReports {
    fun setEnabled(on: Boolean) {
        runCatching { FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = on && BuildConfig.CRASH_REPORTS }
    }

    /** A caught error worth fixing. Being offline or a slow network isn't a bug, so those are left out. */
    fun record(t: Throwable) {
        if (!BuildConfig.CRASH_REPORTS || expected(t)) return
        runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
    }

    internal fun expected(t: Throwable): Boolean = t is IOException || t is FirebaseNetworkException ||
        (t is FirebaseFirestoreException && t.code in setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.CANCELLED,
        ))
}
