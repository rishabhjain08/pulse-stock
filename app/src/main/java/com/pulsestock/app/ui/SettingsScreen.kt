package com.pulsestock.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestock.app.BuildConfig
import com.pulsestock.app.PulseHUDService
import com.pulsestock.app.data.StockPreferences
import com.pulsestock.app.ui.theme.PulseGreen
import com.pulsestock.app.ui.theme.PulseRed
import com.pulsestock.app.ui.theme.PulseSubtext
import com.pulsestock.app.ui.theme.PulseText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context      = LocalContext.current
    val prefs        = remember { StockPreferences(context) }
    val scope        = rememberCoroutineScope()
    val keyboard     = LocalSoftwareKeyboardController.current
    val isHUDRunning  by PulseHUDService.runningState.collectAsState()
    val hasOverlay    = Settings.canDrawOverlays(context)

    val symbols by prefs.watchedSymbols.collectAsState(initial = StockPreferences.DEFAULT_SYMBOLS)
    var input    by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf<String?>(null) }

    fun tryAdd() {
        val symbol = input.trim().uppercase()
        error = StockPreferences.validate(symbol)
            ?: if (symbol in symbols) "$symbol is already in your list" else null
        if (error == null) {
            scope.launch { prefs.updateSymbols(symbols + symbol) }
            input = ""
            keyboard?.hide()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PulseStock", fontWeight = FontWeight.Bold, color = PulseText)
                        Text(
                            "Manage your watched symbols",
                            fontSize = 11.sp,
                            color    = PulseSubtext
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // ── Start / Stop HUD button ──────────────────────────────────────
            Button(
                onClick = {
                    val intent = Intent(context, PulseHUDService::class.java).apply {
                        action = if (isHUDRunning) PulseHUDService.ACTION_STOP
                                 else              PulseHUDService.ACTION_START
                    }
                    if (isHUDRunning) context.startService(intent)
                    else              context.startForegroundService(intent)
                },
                enabled  = hasOverlay || isHUDRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor      = if (isHUDRunning) PulseRed else PulseGreen,
                    disabledContainerColor = PulseGreen.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    imageVector        = if (isHUDRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = if (isHUDRunning) "Stop HUD" else "Start HUD",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }

            if (!hasOverlay) {
                Text(
                    text     = "Grant \"Appear on top\" permission above to enable the HUD",
                    fontSize = 11.sp,
                    color    = PulseRed,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── How it works ─────────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFF0F9F0)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "How it works",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp,
                        color      = PulseText
                    )
                    Spacer(Modifier.height(8.dp))
                    HowToStep("1", "Tap Start HUD — a floating icon appears on screen")
                    HowToStep("2", "Tap the icon to show or hide live stock prices")
                    HowToStep("3", "Drag the icon to reposition it anywhere")
                    HowToStep("4", "Hold the icon (or tap Stop HUD) to close everything")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Watched symbols section header ───────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Watched Symbols",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PulseSubtext)
                )
                Text(
                    "${symbols.size} symbol${if (symbols.size == 1) "" else "s"}",
                    style = TextStyle(fontSize = 13.sp, color = PulseSubtext)
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Symbol list ──────────────────────────────────────────────────
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(symbols, key = { _, s -> s }) { index, symbol ->
                    Card(
                        modifier  = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text     = "${index + 1}. $symbol",
                                style    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                                color    = PulseText,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                scope.launch {
                                    prefs.updateSymbols(symbols.toMutableList().also { it.remove(symbol) })
                                }
                            }) {
                                Icon(
                                    imageVector        = Icons.Default.Delete,
                                    contentDescription = "Remove $symbol",
                                    tint               = PulseRed
                                )
                            }
                        }
                    }
                }

                if (symbols.isEmpty()) {
                    item {
                        Text(
                            "No symbols yet — add one below.",
                            color    = PulseSubtext,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Add symbol row ───────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value         = input,
                    onValueChange = { input = it.uppercase().take(32) },
                    label         = { Text("Add symbol") },
                    placeholder   = { Text("e.g. AAPL or BINANCE:BTCUSDT", color = PulseSubtext) },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    isError       = error != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction      = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { tryAdd() })
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = ::tryAdd,
                    colors  = IconButtonDefaults.filledIconButtonColors(containerColor = PulseGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }

            if (error != null) {
                Text(
                    text     = error!!,
                    color    = PulseRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            } else {
                Text(
                    text     = "US stocks: AAPL · Crypto: BINANCE:BTCUSDT · Forex: OANDA:EUR_USD",
                    fontSize = 11.sp,
                    color    = PulseSubtext,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text     = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                fontSize = 11.sp,
                color    = PulseSubtext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun HowToStep(num: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier          = Modifier.padding(vertical = 3.dp)
    ) {
        Text(
            text       = num,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = PulseGreen,
            modifier   = Modifier.width(20.dp)
        )
        Text(
            text     = text,
            fontSize = 12.sp,
            color    = PulseSubtext,
            modifier = Modifier.weight(1f)
        )
    }
}
