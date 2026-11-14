package com.even.membercards.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/** 已安裝 App 圖示的快取。 */
object AppIcons {
    private val cache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(HashMap())
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun peek(pkg: String): ImageBitmap? = cache[pkg]

    fun load(context: Context, pkg: String): ImageBitmap? {
        if (pkg.isBlank() || pkg in missing) return null
        cache[pkg]?.let { return it }
        return try {
            val bmp = context.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
            cache[pkg] = bmp
            bmp
        } catch (e: Exception) {
            missing.add(pkg)
            null
        }
    }
}

@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState(initialValue = AppIcons.peek(pkg), pkg) {
        if (pkg.isNotBlank() && value == null) {
            value = withContext(Dispatchers.IO) { AppIcons.load(context, pkg) }
        }
    }
    return icon
}

/** 從 App 圖示取出最主要的顏色，當作卡片底色。 */
fun dominantColor(icon: Bitmap, fallback: Long = 0xFF546E7A): Long {
    val small = Bitmap.createScaledBitmap(icon, 32, 32, true)
    val buckets = 12
    val count = IntArray(buckets)
    val sumR = LongArray(buckets)
    val sumG = LongArray(buckets)
    val sumB = LongArray(buckets)
    val hsv = FloatArray(3)
    for (y in 0 until small.height) {
        for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 200) continue
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
            val b = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            count[b]++
            sumR[b] += android.graphics.Color.red(c).toLong()
            sumG[b] += android.graphics.Color.green(c).toLong()
            sumB[b] += android.graphics.Color.blue(c).toLong()
        }
    }
    val best = count.indices.maxByOrNull { count[it] } ?: return fallback
    val n = count[best]
    if (n < 25) return fallback
    val rgb = android.graphics.Color.rgb((sumR[best] / n).toInt(), (sumG[best] / n).toInt(), (sumB[best] / n).toInt())
    return (rgb.toLong() and 0xFFFFFFFFL) or 0xFF000000L
}
