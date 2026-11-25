package com.even.membercards

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.even.membercards.data.AppSettings
import com.even.membercards.data.EntryType
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.Shortcuts
import com.even.membercards.data.formatNumber
import com.even.membercards.ui.Palettes
import java.time.YearMonth

/** 桌面小工具：本月支出／收入／結餘，＋支出、＋收入一鍵記帳。顏色跟著 App 主題。 */
class LedgerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        const val ACTION_OPEN_LEDGER = "com.even.membercards.OPEN_LEDGER"
        const val EXTRA_TYPE = "entry_type"

        private val handler = Handler(Looper.getMainLooper())
        private var pending: Runnable? = null

        /** 記帳資料或主題變更後呼叫（稍微延遲合併多次更新） */
        fun updateAll(context: Context) {
            val app = context.applicationContext
            pending?.let { handler.removeCallbacks(it) }
            val r = Runnable {
                runCatching {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, LedgerWidget::class.java))
                    ids.forEach { update(app, manager, it) }
                }
            }
            pending = r
            handler.postDelayed(r, 300)
        }

        private fun intent(context: Context, action: String, type: String?, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                code,
                Intent(context, MainActivity::class.java)
                    .setAction(action)
                    .apply { if (type != null) putExtra(EXTRA_TYPE, type) }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val ledger = LedgerRepository.instance(context)
            val p = Palettes.of(AppSettings.instance(context).theme)
            val ym = YearMonth.now()
            val exp = ledger.monthExpense(ym)
            val inc = ledger.monthIncome(ym)
            val bal = inc - exp

            fun c(color: Color) = color.toArgb()
            val v = RemoteViews(context.packageName, R.layout.widget_ledger)

            // 底色、按鈕、標籤依主題染色
            v.setInt(R.id.lw_bg, "setColorFilter", c(p.surface))
            v.setInt(R.id.lw_bg, "setImageAlpha", if (p.isLight) 250 else 242)
            val btn = if (p.isLight) p.surface3 else p.bg
            v.setInt(R.id.lw_add_exp_bg, "setColorFilter", c(btn))
            v.setInt(R.id.lw_add_inc_bg, "setColorFilter", c(btn))
            v.setInt(R.id.lw_badge_bg, "setColorFilter", c(p.primary))
            v.setTextColor(R.id.lw_badge, c(if (p.isLight) Color.White else p.bg))

            v.setTextViewText(R.id.lw_month, "${ym.year}年${ym.monthValue}月")
            v.setTextColor(R.id.lw_month, c(p.text))
            listOf(R.id.lw_exp_label, R.id.lw_inc_label, R.id.lw_bal_label).forEach { v.setTextColor(it, c(p.textDim)) }

            v.setTextViewText(R.id.lw_exp, formatNumber(exp))
            v.setTextViewText(R.id.lw_inc, formatNumber(inc))
            v.setTextViewText(R.id.lw_bal, (if (bal < 0) "-" else "") + formatNumber(kotlin.math.abs(bal)))
            v.setTextColor(R.id.lw_exp, c(p.text))
            v.setTextColor(R.id.lw_inc, c(p.text))
            v.setTextColor(R.id.lw_bal, c(if (bal < 0) p.danger else p.text))
            v.setTextColor(R.id.lw_add_exp_text, c(p.text))
            v.setTextColor(R.id.lw_add_inc_text, c(p.text))

            v.setOnClickPendingIntent(R.id.lw_root, intent(context, ACTION_OPEN_LEDGER, null, 30))
            v.setOnClickPendingIntent(R.id.lw_add_exp, intent(context, Shortcuts.ACTION_ADD_ENTRY, EntryType.EXPENSE, 31))
            v.setOnClickPendingIntent(R.id.lw_add_inc, intent(context, Shortcuts.ACTION_ADD_ENTRY, EntryType.INCOME, 32))
            manager.updateAppWidget(id, v)
        }
    }
}
