package com.catmap.dubai.data

import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.Post

/** Whether one map cell has been asked for, when, and whether the answer held everything in it. */
data class CellLoad(val at: Long, val complete: Boolean)

/** The cells to ask for on the current screen, and how many posts (and cats) to ask each one for. */
data class AreaPlan(val cells: List<String>, val perCell: Long)

/**
 * What the map has loaded, as plain data: posts and cats by id, when each was last confirmed by the server, which
 * cells were asked for, and how far the "changed since" listeners got. [MapRepository] keeps one of these, saves it
 * on the phone and replaces it on every answer. Every change returns a new copy, so it is easy to test.
 */
data class MapArea(
    val posts: Map<String, Post> = emptyMap(),
    val cats: Map<String, Cat> = emptyMap(),
    /** "p:<id>" / "c:<id>" -> when the server last returned it (this phone's clock). */
    val seen: Map<String, Long> = emptyMap(),
    val cells: Map<String, CellLoad> = emptyMap(),
    /** Changes up to these times (minus [OVERLAP_MS]) are already in [posts] / [cats]; 0 = never synced. */
    val postsSince: Long = 0,
    val catsSince: Long = 0,
) {
    val visiblePosts: List<Post> get() = posts.values.filter { !it.hidden }
    val visibleCats: List<Cat> get() = cats.values.filter { !it.hidden && it.mergedInto.isEmpty() }

    /** A limited live query is a display window; its evictions never delete sources. */
    fun withPostWindow(cell: String, values: List<Post>, at: Long): MapArea {
        val ids = values.mapTo(HashSet()) { it.id }
        val kept = posts.filterValues { cell !in it.geoCells || it.id in ids }
        return copy(posts = kept, seen = seen.filterKeys { !it.startsWith("p:") || it.drop(2) in kept }).withPosts(values, at)
    }
    fun withCatWindow(cell: String, values: List<Cat>, at: Long): MapArea {
        val ids = values.mapTo(HashSet()) { it.id }
        val kept = cats.filterValues { cell !in it.geoCells || it.id in ids }
        return copy(cats = kept, seen = seen.filterKeys { !it.startsWith("c:") || it.drop(2) in kept }).withCats(values, at)
    }

    /** Cells of [plan] that need asking: not loaded recently, and not inside a recently and fully loaded bigger cell. */
    fun toLoad(cells: List<String>, now: Long): List<String> = cells.filter { cell ->
        val own = this.cells[cell]
        if (own != null && now - own.at < CELL_TTL_MS) return@filter false
        (Geohash.CELL_PRECISIONS.first() until cell.length).none { len ->
            val parent = this.cells[cell.take(len)]
            parent != null && parent.complete && now - parent.at < CELL_TTL_MS
        }
    }

    /**
     * The answer for one cell: [posts] are its newest visible posts (up to [postLimit]), [cats] its newest cats (up
     * to [catLimit], hidden ones included). What the cell held before and the server no longer returns was deleted
     * or hidden, so it goes; when an answer was cut at its limit, only what is at least as new as its oldest item
     * can be judged that way.
     */
    fun withCell(cell: String, posts: List<Post>, cats: List<Cat>, postLimit: Long, catLimit: Long, now: Long): MapArea {
        val postsComplete = posts.size < postLimit
        val catsComplete = cats.size < catLimit
        val postIds = posts.mapTo(HashSet()) { it.id }
        val catIds = cats.mapTo(HashSet()) { it.id }
        val oldestPost = posts.minOfOrNull { it.updatedAt } ?: Long.MIN_VALUE
        val oldestCat = cats.minOfOrNull { it.updatedAt } ?: Long.MIN_VALUE
        val keptPosts = this.posts.filterValues { p ->
            p.id in postIds || cell !in p.geoCells || (!postsComplete && p.updatedAt < oldestPost)
        }
        val keptCats = this.cats.filterValues { c ->
            c.id in catIds || cell !in c.geoCells || (!catsComplete && c.updatedAt < oldestCat)
        }
        return copy(posts = keptPosts, cats = keptCats, seen = seen.filterKeys { k -> if (k.startsWith("p:")) k.drop(2) in keptPosts else k.drop(2) in keptCats })
            .withPosts(posts, now).withCats(cats, now)
            .copy(cells = this.cells + (cell to CellLoad(now, postsComplete && catsComplete)))
    }

    /** Posts from a cell or the "changed since" listener; a copy older than the one already here is ignored. */
    fun withPosts(incoming: List<Post>, now: Long): MapArea {
        if (incoming.isEmpty()) return this
        val next = posts.toMutableMap()
        val stamps = seen.toMutableMap()
        for (p in incoming) {
            val old = next[p.id]
            if (old == null || p.updatedAt >= old.updatedAt) next[p.id] = p
            stamps["p:${p.id}"] = now
        }
        return copy(posts = next, seen = stamps)
    }

    /** Cats from a cell or the listener; hidden and merged profiles are dropped. */
    fun withCats(incoming: List<Cat>, now: Long): MapArea {
        if (incoming.isEmpty()) return this
        val next = cats.toMutableMap()
        val stamps = seen.toMutableMap()
        for (c in incoming) {
            val old = next[c.id]
            if (old != null && c.updatedAt < old.updatedAt) continue
            if (c.hidden || c.mergedInto.isNotEmpty()) {
                next.remove(c.id)
                stamps.remove("c:${c.id}")
            } else {
                next[c.id] = c
                stamps["c:${c.id}"] = now
            }
        }
        return copy(cats = next, seen = stamps)
    }

    /** Forgets what wasn't confirmed for a week and cells asked for long ago, so the saved copy can't grow forever. */
    fun pruned(now: Long): MapArea {
        val fresh = seen.filterValues { now - it < KEEP_MS }
        return copy(
            posts = posts.filterKeys { "p:$it" in fresh },
            cats = cats.filterKeys { "c:$it" in fresh },
            seen = fresh,
            cells = cells.filterValues { now - it.at < KEEP_MS },
        )
    }

    companion object {
        const val CELL_TTL_MS = 24L * 3600 * 1000
        const val KEEP_MS = 7L * 24 * 3600 * 1000
        /** How far back the "changed since" listeners look past the last sync, for phones with slow clocks. */
        const val OVERLAP_MS = 3600L * 1000
        const val MAX_CELLS = 6
        /** About this many posts (and as many cats) are asked for per screen, split between its cells. */
        const val SCREEN_BUDGET = 300L

        /** The finest precision that covers the box in at most [MAX_CELLS] cells. */
        fun plan(south: Double, west: Double, north: Double, east: Double): AreaPlan {
            for (precision in Geohash.CELL_PRECISIONS.reversed()) {
                val cells = Geohash.covering(south, west, north, east, precision, MAX_CELLS)
                if (cells.size <= MAX_CELLS) return AreaPlan(cells, perCell(cells.size))
            }
            // A steeply tilted camera can see very far: keep the coarse cells nearest the middle of the screen.
            val lat = (south + north) / 2
            val lng = (west + east) / 2
            val cells = Geohash.covering(south, west, north, east, Geohash.CELL_PRECISIONS.first(), 64)
                .sortedBy { c ->
                    val (h, w) = Geohash.cellSize(c.length)
                    val (s, westEdge) = Geohash.origin(c)
                    val dy = s + h / 2 - lat
                    val dx = westEdge + w / 2 - lng
                    dy * dy + dx * dx
                }
                .take(MAX_CELLS)
            return AreaPlan(cells, perCell(cells.size))
        }

        private fun perCell(cells: Int): Long = (SCREEN_BUDGET / cells.coerceAtLeast(1)).coerceIn(40, 150)
    }
}
