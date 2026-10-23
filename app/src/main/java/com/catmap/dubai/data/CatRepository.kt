package com.catmap.dubai.data

import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.EarTip
import com.catmap.dubai.data.model.HealthStatus
import com.catmap.dubai.data.model.HealthTag
import com.catmap.dubai.data.model.MedicalRecord
import com.catmap.dubai.data.model.MedicalType
import com.catmap.dubai.data.model.MergeRequest
import com.catmap.dubai.data.model.PrivateLocation
import com.catmap.dubai.data.model.Sighting
import com.catmap.dubai.data.model.Stats
import com.catmap.dubai.data.model.TnrStatus
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.gamification.Points
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/** Everything the sighting form collects. */
data class SightingDraft(
    val existingCat: Cat?,
    val newCatName: String,
    val description: String,
    val distinctiveMarks: String,
    val sex: String,
    val coatColor: String,
    val pattern: String,
    val size: String,
    val breedHints: List<String>,
    val photoIds: List<String>,
    val features: List<Double>,
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val locationSource: String,
    val seenAt: Long,
    val healthTags: List<String>,
    val behaviorTags: List<String>,
    val earTip: String,
    val possiblyNotNeutered: Boolean,
    val needsMedical: Boolean,
    val notes: String,
    val matchScore: Double,
    val aiSummary: String,
    val suggestedCatIds: List<String> = emptyList(),
    val rejectedCatIds: List<String> = emptyList(),
)

