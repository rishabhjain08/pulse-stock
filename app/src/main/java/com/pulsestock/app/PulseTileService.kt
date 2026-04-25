package com.pulsestock.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile for PulseStock.
 *
 * - Tap:       toggle live prices on/off in the tile (independent of the bubble)
 * - Long press: open the PulseStock app
 *
 * The tile and bubble are fully independent — both are backed by the same
 * live data stream but can be started and stopped separately from the app.
 */
class PulseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            // Need overlay permission before any UI can show — send user to settings.
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        if (PulseHUDService.tileRunning.value) {
            sendAction(PulseHUDService.ACTION_STOP_TILE)
        } else {
            sendForegroundAction(PulseHUDService.ACTION_START_TILE)
        }

        refreshTile()
    }

    override fun onStopListening() = Unit

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun refreshTile() {
        val tile = qsTile ?: return
        if (PulseHUDService.tileRunning.value) {
            tile.state    = Tile.STATE_ACTIVE
            tile.subtitle = "Live prices on"
        } else {
            tile.state    = Tile.STATE_INACTIVE
            tile.subtitle = "Tap to enable"
        }
        tile.label = "PulseStock"
        tile.updateTile()
    }

    private fun sendAction(action: String) {
        startService(Intent(this, PulseHUDService::class.java).setAction(action))
    }

    private fun sendForegroundAction(action: String) {
        val intent = Intent(this, PulseHUDService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
