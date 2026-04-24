package com.pulsestock.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pulsestock.app.ui.SettingsScreen
import com.pulsestock.app.ui.theme.PulseGreen
import com.pulsestock.app.ui.theme.PulseStockTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseStockTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainContent()
                }
            }
        }
    }
}

@Composable
private fun MainContent() {
    val context = LocalContext.current

    // ── Overlay permission state ─────────────────────────────────────────────
    var hasOverlay by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    // ── POST_NOTIFICATIONS permission (API 33+) ──────────────────────────────
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPermission = granted }

    // Show permission banners at the top if anything is missing
    if (!hasOverlay || !hasNotifPermission) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionBanner(
                    text        = "Notification permission required for the live status bar chip.",
                    buttonLabel = "Grant"
                ) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (!hasOverlay) {
                PermissionBanner(
                    text        = "\"Appear on top\" permission required to show the HUD overlay.",
                    buttonLabel = "Open Settings"
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data  = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                    // Will re-check on next resume
                    hasOverlay = Settings.canDrawOverlays(context)
                }
            }

            Spacer(Modifier.height(16.dp))
            SettingsScreen()
        }
    } else {
        SettingsScreen()
    }
}

@Composable
private fun PermissionBanner(text: String, buttonLabel: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF555555))
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onClick,
            colors  = ButtonDefaults.buttonColors(containerColor = PulseGreen)
        ) {
            Text(buttonLabel, color = Color.White)
        }
    }
}
