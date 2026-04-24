package com.pulsestock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stock_prefs")

class StockPreferences(private val context: Context) {

    companion object {
        private val WATCHED_SYMBOLS_KEY = stringPreferencesKey("watched_symbols")
        val DEFAULT_SYMBOLS = listOf("AAPL", "TSLA", "NVDA", "MSFT", "GOOGL")

        /**
         * Validates a Finnhub symbol.
         * Accepted formats:
         *   - US stocks:  AAPL, TSLA, BRK.B          (plain ticker, 1–10 chars)
         *   - Crypto:     BINANCE:BTCUSDT             (EXCHANGE:SYMBOL)
         *   - Forex:      OANDA:EUR_USD
         * Returns null if valid, or an error string describing how to fix it.
         */
        fun validate(raw: String): String? {
            val s = raw.trim().uppercase()
            if (s.isBlank()) return "Enter a ticker symbol"
            val regex = Regex("^[A-Z0-9.]{1,10}(:[A-Z0-9._\\-]{1,20})?\$")
            if (!regex.matches(s)) {
                return "Invalid format. Use AAPL for US stocks, or EXCHANGE:SYMBOL for crypto/forex — e.g. BINANCE:BTCUSDT or OANDA:EUR_USD"
            }
            return null
        }
    }

    /**
     * Emits the current watchlist.
     * - Key absent (first launch): returns DEFAULT_SYMBOLS
     * - Key present but empty string (user removed all): returns empty list
     */
    val watchedSymbols: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[WATCHED_SYMBOLS_KEY]
        if (raw == null) {
            DEFAULT_SYMBOLS
        } else {
            raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    suspend fun updateSymbols(symbols: List<String>) {
        context.dataStore.edit { prefs ->
            // Always write the key (even as empty string) so a null read
            // continues to mean "never set" → use defaults only on first launch.
            prefs[WATCHED_SYMBOLS_KEY] = symbols
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(",")
        }
    }
}
