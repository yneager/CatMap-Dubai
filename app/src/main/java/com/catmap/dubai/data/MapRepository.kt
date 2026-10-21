package com.catmap.dubai.data

import android.content.Context
import android.util.Log
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The map's posts and cats (Phase 4 of docs/done/MAJOR_UPDATE_PLAN.md). Instead of downloading the newest 400 posts and
 * 800 cats on every opening, the map
 * * shows the copy saved on the phone at once,
 * * listens only for what changed since the last sync (which also keeps it live), and
 * * asks for the cells on screen that it hasn't loaded yet, after the camera stops moving.
 * The rules of what to keep are in [MapArea]; this class does the Firestore and file work.
 */
@OptIn(FlowPreview::class)
class MapRepository(db: FirebaseFirestore, context: Context, private val scope: CoroutineScope) {
    private val posts = db.collection(Col.POSTS)
    private val cats = db.collection(Col.CATS)
    private val feedFallback = PostFallback(db)
    private val dir = File(context.filesDir, "map")
    private val area = MutableStateFlow(MapArea())
    private var owner: String? = null
    private val viewport = MutableStateFlow<Box?>(null)
    private val inFlight = HashSet<String>()
    private val parallel = Semaphore(3)
    private var shared: Pair<String, SharedFlow<MapArea>>? = null
    /** Set when an area query fails for a reason other than the network (for example a missing index). */
    @Volatile private var useFallback = false

    data class Box(val south: Double, val west: Double, val north: Double, val east: Double)

