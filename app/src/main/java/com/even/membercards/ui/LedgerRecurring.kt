package com.even.membercards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.EntryType
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.Recurring
import com.even.membercards.data.formatNumber
import java.time.LocalDate
import java.time.YearMonth

/** 定期收支清單。 */
@Composable
fun RecurringManagerDialog(ledger: LedgerRepository, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var creating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定期收支") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "訂閱、房租、薪水這類每月固定的收支，到了設定的日期會自動記一筆。",
                    color = Hud.TextDim,
                    fontSize = 12.sp,
                )
                if (ledger.recurring.isEmpty()) {
                    Text("還沒有定期收支", color = Hud.TextFaint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ledger.recurring, key = { it.id }) { r ->
                            val cat = ledger.category(r.category, r.type)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Hud.Surface2)
                                    .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
                                    .clickable { editing = r }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryIcon(cat, 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        r.title.ifBlank { r.category },
                                        color = if (r.enabled) Hud.Text else Hud.TextFaint,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (r.enabled) "每月 ${r.day} 號 · 下次 ${ledger.nextDue(r)}" else "已暫停",
                                        style = MonoLabel.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                                        color = Hud.TextDim,
                                    )
                                }
                                Text(
                                    (if (r.type == EntryType.INCOME) "+" else "-") + formatNumber(r.amount),
                                    fontFamily = Mono,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (r.type == EntryType.INCOME) Hud.Ok else Hud.Text,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("＋ 新增") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )

    editing?.let { r -> RecurringEditDialog(ledger, r, onDismiss = { editing = null }) }
    if (creating) RecurringEditDialog(ledger, null, onDismiss = { creating = false })
}

@Composable
private fun RecurringEditDialog(ledger: LedgerRepository, r: Recurring?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(r?.title ?: "") }
    var type by remember { mutableStateOf(r?.type ?: EntryType.EXPENSE) }
    var category by remember { mutableStateOf(r?.category ?: "訂閱") }
    var amount by remember {
        mutableStateOf(r?.amount?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "")
    }
    var account by remember { mutableStateOf(r?.account ?: ledger.defaultAccount) }
    var day by remember { mutableStateOf((r?.day ?: LocalDate.now().dayOfMonth).toString()) }
    var enabled by remember { mutableStateOf(r?.enabled ?: true) }
    var includeThisMonth by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (r == null) "新增定期收支" else "編輯定期收支") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(EntryType.EXPENSE to "支出", EntryType.INCOME to "收入").forEach { (k, label) ->
                            FilterChip(
                                selected = type == k,
                                onClick = {
                                    type = k
                                    if (ledger.categories(k).none { it.name == category }) category = ledger.categories(k).first().name
                                },
                                label = { Text(label) },
                                colors = hudChipColors(),
                                border = hudChipBorder(type == k),
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("名稱（例：Netflix、房租、薪水）") },
                        singleLine = true,
                        colors = hudFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                            label = { Text("金額") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = hudFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = day,
                            onValueChange = { v -> day = v.filter { it.isDigit() }.take(2) },
                            label = { Text("每月幾號") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = hudFieldColors(),
                            modifier = Modifier.width(100.dp),
                        )
                    }
                }
                item {
                    Column {
                        Text("分類", style = MonoLabel, color = Hud.TextDim)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ledger.categories(type)) { c ->
                                FilterChip(
                                    selected = category == c.name,
                                    onClick = { category = c.name },
                                    label = { Text("${c.emoji} ${c.name}") },
                                    colors = hudChipColors(),
                                    border = hudChipBorder(category == c.name),
                                )
                            }
                        }
                    }
                }
                item {
                    Column {
                        Text("帳戶", style = MonoLabel, color = Hud.TextDim)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ledger.accounts) { a ->
                                FilterChip(
                                    selected = account == a.id,
                                    onClick = { account = a.id },
                                    label = { Text("${a.emoji} ${a.name}") },
                                    colors = hudChipColors(),
                                    border = hudChipBorder(account == a.id),
                                )
                            }
                        }
                    }
                }
                item {
                    Column {
                        SwitchLine("啟用", enabled) { enabled = it }
                        if (r == null) {
                            SwitchLine("本月也記一筆（扣款日已過時）", includeThisMonth) { includeThisMonth = it }
                        }
                        if (r != null) {
                            TextButton(onClick = { confirmDelete = true }) { Text("刪除", color = Hud.Danger) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = amount.toDoubleOrNull()
                val d = day.toIntOrNull()?.coerceIn(1, 31)
                if (v == null || v <= 0 || d == null) {
                    context.toast("請輸入金額與日期")
                    return@TextButton
                }
                val now = YearMonth.now()
                val base = r ?: Recurring(
                    title = title.trim(),
                    amount = v,
                    // 本月扣款日已過且不想補記 → 從下個月開始
                    startMonth = if (!includeThisMonth && d < LocalDate.now().dayOfMonth) now.plusMonths(1).toString() else now.toString(),
                )
                ledger.upsertRecurring(
                    base.copy(
                        title = title.trim(),
                        type = type,
                        category = category,
                        amount = v,
                        account = account,
                        day = d,
                        enabled = enabled,
                    )
                )
                val n = ledger.runRecurring()
                if (n > 0) context.toast("已自動記入 $n 筆")
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (confirmDelete && r != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除「${r.title}」？") },
            text = { Text("已經記入的紀錄會保留。") },
            confirmButton = {
                TextButton(onClick = {
                    ledger.deleteRecurring(r.id)
                    confirmDelete = false
                    onDismiss()
                }) { Text("刪除", color = Hud.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Hud.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Hud.OnPrimary,
                checkedTrackColor = Hud.Cyan,
                uncheckedThumbColor = Hud.TextDim,
                uncheckedTrackColor = Hud.Surface2,
                uncheckedBorderColor = Hud.LineBright,
            ),
        )
    }
}
