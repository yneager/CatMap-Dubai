package com.catmap.dubai.data

import com.catmap.dubai.data.model.ContentReport
import com.catmap.dubai.data.model.AdoptionStatus
import com.catmap.dubai.data.model.AreaFollow
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.data.model.PostUpdate
import com.catmap.dubai.data.model.Stats
import com.catmap.dubai.data.model.UpdateKind
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.data.model.labeledFrom
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** What the post form collects; photos are uploaded before [PostRepository.create] is called. */
data class PostDraft(
    val animalType: String,
    val category: String,
    val name: String,
    val description: String,
    val color: String,
    val breed: String,
    val features: String,
    val sex: String,
    val ageGroup: String,
    val photoIds: List<String>,
    val videoPath: String,
    val lat: Double,
    val lng: Double,
    val approximate: Boolean,
    val areaRadiusM: Long,
    val lastSeenAt: Long,
    val reward: Long,
    val contactMethod: String,
    val contactPhone: String,
    val contactEmail: String,
    val health: String,
    val vaccinated: Boolean,
    val neutered: Boolean,
    val fingerprint: List<Double> = emptyList(),
    val microchip: String = "unknown",
)

enum class PostAvailability { GONE, HIDDEN, THERE }

class PostRepository(
    private val db: FirebaseFirestore,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val moderation: ModerationRepository,
    private val videos: VideoRepository,
) {
    private val posts = db.collection(Col.POSTS)
    private val areas = db.collection(Col.AREA_FOLLOWS)

    fun ref(id: String) = posts.document(id)

    /** The newest visible posts within [radiusKm], found through the map-area cells (Phase 4) instead of the whole feed. */
    suspend fun near(lat: Double, lng: Double, radiusKm: Double, perCell: Long = NEAR_PER_CELL): List<Post> = coroutineScope {
        Geohash.around(lat, lng, radiusKm).map { cell ->
            async {
                posts.whereEqualTo("hidden", false).whereArrayContains("geoCells", cell)
                    .orderBy("updatedAt", Query.Direction.DESCENDING).limit(perCell).get().await().safeObjects<Post>()
            }
        }.awaitAll().flatten().distinctBy { it.id }.filter { distanceKm(lat, lng, it.lat, it.lng) <= radiusKm }
    }

    /** The newest visible posts, for when there's no place to search around yet. */
    suspend fun newest(limit: Long): List<Post> = feedQuery().limit(limit).get().await().safeObjects()

    /**
     * Visible posts by id (favourites); deleted and hidden ones are left out. One read per post, like a query, but
     * read one by one: the rules refuse an `id in [...]` query as soon as one of the posts has been deleted.
     */
    suspend fun byIds(ids: List<String>): List<Post> = coroutineScope {
        ids.distinct().map { id -> async { runCatching { ref(id).get().await().safeObject<Post>() }.getOrNull() } }
            .awaitAll().filterNotNull().filter { !it.hidden }
    }

    /** Visible posts, newest first: Explore and the lists page through this (optionally only some categories). */
    fun feedQuery(categories: Collection<String> = emptyList()): Query {
        val base = posts.whereEqualTo("hidden", false)
        return (if (categories.isEmpty()) base else base.whereIn("category", categories.toList()))
            .orderBy("createdAt", Query.Direction.DESCENDING)
    }

    /** Posts whose search words include [word] (or its stored first letters), newest first. */
    fun searchQuery(word: String): Query =
        posts.whereEqualTo("hidden", false).whereArrayContainsAny("searchTerms", SearchTerms.query(word))
            .orderBy("createdAt", Query.Direction.DESCENDING)

    /** Every post for moderators, hidden ones included (or only hidden ones, or one category), newest first. */
    fun moderationQuery(hiddenOnly: Boolean, category: String?): Query {
        var q: Query = posts
        if (hiddenOnly) q = q.whereEqualTo("hidden", true)
        if (category != null) q = q.whereEqualTo("category", category)
        return q.orderBy("createdAt", Query.Direction.DESCENDING)
    }

    fun postFlow(id: String): Flow<Post?> = ref(id).snapshotFlow().map { it.safeObject<Post>() }
        .catch { error ->
            if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) emit(null)
            else throw error
        }.retryWhen { error, _ -> AppErrors.awaitListenerRetry(error) }

    suspend fun post(id: String): Post? = runCatching { ref(id).get().await().toObject(Post::class.java) }.getOrNull()

    /** Only a confirmed server miss is gone; refused posts may be restored, and offline is unknown. */
    suspend fun availability(id: String): PostAvailability = try {
        if (ref(id).get(Source.SERVER).await().exists()) PostAvailability.THERE else PostAvailability.GONE
    } catch (error: FirebaseFirestoreException) {
        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) PostAvailability.HIDDEN else PostAvailability.THERE
    }

    /** Someone else's visible posts (their profile's activity). */
    fun publicPostsBy(uid: String): Flow<List<Post>> =
        posts.whereEqualTo("ownerId", uid).whereEqualTo("hidden", false).listFlow<Post>().map { l -> l.sortedByDescending { it.createdAt } }

    fun postsBy(uid: String): Flow<List<Post>> =
        posts.whereEqualTo("ownerId", uid).listFlow<Post>().map { l -> l.sortedByDescending { it.createdAt } }

    // ---------- creating and editing ----------

    suspend fun create(draft: PostDraft, me: UserProfile): String {
        val ref = posts.document()
        val created = now()
        val post = draft.toPost(me, created).apply { createdAt = created; lastActivityAt = created }
        val category = labeledFrom(draft.category, PostCategory.OTHER)
        db.runBatch { b ->
            b.set(ref, post)
            users.addStats(b, me.uid, mapOf(Stats.POSTS to 1), points = 10, neighborhood = post.neighborhood, rateLimited = true)
        }.await()
        val title = post.name.ifBlank { "" }
        val params = mapOf("type" to post.animalType, "category" to post.category, "area" to post.neighborhood, "name" to title)
        if (category == PostCategory.LOST) {
            // Everyone following this area hears about it, at most once per post.
            val followers = runCatching {
                areas.whereArrayContains("neighborhoods", post.neighborhood).limit(300).get().await()
                    .safeObjects<AreaFollow>().map { it.uid }
            }.getOrDefault(emptyList()) - me.uid
            notifications.send(followers, NotifType.LOST_NEARBY, params, "post/${ref.id}", dedupeKey = "lost_${ref.id}")
        }
        // Possible matches with lost reports (and, for a lost report, with earlier sightings) are found by
        // MatchRepository.afterPublish: the PC's AI model when it's online, the phone's fingerprint otherwise.
        return ref.id
    }

    suspend fun update(existing: Post, draft: PostDraft, me: UserProfile) {
        val fresh = draft.toPost(me, now())
        val changes = mapOf(
            "animalType" to fresh.animalType, "category" to fresh.category, "name" to fresh.name,
            "description" to fresh.description, "color" to fresh.color, "breed" to fresh.breed, "features" to fresh.features,
            "sex" to fresh.sex, "ageGroup" to fresh.ageGroup, "photoIds" to fresh.photoIds, "videoPath" to fresh.videoPath,
            "lat" to fresh.lat, "lng" to fresh.lng, "approximate" to fresh.approximate, "areaRadiusM" to fresh.areaRadiusM,
            "neighborhood" to fresh.neighborhood, "lastSeenAt" to fresh.lastSeenAt, "reward" to fresh.reward,
            "contactMethod" to fresh.contactMethod, "contactPhone" to fresh.contactPhone, "contactEmail" to fresh.contactEmail,
            "health" to fresh.health, "vaccinated" to fresh.vaccinated, "neutered" to fresh.neutered,
            "keywords" to fresh.keywords, "updatedAt" to fresh.updatedAt, "lastActivityAt" to fresh.updatedAt,
            "fingerprint" to fresh.fingerprint,
            "microchip" to fresh.microchip,
            "geoCells" to fresh.geoCells,
            "searchTerms" to fresh.searchTerms,
        )
        ref(existing.id).update(changes).await()
    }

    private fun PostDraft.toPost(me: UserProfile, time: Long): Post {
        val hood = Dubai.nearest(lat, lng).key
        val isLost = category == PostCategory.LOST.key
        return Post(
            ownerId = me.uid, ownerName = me.username, ownerPhotoId = me.photoId,
            animalType = animalType, category = category, status = PostStatus.OPEN.key,
            name = name.trim().take(60), description = description.trim().take(2000),
            color = color.trim().take(60), breed = breed.trim().take(60), features = features.trim().take(400),
            sex = sex, ageGroup = ageGroup, photoIds = photoIds.take(MAX_PHOTOS), videoPath = videoPath,
            lat = lat, lng = lng, approximate = approximate, areaRadiusM = areaRadiusM.coerceIn(0, 5_000),
            neighborhood = hood, lastSeenAt = lastSeenAt,
            reward = if (isLost) reward.coerceIn(0, 999_999) else 0,
            contactMethod = contactMethod,
            contactPhone = contactPhone.trim().take(30), contactEmail = contactEmail.trim().take(80),
            health = health.trim().take(400), vaccinated = vaccinated, neutered = neutered,
            fingerprint = fingerprint,
            microchip = microchip,
            keywords = keywords(name, color, breed, features, description, hood),
            geoCells = Geohash.cells(lat, lng),
            updatedAt = time,
        ).apply { searchTerms = SearchTerms.of(keywords) }
    }

    suspend fun setStatus(post: Post, status: PostStatus, me: UserProfile) {
        db.runBatch { b ->
            val changes = mutableMapOf<String, Any>("status" to status.key, "resolvedAt" to if (status == PostStatus.OPEN) 0L else now(), "updatedAt" to now(), "lastActivityAt" to now())
            // Reopening forgets how it ended; the match history itself is kept.
            if (status == PostStatus.OPEN) changes += mapOf("resolution" to "", "resolvedMatchId" to "")
            b.update(ref(post.id), changes)
            if (status == PostStatus.REUNITED && post.status != PostStatus.REUNITED.key && post.ownerId == me.uid) {
                users.addStats(b, me.uid, mapOf(Stats.REUNITED to 1), points = 25, rateLimited = true)
            }
        }.await()
    }

    suspend fun stillLooking(post: Post) {
        ref(post.id).update(mapOf("lastActivityAt" to now(), "updatedAt" to now())).await()
    }

    suspend fun remindStalePosts(uid: String) {
        val at = now()
        posts.whereEqualTo("ownerId", uid).get().await().safeObjects<Post>()
            .filter { !it.hidden && PostStaleness.needsReminder(it, at) }.forEach { post ->
                notifications.send(listOf(uid), NotifType.STALE_POST, mapOf("name" to post.name), "post/${post.id}", PostStaleness.reminderId(post,at))
            }
    }

    suspend fun setAdoptionStatus(post: Post, status: AdoptionStatus) {
        ref(post.id).update(mapOf("adoptionStatus" to status.key, "updatedAt" to now())).await()
    }

    /** Deletes a post with its updates and photos. */
    suspend fun delete(post: Post, images: ImageRepository) {
        val updates = ref(post.id).collection(Col.UPDATES).get().await().documents
        updates.chunked(400).forEach { chunk -> db.runBatch { b -> chunk.forEach { b.delete(it.reference) } }.await() }
        ref(post.id).delete().await()
        runCatching { videos.delete(post.videoPath) }.onFailure { android.util.Log.i("CatMap", "Post video cleanup failed", it) }
        runCatching { db.collection(Col.POST_EMBEDDINGS).document(post.id).delete().await() }
            .onFailure { android.util.Log.i("CatMap", "Embedding cleanup failed", it) }
        runCatching {
            db.collection(Col.ADOPTION_REQUESTS).whereEqualTo("ownerId", post.ownerId).whereEqualTo("postId", post.id)
                .get().await().documents.forEach { it.reference.delete().await() }
        }.onFailure { android.util.Log.i("CatMap", "Adoption cleanup failed", it) }
        (post.photoIds + updates.mapNotNull { it.getString("photoId") }.filter { it.isNotBlank() })
            .forEach { runCatching { images.delete(it) } }
        // Its match suggestions too (allowed once the post is gone). A moderator can't delete them; the two people's
        // apps drop them when they next see them (MatchRepository.forPost).
        runCatching {
            db.collection(Col.MATCHES).whereArrayContains("members", post.ownerId).get().await().documents
                .filter { it.getString("lostPostId") == post.id || it.getString("sightingPostId") == post.id }
                .forEach { runCatching { it.reference.delete().await() } }
        }
    }

    suspend fun setHidden(post: Post, hidden: Boolean, moderator: UserProfile, note: String) {
        ref(post.id).update(mapOf("hidden" to hidden, "updatedAt" to now())).await()
        moderation.log(moderator, if (hidden) "post_hidden" else "post_restored", "post", post.id, post.name.ifBlank { post.category }, note)
        if (hidden) {
            notifications.send(
                listOf(post.ownerId), NotifType.MODERATION,
                mapOf("target" to "post", "label" to post.name.ifBlank { post.category }, "decision" to "removed"),
            )
        }
    }

    suspend fun moderatorDelete(post: Post, moderator: UserProfile, images: ImageRepository, note: String) {
        delete(post, images)
        runCatching {
            db.collection(Col.REPORTS).whereEqualTo("targetType", "post").whereEqualTo("targetId", post.id)
                .whereEqualTo("status", "open").get().await().safeObjects<ContentReport>().forEach { report ->
                    runCatching { moderation.closeReport(report, "resolved", note.ifBlank { "post deleted" }, moderator) }
                        .onFailure { android.util.Log.i("CatMap", "Post report cleanup failed", it) }
                }
        }.onFailure { android.util.Log.i("CatMap", "Post report lookup failed", it) }
        moderation.log(moderator, "post_deleted", "post", post.id, post.name.ifBlank { post.category }, note)
        notifications.send(
            listOf(post.ownerId), NotifType.MODERATION,
            mapOf("target" to "post", "label" to post.name.ifBlank { post.category }, "decision" to "removed"),
        )
    }

    // ---------- updates and sightings on a post ----------

    /** The newest [UPDATES_WINDOW] updates of a post, live; older ones are read a page at a time ([olderUpdatesQuery]). */
    fun updates(postId: String): Flow<List<PostUpdate>> =
        ref(postId).collection(Col.UPDATES).orderBy("createdAt", Query.Direction.DESCENDING).limit(UPDATES_WINDOW).listFlow()

    fun olderUpdatesQuery(postId: String, before: Long): Query =
        ref(postId).collection(Col.UPDATES).orderBy("createdAt", Query.Direction.DESCENDING).startAfter(before)

    suspend fun addUpdate(post: Post, kind: UpdateKind, text: String, photoId: String, lat: Double, lng: Double, me: UserProfile) {
        val doc = ref(post.id).collection(Col.UPDATES).document()
        db.runBatch { b ->
            b.set(
                doc,
                PostUpdate(
                    postId = post.id, kind = kind.key, text = text.trim().take(1000), photoId = photoId,
                    lat = lat, lng = lng, neighborhood = if (lat != 0.0 || lng != 0.0) Dubai.nearest(lat, lng).key else "",
                    byUid = me.uid, byName = me.username, createdAt = now(),
                ),
            )
            b.update(ref(post.id), mapOf("updatesCount" to FieldValue.increment(1), "updatedAt" to now()))
            users.addStats(b, me.uid, mapOf(Stats.POST_UPDATES to 1), points = 5, rateLimited = true)
        }.await()
        if (post.ownerId != me.uid) {
            notifications.send(
                listOf(post.ownerId), NotifType.POST_UPDATE,
                mapOf("user" to me.username, "kind" to kind.key, "name" to post.name, "type" to post.animalType),
                "post/${post.id}", dedupeKey = "upd_${post.id}",
            )
        }
    }

    suspend fun deleteUpdate(post: Post, update: PostUpdate, images: ImageRepository) {
        db.runBatch { b ->
            b.delete(ref(post.id).collection(Col.UPDATES).document(update.id))
            b.update(ref(post.id), "updatesCount", FieldValue.increment(-1))
        }.await()
        if (update.photoId.isNotBlank()) runCatching { images.delete(update.photoId) }
    }

    // ---------- area alerts ----------

    // The shared areaFollows document only says which neighbourhoods someone follows, which is all a lost-pet poster
    // needs to alert them. Where the alert area is centred (often near home) is kept in users/{uid}/private/areaFollow,
    // which only its owner can read.
    private fun areaCentre(uid: String) = db.collection(Col.USERS).document(uid).collection(Col.PRIVATE).document("areaFollow")

    fun areaFollow(uid: String): Flow<AreaFollow?> =
        combine(areas.document(uid).objectFlow<AreaFollow>(), areaCentre(uid).objectFlow<AreaFollow>()) { follow, centre ->
            // Versions up to 0.0.9 kept the centre in the shared document; it's used until it has been moved.
            if (follow != null && centre != null) follow.copy(lat = centre.lat, lng = centre.lng, radiusKm = centre.radiusKm) else follow
        }

    /** Follows every neighbourhood whose centre lies within [radiusKm] of the point (at least the nearest one). */
    suspend fun followArea(uid: String, lat: Double, lng: Double, radiusKm: Double) {
        val hoods = Dubai.neighborhoods.filter { distanceKm(lat, lng, it.lat, it.lng) <= radiusKm }.map { it.key }
            .ifEmpty { listOf(Dubai.nearest(lat, lng).key) }
        val at = now()
        db.runBatch { b ->
            b.set(areas.document(uid), mapOf("neighborhoods" to hoods, "updatedAt" to at))
            b.set(areaCentre(uid), mapOf("lat" to lat, "lng" to lng, "radiusKm" to radiusKm, "updatedAt" to at))
        }.await()
    }

    suspend fun unfollowArea(uid: String) {
        db.runBatch { b ->
            b.delete(areas.document(uid))
            b.delete(areaCentre(uid))
        }.await()
    }

    /** Moves a centre saved by version 0.0.9 or older out of the shared document into the private one. */
    suspend fun moveAreaCentreToPrivate(uid: String) {
        val legacy = areas.document(uid).get().await()
        val lat = legacy.getDouble("lat") ?: return
        val lng = legacy.getDouble("lng") ?: return
        db.runBatch { b ->
            b.set(areaCentre(uid), mapOf("lat" to lat, "lng" to lng, "radiusKm" to (legacy.getDouble("radiusKm") ?: 3.0), "updatedAt" to now()))
            b.update(areas.document(uid), mapOf("lat" to FieldValue.delete(), "lng" to FieldValue.delete(), "radiusKm" to FieldValue.delete()))
        }.await()
    }

    companion object {
        const val MAX_PHOTOS = 6
        const val MATCH_RADIUS_KM = 5.0
        const val DUPLICATE_RADIUS_KM = 3.0
        const val UPDATES_WINDOW = 30L
        /** Newest posts asked per map cell by [near]: duplicates and same-animal checks only need recent ones. */
        const val NEAR_PER_CELL = 30L
        private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

        fun keywords(vararg parts: String): List<String> {
            val hood = parts.last()
            val words = parts.dropLast(1).flatMap { it.lowercase(Locale.ROOT).split(SPLIT) }
                .plus(Dubai.byKey(hood).nameEn.lowercase(Locale.ROOT).split(SPLIT))
                .plus(Dubai.byKey(hood).nameAr.split(SPLIT))
            return words.filter { it.length >= 2 }.distinct().take(80)
        }

        /** Posts that might describe the same animal: same kind, nearby, recent, overlapping words. */
        fun likelyDuplicates(draft: Post, candidates: List<Post>): List<Post> {
            val words = draft.keywords.toSet()
            return candidates.asSequence()
                .filter { it.id != draft.id && it.animalType == draft.animalType && it.status == PostStatus.OPEN.key }
                .filter { draft.lat == 0.0 || distanceKm(it.lat, it.lng, draft.lat, draft.lng) <= DUPLICATE_RADIUS_KM }
                .filter { draft.createdAt == 0L || kotlin.math.abs(it.createdAt - draft.createdAt) <= 21L * 24 * 3600 * 1000 }
                .map { it to it.keywords.count { w -> w in words } + if (it.category == draft.category) 1 else 0 }
                .filter { (_, score) -> score >= 2 }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(5)
                .toList()
        }
    }
}
