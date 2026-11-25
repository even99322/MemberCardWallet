package com.even.membercards.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 完整備份：卡片、資料夾、照片、記帳（含帳戶、分類、定期收支）、快捷列與設定。
 * 格式為 zip（backup.json + card_photos/），也能讀舊版的 JSON 備份。
 * 寫到使用者在系統檔案選擇器選的位置——選 Google 雲端硬碟就是存進 Google 帳號。
 */
object Backup {
    const val MIME = "application/zip"

    fun suggestedName(): String = "會員卡夾備份.zip"

    fun buildJson(context: Context): JSONObject {
        val repo = CardRepository.instance(context)
        val ledger = LedgerRepository.instance(context)
        return JSONObject(repo.exportJson())
            .put("quick", AppSettings.instance(context).quickJson())
            .put("ledger", ledger.exportJson())
            .put("ledgerMeta", ledger.exportMeta())
            .put("backupAt", System.currentTimeMillis())
            .put("app", "MemberCardWallet")
    }

    private fun photoDir(context: Context) = File(context.filesDir, "card_photos")

    fun writeZip(context: Context, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(buildJson(context).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photoDir(context).listFiles()?.filter { it.isFile }?.forEach { f ->
                zip.putNextEntry(ZipEntry("card_photos/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** 寫到指定文件；成功回傳大小（bytes） */
    fun writeTo(context: Context, uri: Uri): Long {
        val bytes = ByteArrayOutputStream().also { writeZip(context, it) }.toByteArray()
        val cr = context.contentResolver
        val stream = runCatching { cr.openOutputStream(uri, "wt") }.getOrNull()
            ?: cr.openOutputStream(uri, "w")
            ?: error("無法寫入備份檔")
        stream.use { it.write(bytes) }
        return bytes.size.toLong()
    }

    /** 記住備份位置，之後自動備份可以直接覆寫 */
    fun rememberTarget(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        AppSettings.instance(context).backupUri = uri.toString()
    }

    /** 立即備份到記住的位置 */
    fun backupNow(context: Context): Result<Long> {
        val settings = AppSettings.instance(context)
        val target = settings.backupUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: return Result.failure(IllegalStateException("尚未選擇備份位置"))
        return runCatching { writeTo(context, target) }
            .onSuccess {
                settings.lastBackupAt = System.currentTimeMillis()
                settings.backupError = ""
            }
            .onFailure { settings.backupError = it.message ?: it.javaClass.simpleName }
    }

    data class RestoreResult(val cards: Int, val entries: Int, val photos: Int)

    /** 從備份檔還原（zip 或舊版 JSON）；同一張卡、同一筆帳會被覆蓋 */
    fun restore(context: Context, uri: Uri): RestoreResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("無法讀取檔案")
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        var json: String? = null
        var photos = 0
        if (isZip) {
            val dir = photoDir(context).apply { mkdirs() }
            ZipInputStream(bytes.inputStream()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    when {
                        e.isDirectory -> {}
                        e.name == "backup.json" -> json = zin.readBytes().toString(Charsets.UTF_8)
                        e.name.startsWith("card_photos/") -> {
                            val name = e.name.substringAfterLast('/')
                            // 只接受單純檔名，避免路徑跳脫
                            if (name.isNotBlank() && !name.contains("..") && name.matches(Regex("[\\w\\-.]+"))) {
                                File(dir, name).outputStream().use { zin.copyTo(it) }
                                photos++
                            }
                        }
                    }
                    zin.closeEntry()
                }
            }
        } else {
            json = bytes.toString(Charsets.UTF_8)
        }
        val text = json?.trim() ?: error("備份檔裡沒有資料")
        val o = JSONObject(text)
        val ledger = LedgerRepository.instance(context)
        o.optJSONObject("quick")?.let { AppSettings.instance(context).applyQuickJson(it) }
        o.optJSONObject("ledgerMeta")?.let { ledger.importMeta(it) }
        val entries = o.optJSONArray("ledger")?.let { ledger.importJson(it) } ?: 0
        val cards = CardRepository.instance(context).importJson(text)
        return RestoreResult(cards, entries, photos)
    }
}
