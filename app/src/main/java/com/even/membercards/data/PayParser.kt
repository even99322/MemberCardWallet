package com.even.membercards.data

/**
 * 從付款通知文字中解析金額與商家。
 * 規則刻意寬鬆，解析失敗的通知仍會留在「擷取紀錄」讓使用者手動記一筆。
 */
object PayParser {
    data class Result(val amount: Double, val merchant: String)

    private val payWords = listOf(
        "付款", "支付", "消費", "扣款", "交易成功", "交易完成", "已付", "結帳", "刷卡", "簽帳", "授權成功",
        "payment", "paid",
    )
    /** 一定不是支出 */
    private val excludeWords = listOf(
        "退款", "退費", "收款", "已收到", "儲值", "入帳", "加值", "轉帳", "取消交易", "交易失敗", "付款失敗",
    )

    /** 通常不是支出（回饋、帳單提醒），但若有明確的付款完成字樣就不排除。
     *  例：LINE Pay「付款完成。商店名稱: 統一超商 指定商店加碼回饋最高享16%」 */
    private val softExcludeWords = listOf("回饋", "刷卡金", "帳單", "繳款")
    private val strongPayWords = listOf(
        "付款完成", "付款成功", "交易成功", "交易完成", "消費成功", "扣款成功", "已付款", "刷卡消費", "消費通知",
        "消費金額", "交易金額", "授權成功",
    )

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val amountAfterLabel =
        Regex("""(?:金額|總額|總計|合計|實付|付款|支付|消費|扣款)[^0-9\n]{0,10}?(?:NT\$|NTD|TWD|\$|＄)?\s*$NUM""")
    private val amountCurrency = Regex("""(?:NT\$|NTD|TWD|＄|\$)\s*$NUM""")
    private val amountYuan = Regex("""$NUM\s*元""")

    private val merchantPatterns = listOf(
        Regex("""(?:商店名稱|商店|店家名稱|店家|特店名稱|特店|商家名稱|商家|交易對象)\s*[:：]?\s*([^\n，,。:：]{1,30})"""),
        Regex("""(?:於|在|至)\s*([^\n，,。\s]{1,30}?)\s*(?:消費|付款|完成付款|支付|交易|結帳)"""),
        Regex("""向\s*([^\n，,。\s]{1,30}?)\s*(?:付款|支付)"""),
    )

    fun parse(title: String, text: String): Result? {
        val all = "$title\n$text"
        if (excludeWords.any { all.contains(it) }) return null
        if (softExcludeWords.any { all.contains(it) } && strongPayWords.none { all.contains(it) }) return null
        if (payWords.none { all.contains(it, ignoreCase = true) }) return null
        val amount = guessAmount(all) ?: return null
        val merchant = guessMerchant(all)
        return Result(amount, merchant)
    }

    /** 只抓金額（給「手動記一筆」預填用）。 */
    fun guessAmount(text: String): Double? {
        val raw = (amountAfterLabel.find(text) ?: amountCurrency.find(text) ?: amountYuan.find(text))
            ?.groupValues?.get(1) ?: return null
        val v = raw.replace(",", "").toDoubleOrNull() ?: return null
        return if (v <= 0 || v > 2_000_000) null else v
    }

    fun guessMerchant(text: String): String =
        merchantPatterns.firstNotNullOfOrNull { r ->
            r.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('。', '，', '!', '！')?.takeIf { it.length in 1..30 }
        } ?: ""
}
