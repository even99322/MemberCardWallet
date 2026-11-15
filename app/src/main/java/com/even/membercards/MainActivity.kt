package com.even.membercards

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import com.even.membercards.data.AppSettings
import com.even.membercards.data.CardRepository
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.MemberCard
import com.even.membercards.ui.AutoRecordScreen
import com.even.membercards.ui.LedgerEditScreen
import com.even.membercards.ui.LedgerScreen
import com.even.membercards.data.Shortcuts
import com.even.membercards.data.Updater
import com.even.membercards.ui.AppTheme
import com.even.membercards.ui.DetailScreen
import com.even.membercards.ui.EditScreen
import com.even.membercards.ui.ListScreen
import com.even.membercards.ui.LockScreen
import com.even.membercards.ui.QuickSetupScreen
import com.even.membercards.ui.ReorderScreen
import com.even.membercards.ui.SettingsScreen
import androidx.compose.ui.Modifier
import com.even.membercards.ui.StoreApps
import com.even.membercards.ui.UpdateDialog
import com.even.membercards.ui.CardShare
import com.even.membercards.ui.FullscreenBarcode
import com.even.membercards.ui.FullscreenPhones
import com.even.membercards.ui.effectiveCarrier
import com.even.membercards.data.Barcodes
import androidx.compose.ui.graphics.asImageBitmap
import com.even.membercards.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object QuickSetup : Screen
    data object Reorder : Screen
    data object Ledger : Screen
    data object AutoRecord : Screen
    data class LedgerEdit(
        val id: String?,
        val amount: Double? = null,
        val merchant: String = "",
        val method: String = "",
    ) : Screen
    data class Detail(val id: String) : Screen
    data class Edit(
        val id: String?,
        val sharedImage: Uri? = null,
        val sharedText: String? = null,
        val sharedSubject: String? = null,
        val folder: String = "",
        val kind: String = MemberCard.KIND_CARD,
    ) : Screen
}

/** 從外部進來的請求：桌面捷徑開卡片、或其他 App 分享過來的截圖／文字。 */
sealed interface Incoming {
    data class OpenCard(val id: String) : Incoming
    data class Shared(val image: Uri?, val text: String?, val subject: String?) : Incoming
    data object ShowCarrier : Incoming
    data object ShowPhone : Incoming
    data object AddEntry : Incoming
    data class ImportCard(val card: MemberCard) : Incoming
}

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Incoming?>(null)
    private val locked = mutableStateOf(false)
    private var backgroundAt = 0L
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val repo = CardRepository.instance(applicationContext)
        settings = AppSettings.instance(applicationContext)
        Shortcuts.updateDynamic(applicationContext, repo.cards.toList())
        ReminderWorker.schedule(applicationContext, settings.reminders)
        locked.value = settings.lockEnabled
        if (savedInstanceState == null) incoming.value = parse(intent)
        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize()) {
                    App(repo, settings, incoming.value, locked.value) { incoming.value = null }
                    if (locked.value) LockScreen(onUnlock = { locked.value = false })
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // 離開超過 30 秒回來就重新上鎖
        if (settings.lockEnabled && backgroundAt > 0 &&
            SystemClock.elapsedRealtime() - backgroundAt > LOCK_TIMEOUT_MS
        ) {
            locked.value = true
        }
    }

    companion object {
        private const val LOCK_TIMEOUT_MS = 30_000L
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = parse(intent)
    }

    private fun parse(intent: Intent?): Incoming? {
        intent ?: return null
        return when (intent.action) {
            Shortcuts.ACTION_OPEN_CARD ->
                intent.getStringExtra(Shortcuts.EXTRA_CARD_ID)?.let { Incoming.OpenCard(it) }

            CarrierWidget.ACTION_SHOW_CARRIER -> Incoming.ShowCarrier
            CarrierWidget.ACTION_SHOW_PHONE -> Incoming.ShowPhone
            Shortcuts.ACTION_ADD_ENTRY -> Incoming.AddEntry

            Intent.ACTION_SEND -> {
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                if (intent.type?.startsWith("image/") == true) {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    uri?.let { Incoming.Shared(it, null, subject) }
                } else {
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    val shared = text?.let { CardShare.decode(it) }
                    if (shared != null) Incoming.ImportCard(shared)
                    else text?.takeIf { it.isNotBlank() }?.let { Incoming.Shared(null, it, subject) }
                }
            }

            else -> null
        }
    }
}

