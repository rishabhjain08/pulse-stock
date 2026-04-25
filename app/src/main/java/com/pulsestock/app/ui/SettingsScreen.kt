package com.pulsestock.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
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
import com.pulsestock.app.data.StockStreamManager
import com.pulsestock.app.data.SymbolSearchResult
import com.pulsestock.app.ui.theme.PulseGreen
import com.pulsestock.app.ui.theme.PulseRed
import com.pulsestock.app.ui.theme.PulseSubtext
import com.pulsestock.app.ui.theme.PulseText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val WarnAmber   = Color(0xFFF59E0B)
private val WarnSurface = Color(0xFFFFFBEB)
private val WarnText    = Color(0xFF78350F)
private val LiveGreen   = Color(0xFF16A34A)
private val LiveSurface = Color(0xFFECFDF5)
private val OffSurface  = Color(0xFFF3F4F6)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen() {
    val context  = LocalContext.current
    val prefs    = remember { StockPreferences(context) }
    val scope    = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val stream   = remember { StockStreamManager() }

    val tileRunning   by PulseHUDService.tileRunning.collectAsState()
    val bubbleRunning by PulseHUDService.bubbleRunning.collectAsState()
    val isAnyLive = tileRunning || bubbleRunning

    // ── Permissions ──────────────────────────────────────────────────────────
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
                hasNotif   = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // ── Symbols + drag state ─────────────────────────────────────────────────
    val symbols by prefs.watchedSymbols.collectAsState(initial = StockPreferences.DEFAULT_SYMBOLS)
    var workingList by remember(symbols) { mutableStateOf(symbols) }
    val listState   = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey   = to.key   as? String ?: return@rememberReorderableLazyListState
        val fromIdx = workingList.indexOf(fromKey)
        val toIdx   = workingList.indexOf(toKey)
        if (fromIdx >= 0 && toIdx >= 0) {
            workingList = workingList.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
        }
    }

    // ── Add-symbol state ─────────────────────────────────────────────────────
    val snackbarState = remember { SnackbarHostState() }
    var input        by remember { mutableStateOf("") }
    var hasError     by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var suggestions  by remember { mutableStateOf<List<SymbolSearchResult>>(emptyList()) }
    var showDropdown by remember { mutableStateOf(false) }

    // Debounced autocomplete via LaunchedEffect — cancels automatically when input changes.
    LaunchedEffect(input) {
        val query = input.trim()
        if (query.length >= 2 && !query.contains(':')) {
            delay(300)
            suggestions  = stream.searchSymbols(query)
            showDropdown = suggestions.isNotEmpty()
        } else {
            suggestions  = emptyList()
            showDropdown = false
        }
    }

    fun tryAdd(rawSymbol: String = input) {
        val symbol = rawSymbol.trim().uppercase()
        val formatError = StockPreferences.validate(symbol)
            ?: if (symbol in symbols) "\"$symbol\" is already in your list" else null
        if (formatError != null) {
            hasError = true
            scope.launch { snackbarState.showSnackbar(formatError) }
            return
        }

        isValidating = true
        hasError     = false
        scope.launch {
            val quote = stream.fetchQuote(symbol)
            isValidating = false
            when {
                quote == null -> {
                    hasError = true
                    snackbarState.showSnackbar("Network error — check your connection and try again")
                }
                quote.current == 0.0 && quote.prevClose == 0.0 -> {
                    hasError = true
                    val ticker  = symbol.substringAfterLast(':')
                    val results = stream.searchSymbols(ticker)
                    val msg = if (results.isNotEmpty()) {
                        val hint = results.take(2)
                            .joinToString(" or ") { r -> "${r.displaySymbol} (${r.description.take(25)})" }
                        "\"$ticker\" not found — did you mean: $hint?"
                    } else {
                        "\"$ticker\" not recognized — check the ticker and exchange prefix"
                    }
                    snackbarState.showSnackbar(msg, duration = SnackbarDuration.Long)
                }
                else -> {
                    hasError = false
                    prefs.updateSymbols(symbols + symbol)
                    input        = ""
                    showDropdown = false
                    suggestions  = emptyList()
                    keyboard?.hide()
                }
            }
        }
    }

    val bringIntoView = remember { BringIntoViewRequester() }

    Scaffold(
        modifier = Modifier.imePadding(),   // shrinks Scaffold+SnackbarHost above keyboard
        topBar = {
            TopAppBar(
                title = { Text("PulseStock", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PulseText) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) { data -> Snackbar(data) } },
        containerColor = Color.White
    ) { padding ->
        LazyColumn(
            state    = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── Permission alerts ──────────────────────────────────────────
            if (!hasNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item(key = "notif_alert") {
                    PermissionAlert(
                        icon        = Icons.Default.NotificationsOff,
                        text        = "Allow notifications so the live price chip shows in your status bar",
                        actionLabel = "Allow",
                        onClick     = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (!hasOverlay) {
                item(key = "overlay_alert") {
                    PermissionAlert(
                        icon        = Icons.Default.VisibilityOff,
                        text        = "\"Draw over other apps\" is needed for the floating price bubble",
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
            }

            // ── Live status strip ──────────────────────────────────────────
            item(key = "status") {
                Spacer(Modifier.height(if (!hasOverlay || !hasNotif) 10.dp else 4.dp))
                LiveStatusStrip(isAnyLive = isAnyLive, tileOn = tileRunning, bubbleOn = bubbleRunning)
                Spacer(Modifier.height(14.dp))
            }

            // ── Tile control ───────────────────────────────────────────────
            item(key = "tile_control") {
                ControlCard(
                    label       = "Quick Settings Tile",
                    description = "Shows live prices in your notification panel tile.",
                    isOn        = tileRunning,
                    enabled     = true,
                    onToggle    = { on ->
                        val action = if (on) PulseHUDService.ACTION_START_TILE else PulseHUDService.ACTION_STOP_TILE
                        val intent = Intent(context, PulseHUDService::class.java).setAction(action)
                        if (on) context.startForegroundService(intent) else context.startService(intent)
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Bubble control ─────────────────────────────────────────────
            item(key = "bubble_control") {
                ControlCard(
                    label          = "Floating Price Bubble",
                    description    = "A draggable bubble that floats over any app. Tap it to see prices.",
                    isOn           = bubbleRunning,
                    enabled        = hasOverlay,
                    disabledReason = if (!hasOverlay) "Requires \"Draw over other apps\" permission above" else null,
                    onToggle       = { on ->
                        val action = if (on) PulseHUDService.ACTION_SHOW_BUBBLE else PulseHUDService.ACTION_HIDE_BUBBLE
                        val intent = Intent(context, PulseHUDService::class.java).setAction(action)
                        if (on) context.startForegroundService(intent) else context.startService(intent)
                    }
                )
                Spacer(Modifier.height(20.dp))
            }

            // ── Watched stocks header ──────────────────────────────────────
            item(key = "stocks_header") {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Watched Stocks",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PulseSubtext))
                    Text("${symbols.size} stocks",
                        style = TextStyle(fontSize = 13.sp, color = PulseSubtext))
                }
                Text(
                    "Long-press and drag to reorder",
                    fontSize = 11.sp,
                    color    = PulseSubtext.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
            }

            // ── Reorderable stock items ────────────────────────────────────
            itemsIndexed(workingList, key = { _, s -> s }) { index, symbol ->
                val parts    = symbol.split(":")
                val exchange = if (parts.size == 2) parts[0] else ""
                val ticker   = if (parts.size == 2) parts[1] else symbol

                ReorderableItem(reorderState, key = symbol) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .shadow(elevation, RoundedCornerShape(12.dp)),
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging) Color(0xFFECFDF5) else Color(0xFFF8F8F8)
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DragHandle, null,
                                tint     = PulseSubtext.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 4.dp)
                                    .size(20.dp)
                                    .longPressDraggableHandle(
                                        onDragStopped = { scope.launch { prefs.updateSymbols(workingList) } }
                                    )
                            )
                            Text("${index + 1}", style = TextStyle(fontSize = 12.sp), color = PulseSubtext,
                                modifier = Modifier.width(18.dp))
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                if (exchange.isNotEmpty()) {
                                    Text(exchange, fontSize = 10.sp, color = PulseSubtext,
                                        modifier = Modifier.padding(end = 4.dp))
                                }
                                Text(ticker, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                                    color = PulseText)
                            }
                            IconButton(onClick = {
                                scope.launch { prefs.updateSymbols(symbols - symbol) }
                            }) {
                                Icon(Icons.Default.Delete, "Remove $symbol",
                                    tint = PulseRed.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // ── Empty state ────────────────────────────────────────────────
            if (workingList.isEmpty()) {
                item(key = "empty") {
                    Text("No stocks yet — add one below",
                        color = PulseSubtext, fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp))
                }
            }

            // ── Add symbol with autocomplete ───────────────────────────────
            item(key = "add_field") {
                Spacer(Modifier.height(12.dp))
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value         = input,
                            onValueChange = { v ->
                                input    = v.uppercase().take(32)
                                hasError = false
                            },
                            label         = { Text("Search or type a ticker") },
                            placeholder   = { Text("e.g. NASDAQ:AAPL", color = PulseSubtext) },
                            singleLine    = true,
                            modifier      = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(bringIntoView)
                                .onFocusChanged { fs ->
                                    if (fs.isFocused) scope.launch {
                                        delay(300)
                                        bringIntoView.bringIntoView()
                                    }
                                },
                            isError         = hasError,
                            shape           = RoundedCornerShape(12.dp),
                            trailingIcon    = if (isValidating) ({
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }) else null,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction      = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { tryAdd() })
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick  = ::tryAdd,
                            enabled  = !isValidating,
                            colors   = IconButtonDefaults.filledIconButtonColors(containerColor = PulseGreen)
                        ) {
                            Icon(Icons.Default.Add, "Add stock", tint = Color.White)
                        }
                    }

                    DropdownMenu(
                        expanded         = showDropdown,
                        onDismissRequest = { showDropdown = false },
                        modifier         = Modifier.fillMaxWidth(0.85f)
                    ) {
                        suggestions.forEach { result ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(result.displaySymbol,
                                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PulseText)
                                        Spacer(Modifier.width(6.dp))
                                        Text(result.description,
                                            fontSize = 12.sp, color = PulseSubtext, maxLines = 1,
                                            modifier = Modifier.weight(1f))
                                    }
                                },
                                onClick = {
                                    input        = result.displaySymbol
                                    showDropdown = false
                                    scope.launch {
                                        snackbarState.showSnackbar(
                                            "Add exchange prefix — e.g. NASDAQ:${result.displaySymbol} or NYSE:${result.displaySymbol}"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                Text("Always include exchange — e.g. NASDAQ:AAPL · NYSE:GME · AMEX:SPY",
                    fontSize = 11.sp, color = PulseSubtext,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                Spacer(Modifier.height(20.dp))
            }

            // ── How to use ─────────────────────────────────────────────────
            item(key = "how_to") {
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(12.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color(0xFFF0F9F0)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("How to use", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PulseText)
                        Spacer(Modifier.height(8.dp))
                        HowToStep("1", "Turn on the Tile or Bubble using the toggles above")
                        HowToStep("2", "Tile: add PulseStock to your notification panel for always-visible prices")
                        HowToStep("3", "Tile: tap to toggle prices on/off · long-press tile → tap settings gear to open this app")
                        HowToStep("4", "Bubble: tap the floating icon to show/hide live prices")
                        HowToStep("5", "Bubble: drag to move · drag to red trash circle to hide · hold to open this app")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Version ────────────────────────────────────────────────────
            item(key = "version") {
                Text(
                    "v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                    fontSize  = 11.sp,
                    color     = PulseSubtext.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun LiveStatusStrip(isAnyLive: Boolean, tileOn: Boolean, bubbleOn: Boolean) {
    val bg   = if (isAnyLive) LiveSurface else OffSurface
    val dot  = if (isAnyLive) LiveGreen   else Color.LightGray
    val text = when {
        tileOn && bubbleOn -> "Live · tile + bubble active · using battery"
        tileOn             -> "Live · tile active · using battery"
        bubbleOn           -> "Live · bubble active · using battery"
        else               -> "Stopped · no background activity · no battery drain"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.sp, color = if (isAnyLive) LiveGreen else PulseSubtext)
    }
}

@Composable
private fun ControlCard(
    label: String,
    description: String,
    isOn: Boolean,
    enabled: Boolean,
    disabledReason: String? = null,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = if (isOn) LiveSurface else OffSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PulseText)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (!enabled && disabledReason != null) disabledReason else description,
                    fontSize = 12.sp, color = PulseSubtext, lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked         = isOn,
                onCheckedChange = onToggle,
                enabled         = enabled,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White, checkedTrackColor   = LiveGreen,
                    uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.LightGray
                )
            )
        }
    }
}

@Composable
private fun PermissionAlert(icon: ImageVector, text: String, actionLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WarnSurface, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = WarnAmber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.sp, color = WarnText, modifier = Modifier.weight(1f), lineHeight = 17.sp)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WarnAmber)
        }
    }
}

@Composable
private fun HowToStep(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(num, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PulseGreen,
            modifier = Modifier.width(20.dp))
        Text(text, fontSize = 12.sp, color = PulseSubtext, modifier = Modifier.weight(1f))
    }
}

