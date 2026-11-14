package com.even.membercards.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 透過 GitHub Releases 檢查並安裝新版本。 */
object Updater {
    private const val REPO = "even99322/MemberCardWallet"
    private const val PREFS = "updater"
    private const val KEY_SKIPPED = "skipped_version"

    data class Release(val version: String, val notes: String, val apkUrl: String, val pageUrl: String)

    fun currentVersion(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    /** 在背景執行緒呼叫。 */
    fun fetchLatest(): Release? {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) return null
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = o.optJSONArray("assets") ?: return null
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url")
                    break
                }
            }
            return Release(
                version = o.optString("tag_name").removePrefix("v"),
                notes = o.optString("body").trim(),
                apkUrl = apk ?: return null,
                pageUrl = o.optString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** 比較 1.2.0 這類版本號；remote 較新時回傳 true。 */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun skippedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SKIPPED, null)

    fun skip(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SKIPPED, version).apply()
    }

    /** 在背景執行緒下載 APK，onProgress 回傳 0~1。 */
    fun download(context: Context, url: String, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var lastPct = -1
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return out
    }

    /**
     * 開啟系統安裝畫面。若尚未允許「安裝未知應用程式」，會先帶到設定頁並回傳 false，
     * 使用者允許後回到 App 再按一次安裝即可。
     */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
