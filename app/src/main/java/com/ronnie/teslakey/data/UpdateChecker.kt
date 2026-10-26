package com.ronnie.teslakey.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * Checks the static release manifest kept on the download site and downloads verified APKs.
 * Contract (see website 发布说明): latest.json with available / applicationId / versionCode /
 * versionName / apkUrl (relative to the manifest) / sha256 / size / minSdk / notes.
 * Any malformed field, non-HTTPS or foreign host result in "no update" rather than an install.
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
        val size: Long?,
        val notes: String?
    )

    companion object {
        const val CHECK_URL = "https://benbenyu.top/apps/tesla-ble/latest.json"
        private const val TRUSTED_HOST = "benbenyu.top"
    }

    /** Blocking network call; run off the main thread. Distinguishes "no update" from "check failed". */
    fun check(): CheckResult = try {
        val conn = URL(CHECK_URL).openConnection() as HttpsURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        conn.disconnect()
        parse(body)
    } catch (_: Exception) {
        CheckResult.Failed
    }

    fun parse(body: String): CheckResult = try {
        val o = JSONObject(body)
        val sameApp = o.optString("applicationId") == context.packageName
        val available = o.optBoolean("available", false)
        if (!available || !sameApp) CheckResult.NoUpdate else {
            @Suppress("DEPRECATION")
            val localCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            val code = o.optInt("versionCode", 0)
            // Optional system compatibility field; absence is treated as compatible.
            val minSdkOk = !o.has("minSdk") || Build.VERSION.SDK_INT >= o.optInt("minSdk")
            val resolved = resolveApkUrl(o.optString("apkUrl"))
            val sha = o.optString("sha256").lowercase()
            when {
                code <= localCode || !minSdkOk -> CheckResult.NoUpdate
                resolved == null || sha.length != 64 -> CheckResult.Failed
                else -> CheckResult.Available(Update(
                    versionCode = code,
                    versionName = o.optString("versionName").ifEmpty { code.toString() },
                    apkUrl = resolved,
                    sha256 = sha,
                    size = if (o.has("size")) o.optLong("size") else null,
                    notes = o.optString("notes").takeIf { it.isNotEmpty() }
                ))
            }
        }
    } catch (_: Exception) {
        CheckResult.Failed
    }

    /** apkUrl may be relative to latest.json; it must stay on the trusted host over HTTPS. */
    private fun resolveApkUrl(raw: String): String? {
        if (raw.isEmpty()) return null
        val resolved = URI(CHECK_URL).resolve(raw)
        val ok = resolved.scheme.equals("https", true) && resolved.host == TRUSTED_HOST
        return if (ok) resolved.toString() else null
    }

    /** Downloads into the cache and returns the file only when size and SHA-256 both match. */
    fun download(update: Update): File? = try {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "update.apk")
        val conn = URL(update.apkUrl).openConnection() as HttpsURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
        val digest = MessageDigest.getInstance("SHA-256").digest(out.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val sizeOk = update.size == null || out.length() == update.size
        if (digest == update.sha256 && sizeOk) out else { out.delete(); null }
    } catch (_: Exception) {
        null
    }
}
