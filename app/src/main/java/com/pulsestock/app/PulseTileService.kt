package com.pulsestock.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that starts/stops PulseHUDService.
 *
 * Flow:
 *  1. User adds "PulseStock" tile to their Quick Settings panel.
 *  2. Tapping the tile:
 *     a. If SYSTEM_ALERT_WINDOW is not granted → opens the permission screen.
 *     b. If granted and HUD is off → starts PulseHUDService (foreground).
 *     c. If granted and HUD is on  → stops PulseHUDService.
 */
class PulseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            // Permission not granted — send user to settings.
            // Android 14+ (API 34): startActivityAndCollapse(Intent) throws
            // UnsupportedOperationException; must use PendingIntent overload.
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent)
            } else {
                startActivityAndCollapse(intent)
            }
            return
        }

        if (PulseHUDService.isRunning) {
            stopHUD()
        } else {
            startHUD()
        }

        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun startHUD() {
        val intent = Intent(this, PulseHUDService::class.java)
            .setAction(PulseHUDService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopHUD() {
        startService(
            Intent(this, PulseHUDService::class.java)
                .setAction(PulseHUDService.ACTION_STOP)
        )
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        if (PulseHUDService.isRunning) {
            tile.state    = Tile.STATE_ACTIVE
            tile.subtitle = "Live"
        } else {
            tile.state    = Tile.STATE_INACTIVE
            tile.subtitle = "Tap to start"
        }
        tile.label = "PulseStock"
        tile.updateTile()
    }
}
