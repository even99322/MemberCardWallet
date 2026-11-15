package com.even.membercards.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.Barcodes
import com.even.membercards.data.CardRepository
import com.even.membercards.data.MemberCard
import java.text.Collator
import java.util.Locale

/** 篩選用的特殊值：未分類 */
private const val UNCATEGORIZED = "\u0001uncategorized"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    repo: CardRepository,
    settings: AppSettings,
    onOpenDetail: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: (folder: String) -> Unit,
    onOpenSettings: () -> Unit,
    onQuickSetup: () -> Unit,
    onOpenLedger: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    // null = 全部；UNCATEGORIZED = 未分類；其他 = 資料夾名稱
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var showBindApps by remember { mutableStateOf(false) }
    var creatingFolder by remember { mutableStateOf(false) }
    var movingCard by remember { mutableStateOf<MemberCard?>(null) }
    var deletingCard by remember { mutableStateOf<MemberCard?>(null) }
    var showCarrier by remember { mutableStateOf<String?>(null) }
    var showPhones by remember { mutableStateOf(false) }

    val sel = selectedFolder
    if (sel != null && sel != UNCATEGORIZED && sel !in repo.folders) selectedFolder = null
    val currentFolderName = selectedFolder?.takeIf { it != UNCATEGORIZED } ?: ""

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

    val collator = remember { Collator.getInstance(Locale.TAIWAN) }
    val comparator: Comparator<MemberCard> = when (settings.sortMode) {
        AppSettings.SORT_NAME -> compareByDescending<MemberCard> { it.favorite }
            .then(Comparator { a, b -> collator.compare(a.name, b.name) })
        AppSettings.SORT_USAGE -> compareByDescending<MemberCard> { it.useCount }.thenByDescending { it.lastUsed }
        AppSettings.SORT_ADDED -> compareByDescending<MemberCard> { it.createdAt }
        AppSettings.SORT_EXPIRY -> compareBy<MemberCard> { if (it.expiry > 0) it.expiry else Long.MAX_VALUE }
            .thenByDescending { it.lastUsed }
        else -> compareByDescending<MemberCard> { it.favorite }
            .thenByDescending { it.lastUsed }
            .thenByDescending { it.createdAt }
    }
    val matched = repo.cards
        .filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                it.note.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true) ||
                it.appLabel.contains(query, ignoreCase = true) || it.folder.contains(query, ignoreCase = true)
        }
        .sortedWith(comparator)
    val hasUncategorized = repo.folders.isNotEmpty() && repo.cards.any { isUncategorized(it) }
    val expiringCount = repo.cards.count { expiryBadge(it) != null }
    val listMode = settings.viewMode == AppSettings.VIEW_LIST

    val tile: @Composable (MemberCard) -> Unit = { card ->
        CardItem(
            card = card,
            listMode = listMode,
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
                            Text("會員卡夾", fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(Hud.Ok, RoundedCornerShape(3.dp))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "MEMBER WALLET // ${repo.cards.size} CARDS" +
                                        if (expiringCount > 0) " // $expiringCount EXPIRING" else "",
                                    style = MonoLabel,
                                    color = if (expiringCount > 0) Hud.Warn else Hud.Cyan,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "設定") }
                    },
                    colors = hudTopBarColors(),
                )
            },
            floatingActionButton = {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { addMenuOpen = true },
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("新增", fontWeight = FontWeight.SemiBold) },
                        containerColor = Hud.Cyan,
                        contentColor = Hud.Bg,
                        shape = RoundedCornerShape(10.dp),
                    )
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("綁定會員 App")
                                    Text("不能截圖的條碼，點卡片直接開 App", fontSize = 11.sp, color = Hud.TextDim)
                                }
                            },
                            onClick = {
                                addMenuOpen = false
                                showBindApps = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("新增條碼卡")
                                    Text("相機掃描、截圖辨識或手動輸入", fontSize = 11.sp, color = Hud.TextDim)
                                }
                            },
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
                // 快捷列：載具、手機號碼、付款 App（搜尋時隱藏）
                if (query.isBlank()) {
                    QuickBar(
                        settings = settings,
                        repo = repo,
                        onSetup = onQuickSetup,
                        onShowCarrier = { showCarrier = it },
                        onShowPhones = { showPhones = true },
                        onOpenLedger = onOpenLedger,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                    )
                }

                if (repo.cards.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("搜尋店家、資料夾、號碼", color = Hud.TextFaint) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Hud.TextDim) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "清除") }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = hudFieldColors(),
                    )
                }

                // 資料夾列
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") {
                        FolderChip("全部", repo.cards.size, selectedFolder == null) { selectedFolder = null }
                    }
                    items(repo.folders.toList(), key = { "f_$it" }) { f ->
                        FolderChip(f, repo.cards.count { it.folder == f }, selectedFolder == f) {
                            selectedFolder = if (selectedFolder == f) null else f
                        }
                    }
                    if (hasUncategorized) {
                        item(key = "none") {
                            FolderChip(
                                "未分類",
                                repo.cards.count { isUncategorized(it) },
                                selectedFolder == UNCATEGORIZED,
                            ) { selectedFolder = if (selectedFolder == UNCATEGORIZED) null else UNCATEGORIZED }
                        }
                    }
                    item(key = "add") {
                        FilterChip(
                            selected = false,
                            onClick = { creatingFolder = true },
                            label = { Text("＋ 資料夾") },
                            colors = hudChipColors(),
                            border = hudChipBorder(false),
                        )
                    }
                }

                if (repo.cards.isEmpty()) {
                    EmptyState()
                } else {
                    LazyVerticalGrid(
                        columns = if (listMode) GridCells.Fixed(1) else GridCells.Adaptive(158.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (listMode) 8.dp else 12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val folderSel = selectedFolder
                        val grouped = folderSel == null && repo.folders.isNotEmpty() && query.isBlank() &&
                            settings.sortMode == AppSettings.SORT_SMART
                        if (grouped) {
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
                                        if (query.isNotBlank()) "NO MATCH // 找不到符合的會員卡"
                                        else "EMPTY // 這個資料夾還是空的\n長按卡片選「移動到資料夾」，或直接按「新增」",
                                        textAlign = TextAlign.Center,
                                        color = Hud.TextDim,
                                        fontSize = 13.sp,
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
    }

    showCarrier?.let { c ->
        val bmp = remember(c) { runCatching { Barcodes.encode(c, "CODE_39", 1000) }.getOrNull() }
        if (bmp != null) {
            FullscreenBarcode(
                bitmap = bmp.asImageBitmap(),
                number = c,
                rotate = true,
                title = "手機條碼載具",
                onClose = { showCarrier = null },
            )
        } else {
            showCarrier = null
            context.toast("載具號碼格式不正確，請到快捷列設定修改")
        }
    }
    if (showPhones && settings.phones.isNotEmpty()) {
        FullscreenPhones(entries = settings.phones, onClose = { showPhones = false })
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
                        color = runCatching { dominantColor(app.icon.asAndroidBitmap()) }.getOrDefault(0xFF8A9BB3),
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
                }) { Text("刪除", color = Hud.Danger) }
            },
            dismissButton = { TextButton(onClick = { deletingCard = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun FolderChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                Spacer(Modifier.width(6.dp))
                Text("$count", style = MonoLabel, color = if (selected) Hud.Cyan else Hud.TextFaint)
            }
        },
        colors = hudChipColors(),
        border = hudChipBorder(selected),
    )
}

