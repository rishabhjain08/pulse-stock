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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
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
import com.pulsestock.app.ui.FloatingIconContent
import com.pulsestock.app.ui.HUDContent
import com.pulsestock.app.ui.theme.PulseStockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.hypot

class PulseHUDService : Service() {

    companion object {
        const val ACTION_START_TILE   = "com.pulsestock.ACTION_START_TILE"
        const val ACTION_STOP_TILE    = "com.pulsestock.ACTION_STOP_TILE"
        const val ACTION_SHOW_BUBBLE  = "com.pulsestock.ACTION_SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE  = "com.pulsestock.ACTION_HIDE_BUBBLE"

        // Legacy actions kept for back-compat with PulseTileService
        const val ACTION_START = "com.pulsestock.ACTION_START"
        const val ACTION_STOP  = "com.pulsestock.ACTION_STOP"

        const val NOTIFICATION_ID = 7001
        const val CHANNEL_ID      = "pulse_hud_live"

        val tileRunning   = MutableStateFlow(false)
        val bubbleRunning = MutableStateFlow(false)

        /** Set by PulseTileService when the QS panel is open/closed. */
        val tileVisible   = MutableStateFlow(false)

        /** True while the service is connected via WebSocket (real-time mode). */
        val isStreaming   = MutableStateFlow(false)

        /** Epoch-ms of the last completed REST poll; 0 = never polled this session. */
        val lastRefreshMs = MutableStateFlow(0L)

        val isRunning    get() = tileRunning.value || bubbleRunning.value
        val runningState get() = tileRunning
    }

    private val serviceScope   = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val streamManager  = StockStreamManager()
    private val prefs          by lazy { StockPreferences(this) }
    private val lifecycleOwner = ServiceLifecycleOwner()

    private lateinit var windowManager: WindowManager
    private var floatingIcon: View?               = null
    private var floatingIconParams: LayoutParams? = null
    private var trashOverlay: View?               = null
    private var popupView: View?                  = null
    private var symbolsJob: Job?                  = null
    private var currentSymbols                    = emptyList<String>()

    /** True while the price popup is expanded over the screen. */
    private val popupVisible = MutableStateFlow(false)

