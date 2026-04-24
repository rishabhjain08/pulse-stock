package com.pulsestock.app.data

import android.util.Log
import com.pulsestock.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

private const val TAG = "StockStream"
private const val FINNHUB_WS_URL = "wss://ws.finnhub.io?token="
private const val RECONNECT_DELAY_MS = 3_000L

/**
 * Manages the Finnhub WebSocket connection.
 *
 * Lifecycle contract (enforced by PulseHUDService):
 *   connect() when HUD becomes visible
 *   disconnect() immediately when HUD is dismissed
 *   close() only when the service is destroyed
 */
class StockStreamManager {

    sealed class ConnectionState {
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class PriceSnapshot(
        /** Latest price per symbol. */
        val prices: Map<String, Double>,
        /**
         * First-received price per symbol, used as the session baseline
         * to compute percentage change (not a true "day open" — for that
         * you'd call /api/v1/quote, which is a future enhancement).
         */
        val baselines: Map<String, Double>
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 20_000   // keepalive ping every 20 s
        }
    }

    private val _snapshot = MutableSharedFlow<PriceSnapshot>(
        replay = 1,
        extraBufferCapacity = 128   // absorb bursts without back-pressure on the WS thread
    )
    val snapshot: SharedFlow<PriceSnapshot> = _snapshot.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Mutable state updated on the IO thread
    private val prices = mutableMapOf<String, Double>()
    private val baselines = mutableMapOf<String, Double>()
    private var subscribedSymbols = emptyList<String>()

    private var socketJob: Job? = null

    // ── Public API ──────────────────────────────────────────────────────────

    fun connect(symbols: List<String>, scope: CoroutineScope) {
        if (symbols.isEmpty()) return
        subscribedSymbols = symbols
        socketJob?.cancel()
        socketJob = scope.launch(Dispatchers.IO) {
            connectLoop(symbols)
        }
    }

    fun disconnect() {
        socketJob?.cancel()
        socketJob = null
        prices.clear()
        baselines.clear()
        _connectionState.value = ConnectionState.Disconnected
    }

    /** Call only once when the hosting service is destroyed. */
    fun close() {
        disconnect()
        client.close()
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private suspend fun connectLoop(symbols: List<String>) {
        while (true) {
            _connectionState.value = ConnectionState.Connecting
            try {
                client.webSocket("$FINNHUB_WS_URL${BuildConfig.FINNHUB_API_KEY}") {
                    _connectionState.value = ConnectionState.Connected
                    Log.d(TAG, "Connected — subscribing to $symbols")

                    // Subscribe to each symbol
                    symbols.forEach { symbol ->
                        send("""{"type":"subscribe","symbol":"$symbol"}""")
                    }

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()

                        try {
                            val msg = json.decodeFromString<FinnhubMessage>(text)
                            when (msg.type) {
                                "trade" -> handleTrades(msg.data ?: emptyList(), symbols)
                                "error" -> Log.w(TAG, "Finnhub error: ${msg.msg}")
                                "ping"  -> { /* keepalive, ignore */ }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Intentional disconnect — do not reconnect
                _connectionState.value = ConnectionState.Disconnected
                return
            } catch (e: Exception) {
                Log.e(TAG, "WebSocket error: ${e.message}")
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            }

            // Wait before reconnecting (only if still active)
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return
            delay(RECONNECT_DELAY_MS)
        }
    }

    private suspend fun handleTrades(trades: List<TradeData>, watchlist: List<String>) {
        trades.forEach { trade ->
            if (trade.symbol !in watchlist) return@forEach
            prices[trade.symbol] = trade.price
            if (!baselines.containsKey(trade.symbol)) {
                baselines[trade.symbol] = trade.price
            }
        }
        _snapshot.emit(PriceSnapshot(prices.toMap(), baselines.toMap()))
    }
}
