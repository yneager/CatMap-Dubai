package com.catmap.dubai.data

import android.graphics.Bitmap
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Client for the CatMap AI Server (tools/ai-server), a desktop app on the owner's PC running pet-recognition-base.
 * Nothing connects to the PC: the phone writes a job to Firestore `aiJobs/{uid}` (small JPEG crops), the PC polls for
 * pending jobs over outgoing HTTPS and writes the answer back, and the phone listens to that document. The PC's
 * heartbeat (`config/aiWorker`) lets the app skip the check at once when the PC is off. Every failure returns null so
 * the caller falls back to on-device matching.
 */
class AiServer(private val db: FirebaseFirestore, private val auth: FirebaseAuth) {
    data class Match(val id: String, val similarity: Double, val verdict: String)
    data class Health(val model: String, val provider: String, val same: Double, val maybe: Double, val lastSeen: Long) {
        override fun toString() = "$model · $provider"
    }

    /** The PC's status when it has checked in within the last 90 seconds, otherwise null (offline). */
    suspend fun health(): Health? = runCatching {
        val d = db.collection(Col.CONFIG).document("aiWorker").get().await()
        val seen = d.getTimestamp("lastSeen")?.toDate()?.time ?: return null
        if (d.getBoolean("online") != true || System.currentTimeMillis() - seen > ONLINE_WINDOW_MS) return null
        Health(d.getString("model").orEmpty(), d.getString("provider").orEmpty(), d.getDouble("same") ?: 0.75, d.getDouble("maybe") ?: 0.6, seen)
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
                "query" to Blob.fromBytes(jpeg(query)),
                "images" to candidates.take(MAX_CANDIDATES).map { Blob.fromBytes(jpeg(it.second)) },
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

    /** Embeddings (unit vectors) for up to 16 crops, for the Recognition test. Null on any failure. */
    suspend fun embed(crops: List<Bitmap>): List<List<Double>>? {
        val done = run(mapOf("kind" to "embed", "images" to crops.take(16).map { Blob.fromBytes(jpeg(it)) }, "ids" to emptyList<String>())) ?: return null
        @Suppress("UNCHECKED_CAST")
        val vectors = done.get("embeddings") as? List<Map<String, Any?>> ?: return null
        return vectors.map { m -> (m["v"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }.orEmpty() }
    }

    /** Writes the job and waits for the PC's answer to this request. */
    private suspend fun run(fields: Map<String, Any>): DocumentSnapshot? {
        val uid = auth.currentUser?.uid ?: return null
        val requestId = UUID.randomUUID().toString()
        val ref = db.collection(Col.AI_JOBS).document(uid)
        return runCatching {
            // The rules allow one new job a second per person; wait out a job that was just sent.
            val previous = runCatching { ref.get().await().getTimestamp("createdAt") }.getOrNull()
            previous?.let { val wait = 1_200 - (Timestamp.now().toDate().time - it.toDate().time); if (wait > 0) kotlinx.coroutines.delay(wait) }
            ref.set(fields + mapOf("uid" to uid, "status" to "pending", "requestId" to requestId, "createdAt" to FieldValue.serverTimestamp())).await()
            withTimeoutOrNull(TIMEOUT_MS) {
                callbackFlow {
                    val reg = ref.addSnapshotListener { snap, error ->
                        if (error != null) close(error) else if (snap != null) trySend(snap)
                    }
                    awaitClose { reg.remove() }
                }.first { it.getString("answeredRequestId") == requestId && it.getString("status") in setOf("done", "error") }
            }?.takeIf { it.getString("status") == "done" }
        }.onFailure { android.util.Log.i("CatMap", "AI server job failed: ${it.message}") }.getOrNull()
    }

    private fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()

    companion object {
        const val MAX_CANDIDATES = 8
        const val ONLINE_WINDOW_MS = 90_000L
        const val TIMEOUT_MS = 30_000L
    }
}
