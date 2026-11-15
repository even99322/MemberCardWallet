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

    /** 資料夾（依使用者排列的順序） */
    val folders = mutableStateListOf<String>()

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val text = file.readText().trim()
            val arr: JSONArray
            val folderArr: JSONArray?
            if (text.startsWith("[")) {
                arr = JSONArray(text) // 舊版格式：只有卡片陣列
                folderArr = null
            } else {
                val o = JSONObject(text)
                arr = o.optJSONArray("cards") ?: JSONArray()
                folderArr = o.optJSONArray("folders")
            }
            val loaded = (0 until arr.length()).map { MemberCard.fromJson(arr.getJSONObject(it)) }
            cards.clear()
            cards.addAll(loaded)
            folders.clear()
            if (folderArr != null) {
                for (i in 0 until folderArr.length()) {
                    val f = folderArr.optString(i).trim()
                    if (f.isNotEmpty() && f !in folders) folders.add(f)
                }
            }
            loaded.map { it.folder }.filter { it.isNotBlank() && it !in folders }.distinct().forEach { folders.add(it) }
        }
    }

    private fun save() {
        val arr = JSONArray()
        cards.forEach { arr.put(it.toJson()) }
        val content = JSONObject()
            .put("version", 2)
            .put("cards", arr)
            .put("folders", JSONArray(folders.toList()))
            .toString()
        val tmp = File(file.parentFile, "cards.json.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(content)
            tmp.delete()
        }
        Shortcuts.updateDynamic(context, cards.toList())
        com.even.membercards.CarrierWidget.updateAll(context)
    }

    fun get(id: String): MemberCard? = cards.firstOrNull { it.id == id }

    fun upsert(card: MemberCard) {
        if (card.folder.isNotBlank() && card.folder !in folders) folders.add(card.folder)
        val i = cards.indexOfFirst { it.id == card.id }
        if (i >= 0) cards[i] = card else cards.add(card)
        save()
        Shortcuts.update(context, card)
    }

    fun delete(id: String) {
        cards.removeAll { it.id == id }
        save()
        Shortcuts.remove(context, id)
        CardPhotos.deleteAll(context, id)
    }

    fun addAll(newCards: List<MemberCard>) {
        newCards.forEach { if (it.folder.isNotBlank() && it.folder !in folders) folders.add(it.folder) }
        cards.addAll(newCards)
        save()
    }

    // ---------- 資料夾 ----------

    /** 回傳 false 代表名稱空白或已存在。 */
    fun addFolder(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || n in folders) return false
        folders.add(n)
        save()
        return true
    }

    fun renameFolder(old: String, new: String): Boolean {
        val n = new.trim()
        val idx = folders.indexOf(old)
        if (idx < 0 || n.isEmpty() || (n != old && n in folders)) return false
        folders[idx] = n
        for (i in cards.indices) if (cards[i].folder == old) cards[i] = cards[i].copy(folder = n)
        save()
        return true
    }

    /** 刪除資料夾，裡面的卡片改為未分類（不會刪卡片）。 */
    fun deleteFolder(name: String) {
        folders.remove(name)
        for (i in cards.indices) if (cards[i].folder == name) cards[i] = cards[i].copy(folder = "")
        save()
    }

    /**
     * 依拖曳結果重排一組卡片：這組卡片原本佔的位置，依新順序重新填入
     * （例如只排某個資料夾，其他卡片的位置不變）。
     */
    fun reorderCards(newOrderIds: List<String>) {
        val ids = newOrderIds.toSet()
        val byId = cards.associateBy { it.id }
        val positions = cards.indices.filter { cards[it].id in ids }
        if (positions.size != newOrderIds.size) return
        positions.forEachIndexed { k, pos -> byId[newOrderIds[k]]?.let { cards[pos] = it } }
        save()
    }

    /** 依拖曳結果設定資料夾順序 */
    fun setFolderOrder(order: List<String>) {
        if (order.toSet() != folders.toSet()) return
        folders.clear()
        folders.addAll(order)
        save()
    }

    fun moveFolder(name: String, delta: Int) {
        val i = folders.indexOf(name)
        val j = i + delta
        if (i < 0 || j !in folders.indices) return
        folders.removeAt(i)
        folders.add(j, name)
        save()
    }

    fun moveCard(id: String, folder: String) {
        val c = get(id) ?: return
        upsert(c.copy(folder = folder))
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
            .put("folders", JSONArray(folders.toList()))
            .toString(2)
    }

    /** 匯入備份（同 id 會覆蓋，其餘新增），回傳匯入張數。 */
    fun importJson(text: String): Int {
        val trimmed = text.trim()
        val arr: JSONArray
        if (trimmed.startsWith("[")) {
            arr = JSONArray(trimmed)
        } else {
            val o = JSONObject(trimmed)
            arr = o.getJSONArray("cards")
            o.optJSONArray("folders")?.let { fa ->
                for (i in 0 until fa.length()) {
                    val f = fa.optString(i).trim()
                    if (f.isNotEmpty() && f !in folders) folders.add(f)
                }
            }
        }
        var n = 0
        for (i in 0 until arr.length()) {
            val c = MemberCard.fromJson(arr.getJSONObject(i))
            if (c.name.isBlank() && c.number.isBlank()) continue
            val idx = cards.indexOfFirst { it.id == c.id }
            if (idx >= 0) cards[idx] = c else cards.add(c)
            if (c.folder.isNotBlank() && c.folder !in folders) folders.add(c.folder)
            n++
        }
        save()
        return n
    }
}
