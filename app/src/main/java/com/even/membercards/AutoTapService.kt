package com.even.membercards

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.even.membercards.data.AutoStep
import com.even.membercards.data.AutoTap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 自動點到會員條碼頁的無障礙服務。
 * - 執行：從會員卡夾開啟店家 App 後，依序點擊設定的按鈕文字／位置
 * - 錄製：在店家 App 上蓋一層透明層，攔下每次點擊記成步驟，再把點擊轉給店家 App
 *   （不依賴 App 送出點擊事件，Flutter／網頁型 App 也能錄）
 * 其餘時間不處理任何事件，也不讀取或保存畫面內容。
 */
class AutoTapService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    // 執行中的工作
    private var pkg = ""
    private var steps: List<AutoStep> = emptyList()
    private var index = 0
    private var stepStart = 0L
    private var jobStart = 0L
    private var running = false
    private val tick = Runnable { step() }

    // 錄製用的覆蓋層
    private var catcher: View? = null
    private var catcherParams: WindowManager.LayoutParams? = null
    private var bar: View? = null
    private var barText: TextView? = null
    private val recordWatch = object : Runnable {
        override fun run() = watchRecording()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoTap.service = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (AutoTap.service === this) AutoTap.service = null
        running = false
        handler.removeCallbacksAndMessages(null)
        hideRecordOverlay()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要處理事件：執行與錄製都用輪詢目前畫面的方式
    }

    // ================= 錄製 =================

    /** AutoTap.startRecording 時呼叫：等店家 App 出現就蓋上錄製層 */
    fun beginRecordWatch() {
        handler.removeCallbacks(recordWatch)
        handler.postDelayed(recordWatch, 500)
    }

    /** AutoTap.stopRecording 時呼叫 */
    fun endRecording() {
        handler.removeCallbacks(recordWatch)
        hideRecordOverlay()
    }

    private fun watchRecording() {
        if (!AutoTap.isRecording()) {
            hideRecordOverlay()
            return
        }
        val target = AutoTap.recordingPackage
        val active = activeAppPackage()
        when {
            active == target -> showRecordOverlay()
            active == packageName || active == launcherPackage() -> hideRecordOverlay()
        }
        barText?.text = "● 錄製中 · 已錄 ${AutoTap.recorded.size} 步"
        handler.postDelayed(recordWatch, 600)
    }

    private fun activeAppPackage(): String? = runCatching {
        rootInActiveWindow?.packageName?.toString()
    }.getOrNull()

    private fun launcherPackage(): String? = runCatching {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
    }.getOrNull()

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun showRecordOverlay() {
        if (catcher != null) return
        // 全螢幕透明攔截層
        val c = FrameLayout(this)
        val cp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        c.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    downTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val dur = System.currentTimeMillis() - downTime
                    val moved = hypot(e.rawX - downX, e.rawY - downY)
                    if (moved < dp(16f)) {
                        recordTap(downX, downY)
                        passThrough(downX, downY, downX, downY, if (dur > 450) dur.coerceAtMost(1500) else 60)
                    } else {
                        // 滑動（捲動）：直接轉給 App，不記成步驟
                        passThrough(downX, downY, e.rawX, e.rawY, dur.coerceIn(80, 1200))
                    }
                }
            }
            true
        }
        runCatching { wm.addView(c, cp) }.onFailure { return }
        catcher = c
        catcherParams = cp

        // 上方提示列：● 錄製中 · 完成 · ✕
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(8f), dp(8f), dp(8f))
            background = GradientDrawable().apply {
                cornerRadius = dp(22f).toFloat()
                setColor(Color.parseColor("#F00A111C"))
                setStroke(dp(1f), Color.parseColor("#FF5470"))
            }
        }
        val t = TextView(this).apply {
            text = "● 錄製中 · 已錄 0 步"
            setTextColor(Color.parseColor("#FF5470"))
            textSize = 14f
        }
        fun button(label: String, color: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(color))
            textSize = 14f
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            setOnClickListener { onClick() }
        }
        b.addView(t)
        b.addView(button("完成", "#38E8FF") { finishRecording() })
        b.addView(button("撤銷", "#7D90AA") { if (AutoTap.recorded.isNotEmpty()) AutoTap.recorded.removeAt(AutoTap.recorded.lastIndex) })
        val bp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36f)
        }
        runCatching { wm.addView(b, bp) }
        bar = b
        barText = t
    }

    private fun hideRecordOverlay() {
        catcher?.let { runCatching { wm.removeView(it) } }
        bar?.let { runCatching { wm.removeView(it) } }
        catcher = null
        catcherParams = null
        bar = null
        barText = null
    }

    /** 按「完成」：收起覆蓋層並回到會員卡夾（會員卡夾會把步驟帶入） */
    private fun finishRecording() {
        hideRecordOverlay()
        handler.removeCallbacks(recordWatch)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    /** 記下這次點擊：點到的元件有文字就記文字，否則記位置 */
    private fun recordTap(x: Float, y: Float) {
        val root = targetRoot()
        val label = root?.let { labelAt(it, x.toInt(), y.toInt()) }
        val step = if (!label.isNullOrBlank() && label.length <= 30) {
            AutoStep(AutoStep.TEXT, text = label)
        } else {
            val (w, h) = screenSize()
            AutoStep(AutoStep.POS, x = (x / w).coerceIn(0f, 1f), y = (y / h).coerceIn(0f, 1f))
        }
        AutoTap.onRecordedClick(step)
        barText?.text = "● 錄製中 · 已錄 ${AutoTap.recorded.size} 步"
    }

    /** 店家 App 視窗的根節點（覆蓋層之下） */
    private fun targetRoot(): AccessibilityNodeInfo? {
        val target = AutoTap.recordingPackage
        runCatching {
            windows.forEach { w ->
                val r = w.root ?: return@forEach
                if (r.packageName?.toString() == target) return r
            }
        }
        return rootInActiveWindow?.takeIf { it.packageName?.toString() == target }
    }

    /** 找出點擊位置上最深、且有文字的元件 */
    private fun labelAt(root: AccessibilityNodeInfo, x: Int, y: Int): String? {
        var best: String? = null
        var bestArea = Long.MAX_VALUE
        val r = Rect()
        fun visit(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40) return
            n.getBoundsInScreen(r)
            if (!r.contains(x, y) || !n.isVisibleToUser) return
            val text = n.text?.toString()?.trim().orEmpty().ifBlank { n.contentDescription?.toString()?.trim().orEmpty() }
            val area = r.width().toLong() * r.height()
            if (text.isNotBlank() && area <= bestArea) {
                best = text
                bestArea = area
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        // 範圍太大的文字（例如整頁標題）不可靠，改記位置
        val (w, h) = screenSize()
        return if (bestArea > w.toLong() * h / 6) null else best
    }

    /** 暫時讓覆蓋層不攔截，把點擊／滑動轉給下面的 App */
    private fun passThrough(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val c = catcher ?: return
        val p = catcherParams ?: return
        p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(c, p) }
        val path = Path().apply {
            moveTo(x1, y1)
            if (abs(x2 - x1) > 1 || abs(y2 - y1) > 1) lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        val restore = Runnable {
            val cc = catcher ?: return@Runnable
            val pp = catcherParams ?: return@Runnable
            pp.flags = pp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { wm.updateViewLayout(cc, pp) }
        }
        // 等覆蓋層真的變成不攔截後再送出手勢
        handler.postDelayed({
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.postDelayed(restore, 150)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    handler.postDelayed(restore, 150)
                }
            }, handler)
            if (!ok) handler.postDelayed(restore, 150)
        }, 60)
    }

    private fun screenSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        val display = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        display?.getRealMetrics(dm)
        return if (dm.widthPixels > 0) dm.widthPixels to dm.heightPixels
        else resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
    }

    // ================= 執行步驟 =================

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
                if (elapsed < 1800) {
                    schedule(300)
                    return
                }
                val (w, h) = screenSize()
                tapAt(s.x * w, s.y * h)
                next(1000)
            }

            else -> {
                val node = find(root, s.text)
                if (node != null && click(node)) {
                    next(1000)
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
