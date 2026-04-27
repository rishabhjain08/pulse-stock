package com.pulsestock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stock_prefs")

class StockPreferences(private val context: Context) {

    companion object {
        private val WATCHED_SYMBOLS_KEY = stringPreferencesKey("watched_symbols")
        private val TILE_ACTIVE_KEY     = booleanPreferencesKey("tile_active")
        private val BUBBLE_ACTIVE_KEY   = booleanPreferencesKey("bubble_active")
        private val POPUP_X_KEY         = intPreferencesKey("popup_x")
        private val POPUP_Y_KEY         = intPreferencesKey("popup_y")

        val DEFAULT_SYMBOLS = listOf("NASDAQ:AAPL", "NASDAQ:TSLA", "NASDAQ:NVDA", "NASDAQ:MSFT", "NASDAQ:GOOGL")

        /**
         * Validates a Finnhub symbol.
         * For US stocks the format is EXCHANGE:TICKER (e.g. NASDAQ:AAPL, NYSE:GME).
         * Crypto/forex continue to use the EXCHANGE:SYMBOL convention (BINANCE:BTCUSDT).
         * Plain tickers without an exchange prefix are also accepted for backwards compat.
         */
        fun validate(raw: String): String? {
            val s = raw.trim().uppercase()
            if (s.isBlank()) return "Enter a ticker symbol"
            val regex = Regex("^[A-Z0-9.]{1,10}(:[A-Z0-9._\\-]{1,20})?\$")
            if (!regex.matches(s)) {
                return "Invalid format — use EXCHANGE:TICKER, e.g. NASDAQ:AAPL or NYSE:GME"
            }
            return null
        }
    }

    val watchedSymbols: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[WATCHED_SYMBOLS_KEY]
        if (raw == null) DEFAULT_SYMBOLS
        else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    /** Whether the Quick Settings tile is showing live prices. */
    val tileActive: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TILE_ACTIVE_KEY] ?: false
    }

    /** Whether the floating price bubble is visible. */
    val bubbleActive: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BUBBLE_ACTIVE_KEY] ?: false
    }

    suspend fun updateSymbols(symbols: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[WATCHED_SYMBOLS_KEY] = symbols
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(",")
        }
    }

    suspend fun setTileActive(active: Boolean) {
        context.dataStore.edit { it[TILE_ACTIVE_KEY] = active }
    }

    suspend fun setBubbleActive(active: Boolean) {
        context.dataStore.edit { it[BUBBLE_ACTIVE_KEY] = active }
    }

    val popupX: Flow<Int> = context.dataStore.data.map { it[POPUP_X_KEY] ?: 0 }
    val popupY: Flow<Int> = context.dataStore.data.map { it[POPUP_Y_KEY] ?: 80 }

    suspend fun setPopupPosition(x: Int, y: Int) {
        context.dataStore.edit { it[POPUP_X_KEY] = x; it[POPUP_Y_KEY] = y }
    }
}
