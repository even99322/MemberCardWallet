package com.even.membercards

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CapturedNotice
import com.even.membercards.data.CardRepository
import com.even.membercards.data.Categories
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.PayParser
import com.even.membercards.data.formatMoney

/**
 * 讀取付款通知自動記帳。
 * - LINE：只處理「LINE Pay」官方帳號的訊息
 * - LINE Pay 相關 App
 * - （可選）快捷列設定的其他付款 App
 */
class PayNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        runCatching { handle(sbn) }
    }

    private fun handle(sbn: StatusBarNotification) {
        val settings = AppSettings.instance(applicationContext)
        if (!settings.autoRecord) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val convTitle = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        val (sender, lastMessage) = lastMessagingStyle(ex)
        val text = lastMessage
            ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""
        if (text.isBlank()) return

        val headline = listOf(title, convTitle, sender.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val method = sourceMethod(sbn.packageName, headline, settings) ?: return

        val ledger = LedgerRepository.instance(applicationContext)
        val result = PayParser.parse(headline, text)
        var recorded: Double? = null
        if (result != null) {
            val cards = CardRepository.instance(applicationContext).cards.toList()
            val merchant = result.merchant
            val entry = ledger.addAuto(
                amount = result.amount,
                merchant = merchant,
                method = method,
                key = sbn.key + "|" + text.hashCode(),
                category = Categories.guess(merchant, cards),
            )
            if (entry != null) {
                recorded = entry.amount
                Toast.makeText(
                    applicationContext,
                    "已自動記帳 ${formatMoney(entry.amount)}" + if (merchant.isNotBlank()) " · $merchant" else "",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                return // 重複通知，不重複記錄
            }
        }
        ledger.logCapture(CapturedNotice(System.currentTimeMillis(), method, headline, text.take(400), recorded))
    }

    /** MessagingStyle（LINE 用這種）：取最新一則訊息與發送者。 */
    private fun lastMessagingStyle(ex: Bundle): Pair<String?, String?> {
        @Suppress("DEPRECATION")
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null to null
        val last = msgs.lastOrNull() as? Bundle ?: return null to null
        return last.getCharSequence("sender")?.toString() to last.getCharSequence("text")?.toString()
    }

    /** 判斷通知來源是否要處理，回傳付款方式名稱。 */
    private fun sourceMethod(pkg: String, headline: String, settings: AppSettings): String? {
        if (pkg == packageName) return null
        if (pkg == LINE_PACKAGE) return if (headline.contains("LINE Pay", ignoreCase = true)) "LINE Pay" else null
        if (pkg.contains("linepay", ignoreCase = true)) return "LINE Pay"
        if (settings.captureOtherPay) {
            settings.payApps.firstOrNull { it.pkg == pkg }?.let { return it.label }
        }
        return null
    }

    companion object {
        const val LINE_PACKAGE = "jp.naver.line.android"
    }
}
