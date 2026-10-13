package com.catmap.dubai.data

import org.junit.Assert.*
import org.junit.Test

class PostDraftCodecTest {
    @Test fun roundTripPreservesFieldsAndPhotoOrder() {
        val draft=SavedPostDraft(mapOf("name" to "Amber", "description" to "Line one\nLine two", "radius" to "1500.0"),listOf("local:abc-123","remote:photo1"))
        assertEquals(draft,PostDraftCodec.decode(PostDraftCodec.encode(draft)))
    }
    @Test fun corruptOrUnknownVersionIsRejected() {
        assertNull(PostDraftCodec.decode("not a draft"))
        assertNull(PostDraftCodec.decode("AAAAAgAAAAA="))
    }
    @Test fun unsafePhotoPathsAreRejected() {
        assertNull(PostDraftCodec.decode(PostDraftCodec.encode(SavedPostDraft(emptyMap(),listOf("local:../outside")))))
    }
}
