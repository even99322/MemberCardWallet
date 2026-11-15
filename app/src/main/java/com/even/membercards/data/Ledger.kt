package com.even.membercards.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val time: Long = System.currentTimeMillis(),
    val amount: Double,
    val merchant: String = "",
    val category: String = "其他",
    val method: String = "",
    val note: String = "",
    /** manual = 手動；auto = 通知自動記帳 */
    val source: String = "manual",
    /** 自動記帳的去重複用 key */
    val key: String = "",
) {
    val isAuto: Boolean get() = source == "auto"

    fun localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("time", time).put("amount", amount).put("merchant", merchant)
        .put("category", category).put("method", method).put("note", note).put("source", source).put("key", key)

    companion object {
        fun fromJson(o: JSONObject) = LedgerEntry(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            time = o.optLong("time", System.currentTimeMillis()),
            amount = o.optDouble("amount", 0.0),
            merchant = o.optString("merchant"),
            category = o.optString("category", "其他").ifBlank { "其他" },
            method = o.optString("method"),
            note = o.optString("note"),
            source = o.optString("source", "manual"),
            key = o.optString("key"),
        )
    }
}

/** 收到的付款通知（不論是否成功解析），讓使用者檢查與補記。 */
data class CapturedNotice(
    val time: Long,
    val app: String,
    val title: String,
    val text: String,
    val recordedAmount: Double?,
) {
    fun toJson(): JSONObject = JSONObject().put("time", time).put("app", app).put("title", title).put("text", text)
        .put("recorded", recordedAmount ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = CapturedNotice(
            time = o.optLong("time"),
            app = o.optString("app"),
            title = o.optString("title"),
            text = o.optString("text"),
            recordedAmount = if (o.isNull("recorded")) null else o.optDouble("recorded"),
        )
    }
}

object Categories {
    data class Cat(val name: String, val color: Long)

    val all = listOf(
        Cat("餐飲", 0xFFFF8A3D),
        Cat("超商", 0xFF3CFFA0),
        Cat("購物", 0xFF8B7CFF),
        Cat("交通", 0xFF2B7BFF),
        Cat("生活", 0xFF38E8FF),
        Cat("娛樂", 0xFFE45CFF),
        Cat("醫療", 0xFFFF5470),
        Cat("其他", 0xFF8A9BB3),
    )

    fun color(name: String): Long = all.firstOrNull { it.name == name }?.color ?: 0xFF8A9BB3

    private val keywords = linkedMapOf(
        "超商" to listOf("全家", "familymart", "7-eleven", "7-11", "711", "統一超商", "萊爾富", "hi-life", "ok超商", "ok mart"),
        "餐飲" to listOf(
            "麥當勞", "mcdonald", "摩斯", "mos", "星巴克", "starbucks", "路易莎", "louisa", "咖啡", "cafe", "coffee",
            "肯德基", "kfc", "必勝客", "pizza", "拉亞", "早餐", "餐", "食", "飯", "麵", "鍋", "茶", "飲", "壽司", "uber eats", "foodpanda",
        ),
        "交通" to listOf("台鐵", "高鐵", "捷運", "uber", "計程車", "taxi", "加油", "中油", "台塑", "停車", "ubike", "客運", "etag"),
        "購物" to listOf(
            "全聯", "家樂福", "carrefour", "costco", "好市多", "ikea", "宜得利", "nitori", "momo", "蝦皮", "shopee", "pchome",
            "屈臣氏", "watsons", "康是美", "寶雅", "大創", "daiso", "無印", "muji", "uniqlo", "百貨", "商場", "outlet",
        ),
        "娛樂" to listOf("威秀", "影城", "電影", "netflix", "spotify", "youtube", "steam", "playstation", "kkbox", "遊戲"),
        "醫療" to listOf("醫院", "診所", "藥局", "藥", "牙醫", "醫"),
        "生活" to listOf("電費", "水費", "瓦斯", "電信", "中華電信", "遠傳", "台灣大", "洗衣", "美髮"),
    )

    /** 依商家名稱（以及同名會員卡所在的資料夾）猜分類。 */
    fun guess(merchant: String, cards: List<MemberCard> = emptyList()): String {
        val m = merchant.lowercase()
        if (m.isBlank()) return "其他"
        cards.firstOrNull { c ->
            c.name.isNotBlank() && (m.contains(c.name.lowercase()) || c.name.lowercase().contains(m))
        }?.folder?.let { f -> folderToCategory(f)?.let { return it } }
        for ((cat, words) in keywords) if (words.any { m.contains(it) }) return cat
        return "其他"
    }

    private fun folderToCategory(folder: String): String? = when {
        all.any { it.name == folder } -> folder
        folder.contains("超商") -> "超商"
        folder.contains("吃") || folder.contains("餐") || folder.contains("食") || folder.contains("飲") -> "餐飲"
        folder.contains("買") || folder.contains("購") || folder.contains("藥妝") -> "購物"
        folder.contains("油") || folder.contains("車") || folder.contains("交通") -> "交通"
        folder.contains("醫") -> "醫療"
        folder.contains("玩") || folder.contains("娛") -> "娛樂"
        else -> null
    }
}

