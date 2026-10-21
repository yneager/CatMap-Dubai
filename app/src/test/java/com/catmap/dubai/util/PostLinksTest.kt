package com.catmap.dubai.util

import org.junit.Assert.*
import org.junit.Test

class PostLinksTest {
    @Test fun validLinkRoundTrips() { assertEquals("abc-123_",PostLinks.parse(PostLinks.create("abc-123_"))) }
    @Test fun injectedOrMalformedLinksAreRejected() {
        listOf("catmap://post/../admin", "catmap://post/a%2Fb", "catmap://post/a?next=admin", "catmap://post/a#admin", "https://post/a", "catmap://evil@post/a", "catmap://post:80/a", "catmap://post/", "catmap://post/a/b").forEach { assertNull(it,PostLinks.parse(it)) }
    }
    @Test fun nullInputIsRejected() { assertNull(PostLinks.parse(null)) }
    @Test fun otherSchemesHostsAndOversizedIdsAreRejected() {
        listOf("", "catmap:post/a", "catmap://cat/a", "catmap://admin/a", "intent://post/a#Intent;scheme=catmap;end", "catmap://post/" + "a".repeat(129), "catmap://post/a b", " catmap://post/a").forEach { assertNull(it,PostLinks.parse(it)) }
        assertEquals("a".repeat(128), PostLinks.parse("catmap://post/" + "a".repeat(128)))
    }
}
