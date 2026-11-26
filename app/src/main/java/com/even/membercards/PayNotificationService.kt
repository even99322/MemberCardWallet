package com.even.membercards

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast
import com.even.membercards.data.AppSettings
import com.even.membercards.data.BankParser
import com.even.membercards.data.CapturedNotice
import com.even.membercards.data.EntryType
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
        val method = sourceMethod(sbn.packageName, headline, text, settings) ?: return

        val ledger = LedgerRepository.instance(applicationContext)

        // 銀行 App：帳戶轉出／入帳／提款
        val isBankSource = settings.cardApps.any { it.pkg == sbn.packageName }
        val bank = if (isBankSource) BankParser.parse(headline, text) else null
        if (bank != null) {
            val type = when (bank.kind) {
                BankParser.Kind.OUT -> EntryType.EXPENSE
                BankParser.Kind.IN -> EntryType.INCOME
                BankParser.Kind.WITHDRAW -> EntryType.TRANSFER
            }
            val category = when (bank.kind) {
                BankParser.Kind.OUT -> "轉帳"
                BankParser.Kind.IN -> if (ledger.categories(EntryType.INCOME).any { it.name == "轉帳充正" }) "轉帳充正" else "其他收入"
                BankParser.Kind.WITHDRAW -> ""
            }
            val cash = if (bank.kind == BankParser.Kind.WITHDRAW) ledger.cashAccountId() else ""
            val acc = ledger.resolveAutoAccount(sbn.packageName, method, "$headline\n$text", bankEvent = true)
            // 沒有現金帳戶時，提款改記成支出
            val finalType = if (type == EntryType.TRANSFER && cash.isBlank()) EntryType.EXPENSE else type
            val finalCategory = if (finalType == EntryType.EXPENSE && category.isBlank()) "其他" else category
            val entry = ledger.addAuto(
                amount = bank.amount,
                merchant = bank.title,
                method = method,
                key = "bank:" + sbn.key + "|" + text.hashCode(),
                category = finalCategory,
                type = finalType,
                bank = true,
                toAccount = cash,
                accountId = acc,
            ) ?: return
            val paired = ledger.pairBankTransfer(entry)
            Toast.makeText(
                applicationContext,
                if (paired.isTransfer && paired.id != entry.id || paired.key.contains("||")) "已合併為帳戶間轉帳 ${formatMoney(entry.amount)}"
                else "已自動記帳：${bank.title} ${formatMoney(entry.amount)}",
                Toast.LENGTH_SHORT,
            ).show()
            ledger.logCapture(CapturedNotice(System.currentTimeMillis(), method, headline, text.take(400), entry.amount))
            return
        }

        val result = PayParser.parse(headline, text)
        var recorded: Double? = null
        if (result != null) {
            val cards = CardRepository.instance(applicationContext).cards.toList()
            val merchant = result.merchant
            val source = if (method == "LINE Pay" && sbn.packageName == LINE_PACKAGE) "linepay" else sbn.packageName
            val entry = ledger.addAuto(
                amount = result.amount,
                merchant = merchant,
                method = method,
                key = sbn.key + "|" + text.hashCode(),
                category = Categories.guess(merchant, cards),
                accountId = ledger.resolveAutoAccount(source, method, "$headline\n$text", bankEvent = false),
            )
            if (entry != null) {
                recorded = entry.amount
                Toast.makeText(
                    applicationContext,
                    "已自動記帳 ${formatMoney(entry.amount)}" + (if (merchant.isNotBlank()) " · $merchant" else "") +
                        if (settings.recordLocation) "（記錄位置中）" else "",
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
    private fun sourceMethod(pkg: String, headline: String, text: String, settings: AppSettings): String? {
        if (pkg == packageName) return null
        if (pkg == LINE_PACKAGE) {
            // 標題是「LINE Pay」，或內文開頭就是「LINE Pay 付款…」
            val isPay = headline.contains("LINE Pay", ignoreCase = true) ||
                text.trimStart().startsWith("LINE Pay", ignoreCase = true)
            return if (isPay) "LINE Pay" else null
        }
        if (pkg.contains("linepay", ignoreCase = true)) return "LINE Pay"
        settings.cardApps.firstOrNull { it.pkg == pkg }?.let { return it.label }
        if (settings.captureOtherPay) {
            settings.payApps.firstOrNull { it.pkg == pkg }?.let { return it.label }
        }
        return null
    }

    companion object {
        const val LINE_PACKAGE = "jp.naver.line.android"
    }
}
