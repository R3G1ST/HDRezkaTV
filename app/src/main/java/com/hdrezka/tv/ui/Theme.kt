package com.hdrezka.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF7A18)
val AccentWarm = Color(0xFFFFB15C)
val Background = Color(0xFF0B0B0D)
val SurfaceDark = Color(0xFF16161A)
val SurfaceStrong = Color(0xFF222229)
val Hairline = Color(0xFF2C2C34)
val TextSecondary = Color(0xFFA7A7B0)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentWarm,
    background = Background,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceStrong,
    onSurfaceVariant = TextSecondary
)

@Composable
fun HDRezkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content
    )
}
