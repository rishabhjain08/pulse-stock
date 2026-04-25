package com.pulsestock.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestock.app.ui.theme.PulseGreen
import com.pulsestock.app.ui.theme.PulseRed
import com.pulsestock.app.ui.theme.PulseSubtext
import com.pulsestock.app.ui.theme.PulseText

@Composable
fun StockRow(
    symbol: String,
    price: Double?,
    baseline: Double?,          // first-received price for this session (% change ref)
    modifier: Modifier = Modifier
) {
    val priceColor: Color = when {
        price == null || baseline == null -> PulseSubtext
        price >= baseline                 -> PulseGreen
        else                              -> PulseRed
    }

    val changePercent: Double? = if (price != null && baseline != null && baseline != 0.0) {
        ((price - baseline) / baseline) * 100.0
    } else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Exchange + ticker (NASDAQ dimmed, AAPL bold) ──────────────────
        val parts    = symbol.split(":")
        val exchange = if (parts.size == 2) parts[0] else ""
        val ticker   = if (parts.size == 2) parts[1] else symbol
        Row(
            modifier          = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (exchange.isNotEmpty()) {
                Text(
                    text     = exchange,
                    style    = TextStyle(fontSize = 11.sp),
                    color    = PulseSubtext,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Text(
                text  = ticker,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = PulseText
            )
        }

        // ── Live price with odometer animation ────────────────────────────
        if (price != null) {
            OdometerText(
                price = price,
                color = priceColor,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
            )
        } else {
            Text("--", style = TextStyle(fontSize = 17.sp), color = PulseSubtext)
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ── % change ──────────────────────────────────────────────────────
        val changeText = changePercent?.let {
            val sign = if (it >= 0) "+" else ""
            "$sign${"%.2f".format(it)}%"
        } ?: "--"

        Text(
            text     = changeText,
            style    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color    = if (changePercent != null) priceColor else PulseSubtext,
            modifier = Modifier.widthIn(min = 68.dp)
        )
    }
}
