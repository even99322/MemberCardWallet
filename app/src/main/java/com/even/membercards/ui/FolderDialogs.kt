package com.even.membercards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.even.membercards.data.CardRepository

/** 輸入資料夾名稱（新增或重新命名）。 */
@Composable
fun FolderNameDialog(
    title: String,
    initial: String = "",
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = { Text("資料夾名稱") },
                placeholder = { Text("例如：超商、藥妝、餐廳") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) error = "請輸入名稱"
                else if (!onConfirm(text.trim())) error = "已有同名資料夾"
            }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 選擇要移動到哪個資料夾。 */
@Composable
fun MoveToFolderDialog(
    repo: CardRepository,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val options = listOf("") + repo.folders
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移動到資料夾") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                itemsIndexed(options) { _, f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(f) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = f == current, onClick = { onPick(f) })
                        Text(f.ifBlank { "未分類" })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("新增資料夾") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (creating) {
        FolderNameDialog(
            title = "新增資料夾",
            onConfirm = { name ->
                val ok = repo.addFolder(name)
                if (ok) {
                    creating = false
                    onPick(name)
                }
                ok
            },
            onDismiss = { creating = false },
        )
    }
}

/** 管理資料夾：新增、重新命名、排序、刪除。 */
@Composable
fun ManageFoldersDialog(repo: CardRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理資料夾") },
        text = {
            if (repo.folders.isEmpty()) {
                Text("還沒有資料夾。可以建立「超商」「藥妝」「餐廳」等分類，再把卡片放進去。")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(repo.folders.toList(), key = { _, f -> f }) { i, f ->
                        val count = repo.cards.count { it.folder == f }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "$count 張",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { repo.moveFolder(f, -1) }, enabled = i > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, "上移")
                            }
                            IconButton(onClick = { repo.moveFolder(f, 1) }, enabled = i < repo.folders.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, "下移")
                            }
                            IconButton(onClick = { renaming = f }) { Icon(Icons.Filled.Edit, "重新命名") }
                            IconButton(onClick = { deleting = f }) { Icon(Icons.Filled.Delete, "刪除") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = { TextButton(onClick = { adding = true }) { Text("新增資料夾") } },
    )

    if (adding) {
        FolderNameDialog(
            title = "新增資料夾",
            onConfirm = { repo.addFolder(it).also { ok -> if (ok) adding = false } },
            onDismiss = { adding = false },
        )
    }
    renaming?.let { old ->
        FolderNameDialog(
            title = "重新命名",
            initial = old,
            onConfirm = { repo.renameFolder(old, it).also { ok -> if (ok) renaming = null } },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("刪除資料夾「$f」？") },
            text = { Text("資料夾裡的卡片不會被刪除，會移到「未分類」。") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteFolder(f)
                    deleting = null
                    context.toast("已刪除資料夾")
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}
