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
        const val MAX_SYMBOLS = 5
    }

    /** Emits the current list of watched symbols, defaulting to DEFAULT_SYMBOLS. */
    val watchedSymbols: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[WATCHED_SYMBOLS_KEY]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(MAX_SYMBOLS)
            ?.ifEmpty { DEFAULT_SYMBOLS }
            ?: DEFAULT_SYMBOLS
    }

    suspend fun updateSymbols(symbols: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[WATCHED_SYMBOLS_KEY] = symbols
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SYMBOLS)
                .joinToString(",")
        }
    }
}
