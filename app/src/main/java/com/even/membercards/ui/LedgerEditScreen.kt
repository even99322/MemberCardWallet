package com.even.membercards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CardRepository
import com.even.membercards.data.Categories
import com.even.membercards.data.LedgerEntry
import com.even.membercards.data.LedgerRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val existing = remember(entryId) { entryId?.let { ledger.get(it) } }

    fun fmtAmount(v: Double?) = when {
        v == null -> ""
        v == Math.floor(v) -> v.toLong().toString()
        else -> v.toString()
    }

    var amount by rememberSaveable { mutableStateOf(fmtAmount(existing?.amount ?: prefillAmount)) }
    var merchant by rememberSaveable { mutableStateOf(existing?.merchant ?: prefillMerchant) }
    var category by rememberSaveable {
        mutableStateOf(existing?.category ?: Categories.guess(prefillMerchant, repo.cards.toList()))
    }
    var categoryTouched by rememberSaveable { mutableStateOf(existing != null) }
    var method by rememberSaveable { mutableStateOf(existing?.method ?: prefillMethod.ifBlank { "LINE Pay" }) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var time by rememberSaveable { mutableStateOf(existing?.time ?: System.currentTimeMillis()) }
    var datePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val methods = remember {
        (listOf("LINE Pay", "現金", "信用卡", "悠遊卡", "街口", "全支付", "悠遊付", "Apple/Google Pay") +
            settings.payApps.map { it.label }).distinct()
    }
    val merchantSuggestions = remember {
        (ledger.recentMerchants(10) + repo.cards.map { it.name }).filter { it.isNotBlank() }.distinct().take(16)
    }

    fun save() {
        val v = amount.replace(",", "").toDoubleOrNull()
        if (v == null || v <= 0) {
            context.toast("請輸入金額")
            return
        }
        val base = existing ?: LedgerEntry(amount = v)
        ledger.upsert(
            base.copy(
                amount = v,
                merchant = merchant.trim(),
                category = category,
                method = method,
                note = note.trim(),
                time = time,
            )
        )
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
                            Text(if (existing == null) "記一筆" else "編輯記帳", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (existing?.isAuto == true) "AUTO RECORD // 自動記帳" else "ENTRY // INPUT",
                                style = MonoLabel,
                                color = if (existing?.isAuto == true) Hud.Ok else Hud.Cyan,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        if (existing != null) {
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "刪除") }
                        }
                        IconButton(onClick = { save() }) { Icon(Icons.Filled.Check, "儲存", tint = Hud.Cyan) }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HudSection("01", "金額")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                    prefix = { Text("$ ", fontFamily = Mono, fontSize = 26.sp, color = Hud.Cyan) },
                    placeholder = { Text("0", fontFamily = Mono, fontSize = 26.sp, color = Hud.TextFaint) },
                    singleLine = true,
                    textStyle = MonoLabel.copy(fontSize = 28.sp, letterSpacing = 1.sp, color = Hud.Text, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp),
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                HudSection("02", "商家")
                OutlinedTextField(
                    value = merchant,
                    onValueChange = {
                        merchant = it
                        if (!categoryTouched) category = Categories.guess(it, repo.cards.toList())
                    },
                    label = { Text("商家名稱") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (merchantSuggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(merchantSuggestions) { m ->
                            FilterChip(
                                selected = merchant == m,
                                onClick = {
                                    merchant = m
                                    if (!categoryTouched) category = Categories.guess(m, repo.cards.toList())
                                },
                                label = { Text(m) },
                                colors = hudChipColors(),
                                border = hudChipBorder(merchant == m),
                            )
                        }
                    }
                }

                HudSection("03", "分類")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Categories.all) { c ->
                        val accent = Color(c.color)
                        FilterChip(
                            selected = category == c.name,
                            onClick = {
                                category = c.name
                                categoryTouched = true
                            },
                            label = { Text(c.name) },
                            colors = hudChipColors(accent),
                            border = hudChipBorder(category == c.name),
                        )
                    }
                }

                HudSection("04", "付款方式")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(methods) { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m) },
                            colors = hudChipColors(),
                            border = hudChipBorder(method == m),
                        )
                    }
                }

                HudSection("05", "日期與備註")
                val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                HudButton(
                    text = "日期：$date" + if (date == LocalDate.now()) "（今天）" else "",
                    onClick = { datePicker = true },
                    accent = Hud.TextDim,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("備註（選填）") },
                    shape = RoundedCornerShape(8.dp),
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { save() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Bg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("儲存", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                Spacer(Modifier.height(28.dp))
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
                        // 保留原本的時分
                        time = d.atTime(current.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    datePicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
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
