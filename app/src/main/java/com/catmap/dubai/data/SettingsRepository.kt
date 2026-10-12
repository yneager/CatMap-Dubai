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
) {
    val extraCount: Int get() = animalTypes.size + (if (maxDistanceKm > 0) 1 else 0) + (if (maxAgeDays > 0) 1 else 0) + (if (rewardOnly) 1 else 0)
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
) {
    fun allows(category: NotifCategory) = notificationsEnabled && categories[category] != false
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val PRIVACY_LEVEL = stringPreferencesKey("privacy_level")
        val SHARE_EXACT = booleanPreferencesKey("share_exact")
        fun delivered(uid: String) = stringSetPreferencesKey("delivered_notifications_" + uid)
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
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
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
        ),
        skippedVersion = this[Keys.SKIPPED_VERSION] ?: "",
        lastUpdateCheck = this[Keys.LAST_UPDATE_CHECK] ?: 0L,
    )

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setLargeText(on: Boolean) = context.dataStore.edit { it[Keys.LARGE_TEXT] = on }
    suspend fun setNotificationsEnabled(on: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATIONS] = on }
    suspend fun setCategory(c: NotifCategory, on: Boolean) = context.dataStore.edit { it[Keys.category(c)] = on }
    suspend fun setPrivacyLevel(level: String) = context.dataStore.edit { it[Keys.PRIVACY_LEVEL] = level }
    suspend fun setShareExact(on: Boolean) = context.dataStore.edit { it[Keys.SHARE_EXACT] = on }
    suspend fun setMapMode(mode: MapMode) = context.dataStore.edit { it[Keys.MAP_MODE] = mode.name }
    suspend fun setSatellite(on: Boolean) = context.dataStore.edit { it[Keys.SATELLITE] = on }
    suspend fun setShowStations(on: Boolean) = context.dataStore.edit { it[Keys.STATIONS] = on }
    suspend fun setHeatmap(on: Boolean) = context.dataStore.edit { it[Keys.HEATMAP] = on }
    suspend fun setSkippedVersion(version: String) = context.dataStore.edit { it[Keys.SKIPPED_VERSION] = version }
    suspend fun setLastUpdateCheck(at: Long) = context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK] = at }
    suspend fun setFilters(f: FeedFilters) = context.dataStore.edit {
        it[Keys.LAYERS] = f.layers.map { l -> l.name }.toSet()
        it[Keys.ANIMALS] = f.animalTypes
        it[Keys.DISTANCE] = f.maxDistanceKm
        it[Keys.AGE] = f.maxAgeDays
        it[Keys.REWARD] = f.rewardOnly
    }

    // Null means this account has never established a baseline; empty means an empty baseline.
    suspend fun deliveredNotifications(uid: String): Set<String>? = context.dataStore.data.first()[Keys.delivered(uid)]
    suspend fun setDeliveredNotifications(uid: String, ids: Set<String>) = context.dataStore.edit { it[Keys.delivered(uid)] = ids }
    suspend fun clearDeliveredNotifications(uid: String) = context.dataStore.edit { it.remove(Keys.delivered(uid)) }

    /** "" follows the system language; otherwise "en" or "ar". */
    fun language(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    fun setLanguage(tag: String) {
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
