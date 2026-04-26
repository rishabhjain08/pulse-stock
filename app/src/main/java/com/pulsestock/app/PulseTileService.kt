package com.pulsestock.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for PulseStock.
 *
 * Tap: show or hide the floating price bubble.
 * Long press: open the PulseStock app (via QS_TILE_PREFERENCES intent filter in manifest).
 */
class PulseTileService : TileService() {

    private val tileScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var stateObserver: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
        stateObserver = tileScope.launch {
            PulseHUDService.bubbleRunning.collect { refreshTile() }
        }
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
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

        if (PulseHUDService.bubbleRunning.value) {
            sendAction(PulseHUDService.ACTION_STOP)
        } else {
            sendForegroundAction(PulseHUDService.ACTION_SHOW_BUBBLE)
        }

        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        stateObserver?.cancel()
        stateObserver = null
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        PulseHUDService.bubbleRunning.value = false
        sendAction(PulseHUDService.ACTION_STOP)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun refreshTile() {
        val tile = qsTile ?: return
        if (PulseHUDService.bubbleRunning.value) {
            tile.state    = Tile.STATE_ACTIVE
            tile.subtitle = "Bubble on"
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
