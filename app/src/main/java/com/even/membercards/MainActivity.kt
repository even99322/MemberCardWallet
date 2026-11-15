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
import com.even.membercards.data.Shortcuts
import com.even.membercards.data.Updater
import com.even.membercards.ui.AppTheme
import com.even.membercards.ui.DetailScreen
import com.even.membercards.ui.EditScreen
import com.even.membercards.ui.ListScreen
import com.even.membercards.ui.LockScreen
import com.even.membercards.ui.SettingsScreen
import androidx.compose.ui.Modifier
import com.even.membercards.ui.StoreApps
import com.even.membercards.ui.UpdateDialog
import com.even.membercards.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Detail(val id: String) : Screen
    data class Edit(
        val id: String?,
        val sharedImage: Uri? = null,
        val sharedText: String? = null,
        val sharedSubject: String? = null,
        val folder: String = "",
    ) : Screen
}

/** 從外部進來的請求：桌面捷徑開卡片、或其他 App 分享過來的截圖／文字。 */
sealed interface Incoming {
    data class OpenCard(val id: String) : Incoming
    data class Shared(val image: Uri?, val text: String?, val subject: String?) : Incoming
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

            Intent.ACTION_SEND -> {
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                if (intent.type?.startsWith("image/") == true) {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    uri?.let { Incoming.Shared(it, null, subject) }
                } else {
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    text?.takeIf { it.isNotBlank() }?.let { Incoming.Shared(null, it, subject) }
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
        }
        onIncomingHandled()
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
            onOpenSettings = { stack.add(Screen.Settings) },
            settings = settings,
        )

        Screen.Settings -> SettingsScreen(
            repo = repo,
            settings = settings,
            onBack = { pop() },
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
            onBack = { pop() },
            onSaved = { savedId ->
                pop()
                if (current.id == null) stack.add(Screen.Detail(savedId))
            },
        )
    }
}
