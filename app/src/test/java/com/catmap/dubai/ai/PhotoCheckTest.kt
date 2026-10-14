package com.catmap.dubai.ai

import com.catmap.dubai.ui.posts.PostStep
import com.catmap.dubai.ui.posts.restoredStep
import org.junit.Assert.*
import org.junit.Test

class PhotoCheckTest {
    @Test fun anAnimalLabelWinsAndGivesTheType() {
        val v = PhotoCheck.fromLabels(listOf("Cup" to 0.9f, "Cat" to 0.8f))
        assertTrue(v.isAnimal)
        assertEquals("cat", v.animalType)
        assertFalse(v.blocks)
        assertTrue(PhotoCheck.fromLabels(listOf("Pet" to 0.7f)).isAnimal)
    }

    @Test fun aSureObjectOrPersonBlocksWithAName() {
        val cup = PhotoCheck.fromLabels(listOf("Cup" to 0.92f, "Table" to 0.6f))
        assertEquals(PhotoVerdict.OBJECT, cup.kind)
        assertEquals("a cup", cup.phrase)
        assertTrue(cup.blocks)
        assertEquals("an umbrella", PhotoCheck.fromLabels(listOf("Umbrella" to 0.8f)).phrase)
        val selfie = PhotoCheck.fromLabels(listOf("Selfie" to 0.85f, "Smile" to 0.8f))
        assertEquals(PhotoVerdict.PERSON, selfie.kind)
        assertTrue(selfie.blocks)
    }

    @Test fun anythingUnclearIsAllowed() {
        // The on-device labeller is weak: no labels, or only unsure ones, never stop a post.
        assertEquals(PhotoVerdict.UNKNOWN, PhotoCheck.fromLabels(emptyList()).kind)
        assertFalse(PhotoCheck.fromLabels(listOf("Cup" to 0.55f)).blocks)
        assertFalse(PhotoCheck.fromLabels(listOf("Cat" to 0.3f, "Grass" to 0.6f)).blocks)
        assertFalse(PhotoVerdict(PhotoVerdict.UNKNOWN).blocks)
    }

    @Test fun draftsFromOlderVersionsReopenOnTheSameStep() {
        // 0.0.7 saved the step's position in TYPE, PHOTOS, DETAILS, PLACE, CONTACT, REVIEW.
        assertEquals(PostStep.PHOTOS, restoredStep(mapOf("step" to "1")))
        assertEquals(PostStep.DETAILS, restoredStep(mapOf("step" to "2")))
        assertEquals(PostStep.PLACE, restoredStep(mapOf("step" to "3")))
        assertEquals(PostStep.REVIEW, restoredStep(mapOf("step" to "5")))
        // 0.0.8 saves the name, which wins.
        assertEquals(PostStep.PLACE, restoredStep(mapOf("step" to "1", "stepName" to "PLACE")))
        assertEquals(PostStep.TYPE, restoredStep(mapOf("step" to "99")))
        assertEquals(PostStep.TYPE, restoredStep(emptyMap()))
    }
}
