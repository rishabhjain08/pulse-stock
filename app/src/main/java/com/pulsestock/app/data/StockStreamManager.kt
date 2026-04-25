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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
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
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "StockStream"
private const val FINNHUB_WS_URL    = "wss://ws.finnhub.io?token="
private const val FINNHUB_REST_URL  = "https://finnhub.io/api/v1"
private const val RECONNECT_DELAY_MS = 3_000L
private const val POLL_INTERVAL_MS  = 60_000L

class StockStreamManager {

    sealed class ConnectionState {
        object Connecting   : ConnectionState()
        object Connected    : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class PriceSnapshot(
        val prices: Map<String, Double>,
        /** Previous-close baseline — % change is relative to yesterday's close. */
        val baselines: Map<String, Double>
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val wsClient = HttpClient(OkHttp) {
        install(WebSockets) { pingIntervalMillis = 20_000 }
    }
    private val restClient = OkHttpClient()

    private val _snapshot = MutableSharedFlow<PriceSnapshot>(
        replay = 1,
        extraBufferCapacity = 128
    )
    val snapshot: SharedFlow<PriceSnapshot> = _snapshot.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val prices    = mutableMapOf<String, Double>()
    private val baselines = mutableMapOf<String, Double>()

    private var streamJob: Job? = null
    private var pollJob:   Job? = null

    // ── Streaming (WebSocket) ────────────────────────────────────────────────

    /** Connect WebSocket for real-time ticks. Existing cached prices are kept. */
    fun startStreaming(symbols: List<String>, scope: CoroutineScope) {
        if (symbols.isEmpty()) return
        stopStreaming()
        streamJob = scope.launch(Dispatchers.IO) { connectLoop(symbols) }
    }

    /** Disconnect WebSocket. Cached prices are preserved for instant display on next open. */
    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    // ── Polling (REST every 60s) ─────────────────────────────────────────────

    /** Start a 60-second REST poll loop. Fetches immediately, then every 60s. */
    fun startPolling(symbols: List<String>, scope: CoroutineScope) {
        if (symbols.isEmpty()) return
        stopPolling()
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                fetchRestQuotes(symbols, this)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // ── Full stop (service shutting down) ────────────────────────────────────

    fun stopAll() {
        stopStreaming()
        stopPolling()
        prices.clear()
        baselines.clear()
    }

    fun close() {
        stopAll()
        wsClient.close()
        restClient.dispatcher.executorService.shutdown()
    }

    // ── Finnhub symbol search ────────────────────────────────────────────────

    suspend fun searchSymbols(query: String): List<SymbolSearchResult> {
        if (query.isBlank()) return emptyList()
        return try {
            val url  = "$FINNHUB_REST_URL/search?q=$query&token=${BuildConfig.FINNHUB_API_KEY}"
            val body = restClient.newCall(Request.Builder().url(url).build())
                .execute().use { it.body?.string() ?: return emptyList() }
            json.decodeFromString<SymbolSearchResponse>(body).result
                .filter { it.type == "Common Stock" && !it.symbol.contains(".") }
                .take(8)
        } catch (e: Exception) {
            Log.w(TAG, "Symbol search error: ${e.message}")
            emptyList()
        }
    }

    // ── REST quote fetch ─────────────────────────────────────────────────────

    private suspend fun fetchRestQuotes(symbols: List<String>, scope: CoroutineScope) {
        try {
            val fetches = symbols.map { fullSymbol ->
                scope.async(Dispatchers.IO) {
                    val ticker = finnhubTicker(fullSymbol)
                    val url    = "$FINNHUB_REST_URL/quote?symbol=$ticker&token=${BuildConfig.FINNHUB_API_KEY}"
                    try {
                        val body = restClient.newCall(Request.Builder().url(url).build())
                            .execute().use { it.body?.string() ?: return@async null }
                        val q = json.decodeFromString<QuoteResponse>(body)
                        if (q.current > 0.0) fullSymbol to q else null
                    } catch (e: Exception) {
                        Log.w(TAG, "REST quote failed for $fullSymbol: ${e.message}")
                        null
                    }
                }
            }
            fetches.awaitAll().filterNotNull().forEach { (sym, q) ->
                prices[sym]    = q.current
                baselines[sym] = if (q.prevClose > 0.0) q.prevClose else q.current
            }
            if (prices.isNotEmpty()) {
                _snapshot.emit(PriceSnapshot(prices.toMap(), baselines.toMap()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "REST batch fetch error: ${e.message}")
        }
    }

    // ── WebSocket loop ───────────────────────────────────────────────────────

    private suspend fun connectLoop(symbols: List<String>) {
        while (true) {
            _connectionState.value = ConnectionState.Connecting
            try {
                wsClient.webSocket("$FINNHUB_WS_URL${BuildConfig.FINNHUB_API_KEY}") {
                    _connectionState.value = ConnectionState.Connected
                    symbols.forEach { symbol ->
                        send(Frame.Text("""{"type":"subscribe","symbol":"${finnhubTicker(symbol)}"}"""))
                    }
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        try {
                            val msg = json.decodeFromString<FinnhubMessage>(frame.readText())
                            when (msg.type) {
                                "trade" -> handleTrades(msg.data ?: emptyList(), symbols)
                                "error" -> Log.w(TAG, "Finnhub error: ${msg.msg}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                _connectionState.value = ConnectionState.Disconnected
                return
            } catch (e: Exception) {
                Log.e(TAG, "WS error: ${e.message}")
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            }
            if (!currentCoroutineContext().isActive) return
            delay(RECONNECT_DELAY_MS)
        }
    }

    private suspend fun handleTrades(trades: List<TradeData>, watchlist: List<String>) {
        val tickerToFull = watchlist.associateBy { finnhubTicker(it) }
        trades.forEach { trade ->
            val fullSymbol = tickerToFull[trade.symbol] ?: return@forEach
            prices[fullSymbol] = trade.price
            baselines.getOrPut(fullSymbol) { trade.price }
        }
        _snapshot.emit(PriceSnapshot(prices.toMap(), baselines.toMap()))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun finnhubTicker(symbol: String): String {
        val parts = symbol.split(":")
        return if (parts.size == 2 && parts[0] in US_EXCHANGES) parts[1] else symbol
    }

    companion object {
        private val US_EXCHANGES = setOf("NYSE", "NASDAQ", "AMEX", "NYSEARCA", "BATS", "CBOE")
    }
}
