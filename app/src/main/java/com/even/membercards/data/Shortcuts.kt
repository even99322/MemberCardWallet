package com.even.membercards.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.even.membercards.MainActivity

/** 桌面捷徑：長按 App 圖示的常用卡片清單，以及釘選到主畫面的單張卡片。 */
object Shortcuts {
    const val ACTION_OPEN_CARD = "com.even.membercards.OPEN_CARD"
    const val EXTRA_CARD_ID = "card_id"

    private fun shortcutId(cardId: String) = "card_$cardId"

    fun openIntent(context: Context, cardId: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_CARD)
            .putExtra(EXTRA_CARD_ID, cardId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun icon(card: MemberCard): IconCompat {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = card.color.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        val fg = if (android.graphics.Color.luminance(bg) > 0.55f) 0xFF1B1B1F.toInt() else android.graphics.Color.WHITE
        val text = card.name.trim().take(1).ifBlank { "卡" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = size * 0.48f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, size / 2f, y, paint)
        return IconCompat.createWithBitmap(bmp)
    }

    private fun build(context: Context, card: MemberCard): ShortcutInfoCompat {
        val label = card.name.ifBlank { "會員卡" }
        return ShortcutInfoCompat.Builder(context, shortcutId(card.id))
            .setShortLabel(label.take(12))
            .setLongLabel(label.take(25))
            .setIcon(icon(card))
            .setIntent(openIntent(context, card.id))
            .build()
    }

    /** 長按 App 圖示時顯示：常用優先、再依最近使用。 */
    fun updateDynamic(context: Context, cards: List<MemberCard>) {
        runCatching {
            val max = minOf(4, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context))
            val top = cards
                .sortedWith(compareByDescending<MemberCard> { it.favorite }.thenByDescending { it.lastUsed })
                .take(max)
            ShortcutManagerCompat.setDynamicShortcuts(context, top.map { build(context, it) })
        }
    }

    /** 卡片內容變更時同步更新已釘選的捷徑。 */
    fun update(context: Context, card: MemberCard) {
        runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(build(context, card))) }
    }

    fun remove(context: Context, cardId: String) {
        runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId(cardId)))
            ShortcutManagerCompat.disableShortcuts(context, listOf(shortcutId(cardId)), "這張會員卡已刪除")
        }
    }

    /** 回傳 false 代表桌面不支援釘選捷徑。 */
    fun requestPin(context: Context, card: MemberCard): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return ShortcutManagerCompat.requestPinShortcut(context, build(context, card), null)
    }
}
