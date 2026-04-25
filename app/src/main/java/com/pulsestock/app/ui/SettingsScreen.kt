package com.pulsestock.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pulsestock.app.BuildConfig
import com.pulsestock.app.PulseHUDService
import com.pulsestock.app.data.StockPreferences
import com.pulsestock.app.ui.theme.PulseGreen
import com.pulsestock.app.ui.theme.PulseRed
import com.pulsestock.app.ui.theme.PulseSubtext
import com.pulsestock.app.ui.theme.PulseText
import kotlinx.coroutines.launch

private const val MAX_SYMBOLS = 5
private val WarnAmber  = Color(0xFFF59E0B)
private val WarnSurface = Color(0xFFFFFBEB)
private val WarnText   = Color(0xFF78350F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context      = LocalContext.current
    val prefs        = remember { StockPreferences(context) }
    val scope        = rememberCoroutineScope()
    val keyboard     = LocalSoftwareKeyboardController.current
    val isHUDRunning by PulseHUDService.runningState.collectAsState()

    // ── Permission states ────────────────────────────────────────────────────
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasNotif   by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotif = granted }

    // Re-check permissions when user returns from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
                hasNotif   = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Symbol state ─────────────────────────────────────────────────────────
    val symbols by prefs.watchedSymbols.collectAsState(initial = StockPreferences.DEFAULT_SYMBOLS)
    var input   by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf<String?>(null) }
    val atMax = symbols.size >= MAX_SYMBOLS

    fun tryAdd() {
        val symbol = input.trim().uppercase()
        error = StockPreferences.validate(symbol)
            ?: if (symbol in symbols) "$symbol already in list" else null
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
                    Text(
                        "PulseStock",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp,
                        color      = PulseText
                    )
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

            // ── Permission alerts ────────────────────────────────────────────
            if (!hasNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionAlert(
                    icon        = Icons.Default.NotificationsOff,
                    text        = "Enable notifications for the live status bar chip",
                    actionLabel = "Grant",
                    onClick     = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
                Spacer(Modifier.height(6.dp))
            }
            if (!hasOverlay) {
                PermissionAlert(
                    icon        = Icons.Default.VisibilityOff,
                    text        = "\"Appear on top\" permission required to show the floating HUD",
                    actionLabel = "Open Settings",
                    onClick     = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data  = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(if (!hasOverlay || !hasNotif) 10.dp else 4.dp))

            // ── Start / Stop HUD ─────────────────────────────────────────────
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
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = if (isHUDRunning) PulseRed else PulseGreen,
                    disabledContainerColor = PulseGreen.copy(alpha = 0.35f)
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

            Spacer(Modifier.height(14.dp))

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
                    HowToStep("4", "Hold the icon (or tap Stop HUD) to close")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Watched symbols header ────────────────────────────────────────
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
                    "${symbols.size} / $MAX_SYMBOLS",
                    style = TextStyle(
                        fontSize   = 13.sp,
                        color      = if (atMax) PulseRed else PulseSubtext,
                        fontWeight = if (atMax) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Symbol list ───────────────────────────────────────────────────
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(symbols, key = { _, s -> s }) { index, symbol ->
                    Card(
                        modifier  = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape     = RoundedCornerShape(12.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color(0xFFF8F8F8)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text     = "${index + 1}",
                                style    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                color    = PulseSubtext,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text     = symbol,
                                style    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
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
                                    tint               = PulseRed.copy(alpha = 0.7f),
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (symbols.isEmpty()) {
                    item {
                        Text(
                            "No symbols yet — add one below",
                            color    = PulseSubtext,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Add symbol ────────────────────────────────────────────────────
            if (atMax) {
                Text(
                    "Maximum $MAX_SYMBOLS symbols reached — remove one to add another",
                    fontSize  = 12.sp,
                    color     = PulseSubtext,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value         = input,
                        onValueChange = { input = it.uppercase().take(32) },
                        label         = { Text("Add symbol") },
                        placeholder   = { Text("AAPL · BINANCE:BTCUSDT", color = PulseSubtext) },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        isError       = error != null,
                        shape         = RoundedCornerShape(12.dp),
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
            }

            Spacer(Modifier.height(16.dp))

            // ── Version ───────────────────────────────────────────────────────
            Text(
                text      = "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                fontSize  = 11.sp,
                color     = PulseSubtext.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun PermissionAlert(
    icon: ImageVector,
    text: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WarnSurface)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = WarnAmber,
            modifier           = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text       = text,
            fontSize   = 12.sp,
            color      = WarnText,
            modifier   = Modifier.weight(1f),
            lineHeight = 17.sp
        )
        TextButton(
            onClick          = onClick,
            contentPadding   = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text       = actionLabel,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = WarnAmber
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
