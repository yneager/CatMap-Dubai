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
    private val now = 1_800_000_000_000L
    private val cell = "thrr3"

    private fun post(id: String, updatedAt: Long, cells: List<String> = listOf("thrr", cell, "${cell}s")) =
        Post(id = id, lat = 25.2, lng = 55.27, updatedAt = updatedAt, geoCells = cells, name = "P $id", keywords = listOf("tabby"))

    private fun cat(id: String, updatedAt: Long, hidden: Boolean = false, merged: String = "") =
        Cat(id = id, lat = 25.2, lng = 55.27, updatedAt = updatedAt, hidden = hidden, mergedInto = merged, geoCells = listOf("thrr", cell, "${cell}s"))

    @Test
    fun liveWindowsRemoveLimitEvictionsAndHiddenRecordsFromDisplay() {
        val original = MapArea().withPosts(listOf(post("old", 1), post("kept", 2)), now)
        val refreshed = original.withPostWindow(cell, listOf(post("kept", 2), post("new", 3)), now + 1)
        assertEquals(setOf("kept", "new"), refreshed.posts.keys)
        assertFalse("p:old" in refreshed.seen)
        assertTrue(refreshed.withPostWindow(cell, emptyList(), now + 2).posts.isEmpty())
    }

    @Test
    fun catWindowsDropRemovedAndMergedPinsButPreserveOtherCells() {
        val elsewhere = cat("other", 1).copy(geoCells = listOf("thrq5"))
        val original = MapArea().withCats(listOf(cat("gone", 1), cat("merged", 1), elsewhere), now)
        val refreshed = original.withCatWindow(cell, listOf(cat("merged", 2, merged = "kept")), now + 1)
        assertEquals(setOf("other"), refreshed.cats.keys)
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
        val b = a.pruned(now)
        assertEquals(setOf("new"), b.posts.keys)
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
        val a = MapArea()
            .withPosts(listOf(post("p", 20).apply { photoIds = listOf("a", "b"); reward = 50 }), now)
            .withCats(listOf(cat("c", 30).apply { name = "Simba"; coverPhotoId = "img" }), now)
        val b = MapAreaCodec.decode(MapAreaCodec.encode(a))!!
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

    @Test
    fun copiesSavedBeforeTheCellFieldsWentStillLoad() {
        val old = """{"v":1,"postsSince":7,"catsSince":8,"cells":{"thrr3":[1,false]},"posts":[],"cats":[]}"""
        assertTrue(MapAreaCodec.decode(old)!!.posts.isEmpty())
    }
}
