package com.even.membercards.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.Barcodes
import com.even.membercards.data.CardRepository
import com.even.membercards.data.Shortcuts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repo: CardRepository,
    settings: AppSettings,
    id: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val card = repo.get(id) ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(id) { repo.markUsed(id) }
    if (settings.autoBrightness && card.number.isNotBlank()) KeepScreenBright()

    val accent = accentOf(card.color)
    val fmt = Barcodes.find(card.format)
    val barcode = remember(card.number, card.format) {
        runCatching { Barcodes.encode(card.number, card.format, 1000) }
    }
    val bmp = barcode.getOrNull()
    val hasApp = card.appPackage.isNotBlank() || card.link.isNotBlank()

    Box(
        Modifier
            .fillMaxSize()
            .hudBackground()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        IconButton(onClick = { repo.upsert(card.copy(favorite = !card.favorite)) }) {
                            Icon(
                                if (card.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (card.favorite) "取消常用" else "設為常用",
                                tint = if (card.favorite) Hud.Cyan else Hud.TextDim,
                            )
                        }
                        IconButton(onClick = {
                            if (!Shortcuts.requestPin(context, card)) context.toast("你的桌面不支援新增捷徑")
                        }) { Icon(Icons.Filled.Home, "加到主畫面") }
                        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "編輯") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "刪除") }
                    },
                    colors = hudTopBarColors(),
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 標頭
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CardGlyph(card, 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Hud.Text)
                        Text(
                            "${card.folder.ifBlank { "未分類" }}  //  USED ${card.useCount}×",
                            style = MonoLabel,
                            color = accent,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                if (card.number.isNotBlank()) {
                    // 條碼（白底才掃得到）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .cornerBrackets(Hud.Cyan, length = 16.dp, stroke = 2.dp)
                            .padding(10.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .clickable(enabled = bmp != null) { fullscreen = true }
                                .padding(horizontal = 14.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            when {
                                barcode.isFailure -> Text(
                                    "號碼不符合 ${fmt.label} 格式，請編輯並改用其他格式（例如 Code 128）。",
                                    color = Color(0xFFB00020),
                                    textAlign = TextAlign.Center,
                                )

                                bmp != null -> Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "條碼",
                                    contentScale = ContentScale.Fit,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = if (fmt.is2D && fmt.key != "PDF_417") 280.dp else 150.dp),
                                )

                                else -> Text("（此卡設定為只顯示號碼）", color = Color.Gray)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        prettyNumber(card.number),
                        fontFamily = Mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                        letterSpacing = 2.sp,
                        color = Hud.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(card.number))
                            context.toast("已複製號碼")
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (bmp != null) "點條碼全螢幕放大 · 點號碼複製" else "點號碼複製",
                        style = MonoLabel,
                        color = Hud.TextFaint,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .border(1.dp, Hud.Line, RoundedCornerShape(10.dp))
                            .cornerBrackets(accent, length = 12.dp, stroke = 1.5.dp, inset = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("NO BARCODE STORED", style = MonoLabel.copy(letterSpacing = 3.sp), color = accent)
                            Spacer(Modifier.height(6.dp))
                            Text("這張卡的條碼在店家 App 內", color = Hud.TextDim, fontSize = 13.sp)
                        }
                    }
                }

                if (hasApp) {
                    Spacer(Modifier.height(18.dp))
                    HudButton(
                        text = "▶  開啟 ${card.appLabel.ifBlank { "店家 App" }}",
                        onClick = {
                            repo.markUsed(card.id)
                            if (!StoreApps.open(context, card)) context.toast("尚未設定店家 App")
                        },
                        accent = accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    )
                }

                Spacer(Modifier.height(22.dp))
                HudPanel {
                    if (card.number.isNotBlank()) {
                        HudInfoRow("FORMAT", fmt.label)
                    }
                    val days = daysLeft(card)
                    if (days != null) {
                        val (text, color) = when {
                            days < 0 -> "${expiryDate(card.expiry)}（已過期 ${-days} 天）" to Hud.Danger
                            days <= 30 -> "${expiryDate(card.expiry)}（剩 $days 天）" to Hud.Warn
                            else -> "${expiryDate(card.expiry)}（剩 $days 天）" to Hud.Text
                        }
                        HudInfoRow("EXPIRES", text, color)
                    }
                    if (card.appLabel.isNotBlank()) HudInfoRow("APP", card.appLabel)
                    HudInfoRow("USAGE", "${card.useCount} 次")
                    HudInfoRow("LAST USED", timeAgo(card.lastUsed))
                    HudInfoRow("ADDED", expiryDate(card.createdAt).toString())
                    if (card.note.isNotBlank()) {
                        HudDivider()
                        Text("NOTE", style = MonoLabel, color = Hud.TextDim, modifier = Modifier.padding(top = 10.dp))
                        Text(
                            card.note,
                            color = Hud.Text,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        if (fullscreen && bmp != null) {
            FullscreenBarcode(
                bitmap = bmp.asImageBitmap(),
                number = card.number,
                rotate = !(fmt.is2D && fmt.key != "PDF_417"),
                onClose = { fullscreen = false },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除會員卡？") },
            text = { Text("確定要刪除「${card.name}」嗎？此動作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onBack()
                    repo.delete(id)
                }) { Text("刪除", color = Hud.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

/** 純數字且較長時每 4 碼加空白，方便店員手動輸入。 */
private fun prettyNumber(n: String): String =
    if (n.length > 8 && n.all { it.isDigit() }) n.chunked(4).joinToString(" ") else n
