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
    /** 連結的店家 App 套件名稱（可空） */
    val appPackage: String = "",
    val appLabel: String = "",
    /** 自訂開啟連結（網址或 App 深層連結，可空） */
    val link: String = "",
    /** 所屬資料夾名稱，空字串 = 未分類 */
    val folder: String = "",
    /** 有效期限（UTC 午夜毫秒），0 = 未設定 */
    val expiry: Long = 0L,
    /** 點數／餘額（自由文字，例如「1,250 點」「$300」） */
    val points: String = "",
    val pointsUpdated: Long = 0L,
    /** 開啟店家 App 後自動點到條碼頁的步驟 */
    val autoSteps: List<AutoStep> = emptyList(),
    /** card = 會員卡；coupon = 票券（優惠券、兌換券） */
    val kind: String = KIND_CARD,
    /** 票券是否已使用 */
    val used: Boolean = false,
    val usedAt: Long = 0L,
) {
    val isCoupon: Boolean get() = kind == KIND_COUPON

    /** 已使用或已過期的票券 */
    val isInactive: Boolean
        get() = isCoupon && (used || (expiry > 0 && expiry + 86_400_000L < System.currentTimeMillis()))

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
        put("appPackage", appPackage)
        put("appLabel", appLabel)
        put("link", link)
        put("folder", folder)
        put("expiry", expiry)
        put("points", points)
        put("pointsUpdated", pointsUpdated)
        put("autoSteps", AutoStep.listToJson(autoSteps))
        put("kind", kind)
        put("used", used)
        put("usedAt", usedAt)
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
            appPackage = o.optString("appPackage"),
            appLabel = o.optString("appLabel"),
            link = o.optString("link"),
            folder = o.optString("folder"),
            expiry = o.optLong("expiry", 0L),
            points = o.optString("points"),
            pointsUpdated = o.optLong("pointsUpdated", 0L),
            autoSteps = AutoStep.listFromJson(o.optJSONArray("autoSteps")),
            kind = o.optString("kind", KIND_CARD).ifBlank { KIND_CARD },
            used = o.optBoolean("used", false),
            usedAt = o.optLong("usedAt", 0L),
        )

        const val KIND_CARD = "card"
        const val KIND_COUPON = "coupon"
    }
}
