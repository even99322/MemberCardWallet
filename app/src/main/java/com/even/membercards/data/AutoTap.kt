package com.even.membercards.data

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import com.even.membercards.AutoTapService
import org.json.JSONArray
import org.json.JSONObject

/** 自動點擊的一個步驟。 */
data class AutoStep(
    /** text = 點擊含有這段文字的按鈕；pos = 點擊畫面位置；wait = 等待 */
    val type: String = TEXT,
    val text: String = "",
    /** 位置（畫面寬高的比例 0–1） */
    val x: Float = 0f,
    val y: Float = 0f,
    val seconds: Float = 1f,
) {
    fun toJson(): JSONObject = JSONObject().put("type", type).put("text", text)
        .put("x", x.toDouble()).put("y", y.toDouble()).put("seconds", seconds.toDouble())

    fun describe(): String = when (type) {
        POS -> "點擊位置 ${(x * 100).toInt()}%, ${(y * 100).toInt()}%"
        WAIT -> "等待 ${seconds} 秒"
        else -> "點「$text」"
    }

    companion object {
        const val TEXT = "text"
        const val POS = "pos"
        const val WAIT = "wait"

        fun fromJson(o: JSONObject) = AutoStep(
            type = o.optString("type", TEXT),
            text = o.optString("text"),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            seconds = o.optDouble("seconds", 1.0).toFloat(),
        )

        fun listToJson(list: List<AutoStep>): JSONArray = JSONArray().apply { list.forEach { put(it.toJson()) } }

        fun listFromJson(arr: JSONArray?): List<AutoStep> =
            if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { fromJson(it) } }
    }
}

/**
 * 自動點擊的協調者：記錄無障礙服務是否已連線、目前是否在錄製。
 * 服務只在「從會員卡夾開啟店家 App」或「錄製中」時才會動作。
 */
object AutoTap {
    @Volatile
    var service: AutoTapService? = null

    /** 錄製中的目標 App（空字串 = 沒在錄製） */
    @Volatile
    var recordingPackage: String = ""
        private set
    private var recordingStart = 0L

    /** 錄到的步驟（Compose 狀態，編輯畫面會即時看到） */
    val recorded = mutableStateListOf<AutoStep>()

    fun isEnabled(context: Context): Boolean {
        if (service != null) return true
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, AutoTapService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun startRecording(pkg: String) {
        recorded.clear()
        recordingPackage = pkg
        recordingStart = System.currentTimeMillis()
    }

    fun isRecording(): Boolean {
        if (recordingPackage.isNotBlank() && System.currentTimeMillis() - recordingStart > 5 * 60_000) recordingPackage = ""
        return recordingPackage.isNotBlank()
    }

    /** 結束錄製並回傳錄到的步驟 */
    fun stopRecording(): List<AutoStep> {
        recordingPackage = ""
        return recorded.toList()
    }

    /** 服務收到使用者在目標 App 的點擊時呼叫 */
    fun onRecordedClick(step: AutoStep) {
        // 連續點同一個按鈕只記一次
        if (recorded.lastOrNull() == step) return
        recorded.add(step)
    }

    /** 開啟店家 App 後依序執行卡片的步驟 */
    fun runFor(context: Context, card: MemberCard) {
        if (card.autoSteps.isEmpty() || card.appPackage.isBlank()) return
        val svc = service
        if (svc == null) {
            Toast.makeText(context, "自動點擊未啟用：請在會員卡夾設定中開啟無障礙權限", Toast.LENGTH_LONG).show()
            return
        }
        svc.startJob(card.appPackage, card.autoSteps)
    }
}
