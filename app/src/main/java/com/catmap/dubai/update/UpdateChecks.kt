package com.catmap.dubai.update

import java.net.URI

/**
 * The rules a downloaded update must pass, kept free of Android so they're unit-tested (UpdateChecksTest).
 * The `.sha256` file is only a quick corruption check: it comes from the same release as the APK, so whoever could
 * replace one could replace both. What makes an update trustworthy is [packageProblem]: same package, same signing
 * certificate and a higher version than the installed app (Android's installer checks the signature again).
 */
object UpdateChecks {
    /** Far above today's APK (about 30 MB); a bigger download is refused rather than filling the phone. */
    const val MAX_APK_BYTES = 150L * 1024 * 1024
    /** GitHub's release JSON and the checksum file are small. */
    const val MAX_TEXT_BYTES = 1L * 1024 * 1024
    const val MAX_NOTES_CHARS = 4_000

    private val version = Regex("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}")

    fun validVersion(v: String): Boolean = version.matches(v)

    /** GitHub's own hosts only: the API, release pages and the storage hosts downloads redirect to. HTTPS, no port. */
    fun allowedUrl(value: String?): Boolean = runCatching {
        val uri = URI(value ?: return false)
        val host = uri.host?.lowercase() ?: return false
        uri.scheme == "https" && uri.port == -1 && uri.rawUserInfo == null &&
            (host == "github.com" || host == "api.github.com" || host.endsWith(".githubusercontent.com"))
    }.getOrDefault(false)

    /** The APK asset the release workflow attaches: exactly `CatMap-<version>.apk` from this repository's release. */
    fun apkAssetOk(repo: String, version: String, name: String, url: String, size: Long): Boolean =
        validVersion(version) && name == "CatMap-$version.apk" && size in 1..MAX_APK_BYTES && allowedUrl(url) &&
            url.equals("https://github.com/$repo/releases/download/v$version/$name", ignoreCase = true)

    /** Release notes as plain text: no HTML tags, Markdown markers or control characters, and a length cap. */
    fun plainNotes(text: String): String = text
        .replace(Regex("</?[A-Za-z][^<>]{0,200}>"), "")
        .replace("**", "").replace("__", "").replace("`", "")
        .lines().joinToString("\n") { it.replace(Regex("^#{1,6}\\s+"), "") }
        .filter { it == '\n' || it == '\t' || !it.isISOControl() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .take(MAX_NOTES_CHARS)

    /**
     * Why a downloaded APK must not be installed, or null when it may: it must be this app (same package), the
     * release's version, a higher version code than the installed one, and signed by exactly the same certificates.
     */
    fun packageProblem(
        installedPackage: String, installedCode: Long, installedCerts: Set<String>,
        apkPackage: String?, apkVersionName: String?, apkCode: Long, apkCerts: Set<String>,
        expectedVersion: String,
    ): String? = when {
        apkPackage != installedPackage -> "wrong package"
        apkVersionName != expectedVersion -> "wrong version"
        apkCode <= installedCode -> "not newer"
        apkCerts.isEmpty() || installedCerts.isEmpty() -> "unsigned"
        apkCerts != installedCerts -> "different signing certificate"
        else -> null
    }
}
