package com.catmap.dubai.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.catmap.dubai.AppContainer
import com.catmap.dubai.CatMapApp
import com.catmap.dubai.MainActivity
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.NotifCategory
import com.catmap.dubai.data.NotifType
import com.catmap.dubai.data.model.AppNotification
import com.catmap.dubai.data.model.MaintenanceStatus
import com.catmap.dubai.data.model.RescueStatus
import com.catmap.dubai.data.model.RescueType
import com.catmap.dubai.data.model.Roles
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.data.model.roleLabel
import com.catmap.dubai.gamification.Badge
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Turns a stored notification into localized title and body text. */
object NotificationText {
    fun category(type: String): NotifCategory? = when (type) {
        NotifType.FOLLOWED_CAT -> NotifCategory.FOLLOWED
        NotifType.STALE_POST -> NotifCategory.POST_UPDATES
        NotifType.RESCUE_UPDATE -> NotifCategory.RESCUE
        NotifType.RESCUE_NEW_VOLUNTEER, NotifType.RESCUE_NEW_MOD -> NotifCategory.VOLUNTEER
        NotifType.ACHIEVEMENT -> NotifCategory.ACHIEVEMENTS
        NotifType.MODERATION, NotifType.VOLUNTEER_DECISION, NotifType.MAINTENANCE_UPDATE -> NotifCategory.MODERATION
        NotifType.MESSAGE -> NotifCategory.MESSAGES
        NotifType.LOST_NEARBY -> NotifCategory.LOST_NEARBY
        NotifType.POST_UPDATE, NotifType.POSSIBLE_MATCH, NotifType.MATCH_FOUND, NotifType.MATCH_REPORTER,
        NotifType.MATCH_INFO, NotifType.MATCH_REUNITED -> NotifCategory.POST_UPDATES
        NotifType.ADOPTION_REQUEST, NotifType.ADOPTION_DECISION -> NotifCategory.ADOPTION
        NotifType.BOOKING_REQUEST, NotifType.BOOKING_UPDATE, NotifType.REVIEW -> NotifCategory.SITTING
        else -> null
    }

    fun format(context: Context, n: AppNotification): Pair<String, String> {
        val p = n.params
        fun s(id: Int, vararg args: Any) = context.getString(id, *args)
        fun area() = Dubai.nameOf(p["area"])
        return when (n.type) {
            NotifType.STALE_POST -> s(R.string.stale_post_title) to s(R.string.stale_post_body, p["name"].orEmpty())
            NotifType.FOLLOWED_CAT -> s(R.string.n_followed_title, p["cat"] ?: "") to
                if (p["area"] == "medical") s(R.string.n_followed_medical_body, p["user"] ?: "", p["cat"] ?: "")
                else s(R.string.n_followed_body, p["user"] ?: "", p["cat"] ?: "", area())
            NotifType.RESCUE_UPDATE -> s(R.string.n_rescue_update_title) to
                s(R.string.n_rescue_update_body, s(labeledFrom(p["status"], RescueStatus.SUBMITTED).label), area())
            NotifType.RESCUE_NEW_VOLUNTEER -> s(R.string.n_rescue_volunteer_title) to
                s(R.string.n_rescue_new_body, s(labeledFrom(p["type"], RescueType.OTHER).label), area())
            NotifType.RESCUE_NEW_MOD -> s(R.string.n_rescue_mod_title) to
                s(R.string.n_rescue_new_body, s(labeledFrom(p["type"], RescueType.OTHER).label), area())
            NotifType.ACHIEVEMENT -> {
                val badge = Badge.fromKey(p["badge"] ?: "")
                s(R.string.n_achievement_title) to s(R.string.n_achievement_body, badge?.let { s(it.title) } ?: "")
            }
            NotifType.MODERATION -> s(R.string.n_moderation_title) to moderationBody(context, p)
            NotifType.VOLUNTEER_DECISION -> s(R.string.n_volunteer_title) to
                s(if (p["decision"] == "approved") R.string.n_volunteer_approved else R.string.n_volunteer_rejected)
            NotifType.MAINTENANCE_UPDATE -> s(R.string.n_maintenance_title) to
                s(R.string.n_maintenance_body, p["station"] ?: "", s(labeledFrom(p["status"], MaintenanceStatus.OPEN).label))
            NotifType.WELCOME -> s(R.string.n_welcome_title) to s(R.string.n_welcome_body)
            NotifType.MESSAGE -> s(R.string.n_message_title, p["user"] ?: "") to (p["text"] ?: "")
            NotifType.LOST_NEARBY -> s(R.string.n_lost_nearby_title, animal(context, p["type"])) to
                s(R.string.n_lost_nearby_body, p["name"].orEmpty().ifBlank { animal(context, p["type"]) }, area())
            NotifType.POSSIBLE_MATCH -> s(R.string.n_match_title) to
                s(R.string.n_match_body, s(labeledFrom(p["category"], com.catmap.dubai.data.model.PostCategory.SPOTTED).label), animal(context, p["type"]), area())
            // Lost-pet matches: only the animal's name, its type and neighbourhood names; never an exact place or a contact.
            NotifType.MATCH_FOUND -> s(R.string.n_match_found_title, p["name"].orEmpty().ifBlank { s(R.string.your_pet) }) to
                s(R.string.n_match_found_body, animal(context, p["type"]).lowercase(), area())
            NotifType.MATCH_REPORTER -> s(R.string.n_match_reporter_title, p["name"].orEmpty().ifBlank { animal(context, p["type"]).lowercase() }) to
                s(R.string.n_match_reporter_body, animal(context, p["type"]).lowercase(), area(), Dubai.nameOf(p["lostArea"]))
            NotifType.MATCH_INFO -> s(R.string.n_match_info_title, p["name"].orEmpty().ifBlank { animal(context, p["type"]) }) to
                s(R.string.n_match_info_body, p["user"].orEmpty())
            NotifType.MATCH_REUNITED -> s(R.string.n_match_reunited_title, p["name"].orEmpty().ifBlank { animal(context, p["type"]) }) to
                s(R.string.n_match_reunited_body)
            NotifType.POST_UPDATE -> s(R.string.n_post_update_title, p["name"].orEmpty().ifBlank { animal(context, p["type"]) }) to
                s(if (p["kind"] == "sighting") R.string.n_post_sighting_body else R.string.n_post_update_body, p["user"] ?: "")
            NotifType.ADOPTION_REQUEST -> s(R.string.n_adoption_request_title) to s(R.string.n_adoption_request_body, p["user"] ?: "", p["name"] ?: "")
            NotifType.ADOPTION_DECISION -> s(R.string.n_adoption_decision_title) to
                s(if (p["decision"] == "approved") R.string.n_adoption_approved else R.string.n_adoption_declined, p["name"] ?: "")
            NotifType.BOOKING_REQUEST -> s(R.string.n_booking_request_title) to s(R.string.n_booking_request_body, p["user"] ?: "")
            NotifType.BOOKING_UPDATE -> s(R.string.n_booking_update_title) to
                s(R.string.n_booking_update_body, p["user"] ?: "", s(labeledFrom(p["status"], com.catmap.dubai.data.model.BookingStatus.REQUESTED).label))
            NotifType.REVIEW -> s(R.string.n_review_title) to s(R.string.n_review_body, p["user"] ?: "", p["rating"] ?: "")
            else -> s(R.string.app_name) to ""
        }
    }

