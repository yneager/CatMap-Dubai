package com.catmap.dubai.ai

import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostStatus
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * CatMap's match rules: how a lost report and a sighting are scored and which strength label they get.
 *
 * The same rules run on the PC (`cat-server/matching.py`, with the AI model) and on the web (`src/ai/MatchEngine.ts`);
 * `tools/matching/cases.json` holds shared cases all three must pass, and `docs/MATCHING.md` explains them. The phone
 * uses them while the PC is off, with its colour fingerprint as the photo evidence, which is never enough for
 * "strong". Place and time only move a label one step, never rule a match out. Nothing here decides that two animals
 * are the same: a person always reviews the suggestion.
 */
object MatchEngine {
    const val SPEC_VERSION = 1
    const val MODEL_FINGERPRINT = "fingerprint27/v1"
    const val MODEL_DETAILS = "details/v1"

    val TIERS = listOf("strong", "possible", "weak", "none")

    /**
     * pet-recognition-base similarity thresholds (strong, possible, weak) per species, measured in cat-server/eval
     * (docs/MATCHING.md): strong = 0.1 % and possible = 5 % of different same-breed animals, weak = 1 % of other
     * breeds. Other animal types use the cat values.
     */
    val EMBED = mapOf("cat" to Triple(0.88, 0.79, 0.68), "dog" to Triple(0.89, 0.66, 0.27))
    // The colour fingerprint (PostAppearance.similarity), which can't tell two look-alikes apart: never "strong".
    const val PRINT_POSSIBLE = 0.90
    const val PRINT_WEAK = 0.83

    fun embedThresholds(animalType: String) = EMBED[animalType] ?: EMBED.getValue("cat")

    const val GPS_KM = 0.15
    const val BLUR_KM = 0.45
    const val ROAM_KM = 3.0
    const val ROAM_KM_PER_DAY = 1.0
    const val ROAM_MAX_KM = 25.0
    const val NEAR_KM = 1.5
    const val RECENT_DAYS = 14
    const val BEFORE_DAYS = 2
    const val PREFILTER_KM = 45.0
    private const val DAY_MS = 86_400_000.0

    val LOST = setOf(PostCategory.LOST.key)
    val SIGHTINGS = setOf(PostCategory.FOUND.key, PostCategory.SPOTTED.key, PostCategory.STREET.key)

    data class Side(
        val lat: Double,
        val lng: Double,
        val timeMs: Long,
        val approximate: Boolean = false,
        val areaM: Long = 0,
        val hasPhotos: Boolean = true,
        val colourWords: Set<String> = emptySet(),
    )

    data class Result(
        val tier: String,
        /** For ranking only (0..1); not a probability. */
        val score: Double,
        /** The raw photo similarity: cosine, or fingerprint 0..1; -1 when there is none. */
        val visual: Double,
        val method: String,
        val distanceKm: Double,
        /** The sighting's time minus the lost animal's last-seen time, in days. */
        val daysApart: Double,
        val reasons: List<String>,
    )

    fun down(tier: String): String = TIERS[min(TIERS.size - 1, TIERS.indexOf(tier) + 1)]

    /** One step down for place or time, which lower a photo label but never remove it: it stops at "weak". */
    fun contextDown(tier: String): String = if (tier == "weak" || tier == "none") tier else down(tier)

    fun visualTier(method: String, visual: Double, animalType: String = "cat"): String = when (method) {
        "embedding" -> embedThresholds(animalType).let { (strong, possible, weak) ->
            if (visual >= strong) "strong" else if (visual >= possible) "possible" else if (visual >= weak) "weak" else "none"
        }
        "fingerprint" -> if (visual >= PRINT_POSSIBLE) "possible" else if (visual >= PRINT_WEAK) "weak" else "none"
        else -> "none"
    }

    /** The photo similarity on a common 0..1 scale (0.25 at "weak", 0.5 at "possible", 0.8 at "strong"). */
    fun visualNorm(method: String, visual: Double, animalType: String = "cat"): Double {
        val points = when (method) {
            "embedding" -> embedThresholds(animalType).let { (strong, possible, weak) -> listOf(weak - 0.15 to 0.0, weak to 0.25, possible to 0.5, strong to 0.8, 1.0 to 1.0) }
            "fingerprint" -> listOf(PRINT_WEAK - 0.15 to 0.0, PRINT_WEAK to 0.25, PRINT_POSSIBLE to 0.5, 1.0 to 0.7)
            else -> return 0.0
        }
        if (visual <= points[0].first) return 0.0
        for ((a, b) in points.zipWithNext()) if (visual <= b.first) return a.second + (b.second - a.second) * (visual - a.first) / (b.first - a.first)
        return points.last().second
    }

