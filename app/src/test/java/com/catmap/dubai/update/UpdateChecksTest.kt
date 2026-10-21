package com.catmap.dubai.update

import org.junit.Assert.*
import org.junit.Test

class UpdateChecksTest {
    private val repo = "yneager/CatMap-Dubai"
    private fun url(v: String) = "https://github.com/$repo/releases/download/v$v/CatMap-$v.apk"

    @Test fun onlyGitHubHostsOverHttpsAreAllowed() {
        listOf(
            "https://github.com/$repo/releases/download/v1.0.0/CatMap-1.0.0.apk",
            "https://api.github.com/repos/$repo/releases/latest",
            "https://objects.githubusercontent.com/github-production-release-asset/1?x=y",
            "https://release-assets.githubusercontent.com/github-production-release-asset/1",
        ).forEach { assertTrue(it, UpdateChecks.allowedUrl(it)) }
        listOf(
            null, "", "not a url",
            "http://github.com/$repo/releases/download/v1.0.0/CatMap-1.0.0.apk",
            "https://github.com.evil.example/a.apk",
            "https://evilgithub.com/a.apk",
            "https://githubusercontent.com.evil.example/a.apk",
            "https://user@github.com/a.apk",
            "https://github.com:8443/a.apk",
            "ftp://github.com/a.apk",
            "file:///sdcard/a.apk",
        ).forEach { assertFalse(it.toString(), UpdateChecks.allowedUrl(it)) }
    }

    @Test fun apkAssetMustBeTheWorkflowsFileFromThisRepository() {
        assertTrue(UpdateChecks.apkAssetOk(repo, "1.2.3", "CatMap-1.2.3.apk", url("1.2.3"), 30_000_000))
        assertTrue("owner/repo case", UpdateChecks.apkAssetOk(repo, "1.2.3", "CatMap-1.2.3.apk", url("1.2.3").replace("yneager", "YNeager"), 30_000_000))
        assertFalse("other repo", UpdateChecks.apkAssetOk(repo, "1.2.3", "CatMap-1.2.3.apk", "https://github.com/evil/CatMap-Dubai/releases/download/v1.2.3/CatMap-1.2.3.apk", 30_000_000))
        assertFalse("other name", UpdateChecks.apkAssetOk(repo, "1.2.3", "app.apk", url("1.2.3"), 30_000_000))
        assertFalse("other version's file", UpdateChecks.apkAssetOk(repo, "1.2.3", "CatMap-1.2.3.apk", url("1.2.4"), 30_000_000))
        assertFalse("too big", UpdateChecks.apkAssetOk(repo, "1.2.3", "CatMap-1.2.3.apk", url("1.2.3"), UpdateChecks.MAX_APK_BYTES + 1))
        assertFalse("empty", UpdateChecks.apkAssetOk(repo, "1.2.3", "CatMap-1.2.3.apk", url("1.2.3"), 0))
        assertFalse("bad version", UpdateChecks.apkAssetOk(repo, "1.2", "CatMap-1.2.apk", url("1.2"), 30_000_000))
    }

    @Test fun versionsAreThreeShortNumbers() {
        listOf("0.0.9", "0.1.0", "12.34.5678").forEach { assertTrue(it, UpdateChecks.validVersion(it)) }
        listOf("", "1.2", "1.2.3.4", "v1.2.3", "1.2.3-beta", "1.2.99999", "1.2.3\n", "１.２.３").forEach { assertFalse(it, UpdateChecks.validVersion(it)) }
    }

    @Test fun releaseNotesBecomePlainText() {
        val notes = UpdateChecks.plainNotes("## What's new\n\n\n\n**Map** loads by area <script>alert(1)</script>\n* `fast`\u0007\n")
        assertEquals("What's new\n\nMap loads by area alert(1)\n* fast", notes)
        assertEquals(UpdateChecks.MAX_NOTES_CHARS, UpdateChecks.plainNotes("a".repeat(10_000)).length)
        assertEquals("a < b and c > d", UpdateChecks.plainNotes("a < b and c > d"))
    }

    @Test fun onlyTheSameAppWithTheSameCertificateAndAHigherVersionPasses() {
        val certs = setOf("aa11")
        fun check(pkg: String? = "com.catmap.dubai", name: String? = "0.1.0", code: Long = 100, apkCerts: Set<String> = certs) =
            UpdateChecks.packageProblem("com.catmap.dubai", 9, certs, pkg, name, code, apkCerts, "0.1.0")
        assertNull(check())
        assertEquals("wrong package", check(pkg = "com.catmap.dubai.staging"))
        assertEquals("wrong package", check(pkg = null))
        assertEquals("wrong version", check(name = "0.1.1"))
        assertEquals("not newer", check(code = 9))
        assertEquals("not newer", check(code = 1))
        assertEquals("different signing certificate", check(apkCerts = setOf("bb22")))
        assertEquals("different signing certificate", check(apkCerts = setOf("aa11", "bb22")))
        assertEquals("unsigned", check(apkCerts = emptySet()))
        assertEquals("unsigned", UpdateChecks.packageProblem("com.catmap.dubai", 9, emptySet(), "com.catmap.dubai", "0.1.0", 100, emptySet(), "0.1.0"))
    }
}
