package com.catmap.dubai.data

import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.Post

/** The cells to ask for on the current screen, and how many posts (and cats) to ask each one for. */
data class AreaPlan(val cells: List<String>, val perCell: Long)

/**
 * What the map has loaded, as plain data: posts and cats by id, and when each was last confirmed by the server.
 * [MapRepository] keeps one of these, saves it on the phone and replaces it on every answer. Every change returns a
 * new copy, so it is easy to test.
 */
data class MapArea(
    val posts: Map<String, Post> = emptyMap(),
    val cats: Map<String, Cat> = emptyMap(),
    /** "p:<id>" / "c:<id>" -> when the server last returned it (this phone's clock). */
    val seen: Map<String, Long> = emptyMap(),
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

    /** Posts from a cell window or a single read; a copy older than the one already here is ignored. */
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

    /** Cats from a cell window; hidden and merged profiles are dropped. */
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

    /** Forgets what wasn't confirmed for a week, so the saved copy can't grow forever. */
    fun pruned(now: Long): MapArea {
        val fresh = seen.filterValues { now - it < KEEP_MS }
        return copy(
            posts = posts.filterKeys { "p:$it" in fresh },
            cats = cats.filterKeys { "c:$it" in fresh },
            seen = fresh,
        )
    }

    companion object {
        const val KEEP_MS = 7L * 24 * 3600 * 1000
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
