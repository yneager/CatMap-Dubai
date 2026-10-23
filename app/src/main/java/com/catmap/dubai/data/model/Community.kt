package com.catmap.dubai.data.model

import androidx.annotation.StringRes
import com.catmap.dubai.R
import com.google.firebase.firestore.DocumentId

// Community features added in 2.0: animal posts (lost, found, adoption…), pet sitting, chat.
// Same conventions as Models.kt: every property is a `var` with a default, enums are stored by key.

enum class AnimalType(@StringRes override val label: Int, @androidx.annotation.DrawableRes val icon: Int) : Labeled {
    CAT(R.string.animal_cat, R.drawable.ic_cm_cat),
    DOG(R.string.animal_dog, R.drawable.ic_cm_dog),
    BIRD(R.string.animal_bird, R.drawable.ic_cm_bird),
    RABBIT(R.string.animal_rabbit, R.drawable.ic_cm_rabbit),
    OTHER(R.string.animal_other, R.drawable.ic_cm_paw);

    override val key: String get() = name.lowercase()
}

enum class PostCategory(@StringRes override val label: Int, @StringRes val headline: Int, val color: Long) : Labeled {
    LOST(R.string.cat_lost, R.string.headline_lost, 0xFFE53935),
    FOUND(R.string.cat_found, R.string.headline_found, 0xFF43A047),
    SPOTTED(R.string.cat_spotted, R.string.headline_spotted, 0xFF1E88E5),
    NEEDS_HELP(R.string.cat_needs_help, R.string.headline_needs_help, 0xFFFB8C00),
    STREET(R.string.cat_street, R.string.headline_street, 0xFF8D6E63),
    ADOPTION(R.string.cat_adoption, R.string.headline_adoption, 0xFF8E24AA),
    OTHER(R.string.cat_other, R.string.headline_other, 0xFF546E7A);

    override val key: String get() = name.lowercase()

    /** Lost, found and spotted posts are about finding an animal; they carry "last seen" details. */
    val isSearch: Boolean get() = this == LOST || this == FOUND || this == SPOTTED
}

enum class PostStatus(@StringRes override val label: Int, val color: Long) : Labeled {
    OPEN(R.string.pstatus_open, 0xFF1E88E5),
    REUNITED(R.string.pstatus_reunited, 0xFF2E7D32),
    RESOLVED(R.string.pstatus_resolved, 0xFF546E7A);

    override val key: String get() = name.lowercase()
}

enum class AdoptionStatus(@StringRes override val label: Int, val color: Long) : Labeled {
    AVAILABLE(R.string.astatus_available, 0xFF2E7D32),
    PENDING(R.string.astatus_pending, 0xFFF9A825),
    ADOPTED(R.string.astatus_adopted, 0xFF8E24AA);

    override val key: String get() = name.lowercase()
}

enum class AgeGroup(@StringRes override val label: Int) : Labeled {
    BABY(R.string.age_baby),
    YOUNG(R.string.age_young),
    ADULT(R.string.age_adult),
    SENIOR(R.string.age_senior),
    UNKNOWN(R.string.age_unknown);

    override val key: String get() = name.lowercase()
}

enum class ContactMethod(@StringRes override val label: Int) : Labeled {
    IN_APP(R.string.contact_in_app),
    PHONE(R.string.contact_phone),
    EMAIL(R.string.contact_email);

    override val key: String get() = name.lowercase()
}

enum class UpdateKind(@StringRes override val label: Int) : Labeled {
    SIGHTING(R.string.ukind_sighting),
    PROGRESS(R.string.ukind_progress),
    FOUND_IT(R.string.ukind_found_it);

    override val key: String get() = name.lowercase()
}

enum class RequestStatus(@StringRes override val label: Int, val color: Long) : Labeled {
    PENDING(R.string.rqstatus_pending, 0xFFF9A825),
    APPROVED(R.string.rqstatus_approved, 0xFF2E7D32),
    DECLINED(R.string.rqstatus_declined, 0xFFC62828),
    WITHDRAWN(R.string.rqstatus_withdrawn, 0xFF757575);

    override val key: String get() = name.lowercase()
}

enum class BookingStatus(@StringRes override val label: Int, val color: Long) : Labeled {
    REQUESTED(R.string.bstatus_requested, 0xFFF9A825),
    ACCEPTED(R.string.bstatus_accepted, 0xFF1E88E5),
    DECLINED(R.string.bstatus_declined, 0xFFC62828),
    CANCELLED(R.string.bstatus_cancelled, 0xFF757575),
    COMPLETED(R.string.bstatus_completed, 0xFF2E7D32);

    override val key: String get() = name.lowercase()
}

