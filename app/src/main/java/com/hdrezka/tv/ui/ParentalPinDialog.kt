package com.hdrezka.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hdrezka.tv.data.ParentalStore
import kotlinx.coroutines.delay

/** PIN-экран, удобный для пульта: цифровая клавиатура вместо системной. */
@Composable
fun ParentalPinDialog(
    title: String = "Родительский контроль",
    subtitle: String = "Введите PIN-код",
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    fun submit(entered: String) {
        if (ParentalStore.verify(context, entered)) {
            onSuccess()
        } else {
            error = true
            pin = ""
        }
    }

    fun add(digit: String) {
        if (pin.length >= 4) return
        val next = pin + digit
        pin = next
        error = false
        if (next.length == 4) submit(next)
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
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
                    .width(430.dp)
                    .background(SurfaceDark, RoundedCornerShape(18.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    if (error) "Неверный PIN. Попробуйте ещё раз." else subtitle,
                    color = if (error) Color(0xFFFF7070) else TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Row(
                    Modifier.padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(
                                    if (index < pin.length) Accent else Color(0xFF2C2C36),
                                    CircleShape
                                )
                        )
                    }
                }
                val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (col in 0 until 3) {
                            val digit = digits[row * 3 + col]
                            val requester = if (row == 0 && col == 0) firstFocus else null
                            TvButton(
                                text = digit,
                                onClick = { add(digit) },
                                modifier = Modifier
                                    .width(74.dp)
                                    .then(
                                        if (requester != null) Modifier.focusRequester(requester)
                                        else Modifier
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(
                        text = "0",
                        onClick = { add("0") },
                        modifier = Modifier.width(74.dp)
                    )
                    TvButton(
                        text = "Стереть",
                        onClick = {
                            pin = pin.dropLast(1)
                            error = false
                        },
                        modifier = Modifier.width(110.dp)
                    )
                    TvButton(
                        text = "Отмена",
                        onClick = onDismiss,
                        modifier = Modifier.width(110.dp)
                    )
                }
            }
        }
    }
}

/** Установка нового PIN: ввод и подтверждение. */
@Composable
fun ParentalPinSetupDialog(
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var first by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    fun add(digit: String) {
        if (pin.length >= 4) return
        val next = pin + digit
        pin = next
        error = false
        if (next.length == 4) {
            if (!confirming) {
                first = next
                pin = ""
                confirming = true
            } else if (next == first) {
                ParentalStore.setPin(context, next)
                onDone()
            } else {
                error = true
                pin = ""
                first = ""
                confirming = false
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
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
                    .width(430.dp)
                    .background(SurfaceDark, RoundedCornerShape(18.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(30.dp)
                )
                Text(
                    "Новый PIN",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    when {
                        error -> "PIN не совпал. Начните заново."
                        confirming -> "Повторите PIN"
                        else -> "Введите 4 цифры"
                    },
                    color = if (error) Color(0xFFFF7070) else TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Row(
                    Modifier.padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(
                                    if (index < pin.length) Accent else Color(0xFF2C2C36),
                                    CircleShape
                                )
                        )
                    }
                }
                val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (col in 0 until 3) {
                            val digit = digits[row * 3 + col]
                            val requester = if (row == 0 && col == 0) firstFocus else null
                            TvButton(
                                text = digit,
                                onClick = { add(digit) },
                                modifier = Modifier
                                    .width(74.dp)
                                    .then(
                                        if (requester != null) Modifier.focusRequester(requester)
                                        else Modifier
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(
                        text = "0",
                        onClick = { add("0") },
                        modifier = Modifier.width(74.dp)
                    )
                    TvButton(
                        text = "Стереть",
                        onClick = {
                            pin = pin.dropLast(1)
                            error = false
                        },
                        modifier = Modifier.width(110.dp)
                    )
                    TvButton(
                        text = "Отмена",
                        onClick = onDismiss,
                        modifier = Modifier.width(110.dp)
                    )
                }
            }
        }
    }
}
