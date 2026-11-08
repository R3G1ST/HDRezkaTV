package com.hdrezka.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hdrezka.tv.data.GithubOAuth
import com.hdrezka.tv.data.GithubSyncConfig
import com.hdrezka.tv.data.GithubSyncConfigStore
import kotlinx.coroutines.CancellationException

@Composable
fun GithubOAuthDialog(
    onConnected: (GithubSyncConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var device by remember { mutableStateOf<GithubOAuth.DeviceCode?>(null) }
    var status by remember { mutableStateOf("Получаем код…") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        status = "Получаем код…"
        device = null
        try {
            val code = GithubOAuth.requestDeviceCode()
            device = code
            status = "Ожидаем подтверждение…"
            val token = GithubOAuth.pollForToken(code.deviceCode, code.interval)
            status = "Создаём приватный репозиторий…"
            val login = GithubOAuth.createPrivateRepo(token.accessToken)
            val config = GithubSyncConfig(
                owner = login,
                repo = "hdrezka-sync",
                token = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAt = token.expiresAt
            )
            GithubSyncConfigStore.save(context, config)
            onConnected(config)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            status = ""
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE60B0B0D)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .width(620.dp)
                    .background(SurfaceDark, RoundedCornerShape(20.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Вход через GitHub",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Синхронизация истории между устройствами",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(14.dp))

                val current = device
                if (current != null) {
                    Text(
                        "1. Откройте на телефоне",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        current.verificationUri,
                        color = Accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "2. Введите код",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        current.userCode,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    QrCode(
                        url = current.verificationUri,
                        size = 150.dp
                    )
                    Text(
                        "Наведите камеру телефона на QR",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    CircularProgressIndicator(color = Accent)
                }

                Spacer(Modifier.height(10.dp))
                if (error != null) {
                    Text(
                        error!!,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(status, color = TextSecondary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (error != null) {
                        TvButton(
                            text = "Повторить",
                            primary = true,
                            onClick = { attempt++ }
                        )
                    }
                    TvButton(text = "Отмена", onClick = onDismiss)
                }
            }
        }
    }
}


@Composable
private fun QrCode(url: String, size: Dp) {
    val bitmap = remember(url) {
        runCatching {
            val pixels = 640
            val matrix = QRCodeWriter().encode(
                url,
                BarcodeFormat.QR_CODE,
                pixels,
                pixels
            )
            val bmp = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            for (x in 0 until pixels) {
                for (y in 0 until pixels) {
                    bmp.setPixel(
                        x,
                        y,
                        if (matrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )
                }
            }
            bmp
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR-код для входа через GitHub",
            modifier = Modifier
                .size(size)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(8.dp)
        )
    }
}
