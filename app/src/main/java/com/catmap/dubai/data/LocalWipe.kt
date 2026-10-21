package com.catmap.dubai.data

import android.annotation.SuppressLint
import android.content.Context
import java.io.File

/**
 * What a signed-out account leaves on the phone (threat model S12): Firestore's offline cache (chats, inbox, profiles
 * the account could read), the photo cache, shared files and camera captures. Firestore can only clear its cache
 * before it starts, so logout writes these flags, signs out and restarts the app ([com.catmap.dubai.util.AppRestart]);
 * the fresh process clears everything before the first query ([com.catmap.dubai.AppContainer.finishPendingWipe]).
 *
 * Two flags, because they finish at different times: signing out (again) only makes sense right after the restart,
 * while a cache clear that failed is retried on every start until it works.
 */
@SuppressLint("ApplySharedPref") // commit(): the process may be restarted right after
class LocalWipe(private val context: Context) {
    private val prefs = context.getSharedPreferences("catmap_wipe", Context.MODE_PRIVATE)

    /** Written synchronously, so a restart right after can't lose it. */
    fun request() {
        prefs.edit().putBoolean(SIGN_OUT, true).putBoolean(CACHE, true).commit()
    }

    val signOutPending: Boolean get() = prefs.getBoolean(SIGN_OUT, false)
    val cachePending: Boolean get() = prefs.getBoolean(CACHE, false)

    fun signOutDone() { prefs.edit().remove(SIGN_OUT).commit() }
    fun cacheDone() { prefs.edit().remove(CACHE).commit() }

    /** Shared exports, posters and camera captures in the app's cache. Drafts stay: they're per account. */
    fun deleteCacheFiles() {
        listOf("share", "camera").forEach { File(context.cacheDir, it).deleteRecursively() }
    }

    private companion object {
        const val SIGN_OUT = "sign_out"
        const val CACHE = "cache"
    }
}
