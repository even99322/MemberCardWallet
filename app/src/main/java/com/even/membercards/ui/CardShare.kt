package com.even.membercards.ui

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.even.membercards.data.MemberCard
import org.json.JSONObject
import java.util.UUID

/**
 * 分享卡片：文字中附一段 MCW1: 編碼，對方用會員卡夾開啟（分享給會員卡夾）就能一鍵匯入。
 * 不含照片、使用紀錄與自動點擊步驟。
 */
object CardShare {
    private const val MARK = "MCW1:"

    fun share(context: Context, card: MemberCard) {
        val o = JSONObject()
            .put("name", card.name)
            .put("number", card.number)
            .put("format", card.format)
            .put("color", card.color)
            .put("note", card.note)
            .put("appPackage", card.appPackage)
            .put("appLabel", card.appLabel)
            .put("link", card.link)
            .put("expiry", card.expiry)
            .put("kind", card.kind)
        val code = Base64.encodeToString(o.toString().toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        val text = buildString {
            append(if (card.isCoupon) "【票券】" else "【會員卡】").append(card.name).append('\n')
            if (card.number.isNotBlank()) append("號碼：").append(card.number).append('\n')
            append("\n用「會員卡夾」App 匯入：長按複製整段，分享給會員卡夾即可。\n")
            append(MARK).append(code)
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "分享${if (card.isCoupon) "票券" else "會員卡"}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 從分享文字中解出卡片；不是會員卡夾格式則回傳 null。 */
    fun decode(text: String): MemberCard? {
        val i = text.indexOf(MARK)
        if (i < 0) return null
        val code = text.substring(i + MARK.length).trim().takeWhile { !it.isWhitespace() }
        return runCatching {
            val o = JSONObject(String(Base64.decode(code, Base64.NO_WRAP or Base64.URL_SAFE)))
            MemberCard.fromJson(o).copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                lastUsed = 0L,
                useCount = 0,
                favorite = false,
                folder = "",
            )
        }.getOrNull()
    }
}
