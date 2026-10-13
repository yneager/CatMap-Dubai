package com.catmap.dubai.ai

import com.catmap.dubai.data.model.Post
import org.junit.Assert.*
import org.junit.Test

class PostAppearanceTest {
    private fun fingerprint(bin:Int)=MutableList(PostAppearance.SIZE) { 0.5 }.apply { repeat(PostAppearance.HIST_BINS) { this[it]=if(it==bin) 1.0 else 0.0 } }
    @Test fun photoSimilarityIsBoundedAndRejectsMalformedVectors() {
        assertEquals(1.0,PostAppearance.similarity(fingerprint(1),fingerprint(1)),0.000001)
        assertTrue(PostAppearance.similarity(fingerprint(1),fingerprint(2))<1)
        assertFalse(PostAppearance.valid(List(PostAppearance.SIZE) { Double.NaN }))
        assertEquals(0.0,PostAppearance.similarity(emptyList(),fingerprint(1)),0.0)
    }
    @Test fun matchesRespectAnimalCategoryVisibilityAndPhotoRanking() {
        val lost=Post(id="lost",category="lost",lat=25.2,lng=55.2,createdAt=100,fingerprint=fingerprint(1))
        val found=lost.copy(id="found",category="found")
        val other=found.copy(id="other",fingerprint=fingerprint(2))
        val matches=PostAppearance.matches(lost,listOf(other,found,found.copy(id="hidden",hidden=true),found.copy(id="dog",animalType="dog")))
        assertEquals(listOf("found","other"),matches.map { it.post.id })
    }
    @Test fun averageDropsMalformedDescriptors() { assertEquals(fingerprint(1),PostAppearance.average(listOf(fingerprint(1),emptyList()))) }
}
