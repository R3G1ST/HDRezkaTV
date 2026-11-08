package com.hdrezka.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdrezka.tv.data.AccountInfo
import com.hdrezka.tv.data.AccountStore
import com.hdrezka.tv.data.HdRezkaClient
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (AccountInfo) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loginFocus = rememberInitialFocus()
    val focusManager = LocalFocusManager.current

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy || login.isBlank() || password.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                val account = HdRezkaClient.login(login.trim(), password)
                AccountStore.save(context, account)
                onLoggedIn(account)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            busy = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(620.dp)
                .background(SurfaceDark, RoundedCornerShape(18.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Вход в HDrezka",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Аккаунт нужен для 2K/4K, избранного и синхронизации.",
                color = TextSecondary,
                fontSize = 14.sp
            )

            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                label = { Text("E-mail или логин") },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        } else {
                            false
                        }
                    }
                    .focusRequester(loginFocus),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        } else {
                            false
                        }
                    }
            )

            if (error != null) {
                Text(
                    error!!,
                    color = AccentWarm,
                    fontSize = 12.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    text = if (busy) "Вход…" else "Войти",
                    onClick = { if (!busy) submit() },
                    selected = true,
                    large = true
                )
                TvButton(text = "Назад", onClick = onBack, large = true)
            }

            Spacer(Modifier.height(2.dp))
            Text(
                "Пароль не сохраняется — только cookie сессии.",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}
