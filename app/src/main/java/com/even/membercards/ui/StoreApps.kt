package com.even.membercards.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.even.membercards.data.MemberCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val pkg: String, val label: String, val icon: ImageBitmap)

object StoreApps {
    /** 列出手機上所有可啟動的 App。 */
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val infos = pm.queryIntentActivities(intent, 0)
        return infos
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                InstalledApp(
                    pkg = ri.activityInfo.packageName,
                    label = ri.loadLabel(pm).toString(),
                    icon = ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** 開啟卡片連結的店家 App／連結；回傳 false 表示沒有設定。 */
    fun open(context: Context, card: MemberCard): Boolean {
        val link = card.link.trim()
        if (link.isNotEmpty()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                context.toast("無法開啟此連結，改開啟 App")
            }
        }
        if (card.appPackage.isNotBlank()) {
            val launch = context.packageManager.getLaunchIntentForPackage(card.appPackage)
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${card.appPackage}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                context.toast("手機上找不到 ${card.appLabel.ifBlank { "這個 App" }}，已開啟商店頁面")
                return true
            } catch (e: ActivityNotFoundException) {
                context.toast("手機上找不到 ${card.appLabel.ifBlank { "這個 App" }}")
            }
        }
        return false
    }
}

/** 選擇店家 App 的對話框；名稱與店家名稱相符的 App 會排在最前面。 */
@Composable
fun AppPickerDialog(storeName: String, onPick: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { runCatching { StoreApps.load(context) }.getOrDefault(emptyList()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("選擇店家 App") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜尋 App") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    val key = storeName.trim()
                    val shown = list
                        .filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
                        .sortedByDescending {
                            key.isNotEmpty() && it.label.isNotBlank() &&
                                (it.label.contains(key, true) || key.contains(it.label, true))
                        }
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(shown, key = { it.pkg }) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(app) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(app.icon, null, Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

/** 一次勾選多個會員 App，綁定成卡片。已綁定的 App 會標示出來。 */
@Composable
fun BindAppsDialog(
    boundPackages: Set<String>,
    onConfirm: (List<InstalledApp>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { runCatching { StoreApps.load(context) }.getOrDefault(emptyList()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("綁定會員 App") },
        text = {
            Column {
                Text(
                    "勾選你的會員 App，綁定後點卡片就會直接開啟該 App 出示條碼。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜尋 App") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    val shown = list.filter {
                        query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
                    }
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(shown, key = { it.pkg }) { app ->
                            val bound = app.pkg in boundPackages
                            val checked = app.pkg in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !bound) {
                                        if (checked) selected.remove(app.pkg) else selected.add(app.pkg)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked || bound,
                                    enabled = !bound,
                                    onCheckedChange = {
                                        if (checked) selected.remove(app.pkg) else selected.add(app.pkg)
                                    },
                                )
                                Image(app.icon, null, Modifier.size(32.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (bound) {
                                        Text(
                                            "已綁定",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    val all = apps.orEmpty()
                    onConfirm(selected.mapNotNull { pkg -> all.firstOrNull { it.pkg == pkg } })
                },
            ) { Text(if (selected.isEmpty()) "綁定" else "綁定 ${selected.size} 個") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
