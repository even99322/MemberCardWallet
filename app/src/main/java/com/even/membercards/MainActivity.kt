package com.even.membercards

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import com.even.membercards.data.CardRepository
import com.even.membercards.data.Shortcuts
import com.even.membercards.ui.AppTheme
import com.even.membercards.ui.DetailScreen
import com.even.membercards.ui.EditScreen
import com.even.membercards.ui.ListScreen

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data class Edit(
        val id: String?,
        val sharedImage: Uri? = null,
        val sharedText: String? = null,
        val sharedSubject: String? = null,
    ) : Screen
}

/** 從外部進來的請求：桌面捷徑開卡片、或其他 App 分享過來的截圖／文字。 */
sealed interface Incoming {
    data class OpenCard(val id: String) : Incoming
    data class Shared(val image: Uri?, val text: String?, val subject: String?) : Incoming
}

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Incoming?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = CardRepository.instance(applicationContext)
        if (savedInstanceState == null) incoming.value = parse(intent)
        setContent {
            AppTheme {
                App(repo, incoming.value) { incoming.value = null }
            }
        }
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
fun App(repo: CardRepository, incoming: Incoming?, onIncomingHandled: () -> Unit) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    LaunchedEffect(incoming) {
        when (incoming) {
            null -> return@LaunchedEffect
            is Incoming.OpenCard -> if (repo.get(incoming.id) != null) {
                stack.clear()
                stack.add(Screen.Home)
                stack.add(Screen.Detail(incoming.id))
            }

            is Incoming.Shared -> {
                stack.clear()
                stack.add(Screen.Home)
                stack.add(Screen.Edit(null, incoming.image, incoming.text, incoming.subject))
            }
        }
        onIncomingHandled()
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val current = stack.last()) {
        Screen.Home -> ListScreen(
            repo = repo,
            onOpen = { stack.add(Screen.Detail(it)) },
            onAdd = { stack.add(Screen.Edit(null)) },
        )

        is Screen.Detail -> DetailScreen(
            repo = repo,
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
            onBack = { pop() },
            onSaved = { savedId ->
                pop()
                if (current.id == null) stack.add(Screen.Detail(savedId))
            },
        )
    }
}
