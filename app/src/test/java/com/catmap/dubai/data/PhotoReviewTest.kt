package com.catmap.dubai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The PC's photo review answer (tools/worker/review.py), read defensively: docs/MATCHING_REVIEW_V1.md. */
class PhotoReviewTest {
    private val asked = listOf("tom", "ash", "solo")

    @Test
    fun keepsThePcOrderAndThePhotosItCompared() {
        val result = AiServer.parseReview(
            mapOf(
                "protocol" to "review-v1",
                "space" to "native-train-whitened-raw/9719870ec99a37304d634212",
                "ranked" to listOf(
                    mapOf("id" to "ash", "score" to 0.31, "photoIds" to listOf("a1", "a2", "a3")),
                    mapOf("id" to "tom", "score" to -0.02, "photoIds" to listOf("t1", "t2")),
                ),
                "unavailable" to listOf(mapOf("id" to "solo", "reason" to "too_few_photos")),
            ),
            asked,
        )!!
        assertEquals(listOf("ash", "tom"), result.ranked.map { it.id })
        assertEquals(listOf("a1", "a2", "a3"), result.ranked[0].photoIds)
        assertEquals(listOf(AiServer.Unavailable("solo", "too_few_photos")), result.unavailable)
    }

    @Test
    fun anOlderPcOrAnotherProtocolIsNotAReview() {
        assertNull(AiServer.parseReview(null, asked))
        assertNull(AiServer.parseReview(mapOf("ranked" to emptyList<Any>()), asked))
        assertNull(AiServer.parseReview(mapOf("protocol" to "review-v2", "ranked" to emptyList<Any>()), asked))
    }

    @Test
    fun dropsCatsThatWereNotAskedAboutRepeatsAndBanksWithOnePhoto() {
        val result = AiServer.parseReview(
            mapOf(
                "protocol" to "review-v1",
                "ranked" to listOf(
                    mapOf("id" to "stranger", "photoIds" to listOf("x1", "x2")),
                    mapOf("id" to "tom", "photoIds" to listOf("t1", "t2", "t3", "t4", "t5")),
                    mapOf("id" to "tom", "photoIds" to listOf("t1", "t2")),
                    mapOf("id" to "ash", "photoIds" to listOf("a1")),
                    "not a map",
                ),
                "unavailable" to listOf(mapOf("id" to "stranger", "reason" to "hidden")),
            ),
            asked,
        )!!
        assertEquals(listOf("tom"), result.ranked.map { it.id })
        assertEquals(4, result.ranked[0].photoIds.size)
        assertEquals(emptyList<AiServer.Unavailable>(), result.unavailable)
    }
}
