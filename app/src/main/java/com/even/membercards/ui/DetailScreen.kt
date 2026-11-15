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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import com.even.membercards.data.Barcodes
import com.even.membercards.data.CardPhotos
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
    var viewPhoto by remember { mutableStateOf<String?>(null) }
    var carrierFull by remember { mutableStateOf(false) }
    // 上方載具條碼（票券不顯示）
    val carrier = if (settings.carrierOnCard && !card.isCoupon) effectiveCarrier(settings, repo) else ""
    val carrierBmp = remember(carrier) {
        if (carrier.isBlank()) null else runCatching { Barcodes.encode(carrier, "CODE_39", 1000) }.getOrNull()
    }
    var editPoints by remember { mutableStateOf(false) }
    val front = rememberCardPhoto(card.id, CardPhotos.FRONT, 0, 600)
    val back = rememberCardPhoto(card.id, CardPhotos.BACK, 0, 600)

    LaunchedEffect(id) { repo.markUsed(id) }
    if (settings.autoBrightness && (card.number.isNotBlank() || carrierBmp != null)) KeepScreenBright()

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
                        IconButton(onClick = { CardShare.share(context, card) }) { Icon(Icons.Filled.Share, "分享") }
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
                // 載具條碼（在會員條碼上方，店員可一次掃完）
                if (carrierBmp != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Hud.Surface.copy(alpha = 0.9f))
                            .border(1.dp, Hud.Violet.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { carrierFull = true }
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CARRIER // 載具", style = MonoLabel, color = Hud.Violet, modifier = Modifier.weight(1f))
                            Text(carrier, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.sp, color = Hud.Text)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Image(
                                bitmap = carrierBmp.asImageBitmap(),
                                contentDescription = "載具條碼",
                                contentScale = ContentScale.FillBounds,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

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

                // 票券：標記已使用
                if (card.isCoupon) {
                    Spacer(Modifier.height(16.dp))
                    HudButton(
                        text = if (card.used) "↺  還原為未使用" else "✓  標記為已使用",
                        onClick = {
                            val nowUsed = !card.used
                            repo.upsert(card.copy(used = nowUsed, usedAt = if (nowUsed) System.currentTimeMillis() else 0L))
                            context.toast(if (nowUsed) "已標記為已使用" else "已還原")
                            if (nowUsed) onBack()
                        },
                        accent = if (card.used) Hud.TextDim else Hud.Ok,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    )
                    if (card.used && card.usedAt > 0) {
                        Text("使用於 ${timeAgo(card.usedAt)}", style = MonoLabel, color = Hud.TextDim, modifier = Modifier.padding(top = 6.dp))
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

                // 實體卡照片
                if (front != null || back != null) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(CardPhotos.FRONT to front, CardPhotos.BACK to back).forEach { (side, img) ->
                            if (img != null) {
                                Image(
                                    img,
                                    if (side == CardPhotos.FRONT) "正面" else "背面",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.586f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Hud.LineBright, RoundedCornerShape(8.dp))
                                        .clickable { viewPhoto = side },
                                )
                            }
                        }
                    }
                }

                // 點數／餘額
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Hud.Violet.copy(alpha = 0.08f))
                        .border(1.dp, Hud.Violet.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable { editPoints = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("POINTS // 點數／餘額", style = MonoLabel, color = Hud.Violet)
                        Text(
                            card.points.ifBlank { "點此記錄點數或儲值餘額" },
                            fontFamily = if (card.points.isBlank()) null else Mono,
                            fontWeight = if (card.points.isBlank()) FontWeight.Normal else FontWeight.Bold,
                            fontSize = if (card.points.isBlank()) 13.sp else 20.sp,
                            color = if (card.points.isBlank()) Hud.TextDim else Hud.Text,
                        )
                    }
                    if (card.pointsUpdated > 0) {
                        Text("${timeAgo(card.pointsUpdated)}更新", fontSize = 11.sp, color = Hud.TextDim)
                    }
                }

                Spacer(Modifier.height(18.dp))
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
        viewPhoto?.let { side -> FullscreenPhoto(card.id, side, onClose = { viewPhoto = null }) }
        if (carrierFull && carrierBmp != null) {
            FullscreenBarcode(
                bitmap = carrierBmp.asImageBitmap(),
                number = carrier,
                rotate = true,
                title = "手機條碼載具",
                onClose = { carrierFull = false },
            )
        }
    }

    if (editPoints) {
        var text by remember { mutableStateOf(card.points) }
        AlertDialog(
            onDismissRequest = { editPoints = false },
            title = { Text("點數／餘額") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("例如 1,250 點、$300") },
                    singleLine = true,
                    colors = hudFieldColors(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.upsert(card.copy(points = text.trim(), pointsUpdated = System.currentTimeMillis()))
                    editPoints = false
                }) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { editPoints = false }) { Text("取消") } },
        )
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
