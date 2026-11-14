package com.even.membercards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.Barcodes
import com.even.membercards.data.CardRepository
import com.even.membercards.data.MemberCard
import com.even.membercards.data.Updater

/** 篩選用的特殊值：未分類 */
private const val UNCATEGORIZED = "\u0001uncategorized"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    repo: CardRepository,
    onOpenDetail: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: (folder: String) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    // null = 全部；UNCATEGORIZED = 未分類；其他 = 資料夾名稱
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var showBindApps by remember { mutableStateOf(false) }
    var showManageFolders by remember { mutableStateOf(false) }
    var creatingFolder by remember { mutableStateOf(false) }
    var movingCard by remember { mutableStateOf<MemberCard?>(null) }
    var deletingCard by remember { mutableStateOf<MemberCard?>(null) }

    // 被刪除或改名的資料夾不能停留在選取狀態
    val sel = selectedFolder
    if (sel != null && sel != UNCATEGORIZED && sel !in repo.folders) selectedFolder = null
    val currentFolderName = selectedFolder?.takeIf { it != UNCATEGORIZED } ?: ""

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

    fun isUncategorized(c: MemberCard) = c.folder.isBlank() || c.folder !in repo.folders

    /** 點卡片：只綁定 App 的卡直接開 App；有條碼的卡顯示條碼。 */
    fun openCard(card: MemberCard) {
        if (card.number.isBlank() && (card.appPackage.isNotBlank() || card.link.isNotBlank())) {
            repo.markUsed(card.id)
            if (!StoreApps.open(context, card)) onOpenDetail(card.id)
        } else {
            onOpenDetail(card.id)
        }
    }

    val matched = repo.cards
        .filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true) ||
                it.appLabel.contains(query, ignoreCase = true) || it.folder.contains(query, ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<MemberCard> { it.favorite }
                .thenByDescending { it.lastUsed }
                .thenByDescending { it.createdAt }
        )
    val hasUncategorized = repo.folders.isNotEmpty() && repo.cards.any { isUncategorized(it) }

    val tile: @Composable (MemberCard) -> Unit = { card ->
        CardTile(
            card = card,
            onClick = { openCard(card) },
            onShowDetail = { onOpenDetail(card.id) },
            onEdit = { onEdit(card.id) },
            onOpenApp = {
                repo.markUsed(card.id)
                if (!StoreApps.open(context, card)) context.toast("這張卡還沒綁定 App")
            },
            onMove = { movingCard = card },
            onToggleFavorite = { repo.upsert(card.copy(favorite = !card.favorite)) },
            onDelete = { deletingCard = card },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("會員卡夾") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "選單") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("管理資料夾") },
                                onClick = {
                                    menuOpen = false
                                    showManageFolders = true
                                },
                            )
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
                            DropdownMenuItem(
                                text = { Text("檢查更新（目前 v${Updater.currentVersion(context)}）") },
                                onClick = {
                                    menuOpen = false
                                    onCheckUpdate()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { addMenuOpen = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("新增") },
                )
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("綁定會員 App") },
                        onClick = {
                            addMenuOpen = false
                            showBindApps = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("新增條碼卡（掃描／截圖／輸入）") },
                        onClick = {
                            addMenuOpen = false
                            onAdd(currentFolderName)
                        },
                    )
                }
            }
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜尋店家、資料夾、號碼") },
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

            // 資料夾列
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedFolder == null,
                        onClick = { selectedFolder = null },
                        label = { Text("全部 ${repo.cards.size}") },
                    )
                }
                items(repo.folders.toList(), key = { "f_$it" }) { f ->
                    FilterChip(
                        selected = selectedFolder == f,
                        onClick = { selectedFolder = if (selectedFolder == f) null else f },
                        label = { Text("$f ${repo.cards.count { it.folder == f }}") },
                    )
                }
                if (hasUncategorized) {
                    item(key = "none") {
                        FilterChip(
                            selected = selectedFolder == UNCATEGORIZED,
                            onClick = {
                                selectedFolder = if (selectedFolder == UNCATEGORIZED) null else UNCATEGORIZED
                            },
                            label = { Text("未分類 ${repo.cards.count { isUncategorized(it) }}") },
                        )
                    }
                }
                item(key = "add") {
                    AssistChip(
                        onClick = { creatingFolder = true },
                        label = { Text("新增資料夾") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }

            if (repo.cards.isEmpty()) {
                EmptyState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val folderSel = selectedFolder
                    if (folderSel == null && repo.folders.isNotEmpty() && query.isBlank()) {
                        // 全部：依資料夾分段顯示
                        repo.folders.toList().forEachIndexed { i, f ->
                            val group = matched.filter { it.folder == f }
                            if (group.isNotEmpty()) section("h_$i", f, group, tile)
                        }
                        val rest = matched.filter { isUncategorized(it) }
                        if (rest.isNotEmpty()) section("h_none", "未分類", rest, tile)
                    } else {
                        val list = when (folderSel) {
                            null -> matched
                            UNCATEGORIZED -> matched.filter { isUncategorized(it) }
                            else -> matched.filter { it.folder == folderSel }
                        }
                        if (list.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                                Text(
                                    if (query.isNotBlank()) "找不到符合的會員卡"
                                    else "這個資料夾還是空的\n長按卡片選「移動到資料夾」，或直接按「新增」",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 48.dp),
                                )
                            }
                        } else {
                            items(list, key = { it.id }) { tile(it) }
                        }
                    }
                }
            }
        }
    }

    if (showBindApps) {
        BindAppsDialog(
            boundPackages = repo.cards.map { it.appPackage }.filter { it.isNotBlank() }.toSet(),
            onConfirm = { apps ->
                val now = System.currentTimeMillis()
                val newCards = apps.mapIndexed { i, app ->
                    MemberCard(
                        name = app.label,
                        number = "",
                        format = Barcodes.NONE,
                        color = runCatching { dominantColor(app.icon.asAndroidBitmap()) }.getOrDefault(0xFF546E7A),
                        appPackage = app.pkg,
                        appLabel = app.label,
                        folder = currentFolderName,
                        createdAt = now - i,
                    )
                }
                repo.addAll(newCards)
                showBindApps = false
                context.toast("已綁定 ${newCards.size} 個 App，點卡片即可開啟")
            },
            onDismiss = { showBindApps = false },
        )
    }
    if (showManageFolders) {
        ManageFoldersDialog(repo = repo, onDismiss = { showManageFolders = false })
    }
    if (creatingFolder) {
        FolderNameDialog(
            title = "新增資料夾",
            onConfirm = { name ->
                repo.addFolder(name).also { ok ->
                    if (ok) {
                        creatingFolder = false
                        selectedFolder = name
                    }
                }
            },
            onDismiss = { creatingFolder = false },
        )
    }
    movingCard?.let { card ->
        MoveToFolderDialog(
            repo = repo,
            current = card.folder,
            onPick = { f ->
                repo.moveCard(card.id, f)
                movingCard = null
                context.toast("已移到「${f.ifBlank { "未分類" }}」")
            },
            onDismiss = { movingCard = null },
        )
    }
    deletingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deletingCard = null },
            title = { Text("刪除會員卡？") },
            text = { Text("確定要刪除「${card.name}」嗎？此動作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    repo.delete(card.id)
                    deletingCard = null
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { deletingCard = null }) { Text("取消") } },
        )
    }
}

