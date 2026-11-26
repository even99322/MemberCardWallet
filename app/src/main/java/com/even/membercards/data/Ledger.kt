package com.even.membercards.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

object EntryType {
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val TRANSFER = "transfer"
}

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
    val type: String = EntryType.EXPENSE,
    /** 帳戶 id（轉帳時為轉出帳戶） */
    val account: String = "",
    /** 轉帳的轉入帳戶 id */
    val toAccount: String = "",
    /** 消費地點（沒有記錄時為 null） */
    val lat: Double? = null,
    val lng: Double? = null,
    /** 定位精確度（公尺） */
    val accuracy: Float = 0f,
    /** 地址（反查得到的，可能空白） */
    val place: String = "",
) {
    val hasLocation: Boolean get() = lat != null && lng != null

    val isAuto: Boolean get() = source == "auto"
    val isRecurring: Boolean get() = source == "recurring"
    val isExpense: Boolean get() = type == EntryType.EXPENSE
    val isIncome: Boolean get() = type == EntryType.INCOME
    val isTransfer: Boolean get() = type == EntryType.TRANSFER

    /** 列表上顯示的標題：備註 → 商家 → 分類 */
    val title: String get() = note.ifBlank { merchant }.ifBlank { if (isTransfer) "轉帳" else category }

    fun localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("time", time).put("amount", amount).put("merchant", merchant)
        .put("category", category).put("method", method).put("note", note).put("source", source).put("key", key)
        .put("type", type).put("account", account).put("toAccount", toAccount)
        .apply {
            if (lat != null && lng != null) {
                put("lat", lat).put("lng", lng).put("accuracy", accuracy.toDouble()).put("place", place)
            }
        }

    companion object {
        fun fromJson(o: JSONObject) = LedgerEntry(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            time = o.optLong("time", System.currentTimeMillis()),
            amount = o.optDouble("amount", 0.0),
            merchant = o.optString("merchant"),
            category = Categories.migrate(o.optString("category", "其他").ifBlank { "其他" }),
            method = o.optString("method"),
            note = o.optString("note"),
            source = o.optString("source", "manual"),
            key = o.optString("key"),
            type = o.optString("type", EntryType.EXPENSE).ifBlank { EntryType.EXPENSE },
            account = o.optString("account"),
            toAccount = o.optString("toAccount"),
            lat = if (o.has("lat")) o.optDouble("lat") else null,
            lng = if (o.has("lng")) o.optDouble("lng") else null,
            accuracy = o.optDouble("accuracy", 0.0).toFloat(),
            place = o.optString("place"),
        )
    }
}

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** cash / bank / credit / virtual / other */
    val type: String = "cash",
    val initial: Double = 0.0,
    val emoji: String = "💵",
) {
    val isCredit: Boolean get() = type == "credit"

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("type", type)
        .put("initial", initial).put("emoji", emoji)

    companion object {
        val typeLabels = linkedMapOf(
            "bank" to "銀行",
            "cash" to "現金",
            "credit" to "信用卡",
            "virtual" to "虛擬帳戶",
            "other" to "其他",
        )
        val typeEmoji = mapOf("bank" to "🏦", "cash" to "💵", "credit" to "💳", "virtual" to "📱", "other" to "💼")

        fun fromJson(o: JSONObject) = Account(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name", "帳戶"),
            type = o.optString("type", "cash"),
            initial = o.optDouble("initial", 0.0),
            emoji = o.optString("emoji").ifBlank { typeEmoji[o.optString("type", "cash")] ?: "💵" },
        )
    }
}

data class Category(
    val name: String,
    val emoji: String,
    val color: Long,
    val type: String = EntryType.EXPENSE,
    val custom: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("emoji", emoji).put("color", color).put("type", type)

    companion object {
        fun fromJson(o: JSONObject) = Category(
            name = o.optString("name"),
            emoji = o.optString("emoji", "🏷️"),
            color = o.optLong("color", 0xFF8A9BB3),
            type = o.optString("type", EntryType.EXPENSE),
            custom = true,
        )
    }
}

