package com.catmap.dubai.data

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Client for the CatMap AI server (tools/ai-server), which runs pet-recognition-base on the owner's PC.
 * By default the phone reaches it over USB at http://127.0.0.1:8765 (`adb reverse tcp:8765 tcp:8765`);
 * an admin can point the app elsewhere in Admin > AI server (Firestore config/ai). Every call carries
 * the user's Firebase ID token, and any failure returns null so the app falls back to on-device matching.
 */
class AiServer(private val db: FirebaseFirestore, private val auth: FirebaseAuth) {
    data class Match(val id: String, val similarity: Double, val verdict: String)
    data class Health(val model: String, val provider: String, val same: Double, val maybe: Double) {
        override fun toString() = "$model · $provider"
    }

    private var cachedUrl: String? = null
    private var cachedAt = 0L

    /** The configured server URL, or the USB default when none is set. */
    suspend fun url(): String {
        if (System.currentTimeMillis() - cachedAt < 60_000) cachedUrl?.let { return it }
        val configured = runCatching { db.collection(Col.CONFIG).document("ai").get().await().getString("url") }.getOrNull()
        return (configured?.trim()?.takeIf { it.startsWith("http") } ?: DEFAULT_URL).trimEnd('/').also {
            cachedUrl = it
            cachedAt = System.currentTimeMillis()
        }
    }

    /** Admin only (rules): saves the URL; blank restores the USB default. */
    suspend fun setUrl(url: String, me: com.catmap.dubai.data.model.UserProfile) {
        db.collection(Col.CONFIG).document("ai")
            .set(mapOf("url" to url.trim().trimEnd('/'), "updatedBy" to me.uid, "updatedAt" to now())).await()
        cachedAt = 0
    }

    /** The server's model and verdict thresholds, or null when it can't be reached. */
    suspend fun health(url: String? = null): Health? {
        val base = url?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: url()
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = (URL("$base/health").openConnection() as HttpURLConnection).apply { connectTimeout = 3_000; readTimeout = 5_000 }
                try {
                    if (c.responseCode != 200) return@runCatching null
                    val j = JSONObject(c.inputStream.bufferedReader().readText())
                    Health(j.optString("model"), j.optString("provider"), j.optDouble("same", 0.75), j.optDouble("maybe", 0.6))
                } finally { c.disconnect() }
            }.getOrNull()
        }
    }

    /** Embeddings (unit vectors) for up to 16 crops, for the Recognition test. Null on any failure. */
    suspend fun embed(crops: List<Bitmap>): List<List<Double>>? {
        val token = runCatching { auth.currentUser?.getIdToken(false)?.await()?.token }.getOrNull() ?: return null
        val base = url()
        return withContext(Dispatchers.IO) {
            runCatching {
                val boundary = "catmap-" + UUID.randomUUID()
                val c = (URL("$base/embed").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3_000; readTimeout = 60_000; requestMethod = "POST"; doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }
                try {
                    DataOutputStream(c.outputStream).use { out ->
                        crops.forEachIndexed { i, b ->
                            out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"images\"; filename=\"i$i.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                            out.write(jpeg(b))
                            out.writeBytes("\r\n")
                        }
                        out.writeBytes("--$boundary--\r\n")
                    }
                    if (c.responseCode != 200) return@runCatching null
                    val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("embeddings")
                    List(arr.length()) { i -> arr.getJSONArray(i).let { v -> List(v.length()) { k -> v.getDouble(k) } } }
                } finally { c.disconnect() }
            }.getOrNull()
        }
    }

    /**
     * Compares [query] (a crop of the new animal) with each candidate crop. Returns null when the server
     * is unreachable or refuses, so the caller can fall back.
     */
    suspend fun compare(query: Bitmap, candidates: List<Pair<String, Bitmap>>): List<Match>? {
        val token = runCatching { auth.currentUser?.getIdToken(false)?.await()?.token }.getOrNull() ?: return null
        val base = url()
        return withContext(Dispatchers.IO) {
            runCatching {
                val boundary = "catmap-" + UUID.randomUUID()
                val c = (URL("$base/compare").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3_000
                    readTimeout = 30_000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }
                try {
                    DataOutputStream(c.outputStream).use { out ->
                        fun part(name: String, filename: String?, type: String?, body: ByteArray) {
                            out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"")
                            if (filename != null) out.writeBytes("; filename=\"$filename\"")
                            out.writeBytes("\r\n")
                            if (type != null) out.writeBytes("Content-Type: $type\r\n")
                            out.writeBytes("\r\n")
                            out.write(body)
                            out.writeBytes("\r\n")
                        }
                        part("ids", null, null, JSONArray(candidates.map { it.first }).toString().toByteArray())
                        part("query", "query.jpg", "image/jpeg", jpeg(query))
                        candidates.forEachIndexed { i, (_, bitmap) -> part("candidates", "c$i.jpg", "image/jpeg", jpeg(bitmap)) }
                        out.writeBytes("--$boundary--\r\n")
                    }
                    if (c.responseCode != 200) {
                        android.util.Log.w("CatMap", "AI server answered ${c.responseCode}")
                        return@runCatching null
                    }
                    val results = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("results")
                    List(results.length()) { i ->
                        val r = results.getJSONObject(i)
                        Match(r.getString("id"), r.getDouble("similarity"), r.getString("verdict"))
                    }
                } finally { c.disconnect() }
            }.onFailure { android.util.Log.i("CatMap", "AI server unavailable: ${it.message}") }.getOrNull()
        }
    }

    private fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8765"
    }
}
