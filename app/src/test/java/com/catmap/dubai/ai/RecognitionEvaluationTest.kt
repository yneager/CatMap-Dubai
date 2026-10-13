package com.catmap.dubai.ai

import org.junit.Assert.*
import org.junit.Test

class RecognitionEvaluationTest {
    /** A descriptor whose colour histogram peaks at [bin]; different bins look like different cats. */
    private fun descriptor(bin: Int, noise: Double = 0.0) = MutableList(CatAnalyzer.FEATURE_SIZE) { 0.0 }.apply {
        this[bin] = 1.0 - noise
        this[(bin + 1) % CatAnalyzer.HIST_BINS] = noise
        repeat(CatAnalyzer.LAYOUT_SIZE) { this[CatAnalyzer.HIST_BINS + it] = 0.2 + bin * 0.01 }
        this[CatAnalyzer.HIST_BINS + CatAnalyzer.LAYOUT_SIZE + bin % 8] = 1.0
        this[lastIndex - bin % 6] = 1.0
    }
    private fun sample(cat: String, bin: Int, noise: Double = 0.0) =
        RecognitionEvaluation.Sample(cat, "$cat-$noise.jpg", descriptor(bin, noise), "orange", "tabby", 10)

    @Test fun distinctCatsAreIdentified() {
        val samples = listOf(0, 10, 20, 30).flatMap { bin -> listOf(sample("cat$bin", bin), sample("cat$bin", bin, 0.1)) }
        val r = RecognitionEvaluation.evaluate(samples)!!
        assertEquals(4, r.identities)
        assertEquals(8, r.probes)
        assertEquals(1.0, r.top1, 1e-9)
        assertEquals(1.0, r.top3, 1e-9)
        assertTrue(r.genuineMean > r.impostorMean)
        assertEquals(8, r.perProbe.size)
    }

    @Test fun lookAlikesAreNotReportedAsStrong() {
        // Two cats with the same descriptor: every unseen-cat probe meets a perfect look-alike.
        val samples = listOf(sample("a", 5), sample("a", 5, 0.05), sample("b", 5), sample("b", 5, 0.05))
        val r = RecognitionEvaluation.evaluate(samples)!!
        assertEquals(1.0, r.unseenAnySuggestion, 1e-9)
        assertTrue(r.top1 <= 1.0)
    }

    @Test fun needsTwoCatsAndARepeatedCat() {
        assertNull(RecognitionEvaluation.evaluate(listOf(sample("a", 1), sample("a", 1, 0.1))))
        assertNull(RecognitionEvaluation.evaluate(listOf(sample("a", 1), sample("b", 2))))
    }

    @Test fun unreadablePhotosAreSkippedAndCounted() {
        val samples = listOf(sample("a", 1), sample("a", 1, 0.1), sample("b", 20), sample("b", 20, 0.1),
            RecognitionEvaluation.Sample("b", "broken.jpg", emptyList(), "", "", 0))
        val r = RecognitionEvaluation.evaluate(samples)!!
        assertEquals(1, r.skippedPhotos)
        assertEquals(4, r.photos)
        assertTrue(RecognitionEvaluation.report(r, "test").contains("top-1"))
    }

    @Test fun proximityForgivesLocationUncertaintyAndTime() {
        val near = Similarity.proximity(0.3, uncertaintyKm = 0.4)!!
        assertEquals(1.0, near, 1e-9)
        assertTrue(Similarity.proximity(3.0)!! < Similarity.proximity(3.0, daysApart = 60.0)!!)
        assertNull(Similarity.proximity(Double.NaN))
        // Location only ever supports: a far but identical-looking cat still scores above the match threshold.
        val d = descriptor(3)
        assertTrue(Similarity.score(d, "orange", "tabby", d, "orange", "tabby", 25.0).total > 0.8)
    }

    @Test fun strengthNeedsAClearLead() {
        assertEquals(Similarity.Strength.STRONG, Similarity.strength(0.9, 0.7))
        assertEquals(Similarity.Strength.POSSIBLE, Similarity.strength(0.9, 0.88))
        assertEquals(Similarity.Strength.STRONG, Similarity.strength(0.85, null))
        assertEquals(Similarity.Strength.WEAK, Similarity.strength(0.5, null))
    }
}