object Categories {
    val expense = listOf(
        Category("食物", "🍱", 0xFF3CFFA0),
        Category("零食飲料", "🧋", 0xFFFFC53D),
        Category("購物", "🛒", 0xFFFF8A3D),
        Category("超商", "🏪", 0xFF00B894),
        Category("娛樂", "🎤", 0xFFE45CFF),
        Category("課金", "💎", 0xFF8B7CFF),
        Category("周邊", "🧸", 0xFFFF7EB6),
        Category("訂閱", "📺", 0xFFFFB547),
        Category("教育", "📚", 0xFFFF6B3D),
        Category("運動", "🏊", 0xFF38E8FF),
        Category("旅遊", "✈️", 0xFF4FC3F7),
        Category("交通", "🚌", 0xFFC98A7A),
        Category("醫療", "🏥", 0xFF5B8CFF),
        Category("社交", "👥", 0xFFB39DDB),
        Category("居家", "🏠", 0xFF8D6E63),
        Category("轉帳", "🔁", 0xFF4FC3F7),
        Category("其他", "💲", 0xFF8A9BB3),
    )
    val income = listOf(
        Category("薪水", "💰", 0xFFFFC53D, EntryType.INCOME),
        Category("獎金", "🎁", 0xFFFF8A3D, EntryType.INCOME),
        Category("投資", "📈", 0xFF3CFFA0, EntryType.INCOME),
        Category("兼職", "💼", 0xFF38E8FF, EntryType.INCOME),
        Category("轉帳充正", "🔁", 0xFF4FC3F7, EntryType.INCOME),
        Category("退款", "↩️", 0xFF8B7CFF, EntryType.INCOME),
        Category("其他收入", "💲", 0xFF8A9BB3, EntryType.INCOME),
    )

    /** 舊版分類名稱轉成新版 */
    fun migrate(old: String): String = when (old) {
        "餐飲" -> "食物"
        "生活" -> "居家"
        else -> old
    }

    private val defaultNotes = mapOf(
        "食物" to listOf("早餐", "午餐", "晚餐", "宵夜", "甜點", "零食"),
        "零食飲料" to listOf("飲料", "咖啡", "零食", "手搖"),
        "交通" to listOf("加油", "捷運", "停車", "高鐵", "計程車"),
        "訂閱" to listOf("Netflix", "Spotify", "YouTube", "iCloud"),
        "購物" to listOf("衣服", "日用品", "網購"),
        "超商" to listOf("7-11", "全家", "萊爾富"),
        "居家" to listOf("房租", "水電", "網路", "電話費"),
        "醫療" to listOf("看診", "藥品"),
        "薪水" to listOf("月薪", "加班費"),
    )

    fun defaultNotes(category: String): List<String> = defaultNotes[category].orEmpty()

    private val keywords = linkedMapOf(
        "超商" to listOf("全家", "familymart", "7-eleven", "7-11", "711", "統一超商", "萊爾富", "hi-life", "ok超商", "ok mart"),
        "零食飲料" to listOf("星巴克", "starbucks", "路易莎", "louisa", "咖啡", "cafe", "coffee", "茶", "飲", "50嵐", "清心", "可不可"),
        "食物" to listOf(
            "麥當勞", "mcdonald", "摩斯", "mos", "肯德基", "kfc", "必勝客", "pizza", "拉亞", "早餐",
            "餐", "食", "飯", "麵", "鍋", "壽司", "uber eats", "foodpanda",
        ),
        "交通" to listOf("台鐵", "高鐵", "捷運", "uber", "計程車", "taxi", "加油", "中油", "台塑", "停車", "ubike", "客運", "etag"),
        "購物" to listOf(
            "全聯", "家樂福", "carrefour", "costco", "好市多", "ikea", "宜得利", "nitori", "momo", "蝦皮", "shopee", "pchome",
            "屈臣氏", "watsons", "康是美", "寶雅", "大創", "daiso", "無印", "muji", "uniqlo", "百貨", "商場", "outlet",
        ),
        "訂閱" to listOf("netflix", "spotify", "youtube", "icloud", "google one", "disney", "claude", "chatgpt", "kkbox"),
        "娛樂" to listOf("威秀", "影城", "電影", "steam", "playstation", "nintendo", "ktv", "遊戲"),
        "醫療" to listOf("醫院", "診所", "藥局", "藥", "牙醫", "醫"),
        "居家" to listOf("電費", "水費", "瓦斯", "電信", "中華電信", "遠傳", "台灣大", "洗衣", "房租"),
    )