    private fun animal(context: Context, key: String?) =
        context.getString(labeledFrom(key, com.catmap.dubai.data.model.AnimalType.OTHER).label)

    private fun moderationBody(context: Context, p: Map<String, String>): String {
        if (p["target"] == "role") {
            return context.getString(R.string.n_role_changed, context.getString(roleLabel(p["label"] ?: Roles.USER)))
        }
        val target = when (p["target"]) {
            "merge" -> R.string.target_merge
            "sighting" -> R.string.target_sighting
            "cat" -> R.string.target_cat
            "report" -> R.string.target_report
            "post" -> R.string.target_post
            "sitter" -> R.string.target_sitter
            "account" -> R.string.target_account
            else -> R.string.target_content
        }
        val decision = when (p["decision"]) {
            "approved" -> R.string.decision_approved
            "rejected" -> R.string.decision_rejected
            "removed" -> R.string.decision_removed
            "resolved" -> R.string.decision_resolved
            "dismissed" -> R.string.decision_dismissed
            "suspended" -> R.string.decision_suspended
            "restored" -> R.string.decision_restored
            else -> R.string.decision_updated
        }
        return context.getString(R.string.n_moderation_body, context.getString(target), p["label"] ?: "", context.getString(decision))
    }
}

object SystemNotifications {
    const val EXTRA_ROUTE = "route"
    private const val EXTRA_ROUTE_KEY = "route_key"
    private val deliveryMutex = Mutex()

    /**
     * A random key known only to this install, carried by CatMap's own notification taps. MainActivity must stay
     * exported (launcher and catmap://post links), so without it any other app could open any screen by sending a
     * route (threat model S10).
     */
    internal fun routeKey(context: Context): String {
        val prefs = context.getSharedPreferences("catmap_routes", Context.MODE_PRIVATE)
        prefs.getString(EXTRA_ROUTE_KEY, null)?.let { return it }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        // apply(): readable at once in this process; a key lost to a crash only voids notifications already shown.
        prefs.edit().putString(EXTRA_ROUTE_KEY, key).apply()
        return key
    }

    /** The screen a tapped CatMap notification asks for, or null for any other intent. */
    fun routeFrom(context: Context, intent: Intent?): String? = runCatching {
        // Reading extras unparcels them; a malformed bundle from another app must not crash the launch.
        val key = intent?.getStringExtra(EXTRA_ROUTE_KEY) ?: return null
        if (!java.security.MessageDigest.isEqual(key.toByteArray(), routeKey(context).toByteArray())) return null
        com.catmap.dubai.util.AppRoutes.alert(intent.getStringExtra(EXTRA_ROUTE))
    }.getOrNull()

    private fun channelId(c: NotifCategory) = "catmap_" + c.name.lowercase()

