package com.even.membercards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.Categories
import com.even.membercards.data.LedgerEntry
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.formatMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    ledger: LedgerRepository,
    settings: AppSettings,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onAutoSetup: () -> Unit,
) {
    val context = LocalContext.current
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var menu by remember { mutableStateOf(false) }
    var budgetDialog by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    val list = ledger.monthEntries(month).sortedByDescending { it.time }
    val total = list.sumOf { it.amount }
    val byCategory = list.groupBy { it.category }
        .mapValues { (_, v) -> v.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }
    val byDay = list.groupBy { it.localDate(zone) }.entries.sortedByDescending { it.key }
    val budget = settings.monthlyBudget
    val autoOn = settings.autoRecord && AutoRecord.isPermissionGranted(context)

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(ledger.toCsv().toByteArray()) }
            }.onSuccess { context.toast("已匯出 ${ledger.entries.size} 筆記帳") }
                .onFailure { context.toast("匯出失敗：${it.message}") }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .hudBackground()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("記帳", fontWeight = FontWeight.SemiBold)
                            Text("LEDGER // ${ledger.entries.size} RECORDS", style = MonoLabel, color = Hud.Cyan)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "選單") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("每月預算") },
                                    onClick = { menu = false; budgetDialog = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("自動記帳設定／通知紀錄") },
                                    onClick = { menu = false; onAutoSetup() },
                                )
                                DropdownMenuItem(
                                    text = { Text("匯出 CSV（Excel）") },
                                    onClick = { menu = false; csvLauncher.launch("記帳-${LocalDate.now()}.csv") },
                                )
                            }
                        }
                    },
                    colors = hudTopBarColors(),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("記一筆", fontWeight = FontWeight.SemiBold) },
                    containerColor = Hud.Cyan,
                    contentColor = Hud.Bg,
                    shape = RoundedCornerShape(10.dp),
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 月份切換
                item(key = "month") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上個月", tint = Hud.Cyan)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${month.year} 年 ${month.monthValue} 月", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Hud.Text)
                            if (month == YearMonth.now()) Text("THIS MONTH", style = MonoLabel, color = Hud.TextFaint)
                        }
                        IconButton(
                            onClick = { monthText = month.plusMonths(1).toString() },
                            enabled = month < YearMonth.now(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                "下個月",
                                tint = if (month < YearMonth.now()) Hud.Cyan else Hud.TextFaint,
                            )
                        }
                    }
                }

                // 總覽
                item(key = "summary") {
                    HudPanel {
                        Text("TOTAL SPENT // 本月支出", style = MonoLabel, color = Hud.TextDim, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            formatMoney(total),
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            color = if (budget > 0 && total > budget) Hud.Danger else Hud.Text,
                        )
                        val days = if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()
                        Text(
                            "${list.size} 筆  ·  日均 ${formatMoney(if (days > 0) Math.round(total / days).toDouble() else 0.0)}",
                            color = Hud.TextDim,
                            fontSize = 12.sp,
                        )
                        if (budget > 0) {
                            Spacer(Modifier.height(10.dp))
                            val ratio = (total / budget).toFloat().coerceIn(0f, 1f)
                            val barColor = when {
                                total > budget -> Hud.Danger
                                ratio > 0.8f -> Hud.Warn
                                else -> Hud.Cyan
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Hud.Surface3)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(ratio)
                                        .fillMaxHeight()
                                        .background(barColor)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (total > budget) "預算 ${formatMoney(budget.toDouble())} · 超支 ${formatMoney(total - budget)}"
                                else "預算 ${formatMoney(budget.toDouble())} · 剩 ${formatMoney(budget - total)}",
                                color = barColor,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        } else {
                            Text(
                                "＋ 設定每月預算",
                                color = Hud.Cyan,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .clickable { budgetDialog = true },
                            )
                        }
                    }
                }

                // 自動記帳狀態
                item(key = "auto") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background((if (autoOn) Hud.Ok else Hud.Warn).copy(alpha = 0.08f))
                            .border(1.dp, (if (autoOn) Hud.Ok else Hud.Warn).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onAutoSetup)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(if (autoOn) Hud.Ok else Hud.Warn, RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (autoOn) "LINE Pay 自動記帳運作中" else "開啟 LINE Pay 自動記帳",
                            color = if (autoOn) Hud.Ok else Hud.Warn,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hud.TextDim)
                    }
                }

                // 分類統計
                if (byCategory.isNotEmpty()) {
                    item(key = "cats") {
                        HudPanel {
                            Text("BY CATEGORY // 分類", style = MonoLabel, color = Hud.TextDim, modifier = Modifier.padding(vertical = 4.dp))
                            byCategory.forEach { (cat, sum) ->
                                val c = Color(Categories.color(cat))
                                val ratio = if (total > 0) (sum / total).toFloat() else 0f
                                Row(
                                    Modifier.padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(cat, color = Hud.Text, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Hud.Surface3)
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
                                                .fillMaxHeight()
                                                .background(c)
                                        )
                                    }
                                    Text(
                                        formatMoney(sum),
                                        fontFamily = Mono,
                                        fontSize = 12.sp,
                                        color = Hud.Text,
                                        modifier = Modifier.width(78.dp).padding(start = 8.dp),
                                        maxLines = 1,
                                    )
                                    Text(
                                        "${Math.round(ratio * 100)}%",
                                        style = MonoLabel,
                                        color = Hud.TextDim,
                                        modifier = Modifier.width(34.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                if (list.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "NO RECORDS // 這個月還沒有記帳\n按右下角「記一筆」，或開啟 LINE Pay 自動記帳",
                            color = Hud.TextDim,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                // 依日期列出
                byDay.forEach { (date, entries) ->
                    item(key = "d_$date") {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("//", style = MonoLabel, color = Hud.Cyan)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${date.monthValue}/${date.dayOfMonth}（${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TAIWAN)}）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Hud.Text,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(formatMoney(entries.sumOf { it.amount }), style = MonoLabel, color = Hud.TextDim)
                        }
                    }
                    items(entries, key = { it.id }) { e -> EntryRow(e, zone) { onEdit(e.id) } }
                }
            }
        }
    }

    if (budgetDialog) {
        var text by remember { mutableStateOf(if (budget > 0) budget.toString() else "") }
        AlertDialog(
            onDismissRequest = { budgetDialog = false },
            title = { Text("每月預算") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() } },
                    label = { Text("金額（留空 = 不設定）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = hudFieldColors(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.monthlyBudget = text.toLongOrNull() ?: 0L
                    budgetDialog = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { budgetDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun EntryRow(e: LedgerEntry, zone: ZoneId, onClick: () -> Unit) {
    val c = Color(Categories.color(e.category))
    val t = Instant.ofEpochMilli(e.time).atZone(zone)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Hud.Surface.copy(alpha = 0.9f))
            .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(c.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(e.category.take(1), color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.merchant.ifBlank { e.category },
                color = Hud.Text,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    String.format(Locale.US, "%02d:%02d", t.hour, t.minute),
                    e.method,
                    e.note,
                ).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MonoLabel.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                color = Hud.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (e.isAuto) {
            HudTag("AUTO", Hud.Ok)
            Spacer(Modifier.width(8.dp))
        }
        Text(formatMoney(e.amount), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Hud.Text)
    }
}
