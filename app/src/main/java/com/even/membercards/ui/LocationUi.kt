package com.even.membercards.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.even.membercards.data.AppSettings
import com.even.membercards.data.LocationTagger

/** 每次回到畫面時重新讀取權限狀態 */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun openMap(context: Context, lat: Double, lng: Double, label: String) {
    val uri = Uri.parse(LocationTagger.mapUri(lat, lng, label))
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            // 沒有地圖 App 就開網頁版
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
}

/** 「刷卡時記錄位置」開關＋權限引導 */
@Composable
fun RecordLocationRow(settings: AppSettings) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val fg = remember(tick) { LocationTagger.hasPermission(context) }
    val bg = remember(tick) { LocationTagger.hasBackground(context) }
    var askBackground by remember { mutableStateOf(false) }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) {
            context.toast("請在「權限 → 位置資訊」選「一律允許」")
            openAppSettings(context)
        }
    }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            settings.recordLocation = true
            if (!LocationTagger.hasBackground(context)) askBackground = true
        } else {
            context.toast("需要位置權限才能記錄消費地點")
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                if (settings.recordLocation) settings.recordLocation = false
                else if (fg) {
                    settings.recordLocation = true
                    if (!bg) askBackground = true
                } else {
                    fgLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("刷卡時記錄位置", color = Hud.Text, fontSize = 15.sp)
            Text(
                when {
                    !settings.recordLocation -> "每筆刷卡／付款自動記下消費地點，之後可在地圖上確認"
                    !fg -> "⚠ 位置權限被關閉，請重新開啟"
                    !bg -> "⚠ 目前只有「使用 App 時」允許，背景自動記帳時可能抓不到位置"
                    else -> "運作中 · 位置權限：一律允許"
                },
                color = if (settings.recordLocation && (!fg || !bg)) Hud.Warn else Hud.TextDim,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = settings.recordLocation && fg,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Hud.OnPrimary,
                checkedTrackColor = Hud.Cyan,
                uncheckedThumbColor = Hud.TextDim,
                uncheckedTrackColor = Hud.Surface2,
                uncheckedBorderColor = Hud.LineBright,
            ),
        )
    }
    if (settings.recordLocation && fg && !bg) {
        TextButton(onClick = { askBackground = true }) { Text("改為「一律允許」", color = Hud.Cyan) }
    }

    if (askBackground) {
        AlertDialog(
            onDismissRequest = { askBackground = false },
            containerColor = Hud.Surface,
            title = { Text("背景位置權限", color = Hud.Text) },
            text = {
                Text(
                    "刷卡通知通常是在你沒開會員卡夾的時候收到，要在背景取得位置，需要把位置權限設為「一律允許」。\n\n" +
                        "接下來的頁面請選「一律允許」（部分手機在「權限 → 位置資訊」裡）。\n\n" +
                        "位置只存在手機裡，不會上傳。",
                    color = Hud.TextDim,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askBackground = false
                    if (Build.VERSION.SDK_INT >= 29) {
                        runCatching { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                            .onFailure { openAppSettings(context) }
                    }
                }) { Text("前往設定") }
            },
            dismissButton = {
                TextButton(onClick = { askBackground = false }) { Text("先不要", color = Hud.TextDim) }
            },
        )
    }
}