enum class SitterService(@StringRes override val label: Int) : Labeled {
    HOME_VISITS(R.string.service_home_visits),
    BOARDING(R.string.service_boarding),
    OVERNIGHT(R.string.service_overnight),
    WALKING(R.string.service_walking),
    MEDICATION(R.string.service_medication);

    override val key: String get() = name.lowercase()
}

enum class RateUnit(@StringRes override val label: Int) : Labeled {
    VISIT(R.string.rate_per_visit),
    HOUR(R.string.rate_per_hour),
    DAY(R.string.rate_per_day),
    NIGHT(R.string.rate_per_night);

    override val key: String get() = name.lowercase()
}

/** A report about any animal: lost, found, spotted, needs help, street animal, adoption or other. */
data class Post(
    @DocumentId var id: String = "",
    var ownerId: String = "",
    var ownerName: String = "",
    var ownerPhotoId: String = "",
    var animalType: String = AnimalType.CAT.key,
    var category: String = PostCategory.SPOTTED.key,
    var status: String = PostStatus.OPEN.key,
    var name: String = "",
    var description: String = "",
    var color: String = "",
    var breed: String = "",
    var features: String = "",
    var sex: String = CatSex.UNKNOWN.key,
    var ageGroup: String = AgeGroup.UNKNOWN.key,
    var photoIds: List<String> = emptyList(),
    /** Storage path of an optional short video ("" when there is none). */
    var videoPath: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    /** True when [lat]/[lng] were blurred before saving. */
    var approximate: Boolean = false,
    /** Optional search area around the pin, in metres (0 = just the pin). */
    var areaRadiusM: Long = 0,
    var neighborhood: String = "",
    var lastSeenAt: Long = 0,
    /** Reward in AED (lost posts only); 0 means none. */
    var reward: Long = 0,
    var contactMethod: String = ContactMethod.IN_APP.key,
    /** Only filled when the owner explicitly chose to show it on this post. */
    var contactPhone: String = "",
    var contactEmail: String = "",
    // Adoption listings
    var adoptionStatus: String = AdoptionStatus.AVAILABLE.key,
    var health: String = "",
    var vaccinated: Boolean = false,
    var neutered: Boolean = false,
    var updatesCount: Long = 0,
    var counterSourceId: String = "",
    var hidden: Boolean = false,
    /** Lower-case words used by search. */
    var keywords: List<String> = emptyList(),
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var resolvedAt: Long = 0,
    var lastActivityAt: Long = 0,
    var fingerprint: List<Double> = emptyList(),
    var microchip: String = "unknown",
    /** How a closed lost/found report ended: [Resolution] key, "" while open or for older posts. */
    var resolution: String = "",
    /** The match (matches/{id}) the owner credited for the reunion, "" when none. */
    var resolvedMatchId: String = "",
    /** Geohashes of [lat]/[lng] at precision 4, 5 and 6 ([com.catmap.dubai.data.Geohash.cells]), for map-area queries. */
    var geoCells: List<String> = emptyList(),
    /** [keywords] and their first letters ([com.catmap.dubai.data.SearchTerms]), for searching every post. */
    var searchTerms: List<String> = emptyList(),
)

/** How a lost or found report was closed. The owner says so; a moderator can later mark the match as checked. */
enum class Resolution(@StringRes override val label: Int) : Labeled {
    CATMAP(R.string.resolution_catmap),
    HOME(R.string.resolution_home),
    OTHER(R.string.resolution_other);

    override val key: String get() = name.lowercase()
}

/** Strength of a match suggestion. Never a percentage: the scores aren't calibrated probabilities. */
enum class MatchTier(@StringRes override val label: Int, val color: Long) : Labeled {
    STRONG(R.string.tier_strong, 0xFF2E7D32),
    POSSIBLE(R.string.tier_possible, 0xFFE68A00),
    WEAK(R.string.tier_weak, 0xFF757575),
    NONE(R.string.tier_none, 0xFF9E9E9E);

    override val key: String get() = name.lowercase()
}

/**
 * A suggested connection between a lost report and a sighting (matches/{lostPostId}__{sightingPostId}), seen only
 * by the two people involved. The AI worker or a phone suggests it; only people answer it (see docs/MATCHING.md).
 */
