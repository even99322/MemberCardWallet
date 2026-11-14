package com.even.membercards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(cardId) { cardId?.let { repo.get(it) } }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var number by rememberSaveable { mutableStateOf(existing?.number ?: "") }
    var format by rememberSaveable { mutableStateOf(existing?.format ?: "CODE_128") }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: Palette.colors[0]) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var formatMenu by remember { mutableStateOf(false) }
    var decoding by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            number = contents
            format = Barcodes.fromScanned(result.formatName)
            context.toast("已掃描：${Barcodes.find(format).label}")
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
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
            trimmed.isEmpty() -> context.toast("請輸入或掃描會員號碼")
            error != null -> context.toast(error)
            else -> {
                val base = existing ?: MemberCard(name = name.trim(), number = trimmed)
                val card = base.copy(
                    name = name.trim(),
                    number = trimmed,
                    format = format,
                    color = color,
                    note = note.trim(),
                )
                repo.upsert(card)
                onSaved(card.id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新增會員卡" else "編輯會員卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { save() }) { Icon(Icons.Filled.Check, "儲存") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 取得號碼
            Text("會員條碼", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setPrompt("將條碼對準畫面中央")
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("相機掃描") }
                FilledTonalButton(
                    onClick = {
                        imageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !decoding,
                    modifier = Modifier.weight(1f),
                ) {
                    if (decoding) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("從截圖辨識")
                }
            }

            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("會員號碼") },
                singleLine = true,
                isError = error != null,
                supportingText = { if (error != null) Text(error) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )

            Box {
                OutlinedButton(
                    onClick = { formatMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("條碼格式：${Barcodes.find(format).label}", modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
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
            }

            if (preview != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
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
                            .heightIn(max = 140.dp),
                    )
                }
            }

            // 2. 店家資訊
            Spacer(Modifier.height(4.dp))
            Text("店家", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("店家名稱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (existing == null) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { p ->
                        SuggestionChip(
                            onClick = {
                                name = p.name
                                color = p.color
                                if (number.isBlank() || p.format == "CODE_39") format = p.format
                            },
                            label = { Text(p.name) },
                        )
                    }
                }
            }

            Text("卡片顏色", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Palette.colors) { c ->
                    val selected = c == color
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color(c), CircleShape)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                shape = CircleShape,
                            )
                            .clickable { color = c },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, null, tint = onColor(Color(c)))
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("備註（選填，例如密碼提示、會員等級）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) { Text("儲存") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
