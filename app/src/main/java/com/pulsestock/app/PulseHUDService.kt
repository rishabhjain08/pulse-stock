package com.pulsestock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pulsestock.app.data.StockPreferences
import com.pulsestock.app.data.StockStreamManager
import com.pulsestock.app.ui.HUDContent
import com.pulsestock.app.ui.theme.PulseStockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PulseHUDService : Service() {

    // ── Constants ────────────────────────────────────────────────────────────
    companion object {
        const val ACTION_START = "com.pulsestock.ACTION_START"
        const val ACTION_STOP  = "com.pulsestock.ACTION_STOP"
        const val NOTIFICATION_ID = 7001
        const val CHANNEL_ID      = "pulse_hud_live"

        /** True while the HUD is visible; read by PulseTileService to reflect tile state. */
        @Volatile var isRunning = false
            private set
    }

    // ── State ────────────────────────────────────────────────────────────────
    private val serviceScope  = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val streamManager = StockStreamManager()
    private val prefs         by lazy { StockPreferences(this) }
    private val lifecycleOwner = ServiceLifecycleOwner()

    private lateinit var windowManager: WindowManager
    private var overlayRoot: View? = null
    private var symbolsJob: Job?   = null
    private var currentSymbols     = emptyList<String>()

    // ── Service lifecycle ────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startHUD()
            ACTION_STOP  -> { stopHUD(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopHUD()
        streamManager.close()
        serviceScope.cancel()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── HUD lifecycle ────────────────────────────────────────────────────────
    private fun startHUD() {
        if (isRunning) return
        isRunning = true

        // Start foreground with notification
        val notification = buildNotification("Connecting…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Attach lifecycle for ComposeView
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        // Observe symbol list and reconnect whenever it changes
        symbolsJob = serviceScope.launch {
            prefs.watchedSymbols.collectLatest { symbols ->
                currentSymbols = symbols
                streamManager.disconnect()
                streamManager.connect(symbols, serviceScope)
            }
        }

        // Drive notification + haptics from price updates
        serviceScope.launch {
            streamManager.snapshot.collect { snap ->
                val topSymbol = currentSymbols.firstOrNull() ?: return@collect
                val topPrice  = snap.prices[topSymbol] ?: return@collect
                val text      = "$topSymbol  \$${"%.2f".format(topPrice)}"
                updateNotification(text)
                triggerHapticTick()
            }
        }

        showOverlay()
    }

    private fun stopHUD() {
        if (!isRunning) return
        isRunning = false

        symbolsJob?.cancel()
        streamManager.disconnect()
        removeOverlay()
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ── WindowManager overlay ────────────────────────────────────────────────
    private fun showOverlay() {
        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_TOUCH_MODAL: touches outside the card pass through to the app below
            // WATCH_OUTSIDE_TOUCH: we receive ACTION_OUTSIDE to implement "tap outside = dismiss"
            LayoutParams.FLAG_NOT_FOCUSABLE
                    or LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80   // lift off the navigation bar
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                val snap  by streamManager.snapshot.collectAsState(
                    initial = StockStreamManager.PriceSnapshot(emptyMap(), emptyMap())
                )
                val state by streamManager.connectionState.collectAsState()
                val syms  by prefs.watchedSymbols.collectAsState(
                    initial = StockPreferences.DEFAULT_SYMBOLS
                )

                PulseStockTheme {
                    HUDContent(
                        symbols         = syms,
                        snapshot        = snap,
                        connectionState = state,
                        onDismiss       = { stopHUD(); stopSelf() }
                    )
                }
            }
        }

        // Intercept touches outside the card to dismiss the HUD
        val touchWrapper = object : android.widget.FrameLayout(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    stopHUD()
                    stopSelf()
                    return true
                }
                return super.onTouchEvent(event)
            }
        }
        touchWrapper.addView(composeView)

        windowManager.addView(touchWrapper, params)
        overlayRoot = touchWrapper
    }

    private fun removeOverlay() {
        overlayRoot?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
            overlayRoot = null
        }
    }

    // ── Notification (foreground + Now Bar) ──────────────────────────────────
    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(contentText: String): Notification {
        ensureChannel()

        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, PulseHUDService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse_tile)
            .setContentTitle("PulseStock")
            .setContentText(contentText)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, "Stop", stopPi
                ).build()
            )

        // ── Android 16 (API 36): Now Bar / Status Bar Live Update ────────────
        // No try-catch — we guard with a hard SDK version check.
        // setShortCriticalText() places the price next to the system clock in the Now Bar.
        // android.requestPromotedOngoing promotes it out of the shade into the chip UI.
        if (Build.VERSION.SDK_INT >= 36) {
            applyNowBarExtras(builder, contentText)
        }

        return builder.build()
    }

    @RequiresApi(36)
    private fun applyNowBarExtras(builder: Notification.Builder, text: String) {
        builder.setShortCriticalText(text)
        // Request promotion to the Android 16 "Now Bar" chip next to the clock
        builder.extras.putBoolean("android.requestPromotedOngoing", true)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── Haptics ───────────────────────────────────────────────────────────────
    private fun triggerHapticTick() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // VibrationEffect.Composition (PRIMITIVE_LOW_TICK) requires API 30
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.25f)
                .compose()
            vibrator.vibrate(effect)
        }
    }
}
