package com.catmap.dubai.update

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
data class Release(val version: String, val title: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

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
        val connection = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CatMap/${BuildConfig.VERSION_NAME}")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (connection.responseCode == 404) return@withContext null
            if (connection.responseCode !in 200..299) throw IOException("GitHub responded ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
                ?: return@withContext null
            val url = apk.optString("browser_download_url")
            if (!url.startsWith("https://github.com/")) return@withContext null
            Release(
                version = json.optString("tag_name").removePrefix("v"),
                title = json.optString("name"),
                notes = json.optString("body").trim(),
                apkUrl = url,
                sizeBytes = apk.optLong("size"),
            )
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(version: String): Boolean = compare(version, BuildConfig.VERSION_NAME) > 0

    /** Checks at most every 12 hours, and stays quiet about a version the person chose to skip. */
    suspend fun checkIfDue(): Release? {
        if (!configured) return null
        val s = settings.settings.first()
        if (System.currentTimeMillis() - s.lastUpdateCheck < CHECK_INTERVAL_MS) return null
        val release = runCatching { latest() }.getOrNull()
        settings.setLastUpdateCheck(System.currentTimeMillis())
        return release?.takeIf { isNewer(it.version) && it.version != s.skippedVersion }
    }

    suspend fun skip(version: String) = settings.setSkippedVersion(version)

    /** Downloads the APK into the app's private cache, reporting progress through [download]. */
    suspend fun fetch(release: Release) = withContext(Dispatchers.IO) {
        _download.value = DownloadState.Downloading(0f)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "catmap-${release.version}.apk")
        try {
            var url = URL(release.apkUrl)
            var connection: HttpURLConnection
            // GitHub redirects asset downloads to its storage host; follow a few HTTPS redirects only.
            var hops = 0
            while (true) {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "CatMap/${BuildConfig.VERSION_NAME}")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val code = connection.responseCode
                if (code in 300..399 && hops < 5) {
                    val next = URL(url, connection.getHeaderField("Location"))
                    connection.disconnect()
                    if (next.protocol != "https") throw IOException("Refusing non-HTTPS redirect")
                    url = next
                    hops++
                    continue
                }
                if (code !in 200..299) throw IOException("Download failed: $code")
                break
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) _download.value = DownloadState.Downloading((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            connection.disconnect()
            _download.value = DownloadState.Ready(target)
        } catch (e: Exception) {
            target.delete()
            _download.value = DownloadState.Failed
        }
    }

    fun reset() {
        _download.value = DownloadState.Idle
    }

    /** True when Android still needs the one-time "install unknown apps" permission for CatMap. */
    fun needsInstallPermission(): Boolean = !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
