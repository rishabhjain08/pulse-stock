package com.pulsestock.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_SYMBOLS = 5
private val WarnAmber   = Color(0xFFF59E0B)
private val WarnSurface = Color(0xFFFFFBEB)
private val WarnText    = Color(0xFF78350F)
private val LiveGreen   = Color(0xFF16A34A)
private val LiveSurface = Color(0xFFECFDF5)
private val OffSurface  = Color(0xFFF3F4F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context   = LocalContext.current
    val prefs     = remember { StockPreferences(context) }
    val scope     = rememberCoroutineScope()
    val keyboard  = LocalSoftwareKeyboardController.current

    // ── Live state ───────────────────────────────────────────────────────────
    val tileRunning   by PulseHUDService.tileRunning.collectAsState()
    val bubbleRunning by PulseHUDService.bubbleRunning.collectAsState()
    val isAnyLive = tileRunning || bubbleRunning

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

    // Autocomplete
    val streamManager = remember { StockStreamManager() }
    var suggestions    by remember { mutableStateOf<List<SymbolSearchResult>>(emptyList()) }
    var searchJob: Job? = remember { null }
    var showDropdown   by remember { mutableStateOf(false) }

    fun onInputChange(value: String) {
        input = value.uppercase().take(32)
        error = null
        searchJob?.cancel()
        if (input.length >= 2) {
            searchJob = scope.launch {
                delay(300)
                suggestions = streamManager.searchSymbols(input)
                showDropdown = suggestions.isNotEmpty()
            }
        } else {
            suggestions  = emptyList()
            showDropdown = false
        }
    }

    fun tryAdd(rawSymbol: String = input) {
        val symbol = rawSymbol.trim().uppercase()
        error = StockPreferences.validate(symbol)
            ?: if (symbol in symbols) "$symbol is already in your list" else null
        if (error == null) {
            scope.launch { prefs.updateSymbols(symbols + symbol) }
            input        = ""
            showDropdown = false
            suggestions  = emptyList()
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
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // ── Permission alerts ────────────────────────────────────────────
            if (!hasNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionAlert(
                    icon        = Icons.Default.NotificationsOff,
                    text        = "Allow notifications so the live price chip shows in your status bar",
                    actionLabel = "Allow",
                    onClick     = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
                Spacer(Modifier.height(6.dp))
            }
            if (!hasOverlay) {
                PermissionAlert(
                    icon        = Icons.Default.VisibilityOff,
                    text        = "\"Draw over other apps\" permission is needed to show the floating price bubble",
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

            // ── Battery / live status strip ──────────────────────────────────
            LiveStatusStrip(isAnyLive = isAnyLive, tileOn = tileRunning, bubbleOn = bubbleRunning)

            Spacer(Modifier.height(14.dp))

            // ── Tile control ─────────────────────────────────────────────────
            ControlCard(
                label       = "Quick Settings Tile",
                description = "Shows live prices in the notification panel tile. No bubble, minimal screen space.",
                isOn        = tileRunning,
                enabled     = true,
                onToggle    = { on ->
                    val action = if (on) PulseHUDService.ACTION_START_TILE
                                 else   PulseHUDService.ACTION_STOP_TILE
                    val intent = Intent(context, PulseHUDService::class.java).setAction(action)
                    if (on) context.startForegroundService(intent) else context.startService(intent)
                }
            )

            Spacer(Modifier.height(10.dp))

            // ── Bubble control ───────────────────────────────────────────────
            ControlCard(
                label       = "Floating Price Bubble",
                description = "A draggable bubble that floats over any app. Tap it to expand prices.",
                isOn        = bubbleRunning,
                enabled     = hasOverlay,
                disabledReason = if (!hasOverlay) "Requires \"Draw over other apps\" permission above" else null,
                onToggle    = { on ->
                    val action = if (on) PulseHUDService.ACTION_SHOW_BUBBLE
                                 else   PulseHUDService.ACTION_HIDE_BUBBLE
                    val intent = Intent(context, PulseHUDService::class.java).setAction(action)
                    if (on) context.startForegroundService(intent) else context.startService(intent)
                }
            )

            Spacer(Modifier.height(20.dp))

            // ── Watched symbols header ────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "Watched Stocks",
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

            Spacer(Modifier.height(4.dp))
            Text(
                "Long-press any row and drag to reorder",
                fontSize = 11.sp,
                color    = PulseSubtext.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // ── Reorderable symbol list ───────────────────────────────────────
            ReorderableSymbolList(
                symbols  = symbols,
                onReorder = { reordered -> scope.launch { prefs.updateSymbols(reordered) } },
                onRemove  = { sym -> scope.launch { prefs.updateSymbols(symbols - sym) } }
            )

            Spacer(Modifier.height(12.dp))

            // ── Add symbol with autocomplete ─────────────────────────────────
            if (atMax) {
                Text(
                    "Maximum $MAX_SYMBOLS stocks reached — remove one to add another",
                    fontSize  = 12.sp,
                    color     = PulseSubtext,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            } else {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value         = input,
                            onValueChange = ::onInputChange,
                            label         = { Text("Search or type a ticker") },
                            placeholder   = { Text("e.g. AAPL, GOOGL, TSLA", color = PulseSubtext) },
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
                            Icon(Icons.Default.Add, contentDescription = "Add stock", tint = Color.White)
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
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                result.displaySymbol,
                                                fontWeight = FontWeight.Bold,
                                                fontSize   = 14.sp,
                                                color      = PulseText
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                result.description,
                                                fontSize = 12.sp,
                                                color    = PulseSubtext,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    // Use the raw symbol from Finnhub search.
                                    // Exchange prefix must be added explicitly by the user or
                                    // we pre-fill the field so they can confirm/correct it.
                                    input        = result.displaySymbol
                                    showDropdown = false
                                    // Note: we intentionally do NOT auto-add here — user must
                                    // verify/add exchange prefix (e.g. NASDAQ:AAPL) and tap Add.
                                    error = "Prefix with exchange, e.g. NASDAQ:${result.displaySymbol} or NYSE:${result.displaySymbol}, then tap +"
                                }
                            )
                        }
                    }
                }

                if (error != null) {
                    Text(
                        text     = error!!,
                        color    = if (error!!.startsWith("Prefix")) WarnAmber else PulseRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                } else {
                    Text(
                        text     = "Always include exchange: NASDAQ:AAPL · NYSE:GME · AMEX:SPY",
                        fontSize = 11.sp,
                        color    = PulseSubtext,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── How it works ─────────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFF0F9F0)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "How to use",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 13.sp,
                        color      = PulseText
                    )
                    Spacer(Modifier.height(8.dp))
                    HowToStep("1", "Turn on the Tile or Bubble above")
                    HowToStep("2", "Tile: add PulseStock to your notification panel for always-visible prices")
                    HowToStep("3", "Bubble: tap the floating icon to expand prices over any app")
                    HowToStep("4", "Drag the bubble anywhere · drag to the red trash zone to hide it")
                    HowToStep("5", "Hold the bubble to open this app · long-press the tile to open the app")
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
                    .padding(bottom = 16.dp)
            )
        }
    }
}

// ── Live status strip ─────────────────────────────────────────────────────────

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
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dot, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.sp, color = if (isAnyLive) LiveGreen else PulseSubtext)
    }
}

