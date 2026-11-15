package com.even.membercards.data

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.time.LocalDate
import java.util.UUID

/** 一張紙本電子發票（掃描左側 QR Code 取得）。 */
data class Invoice(
    val id: String = UUID.randomUUID().toString(),
    /** 發票號碼，例如 AB12345678 */
    val number: String,
    /** 開立日期（西元） */
    val date: LocalDate,
    val random: String = "",
    /** 總計金額（含稅） */
    val amount: Long,
    val sellerId: String = "",
    /** 品名摘要 */
    val items: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** 對應的記帳紀錄 */
    val entryId: String = "",
    /** 中獎金額；null = 尚未對獎，0 = 沒中 */
    val prize: Long? = null,
    val prizeName: String = "",
) {
    /** 期別：民國年 + 雙月，例如 11410 代表 114 年 9–10 月 */
    val period: String get() = periodOf(date)

    val digits: String get() = number.takeLast(8)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("number", number).put("date", date.toString())
        .put("random", random).put("amount", amount).put("sellerId", sellerId)
        .put("items", items).put("createdAt", createdAt).put("entryId", entryId)
        .put("prize", prize ?: -1L).put("prizeName", prizeName)

    companion object {
        fun fromJson(o: JSONObject) = Invoice(
            id = o.optString("id", UUID.randomUUID().toString()),
            number = o.optString("number"),
            date = runCatching { LocalDate.parse(o.optString("date")) }.getOrDefault(LocalDate.now()),
            random = o.optString("random"),
            amount = o.optLong("amount"),
            sellerId = o.optString("sellerId"),
            items = o.optString("items"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            entryId = o.optString("entryId"),
            prize = o.optLong("prize", -1L).takeIf { it >= 0 },
            prizeName = o.optString("prizeName"),
        )

        fun periodOf(d: LocalDate): String {
            val m = if (d.monthValue % 2 == 0) d.monthValue else d.monthValue + 1
            return "%03d%02d".format(d.year - 1911, m)
        }
    }
}

/** 一期的中獎號碼。 */
data class InvoiceDraw(
    /** 期別，例如 11410 */
    val period: String,
    val special: String = "",
    val grand: String = "",
    val first: List<String> = emptyList(),
    /** 增開六獎（3 碼）；近年多半沒有 */
    val extraSixth: List<String> = emptyList(),
    val manual: Boolean = false,
) {
    val isValid: Boolean get() = special.length == 8 || grand.length == 8 || first.isNotEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("period", period).put("special", special).put("grand", grand)
        .put("first", JSONArray(first)).put("extraSixth", JSONArray(extraSixth)).put("manual", manual)

    companion object {
        fun fromJson(o: JSONObject) = InvoiceDraw(
            period = o.optString("period"),
            special = o.optString("special"),
            grand = o.optString("grand"),
            first = o.optJSONArray("first").strings(),
            extraSixth = o.optJSONArray("extraSixth").strings(),
            manual = o.optBoolean("manual"),
        )

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }
}

/** 對獎結果 */
data class Prize(val name: String, val amount: Long)

object InvoiceRules {
    /**
     * 對一個 8 碼號碼（發票號碼後 8 碼）。
     * 特別獎 1000 萬、特獎 200 萬、頭獎 20 萬；
     * 與頭獎末 7/6/5/4/3 碼相同：二獎 4 萬、三獎 1 萬、四獎 4 千、五獎 1 千、六獎 200 元。
     */
    fun check(digits: String, d: InvoiceDraw): Prize? {
        if (digits.length != 8) return null
        if (d.special.length == 8 && digits == d.special) return Prize("特別獎", 10_000_000)
        if (d.grand.length == 8 && digits == d.grand) return Prize("特獎", 2_000_000)
        var best: Prize? = null
        val tiers = listOf(
            8 to Prize("頭獎", 200_000), 7 to Prize("二獎", 40_000), 6 to Prize("三獎", 10_000),
            5 to Prize("四獎", 4_000), 4 to Prize("五獎", 1_000), 3 to Prize("六獎", 200),
        )
        for (f in d.first) {
            if (f.length != 8) continue
            val hit = tiers.firstOrNull { (n, _) -> digits.takeLast(n) == f.takeLast(n) }?.second
            if (hit != null && (best == null || hit.amount > best.amount)) best = hit
        }
        if (best == null && d.extraSixth.any { it.length == 3 && digits.endsWith(it) }) best = Prize("增開六獎", 200)
        return best
    }

    /** 末三碼快速對獎：回傳可能中的獎（需要再核對完整號碼） */
    fun quickCheck(last3: String, d: InvoiceDraw): List<String> {
        if (last3.length != 3) return emptyList()
        val out = mutableListOf<String>()
        if (d.special.endsWith(last3)) out += "特別獎 ${d.special}"
        if (d.grand.endsWith(last3)) out += "特獎 ${d.grand}"
        d.first.filter { it.endsWith(last3) }.forEach { out += "頭獎系列 $it" }
        d.extraSixth.filter { it == last3 }.forEach { out += "增開六獎 $it" }
        return out
    }

    /** 期別顯示：11410 → 114 年 09–10 月 */
    fun label(period: String): String {
        if (period.length != 5) return period
        val y = period.take(3).toIntOrNull() ?: return period
        val m = period.takeLast(2).toIntOrNull() ?: return period
        return "%d 年 %02d–%02d 月".format(y, m - 1, m)
    }

    /** 開獎日：雙月期別的下個月 25 日 */
    fun drawDate(period: String): LocalDate? {
        val y = period.take(3).toIntOrNull() ?: return null
        val m = period.takeLast(2).toIntOrNull() ?: return null
        return LocalDate.of(y + 1911, m, 1).plusMonths(1).withDayOfMonth(25)
    }

    /** 兌獎期限：開獎後次月 6 日起 3 個月 */
    fun claimDeadline(period: String): LocalDate? =
        drawDate(period)?.withDayOfMonth(5)?.plusMonths(4)

    /** 最近一期已開獎的期別 */
    fun latestDrawnPeriod(today: LocalDate = LocalDate.now()): String {
        var d = today
        repeat(4) {
            val p = Invoice.periodOf(d)
            val dd = drawDate(p)
            if (dd != null && !today.isBefore(dd)) return p
            d = d.minusMonths(2)
        }
        return Invoice.periodOf(today.minusMonths(2))
    }
}

/** 解析電子發票證明聯左側 QR Code（財政部規格）。 */
object InvoiceQr {
    private val HEAD = Regex("^([A-Z]{2}\\d{8})(\\d{7})(\\d{4})([0-9A-Fa-f]{8})([0-9A-Fa-f]{8})(\\d{8})(\\d{8})")

    data class Parsed(
        val number: String,
        val date: LocalDate,
        val random: String,
        val amount: Long,
        val sellerId: String,
        val items: List<String>,
    )

    /** 是否是右側 QR（只有品項，沒有發票資訊） */
    fun isRightCode(text: String) = text.startsWith("**")

    fun parse(raw: String): Parsed? {
        val text = raw.trim()
        val m = HEAD.find(text) ?: return null
        val number = m.groupValues[1]
        val roc = m.groupValues[2]
        val y = roc.take(3).toIntOrNull() ?: return null
        val mo = roc.substring(3, 5).toIntOrNull() ?: return null
        val da = roc.substring(5, 7).toIntOrNull() ?: return null
        val date = runCatching { LocalDate.of(y + 1911, mo, da) }.getOrNull() ?: return null
        val total = m.groupValues[5].toLongOrNull(16) ?: return null
        val seller = m.groupValues[7]
        // 77 碼固定欄位之後：:營業人自用區:品目筆數:總品目數:編碼:品名:數量:單價...
        val tail = text.drop(77)
        val items = parseItems(tail)
        return Parsed(number, date, m.groupValues[3], total, seller, items)
    }

    private fun parseItems(tail: String): List<String> {
        if (!tail.startsWith(":")) return emptyList()
        val parts = tail.drop(1).split(":")
        if (parts.size < 5) return emptyList()
        val encoding = parts[3].trim()
        var rest = parts.drop(4)
        if (encoding == "2") {
            // Base64：整段解碼後再切
            val joined = rest.joinToString(":")
            val decoded = runCatching { String(Base64.decode(joined, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                ?: return emptyList()
            rest = decoded.split(":")
        }
        val names = rest.chunked(3).mapNotNull { c -> c.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } }
        return names.map { fixCharset(it, encoding) }
    }

    /** Big5 品名若被掃描器當成 ISO-8859-1 讀進來會變亂碼，嘗試還原 */
    private fun fixCharset(s: String, encoding: String): String {
        val looksMangled = s.any { it.code in 0x80..0xFF } && s.none { it.code > 0xFF }
        if (!looksMangled) return s
        val bytes = s.toByteArray(Charsets.ISO_8859_1)
        val cs = if (encoding == "0") "Big5" else "UTF-8"
        return runCatching { String(bytes, Charset.forName(cs)) }.getOrDefault(s)
    }
}

class InvoiceRepository private constructor(private val context: Context) {
    val invoices = mutableStateListOf<Invoice>()
    val draws = mutableStateListOf<InvoiceDraw>()
    private val file = File(context.filesDir, "invoices.json")

    init {
        load()
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            val a = o.optJSONArray("invoices") ?: JSONArray()
            invoices.addAll((0 until a.length()).map { Invoice.fromJson(a.getJSONObject(it)) })
            val d = o.optJSONArray("draws") ?: JSONArray()
            draws.addAll((0 until d.length()).map { InvoiceDraw.fromJson(d.getJSONObject(it)) })
        }
    }

    private fun save() {
        val o = JSONObject()
            .put("invoices", JSONArray().also { a -> invoices.forEach { a.put(it.toJson()) } })
            .put("draws", JSONArray().also { a -> draws.forEach { a.put(it.toJson()) } })
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }

    fun find(number: String) = invoices.firstOrNull { it.number == number }

    /** 新增；已存在回傳 false */
    fun add(inv: Invoice): Boolean {
        if (find(inv.number) != null) return false
        val checked = draw(inv.period)?.let { d -> withPrize(inv, d) } ?: inv
        invoices.add(0, checked)
        save()
        return true
    }

    fun update(inv: Invoice) {
        val i = invoices.indexOfFirst { it.id == inv.id }
        if (i >= 0) invoices[i] = inv
        save()
    }

    fun delete(id: String) {
        invoices.removeAll { it.id == id }
        save()
    }

    fun draw(period: String) = draws.firstOrNull { it.period == period }

    fun putDraw(d: InvoiceDraw) {
        val i = draws.indexOfFirst { it.period == d.period }
        if (i >= 0) draws[i] = d else draws.add(d)
        draws.sortByDescending { it.period }
        while (draws.size > 8) draws.removeAt(draws.lastIndex)
        recheck()
    }

    private fun withPrize(inv: Invoice, d: InvoiceDraw): Invoice {
        val p = InvoiceRules.check(inv.digits, d)
        return inv.copy(prize = p?.amount ?: 0L, prizeName = p?.name ?: "")
    }

    /** 用已知的中獎號碼重新對所有發票；回傳新中獎的發票 */
    fun recheck(): List<Invoice> {
        val newWins = mutableListOf<Invoice>()
        for (i in invoices.indices) {
            val inv = invoices[i]
            val d = draw(inv.period) ?: continue
            val c = withPrize(inv, d)
            if ((c.prize ?: 0) > 0 && (inv.prize ?: 0) == 0L) newWins += c
            invoices[i] = c
        }
        save()
        return newWins
    }

    fun periods(): List<String> =
        (invoices.map { it.period } + draws.map { it.period }).distinct().sortedDescending()

    fun inPeriod(period: String) = invoices.filter { it.period == period }.sortedByDescending { it.date }

    /** 下載最新中獎號碼；成功回傳取得的期別數 */
    suspend fun fetchDraws(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val found = mutableListOf<InvoiceDraw>()
            for (url in SOURCES) {
                val text = runCatching { download(url) }.getOrNull() ?: continue
                found += DrawParser.parse(text)
                if (found.isNotEmpty()) break
            }
            if (found.isEmpty()) error("無法取得中獎號碼")
            withContext(Dispatchers.Main) { found.forEach { putDraw(it) } }
            found.size
        }
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MemberCardWallet")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val SOURCES = listOf(
            "https://invoice.etax.nat.gov.tw/invoice.xml",
            "https://invoice.etax.nat.gov.tw/index.html",
            "https://invoice.etax.nat.gov.tw/lastNumber.html",
        )

        @Volatile
        private var INSTANCE: InvoiceRepository? = null

        fun instance(context: Context): InvoiceRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: InvoiceRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}

/** 從財政部網頁或 RSS 抓出各期中獎號碼（格式容錯）。 */
object DrawParser {
    private val PERIOD = Regex("(\\d{2,3})\\s*年\\s*0?(\\d{1,2})\\s*(?:月)?\\s*[-–~～、至]\\s*0?(\\d{1,2})\\s*月")

    fun parse(raw: String): List<InvoiceDraw> {
        val text = raw
            .replace(Regex("<!\\[CDATA\\[|]]>"), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace(Regex("[ \\t\\r]+"), " ")
        val heads = PERIOD.findAll(text).toList()
        val out = mutableListOf<InvoiceDraw>()
        for ((i, h) in heads.withIndex()) {
            val end = heads.getOrNull(i + 1)?.range?.first ?: text.length
            val chunk = text.substring(h.range.last + 1, end)
            val y = h.groupValues[1].toInt()
            val m2 = h.groupValues[3].toInt()
            if (m2 !in 2..12 || m2 % 2 != 0) continue
            val period = "%03d%02d".format(y, m2)
            val special = numbersAfter(chunk, "特別獎", 8).firstOrNull() ?: ""
            val grand = numbersAfter(chunk, "特獎", 8).firstOrNull() ?: ""
            val first = numbersAfter(chunk, "頭獎", 8).take(3)
            val extra = numbersAfter(chunk, "增開六獎", 3).take(6)
            val d = InvoiceDraw(period, special, grand, first, extra)
            if (d.isValid && out.none { it.period == period }) out += d
        }
        return out
    }

    /** 找出標籤後、下一個「獎」標籤前的所有 n 碼數字 */
    private fun numbersAfter(chunk: String, label: String, n: Int): List<String> {
        var idx = -1
        var from = 0
        while (true) {
            val k = chunk.indexOf(label, from)
            if (k < 0) break
            // 「特獎」不要誤抓到「特別獎」
            if (label == "特獎" && k > 0 && chunk.getOrNull(k - 1) == '別') { from = k + 1; continue }
            idx = k
            break
        }
        if (idx < 0) return emptyList()
        val after = chunk.substring(idx + label.length)
        val stop = Regex("特別獎|特獎|頭獎|二獎|增開六獎|領獎|兌獎|同期|獎金").find(after)?.range?.first ?: after.length
        val seg = after.substring(0, stop.coerceAtMost(600))
        return Regex("\\d+").findAll(seg).map { it.value }
            .flatMap { run -> if (run.length % n == 0) run.chunked(n).asSequence() else emptySequence() }
            .toList()
    }
}
