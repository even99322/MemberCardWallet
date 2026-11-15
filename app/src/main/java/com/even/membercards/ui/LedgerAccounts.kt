package com.even.membercards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.Account
import com.even.membercards.data.Category
import com.even.membercards.data.EntryType
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.formatNumber

@Composable
fun LedgerAccountsTab(ledger: LedgerRepository) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }

    val balances = ledger.accounts.associate { it.id to ledger.balance(it) }
    val assets = balances.values.filter { it > 0 }.sum()
    val liabilities = balances.values.filter { it < 0 }.sumOf { -it }
    val net = assets - liabilities

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "net") {
            HudPanel {
                Text("NET WORTH // 淨資產", style = MonoLabel, color = Hud.TextDim, modifier = Modifier.padding(top = 6.dp))
                Text(
                    formatNumber(net),
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = if (net < 0) Hud.Danger else Hud.Text,
                )
                Row(
                    Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile("資產", assets, Hud.Ok, Modifier.weight(1f))
                    StatTile("負債", liabilities, Hud.Danger, Modifier.weight(1f))
                }
            }
        }

        Account.typeLabels.forEach { (type, label) ->
            val group = ledger.accounts.filter { it.type == type }
            if (group.isNotEmpty()) {
                item(key = "g_$type") {
                    val sum = group.sumOf { balances[it.id] ?: 0.0 }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("//", style = MonoLabel, color = Hud.Cyan)
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = Hud.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (type == "credit" && sum < 0) "負債 ${formatNumber(-sum)}" else formatNumber(sum),
                            style = MonoLabel,
                            color = Hud.TextDim,
                        )
                    }
                }
                items(group, key = { it.id }) { a ->
                    val b = balances[a.id] ?: 0.0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Hud.Surface.copy(alpha = 0.9f))
                            .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
                            .clickable { editing = a }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Hud.Cyan.copy(alpha = 0.12f))
                                .border(1.dp, Hud.Cyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(a.emoji, fontSize = 19.sp) }
                        Spacer(Modifier.width(12.dp))
                        Text(a.name, color = Hud.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            if (a.isCredit && b < 0) Text("（我欠的）", color = Hud.TextDim, fontSize = 11.sp)
                            Text(
                                "TWD ${formatNumber(if (a.isCredit && b < 0) -b else b)}",
                                fontFamily = Mono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = if (b < 0) Hud.Danger else Hud.Text,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hud.TextFaint)
                    }
                }
            }
        }

        item(key = "add") {
            Column {
                HudButton(
                    text = "＋ 新增帳戶",
                    onClick = { creating = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    "帳戶餘額 = 初始金額 + 收入 − 支出 ± 轉帳。點帳戶可修改初始金額。",
                    color = Hud.TextFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    editing?.let { a ->
        AccountEditDialog(ledger, a, onDismiss = { editing = null })
    }
    if (creating) {
        AccountEditDialog(ledger, null, onDismiss = { creating = false })
    }
}

@Composable
private fun StatTile(label: String, value: Double, accent: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Hud.Surface2)
            .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .background(accent)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, color = Hud.TextDim, fontSize = 13.sp)
        }
        Text(formatNumber(value), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Hud.Text)
    }
}

@Composable
private fun AccountEditDialog(ledger: LedgerRepository, account: Account?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.type ?: "bank") }
    var emoji by remember { mutableStateOf(account?.emoji ?: Account.typeEmoji[type] ?: "💵") }
    var initial by remember {
        mutableStateOf(
            account?.initial?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "0"
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新增帳戶" else "編輯帳戶") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 4) emoji = it },
                        label = { Text("圖示") },
                        singleLine = true,
                        colors = hudFieldColors(),
                        modifier = Modifier.width(76.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名稱") },
                        singleLine = true,
                        colors = hudFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Account.typeLabels.entries.toList()) { (k, label) ->
                        FilterChip(
                            selected = type == k,
                            onClick = {
                                if (emoji == Account.typeEmoji[type]) emoji = Account.typeEmoji[k] ?: emoji
                                type = k
                            },
                            label = { Text(label) },
                            colors = hudChipColors(),
                            border = hudChipBorder(type == k),
                        )
                    }
                }
                OutlinedTextField(
                    value = initial,
                    onValueChange = { v -> initial = v.filter { it.isDigit() || it == '.' || it == '-' } },
                    label = { Text(if (type == "credit") "初始金額（欠款請填負數）" else "初始金額") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (account != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("刪除這個帳戶", color = Hud.Danger) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val a = (account ?: Account(name = name.trim())).copy(
                    name = name.trim(),
                    type = type,
                    emoji = emoji.ifBlank { Account.typeEmoji[type] ?: "💵" },
                    initial = initial.toDoubleOrNull() ?: 0.0,
                )
                ledger.upsertAccount(a)
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (confirmDelete && account != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除帳戶「${account.name}」？") },
            text = { Text("相關記帳紀錄會保留，但不再屬於任何帳戶。") },
            confirmButton = {
                TextButton(onClick = {
                    ledger.deleteAccount(account.id)
                    confirmDelete = false
                    onDismiss()
                }) { Text("刪除", color = Hud.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

/** 分類管理：新增／刪除自訂分類。 */
@Composable
fun CategoryManagerDialog(ledger: LedgerRepository, onDismiss: () -> Unit, initialType: String = EntryType.EXPENSE) {
    var type by remember { mutableStateOf(initialType) }
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🏷️") }
    val colors = listOf(0xFF38E8FF, 0xFF3CFFA0, 0xFFFFC53D, 0xFFFF8A3D, 0xFFFF5470, 0xFFE45CFF, 0xFF8B7CFF, 0xFF8A9BB3)
    var color by remember { mutableStateOf(colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分類管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(EntryType.EXPENSE to "支出", EntryType.INCOME to "收入").forEach { (k, label) ->
                        FilterChip(
                            selected = type == k,
                            onClick = { type = k },
                            label = { Text(label) },
                            colors = hudChipColors(),
                            border = hudChipBorder(type == k),
                        )
                    }
                }
                val custom = ledger.customCategories.filter { it.type == type }
                if (custom.isEmpty()) {
                    Text("還沒有自訂分類", color = Hud.TextDim, fontSize = 12.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(custom, key = { it.type + it.name }) { c ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryIcon(c, 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(c.name, color = Hud.Text, modifier = Modifier.weight(1f))
                                IconButton(onClick = { ledger.deleteCategory(c) }) {
                                    Icon(Icons.Filled.Close, "刪除", tint = Hud.TextDim)
                                }
                            }
                        }
                    }
                }
                HudDivider()
                Text("新增分類", style = MonoLabel, color = Hud.Cyan)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 4) emoji = it },
                        label = { Text("圖示") },
                        singleLine = true,
                        colors = hudFieldColors(),
                        modifier = Modifier.width(76.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名稱") },
                        singleLine = true,
                        colors = hudFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { c ->
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(if (c == color) Modifier.border(2.dp, Hud.Text, CircleShape) else Modifier)
                                .clickable { color = c }
                        )
                    }
                }
                Text("圖示可輸入任一個表情符號，例如 🐶 🎮 💄", color = Hud.TextFaint, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    if (ledger.addCategory(Category(name.trim(), emoji.ifBlank { "🏷️" }, color, type, custom = true))) {
                        name = ""
                    }
                }
            }) { Text("新增") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
