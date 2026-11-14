package com.even.membercards.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 以 JSON 檔存在 App 私有空間；所有操作都在主執行緒呼叫。 */
class CardRepository private constructor(private val context: Context) {
    private val file = File(context.filesDir, "cards.json")

    companion object {
        @Volatile
        private var INSTANCE: CardRepository? = null

        fun instance(context: Context): CardRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CardRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    val cards = mutableStateListOf<MemberCard>()

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            val loaded = (0 until arr.length()).map { MemberCard.fromJson(arr.getJSONObject(it)) }
            cards.clear()
            cards.addAll(loaded)
        }
    }

    private fun save() {
        val arr = JSONArray()
        cards.forEach { arr.put(it.toJson()) }
        val tmp = File(file.parentFile, "cards.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
        Shortcuts.updateDynamic(context, cards.toList())
    }

    fun get(id: String): MemberCard? = cards.firstOrNull { it.id == id }

    fun upsert(card: MemberCard) {
        val i = cards.indexOfFirst { it.id == card.id }
        if (i >= 0) cards[i] = card else cards.add(card)
        save()
        Shortcuts.update(context, card)
    }

    fun delete(id: String) {
        cards.removeAll { it.id == id }
        save()
        Shortcuts.remove(context, id)
    }

    fun markUsed(id: String) {
        val c = get(id) ?: return
        upsert(c.copy(lastUsed = System.currentTimeMillis(), useCount = c.useCount + 1))
    }

    fun exportJson(): String {
        val arr = JSONArray()
        cards.forEach { arr.put(it.toJson()) }
        return JSONObject()
            .put("app", "MemberCardWallet")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("cards", arr)
            .toString(2)
    }

    /** 匯入備份（同 id 會覆蓋，其餘新增），回傳匯入張數。 */
    fun importJson(text: String): Int {
        val trimmed = text.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("cards")
        var n = 0
        for (i in 0 until arr.length()) {
            val c = MemberCard.fromJson(arr.getJSONObject(i))
            if (c.name.isBlank() && c.number.isBlank()) continue
            val idx = cards.indexOfFirst { it.id == c.id }
            if (idx >= 0) cards[idx] = c else cards.add(c)
            n++
        }
        save()
        return n
    }
}
