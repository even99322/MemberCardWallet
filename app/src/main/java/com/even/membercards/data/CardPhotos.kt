package com.even.membercards.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.math.max

/** 實體卡正反面照片，存在 App 私有空間（不含在 JSON 備份中）。 */
object CardPhotos {
    const val FRONT = "front"
    const val BACK = "back"
    private const val MAX_SIDE = 1600

    private fun dir(context: Context) = File(context.filesDir, "card_photos").apply { mkdirs() }

    fun file(context: Context, cardId: String, side: String) = File(dir(context), "${cardId}_$side.jpg")

    fun exists(context: Context, cardId: String, side: String) = file(context, cardId, side).exists()

    /** 從相簿或相機的 Uri 存一張（自動縮圖、依 EXIF 轉正）。 */
    fun save(context: Context, cardId: String, side: String, uri: Uri): Boolean = runCatching {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = MAX_SIDE.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("decode failed")
        }
        file(context, cardId, side).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        true
    }.getOrDefault(false)

    fun load(context: Context, cardId: String, side: String, maxSide: Int = 1200): Bitmap? {
        val f = file(context, cardId, side)
        if (!f.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    fun delete(context: Context, cardId: String, side: String) {
        file(context, cardId, side).delete()
    }

    fun deleteAll(context: Context, cardId: String) {
        delete(context, cardId, FRONT)
        delete(context, cardId, BACK)
    }

    /** 相機拍照的暫存檔 */
    fun cameraTemp(context: Context): File = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture.jpg")
}
