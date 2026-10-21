package com.catmap.dubai.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Firestore maps documents onto these classes by reflection, so every property is a `var`
// with a default value. Enumerations are stored as their string keys (see Enums.kt).

object Roles {
    const val USER = "user"
    const val VOLUNTEER = "volunteer"
    const val MODERATOR = "moderator"
    const val ADMIN = "admin"
}

object Stats {
    const val SIGHTINGS = "sightings"
    const val CATS_REGISTERED = "catsRegistered"
    const val APPROVED_SIGHTINGS = "approvedSightings"
    const val PHOTOS = "photos"
    const val RESCUE_REPORTS = "rescueReports"
    const val CHECKINS = "checkins"
    const val MAINTENANCE_REPORTS = "maintenanceReports"
    const val MERGE_SUGGESTIONS = "mergeSuggestions"
    const val TNR_REPORTS = "tnrReports"
    const val NIGHT_SIGHTINGS = "nightSightings"
    const val IFTAR_CHECKINS = "iftarCheckins"
    const val SOUQ_SIGHTINGS = "souqSightings"
    const val MARINA_SIGHTINGS = "marinaSightings"
    const val POSTS = "posts"
    const val REUNITED = "reunited"
    const val POST_UPDATES = "postUpdates"
}

data class UserProfile(
    @DocumentId var uid: String = "",
    var username: String = "",
    var displayName: String = "",
    var bio: String = "",
    var photoId: String = "",
    var role: String = Roles.USER,
    /** none, applied, approved, rejected */
    var volunteerStatus: String = "none",
    var stats: Map<String, Long> = emptyMap(),
    var points: Long = 0,
    var neighborhoods: List<String> = emptyList(),
    var badges: List<String> = emptyList(),
    var followedCats: List<String> = emptyList(),
    var showStatsPublicly: Boolean = true,
    /** Set by an admin; suspended accounts can read but not post. */
    var suspended: Boolean = false,
    var lastPostAt: Timestamp? = null,
    var createdAt: Long = 0,
)

data class Cat(
    @DocumentId var id: String = "",
    var name: String = "",
    var aliases: List<String> = emptyList(),
    var description: String = "",
    var distinctiveMarks: String = "",
    var coatColor: String = CoatColor.UNKNOWN.key,
    var pattern: String = CoatPattern.UNKNOWN.key,
    var size: String = CatSize.UNKNOWN.key,
    var sex: String = CatSex.UNKNOWN.key,
    var breedHints: List<String> = emptyList(),
    var photoIds: List<String> = emptyList(),
    var coverPhotoId: String = "",
    var createdBy: String = "",
    var createdByName: String = "",
    var contributors: List<String> = emptyList(),
    var followers: List<String> = emptyList(),
    var firstSeen: Long = 0,
    var lastSeen: Long = 0,
    var sightingCount: Long = 0,
    /** Last known position, already blurred for privacy. */
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var neighborhood: String = "",
    var neighborhoods: List<String> = emptyList(),
    var tnrStatus: String = TnrStatus.UNKNOWN.key,
    var tnrVerified: Boolean = false,
    var healthStatus: String = HealthStatus.UNKNOWN.key,
    var features: List<Double> = emptyList(),
    var mergedInto: String = "",
    var hidden: Boolean = false,
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
    /** Geohashes of [lat]/[lng] at precision 4, 5 and 6 ([com.catmap.dubai.data.Geohash.cells]), for map-area queries. */
    var geoCells: List<String> = emptyList(),
    /** Name and alias words and their first letters ([com.catmap.dubai.data.SearchTerms]), for search. */
    var searchTerms: List<String> = emptyList(),
)

data class Sighting(
    @DocumentId var id: String = "",
    var catId: String = "",
    var catName: String = "",
    var userId: String = "",
    var username: String = "",
    var photoIds: List<String> = emptyList(),
    var seenAt: Long = 0,
    /** Blurred public position. The exact one lives in privateLocations/{id}. */
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var neighborhood: String = "",
    var locationSource: String = "gps",
    var coatColor: String = CoatColor.UNKNOWN.key,
    var pattern: String = CoatPattern.UNKNOWN.key,
    var size: String = CatSize.UNKNOWN.key,
    var healthTags: List<String> = emptyList(),
    var behaviorTags: List<String> = emptyList(),
    var earTipped: String = EarTip.UNSURE.key,
    var possiblyNotNeutered: Boolean = false,
    var needsMedical: Boolean = false,
    var notes: String = "",
    var newCat: Boolean = false,
    var matchScore: Double = 0.0,
    var aiSummary: String = "",
    /** Cat profiles the matcher suggested, and the ones the person said were "not this cat". */
    var suggestedCatIds: List<String> = emptyList(),
    var rejectedCatIds: List<String> = emptyList(),
    /** pending, approved, rejected */
    var status: String = "pending",
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
)

