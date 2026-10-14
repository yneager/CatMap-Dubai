package com.catmap.dubai.ai

/**
 * What a post photo shows, for the "is this an animal?" check: from CLIP on the owner's PC ([byServer]), or from
 * ML Kit's on-device labels when the PC is offline ([PhotoCheck.fromLabels]). [phrase] names what was seen, with its
 * article ("a coffee cup"), or is empty when unsure; [animalType] is an AnimalType key for animals.
 */
data class PhotoVerdict(val kind: String, val animalType: String = "", val phrase: String = "", val byServer: Boolean = false) {
    val isAnimal: Boolean get() = kind == ANIMAL
    /** Posting can't continue with this photo: it clearly shows a person or an object, not an animal. */
    val blocks: Boolean get() = kind == PERSON || kind == OBJECT

    companion object {
        const val ANIMAL = "animal"
        const val PERSON = "person"
        const val OBJECT = "object"
        /** Couldn't tell (no labels, low confidence): the photo is allowed. */
        const val UNKNOWN = "unknown"
    }
}

object PhotoCheck {
    // ML Kit's base labeller (about 450 labels) has these animal labels; any of them is enough.
    private val animalLabels = mapOf(
        "Cat" to "cat", "Kitten" to "cat", "Dog" to "dog", "Puppy" to "dog", "Bird" to "bird", "Rabbit" to "rabbit",
        "Pet" to "", "Mammal" to "", "Whiskers" to "", "Fur" to "", "Horse" to "other", "Cattle" to "other",
        "Sheep" to "other", "Goat" to "other", "Camel" to "other", "Duck" to "bird", "Chicken" to "bird", "Penguin" to "bird",
        "Fish" to "other", "Turtle" to "other", "Reptile" to "other", "Lizard" to "other", "Snake" to "other",
        "Insect" to "other", "Butterfly" to "other", "Crab" to "other", "Squirrel" to "other", "Bear" to "other",
        "Monkey" to "other", "Hamster" to "other", "Poultry" to "bird",
    )
    private val personLabels = setOf("Selfie", "Smile", "Beard", "Moustache", "Eyelash", "Hair", "Skin", "Baby", "Child")
    /** An animal label at least this sure means "animal"; other labels need this much to block the photo. */
    private const val ANIMAL_MIN = 0.5f
    private const val BLOCK_MIN = 0.7f

    /**
     * The on-device fallback. It only blocks when ML Kit is fairly sure and saw no animal at all; anything unclear
     * is [PhotoVerdict.UNKNOWN] and allowed, because this labeller is much weaker than the PC's model.
     */
    fun fromLabels(labels: List<Pair<String, Float>>): PhotoVerdict {
        val animal = labels.filter { (text, conf) -> text in animalLabels && conf >= ANIMAL_MIN }
        if (animal.isNotEmpty()) {
            val type = animal.sortedByDescending { it.second }.firstNotNullOfOrNull { (text, _) -> animalLabels[text]?.ifEmpty { null } }.orEmpty()
            return PhotoVerdict(PhotoVerdict.ANIMAL, type, if (type.isEmpty()) "" else withArticle(type))
        }
        if (labels.any { (text, conf) -> text in personLabels && conf >= BLOCK_MIN }) {
            return PhotoVerdict(PhotoVerdict.PERSON, phrase = "a selfie or a photo of a person")
        }
        val top = labels.maxByOrNull { it.second } ?: return PhotoVerdict(PhotoVerdict.UNKNOWN)
        return if (top.second >= BLOCK_MIN) PhotoVerdict(PhotoVerdict.OBJECT, phrase = withArticle(top.first.lowercase()))
        else PhotoVerdict(PhotoVerdict.UNKNOWN)
    }

    fun withArticle(noun: String): String = (if (noun.firstOrNull()?.lowercaseChar()?.let { it in "aeiou" } == true) "an " else "a ") + noun
}
