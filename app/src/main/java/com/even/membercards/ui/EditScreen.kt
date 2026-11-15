package com.even.membercards.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.Barcodes
import com.even.membercards.data.CardRepository
import com.even.membercards.data.MemberCard
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    repo: CardRepository,
    cardId: String?,
    sharedImage: Uri? = null,
    sharedText: String? = null,
    sharedSubject: String? = null,
    defaultFolder: String = "",
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(cardId) { cardId?.let { repo.get(it) } }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: sharedSubject?.take(30) ?: "") }
    var number by rememberSaveable { mutableStateOf(existing?.number ?: "") }
    var format by rememberSaveable { mutableStateOf(existing?.format ?: "CODE_128") }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: Palette.colors[0]) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var appPackage by rememberSaveable { mutableStateOf(existing?.appPackage ?: "") }
    var appLabel by rememberSaveable { mutableStateOf(existing?.appLabel ?: "") }
    var link by rememberSaveable { mutableStateOf(existing?.link ?: "") }
    var folder by rememberSaveable { mutableStateOf(existing?.folder ?: defaultFolder) }
    var expiry by rememberSaveable { mutableStateOf(existing?.expiry ?: 0L) }
    var showAppPicker by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var creatingFolder by remember { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var decoding by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            number = contents
            format = Barcodes.fromScanned(result.formatName)
            context.toast("已掃描：${Barcodes.find(format).label}")
        }
    }

    fun decodeImage(uri: Uri) {
        decoding = true
        scope.launch {
            val res = withContext(Dispatchers.Default) {
                runCatching { Barcodes.decodeFromUri(context, uri) }.getOrNull()
            }
            decoding = false
            if (res != null) {
                number = res.first
                format = Barcodes.fromScanned(res.second)
                context.toast("已辨識：${Barcodes.find(format).label}")
            } else {
                context.toast("圖片中找不到條碼，可試著裁切到只剩條碼再選一次")
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) decodeImage(uri)
    }

    // 其他 App 分享進來的截圖或文字
    LaunchedEffect(Unit) {
        if (existing != null) return@LaunchedEffect
        if (sharedImage != null) decodeImage(sharedImage)
        if (sharedText != null) {
            val t = sharedText.trim()
            if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+$").matches(t)) {
                link = t
                context.toast("已帶入開啟連結")
            } else {
                number = extractCode(t)
            }
        }
    }

    val trimmed = number.trim()
    val error = remember(trimmed, format) {
        if (trimmed.isEmpty()) null else Barcodes.validate(trimmed, format)
    }
    val preview = remember(trimmed, format) {
        if (trimmed.isEmpty() || error != null) null
        else runCatching { Barcodes.encode(trimmed, format, 800) }.getOrNull()
    }

    fun save() {
        when {
            name.isBlank() -> context.toast("請輸入店家名稱")
            trimmed.isEmpty() && appPackage.isBlank() && link.isBlank() ->
                context.toast("請輸入會員號碼，或連結店家 App")
            error != null -> context.toast(error)
            else -> {
                val base = existing ?: MemberCard(name = name.trim(), number = trimmed)
                val card = base.copy(
                    name = name.trim(),
                    number = trimmed,
                    format = format,
                    color = color,
                    note = note.trim(),
                    appPackage = appPackage,
                    appLabel = appLabel,
                    link = link.trim(),
                    folder = folder,
                    expiry = expiry,
                )
                repo.upsert(card)
                onSaved(card.id)
            }
        }
    }

    val fieldShape = RoundedCornerShape(8.dp)

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
                            Text(if (existing == null) "新增會員卡" else "編輯會員卡", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (existing == null) "NEW CARD // INPUT" else "EDIT // ${existing.id.take(8).uppercase()}",
                                style = MonoLabel,
                                color = Hud.Cyan,
                            )
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
                // 01 條碼
                HudSection("01", "會員條碼")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudButton(
                        text = "◎  相機掃描",
                        onClick = {
                            scanLauncher.launch(
                                ScanOptions().apply {
                                    setPrompt("將條碼對準畫面中央")
                                    setBeepEnabled(false)
                                    setOrientationLocked(false)
                                }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    )
                    HudButton(
                        text = "▣  截圖辨識",
                        onClick = {
                            imageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !decoding,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        if (decoding) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Hud.Cyan)
                        } else {
                            Text("▣  截圖辨識", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("會員號碼") },
                    placeholder = { Text("綁定 App 的卡可留空", color = Hud.TextFaint) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { if (error != null) Text(error) },
                    textStyle = MonoLabel.copy(fontSize = 16.sp, letterSpacing = 1.sp, color = Hud.Text),
                    shape = fieldShape,
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                DropdownField(
                    label = "條碼格式",
                    value = Barcodes.find(format).label,
                    expanded = formatMenu,
                    onExpand = { formatMenu = it },
                ) {
                    Barcodes.formats.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label) },
                            onClick = {
                                format = f.key
                                formatMenu = false
                            },
                        )
                    }
                }

                if (preview != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .cornerBrackets(Hud.CyanDim, length = 10.dp, stroke = 1.5.dp)
                            .padding(6.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "條碼預覽",
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp),
                            )
                        }
                    }
                }

                // 02 店家
                HudSection("02", "店家")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("店家名稱") },
                    singleLine = true,
                    shape = fieldShape,
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing == null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { p ->
                            FilterChip(
                                selected = name == p.name,
                                onClick = {
                                    name = p.name
                                    color = p.color
                                    if (number.isBlank() || p.format == "CODE_39") format = p.format
                                },
                                label = { Text(p.name) },
                                colors = hudChipColors(),
                                border = hudChipBorder(name == p.name),
                            )
                        }
                    }
                }
                DropdownField(
                    label = "資料夾",
                    value = folder.ifBlank { "未分類" },
                    expanded = folderMenu,
                    onExpand = { folderMenu = it },
                ) {
                    (listOf("") + repo.folders).forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.ifBlank { "未分類" }) },
                            onClick = {
                                folder = f
                                folderMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("＋ 新增資料夾", color = Hud.Cyan) },
                        onClick = {
                            folderMenu = false
                            creatingFolder = true
                        },
                    )
                }

                // 03 外觀
                HudSection("03", "卡片顏色")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Palette.colors) { c ->
                        val selected = c == color
                        Box(
                            Modifier
                                .size(38.dp)
                                .then(
                                    if (selected) Modifier.cornerBrackets(Hud.Cyan, length = 8.dp, stroke = 2.dp)
                                    else Modifier
                                )
                                .padding(5.dp)
                                .background(Color(c), RoundedCornerShape(5.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                                .clickable { color = c },
                        )
                    }
                }

                // 04 店家 App
                HudSection("04", "店家 App")
                Text(
                    "連結後可一鍵開啟。像 OPENPOINT、PX Pay 這類會變動或禁止截圖的條碼，可以只連結 App、號碼留空。",
                    color = Hud.TextDim,
                    fontSize = 12.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudButton(
                        text = if (appPackage.isBlank()) "選擇店家 App" else "已連結：$appLabel",
                        onClick = { showAppPicker = true },
                        accent = if (appPackage.isBlank()) Hud.TextDim else Hud.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    if (appPackage.isNotBlank()) {
                        IconButton(onClick = {
                            appPackage = ""
                            appLabel = ""
                        }) { Icon(Icons.Filled.Close, "取消連結", tint = Hud.TextDim) }
                    }
                }
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("自訂開啟連結（進階，選填）") },
                    placeholder = { Text("網址或 App 深層連結", color = Hud.TextFaint) },
                    singleLine = true,
                    shape = fieldShape,
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                // 05 其他
                HudSection("05", "其他")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DropdownField(
                        label = "有效期限",
                        value = if (expiry > 0) expiryDate(expiry).toString() else "未設定",
                        expanded = false,
                        onExpand = { if (it) datePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {}
                    if (expiry > 0) {
                        IconButton(onClick = { expiry = 0L }) { Icon(Icons.Filled.Close, "清除日期", tint = Hud.TextDim) }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("備註（選填，例如會員等級、密碼提示）") },
                    minLines = 2,
                    shape = fieldShape,
                    colors = hudFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Button(
                    onClick = { save() },
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Hud.Cyan,
                        contentColor = Hud.Bg,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("儲存", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (datePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = if (expiry > 0) expiry else null,
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { expiry = it }
                    datePicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (creatingFolder) {
        FolderNameDialog(
            title = "新增資料夾",
            onConfirm = { n ->
                repo.addFolder(n).also { ok ->
                    if (ok) {
                        folder = n
                        creatingFolder = false
                    }
                }
            },
            onDismiss = { creatingFolder = false },
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            storeName = name,
            onPick = {
                appPackage = it.pkg
                appLabel = it.label
                if (name.isBlank()) name = it.label
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

/** 下拉選單樣式的欄位（小標 + 數值 + ▼）。 */
@Composable
private fun DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    items: @Composable () -> Unit,
) {
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Hud.Surface, RoundedCornerShape(8.dp))
                .border(1.dp, if (expanded) Hud.Cyan else Hud.Line, RoundedCornerShape(8.dp))
                .clickable { onExpand(true) }
                .padding(PaddingValues(horizontal = 14.dp, vertical = 10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MonoLabel, color = Hud.TextDim)
                Text(value, color = Hud.Text, fontSize = 15.sp)
            }
            Icon(Icons.Filled.ArrowDropDown, null, tint = Hud.TextDim)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpand(false) }) { items() }
    }
}

/** 從分享的文字中找出最像會員號碼的字串。 */
private fun extractCode(text: String): String {
    if (text.length <= 48 && !text.any { it.isWhitespace() }) return text
    val tokens = Regex("[A-Za-z0-9/+.\\-]{6,}").findAll(text)
        .map { it.value }
        .filterNot { it.startsWith("http", ignoreCase = true) }
        .toList()
    return tokens.maxByOrNull { t -> t.count { it.isDigit() } } ?: text.take(64)
}