    // ── Service lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TILE, ACTION_START -> startTile()
            ACTION_STOP_TILE               -> stopTile()
            ACTION_SHOW_BUBBLE             -> showBubble()
            ACTION_HIDE_BUBBLE             -> hideBubble()
            ACTION_STOP                    -> { stopTile(); hideBubble() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopTile()
        hideBubble()
        streamManager.close()
        serviceScope.cancel()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Tile mode ────────────────────────────────────────────────────────────

    private fun startTile() {
        if (tileRunning.value) return
        tileRunning.value = true
        ensureStreamAndForeground()
        serviceScope.launch { prefs.setTileActive(true) }
    }

    private fun stopTile() {
        tileRunning.value = false
        serviceScope.launch { prefs.setTileActive(false) }
        maybeStopService()
    }

    // ── Bubble mode ──────────────────────────────────────────────────────────

    private fun showBubble() {
        if (bubbleRunning.value) return
        if (!Settings.canDrawOverlays(this)) return
        bubbleRunning.value = true
        serviceScope.launch { prefs.setBubbleActive(true) }
        ensureStreamAndForeground()
        showFloatingIcon()
        showPopup()
    }

    private fun hideBubble() {
        bubbleRunning.value = false
        serviceScope.launch { prefs.setBubbleActive(false) }
        hidePopup()
        removeFloatingIcon()
        maybeStopService()
    }

    // ── Shared stream / foreground ───────────────────────────────────────────

    private fun ensureStreamAndForeground() {
        if (symbolsJob != null) return

        val notification = buildNotification("Connecting…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        // Outer loop: restart everything when the watchlist changes.
        symbolsJob = serviceScope.launch {
            prefs.watchedSymbols.collectLatest { symbols ->
                currentSymbols = symbols

                // Inner loop: switch between WebSocket and REST polling based on
                // whether anyone is actively looking at prices.
                //   shouldStream = popup is open  OR  tile panel is open (and tile is active)
                var wasStreaming = false
                combine(
                    popupVisible,
                    tileVisible,
                    tileRunning
                ) { popup, tile, tileOn -> popup || (tile && tileOn) }
                    .distinctUntilChanged()
                    .collectLatest { shouldStream ->
                        if (shouldStream) {
                            wasStreaming = true
                            isStreaming.value = true
                            streamManager.stopPolling()
                            streamManager.startStreaming(symbols, serviceScope)
                        } else {
                            isStreaming.value = false
                            // Only linger on WebSocket if we were actively streaming before.
                            // On cold start (tile just enabled, panel closed) poll immediately
                            // so prices appear in seconds, not after a 60 s blank screen.
                            if (wasStreaming) kotlinx.coroutines.delay(60_000L)
                            wasStreaming = false
                            streamManager.stopStreaming()
                            streamManager.startPolling(symbols, serviceScope)
                        }
                    }
            }
        }

        serviceScope.launch {
            streamManager.snapshot.collect { snap ->
                val lines = currentSymbols.take(5).mapNotNull { sym ->
                    val p = snap.prices[sym] ?: return@mapNotNull null
                    val b = snap.baselines[sym]
                    val arrow = if (b == null) "" else if (p >= b) " ↑" else " ↓"
                    "${tickerOnly(sym)} \$${"%.2f".format(p)}$arrow"
                }
                if (lines.isNotEmpty()) updateNotification(lines.joinToString("  ·  "))
                triggerHapticTick()
            }
        }

        // Forward the stream manager's REST poll timestamp to the companion so the UI can show
        // how long ago the last 60s refresh happened.
        serviceScope.launch {
            streamManager.lastRestRefreshMs.collect { ms -> lastRefreshMs.value = ms }
        }

        // Self-stop: if both flags drop to false (e.g. tile removed by Samsung without reliable
        // intent delivery) the service stops itself rather than leaking in the background.
        serviceScope.launch {
            combine(tileRunning, bubbleRunning) { t, b -> t || b }
                .distinctUntilChanged()
                .collect { anyRunning -> if (!anyRunning) maybeStopService() }
        }
    }

    private fun maybeStopService() {
        if (!tileRunning.value && !bubbleRunning.value) {
            isStreaming.value   = false
            lastRefreshMs.value = 0L
            symbolsJob?.cancel()
            symbolsJob = null
            streamManager.stopAll()
            lifecycleOwner.onPause()
            lifecycleOwner.onStop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Floating icon ────────────────────────────────────────────────────────

    private fun showFloatingIcon() {
        if (floatingIcon != null) return
        val density     = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels

        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - (72 * density).toInt()
            y = (200 * density).toInt()
        }
        floatingIconParams = params

        val screenHeight = resources.displayMetrics.heightPixels
        // Trash zone: bottom-centre, radius 60dp
        val trashRadiusPx = (60 * density).toInt()
        val trashCentreX  = screenWidth / 2f
        val trashCentreY  = screenHeight - (80 * density)

        val wrapper = object : android.widget.FrameLayout(this@PulseHUDService) {
            private val longPressHandler = Handler(Looper.getMainLooper())
            private var downX      = 0f
            private var downY      = 0f
            private var downParamX = 0
            private var downParamY = 0
            private var isDragging = false
            private var isLongPress = false

            // Long press → open app (600 ms)
            private val longPressRunnable = Runnable {
                isLongPress = true
                val appIntent = Intent(this@PulseHUDService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(appIntent)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent) = true

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val p = floatingIconParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX       = event.rawX
                        downY       = event.rawY
                        downParamX  = p.x
                        downParamY  = p.y
                        isDragging  = false
                        isLongPress = false
                        longPressHandler.postDelayed(longPressRunnable, 600L)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!isDragging && (abs(dx) > 10 || abs(dy) > 10)) {
                            isDragging = true
                            longPressHandler.removeCallbacks(longPressRunnable)
                            showTrashOverlay()
                        }
                        if (isDragging) {
                            p.x = downParamX + dx.toInt()
                            p.y = downParamY + dy.toInt()
                            if (floatingIcon?.isAttachedToWindow == true) {
                                windowManager.updateViewLayout(this, p)
                            }
                            // Highlight trash zone when bubble hovers over it
                            val iconCentreX = p.x + (width / 2f)
                            val iconCentreY = p.y + (height / 2f)
                            val overTrash = hypot(iconCentreX - trashCentreX, iconCentreY - trashCentreY) < trashRadiusPx
                            trashOverlay?.alpha = if (overTrash) 1f else 0.6f
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        if (isDragging) {
                            hideTrashOverlay()
                            // Drop on trash → hide bubble, keep market data if tile is active
                            val iconCentreX = p.x + (width / 2f)
                            val iconCentreY = p.y + (height / 2f)
                            if (hypot(iconCentreX - trashCentreX, iconCentreY - trashCentreY) < trashRadiusPx) {
                                hideBubble()
                                return true
                            }
                        } else if (!isLongPress) {
                            togglePopup()
                        }
                        isDragging  = false
                        isLongPress = false
                        return true
                    }
                }
                return false
            }
        }

        wrapper.setViewTreeLifecycleOwner(lifecycleOwner)
        wrapper.setViewTreeViewModelStoreOwner(lifecycleOwner)
        wrapper.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        val composeView = ComposeView(this).apply {
            setContent {
                PulseStockTheme { FloatingIconContent() }
            }
        }
        wrapper.addView(composeView)
        windowManager.addView(wrapper, params)
        floatingIcon = wrapper
    }

    private fun removeFloatingIcon() {
        floatingIcon?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        floatingIcon       = null
        floatingIconParams = null
        hideTrashOverlay()
    }

    // ── Trash overlay ─────────────────────────────────────────────────────────

    private fun showTrashOverlay() {
        if (trashOverlay != null) return
        val density = resources.displayMetrics.density

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            (160 * density).toInt(),
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        val view = ComposeView(this).apply {
            alpha = 0.6f
            setContent {
                PulseStockTheme { com.pulsestock.app.ui.TrashZoneContent() }
            }
        }

        val wrapper = android.widget.FrameLayout(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            addView(view)
        }

        windowManager.addView(wrapper, params)
        trashOverlay = wrapper
    }

    private fun hideTrashOverlay() {
        trashOverlay?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        trashOverlay = null
    }

    // ── Stock popup ──────────────────────────────────────────────────────────

    private fun togglePopup() {
        if (popupView == null) showPopup() else hidePopup()
    }

    private fun showPopup() {
        if (popupView != null) return
        popupVisible.value = true

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE
                    or LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        val composeView = ComposeView(this).apply {
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
                        onDismiss       = ::hidePopup
                    )
                }
            }
        }

        val touchWrapper = object : android.widget.FrameLayout(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) { hidePopup(); return true }
                return super.onTouchEvent(event)
            }
        }
        touchWrapper.setViewTreeLifecycleOwner(lifecycleOwner)
        touchWrapper.setViewTreeViewModelStoreOwner(lifecycleOwner)
        touchWrapper.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        touchWrapper.addView(composeView)
        windowManager.addView(touchWrapper, params)
        popupView = touchWrapper
    }

    private fun hidePopup() {
        popupVisible.value = false
        popupView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        popupView = null
    }

    // ── Notification ─────────────────────────────────────────────────────────

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

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse_tile)
            .setContentTitle("PulseStock · Live Prices")
            .setContentText(contentText)
            .setOngoing(true)
            .setStyle(Notification.BigTextStyle().bigText(contentText))

        builder.addAction(Notification.Action.Builder(
            null, "Stop",
            PendingIntent.getService(this, 1,
                Intent(this, PulseHUDService::class.java).setAction(ACTION_STOP_TILE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ).build())

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        builder.addAction(Notification.Action.Builder(
            null, "Open",
            PendingIntent.getActivity(this, 2, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ).build())

        if (Build.VERSION.SDK_INT >= 36) applyNowBarExtras(builder, contentText)

        return builder.build()
    }

    @RequiresApi(36)
    private fun applyNowBarExtras(builder: Notification.Builder, text: String) {
        builder.setShortCriticalText(text)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.25f)
                    .compose()
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns only the ticker part for display (NASDAQ:AAPL → AAPL). */
    private fun tickerOnly(symbol: String) = symbol.substringAfterLast(':')
}
