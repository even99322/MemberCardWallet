package com.even.membercards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.even.membercards.data.CardRepository
import com.even.membercards.ui.AppTheme
import com.even.membercards.ui.DetailScreen
import com.even.membercards.ui.EditScreen
import com.even.membercards.ui.ListScreen

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data class Edit(val id: String?) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = CardRepository(applicationContext)
        setContent {
            AppTheme {
                App(repo)
            }
        }
    }
}

@Composable
fun App(repo: CardRepository) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
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
            onBack = { pop() },
            onSaved = { savedId ->
                pop()
                if (current.id == null) stack.add(Screen.Detail(savedId))
            },
        )
    }
}
