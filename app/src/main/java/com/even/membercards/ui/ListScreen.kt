package com.even.membercards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.CardRepository
import com.even.membercards.data.MemberCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(repo: CardRepository, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson().toByteArray()) }
            }.onSuccess { context.toast("已匯出 ${repo.cards.size} 張會員卡") }
                .onFailure { context.toast("匯出失敗：${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("無法讀取檔案")
                repo.importJson(text)
            }.onSuccess { context.toast("已匯入 $it 張會員卡") }
                .onFailure { context.toast("匯入失敗：檔案格式不正確") }
        }
    }

    val shown = repo.cards
        .filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<MemberCard> { it.favorite }
                .thenByDescending { it.lastUsed }
                .thenByDescending { it.createdAt }
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("會員卡夾") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "選單") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("匯出備份") },
                                onClick = {
                                    menuOpen = false
                                    exportLauncher.launch("member-cards-backup.json")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("匯入備份") },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("新增會員卡") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (repo.cards.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜尋店家、備註或號碼") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "清除") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }

            if (repo.cards.isEmpty()) {
                EmptyState()
            } else if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("找不到符合的會員卡", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { card ->
                        CardTile(
                            card = card,
                            onClick = { onOpen(card.id) },
                            onLongClick = {
                                if (!StoreApps.open(context, card)) context.toast("長按可開啟店家 App，請先在編輯中連結")
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(card: MemberCard, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bg = Color(card.color)
    val fg = onColor(bg)
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    card.name.ifBlank { "未命名" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (card.favorite) {
                    Icon(Icons.Filled.Favorite, "常用", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (card.number.isBlank()) "App：${card.appLabel.ifBlank { "連結" }}" else maskNumber(card.number),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = fg.copy(alpha = 0.85f),
            )
        }
    }
}

private fun maskNumber(n: String): String =
    if (n.length <= 6) n else "•••• " + n.takeLast(4)

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("還沒有會員卡", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "點右下角「新增會員卡」\n可以用相機掃描實體卡，\n或直接選店家 App 的條碼截圖自動辨識。",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