    fun score(lost: Side, sighting: Side, method: String, visual: Double = -1.0, animalType: String = "cat"): Result {
        val d = distanceKm(lost.lat, lost.lng, sighting.lat, sighting.lng)
        val uncertainty = 2 * GPS_KM + (if (lost.approximate) BLUR_KM else 0.0) + (if (sighting.approximate) BLUR_KM else 0.0) + lost.areaM / 1000.0
        val effective = max(0.0, d - uncertainty)
        val days = (sighting.timeMs - lost.timeMs) / DAY_MS
        val roam = min(ROAM_MAX_KM, ROAM_KM + ROAM_KM_PER_DAY * max(0.0, days))
        val far = effective > roam
        val near = effective <= NEAR_KM
        val before = days < -BEFORE_DAYS
        val recent = days >= -BEFORE_DAYS && days <= RECENT_DAYS
        val reasons = mutableListOf<String>()
        var m = method
        var tier: String
        if (m == "details" || visual < 0) {
            m = "details"
            val shared = lost.colourWords.any { it in sighting.colourWords }
            tier = if (near && recent && shared) "weak" else "none"
            reasons += "no_photo"
            if (shared) reasons += "same_colour_words"
        } else {
            tier = visualTier(m, visual, animalType)
            reasons += when (tier) { "strong" -> "photo_very_similar"; "possible" -> "photo_similar"; "weak" -> "photo_somewhat_similar"; else -> "photo_different" }
            if (m == "fingerprint") {
                reasons += "colour_only"
                if (!near) tier = contextDown(tier)
            }
            if (far) tier = contextDown(tier)
            if (before) tier = contextDown(tier)
        }
        reasons += if (near) "near" else if (far) "far" else "in_range"
        reasons += if (before) "before_lost" else if (recent) "recent" else "long_after"
        val proximity = exp(-effective / max(roam, 1e-9))
        val timeFit = if (before) 0.3 else exp(-max(0.0, days) / 30)
        val rank = 0.85 * visualNorm(m, visual, animalType) + 0.15 * (0.5 * proximity + 0.5 * timeFit)
        return Result(tier, round(rank, 4), round(visual, 4), m, round(d, 3), round(days, 2), reasons)
    }

    fun applyFeedback(result: Result, likeRejected: Boolean, reporterRejected: Boolean): Result {
        var r = result
        if (likeRejected) r = r.copy(tier = down(r.tier), reasons = r.reasons + "like_rejected")
        if (reporterRejected) r = r.copy(tier = down(r.tier), reasons = r.reasons + "reporter_rejected")
        return r
    }

    private fun round(v: Double, places: Int): Double {
        val f = Math.pow(10.0, places.toDouble())
        return Math.round(v * f) / f
    }

    // ---------- posts ----------

    fun postTime(p: Post): Long = p.lastSeenAt.takeIf { it > 0 } ?: p.createdAt

    fun colourWords(p: Post): Set<String> =
        "${p.color} ${p.features}".lowercase(Locale.ROOT).replace(",", " ").split(Regex("\\s+")).filter { it.length >= 3 }.toSet()

    fun side(p: Post) = Side(p.lat, p.lng, postTime(p), p.approximate, if (p.category in LOST) p.areaRadiusM else 0, p.photoIds.isNotEmpty(), colourWords(p))

    fun isLost(p: Post) = p.category in LOST
    fun isSighting(p: Post) = p.category in SIGHTINGS

    /** The deterministic id of a pair, so it can only be suggested once. */
    fun matchId(lostId: String, sightingId: String) = "${lostId}__$sightingId"

    /** Open posts on the other side of [post] (sightings for a lost report, lost reports for a sighting) worth scoring. */
    fun candidates(post: Post, feed: List<Post>): List<Post> {
        val other = if (isLost(post)) SIGHTINGS else if (isSighting(post)) LOST else return emptyList()
        return feed.filter {
            it.id != post.id && it.category in other && it.status == PostStatus.OPEN.key && !it.hidden &&
                it.animalType == post.animalType && it.ownerId != post.ownerId &&
                distanceKm(post.lat, post.lng, it.lat, it.lng) <= PREFILTER_KM
        }
    }

    data class Pairing(val lost: Post, val sighting: Post, val result: Result) {
        val id get() = matchId(lost.id, sighting.id)
    }

    /** The phone's own matching (PC offline): colour fingerprints when both posts have one, otherwise the details. */
    fun deviceMatches(post: Post, feed: List<Post>): List<Pairing> = candidates(post, feed).map { c ->
        val (lost, sighting) = if (isLost(post)) post to c else c to post
        val result = if (PostAppearance.valid(lost.fingerprint) && PostAppearance.valid(sighting.fingerprint)) {
            score(side(lost), side(sighting), "fingerprint", PostAppearance.similarity(lost.fingerprint, sighting.fingerprint), lost.animalType)
        } else {
            score(side(lost), side(sighting), "details", animalType = lost.animalType)
        }
        Pairing(lost, sighting, result)
    }.sortedByDescending { it.result.score }
}
