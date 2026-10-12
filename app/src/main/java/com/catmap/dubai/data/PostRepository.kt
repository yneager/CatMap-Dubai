package com.catmap.dubai.data

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
import kotlinx.coroutines.flow.Flow
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
)

class PostRepository(
    private val db: FirebaseFirestore,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val moderation: ModerationRepository,
) {
    private val posts = db.collection(Col.POSTS)
    private val areas = db.collection(Col.AREA_FOLLOWS)

    fun ref(id: String) = posts.document(id)

    /** Public feed: visible posts, newest first. Search, filters and distance work on this list. */
    fun feed(limit: Long = 400): Flow<List<Post>> =
        posts.whereEqualTo("hidden", false).orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).listFlow()

    suspend fun feedOnce(limit: Long = 400): List<Post> =
        posts.whereEqualTo("hidden", false).orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).get().await()
            .toObjects(Post::class.java)

    /** Moderators also see hidden posts. */
    fun allPosts(): Flow<List<Post>> =
        posts.orderBy("createdAt", Query.Direction.DESCENDING).limit(600).listFlow()

    fun postFlow(id: String): Flow<Post?> = ref(id).objectFlow()

    suspend fun post(id: String): Post? = runCatching { ref(id).get().await().toObject(Post::class.java) }.getOrNull()

    /** Someone else's visible posts (their profile's activity). */
    fun publicPostsBy(uid: String): Flow<List<Post>> =
        posts.whereEqualTo("ownerId", uid).whereEqualTo("hidden", false).listFlow<Post>().map { l -> l.sortedByDescending { it.createdAt } }

    fun postsBy(uid: String): Flow<List<Post>> =
        posts.whereEqualTo("ownerId", uid).listFlow<Post>().map { l -> l.sortedByDescending { it.createdAt } }

    // ---------- creating and editing ----------

    suspend fun create(draft: PostDraft, me: UserProfile): String {
        val ref = posts.document()
        val created = now()
        val post = draft.toPost(me, created).apply { createdAt = created }
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
                    .toObjects(AreaFollow::class.java).map { it.uid }
            }.getOrDefault(emptyList()) - me.uid
            notifications.send(followers, NotifType.LOST_NEARBY, params, "post/${ref.id}", dedupeKey = "lost_${ref.id}")
        }
        if (category == PostCategory.FOUND || category == PostCategory.SPOTTED) {
            // Owners of open lost posts for the same kind of animal close by get a "possible match" alert.
            val owners = runCatching { feedOnce(300) }.getOrDefault(emptyList())
                .filter {
                    it.category == PostCategory.LOST.key && it.status == PostStatus.OPEN.key && it.animalType == post.animalType &&
                        it.ownerId != me.uid && distanceKm(it.lat, it.lng, post.lat, post.lng) <= MATCH_RADIUS_KM
                }
                .map { it.ownerId }
            notifications.send(owners, NotifType.POSSIBLE_MATCH, params, "post/${ref.id}", dedupeKey = "match_${ref.id}")
        }
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
            "keywords" to fresh.keywords, "updatedAt" to fresh.updatedAt,
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
            keywords = keywords(name, color, breed, features, description, hood),
            updatedAt = time,
        )
    }

    suspend fun setStatus(post: Post, status: PostStatus, me: UserProfile) {
        db.runBatch { b ->
            b.update(ref(post.id), mapOf("status" to status.key, "resolvedAt" to if (status == PostStatus.OPEN) 0L else now(), "updatedAt" to now()))
            if (status == PostStatus.REUNITED && post.status != PostStatus.REUNITED.key && post.ownerId == me.uid) {
                users.addStats(b, me.uid, mapOf(Stats.REUNITED to 1), points = 25)
            }
        }.await()
    }

    suspend fun setAdoptionStatus(post: Post, status: AdoptionStatus) {
        ref(post.id).update(mapOf("adoptionStatus" to status.key, "updatedAt" to now())).await()
    }

    /** Deletes a post with its updates and photos. */
    suspend fun delete(post: Post, images: ImageRepository) {
        val updates = ref(post.id).collection(Col.UPDATES).get().await().documents
        updates.chunked(400).forEach { chunk -> db.runBatch { b -> chunk.forEach { b.delete(it.reference) } }.await() }
        ref(post.id).delete().await()
        (post.photoIds + updates.mapNotNull { it.getString("photoId") }.filter { it.isNotBlank() })
            .forEach { runCatching { images.delete(it) } }
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
        moderation.log(moderator, "post_deleted", "post", post.id, post.name.ifBlank { post.category }, note)
        notifications.send(
            listOf(post.ownerId), NotifType.MODERATION,
            mapOf("target" to "post", "label" to post.name.ifBlank { post.category }, "decision" to "removed"),
        )
    }

    // ---------- updates and sightings on a post ----------

    fun updates(postId: String): Flow<List<PostUpdate>> =
        ref(postId).collection(Col.UPDATES).orderBy("createdAt", Query.Direction.DESCENDING).limit(200).listFlow()

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

    fun areaFollow(uid: String): Flow<AreaFollow?> = areas.document(uid).objectFlow()

    /** Follows every neighbourhood whose centre lies within [radiusKm] of the point (at least the nearest one). */
    suspend fun followArea(uid: String, lat: Double, lng: Double, radiusKm: Double) {
        val hoods = Dubai.neighborhoods.filter { distanceKm(lat, lng, it.lat, it.lng) <= radiusKm }.map { it.key }
            .ifEmpty { listOf(Dubai.nearest(lat, lng).key) }
        areas.document(uid).set(AreaFollow(neighborhoods = hoods, lat = lat, lng = lng, radiusKm = radiusKm, updatedAt = now())).await()
    }

    suspend fun unfollowArea(uid: String) {
        areas.document(uid).delete().await()
    }

    companion object {
        const val MAX_PHOTOS = 6
        const val MATCH_RADIUS_KM = 5.0
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
                .filter { draft.lat == 0.0 || distanceKm(it.lat, it.lng, draft.lat, draft.lng) <= 3.0 }
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
