package com.catmap.dubai.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlin.system.exitProcess

/** Starts the app again from the login screen in a fresh process (used after logout, see [com.catmap.dubai.data.LocalWipe]). */
object AppRestart {
    /** Set on the relaunch intent: a restart after logout is not a new visit, so it skips the startup story. */
    const val EXTRA_SKIP_INTRO = "catmap.skip_intro"

    fun relaunch(context: Context, message: Int? = null) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        main.post {
            // Text toasts are drawn by the system on Android 11+, so the message outlives this process.
            message?.let { Toast.makeText(app, it, Toast.LENGTH_LONG).show() }
            // A short pause lets queued preference writes (the sign-out) reach the disk; the next start signs out
            // again anyway if one was lost.
            main.postDelayed({
                val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launch != null) {
                    app.startActivity(Intent.makeRestartActivityTask(launch.component).putExtra(EXTRA_SKIP_INTRO, true))
                }
                exitProcess(0)
            }, 300)
        }
    }
}
