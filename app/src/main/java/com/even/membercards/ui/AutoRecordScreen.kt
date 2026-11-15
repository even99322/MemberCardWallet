package com.even.membercards.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CapturedNotice
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.PayParser
import com.even.membercards.data.formatMoney
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

object AutoRecord {
    fun isPermissionGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.onFailure { context.toast("請到「設定 → 通知 → 通知存取權」開啟會員卡夾") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRecordScreen(
    ledger: LedgerRepository,
    settings: AppSettings,
    onBack: () -> Unit,
    onRecordFrom: (amount: Double?, merchant: String, method: String) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AutoRecord.isPermissionGranted(context)) }
    var testText by remember { mutableStateOf("") }

    // 從系統設定回來時重新檢查權限
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) granted = AutoRecord.isPermissionGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
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
                            Text("自動記帳", fontWeight = FontWeight.SemiBold)
                            Text("AUTO RECORD // NOTIFICATION", style = MonoLabel, color = Hud.Cyan)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    colors = hudTopBarColors(),
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "how") {
                    Column {
                        HudSection("01", "運作方式")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "用 LINE Pay 付款後，LINE 會收到「LINE Pay」官方帳號的付款通知。會員卡夾讀取這則通知，自動記下金額與商家，並依商家猜分類。\n\n" +
                                "需要：① 開啟下方「通知存取權」② LINE 的通知要顯示訊息內容（LINE → 設定 → 通知 → 開啟「顯示訊息內容」）。\n\n" +
                                "通知內容只在手機上處理，不會上傳。",
                            color = Hud.TextDim,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }

                item(key = "status") {
                    Column {
                        HudSection("02", "狀態")
                        Spacer(Modifier.height(8.dp))
                        HudPanel {
                            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(if (granted) Hud.Ok else Hud.Warn, RoundedCornerShape(5.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (granted) "通知存取權：已開啟" else "通知存取權：尚未開啟",
                                        color = if (granted) Hud.Ok else Hud.Warn,
                                        fontSize = 15.sp,
                                    )
                                    Text(
                                        if (granted) "可隨時到系統設定關閉" else "在清單中找到「會員卡夾」並開啟",
                                        color = Hud.TextDim,
                                        fontSize = 12.sp,
                                    )
                                }
                                TextButton(onClick = { AutoRecord.openPermissionSettings(context) }) {
                                    Text(if (granted) "管理" else "開啟", color = Hud.Cyan)
                                }
                            }
                            HudDivider()
                            ToggleRow(
                                "LINE Pay 自動記帳",
                                "收到 LINE Pay 付款通知時自動新增一筆",
                                settings.autoRecord,
                            ) { settings.autoRecord = it }
                            HudDivider()
                            ToggleRow(
                                "也記錄其他付款 App",
                                if (settings.payApps.isEmpty()) "快捷列尚未設定付款 App"
                                else "讀取快捷列付款 App 的通知：" + settings.payApps.joinToString("、") { it.label },
                                settings.captureOtherPay,
                            ) { settings.captureOtherPay = it }
                        }
                    }
                }

                item(key = "test") {
                    Column {
                        HudSection("03", "測試解析")
                        Spacer(Modifier.height(8.dp))
                        Text("貼上一則付款通知的文字，看看能不能正確抓到金額與商家。", color = Hud.TextDim, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            placeholder = { Text("例：付款完成 商店名稱：全家便利商店 付款金額 NT$85", color = Hud.TextFaint) },
                            minLines = 2,
                            shape = RoundedCornerShape(8.dp),
                            colors = hudFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (testText.isNotBlank()) {
                            val r = PayParser.parse("LINE Pay", testText)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (r != null) "✓ 金額 ${formatMoney(r.amount)}　商家 ${r.merchant.ifBlank { "（未抓到）" }}"
                                else "✗ 無法辨識為付款通知（需包含「付款／支付／消費」等字與金額）",
                                color = if (r != null) Hud.Ok else Hud.Warn,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                item(key = "logHeader") {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HudSection("04", "通知擷取紀錄", Modifier.weight(1f))
                            if (ledger.captures.isNotEmpty()) {
                                TextButton(onClick = { ledger.clearCaptures() }) { Text("清除", color = Hud.TextDim) }
                            }
                        }
                        Text(
                            "最近收到的付款相關通知。沒自動記到的，點一下可手動記一筆。",
                            color = Hud.TextDim,
                            fontSize = 12.sp,
                        )
                    }
                }

                if (ledger.captures.isEmpty()) {
                    item(key = "noLog") {
                        Text(
                            "NO DATA // 還沒有收到付款通知",
                            style = MonoLabel,
                            color = Hud.TextFaint,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(ledger.captures, key = { it.time.toString() + it.text.hashCode() }) { c ->
                        CaptureRow(c) {
                            onRecordFrom(
                                PayParser.guessAmount(c.text),
                                PayParser.guessMerchant(c.text),
                                c.app,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Hud.Text, fontSize = 15.sp)
            Text(desc, color = Hud.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
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

@Composable
private fun CaptureRow(c: CapturedNotice, onRecord: () -> Unit) {
    val t = Instant.ofEpochMilli(c.time).atZone(ZoneId.systemDefault())
    val ok = c.recordedAmount != null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Hud.Surface.copy(alpha = 0.9f))
            .border(1.dp, if (ok) Hud.Ok.copy(alpha = 0.35f) else Hud.Line, RoundedCornerShape(8.dp))
            .clickable(enabled = !ok, onClick = onRecord)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format(Locale.US, "%d/%d %02d:%02d", t.monthValue, t.dayOfMonth, t.hour, t.minute) + "  ·  " + c.app,
                style = MonoLabel,
                color = Hud.TextDim,
                modifier = Modifier.weight(1f),
            )
            if (ok) HudTag("已記帳 ${formatMoney(c.recordedAmount ?: 0.0)}", Hud.Ok)
            else HudTag("未辨識 · 點此記一筆", Hud.Warn)
        }
        Spacer(Modifier.height(4.dp))
        Text(c.title, color = Hud.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(c.text, color = Hud.TextDim, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