fun formatMoney(v: Double): String =
    if (v == Math.floor(v)) "$" + String.format(Locale.US, "%,.0f", v) else "$" + String.format(Locale.US, "%,.2f", v)

class LedgerRepository private constructor(context: Context) {
    private val file = File(context.filesDir, "ledger.json")
    private val captureFile = File(context.filesDir, "captures.json")

    val entries = mutableStateListOf<LedgerEntry>()
    val captures = mutableStateListOf<CapturedNotice>()

    init {
        runCatching {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                entries.addAll((0 until arr.length()).map { LedgerEntry.fromJson(arr.getJSONObject(it)) })
            }
        }
        runCatching {
            if (captureFile.exists()) {
                val arr = JSONArray(captureFile.readText())
                captures.addAll((0 until arr.length()).map { CapturedNotice.fromJson(arr.getJSONObject(it)) })
            }
        }
    }

    private fun write(f: File, content: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(f)) {
            f.writeText(content)
            tmp.delete()
        }
    }

    private fun save() {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        write(file, arr.toString())
    }

    private fun saveCaptures() {
        val arr = JSONArray()
        captures.forEach { arr.put(it.toJson()) }
        write(captureFile, arr.toString())
    }

    fun get(id: String): LedgerEntry? = entries.firstOrNull { it.id == id }

    fun upsert(e: LedgerEntry) {
        val i = entries.indexOfFirst { it.id == e.id }
        if (i >= 0) entries[i] = e else entries.add(e)
        save()
    }

    fun delete(id: String) {
        entries.removeAll { it.id == id }
        save()
    }

    /**
     * 自動記帳；重複的通知（同 key，或 2 分鐘內同金額同商家）不會重複記。
     * 回傳新增的紀錄，重複時回傳 null。
     */
    fun addAuto(amount: Double, merchant: String, method: String, key: String, category: String): LedgerEntry? {
        val now = System.currentTimeMillis()
        if (key.isNotBlank() && entries.any { it.key == key }) return null
        if (entries.any { it.isAuto && it.amount == amount && it.merchant == merchant && now - it.time < 120_000 }) return null
        val e = LedgerEntry(
            time = now,
            amount = amount,
            merchant = merchant,
            category = category,
            method = method,
            source = "auto",
            key = key,
        )
        entries.add(e)
        save()
        return e
    }

    fun logCapture(c: CapturedNotice) {
        captures.add(0, c)
        while (captures.size > 50) captures.removeAt(captures.lastIndex)
        saveCaptures()
    }

    fun clearCaptures() {
        captures.clear()
        saveCaptures()
    }

    fun monthEntries(ym: YearMonth): List<LedgerEntry> {
        val zone = ZoneId.systemDefault()
        return entries.filter { YearMonth.from(Instant.ofEpochMilli(it.time).atZone(zone)) == ym }
    }

    fun monthTotal(ym: YearMonth = YearMonth.now()): Double = monthEntries(ym).sumOf { it.amount }

    /** 常用商家（給新增時快速選） */
    fun recentMerchants(limit: Int = 12): List<String> =
        entries.sortedByDescending { it.time }.map { it.merchant }.filter { it.isNotBlank() }.distinct().take(limit)

    fun exportJson(): JSONArray {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        return arr
    }

    fun importJson(arr: JSONArray): Int {
        var n = 0
        for (i in 0 until arr.length()) {
            val e = LedgerEntry.fromJson(arr.getJSONObject(i))
            val idx = entries.indexOfFirst { it.id == e.id }
            if (idx >= 0) entries[idx] = e else entries.add(e)
            n++
        }
        save()
        return n
    }

    /** Excel 可直接開啟的 CSV（含 BOM）。 */
    fun toCsv(list: List<LedgerEntry> = entries): String {
        val zone = ZoneId.systemDefault()
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("﻿日期,時間,金額,商家,分類,付款方式,備註,來源\n")
        list.sortedBy { it.time }.forEach { e ->
            val dt = Instant.ofEpochMilli(e.time).atZone(zone)
            sb.append(dt.toLocalDate()).append(',')
                .append(String.format(Locale.US, "%02d:%02d", dt.hour, dt.minute)).append(',')
                .append(if (e.amount == Math.floor(e.amount)) e.amount.toLong().toString() else e.amount.toString()).append(',')
                .append(esc(e.merchant)).append(',')
                .append(esc(e.category)).append(',')
                .append(esc(e.method)).append(',')
                .append(esc(e.note)).append(',')
                .append(if (e.isAuto) "自動" else "手動").append('\n')
        }
        return sb.toString()
    }

    companion object {
        @Volatile
        private var INSTANCE: LedgerRepository? = null

        fun instance(context: Context): LedgerRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LedgerRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
