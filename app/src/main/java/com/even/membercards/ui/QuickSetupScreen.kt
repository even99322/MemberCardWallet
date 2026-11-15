package com.even.membercards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CardRepository
import com.even.membercards.data.PhoneEntry
import com.even.membercards.data.QuickApp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSetupScreen(
    repo: CardRepository,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val detected = remember { effectiveCarrier(settings, repo) }
    var carrier by rememberSaveable { mutableStateOf(settings.carrier.ifBlank { detected }) }
    val phones = remember {
        mutableStateListOf<PhoneEntry>().apply {
            addAll(settings.phones)
            if (isEmpty()) add(PhoneEntry("本人", ""))
        }
    }
    val apps = remember { mutableStateListOf<QuickApp>().apply { addAll(settings.payApps) } }
    var picking by remember { mutableStateOf(false) }

    val carrierTrim = carrier.trim().uppercase()
    val carrierError = carrierTrim.isNotEmpty() && !AppSettings.CARRIER_REGEX.matches(carrierTrim)

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let {
            carrier = it.trim().uppercase()
            context.toast("已掃描載具")
        }
    }

    fun save() {
        if (carrierError) {
            context.toast("載具格式不正確：應為 / 開頭共 8 碼，例如 /ABC1234")
            return
        }
        settings.carrier = carrierTrim
        settings.phones = phones.map { p ->
            PhoneEntry(p.label.trim(), p.number.filter { it.isDigit() || it == '+' })
        }
        settings.payApps = apps.toList()
        context.toast("快捷列已更新")
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
                            Text("快捷列設定", fontWeight = FontWeight.SemiBold)
                            Text("QUICK ACCESS // CONFIG", style = MonoLabel, color = Hud.Cyan)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
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
                HudSection("01", "手機條碼載具")
                Text(
                    "結帳時存載具用。格式為 / 開頭共 8 碼（例如 /ABC1234），可在財政部電子發票 App 或載具卡上找到。",
                    color = Hud.TextDim,
                    fontSize = 12.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = carrier,
                        onValueChange = { carrier = it.uppercase() },
                        label = { Text("載具號碼") },
                        placeholder = { Text("/ABC1234", color = Hud.TextFaint) },
                        singleLine = true,
                        isError = carrierError,
                        supportingText = { if (carrierError) Text("應為 / 開頭共 8 碼") },
                        textStyle = MonoLabel.copy(fontSize = 17.sp, letterSpacing = 2.sp, color = Hud.Text),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = RoundedCornerShape(8.dp),
                        colors = hudFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    HudButton(
                        text = "掃描",
                        onClick = {
                            scanLauncher.launch(
                                ScanOptions().apply {
                                    setPrompt("掃描手機條碼載具")
                                    setBeepEnabled(false)
                                    setOrientationLocked(false)
                                    setDesiredBarcodeFormats(ScanOptions.CODE_39)
                                }
                            )
                        },
                        modifier = Modifier.height(56.dp),
                    )
                }
                if (settings.carrier.isBlank() && detected.isNotBlank()) {
                    Text("已從你的卡片自動帶入載具號碼", color = Hud.Ok, fontSize = 12.sp)
                }

                HudSection("02", "電話號碼")
                Text(
                    "很多店家用電話查會員。第一個是主要號碼（顯示在快捷列），其他是備選；全螢幕時可切換。",
                    color = Hud.TextDim,
                    fontSize = 12.sp,
                )
                phones.forEachIndexed { i, entry ->
                    HudPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (i == 0) "PRIMARY // 主要" else "BACKUP ${i.toString().padStart(2, '0')} // 備選",
                                style = MonoLabel,
                                color = if (i == 0) Hud.Cyan else Hud.TextDim,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { phones.add(i - 1, phones.removeAt(i)) },
                                enabled = i > 0,
                            ) { Icon(Icons.Filled.KeyboardArrowUp, "上移（設為主要請移到第一個）") }
                            IconButton(
                                onClick = { phones.add(i + 1, phones.removeAt(i)) },
                                enabled = i < phones.lastIndex,
                            ) { Icon(Icons.Filled.KeyboardArrowDown, "下移") }
                            IconButton(onClick = { phones.removeAt(i) }) {
                                Icon(Icons.Filled.Close, "刪除這組號碼", tint = Hud.TextDim)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = entry.label,
                                onValueChange = { phones[i] = entry.copy(label = it) },
                                label = { Text("標籤") },
                                placeholder = { Text("本人", color = Hud.TextFaint) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = hudFieldColors(),
                                modifier = Modifier.weight(0.38f),
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = entry.number,
                                onValueChange = { phones[i] = entry.copy(number = it) },
                                label = { Text("號碼") },
                                placeholder = { Text("0912345678", color = Hud.TextFaint) },
                                singleLine = true,
                                textStyle = MonoLabel.copy(fontSize = 16.sp, letterSpacing = 1.sp, color = Hud.Text),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(8.dp),
                                colors = hudFieldColors(),
                                modifier = Modifier.weight(0.62f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                HudButton(
                    text = if (phones.isEmpty()) "＋ 新增電話號碼" else "＋ 新增備選號碼",
                    onClick = { phones.add(PhoneEntry(if (phones.isEmpty()) "本人" else "", "")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )

                HudSection("03", "常用付款 App")
                Text("例如 LINE Pay、街口、全支付、悠遊付、Google 錢包，點一下就能開啟。", color = Hud.TextDim, fontSize = 12.sp)
                if (apps.isNotEmpty()) {
                    HudPanel {
                        apps.forEachIndexed { i, app ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                val icon = rememberAppIcon(app.pkg)
                                if (icon != null) {
                                    Image(icon, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                                } else {
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    color = Hud.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    if (i > 0) apps.add(i - 1, apps.removeAt(i))
                                }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "上移") }
                                IconButton(onClick = {
                                    if (i < apps.lastIndex) apps.add(i + 1, apps.removeAt(i))
                                }, enabled = i < apps.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "下移") }
                                IconButton(onClick = { apps.removeAt(i) }) {
                                    Icon(Icons.Filled.Close, "移除", tint = Hud.TextDim)
                                }
                            }
                        }
                    }
                }
                HudButton(
                    text = if (apps.isEmpty()) "＋ 選擇付款 App" else "＋ 新增／調整付款 App",
                    onClick = { picking = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { save() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.OnPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("儲存", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (picking) {
        PickAppsDialog(
            title = "選擇付款 App",
            hint = "付款、錢包類的 App 會排在前面；數字是顯示順序。",
            initial = apps.map { it.pkg },
            priority = { StoreApps.looksLikePayment(it) },
            onConfirm = { picked ->
                apps.clear()
                apps.addAll(picked.map { QuickApp(it.pkg, it.label) })
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
