package com.catmap.dubai.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Notification categories; each has its own switch in Settings and its own Android channel. */
enum class NotifCategory { FOLLOWED, RESCUE, VOLUNTEER, ACHIEVEMENTS, MODERATION, MESSAGES, LOST_NEARBY, POST_UPDATES, ADOPTION, SITTING }

enum class MapMode { CLASSIC, MODERN }

/** Map filter chips; several can be on at once. Empty means "all animals". */
enum class MapLayer { LOST, FOUND, SPOTTED, NEEDS_HELP, STREET_CATS, ADOPTION, SITTING }

/** Filters shared by the map and the Explore feed; remembered between launches. */
data class FeedFilters(
    val layers: Set<MapLayer> = emptySet(),
    val animalTypes: Set<String> = emptySet(),
    /** 0 = any distance. */
    val maxDistanceKm: Int = 0,
    /** 0 = any time. */
    val maxAgeDays: Int = 0,
    val rewardOnly: Boolean = false,
    val includeOlder: Boolean = false,
    val microchip: String? = null,
) {
    val extraCount: Int get() = animalTypes.size + (if (maxDistanceKm > 0) 1 else 0) + (if (maxAgeDays > 0) 1 else 0) + (if (rewardOnly) 1 else 0) + (if (includeOlder) 1 else 0)
    val isEmpty: Boolean get() = layers.isEmpty() && extraCount == 0
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val largeText: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val categories: Map<NotifCategory, Boolean> = NotifCategory.entries.associateWith { true },
    val privacyLevel: String = LocationPrivacy.STANDARD,
    val shareExactWithVolunteers: Boolean = true,
    val mapMode: MapMode = MapMode.MODERN,
    val satellite: Boolean = false,
    val showStations: Boolean = true,
    val heatmap: Boolean = false,
    val filters: FeedFilters = FeedFilters(),
    val skippedVersion: String = "",
    val lastUpdateCheck: Long = 0,
    val quietHoursEnabled: Boolean = false,
    val quietStart: Int = 22*60,
    val quietEnd: Int = 7*60,
    val lastSeenVersion:String = "",
    val releaseNotesVersion:String = "",
    val releaseNotes:String = "",
    /** Photo review on new sightings (docs/MATCHING_REVIEW_V1.md); offered to moderators only, off by default. */
    val photoReview: Boolean = false,
) {
    fun allows(category: NotifCategory) = notificationsEnabled && categories[category] != false
}

