package com.even.membercards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CardRepository
import com.even.membercards.data.MemberCard
import kotlinx.coroutines.launch

private const val ALL = "\u0001all"
private const val NONE = "\u0001none"
private const val COUPON = "\u0001coupon"

/** 拖曳排序：卡片（可只排某個資料夾）與資料夾。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderScreen(repo: CardRepository, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) } // 0 卡片 1 資料夾
    var group by rememberSaveable { mutableStateOf(ALL) }

    // 目前要排的卡片（依現有的手動順序）
    fun subset(): List<MemberCard> = repo.cards.filter { c ->
        when (group) {
            ALL -> !c.isCoupon
            NONE -> !c.isCoupon && (c.folder.isBlank() || c.folder !in repo.folders)
            COUPON -> c.isCoupon
            else -> !c.isCoupon && c.folder == group
        }
    }

    val cardList = remember { mutableStateListOf<MemberCard>() }
    LaunchedEffect(group, tab) {
        cardList.clear()
        cardList.addAll(subset())
    }
    val folderList = remember { mutableStateListOf<String>().apply { addAll(repo.folders) } }

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
                            Text("調整順序", fontWeight = FontWeight.SemiBold)
                            Text("REORDER // 按住 ≡ 拖動", style = MonoLabel, color = Hud.Cyan)
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
                    .padding(horizontal = 16.dp),
            ) {
                // 卡片／資料夾
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Hud.LineBright, RoundedCornerShape(8.dp)),
                ) {
                    listOf("卡片順序", "資料夾順序").forEachIndexed { i, label ->
                        Text(
                            label,
                            color = if (tab == i) Hud.OnPrimary else Hud.TextDim,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (tab == i) Hud.Cyan else Color.Transparent)
                                .clickable { tab = i }
                                .padding(vertical = 10.dp),
                        )
                    }
                }

                if (tab == 0) {
                    // 選要排哪一組
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val groups = listOf(ALL to "全部") + repo.folders.map { it to it } +
                            (if (repo.cards.any { !it.isCoupon && (it.folder.isBlank() || it.folder !in repo.folders) }) listOf(NONE to "未分類") else emptyList()) +
                            (if (repo.cards.any { it.isCoupon }) listOf(COUPON to "票券") else emptyList())
                        items(groups) { (k, label) ->
                            FilterChip(
                                selected = group == k,
                                onClick = { group = k },
                                label = { Text(label) },
                                colors = hudChipColors(),
                                border = hudChipBorder(group == k),
                            )
                        }
                    }
                    ReorderableList(
                        items = cardList,
                        key = { it.id },
                        onMove = { from, to -> cardList.add(to, cardList.removeAt(from)) },
                        onDragEnd = {
                            repo.reorderCards(cardList.map { it.id })
                            if (settings.sortMode != AppSettings.SORT_MANUAL) {
                                settings.sortMode = AppSettings.SORT_MANUAL
                                context.toast("首頁已改為「自訂（拖曳）」排序")
                            }
                        },
                    ) { card, dragging, handle ->
                        ReorderRow(dragging, handle) {
                            CardGlyph(card, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(card.name, color = Hud.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    card.folder.ifBlank { if (card.isCoupon) "票券" else "未分類" },
                                    style = MonoLabel,
                                    color = Hud.TextDim,
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.size(8.dp))
                    if (folderList.isEmpty()) {
                        Text("還沒有資料夾", color = Hud.TextDim, modifier = Modifier.padding(16.dp))
                    }
                    ReorderableList(
                        items = folderList,
                        key = { it },
                        onMove = { from, to -> folderList.add(to, folderList.removeAt(from)) },
                        onDragEnd = { repo.setFolderOrder(folderList.toList()) },
                    ) { f, dragging, handle ->
                        ReorderRow(dragging, handle) {
                            Text("//", style = MonoLabel, color = Hud.Cyan)
                            Spacer(Modifier.width(10.dp))
                            Text(f, color = Hud.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text("${repo.cards.count { it.folder == f && !it.isCoupon }} 張", style = MonoLabel, color = Hud.TextDim)
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderRow(dragging: Boolean, handle: Modifier, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (dragging) Modifier.shadow(10.dp, RoundedCornerShape(10.dp)) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(if (dragging) Hud.Surface3 else Hud.Surface.copy(alpha = 0.95f))
            .border(1.dp, if (dragging) Hud.Cyan else Hud.Line, RoundedCornerShape(10.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { content() }
        // 拖曳把手
        Box(
            Modifier
                .size(56.dp)
                .then(handle),
            contentAlignment = Alignment.Center,
        ) {
            Text("≡", color = if (dragging) Hud.Cyan else Hud.TextDim, fontSize = 26.sp)
        }
    }
}

/**
 * 可拖曳排序的清單。把手 Modifier 傳給 itemContent：
 * 按住把手立即拖動，或長按整列拖動。
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    itemContent: @Composable (item: T, dragging: Boolean, handle: Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    fun handleDrag(k: Any, dy: Float) {
        dragOffset += dy
        val info = listState.layoutInfo
        val cur = info.visibleItemsInfo.firstOrNull { it.key == k } ?: return
        val center = cur.offset + dragOffset + cur.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != k && center > it.offset && center < it.offset + it.size
        }
        if (target != null) {
            currentOnMove(cur.index, target.index)
            dragOffset += (cur.offset - target.offset)
        }
        // 拖到邊緣自動捲動
        val top = cur.offset + dragOffset
        val bottom = top + cur.size
        val edge = 60f
        when {
            bottom > info.viewportEndOffset - edge -> scope.launch { listState.scrollBy(18f) }
            top < info.viewportStartOffset + edge && listState.firstVisibleItemIndex > 0 -> scope.launch { listState.scrollBy(-18f) }
        }
    }

    fun end() {
        draggingKey = null
        dragOffset = 0f
        currentOnDragEnd()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, it -> key(it) }) { _, item ->
            val k = key(item)
            val dragging = draggingKey == k
            val handle = Modifier
                .pointerInput(k) {
                    detectDragGestures(
                        onDragStart = {
                            draggingKey = k
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            handleDrag(k, amount.y)
                        },
                        onDragEnd = { end() },
                        onDragCancel = { end() },
                    )
                }
            val rowLongPress = Modifier.pointerInput(k) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggingKey = k
                        dragOffset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        handleDrag(k, amount.y)
                    },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                )
            }
            Box(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .then(rowLongPress)
            ) {
                itemContent(item, dragging, handle)
            }
        }
    }
}
