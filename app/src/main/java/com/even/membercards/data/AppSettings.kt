package com.even.membercards.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** 快捷列上的付款 App */
data class QuickApp(val pkg: String, val label: String)

/** 使用者設定（SharedPreferences + Compose 狀態，改了畫面會即時更新）。 */
class AppSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val lockState = mutableStateOf(prefs.getBoolean(K_LOCK, false))
    private val viewState = mutableStateOf(prefs.getString(K_VIEW, VIEW_GRID) ?: VIEW_GRID)
    private val sortState = mutableStateOf(prefs.getString(K_SORT, SORT_SMART) ?: SORT_SMART)
    private val brightState = mutableStateOf(prefs.getBoolean(K_BRIGHT, true))
    private val carrierState = mutableStateOf(prefs.getString(K_CARRIER, "") ?: "")
    private val phoneState = mutableStateOf(prefs.getString(K_PHONE, "") ?: "")
    private val payState = mutableStateOf(parseApps(prefs.getString(K_PAY, null)))
    private val quickCollapsedState = mutableStateOf(prefs.getBoolean(K_QUICK_COLLAPSED, false))

    /** 手機條碼載具（例如 /ABC1234），空字串 = 未設定 */
    var carrier: String
        get() = carrierState.value
        set(v) {
            carrierState.value = v
            prefs.edit().putString(K_CARRIER, v).apply()
        }

    /** 常用手機號碼（結帳報會員用） */
    var phone: String
        get() = phoneState.value
        set(v) {
            phoneState.value = v
            prefs.edit().putString(K_PHONE, v).apply()
        }

    /** 快捷列的常用付款 App（依顯示順序） */
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
        .put("carrier", carrier)
        .put("phone", phone)
        .put("payApps", appsToJson(payApps))

    fun applyQuickJson(o: JSONObject) {
        o.optString("carrier").takeIf { it.isNotBlank() }?.let { carrier = it }
        o.optString("phone").takeIf { it.isNotBlank() }?.let { phone = it }
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

        val sortLabels = linkedMapOf(
            SORT_SMART to "智慧（常用＋最近）",
            SORT_USAGE to "使用次數",
            SORT_NAME to "名稱",
            SORT_ADDED to "新增時間",
            SORT_EXPIRY to "到期日",
        )

        private const val K_LOCK = "lock"
        private const val K_VIEW = "view"
        private const val K_SORT = "sort"
        private const val K_BRIGHT = "bright"
        private const val K_CARRIER = "carrier"
        private const val K_PHONE = "phone"
        private const val K_PAY = "pay_apps"
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
