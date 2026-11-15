package com.even.membercards.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.EntryType
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.formatNumber
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

private val dayColors = listOf(
    Color(0xFFFFC53D), Color(0xFF38E8FF), Color(0xFFFF7EB6), Color(0xFF00B894), Color(0xFF8A9BB3),
)

private data class Slice(val label: String, val value: Double, val color: Color)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerChartsTab(ledger: LedgerRepository, month: YearMonth, onMonth: (YearMonth) -> Unit) {
    var type by rememberSaveable { mutableStateOf(EntryType.EXPENSE) }
    val zone = ZoneId.systemDefault()
    val entries = ledger.monthEntries(month).filter { it.type == type }
    val total = entries.sumOf { it.amount }

    val byCat = entries.groupBy { it.category }
        .map { (k, v) -> k to v.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val catSlices = topSlices(byCat.map { (k, v) -> Slice(k, v, Color(ledger.category(k, type).color)) })

    val byDay = entries.groupBy { it.localDate(zone) }
        .map { (d, v) -> d to v.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val daySlices = topSlices(byDay.mapIndexed { i, (d, v) ->
        Slice("${d.monthValue}月${d.dayOfMonth}日", v, dayColors[i.coerceAtMost(dayColors.lastIndex)])
    })
    val daily = (1..month.lengthOfMonth()).map { day ->
        val d = month.atDay(day)
        entries.filter { it.localDate(zone) == d }.sumOf { it.amount }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 支出／收入
        item(key = "type") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Hud.LineBright, RoundedCornerShape(8.dp)),
            ) {
                listOf(EntryType.EXPENSE to "支出", EntryType.INCOME to "收入").forEach { (k, label) ->
                    val sel = type == k
                    Text(
                        label,
                        color = if (sel) Hud.Bg else Hud.TextDim,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (sel) Hud.Cyan else Color.Transparent)
                            .clickable { type = k }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }

        // 月份
        item(key = "months") {
            val now = YearMonth.now()
            val months = (11L downTo 0L).map { now.minusMonths(it) }
            val state = rememberLazyListState(initialFirstVisibleItemIndex = months.size - 1)
            LaunchedEffect(Unit) { state.scrollToItem(months.size - 1) }
            LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(months) { m ->
                    val sel = m == month
                    val label = when (m) {
                        now -> "本月"
                        now.minusMonths(1) -> "上月"
                        else -> "${m.year}年${m.monthValue}月"
                    }
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onMonth(m) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(label, color = if (sel) Hud.Text else Hud.TextDim, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(28.dp)
                                .height(2.dp)
                                .background(if (sel) Hud.Cyan else Color.Transparent)
                        )
                    }
                }
            }
        }