data class PrivateLocation(
    @DocumentId var id: String = "",
    var ownerId: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var accuracy: Double = 0.0,
)

data class MedicalRecord(
    @DocumentId var id: String = "",
    var catId: String = "",
    var type: String = MedicalType.NOTE.key,
    var note: String = "",
    var byUid: String = "",
    var byName: String = "",
    var at: Long = 0,
)

data class RescueUpdate(
    var status: String = "",
    var note: String = "",
    var byUid: String = "",
    var byName: String = "",
    var at: Long = 0,
)

data class RescueReport(
    @DocumentId var id: String = "",
    var type: String = RescueType.INJURED.key,
    var urgency: String = Urgency.MEDIUM.key,
    var description: String = "",
    var landmark: String = "",
    var photoIds: List<String> = emptyList(),
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var neighborhood: String = "",
    var catId: String = "",
    var catName: String = "",
    var reporterId: String = "",
    var reporterName: String = "",
    var status: String = RescueStatus.SUBMITTED.key,
    var assignedTo: String = "",
    var assignedName: String = "",
    var updates: List<RescueUpdate> = emptyList(),
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
)

data class FeedingStation(
    @DocumentId var id: String = "",
    var name: String = "",
    var description: String = "",
    var operatorName: String = "",
    var schedule: String = "",
    var foodType: String = "",
    var hasWater: Boolean = true,
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var neighborhood: String = "",
    var verified: Boolean = true,
    var active: Boolean = true,
    var sample: Boolean = false,
    var checkinCount: Long = 0,
    var lastCheckinAt: Long = 0,
    var lastCheckinBy: String = "",
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
)

data class StationCheckin(
    @DocumentId var id: String = "",
    var stationId: String = "",
    var stationName: String = "",
    var userId: String = "",
    var username: String = "",
    var catsSeen: Long = 0,
    var catIds: List<String> = emptyList(),
    var foodLevel: String = SupplyLevel.UNKNOWN.key,
    var waterLevel: String = SupplyLevel.UNKNOWN.key,
    var note: String = "",
    var at: Long = 0,
)

data class MaintenanceReport(
    @DocumentId var id: String = "",
    var stationId: String = "",
    var stationName: String = "",
    var userId: String = "",
    var username: String = "",
    var issue: String = MaintenanceIssue.OTHER.key,
    var description: String = "",
    var status: String = MaintenanceStatus.OPEN.key,
    var resolutionNote: String = "",
    var resolvedBy: String = "",
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
)

data class MergeRequest(
    @DocumentId var id: String = "",
    var primaryCatId: String = "",
    var primaryName: String = "",
    var duplicateCatId: String = "",
    var duplicateName: String = "",
    var requestedBy: String = "",
    var requestedByName: String = "",
    var reason: String = "",
    var similarity: Double = 0.0,
    /** pending, approved, rejected */
    var status: String = "pending",
    var reviewedBy: String = "",
    var reviewedByName: String = "",
    var reviewNote: String = "",
    var createdAt: Long = 0,
    var reviewedAt: Long = 0,
)

data class ContentReport(
    @DocumentId var id: String = "",
    /** cat, sighting, photo, user, rescue */
    var targetType: String = "",
    var targetId: String = "",
    var targetLabel: String = "",
    var reason: String = ReportReason.OTHER.key,
    var details: String = "",
    var reporterId: String = "",
    var reporterName: String = "",
    /** open, resolved, dismissed */
    var status: String = "open",
    var resolution: String = "",
    var resolvedBy: String = "",
    var createdAt: Long = 0,
)

data class AppNotification(
    @DocumentId var id: String = "",
    var type: String = "",
    var params: Map<String, String> = emptyMap(),
    var route: String = "",
    var fromUid: String = "",
    var read: Boolean = false,
    var createdAt: Long = 0,
)

data class ModAction(
    @DocumentId var id: String = "",
    var moderatorId: String = "",
    var moderatorName: String = "",
    var action: String = "",
    var targetType: String = "",
    var targetId: String = "",
    var targetLabel: String = "",
    var note: String = "",
    var at: Long = 0,
)

data class VolunteerApplication(
    @DocumentId var id: String = "",
    var userId: String = "",
    var username: String = "",
    var motivation: String = "",
    var experience: String = "",
    var area: String = "",
    /** pending, approved, rejected */
    var status: String = "pending",
    var reviewedBy: String = "",
    var createdAt: Long = 0,
)
