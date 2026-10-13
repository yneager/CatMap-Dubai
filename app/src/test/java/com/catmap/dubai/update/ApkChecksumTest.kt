package com.catmap.dubai.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkChecksumTest {
    @get:Rule val files=TemporaryFolder()
    @Test fun matchingChecksumPassesAndTamperingFails() {
        val file=files.newFile().apply {writeText("APK fixture")}
        val hash=ApkChecksum.digest(file)
        assertTrue(ApkChecksum.verify(file,"$hash  CatMap-1.0.0.apk","CatMap-1.0.0.apk"))
        file.appendText("tampered")
        assertFalse(ApkChecksum.verify(file,hash,"CatMap-1.0.0.apk"))
    }
    @Test fun malformedHashesAndWrongAssetsAreRejected() {
        assertNull(ApkChecksum.parse("not a hash","app.apk"))
        assertNull(ApkChecksum.parse("a".repeat(64)+"  other.apk","app.apk"))
        assertNull(ApkChecksum.parse("a".repeat(64)+"  ../app.apk","app.apk"))
    }
}
