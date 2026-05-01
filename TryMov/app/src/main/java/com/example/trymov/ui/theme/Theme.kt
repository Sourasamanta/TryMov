package com.example.trymov.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TryMovDarkColors = darkColorScheme(
    primary = Color(0xFFE8B84A),
    onPrimary = Color(0xFF1A0E00),
    primaryContainer = Color(0xFF273142),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF121821),
    onSurface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFF101722),
    onSurfaceVariant = Color(0xFF98A2B3),
    outline = Color(0xFF273142),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0000)
)

@Composable
fun TryMovTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TryMovDarkColors,
        typography = Typography,
        content = content
    )
}
