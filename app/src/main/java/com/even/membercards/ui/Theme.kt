package com.even.membercards.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFB3C5FF))
        else -> lightColorScheme(primary = Color(0xFF2B5BD7))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 依底色亮度決定文字用黑或白。 */
fun onColor(bg: Color): Color = if (bg.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

object Palette {
    val colors: List<Long> = listOf(
        0xFF2B5BD7, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFFDD835,
        0xFFFB8C00, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF6D4C41,
        0xFF546E7A, 0xFF212121,
    )
}

/** 常見店家快速帶入：名稱、顏色、條碼格式。 */
data class Preset(val name: String, val color: Long, val format: String)

val presets = listOf(
    Preset("手機條碼載具", 0xFF546E7A, "CODE_39"),
    Preset("7-ELEVEN", 0xFF008163, "CODE_128"),
    Preset("全家", 0xFF009DDC, "CODE_128"),
    Preset("全聯", 0xFF0055A5, "CODE_128"),
    Preset("家樂福", 0xFF1E5BC6, "CODE_128"),
    Preset("屈臣氏", 0xFF00A0A8, "CODE_128"),
    Preset("康是美", 0xFFE4007F, "CODE_128"),
    Preset("寶雅", 0xFFD81B60, "CODE_128"),
    Preset("星巴克", 0xFF00704A, "CODE_128"),
    Preset("Costco", 0xFFE31837, "CODE_128"),
    Preset("IKEA", 0xFF0058A3, "CODE_128"),
    Preset("誠品", 0xFF333333, "CODE_128"),
)
