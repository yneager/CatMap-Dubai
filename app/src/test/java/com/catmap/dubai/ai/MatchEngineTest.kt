package com.catmap.dubai.ai

import android.app.Application
import com.catmap.dubai.data.model.Post
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The phone's match rules must give the same answers as the PC's (cat-server/matching.py), which wrote
 * tools/matching/cases.json. Robolectric only provides org.json here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MatchEngineTest {
    private fun cases(): JSONObject {
        val file = listOf(File("../tools/matching/cases.json"), File("tools/matching/cases.json")).first { it.exists() }
        return JSONObject(file.readText())
    }

    private fun side(o: JSONObject) = MatchEngine.Side(
        o.getDouble("lat"), o.getDouble("lng"), o.getLong("time_ms"), o.getBoolean("approximate"), o.getLong("area_m"),
        o.getBoolean("has_photos"), o.getJSONArray("colour_words").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
    )

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    @Test fun thresholdsAreTheSameAsThePc() {
        val spec = cases()
        assertEquals(MatchEngine.SPEC_VERSION, spec.getInt("spec"))
        val embed = spec.getJSONObject("embed")
        for (animal in listOf("cat", "dog")) {
            val t = embed.getJSONArray(animal)
            assertEquals(Triple(t.getDouble(0), t.getDouble(1), t.getDouble(2)), MatchEngine.EMBED.getValue(animal))
        }
        assertEquals(spec.getJSONArray("print").getDouble(0), MatchEngine.PRINT_POSSIBLE, 0.0)
        assertEquals(spec.getJSONArray("print").getDouble(1), MatchEngine.PRINT_WEAK, 0.0)
    }

    @Test fun everySharedCaseGivesThePcsAnswer() {
        val list = cases().getJSONArray("cases")
        assertTrue(list.length() > 100)
        for (i in 0 until list.length()) {
            val c = list.getJSONObject(i)
            var r = MatchEngine.score(side(c.getJSONObject("lost")), side(c.getJSONObject("sighting")), c.getString("method"), c.getDouble("visual"), c.getString("animalType"))
            if (c.has("likeRejected")) r = MatchEngine.applyFeedback(r, c.getBoolean("likeRejected"), c.getBoolean("reporterRejected"))
            val e = c.getJSONObject("expected")
            val label = "case $i ${c.getString("animalType")} ${c.getString("method")} ${c.getDouble("visual")}"
            assertEquals(label, e.getString("tier"), r.tier)
            assertEquals(label, e.getString("method"), r.method)
            assertEquals(label, e.getDouble("score"), r.score, 0.0002)
            assertEquals(label, e.getDouble("distance_km"), r.distanceKm, 0.002)
            assertEquals(label, e.getDouble("days_apart"), r.daysApart, 0.01)
            assertEquals(label, e.getJSONArray("reasons").strings(), r.reasons)
        }
    }

    @Test fun phoneNeverClaimsStrongAndSkipsOwnAndClosedPosts() {
        val print = MutableList(PostAppearance.SIZE) { 0.5 }.apply { repeat(PostAppearance.HIST_BINS) { this[it] = if (it == 3) 1.0 else 0.0 } }
        val lost = Post(id = "L", ownerId = "a", category = "lost", animalType = "cat", lat = 25.2, lng = 55.27, createdAt = 1_000, fingerprint = print)
        val same = lost.copy(id = "S", ownerId = "b", category = "spotted")
        val feed = listOf(
            same, same.copy(id = "mine", ownerId = "a"), same.copy(id = "closed", status = "resolved"),
            same.copy(id = "dog", animalType = "dog"), same.copy(id = "hidden", hidden = true), lost.copy(id = "L2", ownerId = "c"),
        )
        val pairs = MatchEngine.deviceMatches(lost, feed)
        assertEquals(listOf("S"), pairs.map { it.sighting.id })
        assertEquals("possible", pairs.single().result.tier)  // identical colours, same place: still only "possible"
        assertEquals("L__S", pairs.single().id)
        // Both directions: a sighting finds the lost report too.
        assertEquals(listOf("L"), MatchEngine.deviceMatches(same, listOf(lost)).map { it.lost.id })
    }

    @Test fun placeAndTimeOnlyMoveOneStep() {
        val lost = MatchEngine.Side(25.2, 55.27, 0)
        val farAndBefore = MatchEngine.Side(25.2 + 60 / 111.2, 55.27, -10 * 86_400_000L)
        // A near-identical photo far away and before the loss drops two steps, but is still suggested.
        assertEquals("weak", MatchEngine.score(lost, farAndBefore, "embedding", 0.95, "cat").tier)
        assertEquals("strong", MatchEngine.score(lost, MatchEngine.Side(25.2, 55.27, 86_400_000L), "embedding", 0.95, "cat").tier)
    }
}
