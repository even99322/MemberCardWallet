package com.even.membercards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.CardRepository
import com.even.membercards.data.Categories
import com.even.membercards.data.EntryType
import com.even.membercards.data.Invoice
import com.even.membercards.data.InvoiceDraw
import com.even.membercards.data.InvoiceQr
import com.even.membercards.data.InvoiceRepository
import com.even.membercards.data.InvoiceRules
import com.even.membercards.data.LedgerEntry
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.formatMoney
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 統一發票：掃描紙本發票 QR 存檔＋記帳、下載中獎號碼、自動對獎、末三碼快速對獎。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    invoices: InvoiceRepository,
    ledger: LedgerRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(InvoiceRules.latestDrawnPeriod()) }
    var pending by remember { mutableStateOf<InvoiceQr.Parsed?>(null) }
    var continuous by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var editDraw by remember { mutableStateOf<String?>(null) }
    var openInvoice by remember { mutableStateOf<Invoice?>(null) }

    fun fetch(silent: Boolean) {
        if (loading) return
        loading = true
        scope.launch {
            val r = invoices.fetchDraws()
            loading = false
            r.onSuccess { n -> if (!silent) context.toast("已更新 $n 期中獎號碼") }
                .onFailure { if (!silent) context.toast("下載失敗，可以改用手動輸入號碼") }
        }
    }

    // 進來時若最新一期還沒有號碼，自動抓一次
    LaunchedEffect(Unit) {
        if (invoices.draw(InvoiceRules.latestDrawnPeriod()) == null) fetch(silent = true)
    }

    lateinit var launchScan: () -> Unit
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: run { continuous = false; return@rememberLauncherForActivityResult }
        when {
            InvoiceQr.isRightCode(text) -> {
                context.toast("這是右側的 QR Code，請掃左邊那個")
                launchScan()
            }
            else -> {
                val p = InvoiceQr.parse(text)
                when {
                    p == null -> {
                        context.toast("看不懂這個條碼，請掃描電子發票左側 QR Code")
                        continuous = false
                    }
                    invoices.find(p.number) != null -> {
                        context.toast("${p.number} 已經存過了")
                        if (continuous) launchScan()
                    }
                    else -> pending = p
                }
            }
        }
    }
    launchScan = {
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("對準發票「左側」的 QR Code")
                setBeepEnabled(true)
                setOrientationLocked(false)
                // 品名可能是 Big5 或 UTF-8：先用 ISO-8859-1 原樣讀進來，解析時再轉碼
                addExtra(Intents.Scan.CHARACTER_SET, "ISO-8859-1")
            }
        )
    }

    fun save(p: InvoiceQr.Parsed, merchant: String, record: Boolean) {
        var entryId = ""
        if (record && p.amount > 0) {
            val cards = CardRepository.instance(context).cards.toList()
            val time = if (p.date == LocalDate.now()) System.currentTimeMillis()
            else p.date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val e = LedgerEntry(
                time = time,
                amount = p.amount.toDouble(),
                merchant = merchant,
                category = Categories.guess(merchant, cards),
                note = "發票 ${p.number}",
                key = "inv:${p.number}",
                type = EntryType.EXPENSE,
                account = ledger.defaultAccount,
            )
            if (ledger.entries.none { it.key == e.key }) {
                ledger.upsert(e)
                entryId = e.id
            }
        }
        val inv = Invoice(
            number = p.number, date = p.date, random = p.random, amount = p.amount,
            sellerId = p.sellerId, items = p.items.take(5).joinToString("、"), entryId = entryId,
        )
        invoices.add(inv)
        selected = inv.period
        val won = invoices.find(inv.number)?.prize ?: 0
        context.toast(if (won > 0) "🎉 這張中獎了！" else "已存入 ${p.number}" + if (entryId.isNotEmpty()) "，並記帳" else "")
    }

    Box(Modifier.fillMaxSize().hudBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("發票對獎", fontWeight = FontWeight.SemiBold)
                            Text("INVOICE // ${invoices.invoices.size} SAVED", style = MonoLabel, color = Hud.Cyan)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Hud.Cyan)
                            Spacer(Modifier.width(14.dp))
                        } else {
                            IconButton(onClick = { fetch(silent = false) }) { Icon(Icons.Filled.Refresh, "更新中獎號碼") }
                        }
                    },
                    colors = hudTopBarColors(),
                )
            },
        ) { padding ->
            val list = invoices.inPeriod(selected)
            val draw = invoices.draw(selected)
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HudButton(
                            text = "◎  掃描發票",
                            onClick = { continuous = false; launchScan() },
                            modifier = Modifier.weight(1f).height(52.dp),
                        )
                        HudButton(
                            text = "連續掃描",
                            onClick = { continuous = true; launchScan() },
                            modifier = Modifier.weight(0.8f).height(52.dp),
                            accent = Hud.Violet,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HudButton(
                        text = "#  末三碼快速對獎",
                        onClick = { showQuick = true },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        accent = Hud.Ok,
                    )
                }

                item {
                    val periods = (invoices.periods() + InvoiceRules.latestDrawnPeriod() + Invoice.periodOf(LocalDate.now()))
                        .distinct().sortedDescending()
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        periods.forEach { p ->
                            FilterChip(
                                selected = p == selected,
                                onClick = { selected = p },
                                label = { Text(InvoiceRules.label(p)) },
                                colors = hudChipColors(),
                                border = hudChipBorder(p == selected),
                            )
                        }
                    }
                }

                item { DrawPanel(selected, draw, onEdit = { editDraw = selected }, onFetch = { fetch(false) }) }

                item {
                    val total = list.sumOf { it.amount }
                    val wins = list.filter { (it.prize ?: 0) > 0 }
                    HudSection("01", "本期發票")
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("張數", "${list.size}", Modifier.weight(1f))
                        StatBox("金額", formatMoney(total.toDouble()), Modifier.weight(1f))
                        StatBox(
                            "中獎",
                            if (wins.isEmpty()) "—" else formatMoney(wins.sumOf { it.prize ?: 0 }.toDouble()),
                            Modifier.weight(1f),
                            if (wins.isEmpty()) Hud.TextDim else Hud.Ok,
                        )
                    }
                }

                if (list.isEmpty()) {
                    item {
                        Text(
                            "這期還沒有發票。\n掃描紙本電子發票左邊的 QR Code，就會自動存檔、記帳，開獎後自動對獎。\n雲端載具的發票請在財政部「統一發票兌獎」App 查詢。",
                            color = Hud.TextDim,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { inv ->
                    InvoiceRow(inv, drawKnown = draw != null) { openInvoice = inv }
                }
            }
        }
    }

    pending?.let { p ->
        ScanConfirmDialog(
            p = p,
            continuous = continuous,
            onDismiss = { pending = null; continuous = false },
            onSave = { merchant, record, again ->
                save(p, merchant, record)
                pending = null
                if (again) { continuous = true; launchScan() } else continuous = false
            },
        )
    }

    if (showQuick) QuickCheckDialog(invoices, selected) { showQuick = false }

    editDraw?.let { period ->
        DrawEditDialog(period, invoices.draw(period), onDismiss = { editDraw = null }) {
            invoices.putDraw(it)
            editDraw = null
            context.toast("已更新並重新對獎")
        }
    }

    openInvoice?.let { inv ->
        val cur = invoices.invoices.firstOrNull { it.id == inv.id } ?: inv
        AlertDialog(
            onDismissRequest = { openInvoice = null },
            containerColor = Hud.Surface,
            title = { Text(cur.number, fontFamily = Mono, color = Hud.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HudInfoRow("日期", cur.date.toString())
                    HudInfoRow("金額", formatMoney(cur.amount.toDouble()))
                    if (cur.sellerId.isNotBlank()) HudInfoRow("賣方統編", cur.sellerId)
                    if (cur.random.isNotBlank()) HudInfoRow("隨機碼", cur.random)
                    if (cur.items.isNotBlank()) HudInfoRow("品項", cur.items)
                    HudInfoRow(
                        "對獎",
                        when {
                            cur.prize == null -> "待開獎"
                            cur.prize > 0 -> "${cur.prizeName} ${formatMoney(cur.prize.toDouble())}"
                            else -> "未中獎"
                        },
                        if ((cur.prize ?: 0) > 0) Hud.Ok else Hud.Text,
                    )
                    if ((cur.prize ?: 0) > 0) {
                        Text(
                            "兌獎期限：${InvoiceRules.claimDeadline(cur.period)}，持發票到超商或郵局兌領（金額較大需到指定銀行）。",
                            color = Hud.TextDim, fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                if (cur.entryId.isBlank() && cur.amount > 0) {
                    TextButton(onClick = {
                        val merchant = cur.items.substringBefore("、").ifBlank { "發票 ${cur.number}" }
                        val e = LedgerEntry(
                            time = cur.date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            amount = cur.amount.toDouble(),
                            merchant = merchant,
                            category = Categories.guess(merchant),
                            note = "發票 ${cur.number}",
                            key = "inv:${cur.number}",
                            account = ledger.defaultAccount,
                        )
                        ledger.upsert(e)
                        invoices.update(cur.copy(entryId = e.id))
                        context.toast("已記帳")
                        openInvoice = null
                    }) { Text("記一筆帳") }
                } else {
                    TextButton(onClick = { openInvoice = null }) { Text("關閉") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    invoices.delete(cur.id)
                    openInvoice = null
                }) { Text("刪除發票", color = Hud.Danger) }
            },
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier, color: Color = Hud.Text) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Hud.Surface)
            .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MonoLabel, color = Hud.TextDim)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DrawPanel(period: String, draw: InvoiceDraw?, onEdit: () -> Unit, onFetch: () -> Unit) {
    HudPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("中獎號碼 · ${InvoiceRules.label(period)}", color = Hud.Text, fontWeight = FontWeight.SemiBold)
                val dd = InvoiceRules.drawDate(period)
                Text(
                    if (dd != null && LocalDate.now().isBefore(dd)) "開獎日 $dd" else "兌獎期限 ${InvoiceRules.claimDeadline(period) ?: "—"}",
                    style = MonoLabel, color = Hud.TextDim,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "手動輸入", tint = Hud.TextDim) }
        }
        if (draw == null) {
            val dd = InvoiceRules.drawDate(period)
            Text(
                if (dd != null && LocalDate.now().isBefore(dd)) "尚未開獎，開獎後會自動下載並對獎。"
                else "還沒有這期的號碼。按右上 ⟳ 下載，或按 ✎ 手動輸入。",
                color = Hud.TextDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
            )
            if (dd == null || !LocalDate.now().isBefore(dd)) {
                TextButton(onClick = onFetch) { Text("下載中獎號碼") }
            }
        } else {
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (draw.special.isNotBlank()) DrawLine("特別獎", "1000 萬", listOf(draw.special), Hud.Danger)
                if (draw.grand.isNotBlank()) DrawLine("特獎", "200 萬", listOf(draw.grand), Hud.Warn)
                if (draw.first.isNotEmpty()) DrawLine("頭獎", "20 萬", draw.first, Hud.Cyan)
                if (draw.extraSixth.isNotEmpty()) DrawLine("增開六獎", "200", draw.extraSixth, Hud.Violet)
                Text(
                    "頭獎末 7~3 碼相同：二獎 4 萬、三獎 1 萬、四獎 4 千、五獎 1 千、六獎 200 元",
                    color = Hud.TextFaint, fontSize = 11.sp,
                )
                if (draw.manual) Text("（手動輸入）", color = Hud.TextFaint, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DrawLine(name: String, money: String, nums: List<String>, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.width(72.dp)) {
            Text(name, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(money, color = Hud.TextFaint, fontSize = 11.sp)
        }
        Column {
            nums.forEach { n ->
                Row {
                    // 末三碼強調
                    Text(n.dropLast(3), fontFamily = Mono, fontSize = 18.sp, color = Hud.TextDim, letterSpacing = 1.sp)
                    Text(n.takeLast(3), fontFamily = Mono, fontSize = 18.sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun InvoiceRow(inv: Invoice, drawKnown: Boolean, onClick: () -> Unit) {
    val (tag, color) = when {
        (inv.prize ?: 0) > 0 -> "${inv.prizeName} ${formatMoney(inv.prize!!.toDouble())}" to Hud.Ok
        inv.prize == 0L -> "未中獎" to Hud.TextFaint
        drawKnown -> "未對獎" to Hud.Warn
        else -> "待開獎" to Hud.TextDim
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if ((inv.prize ?: 0) > 0) Hud.Ok.copy(alpha = 0.08f) else Hud.Surface)
            .border(1.dp, if ((inv.prize ?: 0) > 0) Hud.Ok.copy(alpha = 0.5f) else Hud.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(inv.number.take(2) + "-" + inv.number.drop(2).dropLast(3), fontFamily = Mono, color = Hud.Text, fontSize = 15.sp)
                Text(inv.number.takeLast(3), fontFamily = Mono, color = Hud.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                inv.date.toString() + if (inv.items.isNotBlank()) "  ·  ${inv.items}" else "",
                color = Hud.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(inv.amount.toDouble()), color = Hud.Text, fontWeight = FontWeight.SemiBold)
            HudTag(tag, color)
        }
    }
}

@Composable
private fun ScanConfirmDialog(
    p: InvoiceQr.Parsed,
    continuous: Boolean,
    onDismiss: () -> Unit,
    onSave: (merchant: String, record: Boolean, again: Boolean) -> Unit,
) {
    var merchant by remember { mutableStateOf(p.items.firstOrNull()?.take(20) ?: "") }
    var record by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.Surface,
        title = { Text("發票 ${p.number}", color = Hud.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatMoney(p.amount.toDouble()), color = Hud.Cyan, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                Text("${p.date}  ·  賣方 ${p.sellerId}", color = Hud.TextDim, fontSize = 12.sp)
                if (p.items.isNotEmpty()) {
                    Text(p.items.take(6).joinToString("、"), color = Hud.TextDim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("商家／名稱（記帳用）") },
                    singleLine = true,
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { record = !record }) {
                    Checkbox(
                        checked = record,
                        onCheckedChange = { record = it },
                        colors = CheckboxDefaults.colors(checkedColor = Hud.Cyan, checkmarkColor = Hud.OnPrimary),
                    )
                    Text("同時記一筆支出", color = Hud.Text)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(merchant.ifBlank { "發票 ${p.number}" }, record, true) }) {
                Text(if (continuous) "存，繼續掃" else "存並再掃一張")
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(merchant.ifBlank { "發票 ${p.number}" }, record, false) }) { Text("儲存") }
        },
    )
}

@Composable
private fun QuickCheckDialog(invoices: InvoiceRepository, initial: String, onDismiss: () -> Unit) {
    val periods = (invoices.draws.map { it.period }).sortedDescending()
    var period by remember { mutableStateOf(if (invoices.draw(initial) != null) initial else periods.firstOrNull() ?: initial) }
    var digits by remember { mutableStateOf("") }
    val draw = invoices.draw(period)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.Surface,
        title = { Text("末三碼快速對獎", color = Hud.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (periods.isEmpty()) {
                    Text("還沒有中獎號碼，請先下載或手動輸入。", color = Hud.Warn)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        periods.forEach { p ->
                            FilterChip(
                                selected = p == period,
                                onClick = { period = p; digits = "" },
                                label = { Text(InvoiceRules.label(p), fontSize = 12.sp) },
                                colors = hudChipColors(),
                                border = hudChipBorder(p == period),
                            )
                        }
                    }
                }
                Text(
                    digits.padEnd(3, '_').toList().joinToString(" "),
                    fontFamily = Mono, fontSize = 40.sp, color = Hud.Cyan,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                val hits = if (draw != null && digits.length == 3) InvoiceRules.quickCheck(digits, draw) else emptyList()
                Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
                    when {
                        digits.length < 3 -> Text("輸入發票號碼最後 3 碼", color = Hud.TextDim, fontSize = 13.sp)
                        hits.isEmpty() -> Text("沒中，下一張", color = Hud.TextDim, fontSize = 15.sp)
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("有機會！請核對前面號碼", color = Hud.Ok, fontWeight = FontWeight.Bold)
                            hits.forEach { Text(it, color = Hud.Ok, fontFamily = Mono, fontSize = 13.sp) }
                        }
                    }
                }
                // 數字鍵盤：輸完 3 碼後再按一個數字會自動開始下一張
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Hud.Surface2)
                                    .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
                                    .clickable {
                                        digits = when (k) {
                                            "C" -> ""
                                            "⌫" -> digits.dropLast(1)
                                            else -> if (digits.length >= 3) k else digits + k
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text(k, color = Hud.Text, fontSize = 20.sp, fontFamily = Mono) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun DrawEditDialog(
    period: String,
    existing: InvoiceDraw?,
    onDismiss: () -> Unit,
    onSave: (InvoiceDraw) -> Unit,
) {
    var special by remember { mutableStateOf(existing?.special ?: "") }
    var grand by remember { mutableStateOf(existing?.grand ?: "") }
    var first1 by remember { mutableStateOf(existing?.first?.getOrNull(0) ?: "") }
    var first2 by remember { mutableStateOf(existing?.first?.getOrNull(1) ?: "") }
    var first3 by remember { mutableStateOf(existing?.first?.getOrNull(2) ?: "") }
    var extra by remember { mutableStateOf(existing?.extraSixth?.joinToString(" ") ?: "") }
    fun clean(s: String) = s.filter { it.isDigit() }.take(8)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.Surface,
        title = { Text("輸入中獎號碼 · ${InvoiceRules.label(period)}", color = Hud.Text, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("特別獎（8 碼）", special) { special = clean(it) }
                NumField("特獎（8 碼）", grand) { grand = clean(it) }
                NumField("頭獎 1", first1) { first1 = clean(it) }
                NumField("頭獎 2", first2) { first2 = clean(it) }
                NumField("頭獎 3", first3) { first3 = clean(it) }
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it.filter { c -> c.isDigit() || c == ' ' || c == ',' } },
                    label = { Text("增開六獎（3 碼，空白分隔，可留空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    InvoiceDraw(
                        period = period,
                        special = special.takeIf { it.length == 8 } ?: "",
                        grand = grand.takeIf { it.length == 8 } ?: "",
                        first = listOf(first1, first2, first3).filter { it.length == 8 },
                        extraSixth = extra.split(' ', ',').map { it.trim() }.filter { it.length == 3 },
                        manual = true,
                    )
                )
            }) { Text("儲存並對獎") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumField(label: String, v: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = v,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = hudFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
