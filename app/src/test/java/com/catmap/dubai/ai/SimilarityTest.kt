package com.catmap.dubai.ai

import org.junit.Assert.*
import org.junit.Test

class SimilarityTest {
    private fun descriptor() = MutableList(CatAnalyzer.FEATURE_SIZE) { 0.0 }.apply {
        this[0] = 1.0
        repeat(CatAnalyzer.LAYOUT_SIZE) { this[CatAnalyzer.HIST_BINS + it] = 0.5 }
        this[CatAnalyzer.HIST_BINS + CatAnalyzer.LAYOUT_SIZE] = 1.0
        this[lastIndex] = 1.0
    }
    private fun score(a: List<Double>, b: List<Double>, distance: Double? = null) =
        Similarity.score(a, "orange", "tabby", b, "orange", "tabby", distance)

    @Test fun identicalDescriptorsHaveMaximumVisualSimilarity() {
        val (colour, texture, layout) = Similarity.visual(descriptor(), descriptor())
        assertEquals(1.0, colour, 0.000001)
        assertEquals(1.0, texture, 0.000001)
        assertEquals(1.0, layout, 0.000001)
    }
    @Test fun distinctCoatsReduceSimilarity() {
        val a = descriptor()
        val b = descriptor().apply { this[0] = 0.0; this[1] = 1.0; this[lastIndex] = 0.0; this[lastIndex - 1] = 1.0 }
        assertTrue(score(a, a).total > score(a, b).total)
    }
    @Test fun nearerCatHasHigherScore() {
        assertTrue(score(descriptor(), descriptor(), 0.1).total > score(descriptor(), descriptor(), 5.0).total)
    }
    @Test fun malformedAndNonFiniteDescriptorsAreRejected() {
        assertEquals(Triple(0.0, 0.0, 0.0), Similarity.visual(emptyList(), descriptor()))
        assertEquals(Triple(0.0, 0.0, 0.0), Similarity.visual(descriptor().apply { this[3] = Double.NaN }, descriptor()))
    }
    @Test fun invalidDistanceDoesNotProduceInvalidConfidence() {
        for (d in listOf(Double.NaN, Double.POSITIVE_INFINITY, -2.0)) assertTrue(score(descriptor(), descriptor(), d).total in 0.0..1.0)
    }
    @Test fun averagingIgnoresInvalidPhotos() {
        val a = descriptor()
        assertEquals(a, Similarity.average(listOf(a, emptyList(), a.toMutableList().apply { this[0] = Double.NaN })))
        assertTrue(Similarity.average(listOf(emptyList())).isEmpty())
    }
    @Test fun colourClassificationAndHistogramBoundaries() {
        assertEquals(ColorFamily.BLACK, CatAnalyzer.classify(floatArrayOf(0f, 0f, 0.1f)))
        assertEquals(ColorFamily.WHITE, CatAnalyzer.classify(floatArrayOf(0f, 0f, 1f)))
        assertEquals(ColorFamily.ORANGE, CatAnalyzer.classify(floatArrayOf(30f, 0.8f, 0.8f)))
        assertNull(CatAnalyzer.classify(floatArrayOf(120f, 1f, 1f)))
        for (h in listOf(0f, 30f, 359f, 360f)) assertTrue(CatAnalyzer.histBin(floatArrayOf(h, 1f, 1f)) in 0 until CatAnalyzer.HIST_BINS)
    }
}
