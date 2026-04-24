package com.pulsestock.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PulseColorScheme = lightColorScheme(
    primary        = PulseGreen,
    onPrimary      = Color.White,
    secondary      = PulseGreen,
    onSecondary    = Color.White,
    error          = PulseRed,
    onError        = Color.White,
    background     = Color.White,
    onBackground   = PulseText,
    surface        = Color.White,
    onSurface      = PulseText,
    surfaceVariant = PulseSurface,
)

@Composable
fun PulseStockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PulseColorScheme,
        content     = content
    )
}
