package com.even.membercards.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.CardRepository
import com.even.membercards.data.EntryType
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.formatNumber

/** 一句話記帳：「午餐 120」「加油 1500 信用卡」，可用語音輸入。 */
@Composable
fun QuickEntryBar(ledger: LedgerRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val cards = remember { CardRepository.instance(context).cards.toList() }
    var text by rememberSaveable { mutableStateOf("") }
    val preview = remember(text, ledger.entries.size) { ledger.quickParse(text, cards) }

    fun commit() {
        val e = preview
        if (e == null) {
            context.toast("請輸入內容與金額，例如：午餐 120")
            return
        }
        ledger.upsert(e)
        val acc = ledger.account(e.account)?.name.orEmpty()
        context.toast(
            "已記 ${e.note.ifBlank { e.category }} " +
                (if (e.type == EntryType.INCOME) "+" else "-") + formatNumber(e.amount) +
                "（${e.category}${if (acc.isNotBlank()) " · $acc" else ""}）"
        )
        text = ""
        focus.clearFocus()
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) text = said
        }
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("做什麼用的？多少錢？例：午餐 120", color = Hud.TextFaint, fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = hudFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            // 語音
            Text(
                "🎤",
                fontSize = 20.sp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Hud.Surface2)
                    .border(1.dp, Hud.LineBright, CircleShape)
                    .clickable {
                        try {
                            voice.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "說說看：午餐 一百二十元")
                            )
                        } catch (e: ActivityNotFoundException) {
                            context.toast("這支手機沒有語音輸入服務")
                        }
                    }
                    .padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (text.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "✓",
                    color = Hud.OnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (preview != null) Hud.Cyan else Hud.LineBright)
                        .clickable { commit() }
                        .padding(top = 10.dp),
                )
            }
        }
        val p = preview
        if (text.isNotBlank()) {
            Text(
                if (p == null) "需要包含金額，例如：午餐 120"
                else "將記：${ledger.category(p.category, p.type).emoji} ${p.category} · ${p.note.ifBlank { "（無備註）" }} · " +
                    (if (p.type == EntryType.INCOME) "+" else "-") + formatNumber(p.amount) +
                    (ledger.account(p.account)?.name?.let { " · $it" } ?: ""),
                style = MonoLabel.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                color = if (p == null) Hud.Warn else Hud.Cyan,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}
