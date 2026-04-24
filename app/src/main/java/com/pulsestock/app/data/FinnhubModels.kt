package com.pulsestock.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level message from Finnhub WebSocket.
 * type = "trade"  → real price ticks
 * type = "ping"   → keepalive (no data)
 * type = "error"  → subscription error
 */
@Serializable
data class FinnhubMessage(
    val type: String,
    val data: List<TradeData>? = null,
    val msg: String? = null         // present on type="error"
)

/**
 * Single trade tick inside a "trade" message.
 * Finnhub may batch multiple ticks per frame.
 */
@Serializable
data class TradeData(
    @SerialName("s") val symbol: String,
    @SerialName("p") val price: Double,
    @SerialName("t") val timestamp: Long,
    @SerialName("v") val volume: Double
)
