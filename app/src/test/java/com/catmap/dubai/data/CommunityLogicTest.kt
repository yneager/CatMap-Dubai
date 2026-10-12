package com.catmap.dubai.data

import com.catmap.dubai.data.model.Post
import com.catmap.dubai.location.Fix
import com.catmap.dubai.ui.explore.matches
import com.catmap.dubai.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityLogicTest {
    private val now = 1_800_000_000_000L
    private fun post(id: String, extra: Post.() -> Unit = {}) = Post(
        id = id, animalType = "dog", category = "lost", status = "open", lat = 25.0697, lng = 55.1428,
        createdAt = now, keywords = PostRepository.keywords("Rex", "brown", "saluki", "red collar", "", "jlt"),
    ).apply(extra)

    @Test fun versionsCompareNumerically() {
        assertTrue(Updater.compare("2.10.0", "2.9.9") > 0)
        assertTrue(Updater.compare("2.0.1", "2.0.0") > 0)
        assertEquals(0, Updater.compare("2.0", "2.0.0"))
        assertTrue(Updater.compare("1.9.0", "2.0.0") < 0)
    }

    @Test fun keywordsIncludeBothLanguagesOfTheArea() {
        val words = PostRepository.keywords("Rex", "Brown", "", "", "", "jlt")
        assertTrue("rex" in words)
        assertTrue("brown" in words)
        assertTrue("jumeirah" in words)
        assertTrue(words.any { it.contains("جميرا") })
    }

    @Test fun nearbySimilarOpenPostIsAPossibleDuplicate() {
        val draft = post("")
        val similar = post("a")
        val otherAnimal = post("b") { animalType = "cat" }
        val far = post("c") { lat = 25.27; lng = 55.30 }
        val closed = post("d") { status = "reunited" }
        assertEquals(listOf("a"), PostRepository.likelyDuplicates(draft, listOf(similar, otherAnimal, far, closed)).map { it.id })
    }

    @Test fun unrelatedPostIsNotADuplicate() {
        val draft = post("")
        val unrelated = post("x") { keywords = listOf("kiwi", "parrot"); category = "found" }
        assertTrue(PostRepository.likelyDuplicates(draft, listOf(unrelated)).isEmpty())
    }

    @Test fun layerFiltersSelectCategories() {
        val lost = post("l")
        val adoption = post("a") { category = "adoption" }
        val f = FeedFilters(layers = setOf(MapLayer.ADOPTION))
        assertFalse(f.matches(lost, null, now))
        assertTrue(f.matches(adoption, null, now))
        assertTrue(FeedFilters().matches(lost, null, now))
    }

    @Test fun closedPostsOnlyShowWhenAsked() {
        val reunited = post("r") { status = "reunited" }
        assertFalse(FeedFilters().matches(reunited, null, now))
        assertTrue(FeedFilters().matches(reunited, null, now, includeClosed = true))
    }

    @Test fun distanceAgeAndRewardFiltersApply() {
        val here = Fix(25.0697, 55.1428, 5.0)
        val nearbyRecent = post("n") { reward = 200; lastSeenAt = now - 3_600_000 }
        val farOld = post("f") { lat = 25.27; lng = 55.30; lastSeenAt = now - 10L * 24 * 3_600_000 }
        val filters = FeedFilters(maxDistanceKm = 5, maxAgeDays = 3, rewardOnly = true)
        assertTrue(filters.matches(nearbyRecent, here, now))
        assertFalse(filters.matches(farOld, here, now))
        // Without a location, the distance filter can't apply, so it doesn't hide anything.
        assertTrue(FeedFilters(maxDistanceKm = 1).matches(farOld, null, now))
    }

    @Test fun animalTypeFilter() {
        val dog = post("d")
        assertFalse(FeedFilters(animalTypes = setOf("cat")).matches(dog, null, now))
        assertTrue(FeedFilters(animalTypes = setOf("dog", "cat")).matches(dog, null, now))
    }
}