data class PostMatch(
    @DocumentId var id: String = "",
    var lostPostId: String = "",
    var sightingPostId: String = "",
    var lostOwnerId: String = "",
    var sightingOwnerId: String = "",
    var members: List<String> = emptyList(),
    var animalType: String = "",
    var score: Double = 0.0,
    var visual: Double = -1.0,
    var tier: String = MatchTier.NONE.key,
    /** embedding (AI model on the PC), fingerprint (colour, on a phone) or details (no photos). */
    var method: String = "details",
    var model: String = "",
    var distanceKm: Double = 0.0,
    var daysApart: Double = 0.0,
    var reasons: List<String> = emptyList(),
    var source: String = "device",
    /** The owner's answer: new, maybe, not_mine, need_info or reunited. */
    var ownerState: String = "new",
    /** The reporter's answer: new, confirmed or not_same. */
    var reporterState: String = "new",
    var createdBy: String = "",
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    var reviewedAt: Long = 0,
    var notified: Boolean = false,
    var verifiedBy: String = "",
    var verifiedAt: Long = 0,
) {
    val tierEnum: MatchTier get() = labeledFrom(tier, MatchTier.NONE)
    val rejected: Boolean get() = ownerState == "not_mine"
    /** Worth showing: a real suggestion the owner hasn't turned down. */
    val visible: Boolean get() = tier != MatchTier.NONE.key || ownerState in setOf("maybe", "need_info", "reunited") || reporterState == "confirmed"
}

/** A sighting or progress note added to a post by anyone (posts/{id}/updates). */
data class PostUpdate(
    @DocumentId var id: String = "",
    var postId: String = "",
    var kind: String = UpdateKind.SIGHTING.key,
    var text: String = "",
    var photoId: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var neighborhood: String = "",
    var byUid: String = "",
    var byName: String = "",
    var createdAt: Long = 0,
)

/** Document id is "{postId}_{requesterId}", so each person can ask once per animal. */
data class AdoptionRequest(
    @DocumentId var id: String = "",
    var postId: String = "",
    var postName: String = "",
    var postPhotoId: String = "",
    var ownerId: String = "",
    var requesterId: String = "",
    var requesterName: String = "",
    var message: String = "",
    var home: String = "",
    var status: String = RequestStatus.PENDING.key,
    var ownerNote: String = "",
    var createdAt: Long = 0,
    var decidedAt: Long = 0,
)

/** Pet-sitter profile; the document id is the sitter's uid. */
data class SitterProfile(
    @DocumentId var uid: String = "",
    var name: String = "",
    var photoId: String = "",
    var headline: String = "",
    var description: String = "",
    var experience: String = "",
    var petTypes: List<String> = emptyList(),
    var services: List<String> = emptyList(),
    var availability: String = "",
    var rate: Long = 0,
    var rateUnit: String = RateUnit.VISIT.key,
    /** Approximate area only; sitters never publish their exact address. */
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var neighborhood: String = "",
    var active: Boolean = true,
    var ratingSum: Long = 0,
    var verified: Boolean = false,
    var ratingCount: Long = 0,
    var lastReviewId: String = "",
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
) {
    val rating: Double get() = if (ratingCount == 0L) 0.0 else ratingSum.toDouble() / ratingCount
}

data class Booking(
    @DocumentId var id: String = "",
    var ownerId: String = "",
    var ownerName: String = "",
    var sitterId: String = "",
    var sitterName: String = "",
    var petTypes: List<String> = emptyList(),
    var petCount: Long = 1,
    var startAt: Long = 0,
    var endAt: Long = 0,
    var notes: String = "",
    var sitterNote: String = "",
    var status: String = BookingStatus.REQUESTED.key,
    var reviewed: Boolean = false,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
)

/** Review of a completed booking; the document id is the booking id (one review per booking). */
data class Review(
    @DocumentId var id: String = "",
    var sitterId: String = "",
    var authorId: String = "",
    var authorName: String = "",
    var rating: Long = 5,
    var text: String = "",
    var createdAt: Long = 0,
)

/** Private conversation between two people; the id is the two uids sorted and joined with "_". */
data class Chat(
    @DocumentId var id: String = "",
    var members: List<String> = emptyList(),
    var names: Map<String, String> = emptyMap(),
    var photos: Map<String, String> = emptyMap(),
    var lastText: String = "",
    var lastSender: String = "",
    var lastMessageId: String = "",
    var lastDeletedMessageId: String = "",
    var lastAt: Long = 0,
    var unread: Map<String, Long> = emptyMap(),
) {
    fun other(me: String) = members.firstOrNull { it != me }.orEmpty()
}

data class Message(
    @DocumentId var id: String = "",
    var senderId: String = "",
    var text: String = "",
    var imageId: String = "",
    /** A shared post shows as a tappable card. */
    var postId: String = "",
    var postTitle: String = "",
    var postPhotoId: String = "",
    /** A possible match shows as a card with both photos (the lost animal's is [postPhotoId]). */
    var matchId: String = "",
    var matchPhotoId: String = "",
    var createdAt: Long = 0,
)

/** Areas a person follows for "lost animal nearby" alerts; the document id is their uid. */
data class AreaFollow(
    @DocumentId var uid: String = "",
    var neighborhoods: List<String> = emptyList(),
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var radiusKm: Double = 0.0,
    var updatedAt: Long = 0,
)

data class Favorite(
    @DocumentId var id: String = "",
    var postId: String = "",
    var savedAt: Long = 0,
)
