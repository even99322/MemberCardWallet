package com.even.membercards.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** 使用者設定（SharedPreferences + Compose 狀態，改了畫面會即時更新）。 */
class AppSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val lockState = mutableStateOf(prefs.getBoolean(K_LOCK, false))
    private val viewState = mutableStateOf(prefs.getString(K_VIEW, VIEW_GRID) ?: VIEW_GRID)
    private val sortState = mutableStateOf(prefs.getString(K_SORT, SORT_SMART) ?: SORT_SMART)
    private val brightState = mutableStateOf(prefs.getBoolean(K_BRIGHT, true))

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

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun instance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
    }
}
