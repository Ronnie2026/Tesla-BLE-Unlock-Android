package com.ronnie.teslakey.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/**
 * Checks the release manifest on the static download site and downloads verified APKs.
 *
 * Manifest (latest.json):
 * { available, applicationId, versionCode, versionName, apkUrl (relative to latest.json),
 *   sha256, sizeBytes, publishedAt, minAndroid, minSdk?, notes[], releaseNotes }
 *
 * Only HTTPS on the trusted host is accepted, including every redirect hop. A malformed or
 * unreachable manifest yields Failed; this never affects vehicle control features, and nothing
 * beyond the manifest/APK is ever uploaded.
 */
class UpdateChecker(private val context: Context) {

    sealed class CheckResult {
        object NoUpdate : CheckResult()
        object Failed : CheckResult()
        data class Available(val update: Update) : CheckResult()
    }

    data class Update(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val sizeBytes: Long?,
        val publishedAt: String?,
        val minSdk: Int?,
        val notes: List<String>
    )

    companion object {
        const val CHECK_URL = "https://benbenyu.top/apps/tesla-ble/latest.json"
        private const val TRUSTED_HOST = "benbenyu.top"
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 10000
    }

    /** Blocking network call; run off the main thread. */
    fun check(): CheckResult = try {
        val body = openStream(URL(CHECK_URL)).use { it.readBytes().toString(Charsets.UTF_8) }
        parse(body)
    } catch (_: Exception) {
        CheckResult.Failed
    }

    fun parse(body: String): CheckResult = try {
        val o = JSONObject(body)
        val available = o.optBoolean("available", false)
        val sameApp = o.optString("applicationId") == context.packageName
        if (!available || !sameApp) CheckResult.NoUpdate else {
            val localCode = PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0))
            val code = o.optInt("versionCode", 0)
            val minSdk = if (o.has("minSdk")) o.optInt("minSdk") else null
            val compatible = minSdk == null || Build.VERSION.SDK_INT >= minSdk
            val resolved = resolveApkUrl(o.optString("apkUrl"))
            val sha = o.optString("sha256").lowercase()
            when {
                code.toLong() <= localCode || !compatible -> CheckResult.NoUpdate
                resolved == null || sha.length != 64 -> CheckResult.Failed
                else -> CheckResult.Available(Update(
                    versionCode = code,
                    versionName = o.optString("versionName").ifEmpty { code.toString() },
                    apkUrl = resolved,
                    sha256 = sha,
                    sizeBytes = if (o.has("sizeBytes")) o.optLong("sizeBytes") else null,
                    publishedAt = o.optString("publishedAt").takeIf { it.isNotEmpty() },
                    minSdk = minSdk,
                    notes = parseNotes(o)
                ))
            }
        }
    } catch (_: Exception) {
        CheckResult.Failed
    }

    private fun parseNotes(o: JSONObject): List<String> {
        val arr = o.optJSONArray("notes")
        if (arr != null) {
            return (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotEmpty() }
            }
        }
        return o.optString("releaseNotes").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** apkUrl is relative to latest.json and must stay on the trusted host over HTTPS. */
    private fun resolveApkUrl(raw: String): String? {
        if (raw.isEmpty()) return null
        val resolved = URI(CHECK_URL).resolve(raw)
        val ok = resolved.scheme.equals("https", true) && resolved.host == TRUSTED_HOST
        return if (ok) resolved.toString() else null
    }

    /** Open an HTTPS resource on the trusted host, validating protocol and host on every redirect. */
    private fun openStream(start: URL): InputStream {
        var url = start
        var hops = 0
        while (true) {
            require(url.protocol.equals("https", true) && url.host == TRUSTED_HOST) { "untrusted url: $url" }
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            val code = try { conn.responseCode } catch (e: Exception) { conn.disconnect(); throw e }
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null) throw IOException("redirect without location")
                if (++hops > MAX_REDIRECTS) throw IOException("too many redirects")
                url = URL(url, location)
                continue
            }
            if (code !in 200..299) { conn.disconnect(); throw IOException("HTTP $code") }
            return conn.inputStream
        }
    }

    /**
     * Download into the cache with progress and cancellation.
     * Returns the file only when size and SHA-256 match; any failure or cancellation yields null.
     */
    fun download(update: Update, isCancelled: () -> Boolean, onProgress: (done: Long, total: Long) -> Unit): File? = try {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "update.apk")
        val needed = update.sizeBytes ?: 0L
        if (dir.usableSpace < needed + 8L * 1024 * 1024) throw IOException("insufficient space")
        out.delete()
        openStream(URL(update.apkUrl)).use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastReported = 0L
                while (true) {
                    if (isCancelled()) {
                        out.delete()
                        return null
                    }
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (done - lastReported >= 256 * 1024) {
                        lastReported = done
                        onProgress(done, needed)
                    }
                }
                onProgress(done, needed)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(out.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val sizeOk = update.sizeBytes == null || out.length() == update.sizeBytes
        if (digest == update.sha256 && sizeOk) out else { out.delete(); null }
    } catch (_: Exception) {
        null
    }

    /** The downloaded APK must be this app, the announced version, and share our signing certificate. */
    fun verifyApk(file: File, update: Update): Boolean {
        return try {
            val pm = context.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
            if (archive.packageName != context.packageName) return false
            if (PackageInfoCompat.getLongVersionCode(archive) != update.versionCode.toLong()) return false
            val remoteSigners = archive.signingInfo?.apkContentsSigners ?: return false
            val localSigners = pm.getPackageInfo(context.packageName, flags).signingInfo?.apkContentsSigners
                ?: return false
            remoteSigners.any { r -> localSigners.any { l -> r.toByteArray().contentEquals(l.toByteArray()) } }
        } catch (_: Exception) {
            false
        }
    }
}
