package com.even.membercards.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.even.membercards.data.PhoneEntry
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

/** 全螢幕白底條碼；一維條碼轉成橫向，讓條碼盡量大。 */
@Composable
fun FullscreenBarcode(
    bitmap: ImageBitmap,
    number: String,
    rotate: Boolean,
    onClose: () -> Unit,
    title: String? = null,
) {
    BackHandler { onClose() }
    KeepScreenBright()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        if (rotate) {
            Column(
                Modifier
                    .requiredSize(width = maxHeight * 0.86f, height = maxWidth * 0.78f)
                    .rotate(90f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (title != null) {
                    Text(title, color = Color.DarkGray, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                }
                Image(
                    bitmap,
                    "條碼",
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(Modifier.height(10.dp))
                Text(number, fontFamily = Mono, fontSize = 26.sp, letterSpacing = 3.sp, color = Color.Black)
            }
        } else {
            val side = min(maxWidth, maxHeight) * 0.86f
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (title != null) {
                    Text(title, color = Color.DarkGray, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                }
                Image(
                    bitmap,
                    "條碼",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(side),
                )
                Spacer(Modifier.height(16.dp))
                Text(number, fontFamily = Mono, fontSize = 22.sp, letterSpacing = 2.sp, color = Color.Black)
            }
        }
        Text(
            "點任意處關閉",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }
}

/** 全螢幕大字（例如手機號碼給店員看）。 */
@Composable
fun FullscreenText(title: String, text: String, onClose: () -> Unit) {
    BackHandler { onClose() }
    KeepScreenBright()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .requiredSize(width = maxHeight * 0.9f, height = maxWidth * 0.8f)
                .rotate(90f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, color = Color.DarkGray, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
                letterSpacing = 4.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Text(
            "點任意處關閉",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }
}

/** 全螢幕電話號碼（橫向大字）；有多個號碼時下方可切換。 */
@Composable
fun FullscreenPhones(entries: List<PhoneEntry>, startIndex: Int = 0, onClose: () -> Unit) {
    BackHandler { onClose() }
    KeepScreenBright()
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) }
    val entry = entries.getOrNull(index) ?: return
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .requiredSize(width = maxHeight * 0.9f, height = maxWidth * 0.84f)
                .rotate(90f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                entry.label.ifBlank { if (index == 0) "會員手機號碼" else "備選號碼" },
                color = Color.DarkGray,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                formatPhone(entry.number),
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 60.sp,
                letterSpacing = 4.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (entries.size > 1) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    entries.forEachIndexed { i, e ->
                        val sel = i == index
                        Text(
                            e.label.ifBlank { if (i == 0) "主要" else "備選 $i" },
                            color = if (sel) Color.White else Color.Black,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) Color.Black else Color(0xFFEDEDED))
                                .clickable { index = i }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Text(
            if (entries.size > 1) "點標籤切換號碼 · 點其他地方關閉" else "點任意處關閉",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 顯示條碼時把螢幕調到最亮並保持常亮，離開時還原。 */
@Composable
fun KeepScreenBright() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val lp = window.attributes
        val old = lp.screenBrightness
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            val l = window.attributes
            l.screenBrightness = old
            window.attributes = l
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
