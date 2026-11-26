package com.even.membercards.data

/**
 * 銀行 App 帳戶異動推播（轉出、入帳、提款）。
 * 例（中國信託）：
 *  【轉帳交易結果通知】您於2026/11/26 16:07轉出一筆9,000元的臺幣款項
 *  【存款入帳通知】您於2026/11/26 16:08有一筆10,000元的臺幣存款入帳通知
 */
object BankParser {
    enum class Kind { OUT, IN, WITHDRAW }

    data class Result(val kind: Kind, val amount: Double, val title: String)

    private val failWords = listOf("失敗", "未成功", "不成功", "取消", "退回", "預約", "待處理", "驗證碼", "OTP")
    private val withdrawWords = listOf("提款", "提領", "ATM提", "無卡提")
    private val outWords = listOf("轉出", "匯出", "扣款", "轉帳支出", "繳費", "自動扣繳")
    private val inWords = listOf("存款入帳", "轉入", "匯入", "入帳", "薪資", "存入")

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val amountYuan = Regex("""(?:NT\$|NTD|TWD|新臺幣|新台幣|臺幣|台幣)?\s*$NUM\s*元""")
    private val amountCurrency = Regex("""(?:NT\$|NTD|TWD|＄|\$)\s*$NUM""")

    fun parse(title: String, text: String): Result? {
        val all = "$title\n$text"
        if (failWords.any { all.contains(it, ignoreCase = true) }) return null
        val kind = when {
            withdrawWords.any { all.contains(it) } -> Kind.WITHDRAW
            outWords.any { all.contains(it) } -> Kind.OUT
            inWords.any { all.contains(it) } -> Kind.IN
            else -> return null
        }
        val raw = (amountYuan.find(all) ?: amountCurrency.find(all))?.groupValues?.get(1) ?: return null
        val amount = raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 && it < 100_000_000 } ?: return null
        val label = when (kind) {
            Kind.OUT -> if (all.contains("繳費") || all.contains("扣繳")) "自動扣繳" else "轉出"
            Kind.IN -> if (all.contains("薪資")) "薪資入帳" else "存款入帳"
            Kind.WITHDRAW -> "ATM 提款"
        }
        return Result(kind, amount, label)
    }
}