    /** The map's data for [uid], kept while the map is open and for a few seconds after. */
    @Synchronized
    fun area(uid: String): Flow<MapArea> =
        shared?.takeIf { it.first == uid }?.second
            ?: sync(uid).shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1).also { shared = uid to it }

    /** Called when the camera stops: the visible box. Loading waits until it stops changing. */
    fun show(south: Double, west: Double, north: Double, east: Double) {
        viewport.value = Box(
            south.coerceIn(Dubai.SOUTH - 0.3, Dubai.NORTH + 0.3), west.coerceIn(Dubai.WEST - 0.3, Dubai.EAST + 0.3),
            north.coerceIn(Dubai.SOUTH - 0.3, Dubai.NORTH + 0.3), east.coerceIn(Dubai.WEST - 0.3, Dubai.EAST + 0.3),
        )
    }

    /** Makes sure one post is in the map's data (another screen asked the map to show it). */
    suspend fun include(postId: String): Post? {
        area.value.posts[postId]?.let { return it }
        val post = runCatching { posts.document(postId).get().await().safeObject<Post>() }.getOrNull() ?: return null
        if (!post.hidden) area.update { it.withPosts(listOf(post), now()) }
        return post
    }

    /** Deletes every saved copy (sign-out, account deletion). */
    fun clearAll() {
        synchronized(this) {
            owner = null
            area.value = MapArea()
        }
        runCatching { dir.deleteRecursively() }
    }

    private fun sync(uid: String): Flow<MapArea> = channelFlow {
        withContext(Dispatchers.IO) { open(uid) }
        val start = area.value
        val at = now()
        launch { listen(changes(posts.whereEqualTo("hidden", false), start.postsSince, at), isPosts = true) }
        launch { listen(changes(cats, start.catsSince, at), isPosts = false) }
        launch { viewport.filterNotNull().debounce(400).collectLatest { box -> load(box) } }
        launch { area.drop(1).debounce(3_000).collect { a -> withContext(Dispatchers.IO) { save(uid, a) } } }
        launch { area.collect { send(it) } }
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { save(uid, area.value) }
        }
    }

    private fun changes(base: Query, since: Long, at: Long): Query =
        base.whereGreaterThan("updatedAt", (if (since > 0) since else at) - MapArea.OVERLAP_MS)
            .orderBy("updatedAt", Query.Direction.DESCENDING).limit(CHANGES_LIMIT)

    /** "Changed since" listener: also the map's live updates while it is open. */
    private suspend fun listen(query: Query, isPosts: Boolean) {
        var first = true
        runCatching {
            query.snapshotFlow(metadata = true).collect { snap ->
                val fromServer = !snap.metadata.isFromCache
                val at = now()
                if (isPosts) {
                    val list = snap.safeObjects<Post>()
                    area.update { a -> a.withPosts(list, at).let { if (fromServer) it.copy(postsSince = at) else it } }
                    if (fromServer && first && list.size >= CHANGES_LIMIT) area.update { it.withStaleCells() }
                } else {
                    val list = snap.safeObjects<Cat>()
                    area.update { a -> a.withCats(list, at).let { if (fromServer) it.copy(catsSince = at) else it } }
                    if (fromServer && first && list.size >= CHANGES_LIMIT) area.update { it.withStaleCells() }
                }
                if (fromServer) first = false
            }
        }.onFailure { e ->
            when {
                e is CancellationException -> throw e
                // The index isn't built yet: the map still loads by area (or the 0.0.9 way), just without live changes.
                (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                    Log.w("CatMap", "Map changes listener needs its index", e)
                else -> AppErrors.report(e)
            }
        }
    }

    private suspend fun load(box: Box) {
        if (useFallback) return loadFallback()
        val plan = MapArea.plan(box.south, box.west, box.north, box.east)
        val todo = synchronized(inFlight) {
            area.value.toLoad(plan.cells, now()).filter { inFlight.add(it) }
        }
        for (cell in todo) {
            // Each cell is its own job: once asked, its answer is kept even if the camera has moved on.
            scope.launch {
                try {
                    parallel.withPermit { loadCell(cell, plan.perCell) }
                } finally {
                    synchronized(inFlight) { inFlight.remove(cell) }
                }
            }
        }
    }

    private suspend fun loadCell(cell: String, limit: Long) {
        runCatching {
            val p = posts.whereEqualTo("hidden", false).whereArrayContains("geoCells", cell)
                .orderBy("updatedAt", Query.Direction.DESCENDING).limit(limit).get().await().safeObjects<Post>()
            val c = cats.whereArrayContains("geoCells", cell)
                .orderBy("updatedAt", Query.Direction.DESCENDING).limit(limit).get().await().safeObjects<Cat>()
            area.update { it.withCell(cell, p, c, limit, limit, now()) }
        }.onFailure { e ->
            Log.w("CatMap", "Map cell $cell failed", e)
            val code = (e as? FirebaseFirestoreException)?.code
            if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION || code == FirebaseFirestoreException.Code.INVALID_ARGUMENT) {
                // The area index isn't built yet (rules and indexes deployed after the app): load the map as 0.0.9 did.
                useFallback = true
                loadFallback()
            }
        }
    }

    private var fallbackLoaded = false

    private suspend fun loadFallback() {
        if (fallbackLoaded) return
        fallbackLoaded = true
        runCatching {
            val at = now()
            val p = feedFallback.posts()
            val c = feedFallback.cats()
            area.update { it.withPosts(p, at).withCats(c, at) }
        }.onFailure { fallbackLoaded = false; AppErrors.report(it) }
    }

    // ---------- the copy saved on the phone ----------

    private fun file(uid: String) = File(dir, uid.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    private fun open(uid: String) {
        synchronized(this) { if (owner == uid) return }
        val saved = runCatching { file(uid).takeIf { it.exists() }?.readText()?.let(MapAreaCodec::decode) }.getOrNull()
        synchronized(this) {
            owner = uid
            area.value = (saved ?: MapArea()).pruned(now())
        }
    }

    private fun save(uid: String, a: MapArea) {
        if (owner != uid) return
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, file(uid).name + ".tmp")
            tmp.writeText(MapAreaCodec.encode(a))
            if (!tmp.renameTo(file(uid))) { file(uid).delete(); tmp.renameTo(file(uid)) }
        }.onFailure { Log.w("CatMap", "Couldn't save the map copy", it) }
    }

    /** What 0.0.9 loaded, used only while the area index is missing. */
    private class PostFallback(db: FirebaseFirestore) {
        private val posts = db.collection(Col.POSTS)
        private val cats = db.collection(Col.CATS)
        suspend fun posts(): List<Post> =
            posts.whereEqualTo("hidden", false).orderBy("createdAt", Query.Direction.DESCENDING).limit(400).get().await().safeObjects()
        suspend fun cats(): List<Cat> =
            cats.orderBy("lastSeen", Query.Direction.DESCENDING).limit(800).get().await().safeObjects()
    }

    companion object {
        const val CHANGES_LIMIT = 300L
    }
}