    fun channelName(c: NotifCategory) = when (c) {
        NotifCategory.FOLLOWED -> R.string.notif_cat_followed
        NotifCategory.RESCUE -> R.string.notif_cat_rescue
        NotifCategory.VOLUNTEER -> R.string.notif_cat_volunteer
        NotifCategory.ACHIEVEMENTS -> R.string.notif_cat_achievements
        NotifCategory.MODERATION -> R.string.notif_cat_moderation
        NotifCategory.MESSAGES -> R.string.notif_cat_messages
        NotifCategory.LOST_NEARBY -> R.string.notif_cat_lost_nearby
        NotifCategory.POST_UPDATES -> R.string.notif_cat_post_updates
        NotifCategory.ADOPTION -> R.string.notif_cat_adoption
        NotifCategory.SITTING -> R.string.notif_cat_sitting
    }

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        NotifCategory.entries.forEach { c ->
            val importance = if (c == NotifCategory.VOLUNTEER || c == NotifCategory.RESCUE || c == NotifCategory.MESSAGES || c == NotifCategory.LOST_NEARBY) NotificationManager.IMPORTANCE_HIGH
            else NotificationManager.IMPORTANCE_DEFAULT
            manager.createNotificationChannel(NotificationChannel(channelId(c), context.getString(channelName(c)), importance))
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, n: AppNotification, category: NotifCategory) {
        if (!canPost(context)) return
        // Delivery may race lazy initialization on a fresh process.
        createChannels(context)
        val (title, body) = NotificationText.format(context, n)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, n.route.ifBlank { "notifications" })
            putExtra(EXTRA_ROUTE_KEY, routeKey(context))
        }
        val pending = PendingIntent.getActivity(
            context, n.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // On a locked screen only "CatMap · Messages" shows; names and message text appear once unlocked.
        val publicVersion = NotificationCompat.Builder(context, channelId(category))
            .setSmallIcon(R.drawable.ic_stat_cat)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(channelName(category)))
            .build()
        val notification = NotificationCompat.Builder(context, channelId(category))
            .setSmallIcon(R.drawable.ic_stat_cat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(n.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    /** Shows unread notifications that arrived since the last delivery, respecting the user's settings. */
    suspend fun deliver(container: AppContainer, uid: String, notifications: List<AppNotification>) = deliveryMutex.withLock {
        if (container.authRepo.currentUid != uid) return@withLock
        val settings = container.settings.current()
        val delivered = container.settings.deliveredNotifications(uid)
        val fresh = NotificationDelivery.fresh(notifications, delivered)
        val time = java.time.LocalTime.now()
        val quiet = settings.quietHoursEnabled && QuietHours.contains(time.hour*60+time.minute,settings.quietStart,settings.quietEnd)
        if (container.authRepo.currentUid != uid) return@withLock
        fresh.forEach { n ->
            val category = NotificationText.category(n.type) ?: return@forEach
            if (!quiet && settings.allows(category)) post(container.appContext, n, category)
        }
        container.settings.setDeliveredNotifications(uid, NotificationDelivery.checkpoint(notifications, delivered))
    }

    /**
     * How often a closed app checks the inbox. Every 15 minutes, a few hundred installed phones would use the free
     * plan's whole daily read quota on empty checks (docs/cost/FREE_PLAN_CAPACITY.md), so it is 4 hours until alerts
     * are pushed instead. An open app still gets alerts at once from its live inbox listener.
     */
    const val POLL_HOURS = 4L

    fun schedulePolling(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(POLL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UPDATE, not KEEP: phones that scheduled the old 15-minute check switch to the new interval.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("notification-poll", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/** Checks the in-app inbox in the background so alerts arrive even when the app is closed. */
class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CatMapApp).container
        if (!container.firebaseReady) return Result.success()
        val preferences = container.settings.current()
        if (!preferences.notificationsEnabled || preferences.categories.values.none { it }
            || !NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        val time = java.time.LocalTime.now()
        if (preferences.quietHoursEnabled && QuietHours.contains(time.hour * 60 + time.minute, preferences.quietStart, preferences.quietEnd)) return Result.success()
        val uid = container.authRepo.currentUid ?: return Result.success()
        return runCatching {
            val started = System.currentTimeMillis()
            val lastCheck = container.settings.lastInboxCheck(uid) ?: (started - CHECK_OVERLAP_MS)
            var cursor = container.settings.pollCursor(uid)
            var complete = false
            repeat(5) {
                if (complete) return@repeat
                val page = container.notifications.sincePage(uid, lastCheck - CHECK_OVERLAP_MS, cursor)
                SystemNotifications.deliver(container, uid, page)
                cursor = page.lastOrNull()?.let { it.createdAt to it.id } ?: cursor
                container.settings.setPollCursor(uid, cursor)
                complete = page.size < 50
            }
            if (complete) {
                container.settings.setLastInboxCheck(uid, started)
                container.settings.setPollCursor(uid, null)
            }
            Result.success()
        }.getOrElse { if (it is CancellationException) throw it else Result.retry() }
    }

    private companion object {
        const val CHECK_OVERLAP_MS = 60L * 60 * 1000
    }
}