class CatRepository(
    private val db: FirebaseFirestore,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val moderation: ModerationRepository,
) {
    private val cats = db.collection(Col.CATS)
    private val sightings = db.collection(Col.SIGHTINGS)

    fun catRef(id: String) = cats.document(id)

    fun catFlow(id: String): Flow<Cat?> = catRef(id).objectFlow()

    suspend fun cat(id: String): Cat? = catRef(id).get().await().toObject(Cat::class.java)

    /** Cats by id (followed cats, the merge queue), read once; merged and hidden ones are left out. */
    fun catsByIds(ids: List<String>): Flow<List<Cat>> = kotlinx.coroutines.flow.flow { emit(byIds(ids)) }

    suspend fun byIds(ids: List<String>): List<Cat> = coroutineScope {
        ids.distinct().chunked(30).map { chunk ->
            async { cats.whereIn(FieldPath.documentId(), chunk).get().await().safeObjects<Cat>() }
        }.awaitAll().flatten().filter { !it.hidden && it.mergedInto.isEmpty() }.sortedBy { ids.indexOf(it.id) }
    }

    /** Cats last seen within [radiusKm], through the map-area cells (Phase 4) instead of every profile. */
    suspend fun near(lat: Double, lng: Double, radiusKm: Double, perCell: Long = 150): List<Cat> = coroutineScope {
        Geohash.around(lat, lng, radiusKm).map { cell ->
            async {
                cats.whereArrayContains("geoCells", cell).orderBy("updatedAt", Query.Direction.DESCENDING).limit(perCell)
                    .get().await().safeObjects<Cat>()
            }
        }.awaitAll().flatten().distinctBy { it.id }
            .filter { !it.hidden && it.mergedInto.isEmpty() && distanceKm(lat, lng, it.lat, it.lng) <= radiusKm }
    }

    /** The cats directory, most recently seen first (or by [field]); Cats pages through this. */
    fun directoryQuery(field: String = "lastSeen", descending: Boolean = true): Query =
        cats.orderBy(field, if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING)

    /** Cats ever seen in one neighbourhood, most recently seen first. */
    fun neighborhoodQuery(key: String): Query =
        cats.whereArrayContains("neighborhoods", key).orderBy("lastSeen", Query.Direction.DESCENDING)

    /** Cats whose name or aliases include [word] (or its first letters), most recently seen first. */
    fun searchQuery(word: String): Query =
        cats.whereArrayContainsAny("searchTerms", SearchTerms.query(word)).orderBy("lastSeen", Query.Direction.DESCENDING)

    fun catsCreatedBy(uid: String): Flow<List<Cat>> =
        cats.whereEqualTo("createdBy", uid).listFlow<Cat>().map { l -> l.sortedByDescending { it.lastSeen } }

    fun catsContributedBy(uid: String): Flow<List<Cat>> =
        cats.whereArrayContains("contributors", uid).listFlow<Cat>()
            .map { l -> l.filter { it.mergedInto.isEmpty() }.sortedByDescending { it.lastSeen } }

    /** New profiles for moderator review; duplicates already merged into another cat are left out. */
    fun recentCats(sinceMillis: Long): Flow<List<Cat>> =
        cats.whereGreaterThan("createdAt", sinceMillis).listFlow<Cat>()
            .map { l -> l.filter { it.mergedInto.isEmpty() }.sortedByDescending { it.createdAt } }

    fun sightingsForCat(catId: String): Flow<List<Sighting>> =
        sightings.whereEqualTo("catId", catId).listFlow<Sighting>()
            .map { l -> l.filter { it.status != "rejected" }.sortedByDescending { it.seenAt } }

    fun sightingsForUser(uid: String): Flow<List<Sighting>> =
        sightings.whereEqualTo("userId", uid).listFlow<Sighting>().map { l -> l.sortedByDescending { it.seenAt } }

    fun recentSightings(limit: Long = 600): Flow<List<Sighting>> =
        sightings.orderBy("seenAt", Query.Direction.DESCENDING).limit(limit).listFlow<Sighting>()
            .map { l -> l.filter { it.status != "rejected" } }

    fun pendingSightings(): Flow<List<Sighting>> =
        sightings.whereEqualTo("status", "pending").listFlow<Sighting>().map { l -> l.sortedByDescending { it.createdAt } }

    fun sightingFlow(id: String): Flow<Sighting?> = sightings.document(id).objectFlow()

    suspend fun privateLocation(sightingId: String): PrivateLocation? =
        runCatching { db.collection(Col.PRIVATE_LOCATIONS).document(sightingId).get().await().toObject(PrivateLocation::class.java) }
            .getOrNull()

    /** Saves a sighting (and a new cat if needed). Returns the cat id. */
    suspend fun submitSighting(draft: SightingDraft, me: UserProfile, privacyLevel: String, shareExact: Boolean): String {
        MediaLease.retain(db, draft.photoIds)
        val created = now()
        val sightingRef = sightings.document()
        val (publicLat, publicLng) = LocationPrivacy.fuzz(draft.lat, draft.lng, privacyLevel, sightingRef.id)
        val hood = Dubai.nearest(draft.lat, draft.lng).key
        val existing = draft.existingCat
        val catRef = if (existing != null) catRef(existing.id) else cats.document()
        val catName = existing?.name ?: draft.newCatName.trim()
        val concerning = draft.needsMedical || draft.healthTags.any { labeledFrom(it, HealthTag.THIN).concerning }

        val batch = db.batch()
        if (existing == null) {
            batch.set(
                catRef,
                Cat(
                    name = catName,
                    description = draft.description.trim(),
                    distinctiveMarks = draft.distinctiveMarks.trim(),
                    coatColor = draft.coatColor,
                    pattern = draft.pattern,
                    size = draft.size,
                    sex = draft.sex,
                    breedHints = draft.breedHints,
                    photoIds = draft.photoIds,
                    coverPhotoId = draft.photoIds.firstOrNull() ?: "",
                    createdBy = me.uid,
                    createdByName = me.username,
                    contributors = listOf(me.uid),
                    followers = listOf(me.uid),
                    firstSeen = draft.seenAt,
                    lastSeen = draft.seenAt,
                    sightingCount = 1,
                    counterSourceId = sightingRef.id,
                    lat = publicLat,
                    lng = publicLng,
                    neighborhood = hood,
                    neighborhoods = listOf(hood),
                    tnrStatus = tnrFrom(draft, TnrStatus.UNKNOWN.key, false),
                    healthStatus = healthFrom(draft, concerning),
                    features = draft.features,
                    createdAt = created,
                    updatedAt = created,
                    geoCells = Geohash.cells(publicLat, publicLng),
                    searchTerms = searchTerms(catName, emptyList()),
                ),
            )
        } else {
            val updates = mutableMapOf<String, Any>(
                "sightingCount" to FieldValue.increment(1),
                "counterSourceId" to sightingRef.id,
                "contributors" to FieldValue.arrayUnion(me.uid),
                "neighborhoods" to FieldValue.arrayUnion(hood),
                "updatedAt" to created,
                "tnrStatus" to tnrFrom(draft, existing.tnrStatus, existing.tnrVerified),
            )
            // A sighting without health notes says nothing new, so it keeps the current status.
            if (draft.healthTags.isNotEmpty() || draft.needsMedical) updates["healthStatus"] = healthFrom(draft, concerning)
            if (draft.photoIds.isNotEmpty()) {
                updates["photoIds"] = FieldValue.arrayUnion(*draft.photoIds.toTypedArray())
                if (existing.coverPhotoId.isBlank()) updates["coverPhotoId"] = draft.photoIds.first()
            }
            if (draft.seenAt >= existing.lastSeen) {
                updates["lastSeen"] = draft.seenAt
                updates["lat"] = publicLat
                updates["lng"] = publicLng
                updates["neighborhood"] = hood
                updates["geoCells"] = Geohash.cells(publicLat, publicLng)
            }
            if (draft.features.isNotEmpty()) updates["features"] = blendFeatures(existing, draft.features)
            batch.update(catRef, updates)
        }

        batch.set(
            sightingRef,
            Sighting(
                catId = catRef.id,
                catName = catName,
                userId = me.uid,
                username = me.username,
                photoIds = draft.photoIds,
                seenAt = draft.seenAt,
                lat = publicLat,
                lng = publicLng,
                neighborhood = hood,
                locationSource = draft.locationSource,
                coatColor = draft.coatColor,
                pattern = draft.pattern,
                size = draft.size,
                healthTags = draft.healthTags,
                behaviorTags = draft.behaviorTags,
                earTipped = draft.earTip,
                possiblyNotNeutered = draft.possiblyNotNeutered,
                needsMedical = draft.needsMedical,
                notes = draft.notes.trim(),
                newCat = existing == null,
                matchScore = draft.matchScore,
                aiSummary = draft.aiSummary,
                suggestedCatIds = draft.suggestedCatIds.take(6),
                rejectedCatIds = draft.rejectedCatIds.take(6),
                status = "pending",
                createdAt = created,
                updatedAt = created,
            ),
        )
        if (shareExact) {
            batch.set(
                db.collection(Col.PRIVATE_LOCATIONS).document(sightingRef.id),
                PrivateLocation(ownerId = me.uid, lat = draft.lat, lng = draft.lng, accuracy = draft.accuracy),
            )
        }

        val hour = Calendar.getInstance().apply { timeInMillis = draft.seenAt }.get(Calendar.HOUR_OF_DAY)
        val stats = mapOf(
            Stats.SIGHTINGS to 1L,
            Stats.PHOTOS to draft.photoIds.size.toLong(),
            Stats.CATS_REGISTERED to if (existing == null) 1L else 0L,
            Stats.TNR_REPORTS to if (draft.earTip == EarTip.YES.key || draft.possiblyNotNeutered) 1L else 0L,
            Stats.NIGHT_SIGHTINGS to if (hour >= 21 || hour < 4) 1L else 0L,
            Stats.SOUQ_SIGHTINGS to if (hood in Dubai.souqAreas) 1L else 0L,
            Stats.MARINA_SIGHTINGS to if (hood in Dubai.marinaAreas) 1L else 0L,
        )
        val points = Points.SIGHTING + Points.PHOTO * draft.photoIds.size + if (existing == null) Points.NEW_CAT else 0
        users.addStats(batch, me.uid, stats, points, hood, rateLimited = true)
        // Whoever registers a cat follows it (it is already in the cat's followers list).
        if (existing == null) batch.update(users.ref(me.uid), "followedCats", FieldValue.arrayUnion(catRef.id))
        batch.commit().await()

        if (existing != null) {
            notifications.send(
                existing.followers - me.uid,
                NotifType.FOLLOWED_CAT,
                mapOf("cat" to catName, "user" to me.username, "area" to hood),
                "cat/${catRef.id}", sourceId = sightingRef.id,
            )
        }
        return catRef.id
    }

    private fun tnrFrom(draft: SightingDraft, current: String, verified: Boolean): String = when {
        verified -> current
        draft.earTip == EarTip.YES.key -> TnrStatus.EAR_TIPPED.key
        draft.possiblyNotNeutered -> TnrStatus.LIKELY_NOT_NEUTERED.key
        else -> current
    }

    private fun healthFrom(draft: SightingDraft, concerning: Boolean): String = when {
        draft.healthTags.contains(HealthTag.WOUND.key) || draft.healthTags.contains(HealthTag.LIMPING.key) -> HealthStatus.INJURED.key
        draft.needsMedical -> HealthStatus.NEEDS_ATTENTION.key
        concerning -> HealthStatus.MINOR_ISSUES.key
        draft.healthTags.isNotEmpty() -> HealthStatus.HEALTHY.key
        else -> HealthStatus.UNKNOWN.key
    }

    /** Keeps a running average of appearance features so matching improves with every sighting. */
    private fun blendFeatures(cat: Cat, fresh: List<Double>): List<Double> {
        if (cat.features.size != fresh.size) return fresh
        val n = cat.sightingCount.coerceIn(1, 9).toDouble()
        return cat.features.zip(fresh) { old, new -> Math.round(((old * n + new) / (n + 1)) * 10_000) / 10_000.0 }
    }

    suspend fun updateSighting(sighting: Sighting, changes: Map<String, Any>) {
        sightings.document(sighting.id).update(changes + ("updatedAt" to now())).await()
    }

    suspend fun updateSightingLocation(sighting: Sighting, lat: Double, lng: Double, privacyLevel: String, shareExact: Boolean) {
        val (pLat, pLng) = LocationPrivacy.fuzz(lat, lng, privacyLevel, sighting.id)
        db.runBatch { b ->
            b.update(
                sightings.document(sighting.id),
                mapOf(
                    "lat" to pLat, "lng" to pLng, "neighborhood" to Dubai.nearest(lat, lng).key,
                    "locationSource" to "manual", "updatedAt" to now(),
                ),
            )
            val privateRef = db.collection(Col.PRIVATE_LOCATIONS).document(sighting.id)
            if (shareExact) b.set(privateRef, PrivateLocation(ownerId = sighting.userId, lat = lat, lng = lng))
            else b.delete(privateRef)
        }.await()
    }

    suspend fun deleteSighting(sighting: Sighting) {
        db.runBatch { b ->
            b.delete(sightings.document(sighting.id))
            b.delete(db.collection(Col.PRIVATE_LOCATIONS).document(sighting.id))
            b.update(catRef(sighting.catId), mapOf("sightingCount" to FieldValue.increment(-1), "counterSourceId" to sighting.id, "updatedAt" to now()))
            users.addStats(b, sighting.userId, mapOf(Stats.SIGHTINGS to -1L), -Points.SIGHTING)
        }.await()
    }

    suspend fun updateCat(catId: String, changes: Map<String, Any>) {
        catRef(catId).update(changes + ("updatedAt" to now())).await()
    }

    suspend fun addAlias(cat: Cat, alias: String) {
        val name = alias.trim()
        catRef(cat.id).update(
            mapOf("aliases" to FieldValue.arrayUnion(name), "searchTerms" to searchTerms(cat.name, (cat.aliases + name).distinct()), "updatedAt" to now()),
        ).await()
    }

    /** A cat's `searchTerms`: its name and the other names it is known by. */
    fun searchTerms(name: String, aliases: List<String>): List<String> = SearchTerms.of(SearchTerms.words(name, *aliases.toTypedArray()))

    suspend fun setCoverPhoto(catId: String, photoId: String) = updateCat(catId, mapOf("coverPhotoId" to photoId))

    // ---- Verified medical information (volunteers and staff only) ----

    fun medicalRecords(catId: String): Flow<List<MedicalRecord>> =
        db.collection(Col.MEDICAL).whereEqualTo("catId", catId).listFlow<MedicalRecord>()
            .map { l -> l.sortedByDescending { it.at } }

    suspend fun addMedicalRecord(cat: Cat, type: String, note: String, by: UserProfile) {
        val record = db.collection(Col.MEDICAL).document()
        db.runBatch { b ->
            b.set(
                record,
                MedicalRecord(catId = cat.id, type = type, note = note.trim(), byUid = by.uid, byName = by.username, at = now()),
            )
            if (type == MedicalType.TNR_CONFIRMED.key) {
                b.update(
                    catRef(cat.id),
                    mapOf("tnrStatus" to TnrStatus.NEUTERED_VERIFIED.key, "tnrVerified" to true, "updatedAt" to now()),
                )
            }
        }.await()
        notifications.send(cat.followers - by.uid, NotifType.FOLLOWED_CAT, mapOf("cat" to cat.name, "user" to by.username, "area" to "medical"), "cat/${cat.id}", sourceId = record.id)
    }

    // ---- Duplicate profiles ----

    fun mergeRequests(status: String = "pending"): Flow<List<MergeRequest>> =
        db.collection(Col.MERGES).whereEqualTo("status", status).listFlow<MergeRequest>()
            .map { l -> l.sortedByDescending { it.createdAt } }

    suspend fun suggestMerge(primary: Cat, duplicate: Cat, reason: String, similarity: Double, me: UserProfile) {
        db.runBatch { b ->
            b.set(
                db.collection(Col.MERGES).document(),
                MergeRequest(
                    primaryCatId = primary.id, primaryName = primary.name,
                    duplicateCatId = duplicate.id, duplicateName = duplicate.name,
                    requestedBy = me.uid, requestedByName = me.username,
                    reason = reason.trim(), similarity = similarity, createdAt = now(),
                ),
            )
            users.addStats(b, me.uid, mapOf(Stats.MERGE_SUGGESTIONS to 1L), Points.MERGE, rateLimited = true)
        }.await()
    }

    /** Moves every sighting and photo of the duplicate onto the primary profile. */
    suspend fun approveMerge(request: MergeRequest, moderator: UserProfile, note: String) {
        val primary = cat(request.primaryCatId) ?: throw IllegalStateException("Primary cat missing")
        val duplicate = cat(request.duplicateCatId) ?: throw IllegalStateException("Duplicate cat missing")

        val moved = sightings.whereEqualTo("catId", duplicate.id).get().await().documents.map { it.reference } +
            db.collection(Col.MEDICAL).whereEqualTo("catId", duplicate.id).get().await().documents.map { it.reference }
        moved.chunked(400).forEach { chunk ->
            db.runBatch { b ->
                chunk.forEach { ref ->
                    // A moved sighting no longer registers a new cat, so its timeline stops saying "new cat".
                    if (ref.parent.id == Col.SIGHTINGS) b.update(ref, mapOf("catId" to primary.id, "catName" to primary.name, "newCat" to false))
                    else b.update(ref, "catId", primary.id)
                }
            }.await()
        }

        val primaryUpdates = mutableMapOf<String, Any>(
            "sightingCount" to FieldValue.increment(duplicate.sightingCount),
            "firstSeen" to (listOf(primary.firstSeen, duplicate.firstSeen).filter { it > 0 }.minOrNull() ?: primary.firstSeen),
            "updatedAt" to now(),
        )
        val aliases = (listOf(duplicate.name) + duplicate.aliases).filter { it.isNotBlank() && it != primary.name }
        if (aliases.isNotEmpty()) {
            primaryUpdates["aliases"] = FieldValue.arrayUnion(*aliases.toTypedArray())
            primaryUpdates["searchTerms"] = searchTerms(primary.name, (primary.aliases + aliases).distinct())
        }
        if (duplicate.photoIds.isNotEmpty()) primaryUpdates["photoIds"] = FieldValue.arrayUnion(*duplicate.photoIds.toTypedArray())
        if (duplicate.contributors.isNotEmpty()) primaryUpdates["contributors"] = FieldValue.arrayUnion(*duplicate.contributors.toTypedArray())
        if (duplicate.followers.isNotEmpty()) primaryUpdates["followers"] = FieldValue.arrayUnion(*duplicate.followers.toTypedArray())
        if (duplicate.neighborhoods.isNotEmpty()) primaryUpdates["neighborhoods"] = FieldValue.arrayUnion(*duplicate.neighborhoods.toTypedArray())
        if (duplicate.lastSeen > primary.lastSeen) {
            primaryUpdates["lastSeen"] = duplicate.lastSeen
            primaryUpdates["lat"] = duplicate.lat
            primaryUpdates["lng"] = duplicate.lng
            primaryUpdates["geoCells"] = Geohash.cells(duplicate.lat, duplicate.lng)
            primaryUpdates["neighborhood"] = duplicate.neighborhood
        }
        // The duplicate's followers now follow the kept profile; their own "following" lists must say so too,
        // otherwise they get its alerts while its Follow button and their Following tab disagree.
        val followedCats = duplicate.followers.mapNotNull { uid ->
            users.profile(uid)?.let { u -> uid to (u.followedCats - duplicate.id + primary.id).distinct() }
        }

        db.runBatch { b ->
            b.update(catRef(primary.id), primaryUpdates)
            b.update(catRef(duplicate.id), mapOf("mergedInto" to primary.id, "hidden" to true, "updatedAt" to now()))
            b.update(
                db.collection(Col.MERGES).document(request.id),
                mapOf(
                    "status" to "approved", "reviewedBy" to moderator.uid, "reviewedByName" to moderator.username,
                    "reviewNote" to note, "reviewedAt" to now(),
                ),
            )
        }.await()
        followedCats.chunked(400).forEach { chunk ->
            db.runBatch { b -> chunk.forEach { (uid, list) -> b.update(users.ref(uid), "followedCats", list) } }.await()
        }
        moderation.log(moderator, "merge_approved", "cat", primary.id, "${duplicate.name} → ${primary.name}", note)
        notifications.send(
            listOf(request.requestedBy), NotifType.MODERATION,
            mapOf("target" to "merge", "label" to "${duplicate.name} → ${primary.name}", "decision" to "approved"),
            "cat/${primary.id}",
        )
    }

    suspend fun rejectMerge(request: MergeRequest, moderator: UserProfile, note: String) {
        db.collection(Col.MERGES).document(request.id).update(
            mapOf(
                "status" to "rejected", "reviewedBy" to moderator.uid, "reviewedByName" to moderator.username,
                "reviewNote" to note, "reviewedAt" to now(),
            ),
        ).await()
        moderation.log(moderator, "merge_rejected", "cat", request.primaryCatId, "${request.duplicateName} → ${request.primaryName}", note)
        notifications.send(
            listOf(request.requestedBy), NotifType.MODERATION,
            mapOf("target" to "merge", "label" to "${request.duplicateName} → ${request.primaryName}", "decision" to "rejected"),
            "cat/${request.primaryCatId}",
        )
    }

    // ---- Moderation of submissions ----

    suspend fun reviewSighting(sighting: Sighting, approve: Boolean, moderator: UserProfile) {
        db.runBatch { b ->
            b.update(sightings.document(sighting.id), mapOf("status" to if (approve) "approved" else "rejected", "updatedAt" to now()))
            if (approve) users.addStats(b, sighting.userId, mapOf(Stats.APPROVED_SIGHTINGS to 1L), Points.APPROVED)
        }.await()
        moderation.log(moderator, if (approve) "sighting_approved" else "sighting_rejected", "sighting", sighting.id, sighting.catName, "")
        notifications.send(
            listOf(sighting.userId), NotifType.MODERATION,
            mapOf("target" to "sighting", "label" to sighting.catName, "decision" to if (approve) "approved" else "rejected"),
            "sighting/${sighting.id}",
        )
    }

    suspend fun setCatHidden(cat: Cat, hidden: Boolean, moderator: UserProfile, note: String) {
        updateCat(cat.id, mapOf("hidden" to hidden))
        moderation.log(moderator, if (hidden) "cat_hidden" else "cat_restored", "cat", cat.id, cat.name, note)
        if (hidden) {
            notifications.send(
                listOf(cat.createdBy), NotifType.MODERATION,
                mapOf("target" to "cat", "label" to cat.name, "decision" to "removed"), "",
            )
        }
    }
}
