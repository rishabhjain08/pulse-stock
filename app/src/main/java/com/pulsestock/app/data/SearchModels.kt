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

// ── Yahoo Finance /v7/finance/quote response (used for Indian stock REST quotes) ──

@Serializable
data class YahooQuoteApiResponse(
    val quoteResponse: YahooQuoteWrapper = YahooQuoteWrapper()
)

@Serializable
data class YahooQuoteWrapper(
    val result: List<YahooQuoteItem> = emptyList()
)

@Serializable
data class YahooQuoteItem(
    val symbol: String = "",
    val regularMarketPrice: Double = 0.0,
    val regularMarketPreviousClose: Double = 0.0
)
