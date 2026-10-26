package com.catmap.dubai.data

import android.graphics.Bitmap
import com.catmap.dubai.ai.PhotoVerdict
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Client for the CatMap AI Server (Desktop\cat-server, kept outside this repo), a desktop app on the owner's PC running pet-recognition-base.
 * Nothing connects to the PC and nothing polls: the phone writes a job to Firestore `aiJobs/{uid}` (small JPEG crops),
 * Firestore pushes it to the PC's live listener, the PC writes the answer back, and the phone's own listener on that
 * document receives it. `config/aiWorker` (set when the PC app opens and closes, refreshed every 5 minutes) lets the
 * app skip the check at once when the PC is off. Every failure returns null so the caller falls back to on-device
 * matching.
 */
class AiServer(private val db: FirebaseFirestore, private val auth: FirebaseAuth) {
    data class Match(val id: String, val similarity: Double, val verdict: String)
    data class Health(
        val model: String, val provider: String, val same: Double, val maybe: Double, val lastSeen: Long,
        /** Job kinds beyond compare/check/embed that this PC answers, e.g. [REVIEW_PROTOCOL]. */
        val capabilities: List<String> = emptyList(),
    ) {
        override fun toString() = "$model · $provider"
    }

    /**
     * Photo review (docs/MATCHING_REVIEW_V1.md): cats in the order the PC ranked them, best first, with the photo ids
     * it compared. There is deliberately no score or verdict: the model isn't reliable enough to say "same cat", so the
     * order is only something to look through, and the person decides.
     */
    data class ReviewResult(val ranked: List<ReviewedCat>, val unavailable: List<Unavailable>)
    data class ReviewedCat(val id: String, val photoIds: List<String>)
    /** A cat the PC couldn't compare: too_few_photos, same_photo_as_query, photo_unreadable, hidden or missing. */
    data class Unavailable(val id: String, val reason: String)

    /** One job at a time per phone: `aiJobs/{uid}` holds a single job, and a new one would replace a pending one. */
    private val jobLock = Mutex()

    /** The PC's status when it is online and has checked in recently, otherwise null (offline). */
    suspend fun health(): Health? = runCatching {
        val d = db.collection(Col.CONFIG).document("aiWorker").get().await()
        val seen = d.getTimestamp("lastSeen")?.toDate()?.time ?: return null
        if (d.getBoolean("online") != true || System.currentTimeMillis() - seen > ONLINE_WINDOW_MS) return null
        @Suppress("UNCHECKED_CAST")
        val capabilities = (d.get("capabilities") as? List<Any?>).orEmpty().filterIsInstance<String>()
        Health(d.getString("model").orEmpty(), d.getString("provider").orEmpty(), d.getDouble("same") ?: 0.75, d.getDouble("maybe") ?: 0.6, seen, capabilities)
    }.getOrNull()

    /** When the PC last checked in (for the admin status screen), or null if it never has. */
    suspend fun lastSeen(): Long? = runCatching {
        db.collection(Col.CONFIG).document("aiWorker").get().await().getTimestamp("lastSeen")?.toDate()?.time
    }.getOrNull()

    /**
     * Compares [query] (a crop of the new animal) with each candidate crop; [labels] are names shown in the PC's window.
     * Null when the PC is offline, too slow or refuses.
     */
    suspend fun compare(query: Bitmap, candidates: List<Pair<String, Bitmap>>, labels: List<String> = emptyList()): List<Match>? {
        if (health() == null) return null
        val done = run(
            mapOf(
                "kind" to "compare",
                "query" to Blob.fromBytes(cropJpeg(query)),
                "images" to candidates.take(MAX_CANDIDATES).map { Blob.fromBytes(cropJpeg(it.second)) },
                "ids" to candidates.take(MAX_CANDIDATES).map { it.first },
                "labels" to labels.take(MAX_CANDIDATES).map { it.take(60) },
            ),
        ) ?: return null
        @Suppress("UNCHECKED_CAST")
        val results = done.get("results") as? List<Map<String, Any?>> ?: return null
        return results.mapNotNull { r ->
            val id = r["id"] as? String ?: return@mapNotNull null
            Match(id, (r["similarity"] as? Number)?.toDouble() ?: 0.0, r["verdict"] as? String ?: "different")
        }
    }