// ── Control card (tile / bubble toggle) ──────────────────────────────────────

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
        colors    = CardDefaults.cardColors(
            containerColor = if (isOn) LiveSurface else OffSurface
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PulseText)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (!enabled && disabledReason != null) disabledReason else description,
                    fontSize = 12.sp,
                    color    = PulseSubtext,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked         = isOn,
                onCheckedChange = onToggle,
                enabled         = enabled,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor       = Color.White,
                    checkedTrackColor       = LiveGreen,
                    uncheckedThumbColor     = Color.White,
                    uncheckedTrackColor     = Color.LightGray
                )
            )
        }
    }
}

// ── Reorderable symbol list ───────────────────────────────────────────────────

@Composable
private fun ReorderableSymbolList(
    symbols:   List<String>,
    onReorder: (List<String>) -> Unit,
    onRemove:  (String) -> Unit
) {
    // Mutable working copy so drags feel instant before the DataStore roundtrip.
    var workingList by remember(symbols) { mutableStateOf(symbols) }
    var dragIndex   by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }

    Column {
        workingList.forEachIndexed { index, symbol ->
            val isDragging  = dragIndex == index
            val yOffset     = if (isDragging) dragOffsetY.roundToInt() else 0
            val elevation   by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag-elev")

            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .offset { IntOffset(0, yOffset) }
                    .shadow(elevation, RoundedCornerShape(12.dp))
                    .pointerInput(workingList) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragIndex = index; dragOffsetY = 0f },
                            onDragEnd   = {
                                if (dragIndex >= 0) {
                                    onReorder(workingList)
                                }
                                dragIndex   = -1
                                dragOffsetY = 0f
                            },
                            onDragCancel = { dragIndex = -1; dragOffsetY = 0f },
                            onDrag       = { _, delta ->
                                dragOffsetY += delta.y
                                val newIndex = (index + (dragOffsetY / itemHeightPx).roundToInt())
                                    .coerceIn(0, workingList.lastIndex)
                                if (newIndex != dragIndex) {
                                    val mutable = workingList.toMutableList()
                                    val item = mutable.removeAt(dragIndex)
                                    mutable.add(newIndex, item)
                                    workingList = mutable
                                    dragOffsetY -= (newIndex - dragIndex) * itemHeightPx
                                    dragIndex   = newIndex
                                }
                            }
                        )
                    },
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = if (isDragging) Color(0xFFECFDF5) else Color(0xFFF8F8F8)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle
                    Icon(
                        imageVector        = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint               = PulseSubtext.copy(alpha = 0.5f),
                        modifier           = Modifier
                            .padding(start = 8.dp, end = 4.dp)
                            .size(20.dp)
                    )
                    // Index number
                    Text(
                        text     = "${index + 1}",
                        style    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color    = PulseSubtext,
                        modifier = Modifier.width(20.dp)
                    )
                    // Exchange + ticker split display
                    val parts    = symbol.split(":")
                    val exchange = if (parts.size == 2) parts[0] else ""
                    val ticker   = if (parts.size == 2) parts[1] else symbol
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (exchange.isNotEmpty()) {
                            Text(
                                text     = exchange,
                                fontSize = 11.sp,
                                color    = PulseSubtext,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Text(
                            text       = ticker,
                            style      = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            color      = PulseText
                        )
                    }
                    // Delete
                    IconButton(onClick = { onRemove(symbol) }) {
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

        if (workingList.isEmpty()) {
            Text(
                "No stocks yet — add one below",
                color    = PulseSubtext,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

// ── Permission alert ──────────────────────────────────────────────────────────

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
            .background(WarnSurface, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = WarnAmber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.sp, color = WarnText, modifier = Modifier.weight(1f), lineHeight = 17.sp)
        TextButton(
            onClick        = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WarnAmber)
        }
    }
}

// ── How-to step ───────────────────────────────────────────────────────────────

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
        Text(text, fontSize = 12.sp, color = PulseSubtext, modifier = Modifier.weight(1f))
    }
}
