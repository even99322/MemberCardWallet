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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.Category
import com.even.membercards.data.LedgerEntry
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.formatMoney
import com.even.membercards.data.formatNumber
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** 記帳主畫面：明細／圖表／帳戶／設定，中間是「記一筆」。 */
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    // 進記帳頁時補記到期的定期收支
    LaunchedEffect(Unit) {
        val n = ledger.runRecurring()
        if (n > 0) context.toast("已自動記入 $n 筆定期收支")
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
                            Text(
                                when (tab) {
                                    1 -> "圖表"
                                    2 -> "帳戶"
                                    3 -> "記帳設定"
                                    else -> "記帳"
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when (tab) {
                                    1 -> "LEDGER // CHARTS"
                                    2 -> "LEDGER // ACCOUNTS"
                                    3 -> "LEDGER // CONFIG"
                                    else -> "LEDGER // ${ledger.entries.size} RECORDS"
                                },
                                style = MonoLabel,
                                color = Hud.Cyan,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        if (tab == 0) {
                            IconButton(onClick = {
                                searchOpen = !searchOpen
                                if (!searchOpen) query = ""
                            }) {
                                Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, "搜尋")
                            }
                        }
                    },
                    colors = hudTopBarColors(),
                )
            },
            bottomBar = { LedgerBottomBar(tab, onTab = { tab = it }, onAdd = onAdd) },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (tab) {
                    1 -> LedgerChartsTab(ledger, month, onMonth = { monthText = it.toString() })
                    2 -> LedgerAccountsTab(ledger)
                    3 -> LedgerSettingsTab(ledger, settings, onAutoSetup)
                    else -> LedgerDetailTab(
                        ledger = ledger,
                        settings = settings,
                        searchOpen = searchOpen,
                        query = query,
                        onQuery = { query = it },
                        month = month,
                        onMonth = { monthText = it.toString() },
                        onEdit = onEdit,
                        onAutoSetup = onAutoSetup,
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerBottomBar(tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Hud.Surface.copy(alpha = 0.97f))
            .navigationBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Hud.Line)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem("明細", Icons.AutoMirrored.Filled.List, tab == 0, Modifier.weight(1f)) { onTab(0) }
            NavItem("圖表", Icons.Filled.DateRange, tab == 1, Modifier.weight(1f)) { onTab(1) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .offset(y = (-10).dp)
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Hud.Cyan)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, "記一筆", tint = Hud.Bg, modifier = Modifier.size(30.dp)) }
            }
            NavItem("帳戶", Icons.Filled.Home, tab == 2, Modifier.weight(1f)) { onTab(2) }
            NavItem("設定", Icons.Filled.Settings, tab == 3, Modifier.weight(1f)) { onTab(3) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) Hud.Cyan else Hud.TextFaint, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.sp, color = if (selected) Hud.Cyan else Hud.TextDim)
    }
}

/** 分類圖示：彩色圓底 + emoji。 */
@Composable
fun CategoryIcon(cat: Category, size: Dp = 40.dp, selected: Boolean = false) {
    val c = Color(cat.color)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) c.copy(alpha = 0.85f) else c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = if (selected) 1f else 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(cat.emoji, fontSize = (size.value * 0.46f).sp)
    }
}

/** 月份選擇（年/月方塊，點開清單）。 */
@Composable
fun MonthBox(month: YearMonth, months: List<YearMonth>, onMonth: (YearMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Hud.Surface2)
            .border(1.dp, Hud.LineBright, RoundedCornerShape(8.dp))
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${month.year}年", style = MonoLabel, color = Hud.TextDim)
        Text("${month.monthValue}月 ▾", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Hud.Text)
    }
    if (open) {
        val list = (months + (0L..23L).map { YearMonth.now().minusMonths(it) }).distinct().sortedDescending()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("選擇月份") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list) { m ->
                        Text(
                            "${m.year} 年 ${m.monthValue} 月" + if (m == YearMonth.now()) "（本月）" else "",
                            color = if (m == month) Hud.Cyan else Hud.Text,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMonth(m)
                                    open = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("關閉") } },
        )
    }
}

// ================= 明細 =================