@Composable
fun App(
    repo: CardRepository,
    settings: AppSettings,
    incoming: Incoming?,
    locked: Boolean,
    onIncomingHandled: () -> Unit,
) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    var showCarrier by remember { mutableStateOf<String?>(null) }
    var showPhones by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<MemberCard?>(null) }

    LaunchedEffect(incoming, locked) {
        if (locked) return@LaunchedEffect // 解鎖後才處理捷徑／分享
        when (incoming) {
            null -> return@LaunchedEffect
            is Incoming.OpenCard -> {
                val card = repo.get(incoming.id)
                val appOnly = card != null && card.number.isBlank() &&
                    (card.appPackage.isNotBlank() || card.link.isNotBlank())
                if (card != null && appOnly && StoreApps.open(context, card)) {
                    // 只綁定 App 的卡：捷徑直接開啟會員 App
                    repo.markUsed(card.id)
                } else if (card != null) {
                    stack.clear()
                    stack.add(Screen.Home)
                    stack.add(Screen.Detail(incoming.id))
                }
            }

            is Incoming.Shared -> {
                stack.clear()
                stack.add(Screen.Home)
                stack.add(Screen.Edit(null, incoming.image, incoming.text, incoming.subject))
            }

            Incoming.ShowCarrier -> {
                val c = effectiveCarrier(settings, repo)
                if (c.isBlank()) {
                    stack.clear()
                    stack.add(Screen.Home)
                    stack.add(Screen.QuickSetup)
                    context.toast("請先設定手機條碼載具")
                } else {
                    showCarrier = c
                }
            }

            Incoming.AddEntry -> {
                stack.clear()
                stack.add(Screen.Home)
                stack.add(Screen.Ledger)
                stack.add(Screen.LedgerEdit(null))
            }

            is Incoming.ImportCard -> importing = incoming.card

            Incoming.ShowPhone -> {
                if (settings.phones.isEmpty()) {
                    stack.clear()
                    stack.add(Screen.Home)
                    stack.add(Screen.QuickSetup)
                    context.toast("請先設定電話號碼")
                } else {
                    showPhones = true
                }
            }
        }
        onIncomingHandled()
    }

    // 開 App 時補記到期的定期收支
    LaunchedEffect(Unit) {
        val n = LedgerRepository.instance(context).runRecurring()
        if (n > 0) context.toast("已自動記入 $n 筆定期收支")
    }

    // 自動檢查更新（啟動時一次），選單也可手動檢查
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    fun checkUpdate(manual: Boolean) {
        scope.launch {
            val current = Updater.currentVersion(context)
            val latest = withContext(Dispatchers.IO) { runCatching { Updater.fetchLatest() }.getOrNull() }
            if (latest == null) {
                if (manual) context.toast("無法檢查更新，請確認網路連線")
                return@launch
            }
            val newer = Updater.isNewer(latest.version, current)
            if (newer && (manual || Updater.skippedVersion(context) != latest.version)) {
                update = latest
            } else if (manual) {
                context.toast("已是最新版本 v$current")
            }
        }
    }
    LaunchedEffect(Unit) { checkUpdate(manual = false) }
    if (!locked) update?.let { r ->
        UpdateDialog(
            release = r,
            onSkip = {
                Updater.skip(context, r.version)
                update = null
            },
            onDismiss = { update = null },
        )
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val current = stack.last()) {
        Screen.Home -> ListScreen(
            repo = repo,
            onOpenDetail = { stack.add(Screen.Detail(it)) },
            onEdit = { stack.add(Screen.Edit(it)) },
            onAdd = { folder -> stack.add(Screen.Edit(null, folder = folder)) },
            onAddCoupon = { stack.add(Screen.Edit(null, kind = MemberCard.KIND_COUPON)) },
            onReorder = { stack.add(Screen.Reorder) },
            onOpenSettings = { stack.add(Screen.Settings) },
            onQuickSetup = { stack.add(Screen.QuickSetup) },
            onOpenLedger = { stack.add(Screen.Ledger) },
            settings = settings,
        )

        Screen.Ledger -> LedgerScreen(
            ledger = LedgerRepository.instance(context),
            settings = settings,
            onBack = { pop() },
            onAdd = { stack.add(Screen.LedgerEdit(null)) },
            onEdit = { stack.add(Screen.LedgerEdit(it)) },
            onAutoSetup = { stack.add(Screen.AutoRecord) },
        )

        Screen.AutoRecord -> AutoRecordScreen(
            ledger = LedgerRepository.instance(context),
            settings = settings,
            onBack = { pop() },
            onRecordFrom = { amount, merchant, method ->
                stack.add(Screen.LedgerEdit(null, amount, merchant, method))
            },
        )

        is Screen.LedgerEdit -> LedgerEditScreen(
            ledger = LedgerRepository.instance(context),
            repo = repo,
            settings = settings,
            entryId = current.id,
            prefillAmount = current.amount,
            prefillMerchant = current.merchant,
            prefillMethod = current.method,
            onBack = { pop() },
        )

        Screen.Reorder -> ReorderScreen(repo = repo, settings = settings, onBack = { pop() })

        Screen.QuickSetup -> QuickSetupScreen(
            repo = repo,
            settings = settings,
            onBack = { pop() },
        )

        Screen.Settings -> SettingsScreen(
            repo = repo,
            settings = settings,
            onBack = { pop() },
            onQuickSetup = { stack.add(Screen.QuickSetup) },
            onOpenLedger = { stack.add(Screen.Ledger) },
            onAutoRecord = { stack.add(Screen.AutoRecord) },
            onCheckUpdate = { checkUpdate(manual = true) },
        )

        is Screen.Detail -> DetailScreen(
            repo = repo,
            settings = settings,
            id = current.id,
            onBack = { pop() },
            onEdit = { stack.add(Screen.Edit(current.id)) },
        )

        is Screen.Edit -> EditScreen(
            repo = repo,
            cardId = current.id,
            sharedImage = current.sharedImage,
            sharedText = current.sharedText,
            sharedSubject = current.sharedSubject,
            defaultFolder = current.folder,
            defaultKind = current.kind,
            onBack = { pop() },
            onSaved = { savedId ->
                pop()
                if (current.id == null) stack.add(Screen.Detail(savedId))
            },
        )
    }

    // 收到別人分享的卡片
    importing?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importing = null },
            title = { androidx.compose.material3.Text(if (c.isCoupon) "匯入票券？" else "匯入會員卡？") },
            text = {
                androidx.compose.material3.Text(
                    "「${c.name}」" + if (c.number.isNotBlank()) "\n號碼：${c.number}" else "",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    repo.upsert(c)
                    importing = null
                    stack.clear()
                    stack.add(Screen.Home)
                    stack.add(Screen.Detail(c.id))
                    context.toast("已匯入「${c.name}」")
                }) { androidx.compose.material3.Text("匯入") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { importing = null }) { androidx.compose.material3.Text("取消") }
            },
        )
    }

    // 小工具／快速設定磚：全螢幕載具或電話
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
        }
    }
    if (showPhones && settings.phones.isNotEmpty()) {
        FullscreenPhones(entries = settings.phones, onClose = { showPhones = false })
    }
}
