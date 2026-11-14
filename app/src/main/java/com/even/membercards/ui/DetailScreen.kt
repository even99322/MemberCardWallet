package com.even.membercards.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.Barcodes
import com.even.membercards.data.CardRepository
import com.even.membercards.data.Shortcuts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(repo: CardRepository, id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val card = repo.get(id) ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) { repo.markUsed(id) }
    KeepScreenBright()

    val bg = Color(card.color)
    val fg = onColor(bg)
    val fmt = Barcodes.find(card.format)
    val barcode = remember(card.number, card.format) {
        runCatching { Barcodes.encode(card.number, card.format, 1000) }
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text(card.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { repo.upsert(card.copy(favorite = !card.favorite)) }) {
                        Icon(
                            if (card.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (card.favorite) "取消常用" else "設為常用",
                        )
                    }
                    IconButton(onClick = {
                        if (!Shortcuts.requestPin(context, card)) context.toast("你的桌面不支援新增捷徑")
                    }) { Icon(Icons.Filled.Home, "加到主畫面") }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "編輯") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "刪除") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bg,
                    titleContentColor = fg,
                    navigationIconContentColor = fg,
                    actionIconContentColor = fg,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hasApp = card.appPackage.isNotBlank() || card.link.isNotBlank()
            if (card.number.isNotBlank()) Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val bmp = barcode.getOrNull()
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
                            .heightIn(max = if (fmt.is2D && fmt.key != "PDF_417") 300.dp else 160.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    prettyNumber(card.number),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(card.number))
                        context.toast("已複製號碼")
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text("點號碼可複製 · ${fmt.label}", fontSize = 12.sp, color = Color.Gray)
            } else {
                Text(
                    "這張卡沒有存條碼，請開啟店家 App 出示。",
                    color = fg,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }

            if (hasApp) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (!StoreApps.open(context, card)) context.toast("尚未設定店家 App")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1B1B1F),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        "開啟 ${card.appLabel.ifBlank { "店家 App" }}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }

            if (card.note.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    card.note,
                    color = fg,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                }) { Text("刪除") }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 顯示條碼時把螢幕調到最亮並保持常亮，離開時還原。 */
@Composable
private fun KeepScreenBright() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val lp = window.attributes
        val old = lp.screenBrightness
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            val l = window.attributes
            l.screenBrightness = old
            window.attributes = l
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