        // 三頁圖表
        item(key = "pager") {
            val pager = rememberPagerState(pageCount = { 3 })
            HudPanel {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                    when (page) {
                        0 -> DonutWithLegend(catSlices, total)
                        1 -> DonutWithLegend(daySlices, total)
                        else -> DailyLineChart(
                            daily,
                            total,
                            if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth(),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (pager.currentPage == i) Hud.Cyan else Hud.LineBright)
                        )
                    }
                }
            }
        }

        // 分析：近 6 個月、與上月比較、常用商家
        item(key = "trend") {
            val months6 = (5L downTo 0L).map { month.minusMonths(it) }
            val exp = months6.map { ledger.monthExpense(it) }
            val inc = months6.map { ledger.monthIncome(it) }
            HudPanel {
                Text("TREND // 近 6 個月", style = MonoLabel, color = Hud.TextDim, modifier = Modifier.padding(top = 6.dp))
                val prev = if (type == EntryType.INCOME) ledger.monthIncome(month.minusMonths(1)) else ledger.monthExpense(month.minusMonths(1))
                if (prev > 0) {
                    val diff = (total - prev) / prev * 100
                    val up = diff >= 0
                    Text(
                        "比上月${if (type == EntryType.INCOME) "收入" else "支出"}" +
                            (if (up) "多 " else "少 ") + String.format(Locale.US, "%.0f%%", kotlin.math.abs(diff)) +
                            "（${formatNumber(prev)} → ${formatNumber(total)}）",
                        color = when {
                            type == EntryType.EXPENSE && up -> Hud.Warn
                            type == EntryType.EXPENSE -> Hud.Ok
                            up -> Hud.Ok
                            else -> Hud.Warn
                        },
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                MonthBars(months6, exp, inc, month)
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Hud.Cyan))
                    Text(" 支出   ", fontSize = 11.sp, color = Hud.TextDim)
                    Box(Modifier.size(8.dp).background(Hud.Ok))
                    Text(" 收入", fontSize = 11.sp, color = Hud.TextDim)
                }
            }
        }

        val topTitles = entries.groupBy { it.title }
            .map { (k, v) -> Triple(k, v.sumOf { it.amount }, v.size) }
            .sortedByDescending { it.second }
            .take(5)
        if (topTitles.isNotEmpty()) {
            item(key = "top") {
                HudPanel {
                    Text(
                        if (type == EntryType.INCOME) "TOP // 主要收入來源" else "TOP // 花最多的項目",
                        style = MonoLabel,
                        color = Hud.TextDim,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    topTitles.forEachIndexed { i, (name, sum, count) ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MonoLabel, color = Hud.Cyan, modifier = Modifier.width(18.dp))
                            Text(name, color = Hud.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("$count 筆  ", style = MonoLabel, color = Hud.TextDim)
                            Text(formatNumber(sum), fontFamily = Mono, color = Hud.Text, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        // 分類明細
        if (byCat.isEmpty()) {
            item(key = "empty") {
                Text(
                    "NO DATA // 這個月沒有${if (type == EntryType.INCOME) "收入" else "支出"}紀錄",
                    style = MonoLabel,
                    color = Hud.TextFaint,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }
        items(byCat, key = { "c_" + it.first }) { (name, sum) ->
            val cat = ledger.category(name, type)
            val ratio = if (total > 0) (sum / total).toFloat() else 0f
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Hud.Surface.copy(alpha = 0.9f))
                    .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(cat, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Hud.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(formatNumber(sum), fontFamily = Mono, fontWeight = FontWeight.SemiBold, color = Hud.Text, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Hud.Surface3)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(ratio.coerceIn(0.01f, 1f))
                                    .fillMaxHeight()
                                    .background(Color(cat.color))
                            )
                        }
                        Text(
                            String.format(Locale.US, "%.2f%%", ratio * 100),
                            style = MonoLabel,
                            color = Hud.TextDim,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 近幾個月支出／收入長條圖 */
@Composable
private fun MonthBars(months: List<YearMonth>, exp: List<Double>, inc: List<Double>, selected: YearMonth) {
    val measurer = rememberTextMeasurer()
    val maxV = (exp + inc).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(vertical = 6.dp)
    ) {
        val bottom = size.height - 18.dp.toPx()
        val slot = size.width / months.size
        val barW = slot * 0.28f
        val label = TextStyle(color = Hud.TextFaint, fontSize = 9.sp)
        drawLine(Hud.Line, Offset(0f, bottom), Offset(size.width, bottom), 1f)
        months.forEachIndexed { i, m ->
            val cx = slot * i + slot / 2
            val he = ((exp[i] / maxV) * (bottom - 12.dp.toPx())).toFloat()
            val hi = ((inc[i] / maxV) * (bottom - 12.dp.toPx())).toFloat()
            val alpha = if (m == selected) 1f else 0.55f
            drawRect(Hud.Cyan.copy(alpha = alpha), Offset(cx - barW - 1.dp.toPx(), bottom - he), Size(barW, he))
            drawRect(Hud.Ok.copy(alpha = alpha), Offset(cx + 1.dp.toPx(), bottom - hi), Size(barW, hi))
            val t = "${m.monthValue}月"
            val lw = measurer.measure(t, label).size.width
            drawText(measurer, t, Offset(cx - lw / 2f, bottom + 3.dp.toPx()), if (m == selected) label.copy(color = Hud.Cyan) else label)
        }
    }
}

/** 前 4 名 + 其他 */
private fun topSlices(all: List<Slice>): List<Slice> {
    if (all.size <= 5) return all
    val top = all.take(4)
    return top + Slice("其他", all.drop(4).sumOf { it.value }, Color(0xFF8A9BB3))
}

@Composable
private fun DonutWithLegend(slices: List<Slice>, total: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                if (total <= 0 || slices.isEmpty()) {
                    drawArc(Hud.Surface3, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                } else {
                    var start = -90f
                    slices.forEach { s ->
                        val sweep = (s.value / total * 360.0).toFloat()
                        drawArc(s.color, start, (sweep - 1.2f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                        start += sweep
                    }
                }
            }
            Text(formatNumber(total), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Hud.Text)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (slices.isEmpty()) {
                Text("沒有資料", color = Hud.TextDim, fontSize = 13.sp)
            }
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(s.color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        s.label,
                        color = Hud.Text,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (total > 0) String.format(Locale.US, "%.1f%%", s.value / total * 100) else "-",
                        style = MonoLabel,
                        color = Hud.TextDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyLineChart(daily: List<Double>, total: Double, daysSoFar: Int) {
    val measurer = rememberTextMeasurer()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row {
            Text("總和 ", color = Hud.TextDim, fontSize = 13.sp)
            Text(formatNumber(total), fontFamily = Mono, color = Hud.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(16.dp))
            Text("日均 ", color = Hud.TextDim, fontSize = 13.sp)
            Text(
                formatNumber(Math.round(total / daysSoFar.coerceAtLeast(1) * 100) / 100.0),
                fontFamily = Mono,
                color = Hud.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        val maxV = (daily.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val left = 34.dp.toPx()
            val bottom = size.height - 16.dp.toPx()
            val top = 6.dp.toPx()
            val w = size.width - left - 6.dp.toPx()
            val labelStyle = TextStyle(color = Hud.TextFaint, fontSize = 9.sp)
            // 座標軸
            drawLine(Hud.Line, Offset(left, top), Offset(left, bottom), 1f)
            drawLine(Hud.Line, Offset(left, bottom), Offset(left + w, bottom), 1f)
            drawText(measurer, formatNumber(maxV), Offset(0f, top - 4.dp.toPx()), labelStyle)
            drawText(measurer, "0", Offset(left - 12.dp.toPx(), bottom - 10.dp.toPx()), labelStyle)
            if (daily.isEmpty()) return@Canvas
            val step = if (daily.size > 1) w / (daily.size - 1) else w
            val pts = daily.mapIndexed { i, v ->
                Offset(left + i * step, bottom - ((v / maxV) * (bottom - top)).toFloat())
            }
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, Hud.Cyan.copy(alpha = 0.8f), style = Stroke(2.dp.toPx()))
            pts.forEachIndexed { i, p ->
                drawCircle(if (daily[i] > 0) Hud.Cyan else Hud.LineBright, radius = 3.dp.toPx(), center = p)
            }
            listOf(0, 14, daily.size - 1).distinct().forEach { i ->
                if (i in daily.indices) {
                    drawText(measurer, "${i + 1}", Offset(pts[i].x - 4.dp.toPx(), bottom + 3.dp.toPx()), labelStyle)
                }
            }
        }
    }
}
