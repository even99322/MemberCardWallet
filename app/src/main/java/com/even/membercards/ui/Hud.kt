package com.even.membercards.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.Barcodes
import com.even.membercards.data.MemberCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** 全畫面背景：深色底 + 極淡格線 + 頂部青色光暈。 */
fun Modifier.hudBackground(): Modifier = this
    .background(Hud.Bg)
    .drawBehind {
        val step = 28.dp.toPx()
        val line = Hud.Line.copy(alpha = 0.35f)
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        drawRect(
            Brush.radialGradient(
                colors = listOf(Hud.Cyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.85f, 0f),
                radius = size.width * 0.9f,
            )
        )
    }

/** 四角括號框（HUD 瞄準框）。 */
fun Modifier.cornerBrackets(
    color: Color,
    length: Dp = 10.dp,
    stroke: Dp = 1.5.dp,
    inset: Dp = 0.dp,
): Modifier = drawWithContent {
    drawContent()
    val l = length.toPx()
    val s = stroke.toPx()
    val i = inset.toPx() + s / 2
    val w = size.width
    val h = size.height
    fun seg(a: Offset, b: Offset) = drawLine(color, a, b, strokeWidth = s, cap = StrokeCap.Square)
    seg(Offset(i, i), Offset(i + l, i)); seg(Offset(i, i), Offset(i, i + l))
    seg(Offset(w - i, i), Offset(w - i - l, i)); seg(Offset(w - i, i), Offset(w - i, i + l))
    seg(Offset(i, h - i), Offset(i + l, h - i)); seg(Offset(i, h - i), Offset(i, h - i - l))
    seg(Offset(w - i, h - i), Offset(w - i - l, h - i)); seg(Offset(w - i, h - i), Offset(w - i, h - i - l))
}

/** 區塊標題：「01 // 條碼來源 ───」 */
@Composable
fun HudSection(index: String, title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(index, style = MonoLabel, color = Hud.Cyan)
        Text(" // ", style = MonoLabel, color = Hud.TextFaint)
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Hud.Text)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Hud.LineBright, Color.Transparent)))
        )
    }
}

/** 小型標籤（等寬字、細框）。 */
@Composable
fun HudTag(text: String, color: Color = Hud.Cyan, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MonoLabel.copy(fontSize = 9.sp, letterSpacing = 1.sp),
        color = color,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 資訊列：左邊等寬小標、右邊數值。 */
@Composable
fun HudInfoRow(label: String, value: String, valueColor: Color = Hud.Text) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MonoLabel, color = Hud.TextDim, modifier = Modifier.width(96.dp))
        Text(value, color = valueColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

/** 卡片圖示：有綁定 App 用 App 圖示，否則用店名首字。 */
@Composable
fun CardGlyph(card: MemberCard, size: Dp = 32.dp) {
    val accent = accentOf(card.color)
    val icon = rememberAppIcon(card.appPackage)
    val shape = RoundedCornerShape(size * 0.22f)
    if (icon != null) {
        Image(
            icon,
            null,
            Modifier
                .size(size)
                .clip(shape)
                .background(Color.White, shape)
                .padding(size * 0.06f),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .background(accent.copy(alpha = 0.14f), shape)
                .border(1.dp, accent.copy(alpha = 0.7f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                card.name.trim().take(1).ifBlank { "#" },
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.46f).sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
fun hudFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Hud.Cyan,
    unfocusedBorderColor = Hud.Line,
    focusedContainerColor = Hud.Surface,
    unfocusedContainerColor = Hud.Surface,
    focusedLabelColor = Hud.Cyan,
    unfocusedLabelColor = Hud.TextDim,
    cursorColor = Hud.Cyan,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun hudTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Hud.Bg,
    titleContentColor = Hud.Text,
    navigationIconContentColor = Hud.Text,
    actionIconContentColor = Hud.TextDim,
)

@Composable
fun hudChipColors(accent: Color = Hud.Cyan) = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = Hud.TextDim,
    selectedContainerColor = accent.copy(alpha = 0.14f),
    selectedLabelColor = accent,
    selectedLeadingIconColor = accent,
)

@Composable
fun hudChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = Hud.Line,
    selectedBorderColor = Hud.Cyan.copy(alpha = 0.7f),
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp,
)

/** HUD 風格的外框按鈕。 */
@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Cyan,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.6f) else Hud.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = accent.copy(alpha = 0.06f),
            contentColor = accent,
            disabledContentColor = Hud.TextFaint,
        ),
    ) {
        if (content != null) content() else Text(text, fontWeight = FontWeight.Medium)
    }
}

// ---------- 卡片相關的文字工具 ----------

fun shortFormat(key: String): String = when (key) {
    "CODE_128" -> "C128"
    "CODE_39" -> "C39"
    "CODE_93" -> "C93"
    "EAN_13" -> "EAN13"
    "EAN_8" -> "EAN8"
    "UPC_A" -> "UPC-A"
    "UPC_E" -> "UPC-E"
    "QR_CODE" -> "QR"
    "PDF_417" -> "PDF417"
    "DATA_MATRIX" -> "DMTX"
    "AZTEC" -> "AZTEC"
    "ITF" -> "ITF"
    "CODABAR" -> "CDBR"
    Barcodes.NONE -> "TEXT"
    else -> key
}

fun maskNumber(n: String): String = if (n.length <= 6) n else "···· " + n.takeLast(4)

fun expiryDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

/** 距離到期日的天數；沒有設定回傳 null。 */
fun daysLeft(card: MemberCard): Long? =
    if (card.expiry <= 0L) null else ChronoUnit.DAYS.between(LocalDate.now(), expiryDate(card.expiry))

/** 到期狀態標籤（30 天內提醒），不需要提醒時回傳 null。 */
fun expiryBadge(card: MemberCard): Pair<String, Color>? {
    val d = daysLeft(card) ?: return null
    return when {
        d < 0 -> "已過期" to Hud.Danger
        d <= 30 -> "剩 $d 天" to Hud.Warn
        else -> null
    }
}

fun timeAgo(ms: Long): String {
    if (ms <= 0) return "尚未使用"
    val diff = System.currentTimeMillis() - ms
    val min = diff / 60_000
    return when {
        min < 1 -> "剛剛"
        min < 60 -> "$min 分鐘前"
        min < 60 * 24 -> "${min / 60} 小時前"
        min < 60 * 24 * 30 -> "${min / 60 / 24} 天前"
        else -> expiryDate(ms).toString()
    }
}

@Composable
fun HudDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hud.Line)
    )
}

@Composable
fun HudPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Hud.Surface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .border(1.dp, Hud.Line, RoundedCornerShape(10.dp))
            .cornerBrackets(Hud.CyanDim, length = 8.dp, stroke = 1.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Top,
    ) { content() }
}
