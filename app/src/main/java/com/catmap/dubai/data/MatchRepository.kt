package com.catmap.dubai.data

import com.catmap.dubai.ai.MatchEngine
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostMatch
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.data.model.Resolution
import com.catmap.dubai.data.model.Stats
import com.catmap.dubai.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * The lost-to-reunited workflow: match suggestions between lost reports and sightings, people's answers to them,
 * the conversation about a match, and the reunion.
 *
 * Who suggests: the PC worker (cat-server, pet-recognition-base), in the background, for every new lost report and
 * sighting in both directions. While the PC is off, the phone that publishes the post (and the owner's phone when
 * they open their report) suggests from the colour fingerprint instead, with the same rules ([MatchEngine]) and the
 * same documents; the PC re-scores them when it's back. Nobody but the owner can confirm or close anything.
 */
class MatchRepository(
    private val db: FirebaseFirestore,
    private val posts: PostRepository,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val chats: ChatRepository,
    private val ai: AiServer,
) {
    private val col = db.collection(Col.MATCHES)

    /** Every match the person is part of (as the owner or the reporter). */
    fun mine(uid: String): Flow<List<PostMatch>> =
        col.whereArrayContains("members", uid).listFlow<PostMatch>().map { l -> l.sortedByDescending { it.score } }

    /**
     * The person's matches on [postId] whose other post still exists. Deleting a post removes its matches, but not
     * always (a moderator's delete, an older app), so each other post is checked once while the screen is open and a
     * pair whose post is gone is dropped and deleted.
     */
    fun forPost(uid: String, postId: String): Flow<List<PostMatch>> = flow {
        val checked = HashMap<String, PostAvailability>()
        emitAll(
            mine(uid).map { l ->
                l.filter { it.lostPostId == postId || it.sightingPostId == postId }.filterNot { m ->
                    val other = if (m.lostPostId == postId) m.sightingPostId else m.lostPostId
                    checked.getOrPut(other) { posts.availability(other) }.also { if (it == PostAvailability.GONE) drop(m) } != PostAvailability.THERE
                }
            },
        )
    }

    fun matchFlow(id: String): Flow<PostMatch?> = col.document(id).objectFlow()

    /** Unavailable when either post is gone or hidden; only a confirmed deletion removes the match. */
    suspend fun orphaned(match: PostMatch): Boolean {
        val states = listOf(posts.availability(match.lostPostId), posts.availability(match.sightingPostId))
        if (PostAvailability.GONE in states) drop(match)
        return states.any { it != PostAvailability.THERE }
    }

    /** The rules let either person delete a match once one of its posts is gone; not awaited, so it waits offline. */
    private fun drop(match: PostMatch) {
        col.document(match.id).delete().addOnFailureListener { android.util.Log.i("CatMap", "Match ${match.id} not removed: ${it.message}") }
    }

    // ---------- suggesting ----------

    /**
     * After a lost report or a sighting is published. When the PC is online it matches the post with the AI model
     * within seconds (it watches new posts itself), so the phone only steps in while the PC is off.
     */
    suspend fun afterPublish(postId: String, me: UserProfile) {
        val post = posts.post(postId) ?: return
        if (!MatchEngine.isLost(post) && !MatchEngine.isSighting(post)) return
        if (ai.health() != null) return
        matchOnDevice(post, candidates(post), me)
    }

    /**
     * The other side of a pair: the newest lost reports for a sighting, the newest sightings for a lost report (one
     * query on the category, instead of the whole feed). Distance and time are judged by [MatchEngine].
     */
    private suspend fun candidates(post: Post): List<Post> {
        val other = if (MatchEngine.isLost(post)) MatchEngine.SIGHTINGS else MatchEngine.LOST
        return runCatching { posts.feedQuery(other).limit(CANDIDATES).get().await().safeObjects<Post>() }.getOrDefault(emptyList())
    }

    /**
     * The owner opened their open lost report (or a reporter their sighting) while the PC is off: pairs it with
     * posts published since, so a sighting that came first is found too. Only missing pairs are written.
     */
    suspend fun reconcile(post: Post, me: UserProfile) {
        if (post.ownerId != me.uid || post.status != PostStatus.OPEN.key) return
        if (ai.health() != null) return
        matchOnDevice(post, candidates(post), me)
    }

    /** Writes the phone's suggestions (never "strong") and tells the other person about the best few. */
    suspend fun matchOnDevice(post: Post, feed: List<Post>, me: UserProfile): Int {
        val pairs = MatchEngine.deviceMatches(post, feed).filter { it.result.tier != "none" }.take(MAX_DEVICE_MATCHES)
        var written = 0
        for (p in pairs) {
            val ref = col.document(p.id)
            // A pair that exists (maybe rejected) is never written again; the rules refuse overwrites anyway.
            if (runCatching { ref.get().await().exists() }.getOrDefault(true)) continue
            val r = p.result
            val now = now()
            runCatching {
                ref.set(
                    mapOf(
                        "lostPostId" to p.lost.id, "sightingPostId" to p.sighting.id,
                        "lostOwnerId" to p.lost.ownerId, "sightingOwnerId" to p.sighting.ownerId,
                        "members" to listOf(p.lost.ownerId, p.sighting.ownerId), "animalType" to p.lost.animalType,
                        "score" to r.score, "visual" to r.visual, "tier" to r.tier, "method" to r.method,
                        "model" to if (r.method == "fingerprint") MatchEngine.MODEL_FINGERPRINT else MatchEngine.MODEL_DETAILS,
                        "distanceKm" to r.distanceKm, "daysApart" to r.daysApart, "reasons" to r.reasons, "source" to "device",
                        "ownerState" to "new", "reporterState" to "new", "createdBy" to me.uid,
                        "createdAt" to now, "updatedAt" to now, "notified" to (r.tier == "possible"),
                    ),
                ).await()
            }.onSuccess {
                written++
                if (r.tier == "possible") announce(p.lost, p.sighting, p.id, me)
            }.onFailure { android.util.Log.i("CatMap", "Match ${p.id} not saved: ${it.message}") }
        }
        return written
    }

    /** One inbox alert for the other person, with a fixed id so it's never sent twice. */
    private suspend fun announce(lost: Post, sighting: Post, id: String, me: UserProfile) {
        val params = mapOf(
            "name" to lost.name, "type" to lost.animalType, "area" to sighting.neighborhood, "lostArea" to lost.neighborhood, "matchId" to id,
        )
        if (lost.ownerId != me.uid) notifications.send(listOf(lost.ownerId), NotifType.MATCH_FOUND, params, "match/$id", dedupeKey = "pm_$id")
        if (sighting.ownerId != me.uid) notifications.send(listOf(sighting.ownerId), NotifType.MATCH_REPORTER, params, "match/$id", dedupeKey = "pr_$id")
    }

    // ---------- answers ----------

    /** The owner's answer: "maybe" (this might be my animal), "not_mine", or back to "new". */
    suspend fun setOwnerState(match: PostMatch, state: String) {
        col.document(match.id).update(mapOf("ownerState" to state, "reviewedAt" to now(), "updatedAt" to now())).await()
    }

    /** The reporter's answer about their own sighting: "confirmed" (same animal), "not_same", or back to "new". */
    suspend fun setReporterState(match: PostMatch, state: String) {
        col.document(match.id).update(mapOf("reporterState" to state, "updatedAt" to now())).await()
    }

    /** The reporter links their sighting to a lost report themselves (from the report's page). */
    suspend fun linkSighting(lost: Post, sighting: Post, me: UserProfile): String {
        val id = MatchEngine.matchId(lost.id, sighting.id)
        val ref = col.document(id)
        if (ref.get().await().exists()) {
            setReporterState(PostMatch(id = id), "confirmed")
            return id
        }
        val r = MatchEngine.deviceMatches(sighting, listOf(lost)).firstOrNull()?.result
            ?: MatchEngine.score(MatchEngine.side(lost), MatchEngine.side(sighting), "details")
        val now = now()
        ref.set(
            mapOf(
                "lostPostId" to lost.id, "sightingPostId" to sighting.id, "lostOwnerId" to lost.ownerId, "sightingOwnerId" to sighting.ownerId,
                "members" to listOf(lost.ownerId, sighting.ownerId), "animalType" to lost.animalType,
                "score" to r.score, "visual" to r.visual, "tier" to if (r.tier == "strong") "possible" else r.tier, "method" to r.method,
                "model" to if (r.method == "fingerprint") MatchEngine.MODEL_FINGERPRINT else MatchEngine.MODEL_DETAILS,
                "distanceKm" to r.distanceKm, "daysApart" to r.daysApart, "reasons" to r.reasons + "reporter_linked", "source" to "device",
                "ownerState" to "new", "reporterState" to "confirmed", "createdBy" to me.uid, "createdAt" to now, "updatedAt" to now, "notified" to true,
            ),
        ).await()
        announce(lost, sighting, id, me)
        return id
    }

    // ---------- talking about a match ----------

    /**
     * Opens the chat with the other person (one chat per pair of people, reused), adding a card with both photos
     * the first time this match is discussed there. Returns the chat id.
     */
    suspend fun openChat(match: PostMatch, lost: Post?, sighting: Post?, me: UserProfile): String {
        val other = if (me.uid == match.lostOwnerId) match.sightingOwnerId else match.lostOwnerId
        val chatId = chats.open(me, other)
        if (!chats.hasMatchCard(chatId, match.id)) {
            chats.sendMatchCard(chatId, me, match.id, lost, sighting)
        }
        return chatId
    }

    /** "Request more information": recorded on the match, the reporter is told, and the chat opens. */
    suspend fun requestInfo(match: PostMatch, lost: Post?, sighting: Post?, me: UserProfile): String {
        setOwnerState(match, "need_info")
        val chatId = openChat(match, lost, sighting, me)
        notifications.send(
            listOf(match.sightingOwnerId), NotifType.MATCH_INFO,
            mapOf("name" to lost?.name.orEmpty(), "type" to match.animalType, "user" to me.username), "chat/$chatId", dedupeKey = "mi_${match.id}",
        )
        return chatId
    }

    // ---------- reunion ----------

    /**
     * Marks a lost (or found) report as reunited, once: the report's status, time and how it ended, and the match
     * that helped, in one transaction; a second tap (or a second phone) changes nothing. The person whose sighting
     * helped is thanked. Returns false if it was already reunited.
     */
    suspend fun reunite(post: Post, match: PostMatch?, resolution: Resolution, me: UserProfile): Boolean {
        val ref = posts.ref(post.id)
        val credited = match?.takeIf { resolution == Resolution.CATMAP && it.lostPostId == post.id }
        val changed = db.runTransaction { tx ->
            val fresh = tx.get(ref)
            if (fresh.getString("status") == PostStatus.REUNITED.key) return@runTransaction false
            val at = now()
            tx.update(
                ref, mapOf(
                    "status" to PostStatus.REUNITED.key, "resolution" to resolution.key, "resolvedMatchId" to (credited?.id ?: ""),
                    "resolvedAt" to at, "updatedAt" to at, "lastActivityAt" to at,
                ),
            )
            if (credited != null) tx.update(col.document(credited.id), mapOf("ownerState" to "reunited", "reviewedAt" to at, "updatedAt" to at))
            true
        }.await()
        if (!changed) return false
        if (post.ownerId == me.uid) {
            runCatching { db.runBatch { b -> users.addStats(b, me.uid, mapOf(Stats.REUNITED to 1), points = 25, rateLimited = true) }.await() }
        }
        if (credited != null) {
            notifications.send(
                listOf(credited.sightingOwnerId), NotifType.MATCH_REUNITED,
                mapOf("name" to post.name, "type" to post.animalType), "post/${post.id}", dedupeKey = "ru_${credited.id}",
            )
        }
        return true
    }

    companion object {
        /** The phone writes at most this many suggestions per post (the PC has no such limit). */
        const val MAX_DEVICE_MATCHES = 5
        /** How many of the newest posts on the other side the phone compares (only while the PC is off). */
        const val CANDIDATES = 200L
    }
}
