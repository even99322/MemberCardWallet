package com.even.membercards.data

import org.json.JSONObject
import java.util.UUID

data class MemberCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val number: String,
    val format: String = "CODE_128",
    val color: Long = 0xFF2B5BD7,
    val note: String = "",
    val favorite: Boolean = false,
    val lastUsed: Long = 0L,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("number", number)
        put("format", format)
        put("color", color)
        put("note", note)
        put("favorite", favorite)
        put("lastUsed", lastUsed)
        put("useCount", useCount)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): MemberCard = MemberCard(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            number = o.optString("number"),
            format = o.optString("format", "CODE_128"),
            color = o.optLong("color", 0xFF2B5BD7),
            note = o.optString("note"),
            favorite = o.optBoolean("favorite", false),
            lastUsed = o.optLong("lastUsed", 0L),
            useCount = o.optInt("useCount", 0),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}
