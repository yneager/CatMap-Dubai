package com.catmap.dubai.gamification

import androidx.annotation.StringRes
import com.catmap.dubai.R
import com.catmap.dubai.data.model.Stats
import com.catmap.dubai.data.model.UserProfile

/** Points awarded per contribution; they drive the level shown on the profile. */
object Points {
    const val SIGHTING = 10L
    const val NEW_CAT = 15L
    const val PHOTO = 2L
    const val RESCUE = 20L
    const val CHECKIN = 5L
    const val MAINTENANCE = 5L
    const val MERGE = 5L
    const val APPROVED = 5L
}

private fun UserProfile.stat(key: String) = stats[key] ?: 0L

enum class Badge(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @androidx.annotation.DrawableRes val icon: Int,
    val threshold: Long,
    val progress: (UserProfile) -> Long,
) {
    FIRST_WHISKERS(R.string.badge_first_whiskers, R.string.badge_first_whiskers_desc, R.drawable.ic_cm_badge_paw, 1, { it.stat(Stats.SIGHTINGS) }),
    SOUQ_RESIDENT(R.string.badge_souq_resident, R.string.badge_souq_resident_desc, R.drawable.ic_cm_badge_souq, 5, { it.stat(Stats.SOUQ_SIGHTINGS) }),
    ABRA_RIDER(R.string.badge_abra_rider, R.string.badge_abra_rider_desc, R.drawable.ic_cm_badge_abra, 3, { it.neighborhoods.size.toLong() }),
    MARINA_WATCHER(R.string.badge_marina_watcher, R.string.badge_marina_watcher_desc, R.drawable.ic_cm_badge_boat, 3, { it.stat(Stats.MARINA_SIGHTINGS) }),
    MAJLIS_HOST(R.string.badge_majlis_host, R.string.badge_majlis_host_desc, R.drawable.ic_cm_badge_cup, 5, { it.stat(Stats.CATS_REGISTERED) }),
    EHSAN_HERO(R.string.badge_ehsan_hero, R.string.badge_ehsan_hero_desc, R.drawable.ic_cm_badge_hands, 10, { it.stat(Stats.CHECKINS) }),
    IFTAR_FEEDER(R.string.badge_iftar_feeder, R.string.badge_iftar_feeder_desc, R.drawable.ic_cm_badge_moon, 1, { it.stat(Stats.IFTAR_CHECKINS) }),
    NIGHT_PATROL(R.string.badge_night_patrol, R.string.badge_night_patrol_desc, R.drawable.ic_cm_badge_night, 5, { it.stat(Stats.NIGHT_SIGHTINGS) }),
    GHAF_GUARDIAN(R.string.badge_ghaf_guardian, R.string.badge_ghaf_guardian_desc, R.drawable.ic_cm_badge_tree, 3, { it.stat(Stats.RESCUE_REPORTS) }),
    FALAJ_KEEPER(R.string.badge_falaj_keeper, R.string.badge_falaj_keeper_desc, R.drawable.ic_cm_badge_water, 3, { it.stat(Stats.MAINTENANCE_REPORTS) }),
    TNR_CHAMPION(R.string.badge_tnr_champion, R.string.badge_tnr_champion_desc, R.drawable.ic_cm_badge_scissors, 5, { it.stat(Stats.TNR_REPORTS) }),
    PEARL_DIVER(R.string.badge_pearl_diver, R.string.badge_pearl_diver_desc, R.drawable.ic_cm_badge_pearl, 10, { it.stat(Stats.APPROVED_SIGHTINGS) }),
    PALM_PHOTOGRAPHER(R.string.badge_palm_photographer, R.string.badge_palm_photographer_desc, R.drawable.ic_cm_badge_camera, 20, { it.stat(Stats.PHOTOS) }),
    MERGE_DETECTIVE(R.string.badge_merge_detective, R.string.badge_merge_detective_desc, R.drawable.ic_cm_badge_lost, 3, { it.stat(Stats.MERGE_SUGGESTIONS) }),
    HEART_OF_DUBAI(R.string.badge_heart_of_dubai, R.string.badge_heart_of_dubai_desc, R.drawable.ic_cm_badge_adoption, 5, { it.followedCats.size.toLong() }),
    FALCON_EYE(R.string.badge_falcon_eye, R.string.badge_falcon_eye_desc, R.drawable.ic_cm_badge_falcon, 25, { it.stat(Stats.SIGHTINGS) }),
    BURJ_EXPLORER(R.string.badge_burj_explorer, R.string.badge_burj_explorer_desc, R.drawable.ic_cm_badge_street, 10, { it.neighborhoods.size.toLong() }),
    CARAVAN_LEADER(R.string.badge_caravan_leader, R.string.badge_caravan_leader_desc, R.drawable.ic_cm_badge_camel, 100, { it.stat(Stats.SIGHTINGS) });

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key }

        fun newlyEarned(profile: UserProfile): List<Badge> =
            entries.filter { it.key !in profile.badges && it.progress(profile) >= it.threshold }
    }
}

data class Level(val number: Int, @StringRes val title: Int, val minPoints: Long)

object Levels {
    val all = listOf(
        Level(1, R.string.level_1, 0),
        Level(2, R.string.level_2, 100),
        Level(3, R.string.level_3, 300),
        Level(4, R.string.level_4, 700),
        Level(5, R.string.level_5, 1500),
        Level(6, R.string.level_6, 3000),
    )

    fun of(points: Long): Level = all.last { points >= it.minPoints }
    fun next(points: Long): Level? = all.firstOrNull { it.minPoints > points }

    /** Progress from the current level to the next one, 0..1. */
    fun progress(points: Long): Float {
        val current = of(points)
        val next = next(points) ?: return 1f
        return (points - current.minPoints).toFloat() / (next.minPoints - current.minPoints)
    }
}
