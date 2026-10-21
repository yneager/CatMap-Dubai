package com.catmap.dubai.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** GitHub's "latest release" JSON, as the release workflow publishes it (org.json needs Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReleaseParsingTest {
    private val repo = "yneager/CatMap-Dubai"
    private val base = "https://github.com/$repo/releases/download"

    private fun asset(name: String, url: String, size: Long = 30_000_000) =
        JSONObject().put("name", name).put("browser_download_url", url).put("size", size)

    private fun release(tag: String = "v0.1.0", vararg assets: JSONObject = arrayOf(
        asset("CatMap-0.1.0.apk", "$base/v0.1.0/CatMap-0.1.0.apk"),
        asset("CatMap-0.1.0.apk.sha256", "$base/v0.1.0/CatMap-0.1.0.apk.sha256", 85),
    )) = JSONObject().put("tag_name", tag).put("name", "CatMap 0.1.0").put("body", "**Faster** map").put("assets", JSONArray(assets.toList()))

    @Test fun workflowReleaseIsAccepted() {
        val r = Updater.parseRelease(release(), repo)!!
        assertEquals("0.1.0", r.version)
        assertEquals("CatMap-0.1.0.apk", r.assetName)
        assertEquals("$base/v0.1.0/CatMap-0.1.0.apk.sha256", r.checksumUrl)
        assertEquals("Faster map", r.notes)
        assertEquals(30_000_000, r.sizeBytes)
    }

    @Test fun draftsPreReleasesAndOddTagsAreIgnored() {
        assertNull(Updater.parseRelease(release().put("draft", true), repo))
        assertNull(Updater.parseRelease(release().put("prerelease", true), repo))
        assertNull(Updater.parseRelease(release(tag = "0.1.0"), repo))
        assertNull(Updater.parseRelease(release(tag = "v0.1"), repo))
        assertNull(Updater.parseRelease(release(tag = "v0.1.0-rc1"), repo))
    }

    @Test fun apkFromAnotherPlaceOrWithAnotherNameIsIgnored() {
        assertNull(Updater.parseRelease(release("v0.1.0", asset("CatMap-0.1.0.apk", "https://evil.example/CatMap-0.1.0.apk")), repo))
        assertNull(Updater.parseRelease(release("v0.1.0", asset("CatMap-0.1.0.apk", "https://github.com/evil/x/releases/download/v0.1.0/CatMap-0.1.0.apk")), repo))
        assertNull(Updater.parseRelease(release("v0.1.0", asset("other.apk", "$base/v0.1.0/other.apk")), repo))
        assertNull(Updater.parseRelease(release("v0.1.0", asset("CatMap-0.1.0.apk", "$base/v0.1.0/CatMap-0.1.0.apk", UpdateChecks.MAX_APK_BYTES + 1)), repo))
        assertNull("no assets", Updater.parseRelease(release().put("assets", JSONArray()), repo))
    }

    @Test fun aChecksumFromElsewhereIsDropped() {
        val r = Updater.parseRelease(release("v0.1.0",
            asset("CatMap-0.1.0.apk", "$base/v0.1.0/CatMap-0.1.0.apk"),
            asset("CatMap-0.1.0.apk.sha256", "https://evil.example/CatMap-0.1.0.apk.sha256", 85),
        ), repo)!!
        assertNull(r.checksumUrl)
    }

    @Test fun oldReleaseTagFromV009StillParses() {
        // Updating from 0.0.9: the new app reads the same kind of release the old one did.
        val json = release("v0.0.9",
            asset("CatMap-0.0.9.apk", "$base/v0.0.9/CatMap-0.0.9.apk"),
            asset("CatMap-0.0.9.apk.sha256", "$base/v0.0.9/CatMap-0.0.9.apk.sha256", 85),
        )
        assertEquals("0.0.9", Updater.parseRelease(json, repo)?.version)
    }
}
