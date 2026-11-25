package com.even.membercards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CardRepository
import com.even.membercards.data.Categories
import com.even.membercards.data.EntryType
import com.even.membercards.data.LedgerEntry
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.LocationTagger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.even.membercards.data.formatNumber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** 計算機式記帳：選分類 → 按金額 → ✓。支援 + − 計算、日期、帳戶、常用備註。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerEditScreen(
    ledger: LedgerRepository,
    repo: CardRepository,
    settings: AppSettings,
    entryId: String?,
    prefillAmount: Double? = null,
    prefillMerchant: String = "",
    prefillMethod: String = "",
    prefillType: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val existing = remember(entryId) { entryId?.let { ledger.get(it) } }
    // 位置：沒動過就用資料庫裡最新的（背景定位可能晚一點才寫入）
    val live = existing?.let { e -> ledger.entries.firstOrNull { it.id == e.id } }
    var locTouched by remember { mutableStateOf(false) }
    var locLat by remember { mutableStateOf<Double?>(null) }
    var locLng by remember { mutableStateOf<Double?>(null) }
    var locAcc by remember { mutableStateOf(0f) }
    var locPlace by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    val showLat = if (locTouched) locLat else live?.lat
    val showLng = if (locTouched) locLng else live?.lng
    val showPlace = if (locTouched) locPlace else live?.place.orEmpty()
    val locScope = rememberCoroutineScope()

    fun locateNow() {
        if (locating) return
        locating = true
        locScope.launch {
            val l = LocationTagger.current(context)
            if (l == null) {
                locating = false
                context.toast("定位失敗，請確認已開啟定位")
                return@launch
            }
            locTouched = true
            locLat = l.latitude; locLng = l.longitude; locAcc = l.accuracy; locPlace = ""
            locPlace = LocationTagger.address(context, l.latitude, l.longitude)
            locating = false
        }
    }
    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) locateNow() else context.toast("需要位置權限")
    }

    fun fmt(v: Double?) = when {
        v == null -> ""
        v == Math.floor(v) -> v.toLong().toString()
        else -> v.toString()
    }

    val initialAccount = remember {
        existing?.account
            ?: ledger.accounts.firstOrNull {
                prefillMethod.isNotBlank() &&
                    (it.name.contains(prefillMethod, true) || prefillMethod.contains(it.name, true))
            }?.id
            ?: ledger.defaultAccount
    }

    var type by rememberSaveable { mutableStateOf(existing?.type ?: prefillType ?: EntryType.EXPENSE) }
    var category by rememberSaveable {
        mutableStateOf(
            existing?.category
                ?: if (prefillType == EntryType.INCOME) ledger.categories(EntryType.INCOME).firstOrNull()?.name ?: "薪水"
                else if (prefillMerchant.isNotBlank()) Categories.guess(prefillMerchant, repo.cards.toList()) else "食物"
        )
    }
    var expr by rememberSaveable { mutableStateOf(fmt(existing?.amount ?: prefillAmount)) }
    var note by rememberSaveable {
        mutableStateOf(existing?.let { e -> e.note.ifBlank { e.merchant } } ?: prefillMerchant)
    }
    var account by rememberSaveable { mutableStateOf(initialAccount) }
    var toAccount by rememberSaveable {
        mutableStateOf(existing?.toAccount ?: ledger.accounts.firstOrNull { it.id != initialAccount }?.id.orEmpty())
    }
    var time by rememberSaveable { mutableStateOf(existing?.time ?: System.currentTimeMillis()) }
    // null = 關閉；"from" / "to" = 正在選哪個帳戶
    var sheetTarget by remember { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var manageCategories by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (existing == null && ledger.autoPopupAccount) sheetTarget = "from"
    }

    val cats = ledger.categories(type)
    val result = evaluate(expr)
    val hasOp = expr.drop(1).any { it == '+' || it == '-' }

    fun switchType(t: String) {
        type = t
        if (t != EntryType.TRANSFER && ledger.categories(t).none { it.name == category }) {
            category = ledger.categories(t).first().name
        }
    }

    fun press(key: String) {
        when (key) {
            "⌫" -> expr = expr.dropLast(1)
            "+", "-" -> {
                if (expr.isEmpty()) return
                expr = if (expr.last() == '+' || expr.last() == '-') expr.dropLast(1) + key else expr + key
            }
            "." -> {
                val seg = expr.substringAfterLast('+').substringAfterLast('-')
                if (seg.contains('.')) return
                expr += if (seg.isEmpty()) "0." else "."
            }
            else -> {
                if (expr.length >= 18) return
                expr = if (expr == "0") key else expr + key
            }
        }
    }

    fun save() {
        val v = evaluate(expr)
        if (v == null || v <= 0) {
            context.toast("請輸入金額")
            return
        }
        if (type == EntryType.TRANSFER && (account.isBlank() || toAccount.isBlank() || account == toAccount)) {
            context.toast("請選擇不同的轉出／轉入帳戶")
            return
        }
        val n = note.trim()
        val keepMerchant = existing?.merchant?.takeIf { it.isNotBlank() && it == n }
            ?: prefillMerchant.takeIf { existing == null && it.isNotBlank() && it == n }
            ?: ""
        val base = existing?.let { ledger.get(it.id) ?: it } ?: LedgerEntry(amount = v, method = prefillMethod)
        val withLoc = if (locTouched) {
            base.copy(lat = locLat, lng = locLng, accuracy = locAcc, place = if (locLat == null) "" else locPlace)
        } else base
        ledger.upsert(
            withLoc.copy(
                amount = v,
                type = type,
                category = if (type == EntryType.TRANSFER) "轉帳" else category,
                note = if (keepMerchant.isNotBlank()) "" else n,
                merchant = keepMerchant,
                account = account,
                toAccount = if (type == EntryType.TRANSFER) toAccount else "",
                time = time,
            )
        )
        if (existing == null && type != EntryType.TRANSFER && account.isNotBlank()) ledger.defaultAccount = account
        onBack()
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
                            Text(if (existing == null) "添加" else "編輯", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (existing?.isAuto == true) "AUTO RECORD // 自動記帳" else "ENTRY // INPUT",
                                style = MonoLabel,
                                color = if (existing?.isAuto == true) Hud.Ok else Hud.Cyan,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "取消") }
                    },
                    actions = {
                        if (existing != null) {
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "刪除") }
                        }
                    },
                    colors = hudTopBarColors(),
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
            ) {
                // 支出／收入／轉帳
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Hud.LineBright, RoundedCornerShape(8.dp)),
                ) {
                    listOf(EntryType.EXPENSE to "支出", EntryType.INCOME to "收入", EntryType.TRANSFER to "轉帳").forEach { (k, label) ->
                        val sel = type == k
                        Text(
                            label,
                            color = if (sel) Hud.OnPrimary else Hud.TextDim,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (sel) Hud.Cyan else Color.Transparent)
                                .clickable { switchType(k) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }

                // 分類格子 或 轉帳帳戶
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (type == EntryType.TRANSFER) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TransferAccountCard("轉出", ledger, account) { sheetTarget = "from" }
                            Text("↓", color = Hud.Cyan, fontSize = 22.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            TransferAccountCard("轉入", ledger, toAccount) { sheetTarget = "to" }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(cats, key = { it.type + it.name }) { c ->
                                Column(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { category = c.name }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CategoryIcon(c, 50.dp, selected = category == c.name)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        c.name,
                                        fontSize = 13.sp,
                                        color = if (category == c.name) Hud.Cyan else Hud.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            item(key = "__manage") {
                                Column(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { manageCategories = true }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Hud.LineBright, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Filled.Add, null, tint = Hud.TextDim) }
                                    Spacer(Modifier.height(4.dp))
                                    Text("設定", fontSize = 13.sp, color = Hud.TextDim)
                                }
                            }
                        }
                    }
                }

                // 輸入面板
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Hud.Surface)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Hud.Line)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (type != EntryType.TRANSFER) {
                            val acc = ledger.account(account)
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Hud.Surface2)
                                    .border(1.dp, Hud.LineBright, RoundedCornerShape(8.dp))
                                    .clickable { sheetTarget = "from" }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(acc?.emoji ?: "❔", fontSize = 18.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(acc?.name ?: "選帳戶", color = Hud.Text, fontSize = 13.sp, maxLines = 1)
                            }
                        } else {
                            Text("TRANSFER", style = MonoLabel, color = Hud.Cyan)
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            if (hasOp) {
                                Text(expr, style = MonoLabel.copy(fontSize = 12.sp), color = Hud.TextDim, maxLines = 1)
                            }
                            Text(
                                if (hasOp) "= ${formatNumber(result ?: 0.0)}" else expr.ifBlank { "0" },
                                fontFamily = Mono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = when (type) {
                                    EntryType.INCOME -> Hud.Ok
                                    EntryType.TRANSFER -> Hud.Cyan
                                    else -> Hud.Text
                                },
                                maxLines = 1,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("備註：請輸入備註…", color = Hud.TextFaint) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = hudFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 消費地點
                    if (type != EntryType.TRANSFER) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val lat = showLat
                            val lng = showLng
                            val chipMod = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Hud.Surface2)
                                .border(1.dp, Hud.Line, RoundedCornerShape(6.dp))
                            when {
                                locating -> Text("📍 定位中…", color = Hud.TextDim, fontSize = 13.sp, modifier = chipMod.padding(horizontal = 10.dp, vertical = 6.dp))
                                lat != null && lng != null -> {
                                    Text(
                                        "📍 " + showPlace.ifBlank { "%.5f, %.5f".format(lat, lng) },
                                        color = Hud.Cyan,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = chipMod
                                            .weight(1f, fill = false)
                                            .clickable { openMap(context, lat, lng, note.ifBlank { showPlace }) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                    Text(
                                        "✕",
                                        color = Hud.TextDim,
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                locTouched = true
                                                locLat = null; locLng = null; locPlace = ""
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                                else -> Text(
                                    if (existing == null && settings.recordLocation) "📍 儲存時記錄目前位置" else "📍 加入目前位置",
                                    color = Hud.TextDim,
                                    fontSize = 13.sp,
                                    modifier = chipMod
                                        .clickable {
                                            if (LocationTagger.hasPermission(context)) locateNow()
                                            else locPermLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                                )
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    val quick = if (type == EntryType.TRANSFER) emptyList() else ledger.quickNotes(category)
                    if (quick.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quick) { q ->
                                Text(
                                    q,
                                    color = if (note == q) Hud.OnPrimary else Hud.Text,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (note == q) Hud.Cyan else Hud.Surface2)
                                        .clickable { note = if (note == q) "" else q }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }

                    // 計算機鍵盤
                    val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                    val dateLabel = if (date == LocalDate.now()) "今日" else "${date.monthValue}/${date.dayOfMonth}"
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KeyRow {
                            Key("7") { press("7") }; Key("8") { press("8") }; Key("9") { press("9") }
                            Key("📅 $dateLabel", accent = Hud.Cyan, small = true) { datePicker = true }
                        }
                        KeyRow {
                            Key("4") { press("4") }; Key("5") { press("5") }; Key("6") { press("6") }
                            Key("+") { press("+") }
                        }
                        KeyRow {
                            Key("1") { press("1") }; Key("2") { press("2") }; Key("3") { press("3") }
                            Key("−") { press("-") }
                        }
                        KeyRow {
                            Key(".") { press(".") }; Key("0") { press("0") }; Key("⌫") { press("⌫") }
                            Key("✓", filled = true) { save() }
                        }
                    }
                }
            }
        }
    }

    // 帳戶選單
    sheetTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { sheetTarget = null },
            containerColor = Hud.Surface,
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (target == "to") "轉入帳戶" else if (type == EntryType.TRANSFER) "轉出帳戶" else "帳戶",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Hud.Text,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp),
                )
                val current = if (target == "to") toAccount else account
                ledger.accounts.forEach { a ->
                    val b = ledger.balance(a)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (target == "to") toAccount = a.id else account = a.id
                                sheetTarget = null
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Hud.Cyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(a.emoji, fontSize = 19.sp) }
                        Spacer(Modifier.width(12.dp))
                        Text(a.name, color = Hud.Text, fontSize = 16.sp)
                        if (a.id == current) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Check, null, tint = Hud.Cyan)
                        }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            if (a.isCredit && b < 0) Text("（我欠的）", color = Hud.TextDim, fontSize = 11.sp)
                            Text(
                                formatNumber(if (a.isCredit && b < 0) -b else b),
                                fontFamily = Mono,
                                color = if (b < 0) Hud.Danger else Hud.Text,
                            )
                        }
                    }
                }
                HudDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("每次自動彈出", color = Hud.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = ledger.autoPopupAccount,
                        onCheckedChange = { ledger.autoPopupAccount = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Hud.OnPrimary,
                            checkedTrackColor = Hud.Cyan,
                            uncheckedThumbColor = Hud.TextDim,
                            uncheckedTrackColor = Hud.Surface2,
                            uncheckedBorderColor = Hud.LineBright,
                        ),
                    )
                }
                Text("帳戶可在記帳 →「帳戶」頁新增或修改。", color = Hud.TextFaint, fontSize = 12.sp)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (datePicker) {
        val current = Instant.ofEpochMilli(time).atZone(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        time = d.atTime(current.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    datePicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }

    if (manageCategories) {
        CategoryManagerDialog(ledger, onDismiss = { manageCategories = false }, initialType = type)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除這筆記帳？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    existing?.let { ledger.delete(it.id) }
                    onBack()
                }) { Text("刪除", color = Hud.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TransferAccountCard(label: String, ledger: LedgerRepository, id: String, onClick: () -> Unit) {
    val a = ledger.account(id)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Hud.Surface.copy(alpha = 0.9f))
            .border(1.dp, Hud.LineBright, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MonoLabel, color = Hud.Cyan, modifier = Modifier.width(44.dp))
        Text(a?.emoji ?: "❔", fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(a?.name ?: "選擇帳戶", color = Hud.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (a != null) Text(formatNumber(ledger.balance(a)), fontFamily = Mono, color = Hud.TextDim)
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun RowScope.Key(
    label: String,
    accent: Color = Hud.Text,
    filled: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) Hud.Cyan else Hud.Surface2)
            .border(1.dp, if (filled) Hud.Cyan else Hud.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) Hud.OnPrimary else accent,
            fontFamily = if (small) null else Mono,
            fontSize = if (small) 15.sp else 22.sp,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 計算 "120+30-5" 這類算式；格式錯誤回傳 null。 */
private fun evaluate(expr: String): Double? {
    if (expr.isBlank()) return null
    var total = 0.0
    var sign = 1
    val num = StringBuilder()
    fun flush(): Boolean {
        if (num.isEmpty()) return true
        val v = num.toString().toDoubleOrNull() ?: return false
        total += sign * v
        num.clear()
        return true
    }
    for (ch in expr) {
        when (ch) {
            '+', '-' -> {
                if (!flush()) return null
                sign = if (ch == '+') 1 else -1
            }
            else -> num.append(ch)
        }
    }
    if (!flush()) return null
    return Math.round(total * 100) / 100.0
}