private fun LazyGridScope.section(
    key: String,
    title: String,
    cards: List<MemberCard>,
    tile: @Composable (MemberCard) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = key, contentType = "header") {
        Row(
            Modifier.padding(top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("//", style = MonoLabel, color = Hud.Cyan)
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Hud.Text)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Hud.LineBright, Color.Transparent)))
            )
            Spacer(Modifier.width(8.dp))
            Text(cards.size.toString().padStart(2, '0'), style = MonoLabel, color = Hud.TextDim)
        }
    }
    items(cards, key = { it.id }) { tile(it) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardItem(
    card: MemberCard,
    listMode: Boolean,
    onClick: () -> Unit,
    onShowDetail: () -> Unit,
    onEdit: () -> Unit,
    onOpenApp: () -> Unit,
    onMove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val accent = accentOf(card.color)
    val appOnly = card.number.isBlank()
    val hasApp = card.appPackage.isNotBlank() || card.link.isNotBlank()
    val badge = expiryBadge(card)
    val shape = RoundedCornerShape(if (listMode) 8.dp else 12.dp)
    val tag = if (appOnly) "APP" else shortFormat(card.format)

    Box {
        val base = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.22f), Hud.Surface.copy(alpha = 0.95f)),
                    start = Offset.Zero,
                    end = Offset(600f, 400f),
                )
            )
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })

        if (listMode) {
            Row(base.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accent)
                )
                Spacer(Modifier.width(12.dp))
                CardGlyph(card, 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        card.name.ifBlank { "未命名" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Hud.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            card.folder.takeIf { it.isNotBlank() },
                            if (appOnly) "點一下開啟 App" else maskNumber(card.number),
                        ).joinToString("  ·  "),
                        style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                        color = Hud.TextDim,
                        maxLines = 1,
                    )
                }
                if (card.favorite) {
                    Icon(Icons.Filled.Favorite, "常用", tint = Hud.Cyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                if (badge != null) {
                    HudTag(badge.first, badge.second)
                    Spacer(Modifier.width(6.dp))
                }
                HudTag(tag, accent)
                Spacer(Modifier.width(12.dp))
            }
        } else {
            Column(
                base
                    .aspectRatio(1.586f)
                    .cornerBrackets(accent.copy(alpha = 0.9f), length = 9.dp, stroke = 1.5.dp, inset = 5.dp)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardGlyph(card, 30.dp)
                    Spacer(Modifier.weight(1f))
                    if (card.favorite) {
                        Icon(Icons.Filled.Favorite, "常用", tint = Hud.Cyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    HudTag(tag, accent)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    card.name.ifBlank { "未命名" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Hud.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (appOnly) "▶ LAUNCH APP" else maskNumber(card.number),
                        style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                        color = if (appOnly) accent else Hud.TextDim,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (badge != null) HudTag(badge.first, badge.second)
                }
            }
        }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!appOnly) {
                DropdownMenuItem(text = { Text("顯示條碼") }, onClick = { menu = false; onShowDetail() })
            } else {
                DropdownMenuItem(text = { Text("卡片資訊") }, onClick = { menu = false; onShowDetail() })
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
            DropdownMenuItem(text = { Text("刪除", color = Hud.Danger) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 150.dp, height = 94.dp)
                .border(1.dp, Hud.Line, RoundedCornerShape(10.dp))
                .cornerBrackets(Hud.Cyan, length = 12.dp, stroke = 2.dp, inset = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("NO CARDS", style = MonoLabel.copy(fontSize = 12.sp, letterSpacing = 3.sp), color = Hud.Cyan)
        }
        Spacer(Modifier.height(24.dp))
        Text("還沒有會員卡", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Hud.Text)
        Spacer(Modifier.height(12.dp))
        Text(
            "點右下角「新增」\n\n綁定會員 App — 不能截圖的條碼，點卡片直接開 App\n新增條碼卡 — 掃描實體卡或辨識截圖",
            textAlign = TextAlign.Center,
            color = Hud.TextDim,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }
}
