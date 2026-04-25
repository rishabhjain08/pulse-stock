package com.pulsestock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import com.pulsestock.app.ui.SettingsScreen
import com.pulsestock.app.ui.theme.PulseStockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let Compose handle all insets (including IME) rather than the window.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PulseStockTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen()
                }
            }
        }
    }
}
