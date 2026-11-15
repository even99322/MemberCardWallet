package com.even.membercards.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 一套介面配色。 */
data class UiPalette(
    val key: String,
    val label: String,
    val isLight: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val lineBright: Color,
    val primary: Color,
    val primaryDim: Color,
    val secondary: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    /** 背景格線與光暈 */
    val grid: Boolean,
    /** 四角括號框 */
    val brackets: Boolean,
    /** 標籤用等寬字 */
    val mono: Boolean,
    /** 圓角大小 */
    val radius: Int = 10,
)

object Palettes {
    val Hud = UiPalette(
        "hud", "科幻 HUD", false,
        Color(0xFF05080F), Color(0xFF0A111C), Color(0xFF101A28), Color(0xFF152234),
        Color(0xFF1B2A3E), Color(0xFF2B4260), Color(0xFF38E8FF), Color(0xFF1A8FA3), Color(0xFF8B7CFF),
        Color(0xFFE4EDF8), Color(0xFF7D90AA), Color(0xFF4A5D78),
        Color(0xFF3CFFA0), Color(0xFFFFB547), Color(0xFFFF5470),
        grid = true, brackets = true, mono = true,
    )
    val Neon = UiPalette(
        "neon", "霓虹賽博", false,
        Color(0xFF0B0614), Color(0xFF140A22), Color(0xFF1C0F30), Color(0xFF26143F),
        Color(0xFF2E1A4A), Color(0xFF4A2A73), Color(0xFFFF3FD8), Color(0xFF9C2A86), Color(0xFF38E8FF),
        Color(0xFFF3E8FF), Color(0xFFA58FC2), Color(0xFF6A5687),
        Color(0xFF3CFFA0), Color(0xFFFFC53D), Color(0xFFFF4D6D),
        grid = true, brackets = true, mono = true,
    )
    val Terminal = UiPalette(
        "terminal", "終端機綠", false,
        Color(0xFF030803), Color(0xFF071207), Color(0xFF0B1C0B), Color(0xFF102810),
        Color(0xFF133313), Color(0xFF1F5A1F), Color(0xFF39FF6E), Color(0xFF1E9A42), Color(0xFFB6FF3C),
        Color(0xFFCFFFD9), Color(0xFF6FBF83), Color(0xFF3E7A4E),
        Color(0xFF39FF6E), Color(0xFFFFD23C), Color(0xFFFF4D4D),
        grid = true, brackets = true, mono = true, radius = 4,
    )
    val Classic = UiPalette(
        "classic", "經典深色", false,
        Color(0xFF161616), Color(0xFF212121), Color(0xFF2A2A2A), Color(0xFF333333),
        Color(0xFF2E2E2E), Color(0xFF474747), Color(0xFFFFD54A), Color(0xFFB39535), Color(0xFF9FA8FF),
        Color(0xFFF2F2F2), Color(0xFFA0A0A0), Color(0xFF6E6E6E),
        Color(0xFF4CD48A), Color(0xFFFFB547), Color(0xFFFF6B6B),
        grid = false, brackets = false, mono = false, radius = 14,
    )
    val Light = UiPalette(
        "light", "簡約淺色", true,
        Color(0xFFF5F6F8), Color(0xFFFFFFFF), Color(0xFFF0F2F5), Color(0xFFE6E9EE),
        Color(0xFFE1E5EB), Color(0xFFC9D0DA), Color(0xFF2563EB), Color(0xFF93B4F5), Color(0xFF7C3AED),
        Color(0xFF1A1F2B), Color(0xFF5B6475), Color(0xFF98A1B2),
        Color(0xFF16A34A), Color(0xFFD97706), Color(0xFFDC2626),
        grid = false, brackets = false, mono = false, radius = 14,
    )
    val Morandi = UiPalette(
        "morandi", "莫蘭迪", true,
        Color(0xFFEDE8E2), Color(0xFFF8F5F1), Color(0xFFE6DFD7), Color(0xFFDCD3C9),
        Color(0xFFD6CDC2), Color(0xFFBDB1A3), Color(0xFF6F8F7E), Color(0xFFA9BDB2), Color(0xFF9A7E9E),
        Color(0xFF3B3632), Color(0xFF776E66), Color(0xFFA89F96),
        Color(0xFF5E8C61), Color(0xFFC08A3E), Color(0xFFB5615C),
        grid = false, brackets = false, mono = false, radius = 16,
    )
    val Sakura = UiPalette(
        "sakura", "櫻花粉", true,
        Color(0xFFFFF5F7), Color(0xFFFFFFFF), Color(0xFFFFEAF0), Color(0xFFFFDDE6),
        Color(0xFFF7D6DF), Color(0xFFEDB8C7), Color(0xFFE85D8A), Color(0xFFF4A9C0), Color(0xFF8E7CC3),
        Color(0xFF3A2A30), Color(0xFF8A6A75), Color(0xFFC0A3AD),
        Color(0xFF3FA772), Color(0xFFE09A3A), Color(0xFFD9425B),
        grid = false, brackets = false, mono = false, radius = 18,
    )

