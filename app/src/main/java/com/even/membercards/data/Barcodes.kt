package com.even.membercards.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlin.math.max

object Barcodes {
    data class Fmt(val key: String, val label: String, val is2D: Boolean)

    const val NONE = "NONE"

    val formats = listOf(
        Fmt("CODE_128", "Code 128（最常見）", false),
        Fmt("CODE_39", "Code 39（手機條碼載具）", false),
        Fmt("EAN_13", "EAN-13", false),
        Fmt("EAN_8", "EAN-8", false),
        Fmt("UPC_A", "UPC-A", false),
        Fmt("UPC_E", "UPC-E", false),
        Fmt("ITF", "ITF", false),
        Fmt("CODABAR", "Codabar", false),
        Fmt("CODE_93", "Code 93", false),
        Fmt("QR_CODE", "QR Code", true),
        Fmt("PDF_417", "PDF417", true),
        Fmt("DATA_MATRIX", "Data Matrix", true),
        Fmt("AZTEC", "Aztec", true),
        Fmt(NONE, "只顯示號碼（無條碼）", false),
    )

    fun find(key: String): Fmt = formats.firstOrNull { it.key == key } ?: formats[0]

    /** 把掃描到的格式名稱對應成可產生的格式。 */
    fun fromScanned(name: String?): String =
        formats.firstOrNull { it.key == name && it.key != NONE }?.key ?: "CODE_128"

    /** 產生條碼圖；格式為 NONE 時回傳 null，內容不合法時丟例外。 */
    fun encode(content: String, key: String, width: Int): Bitmap? {
        if (key == NONE || content.isBlank()) return null
        val fmt = find(key)
        val format = BarcodeFormat.valueOf(fmt.key)
        val height = when {
            fmt.key == "PDF_417" -> width / 3
            fmt.is2D -> width
            else -> width * 2 / 5
        }
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = if (fmt.is2D) 1 else 8
        if (fmt.key == "QR_CODE") hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        val m = MultiFormatWriter().encode(content, format, width, height, hints)
        val w = m.width
        val h = m.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val off = y * w
            for (x in 0 until w) pixels[off + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    /** 回傳錯誤訊息；null 代表可產生。 */
    fun validate(content: String, key: String): String? {
        if (key == NONE) return null
        return try {
            encode(content, key, 400)
            null
        } catch (e: Exception) {
            "此號碼不符合 ${find(key).label} 格式"
        }
    }

    /** 從圖片（例如店家 App 的截圖）辨識條碼，回傳 (內容, 格式)。 */
    fun decodeFromUri(context: Context, uri: Uri): Pair<String, String>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        return decode(bmp)
    }

    fun decode(bitmap: Bitmap): Pair<String, String>? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to formats.filter { it.key != NONE }.map { BarcodeFormat.valueOf(it.key) },
        )
        val reader = MultiFormatReader()
        val candidates = listOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(GlobalHistogramBinarizer(source)),
            BinaryBitmap(HybridBinarizer(source.invert())),
        )
        for (bb in candidates) {
            try {
                val r = reader.decode(bb, hints)
                return r.text to r.barcodeFormat.name
            } catch (ignored: Exception) {
                // 換下一種二值化方式
            } finally {
                reader.reset()
            }
        }
        return null
    }
}
