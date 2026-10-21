package com.catmap.dubai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same examples are in cat-server/tests/test_searchterms.py: the phone, the web app and the PC must agree. */
class SearchTermsTest {
    @Test
    fun wordsAndTheirFirstLetters() {
        assertEquals(
            listOf("simba", "orange", "tabby", "al", "barsha", "sim", "simb", "ora", "oran", "orang", "tab", "tabb", "bar", "bars", "barsh"),
            SearchTerms.of(listOf("simba", "orange", "tabby", "al", "barsha")),
        )
    }

    @Test
    fun wordsAreSplitLikeKeywords() {
        assertEquals(listOf("simba", "the", "king"), SearchTerms.words("Simba (the KING)", "simba", "a"))
        assertEquals(listOf("قطة", "برتقالية"), SearchTerms.words("قطة برتقالية"))
    }

    @Test
    fun middleWordsKeepOnlyTheWholeWord() {
        val t = SearchTerms.of((0 until 40).map { "w%02dxxxxxx".format(it) })
        assertTrue("w00xxxx" in t && "w39xxxx" in t && "w30xxxxxx" in t)
        assertFalse("w30xxx" in t)
    }

    @Test
    fun atMostTwoHundred() {
        assertEquals(200, SearchTerms.of((0 until 80).map { "%03dabcdefgh".format(it) }).size)
    }

    @Test
    fun catsUseNameAndAliases() {
        assertEquals(
            listOf("zafran", "ginger", "zaf", "zafr", "zafra", "gin", "ging", "ginge"),
            SearchTerms.of(SearchTerms.words("Zafran", "Ginger")),
        )
    }

    @Test
    fun aLongTypedWordAlsoAsksForItsStoredPrefix() {
        assertEquals(listOf("beautiful", "beautif"), SearchTerms.query("beautiful"))
        assertEquals(listOf("tab"), SearchTerms.query("tab"))
        assertTrue(SearchTerms.matches("tab", listOf("orange", "tabby")))
    }
}
