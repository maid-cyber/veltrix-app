package com.veltrix.vpn.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.veltrix.vpn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val build: Int, val version: String, val url: String, val size: Long, val notes: String)

/** Provjera i instalacija nove verzije sa GitHub Releases (bez Play Storea). */
object Updater {
    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 30000
        setRequestProperty("User-Agent", "VeltrixVPN/Android")
    }

    /** Adresa panela: iz linka naloga (https://…/s/TOKEN), inače ugrađena. */
    private fun panelBase(): String = Store.link.substringBefore("/s/", "").ifBlank { BuildConfig.PANEL_URL }.trimEnd('/')

    /** Nova verzija sa panela (/app/config → /download/android); GitHub samo ako panel ne odgovara. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val base = panelBase()
            val c = open("$base/app/config")
            try {
                if (c.responseCode == 200) {
                    val l = org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONObject("latest")
                    val b = l?.optInt("android") ?: 0
                    if (b > 0) return@withContext if (b > BuildConfig.VERSION_CODE) UpdateInfo(b, "0.1.$b", base + l!!.optString("android_url", "/download/android"), 0, "") else null
                }
            } finally { c.disconnect() }
        }
        checkGithub()
    }

    private fun checkGithub(): UpdateInfo? {
        val c = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=5")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) return null
            val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            var best: UpdateInfo? = null
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optBoolean("draft")) continue
                val build = Regex("(\\d+)$").find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val assets = r.optJSONArray("assets") ?: continue
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } ?: continue
                if (best == null || build > best.build) {
                    best = UpdateInfo(build, "0.1.$build", apk.optString("browser_download_url"), apk.optLong("size"), r.optString("body"))
                }
            }
            return best?.takeIf { it.build > BuildConfig.VERSION_CODE }
        } catch (e: Exception) {
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun file(ctx: Context) = File(ctx.cacheDir, "updates/update.apk")

    /** Skida APK; onProgress dobija 0..1. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val out = file(ctx)
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".part")
        val c = open(info.url)
        c.instanceFollowRedirects = true
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            c.inputStream.use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - last > 256 * 1024) {
                            last = done
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        out.delete()
        tmp.renameTo(out)
        onProgress(1f)
        out
    }

    /** Android 8+ traži dozvolu "Instaliraj nepoznate aplikacije" za ovu aplikaciju. */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun downloaded(ctx: Context): File? = file(ctx).takeIf { it.exists() && it.length() > 0 }
}