    /**
     * A new post's photos in one job: what each photo shows ([PhotoVerdict], from CLIP on the PC) and, when
     * [query] and [candidates] are given (street animals), how alike the first photo is to each nearby post.
     * Null when the PC is offline, too slow or refuses. [CheckResult.photos] is null when the PC answered but is
     * an older version without the photo check.
     */
    suspend fun check(photos: List<Bitmap>, query: Bitmap?, candidates: List<Pair<String, Bitmap>>, labels: List<String>): CheckResult? {
        if (health() == null) return null
        val compare = query != null && candidates.isNotEmpty()
        val fields = mutableMapOf<String, Any>(
            "kind" to "check",
            "photos" to photos.take(MAX_PHOTOS).map { Blob.fromBytes(jpeg(com.catmap.dubai.util.ImageUtils.scaleToMax(it, CHECK_PX))) },
            "images" to if (compare) candidates.take(MAX_CANDIDATES).map { Blob.fromBytes(cropJpeg(it.second)) } else emptyList(),
            "ids" to if (compare) candidates.take(MAX_CANDIDATES).map { it.first } else emptyList(),
            "labels" to if (compare) labels.take(MAX_CANDIDATES).map { it.take(60) } else emptyList(),
        )
        if (compare) fields["query"] = Blob.fromBytes(cropJpeg(query))
        val done = run(fields) ?: return null
        @Suppress("UNCHECKED_CAST")
        val verdicts = (done.get("classification") as? List<Map<String, Any?>>)?.map { c ->
            PhotoVerdict(
                kind = c["kind"] as? String ?: PhotoVerdict.UNKNOWN,
                animalType = c["animalType"] as? String ?: "",
                phrase = c["phrase"] as? String ?: "",
                byServer = true,
            )
        }?.takeIf { it.size == photos.take(MAX_PHOTOS).size }
        @Suppress("UNCHECKED_CAST")
        val matches = (done.get("results") as? List<Map<String, Any?>>).orEmpty().mapNotNull { r ->
            val id = r["id"] as? String ?: return@mapNotNull null
            Match(id, (r["similarity"] as? Number)?.toDouble() ?: 0.0, r["verdict"] as? String ?: "different")
        }
        return CheckResult(verdicts, matches, compare)
    }

    data class CheckResult(val photos: List<PhotoVerdict>?, val matches: List<Match>, val compared: Boolean)

    /**
     * Photo review of up to [MAX_REVIEW_CATS] cat profiles for [query] (the photo's 360 px thumbnail, the same bytes the
     * app uploads as `{id}_t`). The PC reads the cats' photos itself. Null when the PC is off, doesn't offer photo
     * review, is too slow or refuses: that means "not reviewed", never "no match".
     */
    suspend fun review(query: ByteArray, catIds: List<String>, labels: List<String>): ReviewResult? {
        if (catIds.isEmpty() || catIds.size > MAX_REVIEW_CATS || query.size > MAX_QUERY_BYTES) return null
        if (health()?.capabilities?.contains(REVIEW_PROTOCOL) != true) return null
        val done = run(
            mapOf(
                "kind" to "review",
                "query" to Blob.fromBytes(query),
                "images" to emptyList<Blob>(),
                "photos" to emptyList<Blob>(),
                "ids" to catIds,
                "labels" to labels.take(MAX_REVIEW_CATS).map { it.take(60) },
            ),
            REVIEW_TIMEOUT_MS,
        ) ?: return null
        return parseReview(done.get("review"), catIds)
    }

