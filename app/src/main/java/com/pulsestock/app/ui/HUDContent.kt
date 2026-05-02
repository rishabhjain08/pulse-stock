package com.pulsestock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestock.app.data.StockStreamManager
import com.pulsestock.app.ui.components.StockRow
import com.pulsestock.app.ui.theme.PulseGreen
import com.pulsestock.app.ui.theme.PulseRed
import com.pulsestock.app.ui.theme.PulseDivider
import com.pulsestock.app.ui.theme.PulseSubtext
import com.pulsestock.app.ui.theme.PulseText

@Composable
fun HUDContent(
    symbols: List<String>,
    snapshot: StockStreamManager.PriceSnapshot,
    connectionState: StockStreamManager.ConnectionState,
    lastRefreshMs: Long,
    onDismiss: () -> Unit
) {
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .heightIn(max = maxCardHeight),
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())) {

                // ── Header ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(connectionDotColor(connectionState, symbols))
                    )
                    Spacer(Modifier.width(8.dp))

                    Text(
                        text       = "PulseStock",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = PulseText,
                        modifier   = Modifier.weight(1f)
                    )

                    Text(
                        text     = connectionLabel(connectionState, lastRefreshMs, symbols),
                        fontSize = 11.sp,
                        color    = PulseSubtext
                    )
                    Spacer(Modifier.width(4.dp))

                    TextButton(
                        onClick  = onDismiss,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("✕", fontSize = 15.sp, color = PulseSubtext)
                    }
                }

                HorizontalDivider(color = PulseDivider, thickness = 0.5.dp)

                // ── Stock rows ────────────────────────────────────────────
                symbols.forEachIndexed { index, symbol ->
                    StockRow(
                        symbol   = symbol,
                        price    = snapshot.prices[symbol],
                        baseline = snapshot.baselines[symbol]
                    )
                    if (index < symbols.lastIndex) {
                        HorizontalDivider(
                            color     = PulseDivider,
                            thickness = 0.5.dp,
                            modifier  = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun connectionDotColor(
    state: StockStreamManager.ConnectionState,
    symbols: List<String>
): Color = when {
    // Indian-only watchlist has no WebSocket — amber signals "live but delayed"
    state is StockStreamManager.ConnectionState.Disconnected && symbols.all { isIndian(it) } ->
        Color(0xFFFFA500)
    else -> when (state) {
        is StockStreamManager.ConnectionState.Connected    -> PulseGreen
        is StockStreamManager.ConnectionState.Connecting   -> Color(0xFFFFA500)
        is StockStreamManager.ConnectionState.Error        -> PulseRed
        is StockStreamManager.ConnectionState.Disconnected -> Color.LightGray
    }
}

private fun connectionLabel(
    state: StockStreamManager.ConnectionState,
    lastRefreshMs: Long,
    symbols: List<String>
): String {
    val allIndian = symbols.isNotEmpty() && symbols.all { isIndian(it) }
    return when (state) {
        is StockStreamManager.ConnectionState.Connected    -> "Live"
        is StockStreamManager.ConnectionState.Connecting  -> "Connecting…"
        is StockStreamManager.ConnectionState.Error       -> staleLabel(lastRefreshMs) ?: "Reconnecting…"
        is StockStreamManager.ConnectionState.Disconnected ->
            // Indian-only watchlist: no WebSocket, REST polling — show actual data age
            if (allIndian) staleLabel(lastRefreshMs) ?: "Fetching…"
            else staleLabel(lastRefreshMs) ?: "Offline"
    }
}

private fun isIndian(symbol: String) =
    symbol.substringBefore(":") in setOf("NSE", "BSE")

private fun staleLabel(lastRefreshMs: Long): String? {
    if (lastRefreshMs == 0L) return null
    val ageMin = (System.currentTimeMillis() - lastRefreshMs) / 60_000
    return when {
        ageMin < 1  -> "< 1 min ago"
        ageMin < 60 -> "~${ageMin}m ago"
        else        -> "~${ageMin / 60}h ago"
    }
}
