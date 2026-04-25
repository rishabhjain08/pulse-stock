package com.pulsestock.app.data

import kotlinx.serialization.Serializable

/** A resolved stock suggestion — fullSymbol is ready to add (e.g. "NASDAQ:NVDA"). */
data class StockSuggestion(
    val fullSymbol: String,
    val ticker: String,
    val name: String,
    val exchange: String
)

@Serializable
data class YahooSearchResponse(
    val quotes: List<YahooQuote> = emptyList()
)

@Serializable
data class YahooQuote(
    val symbol: String = "",
    val shortname: String = "",
    val longname: String = "",
    val exchDisp: String = "",
    val quoteType: String = ""
)
