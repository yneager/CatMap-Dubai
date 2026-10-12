package com.catmap.dubai.notifications

import android.annotation.SuppressLint
import com.catmap.dubai.BuildConfig
import com.catmap.dubai.CatMapApp
import com.catmap.dubai.data.Col
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Receives FCM pushes sent by the Cloud Function in functions/index.js. A push carries no personal content;
 * it only wakes the app, which then reads the person's inbox and shows new alerts with the usual settings
 * and de-duplication. Without the Blaze plan nothing sends pushes and the 15-minute check covers delivery.
 */
// FCM 25 replaced onNewToken with onRegistered (below); lint still looks for the old name.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class PushService : FirebaseMessagingService() {
    /** FCM 25 reports new and refreshed registrations here. */
    override fun onRegistered(token: String) {
        if (BuildConfig.PUSH_ENABLED) runCatching { runBlocking { saveToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val container = (applicationContext as CatMapApp).container
        val uid = container.authRepo.currentUid ?: return
        runCatching { runBlocking { SystemNotifications.deliver(container, uid, container.notifications.latest(uid)) } }
    }

    companion object {
        /** Turns FCM on (it is off by default in the manifest) and stores this phone's token for the signed-in user. */
        suspend fun register() {
            if (!BuildConfig.PUSH_ENABLED) return
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            // A fresh registration also arrives in onRegistered; reading the current token covers sign-ins on a
            // phone that was registered earlier.
            @Suppress("DEPRECATION")
            saveToken(messaging.token.await())
        }

        private suspend fun saveToken(token: String) {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().collection(Col.USERS).document(uid).collection(Col.PRIVATE).document("push")
                .set(mapOf("tokens" to FieldValue.arrayUnion(token)), SetOptions.merge()).await()
        }
    }
}
