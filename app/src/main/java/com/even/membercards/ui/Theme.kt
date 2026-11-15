package com.even.membercards.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** HUD 科幻風配色：深藍黑底、青色主色、細線框。 */
object Hud {
    val Bg = Color(0xFF05080F)
    val Surface = Color(0xFF0A111C)
    val Surface2 = Color(0xFF101A28)
    val Surface3 = Color(0xFF152234)
    val Line = Color(0xFF1B2A3E)
    val LineBright = Color(0xFF2B4260)
    val Cyan = Color(0xFF38E8FF)
    val CyanDim = Color(0xFF1A8FA3)
    val Violet = Color(0xFF8B7CFF)
    val Text = Color(0xFFE4EDF8)
    val TextDim = Color(0xFF7D90AA)
    val TextFaint = Color(0xFF4A5D78)
    val Ok = Color(0xFF3CFFA0)
    val Warn = Color(0xFFFFB547)
    val Danger = Color(0xFFFF5470)
}

private val HudColors = darkColorScheme(
    primary = Hud.Cyan,
    onPrimary = Color(0xFF00222A),
    primaryContainer = Color(0xFF0C2C36),
    onPrimaryContainer = Hud.Cyan,
    secondary = Hud.Violet,
    onSecondary = Color(0xFF140F33),
    secondaryContainer = Color(0xFF17183A),
    onSecondaryContainer = Color(0xFFC9C2FF),
    tertiary = Hud.Ok,
    background = Hud.Bg,
    onBackground = Hud.Text,
    surface = Hud.Bg,
    onSurface = Hud.Text,
    surfaceVariant = Hud.Surface2,
    onSurfaceVariant = Hud.TextDim,
    surfaceTint = Hud.Cyan,
    surfaceContainerLowest = Hud.Bg,
    surfaceContainerLow = Hud.Surface,
    surfaceContainer = Hud.Surface,
    surfaceContainerHigh = Hud.Surface2,
    surfaceContainerHighest = Hud.Surface3,
    outline = Hud.LineBright,
    outlineVariant = Hud.Line,
    error = Hud.Danger,
    onError = Color(0xFF2A0009),
    inverseSurface = Hud.Text,
    inverseOnSurface = Hud.Bg,
    scrim = Color(0xCC000000),
)

private val HudShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** 等寬字：用在號碼、標籤、狀態列這類「數據讀數」。 */
val Mono = FontFamily.Monospace

val MonoLabel = TextStyle(
    fontFamily = Mono,
    fontSize = 10.sp,
    letterSpacing = 1.6.sp,
    fontWeight = FontWeight.Medium,
)

private val HudTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontFamily = Mono, letterSpacing = 1.2.sp),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HudColors,
        shapes = HudShapes,
        typography = HudTypography,
        content = content,
    )
}

/** 依底色亮度決定文字用黑或白。 */
fun onColor(bg: Color): Color = if (bg.luminance() > 0.55f) Color(0xFF0A0F18) else Color.White

/** 卡片顏色在深色背景上的「強調色」：太暗的顏色會提亮，確保看得見。 */
fun accentOf(cardColor: Long): Color {
    val c = Color(cardColor)
    val l = c.luminance()
    return when {
        l < 0.06f -> lerp(c, Color.White, 0.55f)
        l < 0.14f -> lerp(c, Color.White, 0.35f)
        else -> c
    }
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

object Palette {
    val colors: List<Long> = listOf(
        0xFF38E8FF, 0xFF2B7BFF, 0xFF8B7CFF, 0xFFE45CFF, 0xFFFF5470, 0xFFFF8A3D,
        0xFFFFC53D, 0xFF3CFFA0, 0xFF00B894, 0xFF00897B, 0xFF6D4C41, 0xFF8A9BB3,
        0xFF546E7A, 0xFF2A3A50,
    )
}

/** 常見店家快速帶入：名稱、顏色、條碼格式。 */
data class Preset(val name: String, val color: Long, val format: String)

val presets = listOf(
    Preset("手機條碼載具", 0xFF8A9BB3, "CODE_39"),
    Preset("7-ELEVEN", 0xFF00A06B, "CODE_128"),
    Preset("全家", 0xFF00A0E9, "CODE_128"),
    Preset("全聯", 0xFF2B6FD6, "CODE_128"),
    Preset("家樂福", 0xFF1E5BC6, "CODE_128"),
    Preset("屈臣氏", 0xFF00A0A8, "CODE_128"),
    Preset("康是美", 0xFFE4007F, "CODE_128"),
    Preset("寶雅", 0xFFD81B60, "CODE_128"),
    Preset("星巴克", 0xFF00704A, "CODE_128"),
    Preset("Costco", 0xFFE31837, "CODE_128"),
    Preset("IKEA", 0xFF0058A3, "CODE_128"),
    Preset("誠品", 0xFF8A9BB3, "CODE_128"),
)
