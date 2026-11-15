package com.even.membercards.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CardRepository
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.Updater
import com.even.membercards.ReminderWorker
import android.Manifest
import android.os.Build
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repo: CardRepository,
    settings: AppSettings,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onQuickSetup: () -> Unit,
    onOpenLedger: () -> Unit,
    onAutoRecord: () -> Unit,
) {
    val context = LocalContext.current
    var showFolders by remember { mutableStateOf(false) }
    val version = remember { Updater.currentVersion(context) }
    var notifyGranted by remember { mutableStateOf(ReminderWorker.canNotify(context)) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notifyGranted = ok
        if (ok) settings.reminders = true else context.toast("沒有通知權限，無法提醒")
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                // 卡片、資料夾之外，也備份快捷列（載具、手機、付款 App）
                val json = JSONObject(repo.exportJson())
                    .put("quick", settings.quickJson())
                    .put("ledger", LedgerRepository.instance(context).exportJson())
                    .put("ledgerMeta", LedgerRepository.instance(context).exportMeta())
                    .toString(2)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess { context.toast("已匯出 ${repo.cards.size} 張會員卡") }
                .onFailure { context.toast("匯出失敗：${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("無法讀取檔案")
                runCatching { JSONObject(text.trim()) }.getOrNull()?.let { o ->
                    o.optJSONObject("quick")?.let { settings.applyQuickJson(it) }
                    o.optJSONObject("ledgerMeta")?.let { LedgerRepository.instance(context).importMeta(it) }
                    o.optJSONArray("ledger")?.let { LedgerRepository.instance(context).importJson(it) }
                }
                repo.importJson(text)
            }.onSuccess { context.toast("已匯入 $it 張會員卡") }
                .onFailure { context.toast("匯入失敗：檔案格式不正確") }
        }
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
                            Text("設定", fontWeight = FontWeight.SemiBold)
                            Text("SYSTEM // CONFIG", style = MonoLabel, color = Hud.Cyan)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HudSection("01", "安全")
                HudPanel {
                    SwitchRow(
                        title = "App 鎖定",
                        desc = "開啟會員卡夾時需用指紋、臉部或螢幕鎖解鎖；離開超過 30 秒會重新鎖定",
                        checked = settings.lockEnabled,
                        onChange = { on ->
                            if (on && !AppLock.isDeviceSecure(context)) {
                                context.toast("請先在手機設定中設定螢幕鎖定（密碼／圖形／指紋）")
                            } else {
                                settings.lockEnabled = on
                            }
                        },
                    )
                }

                HudSection("01-B", "提醒")
                HudPanel {
                    SwitchRow(
                        title = "提醒通知",
                        desc = "會員卡／票券快到期、定期收支明天扣款、本月支出達預算 80% 時通知（每天早上 9 點檢查）",
                        checked = settings.reminders && notifyGranted,
                        onChange = { on ->
                            if (on && !ReminderWorker.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
                                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                settings.reminders = on
                                notifyGranted = ReminderWorker.canNotify(context)
                            }
                        },
                    )
                    HudDivider()
                    NavRow("立即檢查一次", "馬上檢查有沒有要提醒的事") {
                        val n = ReminderWorker.check(context)
                        context.toast(if (n > 0) "已送出 $n 則提醒" else "目前沒有需要提醒的事")
                    }
                }

                HudSection("02", "顯示")
                HudPanel {
                    Text("檢視方式", color = Hud.Text, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        listOf(AppSettings.VIEW_GRID to "卡片格狀", AppSettings.VIEW_LIST to "精簡清單").forEach { (k, label) ->
                            FilterChip(
                                selected = settings.viewMode == k,
                                onClick = { settings.viewMode = k },
                                label = { Text(label) },
                                colors = hudChipColors(),
                                border = hudChipBorder(settings.viewMode == k),
                            )
                        }
                    }
                    HudDivider()
                    Text("排序方式", color = Hud.Text, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                    Column(Modifier.padding(vertical = 6.dp)) {
                        AppSettings.sortLabels.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (k, label) ->
                                    FilterChip(
                                        selected = settings.sortMode == k,
                                        onClick = { settings.sortMode = k },
                                        label = { Text(label) },
                                        colors = hudChipColors(),
                                        border = hudChipBorder(settings.sortMode == k),
                                    )
                                }
                            }
                        }
                    }
                    HudDivider()
                    SwitchRow(
                        title = "顯示條碼時自動調亮",
                        desc = "打開條碼時螢幕調到最亮並保持常亮，方便店員掃描",
                        checked = settings.autoBrightness,
                        onChange = { settings.autoBrightness = it },
                    )
                }

                HudSection("03", "資料")
                HudPanel {
                    NavRow("快捷列", "手機條碼載具、電話號碼、常用付款 App") { onQuickSetup() }
                    HudDivider()
                    NavRow("記帳", "本月支出、預算、分類統計、匯出 CSV") { onOpenLedger() }
                    HudDivider()
                    NavRow(
                        "自動記帳",
                        if (settings.autoRecord && AutoRecord.isPermissionGranted(context)) "LINE Pay 付款通知自動記帳：運作中"
                        else "讀取 LINE Pay 付款通知自動記帳：未開啟",
                    ) { onAutoRecord() }
                    HudDivider()
                    NavRow("管理資料夾", "${repo.folders.size} 個資料夾") { showFolders = true }
                    HudDivider()
                    NavRow("匯出備份", "卡片、資料夾、快捷列、記帳都會備份成 JSON 檔") { exportLauncher.launch("member-cards-backup.json") }
                    HudDivider()
                    NavRow("匯入備份", "從 JSON 備份檔還原，同一張卡會被覆蓋") {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                }

                HudSection("04", "系統")
                HudPanel {
                    NavRow(
                        "自動點擊（無障礙權限）",
                        if (com.even.membercards.data.AutoTap.isEnabled(context)) "已開啟：開店家 App 時可自動點到條碼頁"
                        else "未開啟：開啟後可在卡片設定自動點擊步驟",
                    ) { openAccessibilitySettings(context) }
                    HudDivider()
                    NavRow("檢查更新", "目前版本 v$version") { onCheckUpdate() }
                    HudDivider()
                    NavRow("GitHub 專案頁", "github.com/even99322/MemberCardWallet") {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/even99322/MemberCardWallet"))
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "MEMBER WALLET  v$version  //  ${repo.cards.size} CARDS  //  DATA STORED LOCALLY",
                    style = MonoLabel,
                    color = Hud.TextFaint,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showFolders) ManageFoldersDialog(repo = repo, onDismiss = { showFolders = false })
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
                checkedThumbColor = Hud.Bg,
                checkedTrackColor = Hud.Cyan,
                uncheckedThumbColor = Hud.TextDim,
                uncheckedTrackColor = Hud.Surface2,
                uncheckedBorderColor = Hud.LineBright,
            ),
        )
    }
}

@Composable
private fun NavRow(title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Hud.Text, fontSize = 15.sp)
            Text(desc, color = Hud.TextDim, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hud.TextFaint)
    }
}
