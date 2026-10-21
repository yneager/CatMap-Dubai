package com.catmap.dubai.update

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.catmap.dubai.BuildConfig
import com.catmap.dubai.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A GitHub release that carries an APK. */
data class Release(val version: String, val title: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val checksumUrl:String?=null, val assetName:String="")

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Ready(val file: File) : DownloadState
    data object Failed : DownloadState
}

/**
 * In-app updates outside Google Play. Each GitHub release (built and signed by the workflow in
 * .github/workflows/release.yml) has an APK attached; the app compares the release tag with its own
 * version, downloads the APK over HTTPS and hands it to Android's installer. Android only installs it
 * when it is signed with the same key as the installed app, so data is kept and tampered files fail.
 */
class Updater(private val context: Context, private val settings: SettingsRepository) {
    val repo: String = BuildConfig.GITHUB_REPO
    val configured: Boolean get() = repo.isNotBlank()

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    /** The newest release, or null when there is none (or no APK attached). */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val text = httpsText("https://api.github.com/repos/$repo/releases/latest", notFoundIsNull = true) ?: return@withContext null
        parseRelease(JSONObject(text), repo)
    }

    fun isNewer(version: String): Boolean = compare(version, BuildConfig.VERSION_NAME) > 0

    /** Checks at most every 12 hours, and stays quiet about a version the person chose to skip. */
    suspend fun checkIfDue(): Release? {
        if (!configured) return null
        // An APK from an earlier session is either installed by now or abandoned; it's downloaded again if needed.
        if (_download.value == DownloadState.Idle) withContext(Dispatchers.IO) {
            File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
        }
        val s = settings.settings.first()
        if (System.currentTimeMillis() - s.lastUpdateCheck < CHECK_INTERVAL_MS) return null
        // A failed check (offline, GitHub busy) isn't recorded, so the next launch tries again.
        val release = runCatching { latest() }.getOrElse { return null }
        settings.setLastUpdateCheck(System.currentTimeMillis())
        return release?.takeIf { isNewer(it.version) && it.version != s.skippedVersion }
    }

    suspend fun skip(version: String) = settings.setSkippedVersion(version)

    /** Downloads the APK into the app's private cache, reporting progress through [download]. */
    suspend fun fetch(release: Release) = withContext(Dispatchers.IO) {
        if (!UpdateChecks.apkAssetOk(repo, release.version, release.assetName, release.apkUrl, release.sizeBytes)) {
            _download.value = DownloadState.Failed
            return@withContext
        }
        _download.value = DownloadState.Downloading(0f)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "catmap-${release.version}.apk")
        try {
            val connection = open(release.apkUrl, readTimeout = 30_000)
            try {
                if (connection.responseCode !in 200..299) throw IOException("Download failed: ${connection.responseCode}")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                if (total > UpdateChecks.MAX_APK_BYTES || total != release.sizeBytes) throw IOException("Unexpected APK size")
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            read += n
                            if (read > total) throw IOException("APK larger than announced")
                            output.write(buffer, 0, n)
                            _download.value = DownloadState.Downloading((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (target.length() != release.sizeBytes) throw IOException("Incomplete APK")
            // A quick check for a damaged download; trust comes from verifyPackage (see UpdateChecks).
            release.checksumUrl?.let { checksumUrl ->
                val sidecar = httpsText(checksumUrl) ?: throw IOException("Checksum missing")
                if (!ApkChecksum.verify(target, sidecar, release.assetName)) throw IOException("APK checksum mismatch")
            }
            verifyPackage(target, release.version)
            settings.saveReleaseNotes(release.version, release.notes)
            _download.value = DownloadState.Ready(target)
        } catch (e: Exception) {
            // Nothing that failed a check stays on the phone.
            target.delete()
            _download.value = DownloadState.Failed
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, version: String) {
        val pm = context.packageManager
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else android.content.pm.PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IOException("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun code(info: android.content.pm.PackageInfo) = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        fun certificates(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { signature ->
                java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        UpdateChecks.packageProblem(
            installedPackage = context.packageName, installedCode = code(installed), installedCerts = certificates(installed),
            apkPackage = archive.packageName, apkVersionName = archive.versionName, apkCode = code(archive), apkCerts = certificates(archive),
            expectedVersion = version,
        )?.let { throw IOException("Update refused: $it") }
    }

    /** Opens [value], following at most 5 redirects, each to a GitHub host over HTTPS ([UpdateChecks.allowedUrl]). */
    private fun open(value: String, readTimeout: Int = 15_000): HttpURLConnection {
        var url = value
        repeat(6) {
            if (!UpdateChecks.allowedUrl(url)) throw IOException("Refusing a non-GitHub address")
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "CatMap/${BuildConfig.VERSION_NAME}")
                if (url.startsWith("https://api.github.com/")) setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 15_000
                this.readTimeout = readTimeout
            }
            val code = connection.responseCode
            if (code !in 300..399) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            url = URL(URL(url), location ?: throw IOException("Redirect without a target")).toString()
        }
        throw IOException("Too many redirects")
    }

    /** A small text response (release JSON, checksum), capped at [UpdateChecks.MAX_TEXT_BYTES]. */
    private fun httpsText(value: String, notFoundIsNull: Boolean = false): String? {
        val connection = open(value)
        try {
            val code = connection.responseCode
            if (code == 404 && notFoundIsNull) return null
            if (code !in 200..299) throw IOException("HTTP $code")
            val out = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > UpdateChecks.MAX_TEXT_BYTES) throw IOException("Response too large")
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun installedReleaseNotes(): String? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        runCatching {
            val text = httpsText("https://api.github.com/repos/$repo/releases/tags/v${BuildConfig.VERSION_NAME}") ?: return@runCatching null
            UpdateChecks.plainNotes(JSONObject(text).optString("body")).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun reset() {
        _download.value = DownloadState.Idle
    }

    /** True when Android still needs the one-time "install unknown apps" permission for CatMap. */
    fun needsInstallPermission(): Boolean = !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Opens Android's installer; the person confirms the update there. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

        /**
         * The release from GitHub's JSON, or null unless it's a published (not draft or pre-release) version tag with
         * the workflow's `CatMap-<version>.apk` from this repository ([UpdateChecks.apkAssetOk]).
         */
        fun parseRelease(json: JSONObject, repo: String): Release? {
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val tag = json.optString("tag_name")
            if (!tag.startsWith("v")) return null
            val version = tag.removePrefix("v")
            if (!UpdateChecks.validVersion(version)) return null
            val assets = json.optJSONArray("assets") ?: return null
            val list = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            val name = "CatMap-$version.apk"
            val apk = list.firstOrNull { it.optString("name") == name } ?: return null
            val url = apk.optString("browser_download_url")
            val size = apk.optLong("size")
            if (!UpdateChecks.apkAssetOk(repo, version, name, url, size)) return null
            val checksum = list.firstOrNull { it.optString("name") == "$name.sha256" }?.optString("browser_download_url")
            return Release(
                version = version,
                title = UpdateChecks.plainNotes(json.optString("name")).take(120),
                notes = UpdateChecks.plainNotes(json.optString("body")),
                apkUrl = url,
                sizeBytes = size,
                checksumUrl = checksum?.takeIf { it == "$url.sha256" },
                assetName = name,
            )
        }

        /** Compares dotted versions numerically ("2.10.0" > "2.9.3"). */
        fun compare(a: String, b: String): Int {
            val x = a.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val y = b.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}
