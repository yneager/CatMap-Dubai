package com.catmap.dubai.data

import android.app.Application
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only for org.json (the saved copy); the rest is plain logic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MapAreaTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L
    private val cell = "thrr3"

    private fun post(id: String, updatedAt: Long, cells: List<String> = listOf("thrr", cell, "${cell}s")) =
        Post(id = id, lat = 25.2, lng = 55.27, updatedAt = updatedAt, geoCells = cells, name = "P $id", keywords = listOf("tabby"))

    private fun cat(id: String, updatedAt: Long, hidden: Boolean = false, merged: String = "") =
        Cat(id = id, lat = 25.2, lng = 55.27, updatedAt = updatedAt, hidden = hidden, mergedInto = merged, geoCells = listOf("thrr", cell, "${cell}s"))

    @Test
    fun toLoadSkipsFreshCellsAndCompleteParents() {
        val a = MapArea(cells = mapOf("thrr" to CellLoad(now - hour, complete = true), "thrq5" to CellLoad(now - hour, false)))
        assertEquals(emptyList<String>(), a.toLoad(listOf("thrr3", "thrr3s"), now))
        assertEquals(emptyList<String>(), a.toLoad(listOf("thrq5"), now))
        // Inside an incomplete parent, or once the parent is a day old, the cell is asked for.
        val partial = MapArea(cells = mapOf("thrr" to CellLoad(now - hour, complete = false)))
        assertEquals(listOf("thrr3"), partial.toLoad(listOf("thrr3"), now))
        assertEquals(listOf("thrr3"), a.toLoad(listOf("thrr3"), now + MapArea.CELL_TTL_MS))
    }

    @Test
    fun aCompleteCellAnswerDropsWhatTheServerNoLongerReturns() {
        val a = MapArea().withPosts(listOf(post("kept", 10), post("gone", 20), post("other", 5, listOf("thrq", "thrq5", "thrq5x"))), now)
        val b = a.withCell(cell, listOf(post("kept", 10), post("new", 30)), emptyList(), postLimit = 50, catLimit = 50, now = now)
        assertEquals(setOf("kept", "new", "other"), b.posts.keys)
        assertTrue(b.cells.getValue(cell).complete)
        assertFalse("p:gone" in b.seen)
    }

    @Test
    fun aCutAnswerOnlyJudgesWhatIsAsNewAsItsOldestItem() {
        val a = MapArea().withPosts(listOf(post("older", 5), post("gone", 25), post("noCells", 30, emptyList())), now)
        val b = a.withCell(cell, listOf(post("x", 40), post("y", 20)), emptyList(), postLimit = 2, catLimit = 50, now = now)
        // "older" may simply not have fitted; "gone" is newer than the oldest answer, so it was deleted or hidden.
        assertEquals(setOf("older", "noCells", "x", "y"), b.posts.keys)
        assertFalse(b.cells.getValue(cell).complete)
    }

    @Test
    fun newerCopiesWinAndHiddenCatsLeave() {
        val a = MapArea().withPosts(listOf(post("p", 20)), now).withPosts(listOf(post("p", 10).apply { name = "stale" }), now)
        assertEquals("P p", a.posts.getValue("p").name)
        val c = MapArea().withCats(listOf(cat("c1", 10), cat("c2", 10)), now)
            .withCats(listOf(cat("c1", 11, hidden = true), cat("c2", 11, merged = "c9")), now)
        assertTrue(c.cats.isEmpty())
    }

    @Test
    fun prunedForgetsWhatWasNotConfirmedForAWeek() {
        val a = MapArea().withPosts(listOf(post("old", 1)), now - MapArea.KEEP_MS - 1).withPosts(listOf(post("new", 2)), now)
            .copy(cells = mapOf("thrr" to CellLoad(now - MapArea.KEEP_MS - 1, true)))
        val b = a.pruned(now)
        assertEquals(setOf("new"), b.posts.keys)
        assertTrue(b.cells.isEmpty())
    }

    @Test
    fun planKeepsTheScreenToFewCellsAndABudget() {
        // The default camera: zoom 11.2 over the centre of Dubai on a phone.
        val city = MapArea.plan(25.08, 55.20, 25.33, 55.34)
        assertTrue(city.cells.size in 1..MapArea.MAX_CELLS)
        assertTrue(city.perCell in 40L..150L)
        // A street-level view uses fine cells.
        val street = MapArea.plan(25.200, 55.270, 25.206, 55.274)
        assertTrue(street.cells.all { it.length == 6 })
        // A steep tilt can see the whole emirate: still a few cells.
        assertTrue(MapArea.plan(24.45, 54.60, 25.70, 55.95).cells.size <= MapArea.MAX_CELLS)
    }

    @Test
    fun savedCopyRoundTrips() {
        val a = MapArea(postsSince = 7, catsSince = 8, cells = mapOf(cell to CellLoad(now, false)))
            .withPosts(listOf(post("p", 20).apply { photoIds = listOf("a", "b"); reward = 50 }), now)
            .withCats(listOf(cat("c", 30).apply { name = "Simba"; coverPhotoId = "img" }), now)
        val b = MapAreaCodec.decode(MapAreaCodec.encode(a))!!
        assertEquals(7L, b.postsSince); assertEquals(8L, b.catsSince)
        assertEquals(a.cells, b.cells)
        assertEquals(a.seen, b.seen)
        val p = b.posts.getValue("p")
        assertEquals(listOf("a"), p.photoIds) // only the cover is kept
        assertEquals(50L, p.reward); assertEquals(listOf("tabby"), p.keywords); assertEquals(a.posts.getValue("p").geoCells, p.geoCells)
        assertEquals("Simba", b.cats.getValue("c").name)
    }

    @Test
    fun damagedOrOldCopiesAreIgnored() {
        assertNull(MapAreaCodec.decode("not json"))
        assertNull(MapAreaCodec.decode("""{"v":0}"""))
    }
}
