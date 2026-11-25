package com.even.membercards.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** 一組電話號碼（標籤例如：本人、家裡、媽媽） */
data class PhoneEntry(val label: String, val number: String)

/** 快捷列上的付款 App */
data class QuickApp(val pkg: String, val label: String)

/** 使用者設定（SharedPreferences + Compose 狀態，改了畫面會即時更新）。 */
class AppSettings private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val lockState = mutableStateOf(prefs.getBoolean(K_LOCK, false))
    private val viewState = mutableStateOf(prefs.getString(K_VIEW, VIEW_GRID) ?: VIEW_GRID)
    private val sortState = mutableStateOf(prefs.getString(K_SORT, SORT_SMART) ?: SORT_SMART)
    private val brightState = mutableStateOf(prefs.getBoolean(K_BRIGHT, true))
    private val carrierState = mutableStateOf(prefs.getString(K_CARRIER, "") ?: "")
    private val phonesState = mutableStateOf(loadPhones())
    private val payState = mutableStateOf(parseApps(prefs.getString(K_PAY, null)))
    private val quickCollapsedState = mutableStateOf(prefs.getBoolean(K_QUICK_COLLAPSED, false))
    private val autoRecordState = mutableStateOf(prefs.getBoolean(K_AUTO_RECORD, true))
    private val captureOtherState = mutableStateOf(prefs.getBoolean(K_CAPTURE_OTHER, false))
    private val budgetState = mutableStateOf(prefs.getLong(K_BUDGET, 0L))
    private val remindersState = mutableStateOf(prefs.getBoolean(K_REMINDERS, true))
    private val carrierOnCardState = mutableStateOf(prefs.getBoolean(K_CARRIER_ON_CARD, true))

    private val themeState = mutableStateOf(prefs.getString(K_THEME, "hud") ?: "hud")

    /** 介面主題（Palettes.key） */
    var theme: String
        get() = themeState.value
        set(v) {
            themeState.value = v
            prefs.edit().putString(K_THEME, v).apply()
            com.even.membercards.LedgerWidget.updateAll(appContext)
        }

    private val backupUriState = mutableStateOf(prefs.getString(K_BACKUP_URI, "") ?: "")
    private val autoBackupState = mutableStateOf(prefs.getBoolean(K_AUTO_BACKUP, false))
    private val lastBackupState = mutableStateOf(prefs.getLong(K_LAST_BACKUP, 0L))
    private val backupErrorState = mutableStateOf(prefs.getString(K_BACKUP_ERROR, "") ?: "")

    /** 雲端備份檔位置（SAF 文件網址，通常是 Google 雲端硬碟上的檔案） */
    var backupUri: String
        get() = backupUriState.value
        set(v) {
            backupUriState.value = v
            prefs.edit().putString(K_BACKUP_URI, v).apply()
        }

    /** 每天自動備份到 backupUri */
    var autoBackup: Boolean
        get() = autoBackupState.value
        set(v) {
            autoBackupState.value = v
            prefs.edit().putBoolean(K_AUTO_BACKUP, v).apply()
            com.even.membercards.BackupWorker.schedule(appContext, v)
        }

    var lastBackupAt: Long
        get() = lastBackupState.value
        set(v) {
            lastBackupState.value = v
            prefs.edit().putLong(K_LAST_BACKUP, v).apply()
        }

    /** 最近一次自動備份失敗原因（成功時清空） */
    var backupError: String
        get() = backupErrorState.value
        set(v) {
            backupErrorState.value = v
            prefs.edit().putString(K_BACKUP_ERROR, v).apply()
        }

    /** 卡片頁上方同時顯示載具條碼 */
    var carrierOnCard: Boolean
        get() = carrierOnCardState.value
        set(v) {
            carrierOnCardState.value = v
            prefs.edit().putBoolean(K_CARRIER_ON_CARD, v).apply()
        }

    /** 到期、定期扣款、預算提醒通知 */
    var reminders: Boolean
        get() = remindersState.value
        set(v) {
            remindersState.value = v
            prefs.edit().putBoolean(K_REMINDERS, v).apply()
            com.even.membercards.ReminderWorker.schedule(appContext, v)
        }

    /** 已送出的提醒（避免同一件事重複提醒） */
    fun reminderSent(key: String): Boolean = prefs.getStringSet(K_SENT, emptySet())?.contains(key) == true

    fun markReminderSent(key: String) {
        val set = (prefs.getStringSet(K_SENT, emptySet()) ?: emptySet()).toMutableSet()
        set.add(key)
        // 只保留最近 300 筆
        val trimmed = if (set.size > 300) set.toList().takeLast(300).toSet() else set
        prefs.edit().putStringSet(K_SENT, trimmed).apply()
    }

    /** 讀取 LINE Pay 付款通知自動記帳（還需要系統的通知存取權） */
    var autoRecord: Boolean
        get() = autoRecordState.value
        set(v) {
            autoRecordState.value = v
            prefs.edit().putBoolean(K_AUTO_RECORD, v).apply()
        }

    /** 也擷取快捷列上其他付款 App 的通知 */
    var captureOtherPay: Boolean
        get() = captureOtherState.value
        set(v) {
            captureOtherState.value = v
            prefs.edit().putBoolean(K_CAPTURE_OTHER, v).apply()
        }

    /** 每月預算（0 = 未設定） */
    var monthlyBudget: Long
        get() = budgetState.value
        set(v) {
            budgetState.value = v
            prefs.edit().putLong(K_BUDGET, v).apply()
        }

    /** 手機條碼載具（例如 /ABC1234），空字串 = 未設定 */
    var carrier: String
        get() = carrierState.value
        set(v) {
            carrierState.value = v
            prefs.edit().putString(K_CARRIER, v).apply()
            com.even.membercards.CarrierWidget.updateAll(appContext)
        }

    /** 會員電話號碼清單；第一個是主要號碼，其餘為備選 */
    var phones: List<PhoneEntry>
        get() = phonesState.value
        set(v) {
            val clean = v.filter { it.number.isNotBlank() }
            phonesState.value = clean
            prefs.edit().putString(K_PHONES, phonesToJson(clean).toString()).apply()
            com.even.membercards.CarrierWidget.updateAll(appContext)
        }

    private fun loadPhones(): List<PhoneEntry> {
        val raw = prefs.getString(K_PHONES, null)
        if (raw != null) return parsePhones(raw)
        // 舊版只有一個號碼
        val legacy = prefs.getString(K_PHONE, "") ?: ""
        return if (legacy.isBlank()) emptyList() else listOf(PhoneEntry("主要", legacy))
    }

    /** 快捷列的常用付款 App（依顯示順序） */
    private val recordLocationState = mutableStateOf(prefs.getBoolean(K_RECORD_LOCATION, false))
    private val cardAppsState = mutableStateOf(parseApps(prefs.getString(K_CARD_APPS, null)))

    /** 刷卡／付款自動記帳時一併記錄位置 */
    var recordLocation: Boolean
        get() = recordLocationState.value
        set(v) {
            recordLocationState.value = v
            prefs.edit().putBoolean(K_RECORD_LOCATION, v).apply()
        }

    /** 信用卡、行動支付的刷卡通知來源 App（銀行 App、Google 錢包…） */
    var cardApps: List<QuickApp>
        get() = cardAppsState.value
        set(v) {
            cardAppsState.value = v
            prefs.edit().putString(K_CARD_APPS, appsToJson(v).toString()).apply()
        }

    var payApps: List<QuickApp>
        get() = payState.value
        set(v) {
            payState.value = v
            prefs.edit().putString(K_PAY, appsToJson(v).toString()).apply()
        }

    var quickCollapsed: Boolean
        get() = quickCollapsedState.value
        set(v) {
            quickCollapsedState.value = v
            prefs.edit().putBoolean(K_QUICK_COLLAPSED, v).apply()
        }

    /** 匯出備份用 */
    fun quickJson(): JSONObject = JSONObject()
        .put("budget", monthlyBudget)
        .put("carrier", carrier)
        .put("phone", phones.firstOrNull()?.number ?: "")
        .put("phones", phonesToJson(phones))
        .put("payApps", appsToJson(payApps))
        .put("cardApps", appsToJson(cardApps))
        .put("theme", theme)
        .put("recordLocation", recordLocation)

    fun applyQuickJson(o: JSONObject) {
        o.optString("theme").takeIf { it.isNotBlank() }?.let { theme = it }
        if (o.has("recordLocation")) recordLocation = o.optBoolean("recordLocation")
        o.optJSONArray("cardApps")?.let { a -> parseApps(a.toString()).takeIf { it.isNotEmpty() }?.let { cardApps = it } }
        o.optLong("budget", 0L).takeIf { it > 0 }?.let { monthlyBudget = it }
        o.optString("carrier").takeIf { it.isNotBlank() }?.let { carrier = it }
        val arr = o.optJSONArray("phones")
        if (arr != null) {
            val list = parsePhones(arr.toString())
            if (list.isNotEmpty()) phones = list
        } else {
            o.optString("phone").takeIf { it.isNotBlank() }?.let { phones = listOf(PhoneEntry("主要", it)) }
        }
        o.optJSONArray("payApps")?.let { arr ->
            val list = parseApps(arr.toString())
            if (list.isNotEmpty()) payApps = list
        }
    }

    /** 開啟 App 時需要指紋／螢幕鎖解鎖 */
    var lockEnabled: Boolean
        get() = lockState.value
        set(v) {
            lockState.value = v
            prefs.edit().putBoolean(K_LOCK, v).apply()
        }

    var viewMode: String
        get() = viewState.value
        set(v) {
            viewState.value = v
            prefs.edit().putString(K_VIEW, v).apply()
        }

    var sortMode: String
        get() = sortState.value
        set(v) {
            sortState.value = v
            prefs.edit().putString(K_SORT, v).apply()
        }

    /** 顯示條碼時自動調到最亮 */
    var autoBrightness: Boolean
        get() = brightState.value
        set(v) {
            brightState.value = v
            prefs.edit().putBoolean(K_BRIGHT, v).apply()
        }

    companion object {
        const val VIEW_GRID = "GRID"
        const val VIEW_LIST = "LIST"
        const val SORT_SMART = "SMART"
        const val SORT_NAME = "NAME"
        const val SORT_USAGE = "USAGE"
        const val SORT_ADDED = "ADDED"
        const val SORT_EXPIRY = "EXPIRY"
        const val SORT_MANUAL = "MANUAL"

        val sortLabels = linkedMapOf(
            SORT_SMART to "智慧（常用＋最近）",
            SORT_USAGE to "使用次數",
            SORT_NAME to "名稱",
            SORT_ADDED to "新增時間",
            SORT_EXPIRY to "到期日",
            SORT_MANUAL to "自訂（拖曳）",
        )

        private const val K_LOCK = "lock"
        private const val K_VIEW = "view"
        private const val K_SORT = "sort"
        private const val K_BRIGHT = "bright"
        private const val K_CARRIER = "carrier"
        private const val K_PHONE = "phone"
        private const val K_PHONES = "phones"
        private const val K_AUTO_RECORD = "auto_record"
        private const val K_CAPTURE_OTHER = "capture_other_pay"
        private const val K_BUDGET = "monthly_budget"
        private const val K_REMINDERS = "reminders"
        private const val K_CARRIER_ON_CARD = "carrier_on_card"
        private const val K_THEME = "theme"
        private const val K_SENT = "reminders_sent"

        private fun phonesToJson(list: List<PhoneEntry>): JSONArray {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("label", it.label).put("number", it.number)) }
            return arr
        }

        private fun parsePhones(text: String): List<PhoneEntry> = runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val n = o.optString("number")
                if (n.isBlank()) null else PhoneEntry(o.optString("label"), n)
            }
        }.getOrDefault(emptyList())
        private const val K_PAY = "pay_apps"
        private const val K_RECORD_LOCATION = "record_location"
        private const val K_BACKUP_URI = "backup_uri"
        private const val K_AUTO_BACKUP = "auto_backup"
        private const val K_LAST_BACKUP = "last_backup"
        private const val K_BACKUP_ERROR = "backup_error"
        private const val K_CARD_APPS = "card_apps"
        private const val K_QUICK_COLLAPSED = "quick_collapsed"

        /** 手機條碼載具格式：/ 加 7 碼（數字、大寫英文、+ - .） */
        val CARRIER_REGEX = Regex("^/[0-9A-Z.+\\-]{7}$")

        private fun appsToJson(list: List<QuickApp>): JSONArray {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("pkg", it.pkg).put("label", it.label)) }
            return arr
        }

        private fun parseApps(text: String?): List<QuickApp> = runCatching {
            val arr = JSONArray(text ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val pkg = o.optString("pkg")
                if (pkg.isBlank()) null else QuickApp(pkg, o.optString("label", pkg))
            }
        }.getOrDefault(emptyList())

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun instance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
    }
}
