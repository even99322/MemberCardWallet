package com.even.membercards

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.even.membercards.data.AutoStep
import com.even.membercards.data.AutoTap

/**
 * 自動點到會員條碼頁的無障礙服務。
 * - 執行：從會員卡夾開啟店家 App 後，依序點擊設定的按鈕文字／位置
 * - 錄製：記下使用者在店家 App 中的點擊，轉成步驟
 * 其餘時間不處理任何事件，也不讀取或保存畫面內容。
 */
class AutoTapService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    private var pkg = ""
    private var steps: List<AutoStep> = emptyList()
    private var index = 0
    private var stepStart = 0L
    private var jobStart = 0L
    private var running = false

    private val tick = Runnable { step() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoTap.service = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (AutoTap.service === this) AutoTap.service = null
        running = false
        handler.removeCallbacks(tick)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (AutoTap.service === this) AutoTap.service = null
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // 只有錄製中才看點擊事件
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED || !AutoTap.isRecording()) return
        val p = event.packageName?.toString() ?: return
        if (p != AutoTap.recordingPackage) return
        val label = event.text?.joinToString(" ")?.trim().orEmpty()
            .ifBlank { event.contentDescription?.toString()?.trim().orEmpty() }
            .ifBlank { event.source?.let { labelOf(it) }.orEmpty() }
        if (label.isNotBlank() && label.length <= 30) {
            AutoTap.onRecordedClick(AutoStep(AutoStep.TEXT, text = label))
        } else {
            // 沒有文字的圖示按鈕：記位置
            val src = event.source ?: return
            val r = Rect()
            src.getBoundsInScreen(r)
            val dm = resources.displayMetrics
            if (r.isEmpty || dm.widthPixels == 0) return
            AutoTap.onRecordedClick(
                AutoStep(
                    AutoStep.POS,
                    x = (r.exactCenterX() / dm.widthPixels).coerceIn(0f, 1f),
                    y = (r.exactCenterY() / dm.heightPixels).coerceIn(0f, 1f),
                )
            )
        }
    }

    /** 節點或其子節點中的文字 */
    private fun labelOf(node: AccessibilityNodeInfo, depth: Int = 0): String? {
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        if (depth > 3) return null
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            labelOf(c, depth + 1)?.let { return it }
        }
        return null
    }

    // ---------- 執行步驟 ----------

    fun startJob(targetPackage: String, list: List<AutoStep>) {
        pkg = targetPackage
        steps = list
        index = 0
        jobStart = System.currentTimeMillis()
        stepStart = jobStart + 800
        running = true
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 800)
    }

    private fun schedule(ms: Long) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, ms)
    }

    private fun next(delay: Long) {
        index++
        stepStart = System.currentTimeMillis() + delay
        schedule(delay)
    }

    private fun fail(msg: String) {
        running = false
        Toast.makeText(applicationContext, "自動點擊停止：$msg", Toast.LENGTH_LONG).show()
    }

    private fun step() {
        if (!running) return
        if (index >= steps.size) {
            running = false
            return
        }
        val now = System.currentTimeMillis()
        if (now - jobStart > 60_000) return fail("超過 60 秒")
        val elapsed = now - stepStart
        val s = steps[index]

        if (s.type == AutoStep.WAIT) {
            if (elapsed >= (s.seconds * 1000).toLong()) next(100) else schedule(150)
            return
        }

        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != pkg) {
            if (elapsed > 12_000) fail("等不到 App 畫面") else schedule(400)
            return
        }

        when (s.type) {
            AutoStep.POS -> {
                // 位置點擊沒辦法確認畫面載入完成，先多等一下
                if (elapsed < 1500) {
                    schedule(300)
                    return
                }
                val dm = resources.displayMetrics
                tapAt(s.x * dm.widthPixels, s.y * dm.heightPixels)
                next(900)
            }

            else -> {
                val node = find(root, s.text)
                if (node != null && click(node)) {
                    next(900)
                } else if (elapsed > 10_000) {
                    fail("找不到「${s.text}」")
                } else {
                    schedule(400)
                }
            }
        }
    }

    private fun find(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (text.isBlank()) return null
        val found = root.findAccessibilityNodeInfosByText(text).filter { it.isVisibleToUser }
        if (found.isEmpty()) return null
        return found.firstOrNull {
            it.text?.toString()?.trim() == text || it.contentDescription?.toString()?.trim() == text
        } ?: found.first()
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && !n.isClickable && depth < 8) {
            n = n.parent
            depth++
        }
        if (n != null && n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // 找不到可點擊的節點：直接點文字中心
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return tapAt(r.exactCenterX(), r.exactCenterY())
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
