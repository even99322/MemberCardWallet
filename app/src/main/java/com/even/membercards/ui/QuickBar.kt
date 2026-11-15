package com.even.membercards.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.membercards.data.AppSettings
import com.even.membercards.data.Barcodes
import com.even.membercards.data.CardRepository
import com.even.membercards.data.LedgerRepository
import com.even.membercards.data.formatMoney

/** 載具號碼：優先用設定值，沒有就從卡片中找「手機條碼載具」。 */
fun effectiveCarrier(settings: AppSettings, repo: CardRepository): String {
    if (settings.carrier.isNotBlank()) return settings.carrier
    return repo.cards.firstOrNull { AppSettings.CARRIER_REGEX.matches(it.number) }?.number
        ?: repo.cards.firstOrNull { it.name.contains("載具") && it.number.isNotBlank() }?.number
        ?: ""
}

/** 手機號碼加上分隔：0912-345-678 */
fun formatPhone(p: String): String {
    val d = p.filter { it.isDigit() }
    return if (d.length == 10 && d.startsWith("09")) "${d.take(4)}-${d.substring(4, 7)}-${d.substring(7)}" else p
}

/**
 * 首頁最上方的快捷列：載具、手機號碼、常用付款 App。
 */
@Composable
fun QuickBar(
    settings: AppSettings,
    repo: CardRepository,
    onSetup: () -> Unit,
    onShowCarrier: (String) -> Unit,
    onShowPhones: () -> Unit,
    onOpenLedger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val carrier = effectiveCarrier(settings, repo)
    val phones = settings.phones
    val phone = phones.firstOrNull()?.number ?: ""
    val collapsed = settings.quickCollapsed
    val ledger = remember { LedgerRepository.instance(context) }
    val monthTotal = ledger.monthTotal()
    val budget = settings.monthlyBudget

    Column(
        modifier
            .fillMaxWidth()
            .background(Hud.Surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .border(1.dp, Hud.Line, RoundedCornerShape(12.dp))
            .cornerBrackets(Hud.CyanDim, length = 8.dp, stroke = 1.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 標題列
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("QUICK ACCESS", style = MonoLabel, color = Hud.Cyan)
            Spacer(Modifier.weight(1f))
            // 本月支出（點了進記帳）
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        1.dp,
                        (if (budget > 0 && monthTotal > budget) Hud.Danger else Hud.LineBright).copy(alpha = 0.7f),
                        RoundedCornerShape(6.dp),
                    )
                    .clickable(onClick = onOpenLedger)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("本月 ", fontSize = 11.sp, color = Hud.TextDim)
                Text(
                    formatMoney(monthTotal),
                    fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = if (budget > 0 && monthTotal > budget) Hud.Danger else Hud.Text,
                )
                if (budget > 0) {
                    Text(
                        " /${Math.round(monthTotal * 100 / budget)}%",
                        style = MonoLabel,
                        color = Hud.TextDim,
                    )
                }
                Text("  記帳 ›", fontSize = 11.sp, color = Hud.Cyan)
            }
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Filled.Edit,
                "設定快捷列",
                tint = Hud.TextDim,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSetup)
                    .padding(6.dp),
            )
            Icon(
                if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                if (collapsed) "展開" else "收合",
                tint = Hud.TextDim,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { settings.quickCollapsed = !collapsed }
                    .padding(4.dp),
            )
        }

        if (!collapsed) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 載具
                QuickTile(
                    label = "CARRIER // 載具",
                    modifier = Modifier.weight(1.15f),
                    onClick = { if (carrier.isBlank()) onSetup() else onShowCarrier(carrier) },
                ) {
                    if (carrier.isBlank()) {
                        Text("＋ 設定手機條碼", color = Hud.TextDim, fontSize = 13.sp)
                    } else {
                        val bmp = remember(carrier) {
                            runCatching { Barcodes.encode(carrier, "CODE_39", 600) }.getOrNull()
                        }
                        if (bmp != null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .background(Color.White, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 3.dp)
                            ) {
                                Image(
                                    bmp.asImageBitmap(),
                                    null,
                                    contentScale = ContentScale.FillBounds,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                        }
                        Text(
                            carrier,
                            fontFamily = Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                            color = Hud.Text,
                        )
                    }
                }
                // 手機號碼
                QuickTile(
                    label = "PHONE // 手機",
                    modifier = Modifier.weight(1f),
                    onClick = { if (phone.isBlank()) onSetup() else onShowPhones() },
                ) {
                    if (phone.isBlank()) {
                        Text("＋ 設定手機號碼", color = Hud.TextDim, fontSize = 13.sp)
                    } else {
                        Text(
                            formatPhone(phone),
                            fontFamily = Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp,
                            color = Hud.Text,
                            maxLines = 1,
                        )
                        Text(
                            if (phones.size > 1) "備選 +${phones.size - 1} · 點一下放大" else "點一下放大",
                            fontSize = 10.sp,
                            color = if (phones.size > 1) Hud.Cyan else Hud.TextFaint,
                            maxLines = 1,
                        )
                    }
                }
            }

            // 付款 App
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(settings.payApps, key = { it.pkg }) { app ->
                    PayAppButton(app.pkg, app.label) { StoreApps.launch(context, app.pkg, app.label) }
                }
                item(key = "__add") {
                    Column(
                        Modifier
                            .width(58.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onSetup)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .border(1.dp, Hud.LineBright, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("＋", color = Hud.Cyan, fontSize = 18.sp) }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (settings.payApps.isEmpty()) "加付款App" else "編輯",
                            fontSize = 10.sp,
                            color = Hud.TextDim,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Hud.Surface2, RoundedCornerShape(8.dp))
            .border(1.dp, Hud.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MonoLabel.copy(fontSize = 9.sp), color = Hud.Cyan)
        Column { content() }
    }
}

@Composable
private fun PayAppButton(pkg: String, label: String, onClick: () -> Unit) {
    val icon = rememberAppIcon(pkg)
    Column(
        Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(
                icon,
                label,
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .border(1.dp, Hud.Line, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(label.take(1), color = Hud.TextDim) }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = Hud.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