private fun LazyGridScope.section(
    key: String,
    title: String,
    cards: List<MemberCard>,
    tile: @Composable (MemberCard) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = key, contentType = "header") {
        Text(
            "$title（${cards.size}）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
    }
    items(cards, key = { it.id }) { tile(it) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(
    card: MemberCard,
    onClick: () -> Unit,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onOpenApp: () -> Unit,
    onMove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val bg = Color(card.color)
    val fg = onColor(bg)
    var menu by remember { mutableStateOf(false) }
    val icon = rememberAppIcon(card.appPackage)
    val appOnly = card.number.isBlank()
    val hasApp = card.appPackage.isNotBlank() || card.link.isNotBlank()

    Box {
        Surface(
            color = bg,
            contentColor = fg,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Image(
                            icon,
                            null,
                            Modifier
                                .size(28.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        card.name.ifBlank { "未命名" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
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
                    if (appOnly) "點一下開啟 App" else maskNumber(card.number),
                    fontFamily = if (appOnly) FontFamily.Default else FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = fg.copy(alpha = 0.85f),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!appOnly) {
                DropdownMenuItem(text = { Text("顯示條碼") }, onClick = { menu = false; onShowDetail() })
            }
            if (hasApp) {
                DropdownMenuItem(
                    text = { Text("開啟 ${card.appLabel.ifBlank { "App" }}") },
                    onClick = { menu = false; onOpenApp() },
                )
            }
            DropdownMenuItem(text = { Text("編輯") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("移動到資料夾") }, onClick = { menu = false; onMove() })
            DropdownMenuItem(
                text = { Text(if (card.favorite) "取消常用" else "設為常用") },
                onClick = { menu = false; onToggleFavorite() },
            )
            DropdownMenuItem(text = { Text("刪除") }, onClick = { menu = false; onDelete() })
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
            "點右下角「新增」：\n\n「綁定會員 App」\n不能截圖的會員條碼，點卡片直接開啟該 App\n\n「新增條碼卡」\n掃描實體卡或辨識截圖，條碼存在這裡",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