/**
 * The saved copy: only what a map pin, its preview card and the map's search need, so the file stays small. Posts
 * and cats read back from it are partial objects; screens that open one load the full document.
 */
object MapAreaCodec {
    private const val VERSION = 1

    fun encode(a: MapArea): String = JSONObject().apply {
        put("v", VERSION)
        put("postsSince", a.postsSince)
        put("catsSince", a.catsSince)
        put("cells", JSONObject().apply { a.cells.forEach { (k, c) -> put(k, JSONArray().put(c.at).put(c.complete)) } })
        put("posts", JSONArray().apply {
            a.posts.values.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("seen", a.seen["p:${p.id}"] ?: 0L)
                    put("animalType", p.animalType); put("category", p.category); put("status", p.status)
                    put("name", p.name); put("photoIds", JSONArray(p.photoIds.take(1)))
                    put("lat", p.lat); put("lng", p.lng); put("neighborhood", p.neighborhood)
                    put("lastSeenAt", p.lastSeenAt); put("reward", p.reward); put("adoptionStatus", p.adoptionStatus)
                    put("keywords", JSONArray(p.keywords)); put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
                    put("resolvedAt", p.resolvedAt); put("lastActivityAt", p.lastActivityAt); put("microchip", p.microchip)
                    put("geoCells", JSONArray(p.geoCells))
                })
            }
        })
        put("cats", JSONArray().apply {
            a.cats.values.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("seen", a.seen["c:${c.id}"] ?: 0L)
                    put("name", c.name); put("coatColor", c.coatColor); put("coverPhotoId", c.coverPhotoId)
                    put("lat", c.lat); put("lng", c.lng); put("neighborhood", c.neighborhood); put("lastSeen", c.lastSeen)
                    put("healthStatus", c.healthStatus); put("updatedAt", c.updatedAt); put("geoCells", JSONArray(c.geoCells))
                })
            }
        })
    }.toString()

    /** Null for a file from another version or a damaged one; the map then starts empty. */
    fun decode(text: String): MapArea? = runCatching {
        val o = JSONObject(text)
        if (o.optInt("v") != VERSION) return null
        val seen = HashMap<String, Long>()
        val cellsJson = o.getJSONObject("cells")
        val cells = cellsJson.keys().asSequence().associateWith { k ->
            val c = cellsJson.getJSONArray(k)
            CellLoad(c.getLong(0), c.getBoolean(1))
        }
        val posts = o.getJSONArray("posts").objects().map { j ->
            Post(
                id = j.getString("id"), animalType = j.getString("animalType"), category = j.getString("category"),
                status = j.getString("status"), name = j.getString("name"), photoIds = j.getJSONArray("photoIds").strings(),
                lat = j.getDouble("lat"), lng = j.getDouble("lng"), neighborhood = j.getString("neighborhood"),
                lastSeenAt = j.getLong("lastSeenAt"), reward = j.getLong("reward"), adoptionStatus = j.getString("adoptionStatus"),
                keywords = j.getJSONArray("keywords").strings(), createdAt = j.getLong("createdAt"), updatedAt = j.getLong("updatedAt"),
                resolvedAt = j.getLong("resolvedAt"), lastActivityAt = j.getLong("lastActivityAt"), microchip = j.getString("microchip"),
                geoCells = j.getJSONArray("geoCells").strings(),
            ).also { seen["p:${it.id}"] = j.getLong("seen") }
        }
        val cats = o.getJSONArray("cats").objects().map { j ->
            Cat(
                id = j.getString("id"), name = j.getString("name"), coatColor = j.getString("coatColor"),
                coverPhotoId = j.getString("coverPhotoId"), lat = j.getDouble("lat"), lng = j.getDouble("lng"),
                neighborhood = j.getString("neighborhood"), lastSeen = j.getLong("lastSeen"),
                healthStatus = j.getString("healthStatus"), updatedAt = j.getLong("updatedAt"),
                geoCells = j.getJSONArray("geoCells").strings(),
            ).also { seen["c:${it.id}"] = j.getLong("seen") }
        }
        MapArea(
            posts = posts.associateBy { it.id }, cats = cats.associateBy { it.id }, seen = seen, cells = cells,
            postsSince = o.getLong("postsSince"), catsSince = o.getLong("catsSince"),
        )
    }.getOrNull()

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
