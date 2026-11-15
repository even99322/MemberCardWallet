package com.even.membercards.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppLock {
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Android 9 以上用系統生物辨識（可退回螢幕鎖密碼）。 */
    fun authenticateModern(context: Context, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val builder = BiometricPrompt.Builder(context)
            .setTitle("解鎖會員卡夾")
            .setSubtitle("使用指紋、臉部或螢幕鎖驗證")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    // 手機已經沒有任何螢幕鎖時無法驗證，直接放行避免被鎖在外面
                    if (!isDeviceSecure(context)) onSuccess() else onFail(errString?.toString() ?: "驗證取消")
                }
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    // Android 8.x：用系統螢幕鎖確認畫面
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onUnlock()
    }

    fun unlock() {
        if (!AppLock.isDeviceSecure(context)) {
            onUnlock()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AppLock.authenticateModern(context, onUnlock) { /* 留在鎖定畫面 */ }
        } else {
            @Suppress("DEPRECATION")
            val intent = context.getSystemService(KeyguardManager::class.java)
                ?.createConfirmDeviceCredentialIntent("解鎖會員卡夾", null)
            if (intent == null) onUnlock() else legacyLauncher.launch(intent)
        }
    }

    LaunchedEffect(Unit) { unlock() }
    BackHandler { context.findActivity()?.moveTaskToBack(true) }

    Box(
        Modifier
            .fillMaxSize()
            .hudBackground(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .border(1.dp, Hud.Line, RoundedCornerShape(12.dp))
                    .cornerBrackets(Hud.Cyan, length = 14.dp, stroke = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, null, tint = Hud.Cyan, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("ACCESS LOCKED", style = MonoLabel.copy(fontSize = 12.sp, letterSpacing = 4.sp), color = Hud.Cyan)
            Spacer(Modifier.height(8.dp))
            Text("會員卡夾已鎖定", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Hud.Text)
            Spacer(Modifier.height(32.dp))
            HudButton("解鎖", onClick = { unlock() }, modifier = Modifier.fillMaxWidth(0.6f))
        }
    }
}
