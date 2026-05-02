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
import kotlinx.coroutines.withContext
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
private const val FINNHUB_WS_URL     = "wss://ws.finnhub.io?token="
private const val FINNHUB_REST_URL   = "https://finnhub.io/api/v1"
private const val YAHOO_QUOTE_URL    = "https://query1.finance.yahoo.com/v7/finance/quote"
private const val RECONNECT_DELAY_MS = 3_000L
private const val POLL_INTERVAL_MS   = 60_000L
private const val INDIAN_POLL_MS     = 30_000L  // refresh interval for Indian stocks while popup open

class StockStreamManager {

    sealed class ConnectionState {
        object Connecting   : ConnectionState()
        object Connected    : ConnectionState()
        object Polling      : ConnectionState()  // REST-only mode (no WebSocket, e.g. Indian stocks)
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

    private val _lastRestRefreshMs = MutableStateFlow(0L)
    /** Epoch-ms timestamp of the most recent completed REST poll. 0 = never polled. */
    val lastRestRefreshMs: StateFlow<Long> = _lastRestRefreshMs.asStateFlow()

    private val prices    = mutableMapOf<String, Double>()
    private val baselines = mutableMapOf<String, Double>()

    private var streamJob: Job? = null
    private var pollJob:   Job? = null

    // ── Streaming (WebSocket + Indian polling) ───────────────────────────────

    /**
     * Start live data for all symbols.
     * US/crypto → Finnhub WebSocket with an initial REST fetch.
     * Indian (NSE/BSE) → Yahoo Finance REST, refreshed every 30s while popup is open.
     */
    fun startStreaming(symbols: List<String>, scope: CoroutineScope) {
        if (symbols.isEmpty()) return
        stopStreaming()
        streamJob = scope.launch(Dispatchers.IO) {
            fetchRestQuotes(symbols, this)

            val indianSymbols = symbols.filter { isIndianSymbol(it) }
            val wsSymbols     = symbols.filterNot { isIndianSymbol(it) }

            // Keep Indian stocks fresh while the popup is open
            if (indianSymbols.isNotEmpty()) {
                launch {
                    while (isActive) {
                        delay(INDIAN_POLL_MS)
                        val results = fetchYahooQuotes(indianSymbols)
                        results.forEach { (sym, price, baseline) ->
                            prices[sym]    = price
                            baselines[sym] = baseline
                        }
                        if (prices.isNotEmpty()) {
                            _lastRestRefreshMs.value = System.currentTimeMillis()
                            _snapshot.emit(PriceSnapshot(prices.toMap(), baselines.toMap()))
                        }
                    }
                }
            }

            if (wsSymbols.isNotEmpty()) {
                connectLoop(wsSymbols)
            } else {
                // Indian-only watchlist — REST polling is the only data source
                _connectionState.value = ConnectionState.Polling
            }
        }
    }

    /** Disconnect WebSocket + Indian polling. Cached prices preserved for instant display on reopen. */
    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    // ── Polling (REST every 60s) ─────────────────────────────────────────────

    /** Start a 60-second REST poll loop for all symbols (US via Finnhub, Indian via Yahoo). */
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

    // ── Single-symbol quote (used for add-stock validation) ─────────────────

