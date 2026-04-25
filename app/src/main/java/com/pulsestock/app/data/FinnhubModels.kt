package com.pulsestock.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FinnhubMessage(
    val type: String,
    val data: List<TradeData>? = null,
    val msg: String? = null
)

@Serializable
data class TradeData(
    @SerialName("s") val symbol: String,
    @SerialName("p") val price: Double,
    @SerialName("t") val timestamp: Long,
    @SerialName("v") val volume: Double
)

/** REST /api/v1/quote response. `c` = current price, `pc` = previous close. */
@Serializable
data class QuoteResponse(
    @SerialName("c")  val current: Double = 0.0,
    @SerialName("pc") val prevClose: Double = 0.0,
    @SerialName("d")  val change: Double = 0.0,
    @SerialName("dp") val changePct: Double = 0.0
)