    val all = listOf(Hud, Neon, Terminal, Classic, Light, Morandi, Sakura)

    fun of(key: String): UiPalette = all.firstOrNull { it.key == key } ?: Hud
}

/**
 * 目前的介面配色（各畫面用 Hud.X 取色；切換主題時整個 App 會跟著重繪）。
 * 名稱沿用最早的 HUD 主題：Cyan = 主色、Violet = 次色。
 */
object Hud {
    var palette by mutableStateOf(Palettes.Hud)

    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val Surface2 get() = palette.surface2
    val Surface3 get() = palette.surface3
    val Line get() = palette.line
    val LineBright get() = palette.lineBright
    val Cyan get() = palette.primary
    val CyanDim get() = palette.primaryDim
    val Violet get() = palette.secondary
    val Text get() = palette.text
    val TextDim get() = palette.textDim
    val TextFaint get() = palette.textFaint
    val Ok get() = palette.ok
    val Warn get() = palette.warn
    val Danger get() = palette.danger
    /** 主色按鈕上的文字顏色 */
    val OnPrimary get() = if (palette.isLight) Color.White else palette.bg
}

private fun schemeOf(p: UiPalette) = if (p.isLight) {
    lightColorScheme(
        primary = p.primary,
        onPrimary = Color.White,
        primaryContainer = p.primaryDim.copy(alpha = 0.35f),
        onPrimaryContainer = p.text,
        secondary = p.secondary,
        onSecondary = Color.White,
        secondaryContainer = p.surface3,
        onSecondaryContainer = p.text,
        tertiary = p.ok,
        background = p.bg,
        onBackground = p.text,
        surface = p.bg,
        onSurface = p.text,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.textDim,
        surfaceTint = p.primary,
        surfaceContainerLowest = p.surface,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface,
        surfaceContainerHighest = p.surface2,
        outline = p.lineBright,
        outlineVariant = p.line,
        error = p.danger,
        onError = Color.White,
        inverseSurface = p.text,
        inverseOnSurface = p.bg,
        scrim = Color(0x99000000),
    )
} else {
    darkColorScheme(
        primary = p.primary,
        onPrimary = p.bg,
        primaryContainer = p.primaryDim.copy(alpha = 0.3f),
        onPrimaryContainer = p.primary,
        secondary = p.secondary,
        onSecondary = p.bg,
        secondaryContainer = p.surface3,
        onSecondaryContainer = p.text,
        tertiary = p.ok,
        background = p.bg,
        onBackground = p.text,
        surface = p.bg,
        onSurface = p.text,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.textDim,
        surfaceTint = p.primary,
        surfaceContainerLowest = p.bg,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface2,
        surfaceContainerHighest = p.surface3,
        outline = p.lineBright,
        outlineVariant = p.line,
        error = p.danger,
        onError = p.bg,
        inverseSurface = p.text,
        inverseOnSurface = p.bg,
        scrim = Color(0xCC000000),
    )
}

private fun shapesOf(p: UiPalette) = Shapes(
    extraSmall = RoundedCornerShape((p.radius * 0.4f).dp),
    small = RoundedCornerShape((p.radius * 0.6f).dp),
    medium = RoundedCornerShape(p.radius.dp),
    large = RoundedCornerShape((p.radius * 1.2f).dp),
    extraLarge = RoundedCornerShape((p.radius * 1.6f).dp),
)

/** 等寬字：用在號碼、條碼數字這類「數據讀數」。 */
val Mono = FontFamily.Monospace

/** 小標籤：科技風主題用等寬字，其他主題用一般字型 */
val MonoLabel: TextStyle
    get() = TextStyle(
        fontFamily = if (Hud.palette.mono) Mono else FontFamily.Default,
        fontSize = if (Hud.palette.mono) 10.sp else 11.sp,
        letterSpacing = if (Hud.palette.mono) 1.6.sp else 0.6.sp,
        fontWeight = FontWeight.Medium,
    )

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = Hud.palette
    val scheme = remember(p) { schemeOf(p) }
    val shapes = remember(p) { shapesOf(p) }
    val typography = remember(p) {
        Typography().let { t ->
            t.copy(
                titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                labelSmall = t.labelSmall.copy(fontFamily = if (p.mono) Mono else FontFamily.Default, letterSpacing = 1.2.sp),
            )
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
}

/** 依底色亮度決定文字用黑或白。 */
fun onColor(bg: Color): Color = if (bg.luminance() > 0.55f) Color(0xFF0A0F18) else Color.White

/** 卡片顏色在深色背景上的「強調色」：太暗的顏色會提亮，確保看得見。 */
fun accentOf(cardColor: Long): Color {
    val c = Color(cardColor)
    val l = c.luminance()
    return if (Hud.palette.isLight) {
        // 淺色主題：太淡的顏色加深
        when {
            l > 0.75f -> lerp(c, Color.Black, 0.45f)
            l > 0.55f -> lerp(c, Color.Black, 0.28f)
            else -> c
        }
    } else {
        when {
            l < 0.06f -> lerp(c, Color.White, 0.55f)
            l < 0.14f -> lerp(c, Color.White, 0.35f)
            else -> c
        }
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
