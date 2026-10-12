package com.catmap.dubai.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageDiskCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun storesAndRemovesJpegBytes() {
        val cache = ImageDiskCache(temporary.newFolder())
        cache.write("photo_t", byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), cache.read("photo_t"))
        cache.remove("photo_t")
        assertNull(cache.read("photo_t"))
    }
    @Test fun cacheSizeIsBoundedAndOldestImageIsEvicted() {
        var now = 1000L
        val cache = ImageDiskCache(temporary.newFolder(), maxBytes = 6, clock = { now })
        cache.write("old", ByteArray(4))
        now += 1000
        cache.write("new", ByteArray(4))
        assertNull(cache.read("old"))
        assertEquals(4, cache.read("new")?.size)
    }
    @Test fun expiredImageMustBeFetchedAgain() {
        var now = 1000L
        val cache = ImageDiskCache(temporary.newFolder(), maxAgeMillis = 100, clock = { now })
        cache.write("photo", byteArrayOf(1))
        now += 100
        assertNull(cache.read("photo"))
    }
    @Test fun pathTraversalCannotReadOrWriteOutsideCache() {
        val directory = temporary.newFolder()
        val cache = ImageDiskCache(directory)
        cache.write("../secret", byteArrayOf(1))
        assertNull(cache.read("../secret"))
        assertFalse(java.io.File(directory.parentFile, "secret").exists())
    }
    @Test fun oversizedAndEmptyValuesAreNotStored() {
        val cache = ImageDiskCache(temporary.newFolder(), maxBytes = 3)
        cache.write("big", ByteArray(4))
        cache.write("empty", byteArrayOf())
        assertNull(cache.read("big"))
        assertNull(cache.read("empty"))
    }
    @Test fun clearingCacheRemovesImages() {
        val cache = ImageDiskCache(temporary.newFolder())
        cache.write("photo", byteArrayOf(1))
        cache.clear()
        assertNull(cache.read("photo"))
    }
}