    /** Embeddings (unit vectors) for up to 16 crops, for the Recognition test. Null on any failure. */
    suspend fun embed(crops: List<Bitmap>): List<List<Double>>? {
        val done = run(mapOf("kind" to "embed", "images" to crops.take(16).map { Blob.fromBytes(cropJpeg(it)) }, "ids" to emptyList<String>())) ?: return null
        @Suppress("UNCHECKED_CAST")
        val vectors = done.get("embeddings") as? List<Map<String, Any?>> ?: return null
        return vectors.map { m -> (m["v"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }.orEmpty() }
    }

    /** Writes the job and waits for the PC's answer to this request. */
    private suspend fun run(fields: Map<String, Any>, timeoutMs: Long = TIMEOUT_MS): DocumentSnapshot? = jobLock.withLock {
        val uid = auth.currentUser?.uid ?: return@withLock null
        val requestId = UUID.randomUUID().toString()
        val ref = db.collection(Col.AI_JOBS).document(uid)
        runCatching {
            // The rules allow one new job a second per person; wait out a job that was just sent.
            val previous = runCatching { ref.get().await().getTimestamp("createdAt") }.getOrNull()
            previous?.let { val wait = 1_200 - (Timestamp.now().toDate().time - it.toDate().time); if (wait > 0) kotlinx.coroutines.delay(wait) }
            ref.set(fields + mapOf("uid" to uid, "status" to "pending", "requestId" to requestId, "createdAt" to FieldValue.serverTimestamp())).await()
            withTimeoutOrNull(timeoutMs) {
                callbackFlow {
                    val reg = ref.addSnapshotListener { snap, error ->
                        if (error != null) close(error) else if (snap != null) trySend(snap)
                    }
                    awaitClose { reg.remove() }
                }.first { it.getString("answeredRequestId") == requestId && it.getString("status") in setOf("done", "error") }
            }?.takeIf { it.getString("status") == "done" }
        }.also {
            // The answer is in hand (or never came): remove the job, whose photos would otherwise stay stored in Firestore
            // (up to about 1 MB per person), and so the next job's "previous job" read finds nothing to download.
            runCatching { ref.delete().await() }
        }.onFailure { android.util.Log.i("CatMap", "AI server job failed: ${it.message}") }.getOrNull()
    }

    /**
     * An animal crop for the recognition model, which looks at 224 × 224 pixels: 320 px on the long side keeps the
     * short side at 224 or more for most crops, and keeps a job with 9 crops far below Firestore's 1 MB per document.
     */
    private fun cropJpeg(bitmap: Bitmap): ByteArray = jpeg(com.catmap.dubai.util.ImageUtils.scaleToMax(bitmap, COMPARE_PX))

    private fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()

    companion object {
        const val MAX_CANDIDATES = 8
        /** Photos per check job (the rules allow 6, like a post), sent at this size: CLIP looks at 224 px. */
        const val MAX_PHOTOS = 6
        const val CHECK_PX = 320
        const val COMPARE_PX = 320
        /** The PC refreshes its status every 5 minutes; a little longer than that means it crashed. */
        const val ONLINE_WINDOW_MS = 390_000L
        const val TIMEOUT_MS = 15_000L
        const val REVIEW_PROTOCOL = "review-v1"
        const val MAX_REVIEW_CATS = 8
        /** The rules' cap on a job's photo. */
        const val MAX_QUERY_BYTES = 150_000
        /** A PC without a GPU took about 8 s for 33 photos in the research; reading the photos adds a little. */
        const val REVIEW_TIMEOUT_MS = 25_000L

        /** The PC's `review` answer, or null if it isn't one; only cats that were asked about are kept. */
        fun parseReview(value: Any?, asked: List<String>): ReviewResult? {
            val map = value as? Map<*, *> ?: return null
            if (map["protocol"] != REVIEW_PROTOCOL) return null
            val ranked = (map["ranked"] as? List<*>).orEmpty().mapNotNull { r ->
                val m = r as? Map<*, *> ?: return@mapNotNull null
                val id = (m["id"] as? String)?.takeIf { it in asked } ?: return@mapNotNull null
                ReviewedCat(id, (m["photoIds"] as? List<*>).orEmpty().filterIsInstance<String>().take(4))
            }.distinctBy { it.id }.filter { it.photoIds.size >= 2 }
            val unavailable = (map["unavailable"] as? List<*>).orEmpty().mapNotNull { u ->
                val m = u as? Map<*, *> ?: return@mapNotNull null
                val id = (m["id"] as? String)?.takeIf { it in asked } ?: return@mapNotNull null
                Unavailable(id, m["reason"] as? String ?: "")
            }
            return ReviewResult(ranked, unavailable)
        }
    }
}