@Composable
private fun LedgerDetailTab(
    ledger: LedgerRepository,
    settings: AppSettings,
    searchOpen: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onEdit: (String) -> Unit,
    onAutoSetup: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val searching = searchOpen && query.isNotBlank()
    val list = if (searching) ledger.search(query) else ledger.monthEntries(month).sortedByDescending { it.time }
    val expense = list.filter { it.isExpense }.sumOf { it.amount }
    val income = list.filter { it.isIncome }.sumOf { it.amount }
    val byDay = list.groupBy { it.localDate(zone) }.entries.sortedByDescending { it.key }
    val budget = settings.monthlyBudget
    val autoOn = settings.autoRecord && AutoRecord.isPermissionGranted(context)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (searchOpen) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text("搜尋備註、商家、分類、帳戶或金額", color = Hud.TextFaint) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (searching) {
                item(key = "searchSummary") {
                    Text(
                        "找到 ${list.size} 筆（所有月份）  ·  支出 ${formatNumber(expense)}  ·  收入 ${formatNumber(income)}",
                        style = MonoLabel,
                        color = Hud.Cyan,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        if (!searchOpen) {
            item(key = "quick") { QuickEntryBar(ledger) }
        }

        if (!searching) item(key = "header") {
            HudPanel {
                Row(
                    Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthBox(month, ledger.activeMonths(), onMonth)
                    Spacer(Modifier.width(12.dp))
                    SummaryCell("支出", expense, Hud.Text, Modifier.weight(1f))
                    SummaryCell("收入", income, Hud.Ok, Modifier.weight(1f))
                    SummaryCell("結餘", income - expense, if (income - expense < 0) Hud.Danger else Hud.Cyan, Modifier.weight(1f))
                }
                if (budget > 0) {
                    val ratio = (expense / budget).toFloat().coerceIn(0f, 1f)
                    val barColor = when {
                        expense > budget -> Hud.Danger
                        ratio > 0.8f -> Hud.Warn
                        else -> Hud.Cyan
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
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
                    Text(
                        if (expense > budget) "預算 ${formatMoney(budget.toDouble())} · 超支 ${formatMoney(expense - budget)}"
                        else "預算 ${formatMoney(budget.toDouble())} · 剩 ${formatMoney(budget - expense)}",
                        color = barColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    )
                }
            }
        }

        if (!autoOn && !searching) {
            item(key = "auto") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Hud.Warn.copy(alpha = 0.08f))
                        .border(1.dp, Hud.Warn.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onAutoSetup)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("開啟 LINE Pay 自動記帳", color = Hud.Warn, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hud.TextDim)
                }
            }
        }

        if (list.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (searching) "NO MATCH // 找不到符合的紀錄" else "NO RECORDS // 這個月還沒有記帳\n按下方「＋」記一筆",
                    color = Hud.TextDim,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                )
            }
        }

        byDay.forEach { (date, entries) ->
            item(key = "d_$date") {
                val dayExp = entries.filter { it.isExpense }.sumOf { it.amount }
                val dayInc = entries.filter { it.isIncome }.sumOf { it.amount }
                Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${date.monthValue}月${date.dayOfMonth}日  ${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TAIWAN)}" +
                            if (date == LocalDate.now()) "  今天" else "",
                        fontSize = 13.sp,
                        color = Hud.TextDim,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        listOfNotNull(
                            if (dayInc > 0) "收入 ${formatNumber(dayInc)}" else null,
                            if (dayExp > 0) "支出 ${formatNumber(dayExp)}" else null,
                        ).joinToString("  "),
                        style = MonoLabel,
                        color = Hud.TextDim,
                    )
                }
            }
            items(entries, key = { it.id }) { e -> EntryRow(ledger, e, zone) { onEdit(e.id) } }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: Double, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Hud.TextDim)
        Text(
            formatNumber(value),
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun EntryRow(ledger: LedgerRepository, e: LedgerEntry, zone: ZoneId, onClick: () -> Unit) {
    val cat = ledger.category(e.category, e.type)
    val t = Instant.ofEpochMilli(e.time).atZone(zone)
    val accName = ledger.account(e.account)?.name.orEmpty()
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
        if (e.isTransfer) {
            CategoryIcon(Category("轉帳", "🔁", 0xFF38E8FF), 38.dp)
        } else {
            CategoryIcon(cat, 38.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, color = Hud.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (e.isTransfer) {
                "${accName.ifBlank { "?" }} → ${ledger.account(e.toAccount)?.name ?: "?"}"
            } else {
                listOf(
                    String.format(Locale.US, "%02d:%02d", t.hour, t.minute),
                    if (e.title != e.category) e.category else "",
                    accName,
                ).filter { it.isNotBlank() }.joinToString("  ·  ")
            }
            Text(
                sub,
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
        if (e.isRecurring) {
            HudTag("定期", Hud.Violet)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            when {
                e.isIncome -> "+" + formatNumber(e.amount)
                e.isTransfer -> formatNumber(e.amount)
                else -> "-" + formatNumber(e.amount)
            },
            fontFamily = Mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = when {
                e.isIncome -> Hud.Ok
                e.isTransfer -> Hud.TextDim
                else -> Hud.Text
            },
        )
    }
}

// ================= 設定 =================

@Composable
private fun LedgerSettingsTab(ledger: LedgerRepository, settings: AppSettings, onAutoSetup: () -> Unit) {
    val context = LocalContext.current
    var budgetDialog by remember { mutableStateOf(false) }
    var accountDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var recurringDialog by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(ledger.toCsv().toByteArray()) }
            }.onSuccess { context.toast("已匯出 ${ledger.entries.size} 筆記帳") }
                .onFailure { context.toast("匯出失敗：${it.message}") }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HudPanel {
            SettingRow("每月預算", if (settings.monthlyBudget > 0) formatMoney(settings.monthlyBudget.toDouble()) else "未設定") {
                budgetDialog = true
            }
            HudDivider()
            SettingRow("預設帳戶", ledger.account(ledger.defaultAccount)?.name ?: "未設定") { accountDialog = true }
            HudDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { ledger.autoPopupAccount = !ledger.autoPopupAccount }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("記帳時自動彈出帳戶選單", color = Hud.Text, fontSize = 15.sp)
                    Text("每次新增時先選帳戶", color = Hud.TextDim, fontSize = 12.sp)
                }
                Switch(
                    checked = ledger.autoPopupAccount,
                    onCheckedChange = { ledger.autoPopupAccount = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Hud.Bg,
                        checkedTrackColor = Hud.Cyan,
                        uncheckedThumbColor = Hud.TextDim,
                        uncheckedTrackColor = Hud.Surface2,
                        uncheckedBorderColor = Hud.LineBright,
                    ),
                )
            }
            HudDivider()
            SettingRow("分類管理", "新增自訂分類（${ledger.customCategories.size} 個）") { categoryDialog = true }
            HudDivider()
            SettingRow(
                "定期收支",
                if (ledger.recurring.isEmpty()) "訂閱、房租、薪水每月自動記一筆"
                else "${ledger.recurring.count { it.enabled }} 筆啟用中",
            ) { recurringDialog = true }
        }
        HudPanel {
            SettingRow(
                "自動記帳",
                if (settings.autoRecord && AutoRecord.isPermissionGranted(context)) "LINE Pay 付款通知：運作中" else "LINE Pay 付款通知：未開啟",
                onAutoSetup,
            )
            HudDivider()
            SettingRow("匯出 CSV", "Excel 可直接開啟，共 ${ledger.entries.size} 筆") {
                csvLauncher.launch("記帳-${LocalDate.now()}.csv")
            }
        }
        Text(
            "記帳資料也會包含在「設定 → 匯出備份」中。",
            color = Hud.TextFaint,
            fontSize = 12.sp,
        )
    }

    if (budgetDialog) {
        var text by remember { mutableStateOf(if (settings.monthlyBudget > 0) settings.monthlyBudget.toString() else "") }
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
    if (accountDialog) {
        AccountPickerDialog(ledger, ledger.defaultAccount, onPick = {
            ledger.defaultAccount = it
            accountDialog = false
        }, onDismiss = { accountDialog = false })
    }
    if (categoryDialog) {
        CategoryManagerDialog(ledger, onDismiss = { categoryDialog = false })
    }
    if (recurringDialog) {
        RecurringManagerDialog(ledger, onDismiss = { recurringDialog = false })
    }
}

@Composable
private fun SettingRow(title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Hud.Text, fontSize = 15.sp)
            Text(desc, color = Hud.TextDim, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hud.TextFaint)
    }
}

/** 選帳戶（對話框版，設定頁用）。 */
@Composable
fun AccountPickerDialog(ledger: LedgerRepository, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇帳戶") },
        text = {
            Column {
                ledger.accounts.forEach { a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(a.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(a.name, color = if (a.id == current) Hud.Cyan else Hud.Text, modifier = Modifier.weight(1f))
                        if (a.id == current) Icon(Icons.Filled.Star, null, tint = Hud.Cyan, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}
