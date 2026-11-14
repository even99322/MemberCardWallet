package com.even.membercards.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.even.membercards.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun UpdateDialog(release: Updater.Release, onSkip: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { Updater.currentVersion(context) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    val downloading = progress != null && downloaded == null

    fun startDownload() {
        failed = null
        progress = 0f
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Updater.download(context, release.apkUrl) { p -> progress = p } }
            }
            result.onSuccess {
                downloaded = it
                Updater.install(context, it)
            }.onFailure {
                progress = null
                failed = "下載失敗，請確認網路後再試一次"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("有新版本 v${release.version}") },
        text = {
            Column {
                Text("目前版本 v$current", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(release.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val p = progress
                if (p != null) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (downloaded != null) "下載完成，若沒有出現安裝畫面請按「安裝」"
                        else "下載中… ${(p * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                failed?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val file = downloaded
            TextButton(
                enabled = !downloading,
                onClick = { if (file != null) Updater.install(context, file) else startDownload() },
            ) { Text(if (file != null) "安裝" else if (downloading) "下載中…" else "立即更新") }
        },
        dismissButton = {
            if (!downloading) {
                Row {
                    if (downloaded == null) TextButton(onClick = onSkip) { Text("略過此版") }
                    TextButton(onClick = onDismiss) { Text("稍後") }
                }
            }
        },
    )
}