class SettingsRepository(private val context: Context) {
    suspend fun pollCursor(uid: String): Pair<Long, String>? {
        val raw = context.dataStore.data.first()[stringPreferencesKey("poll_cursor_$uid")] ?: return null
        val split = raw.indexOf(':')
        return if (split > 0) raw.substring(0, split).toLongOrNull()?.let { it to raw.substring(split + 1) } else null
    }
    suspend fun setPollCursor(uid: String, cursor: Pair<Long, String>?) {
        context.dataStore.edit { prefs ->
            val key = stringPreferencesKey("poll_cursor_$uid")
            if (cursor == null) prefs.remove(key) else prefs[key] = "${cursor.first}:${cursor.second}"
        }
    }
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val PRIVACY_LEVEL = stringPreferencesKey("privacy_level")
        val SHARE_EXACT = booleanPreferencesKey("share_exact")
        fun delivered(uid: String) = stringSetPreferencesKey("delivered_notifications_" + uid)
        fun areaFollowPrivate(uid: String) = booleanPreferencesKey("area_follow_private_" + uid)
        fun lastInboxCheck(uid: String) = longPreferencesKey("last_inbox_check_" + uid)
        fun lastStaleCheck(uid: String) = longPreferencesKey("last_stale_check_" + uid)
        fun category(c: NotifCategory) = booleanPreferencesKey("notif_" + c.name.lowercase())
        val MAP_MODE = stringPreferencesKey("map_mode")
        val SATELLITE = booleanPreferencesKey("map_satellite")
        val STATIONS = booleanPreferencesKey("map_stations")
        val HEATMAP = booleanPreferencesKey("map_heatmap")
        val LAYERS = stringSetPreferencesKey("filter_layers")
        val ANIMALS = stringSetPreferencesKey("filter_animals")
        val DISTANCE = intPreferencesKey("filter_distance")
        val AGE = intPreferencesKey("filter_age")
        val REWARD = booleanPreferencesKey("filter_reward")
        val INCLUDE_OLDER = booleanPreferencesKey("filter_include_older")
        val MICROCHIP = stringPreferencesKey("filter_microchip")
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val RELEASE_NOTES_VERSION = stringPreferencesKey("release_notes_version")
        val RELEASE_NOTES = stringPreferencesKey("release_notes")
        val PHOTO_REVIEW = booleanPreferencesKey("photo_review")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        theme = runCatching { ThemeMode.valueOf(this[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        largeText = this[Keys.LARGE_TEXT] ?: false,
        notificationsEnabled = this[Keys.NOTIFICATIONS] ?: true,
        categories = NotifCategory.entries.associateWith { this[Keys.category(it)] ?: true },
        privacyLevel = this[Keys.PRIVACY_LEVEL] ?: LocationPrivacy.STANDARD,
        shareExactWithVolunteers = this[Keys.SHARE_EXACT] ?: true,
        mapMode = runCatching { MapMode.valueOf(this[Keys.MAP_MODE] ?: "MODERN") }.getOrDefault(MapMode.MODERN),
        satellite = this[Keys.SATELLITE] ?: false,
        showStations = this[Keys.STATIONS] ?: true,
        heatmap = this[Keys.HEATMAP] ?: false,
        filters = FeedFilters(
            layers = this[Keys.LAYERS].orEmpty().mapNotNull { runCatching { MapLayer.valueOf(it) }.getOrNull() }.toSet(),
            animalTypes = this[Keys.ANIMALS].orEmpty(),
            maxDistanceKm = this[Keys.DISTANCE] ?: 0,
            maxAgeDays = this[Keys.AGE] ?: 0,
            rewardOnly = this[Keys.REWARD] ?: false,
            includeOlder = this[Keys.INCLUDE_OLDER] ?: false,
            microchip = this[Keys.MICROCHIP]?.takeIf {it in listOf("yes","no","unknown")},
        ),
        skippedVersion = this[Keys.SKIPPED_VERSION] ?: "",
        lastUpdateCheck = this[Keys.LAST_UPDATE_CHECK] ?: 0L,
        quietHoursEnabled = this[Keys.QUIET_ENABLED] ?: false,
        quietStart = (this[Keys.QUIET_START] ?: 22*60).coerceIn(0,1439),
        quietEnd = (this[Keys.QUIET_END] ?: 7*60).coerceIn(0,1439),
        lastSeenVersion = this[Keys.LAST_SEEN_VERSION].orEmpty(),
        releaseNotesVersion = this[Keys.RELEASE_NOTES_VERSION].orEmpty(),
        releaseNotes = this[Keys.RELEASE_NOTES].orEmpty(),
        photoReview = this[Keys.PHOTO_REVIEW] ?: false,
    )

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setLargeText(on: Boolean) = context.dataStore.edit { it[Keys.LARGE_TEXT] = on }
    suspend fun setNotificationsEnabled(on: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATIONS] = on }
    suspend fun setCategory(c: NotifCategory, on: Boolean) = context.dataStore.edit { it[Keys.category(c)] = on }
    suspend fun setPrivacyLevel(level: String) = context.dataStore.edit { it[Keys.PRIVACY_LEVEL] = level }
    suspend fun setShareExact(on: Boolean) = context.dataStore.edit { it[Keys.SHARE_EXACT] = on }
    suspend fun setPhotoReview(on: Boolean) = context.dataStore.edit { it[Keys.PHOTO_REVIEW] = on }
    suspend fun setMapMode(mode: MapMode) = context.dataStore.edit { it[Keys.MAP_MODE] = mode.name }
    suspend fun setSatellite(on: Boolean) = context.dataStore.edit { it[Keys.SATELLITE] = on }
    suspend fun setShowStations(on: Boolean) = context.dataStore.edit { it[Keys.STATIONS] = on }
    suspend fun setHeatmap(on: Boolean) = context.dataStore.edit { it[Keys.HEATMAP] = on }
    suspend fun setSkippedVersion(version: String) = context.dataStore.edit { it[Keys.SKIPPED_VERSION] = version }
    suspend fun setLastUpdateCheck(at: Long) = context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK] = at }
    suspend fun setLastSeenVersion(version:String) = context.dataStore.edit {it[Keys.LAST_SEEN_VERSION]=version}
    suspend fun saveReleaseNotes(version:String,notes:String) = context.dataStore.edit {it[Keys.RELEASE_NOTES_VERSION]=version; it[Keys.RELEASE_NOTES]=notes.take(50_000)}
    suspend fun setQuietHours(enabled:Boolean,start:Int,end:Int) = context.dataStore.edit {
        require(start in 0..1439 && end in 0..1439)
        it[Keys.QUIET_ENABLED]=enabled; it[Keys.QUIET_START]=start; it[Keys.QUIET_END]=end
    }
    suspend fun setFilters(f: FeedFilters) = context.dataStore.edit {
        it[Keys.LAYERS] = f.layers.map { l -> l.name }.toSet()
        it[Keys.ANIMALS] = f.animalTypes
        it[Keys.DISTANCE] = f.maxDistanceKm
        it[Keys.AGE] = f.maxAgeDays
        it[Keys.REWARD] = f.rewardOnly
        it[Keys.INCLUDE_OLDER] = f.includeOlder
        if(f.microchip==null) it.remove(Keys.MICROCHIP) else it[Keys.MICROCHIP]=f.microchip
    }

    // Null means this account has never established a baseline; empty means an empty baseline.
    suspend fun deliveredNotifications(uid: String): Set<String>? = context.dataStore.data.first()[Keys.delivered(uid)]
    suspend fun setDeliveredNotifications(uid: String, ids: Set<String>) = context.dataStore.edit { it[Keys.delivered(uid)] = ids }
    suspend fun clearDeliveredNotifications(uid: String) = context.dataStore.edit { it.remove(Keys.delivered(uid)) }

    /** When the background check last read this account's inbox (device time), or null if it never has. */
    suspend fun lastInboxCheck(uid: String): Long? = context.dataStore.data.first()[Keys.lastInboxCheck(uid)]
    suspend fun setLastInboxCheck(uid: String, at: Long) = context.dataStore.edit { it[Keys.lastInboxCheck(uid)] = at }

    /** When this account's posts were last checked for 14-day reminders (device time), 0 if never. */
    suspend fun lastStaleCheck(uid: String): Long = context.dataStore.data.first()[Keys.lastStaleCheck(uid)] ?: 0L
    suspend fun setLastStaleCheck(uid: String, at: Long) = context.dataStore.edit { it[Keys.lastStaleCheck(uid)] = at }

    /** True once this account's alert-area centre has been moved out of the shared areaFollows document. */
    suspend fun areaFollowPrivate(uid: String): Boolean = context.dataStore.data.first()[Keys.areaFollowPrivate(uid)] == true
    suspend fun setAreaFollowPrivate(uid: String) = context.dataStore.edit { it[Keys.areaFollowPrivate(uid)] = true }

    /** "" follows the system language; otherwise "en" or "ar". */
    fun language(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    fun setLanguage(tag: String) {
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
