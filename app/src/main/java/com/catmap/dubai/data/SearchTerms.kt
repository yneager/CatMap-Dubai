package com.catmap.dubai.data

import java.util.Locale

/**
 * The `searchTerms` field of posts and cats, so a search can ask Firestore for every match instead of filtering what
 * the phone happens to have. Firestore can only find whole list entries, so each word is stored together with its
 * first few letters ("tab", "tabb", "tabby"). The same code is in `cat-server/searchterms.py` (the PC worker and the
 * backfill fill it in for older posts); keep them identical.
 */
object SearchTerms {
    const val PREFIX_MIN = 3
    const val PREFIX_MAX = 7
    /** Prefixes are stored for the first and last words only: names, colours and breeds come first, area names last. */
    const val HEAD = 25
    const val TAIL = 6
    const val MAX = 200
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    /** Lower-case words of at least two letters, in order, without repeats (as in a post's `keywords`). */
    fun words(vararg texts: String): List<String> =
        texts.flatMap { it.lowercase(Locale.ROOT).split(SPLIT) }.filter { it.length >= 2 }.distinct().take(80)

    /** The stored terms for these words. */
    fun of(words: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        words.forEach { out += it }
        words.forEachIndexed { i, w ->
            if (i < HEAD || i >= words.size - TAIL) {
                for (k in PREFIX_MIN..minOf(w.length - 1, PREFIX_MAX)) out += w.take(k)
            }
        }
        return out.take(MAX)
    }

    /** What to ask for one typed word: the word itself and, for a long word, its stored prefix. */
    fun query(word: String): List<String> = listOf(word, word.take(PREFIX_MAX)).distinct()

    /** Whether a typed word matches one of a document's words (the check for the other words typed). */
    fun matches(word: String, words: List<String>): Boolean = words.any { it.startsWith(word) }
}