    suspend fun fetchQuote(fullSymbol: String): QuoteResponse? = withContext(Dispatchers.IO) {
        try {
            if (isIndianSymbol(fullSymbol)) {
                val results = fetchYahooQuotes(listOf(fullSymbol))
                val (_, price, baseline) = results.firstOrNull() ?: return@withContext null
                QuoteResponse(
                    current   = price,
                    prevClose = baseline,
                    change    = price - baseline,
                    changePct = if (baseline > 0.0) (price - baseline) / baseline * 100.0 else 0.0
                )
            } else {
                val ticker = finnhubTicker(fullSymbol)
                val url    = "$FINNHUB_REST_URL/quote?symbol=$ticker&token=${BuildConfig.FINNHUB_API_KEY}"
                val body   = restClient.newCall(Request.Builder().url(url).build()).execute()
                    .use { resp -> if (!resp.isSuccessful) null else resp.body?.string() }
                    ?: return@withContext null
                json.decodeFromString<QuoteResponse>(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── Symbol search (Yahoo Finance — free, no key, substring matching) ────────

    suspend fun searchSymbols(query: String): List<StockSuggestion> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val q    = java.net.URLEncoder.encode(query, "UTF-8")
                val url  = "https://query1.finance.yahoo.com/v1/finance/search" +
                           "?q=$q&quotesCount=10&newsCount=0&listsCount=0"
                val body = restClient.newCall(
                    Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()
                } ?: return@withContext emptyList()
                json.decodeFromString<YahooSearchResponse>(body)
                    .quotes
                    .filter { it.quoteType == "EQUITY" || it.quoteType == "ETF" }
                    .mapNotNull { quote ->
                        val exchange = yahooExchangeToPrefix(quote.exchDisp) ?: return@mapNotNull null
                        // Strip Yahoo suffixes (.NS for NSE India, .BO for BSE India)
                        val cleanTicker = quote.symbol.removeSuffix(".NS").removeSuffix(".BO")
                        StockSuggestion(
                            fullSymbol = "$exchange:$cleanTicker",
                            ticker     = cleanTicker,
                            name       = quote.shortname.ifBlank { quote.longname },
                            exchange   = exchange
                        )
                    }
                    .take(8)
            } catch (e: Exception) {
                Log.w(TAG, "Symbol search error: ${e.message}")
                emptyList()
            }
        }
    }

    private fun yahooExchangeToPrefix(exchDisp: String): String? = when {
        exchDisp.equals("NASDAQ", ignoreCase = true)
            || exchDisp.startsWith("Nasdaq", ignoreCase = true) -> "NASDAQ"
        exchDisp.equals("NYSE", ignoreCase = true)              -> "NYSE"
        exchDisp.contains("Arca", ignoreCase = true)            -> "NYSEARCA"
        exchDisp.contains("American", ignoreCase = true)        -> "AMEX"
        exchDisp.equals("CBOE", ignoreCase = true)              -> "CBOE"
        exchDisp.equals("BATS", ignoreCase = true)              -> "BATS"
        // NSI is Yahoo's internal code for NSE India; BOM is Yahoo's code for BSE
        exchDisp.equals("NSE", ignoreCase = true)
            || exchDisp.equals("NSI", ignoreCase = true)        -> "NSE"
        exchDisp.equals("BSE", ignoreCase = true)
            || exchDisp.equals("BOM", ignoreCase = true)        -> "BSE"
        else -> null
    }

    // ── REST quote fetch ─────────────────────────────────────────────────────

    private suspend fun fetchRestQuotes(symbols: List<String>, scope: CoroutineScope) {
        try {
            val indianSymbols  = symbols.filter { isIndianSymbol(it) }
            val finnhubSymbols = symbols.filterNot { isIndianSymbol(it) }

            // Finnhub: one async call per US/crypto symbol (all run in parallel)
            val finnhubDeferred = finnhubSymbols.map { fullSymbol ->
                scope.async(Dispatchers.IO) { fetchFinnhubQuote(fullSymbol) }
            }
            // Yahoo Finance: single batch call for all Indian symbols
            val yahooDeferred = if (indianSymbols.isNotEmpty()) {
                scope.async(Dispatchers.IO) { fetchYahooQuotes(indianSymbols) }
            } else null

            // Collect results then mutate maps sequentially (avoids data races)
            val finnhubResults = finnhubDeferred.awaitAll().filterNotNull()
            val yahooResults   = yahooDeferred?.await() ?: emptyList()

            (finnhubResults + yahooResults).forEach { (sym, price, baseline) ->
                prices[sym]    = price
                baselines[sym] = baseline
            }

            if (prices.isNotEmpty()) {
                _lastRestRefreshMs.value = System.currentTimeMillis()
                _snapshot.emit(PriceSnapshot(prices.toMap(), baselines.toMap()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "REST batch fetch error: ${e.message}")
        }
    }

    /** Fetch a single US/crypto symbol from Finnhub. Returns (fullSymbol, price, baseline) or null. */
    private fun fetchFinnhubQuote(fullSymbol: String): Triple<String, Double, Double>? {
        val ticker = finnhubTicker(fullSymbol)
        val url    = "$FINNHUB_REST_URL/quote?symbol=$ticker&token=${BuildConfig.FINNHUB_API_KEY}"
        return try {
            val body = restClient.newCall(Request.Builder().url(url).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    resp.body?.string()
                } ?: return null
            val q = json.decodeFromString<QuoteResponse>(body)
            if (q.current <= 0.0 && q.prevClose <= 0.0) return null
            Triple(
                fullSymbol,
                if (q.current > 0.0) q.current else q.prevClose,
                if (q.prevClose > 0.0) q.prevClose else q.current
            )
        } catch (e: Exception) {
            Log.w(TAG, "Finnhub REST failed for $fullSymbol: ${e.message}")
            null
        }
    }

    /** Batch-fetch Indian symbols from Yahoo Finance /v7/finance/quote (free, no key). */
    private fun fetchYahooQuotes(indianSymbols: List<String>): List<Triple<String, Double, Double>> {
        if (indianSymbols.isEmpty()) return emptyList()
        return try {
            val yTickers = indianSymbols.joinToString(",") { yahooTicker(it) }
            val url = "$YAHOO_QUOTE_URL?symbols=${
                java.net.URLEncoder.encode(yTickers, "UTF-8")
            }"
            val body = restClient.newCall(
                Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string()
            } ?: return emptyList()
            val response      = json.decodeFromString<YahooQuoteApiResponse>(body)
            val yTickerToFull = indianSymbols.associateBy { yahooTicker(it) }
            response.quoteResponse.result.mapNotNull { item ->
                val fullSymbol = yTickerToFull[item.symbol] ?: return@mapNotNull null
                val price = item.regularMarketPrice
                val prev  = item.regularMarketPreviousClose
                if (price <= 0.0 && prev <= 0.0) return@mapNotNull null
                Triple(fullSymbol, if (price > 0.0) price else prev, if (prev > 0.0) prev else price)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Yahoo Finance quote failed: ${e.message}")
            emptyList()
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

    private fun isIndianSymbol(symbol: String) =
        symbol.split(":").firstOrNull() in INDIAN_EXCHANGES

    /** Strip exchange prefix for US stocks; pass through everything else (crypto, Indian, etc.). */
    private fun finnhubTicker(symbol: String): String {
        val parts = symbol.split(":")
        return if (parts.size == 2 && parts[0] in US_EXCHANGES) parts[1] else symbol
    }

    /** Convert internal symbol to Yahoo Finance ticker format (NSE:RELIANCE → RELIANCE.NS). */
    private fun yahooTicker(symbol: String): String {
        val parts = symbol.split(":")
        if (parts.size != 2) return symbol
        return when (parts[0]) {
            "NSE" -> "${parts[1]}.NS"
            "BSE" -> "${parts[1]}.BO"
            else  -> symbol
        }
    }

    companion object {
        private val US_EXCHANGES     = setOf("NYSE", "NASDAQ", "AMEX", "NYSEARCA", "BATS", "CBOE")
        private val INDIAN_EXCHANGES = setOf("NSE", "BSE")
    }
}