    /** 依商家名稱（以及同名會員卡所在的資料夾）猜支出分類。 */
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
        expense.any { it.name == folder } -> folder
        folder.contains("超商") -> "超商"
        folder.contains("吃") || folder.contains("餐") || folder.contains("食") -> "食物"
        folder.contains("飲") -> "零食飲料"
        folder.contains("買") || folder.contains("購") || folder.contains("藥妝") -> "購物"
        folder.contains("油") || folder.contains("車") || folder.contains("交通") -> "交通"
        folder.contains("醫") -> "醫療"
        folder.contains("玩") || folder.contains("娛") -> "娛樂"
        else -> null
    }
}

fun formatMoney(v: Double): String =
    if (v == Math.floor(v)) "$" + String.format(Locale.US, "%,.0f", v) else "$" + String.format(Locale.US, "%,.2f", v)

/** 不帶 $ 的金額：37,637 */
fun formatNumber(v: Double): String =
    if (v == Math.floor(v)) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%,.2f", v)

/** 定期收支：每月固定日期自動記一筆（訂閱、房租、薪水…）。 */
data class Recurring(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: String = EntryType.EXPENSE,
    val category: String = "訂閱",
    val amount: Double,
    val account: String = "",
    /** 每月幾號（1–31，月份天數不足時記在月底） */
    val day: Int = 1,
    val enabled: Boolean = true,
    /** 最後一次已處理的月份（YearMonth 字串），空字串代表從 startMonth 開始 */
    val lastMonth: String = "",
    val startMonth: String = YearMonth.now().toString(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("type", type)
        .put("category", category).put("amount", amount).put("account", account).put("day", day)
        .put("enabled", enabled).put("lastMonth", lastMonth).put("startMonth", startMonth)

    companion object {
        fun fromJson(o: JSONObject) = Recurring(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = o.optString("title"),
            type = o.optString("type", EntryType.EXPENSE),
            category = o.optString("category", "其他"),
            amount = o.optDouble("amount", 0.0),
            account = o.optString("account"),
            day = o.optInt("day", 1).coerceIn(1, 31),
            enabled = o.optBoolean("enabled", true),
            lastMonth = o.optString("lastMonth"),
            startMonth = o.optString("startMonth", YearMonth.now().toString()),
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

class LedgerRepository private constructor(private val appContext: Context) {
    private val context get() = appContext
    private val file = File(context.filesDir, "ledger.json")
    private val metaFile = File(context.filesDir, "ledger_meta.json")
    private val captureFile = File(context.filesDir, "captures.json")

    val entries = mutableStateListOf<LedgerEntry>()
    val captures = mutableStateListOf<CapturedNotice>()
    val accounts = mutableStateListOf<Account>()
    val customCategories = mutableStateListOf<Category>()
    val recurring = mutableStateListOf<Recurring>()

    private val defaultAccountState = mutableStateOf("")
    private val autoPopupState = mutableStateOf(false)

    /** 自動記帳來源（"linepay" 或 App 套件名）→ 帳戶 id；使用者指定的對應 */
    val sourceAccounts = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /** 新增記帳時預設的帳戶 id */
    var defaultAccount: String
        get() = defaultAccountState.value.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id.orEmpty()
        set(v) {
            defaultAccountState.value = v
            saveMeta()
        }

    /** 新增記帳時自動彈出帳戶選單 */
    var autoPopupAccount: Boolean
        get() = autoPopupState.value
        set(v) {
            autoPopupState.value = v
            saveMeta()
        }

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
        val metaLoaded = runCatching {
            if (metaFile.exists()) {
                applyMeta(JSONObject(metaFile.readText()))
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (!metaLoaded || accounts.isEmpty()) {
            accounts.clear()
            accounts.addAll(
                listOf(
                    Account(name = "現金", type = "cash", emoji = "💵"),
                    Account(name = "銀行", type = "bank", emoji = "🏦"),
                    Account(name = "信用卡", type = "credit", emoji = "💳"),
                    Account(name = "LINE Pay", type = "virtual", emoji = "📱"),
                )
            )
            saveMeta()
        }
    }

    private fun applyMeta(o: JSONObject) {
        o.optJSONArray("accounts")?.let { arr ->
            accounts.clear()
            accounts.addAll((0 until arr.length()).map { Account.fromJson(arr.getJSONObject(it)) })
        }
        o.optJSONArray("categories")?.let { arr ->
            customCategories.clear()
            customCategories.addAll((0 until arr.length()).map { Category.fromJson(arr.getJSONObject(it)) })
        }
        o.optJSONArray("recurring")?.let { arr ->
            recurring.clear()
            recurring.addAll((0 until arr.length()).map { Recurring.fromJson(arr.getJSONObject(it)) })
        }
        defaultAccountState.value = o.optString("defaultAccount")
        autoPopupState.value = o.optBoolean("autoPopupAccount", false)
        o.optJSONObject("sourceAccounts")?.let { m ->
            sourceAccounts.clear()
            m.keys().forEach { k -> m.optString(k).takeIf { it.isNotBlank() }?.let { sourceAccounts[k] = it } }
        }
    }

    fun exportMeta(): JSONObject {
        val acc = JSONArray()
        accounts.forEach { acc.put(it.toJson()) }
        val cats = JSONArray()
        customCategories.forEach { cats.put(it.toJson()) }
        val rec = JSONArray()
        recurring.forEach { rec.put(it.toJson()) }
        return JSONObject()
            .put("accounts", acc)
            .put("categories", cats)
            .put("recurring", rec)
            .put("defaultAccount", defaultAccountState.value)
            .put("autoPopupAccount", autoPopupState.value)
            .put("sourceAccounts", JSONObject().also { m -> sourceAccounts.forEach { (k, v) -> m.put(k, v) } })
    }

    fun importMeta(o: JSONObject) {
        applyMeta(o)
        saveMeta()
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
        com.even.membercards.LedgerWidget.updateAll(context)
    }

    private fun saveMeta() = write(metaFile, exportMeta().toString())

    private fun saveCaptures() {
        val arr = JSONArray()
        captures.forEach { arr.put(it.toJson()) }
        write(captureFile, arr.toString())
    }

    // ---------- 紀錄 ----------

    fun get(id: String): LedgerEntry? = entries.firstOrNull { it.id == id }

    fun upsert(e: LedgerEntry) {
        val i = entries.indexOfFirst { it.id == e.id }
        if (i >= 0) entries[i] = e else entries.add(e)
        save()
        if (i < 0) maybeTagLocation(e)
    }

    /** 更新某筆紀錄的消費地點；lat 為 null 代表移除 */
    fun setLocation(id: String, lat: Double?, lng: Double?, accuracy: Float = 0f, place: String = "") {
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0) return
        entries[i] = entries[i].copy(lat = lat, lng = lng, accuracy = if (lat == null) 0f else accuracy, place = if (lat == null) "" else place)
        save()
    }

    /** 剛發生的支出（刷卡、付款）自動附上目前位置 */
    private fun maybeTagLocation(e: LedgerEntry) {
        if (!e.isExpense || e.isRecurring || e.hasLocation) return
        if (kotlin.math.abs(System.currentTimeMillis() - e.time) > 30 * 60_000L) return
        if (!AppSettings.instance(context).recordLocation) return
        LocationTagger.tag(context, e.id)
    }

    fun delete(id: String) {
        entries.removeAll { it.id == id }
        save()
    }

    /**
     * 自動記帳（支出）；重複的通知（同 key，或 2 分鐘內同金額同商家）不會重複記。
     * 帳戶會優先選名稱與付款方式相符的帳戶（例如 LINE Pay）。
     */
    fun addAuto(
        amount: Double,
        merchant: String,
        method: String,
        key: String,
        category: String,
        type: String = EntryType.EXPENSE,
        /** 銀行帳戶異動：優先記到銀行類帳戶，且不記錄位置 */
        bank: Boolean = false,
        toAccount: String = "",
        accountId: String? = null,
    ): LedgerEntry? {
        val now = System.currentTimeMillis()
        if (key.isNotBlank() && entries.any { it.key == key || it.key.split("||").contains(key) }) return null
        if (entries.any { it.isAuto && it.amount == amount && it.merchant == merchant && it.type == type && now - it.time < 120_000 }) return null
        val acc = accountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull {
                it.name.contains(method, ignoreCase = true) || method.contains(it.name, ignoreCase = true)
            }?.id
            ?: (if (bank) accounts.firstOrNull { it.type == "bank" }?.id else null)
            ?: defaultAccount
        // 同一筆消費常會收到兩則通知（例如 LINE Pay 付款＋信用卡刷卡通知）：3 分鐘內同帳戶同金額的支出視為重複
        if (!bank && type == EntryType.EXPENSE &&
            entries.any { it.isAuto && it.isExpense && it.amount == amount && it.account == acc && now - it.time < 180_000 }
        ) return null
        val e = LedgerEntry(
            time = now,
            amount = amount,
            merchant = merchant,
            category = category,
            method = method,
            source = "auto",
            key = key,
            type = type,
            account = acc,
            toAccount = toAccount,
        )
        entries.add(e)
        save()
        if (!bank) maybeTagLocation(e)
        return e
    }

    fun setSourceAccount(source: String, accountId: String?) {
        if (accountId.isNullOrBlank()) sourceAccounts.remove(source) else sourceAccounts[source] = accountId
        saveMeta()
    }

    /**
     * 自動記帳要記到哪個帳戶。
     * - 消費：通知提到某張信用卡（例如「LINE Pay卡」）就記到那張信用卡帳戶
     * - 使用者指定的來源對應
     * - 名稱相符的帳戶（消費優先信用卡，銀行異動優先銀行帳戶）
     * - 銀行異動找不到帳戶時，自動以 App 名稱建立銀行帳戶
     */
    fun resolveAutoAccount(source: String, method: String, text: String, bankEvent: Boolean): String {
        if (!bankEvent && source != "linepay") {
            val credits = accounts.filter { it.isCredit }
            credits.firstOrNull { c ->
                val kw = c.name.replace("信用卡", "").replace("卡", "").trim()
                kw.length >= 2 && text.contains(kw, ignoreCase = true)
            }?.let { return it.id }
            if (credits.size == 1 && Regex("卡號|末四碼|末4碼|信用卡|刷卡").containsMatchIn(text)) return credits[0].id
        }
        sourceAccounts[source]?.takeIf { id -> accounts.any { it.id == id } }?.let { return it }
        val matches = accounts.filter {
            it.name.contains(method, ignoreCase = true) || method.contains(it.name, ignoreCase = true)
        }
        val pick = if (bankEvent) matches.firstOrNull { it.type == "bank" } ?: matches.firstOrNull()
        else matches.sortedBy { if (it.isCredit) 0 else if (it.type == "virtual") 1 else 2 }.firstOrNull()
        if (pick != null) return pick.id
        if (bankEvent) {
            val name = method.replace(Regex("\\s*(行動銀行|Home Bank|網路銀行|行動網銀|App)$", RegexOption.IGNORE_CASE), "").trim().ifBlank { method }
            val a = Account(name = name, type = "bank", emoji = "🏦")
            accounts.add(a)
            sourceAccounts[source] = a.id
            saveMeta()
            return a.id
        }
        return defaultAccount
    }

    /**
     * 自己帳戶間轉帳：A 銀行轉出、B 銀行入帳，金額相同且 30 分鐘內 → 合併成一筆「轉帳」，不重複算支出與收入。
     */
    fun pairBankTransfer(e: LedgerEntry): LedgerEntry {
        if (!e.key.startsWith("bank:") || (e.type != EntryType.EXPENSE && e.type != EntryType.INCOME)) return e
        val other = entries.firstOrNull { o ->
            o.id != e.id && o.key.startsWith("bank:") && o.amount == e.amount && o.account != e.account &&
                kotlin.math.abs(o.time - e.time) <= 30 * 60_000L &&
                ((o.type == EntryType.EXPENSE && e.type == EntryType.INCOME) || (o.type == EntryType.INCOME && e.type == EntryType.EXPENSE))
        } ?: return e
        val out = if (e.type == EntryType.EXPENSE) e else other
        val inn = if (e.type == EntryType.INCOME) e else other
        val fromName = accounts.firstOrNull { it.id == out.account }?.name.orEmpty()
        val toName = accounts.firstOrNull { it.id == inn.account }?.name.orEmpty()
        val t = out.copy(
            type = EntryType.TRANSFER,
            toAccount = inn.account,
            category = "",
            merchant = "轉帳 $fromName → $toName",
            key = out.key + "||" + inn.key,
            time = minOf(out.time, inn.time),
        )
        entries.removeAll { it.id == out.id || it.id == inn.id }
        entries.add(t)
        save()
        return t
    }

    /** 現金帳戶（ATM 提款轉入用） */
    fun cashAccountId(): String = accounts.firstOrNull { it.type == "cash" }?.id ?: ""

    fun logCapture(c: CapturedNotice) {
        captures.add(0, c)
        while (captures.size > 50) captures.removeAt(captures.lastIndex)
        saveCaptures()
    }

    fun clearCaptures() {
        captures.clear()
        saveCaptures()
    }

    // ---------- 定期收支 ----------

    fun upsertRecurring(r: Recurring) {
        val i = recurring.indexOfFirst { it.id == r.id }
        if (i >= 0) recurring[i] = r else recurring.add(r)
        saveMeta()
    }

    fun deleteRecurring(id: String) {
        recurring.removeAll { it.id == id }
        saveMeta()
    }

    /**
     * 補記所有已到期的定期收支（每月一次，不會重複）。回傳新增筆數。
     * 開 App、開記帳頁時呼叫即可。
     */
    fun runRecurring(): Int {
        val today = LocalDate.now()
        val now = YearMonth.now()
        val zone = ZoneId.systemDefault()
        var added = 0
        for (idx in recurring.indices) {
            var r = recurring[idx]
            if (!r.enabled || r.amount <= 0) continue
            var m = if (r.lastMonth.isBlank()) {
                runCatching { YearMonth.parse(r.startMonth) }.getOrDefault(now)
            } else {
                runCatching { YearMonth.parse(r.lastMonth).plusMonths(1) }.getOrDefault(now)
            }
            var guard = 0
            while (!m.isAfter(now) && guard++ < 36) {
                val due = m.atDay(r.day.coerceAtMost(m.lengthOfMonth()))
                if (due.isAfter(today)) break
                val key = "rec:${r.id}:$m"
                if (entries.none { it.key == key }) {
                    entries.add(
                        LedgerEntry(
                            time = due.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
                            amount = r.amount,
                            category = r.category,
                            note = r.title,
                            source = "recurring",
                            key = key,
                            type = r.type,
                            account = r.account,
                        )
                    )
                    added++
                }
                r = r.copy(lastMonth = m.toString())
                m = m.plusMonths(1)
            }
            if (r != recurring[idx]) recurring[idx] = r
        }
        if (added > 0) save()
        saveMeta()
        return added
    }

    /** 下一次扣款／入帳日 */
    fun nextDue(r: Recurring): LocalDate {
        val start = if (r.lastMonth.isBlank()) {
            runCatching { YearMonth.parse(r.startMonth) }.getOrDefault(YearMonth.now())
        } else {
            runCatching { YearMonth.parse(r.lastMonth).plusMonths(1) }.getOrDefault(YearMonth.now())
        }
        return start.atDay(r.day.coerceAtMost(start.lengthOfMonth()))
    }

    // ---------- 一句話記帳 ----------

    private val incomeWords = listOf("薪水", "薪資", "收入", "獎金", "退款", "入帳", "紅包", "利息", "股利", "兼職", "收到")

    /**
     * 解析「午餐 120」「加油 1500 信用卡」「薪水 32000」這類一句話。
     * 金額取最後一個數字；有記過同樣備註就沿用當時的分類與帳戶。
     */
    fun quickParse(text: String, cards: List<MemberCard> = emptyList()): LedgerEntry? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val m = Regex("""(\d[\d,]*(?:\.\d+)?)\s*(?:元|塊|圓|NT|TWD)?""").findAll(t).lastOrNull() ?: return null
        val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        var note = (t.substring(0, m.range.first) + " " + t.substring(m.range.last + 1))
            .replace("$", " ").replace("＄", " ")
            .replace(Regex("""\s+"""), " ").trim()
            .replace(Regex("""(花了|付了|用了|總共|花|付|共)$"""), "")
            .trim().trim('，', ',', '。', ' ')
        val acc = accounts.sortedByDescending { it.name.length }.firstOrNull { note.contains(it.name, ignoreCase = true) }
        if (acc != null) note = note.replace(acc.name, "", ignoreCase = true).replace(Regex("""\s+"""), " ").trim()
        val type = if (incomeWords.any { note.contains(it) }) EntryType.INCOME else EntryType.EXPENSE
        val history = entries.filter { it.type == type && it.note.isNotBlank() && it.note == note }.maxByOrNull { it.time }
        val category = history?.category
            ?: if (type == EntryType.INCOME) {
                Categories.income.firstOrNull { note.contains(it.name) }?.name
                    ?: if (note.contains("薪")) "薪水" else "其他收入"
            } else {
                categories(EntryType.EXPENSE).firstOrNull { c -> c.name.length >= 2 && note.contains(c.name) }?.name
                    ?: Categories.expense.firstOrNull { c -> Categories.defaultNotes(c.name).any { note.contains(it) } }?.name
                    ?: Categories.guess(note, cards)
            }
        return LedgerEntry(
            amount = Math.round(amount * 100) / 100.0,
            note = note,
            category = category,
            type = type,
            account = acc?.id ?: history?.account ?: defaultAccount,
        )
    }

    // ---------- 搜尋 ----------

    fun search(query: String): List<LedgerEntry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val amount = q.replace(",", "").toDoubleOrNull()
        return entries.filter { e ->
            e.note.contains(q, true) || e.merchant.contains(q, true) || e.category.contains(q, true) ||
                (account(e.account)?.name?.contains(q, true) == true) ||
                (amount != null && e.amount == amount)
        }.sortedByDescending { it.time }
    }

    // ---------- 統計 ----------

    fun monthEntries(ym: YearMonth): List<LedgerEntry> {
        val zone = ZoneId.systemDefault()
        return entries.filter { YearMonth.from(Instant.ofEpochMilli(it.time).atZone(zone)) == ym }
    }

    fun monthExpense(ym: YearMonth = YearMonth.now()): Double = monthEntries(ym).filter { it.isExpense }.sumOf { it.amount }
    fun monthIncome(ym: YearMonth = YearMonth.now()): Double = monthEntries(ym).filter { it.isIncome }.sumOf { it.amount }

    /** 本月支出（首頁快捷列用） */
    fun monthTotal(ym: YearMonth = YearMonth.now()): Double = monthExpense(ym)

    /** 有記帳資料的月份（含本月），新到舊 */
    fun activeMonths(): List<YearMonth> {
        val zone = ZoneId.systemDefault()
        return (entries.map { YearMonth.from(Instant.ofEpochMilli(it.time).atZone(zone)) } + YearMonth.now())
            .distinct().sortedDescending()
    }

    /** 某分類常用的備註（預設 + 歷史紀錄） */
    fun quickNotes(category: String, limit: Int = 10): List<String> =
        (Categories.defaultNotes(category) +
            entries.filter { it.category == category }.sortedByDescending { it.time }.map { it.note })
            .filter { it.isNotBlank() }.distinct().take(limit)

    // ---------- 分類 ----------

    fun categories(type: String): List<Category> {
        val base = if (type == EntryType.INCOME) Categories.income else Categories.expense
        return base + customCategories.filter { it.type == type }
    }

    fun category(name: String, type: String = EntryType.EXPENSE): Category =
        categories(type).firstOrNull { it.name == name }
            ?: (Categories.expense + Categories.income + customCategories).firstOrNull { it.name == name }
            ?: Category(name, "🏷️", 0xFF8A9BB3, type)

    fun addCategory(c: Category): Boolean {
        if (c.name.isBlank() || categories(c.type).any { it.name == c.name }) return false
        customCategories.add(c.copy(custom = true))
        saveMeta()
        return true
    }

    fun deleteCategory(c: Category) {
        customCategories.removeAll { it.name == c.name && it.type == c.type }
        saveMeta()
    }

    // ---------- 帳戶 ----------

    fun account(id: String): Account? = accounts.firstOrNull { it.id == id }

    fun balance(acc: Account): Double {
        var b = acc.initial
        for (e in entries) {
            when (e.type) {
                EntryType.INCOME -> if (e.account == acc.id) b += e.amount
                EntryType.EXPENSE -> if (e.account == acc.id) b -= e.amount
                EntryType.TRANSFER -> {
                    if (e.account == acc.id) b -= e.amount
                    if (e.toAccount == acc.id) b += e.amount
                }
            }
        }
        return b
    }

    fun upsertAccount(a: Account) {
        val i = accounts.indexOfFirst { it.id == a.id }
        if (i >= 0) accounts[i] = a else accounts.add(a)
        saveMeta()
    }

    /** 刪除帳戶；相關紀錄保留但不再屬於任何帳戶 */
    fun deleteAccount(id: String) {
        accounts.removeAll { it.id == id }
        var changed = false
        for (i in entries.indices) {
            val e = entries[i]
            if (e.account == id || e.toAccount == id) {
                entries[i] = e.copy(
                    account = if (e.account == id) "" else e.account,
                    toAccount = if (e.toAccount == id) "" else e.toAccount,
                )
                changed = true
            }
        }
        if (changed) save()
        saveMeta()
    }

    // ---------- 匯出入 ----------

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
        fun accName(id: String) = account(id)?.name.orEmpty()
        val sb = StringBuilder("﻿日期,時間,類型,金額,分類,備註,商家,帳戶,轉入帳戶,付款方式,來源,地點,緯度,經度\n")
        list.sortedBy { it.time }.forEach { e ->
            val dt = Instant.ofEpochMilli(e.time).atZone(zone)
            val typeLabel = when (e.type) {
                EntryType.INCOME -> "收入"
                EntryType.TRANSFER -> "轉帳"
                else -> "支出"
            }
            sb.append(dt.toLocalDate()).append(',')
                .append(String.format(Locale.US, "%02d:%02d", dt.hour, dt.minute)).append(',')
                .append(typeLabel).append(',')
                .append(if (e.amount == Math.floor(e.amount)) e.amount.toLong().toString() else e.amount.toString()).append(',')
                .append(esc(e.category)).append(',')
                .append(esc(e.note)).append(',')
                .append(esc(e.merchant)).append(',')
                .append(esc(accName(e.account))).append(',')
                .append(esc(accName(e.toAccount))).append(',')
                .append(esc(e.method)).append(',')
                .append(if (e.isAuto) "自動" else "手動").append(',')
                .append(esc(e.place)).append(',')
                .append(e.lat?.toString() ?: "").append(',')
                .append(e.lng?.toString() ?: "").append('\n')
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
