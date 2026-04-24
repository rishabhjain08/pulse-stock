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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class PulseHUDService : Service() {

    companion object {
        const val ACTION_START    = "com.pulsestock.ACTION_START"
        const val ACTION_STOP     = "com.pulsestock.ACTION_STOP"
        const val NOTIFICATION_ID = 7001
        const val CHANNEL_ID      = "pulse_hud_live"

        /** Observed by SettingsScreen to reflect the Start/Stop button state. */
        val runningState = MutableStateFlow(false)
        val isRunning get() = runningState.value
    }

    private val serviceScope   = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val streamManager  = StockStreamManager()
    private val prefs          by lazy { StockPreferences(this) }
    private val lifecycleOwner = ServiceLifecycleOwner()

    private lateinit var windowManager: WindowManager
    private var floatingIcon: View?               = null
    private var floatingIconParams: LayoutParams? = null
    private var popupView: View?                  = null
    private var symbolsJob: Job?                  = null
    private var currentSymbols                    = emptyList<String>()
    private val showIconHint                      = MutableStateFlow(true)

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
        if (runningState.value) return
        runningState.value = true

        val notification = buildNotification("Connecting…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        symbolsJob = serviceScope.launch {
            prefs.watchedSymbols.collectLatest { symbols ->
                currentSymbols = symbols
                streamManager.disconnect()
                streamManager.connect(symbols, serviceScope)
            }
        }

        serviceScope.launch {
            streamManager.snapshot.collect { snap ->
                val topSymbol = currentSymbols.firstOrNull() ?: return@collect
                val topPrice  = snap.prices[topSymbol] ?: return@collect
                updateNotification("$topSymbol  \$${"%.2f".format(topPrice)}")
                triggerHapticTick()
            }
        }

        // Show "Hold to stop" hint label for 3 seconds on first launch
        showIconHint.value = true
        serviceScope.launch {
            delay(3000L)
            showIconHint.value = false
        }

        showFloatingIcon()
        showPopup()  // open stock card immediately on first launch
    }

    private fun stopHUD() {
        if (!runningState.value) return
        runningState.value = false

        symbolsJob?.cancel()
        streamManager.disconnect()
        hidePopup()
        removeFloatingIcon()
        lifecycleOwner.onPause()
        lifecycleOwner.onStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ── Floating icon ────────────────────────────────────────────────────────
    private fun showFloatingIcon() {
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

        val wrapper = object : android.widget.FrameLayout(this@PulseHUDService) {
            private val longPressHandler = Handler(Looper.getMainLooper())
            private var downX       = 0f
            private var downY       = 0f
            private var downParamX  = 0
            private var downParamY  = 0
            private var isDragging  = false
            private var isLongPress = false

            private val longPressRunnable = Runnable {
                isLongPress = true
                stopHUD()
                stopSelf()
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
                        }
                        if (isDragging) {
                            p.x = downParamX + dx.toInt()
                            p.y = downParamY + dy.toInt()
                            if (floatingIcon?.isAttachedToWindow == true) {
                                windowManager.updateViewLayout(this, p)
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        if (!isDragging && !isLongPress) togglePopup()
                        isDragging  = false
                        isLongPress = false
                        return true
                    }
                }
                return false
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val hint by showIconHint.collectAsState()
                PulseStockTheme { FloatingIconContent(showHint = hint) }
            }
        }
        wrapper.addView(composeView)

        windowManager.addView(wrapper, params)
        floatingIcon = wrapper
    }

    private fun removeFloatingIcon() {
        floatingIcon?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        floatingIcon      = null
        floatingIconParams = null
    }

    // ── Stock popup ──────────────────────────────────────────────────────────
    private fun togglePopup() {
        if (popupView == null) showPopup() else hidePopup()
    }

    private fun showPopup() {
        if (popupView != null) return

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
                        onDismiss       = ::hidePopup  // collapses popup; service + WebSocket stay alive
                    )
                }
            }
        }

        // Outside tap collapses the popup only — does NOT stop the service
        val touchWrapper = object : android.widget.FrameLayout(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hidePopup()
                    return true
                }
                return super.onTouchEvent(event)
            }
        }
        touchWrapper.addView(composeView)
        windowManager.addView(touchWrapper, params)
        popupView = touchWrapper
    }

    private fun hidePopup() {
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
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())

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
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.25f)
                .compose()
            vibrator.vibrate(effect)
        }
    }
}
