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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

class PulseHUDService : Service() {

    companion object {
        const val ACTION_SHOW_BUBBLE  = "com.pulsestock.ACTION_SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE  = "com.pulsestock.ACTION_HIDE_BUBBLE"
        const val ACTION_STOP         = "com.pulsestock.ACTION_STOP"

        const val NOTIFICATION_ID = 7001
        const val CHANNEL_ID      = "pulse_bubble"

        val bubbleRunning = MutableStateFlow(false)

        /** True while the service is connected via WebSocket (real-time mode). */
        val isStreaming   = MutableStateFlow(false)

        /** Epoch-ms of the last completed REST poll; 0 = never polled this session. */
        val lastRefreshMs = MutableStateFlow(0L)

        val isRunning get() = bubbleRunning.value
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

    /** True while the bubble is being dragged over the trash zone. */
    private val trashHovered = MutableStateFlow(false)

    /** True while a finger is down on the bubble (press or drag). */
    private val bubblePressed = MutableStateFlow(false)

    // ── Service lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_BUBBLE             -> showBubble()
            ACTION_HIDE_BUBBLE, ACTION_STOP -> hideBubble()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideBubble()
        streamManager.close()
        serviceScope.cancel()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    // ── Stream / foreground ──────────────────────────────────────────────────

    private fun ensureStreamAndForeground() {
        if (symbolsJob != null) return

        val notification = buildNotification()
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

                // Stream (WebSocket) when popup is open; REST-poll every 60s otherwise
                // so prices are never too stale when the popup opens next time.
                var wasStreaming = false
                popupVisible
                    .collectLatest { popupOpen ->
                        if (popupOpen) {
                            wasStreaming = true
                            isStreaming.value = true
                            streamManager.stopPolling()
                            streamManager.startStreaming(symbols, serviceScope)
                        } else {
                            isStreaming.value = false
                            if (wasStreaming) kotlinx.coroutines.delay(60_000L)
                            wasStreaming = false
                            streamManager.stopStreaming()
                            streamManager.startPolling(symbols, serviceScope)
                        }
                    }
            }
        }

        // Haptic tick on each price update while popup is open.
        serviceScope.launch {
            streamManager.snapshot.collect { triggerHapticTick() }
        }

        serviceScope.launch {
            streamManager.lastRestRefreshMs.collect { ms -> lastRefreshMs.value = ms }
        }

        // Self-stop: if bubbleRunning drops to false the service cleans up.
        serviceScope.launch {
            bubbleRunning
                .collect { running -> if (!running) maybeStopService() }
        }
    }

    private fun maybeStopService() {
        if (!bubbleRunning.value) {
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
        val trashRadiusPx = (72 * density).toInt()
        val trashCentreX  = screenWidth / 2f
        // Inset from bottom: overlay sits at Gravity.BOTTOM, height=160dp, circle centred at 80dp from bottom.
        // Use windowManager display bounds so nav bar insets don't skew the hit target.
        val trashCentreY  = screenHeight - (80 * density)

        val wrapper = object : android.widget.FrameLayout(this@PulseHUDService) {
            private val longPressHandler = Handler(Looper.getMainLooper())
            private var downX           = 0f
            private var downY           = 0f
            private var downParamX      = 0
            private var downParamY      = 0
            private var isDragging      = false
            private var isLongPress     = false
            private var wasOverTrash    = false
            private var snapAnimator: android.animation.ValueAnimator? = null

            private val longPressRunnable = Runnable {
                isLongPress = true
                val appIntent = Intent(this@PulseHUDService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(appIntent)
            }

            private fun snapToEdge() {
                val p = floatingIconParams ?: return
                val margin = (14 * density).toInt()
                val targetX = if (p.x + width / 2 < screenWidth / 2) margin
                              else screenWidth - width - margin
                snapAnimator?.cancel()
                val self = this
                snapAnimator = android.animation.ValueAnimator.ofInt(p.x, targetX).apply {
                    duration = 300
                    interpolator = android.view.animation.OvershootInterpolator(0.8f)
                    addUpdateListener { anim ->
                        p.x = anim.animatedValue as Int
                        if (self.isAttachedToWindow) windowManager.updateViewLayout(self, p)
                    }
                    start()
                }
            }

            override fun onInterceptTouchEvent(ev: MotionEvent) = true

            override fun onTouchEvent(event: MotionEvent): Boolean {
                val p = floatingIconParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX        = event.rawX
                        downY        = event.rawY
                        downParamX   = p.x
                        downParamY   = p.y
                        isDragging   = false
                        isLongPress  = false
                        wasOverTrash = false
                        bubblePressed.value = true
                        snapAnimator?.cancel()
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
                            p.x = (downParamX + dx.toInt()).coerceIn(0, screenWidth - width)
                            p.y = (downParamY + dy.toInt()).coerceIn(0, screenHeight - height)
                            if (floatingIcon?.isAttachedToWindow == true) {
                                windowManager.updateViewLayout(this, p)
                            }
                            val iconCentreX = p.x + (width / 2f)
                            val iconCentreY = p.y + (height / 2f)
                            val overTrash = hypot(iconCentreX - trashCentreX, iconCentreY - trashCentreY) < trashRadiusPx
                            if (overTrash && !wasOverTrash) triggerHapticTick()
                            wasOverTrash       = overTrash
                            trashHovered.value = overTrash
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        bubblePressed.value = false
                        val dismissNow   = isDragging && wasOverTrash
                        val wasDragging  = isDragging
                        val wasLongPress = isLongPress
                        hideTrashOverlay()
                        wasOverTrash = false
                        isDragging   = false
                        isLongPress  = false
                        when {
                            dismissNow   -> Handler(Looper.getMainLooper()).post { hideBubble() }
                            wasDragging  -> snapToEdge()
                            !wasLongPress -> togglePopup()
                        }
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
                val isPressed by bubblePressed.collectAsState()
                PulseStockTheme { FloatingIconContent(isPressed = isPressed) }
            }
        }
        wrapper.addView(composeView)
        windowManager.addView(wrapper, params)
        floatingIcon = wrapper
    }

    private fun removeFloatingIcon() {
        bubblePressed.value = false
        floatingIcon?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
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
            setContent {
                val hovered by trashHovered.collectAsState()
                PulseStockTheme { com.pulsestock.app.ui.TrashZoneContent(isHovered = hovered) }
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
        trashHovered.value = false
        trashOverlay?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
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
        popupView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        popupView = null
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "PulseStock Bubble",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Background service notification for the floating price bubble"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        ensureChannel()

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PulseHUDService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse_tile)
            .setContentTitle("PulseStock")
            .setContentText("Tap the bubble to view prices")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .addAction(Notification.Action.Builder(null, "Open", openIntent).build())
            .build()
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

}
