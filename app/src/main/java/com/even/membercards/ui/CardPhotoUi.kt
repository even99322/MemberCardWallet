package com.even.membercards.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.even.membercards.data.CardPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 讀取卡片照片（version 變動時重新讀）。 */
@Composable
fun rememberCardPhoto(cardId: String, side: String, version: Int, maxSide: Int = 800): ImageBitmap? {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, cardId, side, version) {
        value = withContext(Dispatchers.IO) { CardPhotos.load(context, cardId, side, maxSide)?.asImageBitmap() }
    }
    return bmp
}

/** 編輯頁的照片格：空的可拍照／選相簿，有照片可刪除。 */
@Composable
fun PhotoSlot(
    label: String,
    cardId: String,
    side: String,
    version: Int,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val photo = rememberCardPhoto(cardId, side, version, 600)

    fun saveFrom(uri: Uri) {
        if (CardPhotos.save(context, cardId, side, uri)) onChanged() else context.toast("照片讀取失敗")
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveFrom(uri)
    }
    val cameraUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", CardPhotos.cameraTemp(context))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) saveFrom(cameraUri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(cameraUri) else context.toast("需要相機權限才能拍照")
    }

    fun takePhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.launch(cameraUri)
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .clip(RoundedCornerShape(8.dp))
                .background(Hud.Surface)
                .border(1.dp, if (photo != null) Hud.LineBright else Hud.Line, RoundedCornerShape(8.dp))
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo, label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA000000))
                        .clickable {
                            CardPhotos.delete(context, cardId, side)
                            onChanged()
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "刪除照片", tint = Color.White, modifier = Modifier.size(16.dp)) }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("＋", color = Hud.Cyan, fontSize = 22.sp)
                    Text(label, color = Hud.TextDim, fontSize = 12.sp)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("拍照") }, onClick = { menu = false; takePhoto() })
            DropdownMenuItem(
                text = { Text("從相簿選擇") },
                onClick = {
                    menu = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

/** 全螢幕看照片（黑底、螢幕調亮）。 */
@Composable
fun FullscreenPhoto(cardId: String, side: String, onClose: () -> Unit) {
    BackHandler { onClose() }
    KeepScreenBright()
    val photo = rememberCardPhoto(cardId, side, 0, 2000)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
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
