package com.even.membercards.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.even.membercards.data.AutoStep
import com.even.membercards.data.AutoTap

fun openAccessibilitySettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    context.toast("找到「會員卡夾自動點擊」並開啟")
}

/**
 * 「自動點到條碼頁」步驟編輯器：錄製、手動新增、排序、測試。
 * onTest 由呼叫端用目前（尚未儲存）的步驟開啟 App。
 */
@Composable
fun AutoStepsEditor(
    steps: SnapshotStateList<AutoStep>,
    appPackage: String,
    appLabel: String,
    onTest: () -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AutoTap.isEnabled(context)) }
    var addMenu by remember { mutableStateOf(false) }

    // 從系統設定或店家 App 回來時：更新權限狀態、收下錄到的步驟
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                enabled = AutoTap.isEnabled(context)
                if (AutoTap.isRecording()) {
                    val rec = AutoTap.stopRecording()
                    if (rec.isNotEmpty()) {
                        steps.clear()
                        steps.addAll(rec)
                        context.toast("已錄下 ${rec.size} 個步驟，按「測試」試試看")
                    } else {
                        context.toast("沒有錄到點擊。請確認畫面上方有出現「● 錄製中」再點，點完按「完成」")
                    }
                }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("自動點到條碼頁", color = Hud.Text, fontSize = 15.sp)
        Text(
            "開啟 App 後自動依序點擊，直接停在會員條碼畫面。按「錄製」後，店家 App 上方會出現「● 錄製中」，照平常點到條碼頁再按「完成」即可（點錯可按「撤銷」）。",
            color = Hud.TextDim,
            fontSize = 12.sp,
        )

        if (!enabled) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Hud.Warn.copy(alpha = 0.08f))
                    .border(1.dp, Hud.Warn.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { openAccessibilitySettings(context) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "需要開啟無障礙權限「會員卡夾自動點擊」，點此前往設定",
                    color = Hud.Warn,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton(
                text = "● 錄製",
                onClick = {
                    when {
                        appPackage.isBlank() -> context.toast("請先選擇店家 App")
                        !enabled -> openAccessibilitySettings(context)
                        else -> {
                            AutoTap.startRecording(appPackage)
                            context.toast("上方出現「● 錄製中」後，照平常點到條碼頁，再按「完成」")
                            StoreApps.launch(context, appPackage, appLabel)
                        }
                    }
                },
                accent = Hud.Danger,
                modifier = Modifier.weight(1f),
            )
            HudButton(
                text = "▶ 測試",
                onClick = {
                    when {
                        steps.isEmpty() -> context.toast("還沒有步驟")
                        !enabled -> openAccessibilitySettings(context)
                        else -> onTest()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Box {
                HudButton(text = "＋ 步驟", onClick = { addMenu = true }, accent = Hud.TextDim)
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text("點擊按鈕文字") }, onClick = {
                        addMenu = false
                        steps.add(AutoStep(AutoStep.TEXT))
                    })
                    DropdownMenuItem(text = { Text("等待幾秒") }, onClick = {
                        addMenu = false
                        steps.add(AutoStep(AutoStep.WAIT, seconds = 1f))
                    })
                    DropdownMenuItem(text = { Text("點擊畫面位置") }, onClick = {
                        addMenu = false
                        steps.add(AutoStep(AutoStep.POS, x = 0.5f, y = 0.5f))
                    })
                }
            }
        }

        steps.forEachIndexed { i, s ->
            StepRow(
                index = i,
                step = s,
                isFirst = i == 0,
                isLast = i == steps.lastIndex,
                onChange = { steps[i] = it },
                onUp = { steps.add(i - 1, steps.removeAt(i)) },
                onDown = { steps.add(i + 1, steps.removeAt(i)) },
                onDelete = { steps.removeAt(i) },
            )
        }
        if (steps.isNotEmpty()) {
            TextButton(onClick = { steps.clear() }) { Text("清除所有步驟", color = Hud.TextDim) }
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: AutoStep,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: (AutoStep) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Hud.Surface)
            .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "STEP ${(index + 1).toString().padStart(2, '0')}",
                style = MonoLabel,
                color = Hud.Cyan,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when (step.type) {
                    AutoStep.POS -> "點擊位置"
                    AutoStep.WAIT -> "等待"
                    else -> "點擊文字"
                },
                color = Hud.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onUp, enabled = !isFirst, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, "上移")
            }
            IconButton(onClick = onDown, enabled = !isLast, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "下移")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, "刪除", tint = Hud.TextDim)
            }
        }
        when (step.type) {
            AutoStep.WAIT -> OutlinedTextField(
                value = step.seconds.toString().removeSuffix(".0"),
                onValueChange = { v -> v.toFloatOrNull()?.let { onChange(step.copy(seconds = it.coerceIn(0.1f, 30f))) } },
                label = { Text("秒數") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = hudFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            AutoStep.POS -> Row {
                OutlinedTextField(
                    value = (step.x * 100).toInt().toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { onChange(step.copy(x = (it / 100f).coerceIn(0f, 1f))) } },
                    label = { Text("橫向 %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = hudFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = (step.y * 100).toInt().toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { onChange(step.copy(y = (it / 100f).coerceIn(0f, 1f))) } },
                    label = { Text("縱向 %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = hudFieldColors(),
                    modifier = Modifier.weight(1f),
                )
            }

            else -> OutlinedTextField(
                value = step.text,
                onValueChange = { onChange(step.copy(text = it)) },
                label = { Text("按鈕上的文字") },
                placeholder = { Text("例如：會員條碼", color = Hud.TextFaint) },
                singleLine = true,
                colors = hudFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
