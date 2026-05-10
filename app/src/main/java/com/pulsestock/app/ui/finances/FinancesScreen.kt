package com.pulsestock.app.ui.finances

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.sqrt
import com.pulsestock.app.BuildConfig
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.CategorySpend
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.effectiveCategory
import com.pulsestock.app.data.poarvault.usesWindowHeuristic
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancesScreen(
    vm: FinancesViewModel,
    onReconcile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsState()
    val snackbarState = remember { SnackbarHostState() }
    val currencyFmt = remember { NumberFormat.getCurrencyInstance(Locale.US) }

    // Entrance animation trigger — set true immediately on first composition
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbarState.showSnackbar(msg)
        vm.dismissError()
    }

    // One-shot: show a Short snackbar when the ViewModel auto-excluded business CCs
    // because the user picked a statement-anchored spending window.
    LaunchedEffect(state.pendingBusinessCardSnackbar) {
        if (!state.pendingBusinessCardSnackbar) return@LaunchedEffect
        vm.clearBusinessCardSnackbar()
        snackbarState.showSnackbar(
            message = "Business card statement unknown — select Last 30 Days or Custom",
            duration = SnackbarDuration.Short,
        )
    }

    // Category drill-down sheet — also shown when allTransactionsMode is active
    if (state.categoryDrillDown != null || state.allTransactionsMode) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = vm::closeCategoryDrillDown,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            CategoryDrillDownSheet(
                category = state.categoryDrillDown,
                transactions = state.drillDownTransactions,
                currencyFmt = currencyFmt,
                onEditCategory = vm::startOverride,
                onDismiss = vm::closeCategoryDrillDown,
                isBulkMode = state.isBulkMode,
                isAllTransactionsMode = state.allTransactionsMode,
                bulkSelectedIds = state.bulkSelectedIds,
                sessionCategorizedIds = state.sessionCategorizedIds,
                onEnterBulkMode = vm::enterBulkMode,
                onExitBulkMode = vm::exitBulkMode,
                onToggleBulkSelection = vm::toggleBulkSelection,
                onOpenBulkPicker = vm::openBulkPicker,
            )
        }
    }

    // Category override picker sheet (single transaction)
    val overridingTx = state.overridingTransaction
    if (overridingTx != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = vm::cancelOverride,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            CategoryPickerSheet(
                transaction = overridingTx,
                customCategories = state.customCategories,
                onPick = { category -> vm.applyOverride(overridingTx.transactionId, category) },
                onSaveCustomCategory = { name -> vm.saveCustomCategory(name) },
                onDeleteCustomCategory = { name -> vm.deleteCustomCategory(name) },
                countTransactionsWithOverride = { name -> vm.countTransactionsWithOverride(name) },
                onDismiss = vm::cancelOverride,
            )
        }
    }

    // Bulk category picker sheet
    if (state.isBulkPickerOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = vm::closeBulkPicker,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            CategoryPickerSheet(
                transaction = null,
                customCategories = state.customCategories,
                onPick = { category -> vm.applyBulkCategory(category) },
                onSaveCustomCategory = { name -> vm.saveCustomCategory(name) },
                onDeleteCustomCategory = { name -> vm.deleteCustomCategory(name) },
                countTransactionsWithOverride = { name -> vm.countTransactionsWithOverride(name) },
                onDismiss = vm::closeBulkPicker,
                isBulkMode = true,
                bulkCount = state.bulkSelectedIds.size,
            )
        }
    }

    // Merchant rule confirmation dialog
    val proposal = state.pendingMerchantRule
    if (proposal != null) {
        val proposalMeta = CategoryMeta.get(proposal.category)
        AlertDialog(
            onDismissRequest = vm::dismissMerchantRule,
            title = { Text("Apply to all ${proposal.merchantName}?") },
            text = {
                Text(
                    "Also set ${proposalMeta.emoji} ${proposalMeta.displayName} for " +
                    "${proposal.otherCount} other ${proposal.merchantName} " +
                    "transaction${if (proposal.otherCount == 1) "" else "s"} " +
                    "and save as a rule for future syncs."
                )
            },
            confirmButton = {
                Button(onClick = vm::confirmMerchantRule) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissMerchantRule) { Text("No") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        // Entrance: fade in + slide up the entire list.
        // initialOffsetY = it / 10 → subtle 10% shift, not a full-screen slide.
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn() + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialOffsetY = { it / 10 },
            ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Credit cards — only shown when at least one is connected ─────
                if (state.creditAccounts.isNotEmpty()) {
                    item {
                        FinancesSectionLabel("Credit Cards")
                    }
                    item {
                        // Outer container card: surfaceContainerLow + shapes.large groups all
                        // CC sub-cards, the totals row, and the Splitwise chip into one visual unit.
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // ── Per-account sub-cards ─────────────────────────────
                                state.creditAccounts.forEach { account ->
                                    CreditCardSummaryCard(
                                        account = account,
                                        isBusinessCard = account.usesWindowHeuristic,
                                        currencyFmt = currencyFmt,
                                    )
                                }

                                // ── Divider before aggregate totals ──────────────────
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )

                                // ── Aggregate totals (two columns only — no empty third) ──
                                CreditCardTotalsRow(
                                    accounts = state.creditAccounts,
                                    reimbursable = state.currentMonthReimbursable,
                                    includeReimbursements = state.includeReimbursements,
                                    currencyFmt = currencyFmt,
                                )

                                // ── Splitwise FilterChip (only when connected) ─────────
                                if (state.isSplitwiseConnected) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                    FilterChip(
                                        selected = state.includeReimbursements,
                                        onClick = vm::toggleIncludeReimbursements,
                                        label = { Text("Subtract Splitwise reimbursable") },
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        CategoryBreakdownCard(
                            breakdown = state.categoryBreakdown,
                            spendingWindow = state.spendingWindow,
                            dateRangeLabel = state.spendingDateRangeLabel,
                            onWindowChange = vm::setSpendingWindow,
                            filterAccounts = state.creditAccounts,
                            selectedSpendingAccountIds = state.selectedSpendingAccountIds,
                            onToggleAccount = vm::toggleSpendingAccount,
                            onCategoryTap = vm::openCategoryDrillDown,
                            onManage = vm::openAllTransactionsDrillDown,
                            currencyFmt = currencyFmt,
                            spendingHistoryByMonth = state.spendingHistoryByMonth,
                            spendingHistoryByMonthAndMerchant = state.spendingHistoryByMonthAndMerchant,
                            allHistoryCategories = state.allHistoryCategories,
                            topMerchantsForHistory = state.topMerchantsForHistory,
                            historySelectedCategories = state.historySelectedCategories,
                            historySelectedMerchants = state.historySelectedMerchants,
                            onSetHistoryCategoryFilter = vm::setHistoryCategoryFilter,
                            onSetHistoryMerchantFilter = vm::setHistoryMerchantFilter,
                            onClearHistoryFilters = vm::clearHistoryFilters,
                            balanceSnapshotsByMonth = state.balanceSnapshotsByMonth,
                            selectedAccountNames = state.effectiveSpendingAccounts.map { it.name },
                        )
                    }
                }

                // ── Splitwise ────────────────────────────────────────────────────
                if (state.isSplitwiseConnected) {
                    item {
                        SplitwiseMonthCard(
                            selectedMonth = state.selectedMonth,
                            reimbursable = state.monthlyReimbursable,
                            isLoading = state.isSplitwiseLoading,
                            onPreviousMonth = vm::previousMonth,
                            onNextMonth = vm::nextMonth,
                            currencyFmt = currencyFmt,
                        )
                    }
                }

                if (state.isSplitwiseConnected && BuildConfig.RECONCILIATION_ENABLED) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ReconcileEntryCard(count = state.inboxCount, onClick = onReconcile)
                    }
                }

                // ── Empty state — nothing connected at all ───────────────────────
                if (state.creditAccounts.isEmpty() && !state.isSplitwiseConnected) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "No data yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Connect a bank or Splitwise in the Accounts tab.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared section label ──────────────────────────────────────────────────────

@Composable
private fun FinancesSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

// ── Reconcile entry card ──────────────────────────────────────────────────────

@Composable
private fun ReconcileEntryCard(count: Int, onClick: () -> Unit) {
    val highlighted = count > 0
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        // medium = 12dp for list-level cards
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant,
        ),
        // tonalElevation lifts the surface tint; 0 on primaryContainer (already chromatic),
        // 2dp on neutral surfaceVariant to provide a slight tonal lift vs the background.
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            // tonalElevation is the correct M3 lever for surface tinting — no drop shadow.
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                // Decorative alongside the adjacent text label; the label conveys the action.
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (highlighted) "Splitwise Expenses · $count to link"
                       else "Splitwise Expenses",
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                // Decorative chevron — the card's onClick handles the navigation action.
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Credit card summary card ──────────────────────────────────────────────────

/**
 * Per-account summary card rendered inside the unified CC outer card.
 *
 * [isBusinessCard] is true when Plaid supplies no statement cycle dates
 * (lastStatementDate == null && nextDueDate == null). Business CCs only show
 * the Current column — Statement and Due Date are hidden because Plaid does not
 * report them for this card type (not because the data is unavailable or estimated).
 */
@Composable
internal fun CreditCardSummaryCard(
    account: AccountEntity,
    isBusinessCard: Boolean,
    currencyFmt: NumberFormat,
) {
    // Inner sub-cards use surfaceContainerHigh to lift visually above the
    // surfaceContainerLow outer card. shapes.medium (12dp) = list-level card standard.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (!isBusinessCard) {
                    // Standard CC: show Statement balance in the first column.
                    LabeledAmount(
                        label = "Statement",
                        amount = account.statementBalance,
                        currencyFmt = currencyFmt,
                        amountStyle = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Current balance is always shown regardless of card type.
                LabeledAmount(
                    label = "Current",
                    amount = account.currentBalance,
                    currencyFmt = currencyFmt,
                    amountStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!isBusinessCard) {
                    // Standard CC: Due Date in the third column.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Due Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = account.nextDueDate ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ── Credit card totals row ────────────────────────────────────────────────────

/**
 * Aggregate totals row rendered inside the unified CC outer card.
 * Two columns only — Statement and Current. The empty third column is dropped;
 * Due Date is per-account and has no meaningful aggregate. reimbursable is always
 * currentMonthReimbursable (the live current-month value, not the browsed month).
 */
@Composable
private fun CreditCardTotalsRow(
    accounts: List<AccountEntity>,
    reimbursable: Double,
    includeReimbursements: Boolean,
    currencyFmt: NumberFormat,
) {
    val hasStatements = accounts.any { it.statementBalance != null }
    val totalStatement = if (hasStatements) accounts.sumOf { it.statementBalance ?: 0.0 } else null
    val totalCurrent = accounts.sumOf { it.currentBalance ?: 0.0 }
    val statementDisplay = if (includeReimbursements) totalStatement?.minus(reimbursable) else totalStatement
    val currentDisplay = if (includeReimbursements) totalCurrent - reimbursable else totalCurrent
    // No Card wrapper — the outer CC card provides the visual container.
    // Padding matches the inner sub-cards' horizontal padding for alignment.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasStatements) {
            LabeledAmount(
                label = if (includeReimbursements) "Net Statement" else "Total Statement",
                amount = statementDisplay,
                currencyFmt = currencyFmt,
                modifier = Modifier.weight(1f),
            )
        }
        LabeledAmount(
            label = if (includeReimbursements) "Net Current" else "Total Current",
            amount = currentDisplay,
            currencyFmt = currencyFmt,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Splitwise month card ──────────────────────────────────────────────────────

@Composable
private fun SplitwiseMonthCard(
    selectedMonth: YearMonth,
    reimbursable: Double,
    isLoading: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    currencyFmt: NumberFormat,
) {
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val isCurrentMonth = selectedMonth >= YearMonth.now()
    // shapes.large = 16dp for section-level cards (Splitwise summary is a standalone section)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Splitwise",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // M3 IconButton defaults to 48dp×48dp touch target — no size override needed.
                    IconButton(onClick = onPreviousMonth) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous month",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = selectedMonth.format(monthFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(96.dp),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(
                        onClick = onNextMonth,
                        enabled = !isCurrentMonth,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next month",
                            modifier = Modifier.size(18.dp),
                            tint = if (isCurrentMonth) MaterialTheme.colorScheme.outlineVariant
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Column {
                Text(
                    text = "Reimbursable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    Text(
                        text = currencyFmt.format(reimbursable),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ── Category Breakdown ────────────────────────────────────────────────────────

/** Truncates long card names to a chip-friendly label, e.g. "Chase Sapphire Preferred" → "Sapphire". */
private fun String.shortCardName(): String {
    val skipWords = setOf("card", "credit", "cash", "the", "bank", "of", "america", "rewards")
    val parts = split(" ").filter { it.lowercase() !in skipWords }
    return parts.take(2).joinToString(" ").ifEmpty { this }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryBreakdownCard(
    breakdown: List<CategorySpend>,
    spendingWindow: SpendingWindow,
    dateRangeLabel: String?,
    onWindowChange: (SpendingWindow) -> Unit,
    filterAccounts: List<AccountEntity>,
    selectedSpendingAccountIds: Set<String>?,
    onToggleAccount: (String) -> Unit,
    onCategoryTap: (String) -> Unit,
    onManage: () -> Unit,
    currencyFmt: NumberFormat,
    spendingHistoryByMonth: List<MonthlySpendingHistory>,
    spendingHistoryByMonthAndMerchant: List<MonthlyMerchantHistory>,
    allHistoryCategories: List<CategoryAmount>,
    topMerchantsForHistory: List<MerchantSpendSummary>,
    historySelectedCategories: Set<String>?,
    historySelectedMerchants: Set<String>?,
    onSetHistoryCategoryFilter: (Set<String>?) -> Unit,
    onSetHistoryMerchantFilter: (Set<String>?) -> Unit,
    onClearHistoryFilters: () -> Unit,
    balanceSnapshotsByMonth: List<MonthlyBalanceSnapshot>,
    selectedAccountNames: List<String>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    val displayList = if (expanded || breakdown.size <= 4) breakdown else breakdown.take(4)

    // History sheet — only rendered when triggered via the BarChart icon
    if (showHistorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SpendingHistorySheet(
                spendingHistoryByMonth = spendingHistoryByMonth,
                spendingHistoryByMonthAndMerchant = spendingHistoryByMonthAndMerchant,
                allHistoryCategories = allHistoryCategories,
                topMerchantsForHistory = topMerchantsForHistory,
                historySelectedCategories = historySelectedCategories,
                historySelectedMerchants = historySelectedMerchants,
                onSetHistoryCategoryFilter = onSetHistoryCategoryFilter,
                onSetHistoryMerchantFilter = onSetHistoryMerchantFilter,
                onClearHistoryFilters = onClearHistoryFilters,
                selectedAccountNames = selectedAccountNames,
                currencyFmt = currencyFmt,
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
        ) {
            // Header row: title | [tune icon] [window dropdown]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Spending",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (breakdown.isNotEmpty()) {
                        IconButton(
                            onClick = onManage,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Manage transactions",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // History sheet entry point — same visual weight as the Tune icon
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = "Spending and balance history",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SpendingWindowDropdown(selected = spendingWindow, onSelect = onWindowChange)
                }
            }
            // Date range subtitle
            if (dateRangeLabel != null) {
                Text(
                    text = dateRangeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Account filter chips — only when 2+ cards connected
            if (filterAccounts.size > 1) {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(filterAccounts, key = { it.accountId }) { account ->
                        val selected = selectedSpendingAccountIds?.contains(account.accountId) ?: true
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleAccount(account.accountId) },
                            label = { Text(account.name.shortCardName()) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            // Donut chart — shown whenever there's data to display
            if (breakdown.isNotEmpty()) {
                SpendingDonutChart(
                    breakdown = breakdown,
                    onSegmentTap = onCategoryTap,
                    currencyFmt = currencyFmt,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 8.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (breakdown.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No credit card transactions for this period",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                displayList.forEachIndexed { index, spend ->
                    CategoryRow(spend, onCategoryTap, currencyFmt)
                    if (index < displayList.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
                if (breakdown.size > 4) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    ) {
                        Text(
                            text = if (expanded) "Show less" else "+${breakdown.size - 4} more categories",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingWindowDropdown(
    selected: SpendingWindow,
    onSelect: (SpendingWindow) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Change spending period",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SpendingWindow.entries.forEach { window ->
                DropdownMenuItem(
                    text = { Text(window.label) },
                    onClick = { onSelect(window); expanded = false },
                )
            }
        }
    }
}

// ── Donut chart ───────────────────────────────────────────────────────────────

private val donutSegmentColors = listOf(
    Color(0xFF6750A4), Color(0xFF0286C2), Color(0xFF00897B), Color(0xFFE65100),
    Color(0xFF558B2F), Color(0xFF7B1FA2), Color(0xFFC62828), Color(0xFF00838F),
    Color(0xFFF9A825), Color(0xFF4527A0), Color(0xFF2E7D32), Color(0xFFAD1457),
)

@Composable
private fun SpendingDonutChart(
    breakdown: List<CategorySpend>,
    onSegmentTap: (String) -> Unit,
    currencyFmt: NumberFormat,
    modifier: Modifier = Modifier,
) {
    val total = breakdown.sumOf { it.totalAmount }.toFloat()
    if (total <= 0f) return

    val sweepAngles = remember(breakdown) {
        breakdown.map { (it.totalAmount / total * 360f).toFloat() }
    }
    val colors = remember(breakdown) {
        breakdown.mapIndexed { i, _ -> donutSegmentColors[i % donutSegmentColors.size] }
    }

    // Entry animation: arcs sweep in from 0 → full on first composition
    var animatedIn by remember { mutableStateOf(false) }
    LaunchedEffect(breakdown) { animatedIn = true }
    val progress by animateFloatAsState(
        targetValue = if (animatedIn) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "donut_entry",
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .pointerInput(breakdown) {
                detectTapGestures { offset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = offset.x - cx
                    val dy = offset.y - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    val innerR = size.width * 0.275f
                    val outerR = size.width * 0.5f
                    if (dist < innerR || dist > outerR) return@detectTapGestures
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angle < 0f) angle += 360f
                    var cumulative = 0f
                    breakdown.forEachIndexed { i, spend ->
                        val end = cumulative + sweepAngles[i]
                        if (angle in cumulative..end) {
                            onSegmentTap(spend.effectiveCategory)
                            return@detectTapGestures
                        }
                        cumulative = end
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.width * 0.22f
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            val gapDeg = if (breakdown.size > 1) 1.5f else 0f
            var startAngle = -90f
            breakdown.forEachIndexed { i, _ ->
                val raw = sweepAngles[i]
                val sweep = ((raw - gapDeg).coerceAtLeast(0.5f)) * progress
                drawArc(
                    color = colors[i],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += raw
            }
        }
        // Center label: total spend
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currencyFmt.format(total.toDouble()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    spend: CategorySpend,
    onTap: (String) -> Unit,
    currencyFmt: NumberFormat,
) {
    val meta = CategoryMeta.get(spend.effectiveCategory)
    // Minimum touch target: padding(vertical = 12.dp) + bodyMedium (≈20sp) ≥ 48dp total row height.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "View ${meta.displayName} transactions",
            ) { onTap(spend.effectiveCategory) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category emoji pill — surfaceContainerHighest background adds a subtle visual
        // container that differentiates the emoji from the plain text content.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            // Emoji glyph — decorative; meaning conveyed by adjacent displayName text.
            Text(
                text = meta.emoji,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = meta.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Transaction count chip — compact label style
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(end = 10.dp),
        ) {
            Text(
                text = "${spend.txCount}×",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(
            text = currencyFmt.format(spend.totalAmount),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Category Drill-Down Sheet ─────────────────────────────────────────────────

@Composable
private fun CategoryDrillDownSheet(
    category: String?,
    transactions: List<PlaidTransaction>,
    currencyFmt: NumberFormat,
    onEditCategory: (PlaidTransaction) -> Unit,
    onDismiss: () -> Unit,
    isBulkMode: Boolean = false,
    isAllTransactionsMode: Boolean = false,
    bulkSelectedIds: Set<String> = emptySet(),
    sessionCategorizedIds: Set<String> = emptySet(),
    onEnterBulkMode: () -> Unit = {},
    onExitBulkMode: () -> Unit = {},
    onToggleBulkSelection: (String) -> Unit = {},
    onOpenBulkPicker: () -> Unit = {},
) {
    val meta = CategoryMeta.get(category ?: "OTHER")
    val rowBgSelected = MaterialTheme.colorScheme.secondaryContainer
    val rowBgDefault = MaterialTheme.colorScheme.surfaceContainerHigh
    // Outer Box so the sticky bottom bar can overlay the scroll content.
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                // Extra bottom padding in bulk mode so the sticky bar doesn't obscure last row.
                .padding(bottom = if (isBulkMode) 88.dp else 32.dp),
        ) {
            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isBulkMode) {
                    // Bulk mode: title left, X right — natural dismiss gesture
                    Text(
                        text = when {
                            bulkSelectedIds.isNotEmpty() -> "${bulkSelectedIds.size} selected"
                            isAllTransactionsMode -> "All Transactions"
                            else -> "Select transactions"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text(text = meta.emoji, style = MaterialTheme.typography.titleLarge) }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = meta.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = currencyFmt.format(transactions.sumOf { it.amount }),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(4.dp))

            // ── Transaction rows ───────────────────────────────────────────────
            transactions.forEach { tx ->
                val isSelected = tx.transactionId in bulkSelectedIds
                val isTouched = isAllTransactionsMode && tx.transactionId in sessionCategorizedIds
                val rowBgTouched = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                val rowBg by animateColorAsState(
                    when {
                        isBulkMode && isSelected -> rowBgSelected
                        isTouched -> rowBgTouched
                        else -> rowBgDefault
                    },
                    label = "row_bg",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .then(
                            if (isBulkMode)
                                Modifier.clickable { onToggleBulkSelection(tx.transactionId) }
                            else Modifier
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isBulkMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleBulkSelection(tx.transactionId) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tx.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isAllTransactionsMode) {
                            val txMeta = CategoryMeta.get(tx.effectiveCategory)
                            Spacer(Modifier.height(3.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = "${txMeta.emoji} ${txMeta.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (tx.categoryOverride != null) {
                            Text(
                                text = "Overridden",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    Text(
                        text = currencyFmt.format(tx.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = if (isBulkMode) 0.dp else 4.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isTouched) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Recategorized",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (!isBulkMode) {
                        IconButton(onClick = { onEditCategory(tx) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change category for ${tx.name}",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }

        // ── Sticky bulk bottom bar ─────────────────────────────────────────────
        if (isBulkMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (bulkSelectedIds.isEmpty()) "Tap rows to select"
                               else "Assign to ${bulkSelectedIds.size} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = onOpenBulkPicker,
                        enabled = bulkSelectedIds.isNotEmpty(),
                    ) {
                        Text("Category")
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Category Override Picker ──────────────────────────────────────────────────

@Composable
private fun CategoryPickerSheet(
    transaction: PlaidTransaction?,
    customCategories: List<String>,
    onPick: (String) -> Unit,
    onSaveCustomCategory: (String) -> Unit,
    onDeleteCustomCategory: (String) -> Unit,
    countTransactionsWithOverride: suspend (String) -> Int,
    onDismiss: () -> Unit,
    // Bulk mode: no pre-selection, custom title/subtitle
    isBulkMode: Boolean = false,
    bulkCount: Int = 0,
) {
    var customInput by rememberSaveable { mutableStateOf("") }
    var showChangeHint by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(showChangeHint) {
        if (showChangeHint) {
            kotlinx.coroutines.delay(3000)
            showChangeHint = false
        }
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    val quickPickCodes = remember { CategoryMeta.quickPicks.map { it.first }.toSet() }
    // In bulk mode there is no single pre-selected category — treat as unset.
    val effectiveCat = if (isBulkMode) null else (transaction?.effectiveCategory ?: "OTHER")
    val hasOverride = !isBulkMode && transaction?.categoryOverride != null

    // Selected floats to top; rest sorted alphabetically by display name
    val trulyCustomCategories = customCategories.filter { it !in quickPickCodes }
        .sortedWith(compareByDescending<String> { it == effectiveCat }
            .thenBy { CategoryMeta.get(it).displayName })

    // Effective category not covered by either list — surface it at top of Categories
    val effectiveCatInQuickPicks = effectiveCat != null && effectiveCat in quickPickCodes
    val effectiveCatInCustom = effectiveCat != null && effectiveCat in trulyCustomCategories

    pendingDelete?.let { (catName, count) ->
        val meta = CategoryMeta.get(catName)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove \"${meta.displayName}\"?") },
            text = {
                Text(
                    "$count transaction${if (count == 1) "" else "s"} using this category will " +
                    "revert to their default automatic category."
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteCustomCategory(catName); pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Banner is anchored above the scroll content so it's always visible regardless of
        // scroll position — the selected item floats to top where the user's thumb already is.
        AnimatedVisibility(
            visible = showChangeHint,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Tap a different category to change it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
        Text(
            text = if (isBulkMode) "Assign category" else "Change category",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (isBulkMode) {
            Text(
                text = "Applies to $bulkCount transaction${if (bulkCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        } else if (transaction?.name != null) {
            Text(
                text = transaction.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        } else {
            Spacer(Modifier.height(14.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        // "Your categories" — user-created names, with trailing delete icon
        if (trulyCustomCategories.isNotEmpty()) {
            Text(
                text = "Your categories",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            trulyCustomCategories.forEach { cat ->
                val meta = CategoryMeta.get(cat)
                CategoryPickerRow(
                    emoji = meta.emoji,
                    label = meta.displayName,
                    selected = !isBulkMode && effectiveCat == cat,
                    onPick = {
                        if (!isBulkMode && effectiveCat == cat) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showChangeHint = true
                        } else onPick(cat)
                    },
                    onDelete = {
                        scope.launch {
                            val count = countTransactionsWithOverride(cat)
                            if (count > 0) pendingDelete = cat to count
                            else onDeleteCustomCategory(cat)
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
        }

        // "Categories" — selected floats to top, rest sorted alphabetically.
        // Orphan: effective category not in quick picks or custom list — shown first.
        val sortedQuickPicks = remember(effectiveCat) {
            CategoryMeta.quickPicks.sortedWith(
                compareByDescending<Pair<String, CategoryMeta.Meta>> { it.first == effectiveCat }
                    .thenBy { it.second.displayName }
            )
        }
        Text(
            text = "Categories",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        // Suppress orphan when its display name already appears in quick picks — avoids
        // showing two identically-named rows (e.g. both Gym codes map to "Gym").
        val quickPickDisplayNames = remember { CategoryMeta.quickPicks.map { it.second.displayName }.toSet() }
        val orphanDisplayName = effectiveCat?.let { CategoryMeta.get(it).displayName }
        val showOrphan = effectiveCat != null && !effectiveCatInQuickPicks && !effectiveCatInCustom &&
            orphanDisplayName !in quickPickDisplayNames
        if (showOrphan) {
            val orphanMeta = CategoryMeta.get(effectiveCat)
            CategoryPickerRow(
                emoji = orphanMeta.emoji,
                label = orphanMeta.displayName,
                selected = true,
                onPick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showChangeHint = true
                },
            )
        }
        sortedQuickPicks.forEach { (code, meta) ->
            // Show as selected if effectiveCat is this code OR a sibling code with same displayName
            // (e.g. PERSONAL_CARE_GYMS → shows ENTERTAINMENT_GYMS quickPick as selected)
            val siblingSelected = effectiveCat != null && !effectiveCatInQuickPicks && !effectiveCatInCustom &&
                CategoryMeta.get(effectiveCat).displayName == meta.displayName
            CategoryPickerRow(
                emoji = meta.emoji,
                label = meta.displayName,
                selected = effectiveCat == code || siblingSelected,
                onPick = {
                    val isSameDisplay = effectiveCat != null &&
                        CategoryMeta.get(effectiveCat).displayName == meta.displayName
                    if (effectiveCat == code || (isSameDisplay && !effectiveCatInQuickPicks)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showChangeHint = true
                    } else onPick(code)
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        // Custom free-text entry — "Add" saves to list without auto-assigning
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = customInput,
                onValueChange = { customInput = it },
                label = { Text("Custom category") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(
                onClick = {
                    val name = customInput.trim()
                    if (name.isNotBlank()) {
                        onSaveCustomCategory(name)
                        customInput = ""
                        focusManager.clearFocus()
                        scope.launch { scrollState.animateScrollTo(0) }
                    }
                },
                enabled = customInput.isNotBlank(),
            ) {
                Text("Add")
            }
        }

        } // inner scrollable Column
    } // outer Column
}

@Composable
private fun CategoryPickerRow(
    emoji: String,
    label: String,
    selected: Boolean,
    onPick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Select $label") { onPick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = emoji,
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Spending History Sheet (stacked bar chart) ────────────────────────────────

// M3-token-anchored palette for top-5 categories. These are stable Color values derived
// from M3 seed colors. We cannot use MaterialTheme tokens inside Canvas drawscope, so
// we resolve them once in the Composable scope and pass them to the draw functions.
private val historyBarPalette = listOf(
    Color(0xFF6750A4), // primary-ish (purple)
    Color(0xFF0286C2), // tertiary-ish (teal-blue)
    Color(0xFF00897B), // secondary-ish (teal-green)
    Color(0xFFE65100), // error-ish (orange)
    Color(0xFF558B2F), // on-surface-variant-ish (green)
)

// Short month label formatter, e.g. "Apr 25"
private val barMonthFmt = DateTimeFormatter.ofPattern("MMM yy")

/**
 * Builds the color-assignment map for the chart.
 *
 * When [selectedCategories] is null (all selected), the top-5 categories by 12-month spend
 * get distinct palette colors; remaining categories group into the "Misc" bucket colored
 * with [miscColor]. When an explicit selection is active, colors are reassigned fresh from
 * palette to the selected categories and Misc disappears.
 *
 * Returns: Map<effectiveCategory or "Misc" → Color>
 */
private fun buildHistoryColorMap(
    allCategories: List<CategoryAmount>,
    selectedCategories: Set<String>?,
    palette: List<Color>,
    miscColor: Color,
): Map<String, Color> {
    val categories = if (selectedCategories == null) {
        allCategories
    } else {
        allCategories.filter { it.effectiveCategory in selectedCategories }
    }
    val top5 = categories.take(5)
    val result = mutableMapOf<String, Color>()
    top5.forEachIndexed { i, cat -> result[cat.effectiveCategory] = palette[i % palette.size] }
    if (selectedCategories == null && categories.size > 5) {
        result["Misc"] = miscColor
    }
    return result
}

/**
 * Builds per-bar segment data for a single month.
 *
 * When no merchant filter is active, groups by category. When a merchant filter is active,
 * re-aggregates from the merchant history and assigns each selected merchant its own color
 * slice, dropping the Misc bucket entirely.
 *
 * [colorMap] maps category key → Color; for merchant mode we build a merchant→Color map
 * inline using the same palette.
 *
 * Returns List<Pair<label, amount, Color>> for each segment, bottom to top.
 */
private data class BarSegment(val label: String, val amount: Double, val color: Color)

private fun buildBarSegments(
    monthHistory: MonthlySpendingHistory,
    monthMerchantHistory: MonthlyMerchantHistory?,
    selectedCategories: Set<String>?,
    selectedMerchants: Set<String>?,
    colorMap: Map<String, Color>,
    miscColor: Color,
    allCategories: List<CategoryAmount>,
    palette: List<Color>,
): List<BarSegment> {
    // Merchant filter active — group by merchant name
    if (selectedMerchants != null && monthMerchantHistory != null) {
        val merchantColorMap = mutableMapOf<String, Color>()
        val filteredMerchants = monthMerchantHistory.merchants
            .filter { it.merchantName in selectedMerchants }
        filteredMerchants.forEachIndexed { i, m ->
            merchantColorMap[m.merchantName] = palette[i % palette.size]
        }
        return filteredMerchants.map { m ->
            BarSegment(m.merchantName, m.totalAmount, merchantColorMap[m.merchantName] ?: miscColor)
        }
    }

    // Category filter or no filter — group by effectiveCategory
    val topCategoryKeys = allCategories.take(5).map { it.effectiveCategory }.toSet()
    val segments = mutableListOf<BarSegment>()
    var miscTotal = 0.0

    monthHistory.categories.forEach { cat ->
        val isSelected = selectedCategories == null || cat.effectiveCategory in selectedCategories
        if (!isSelected) return@forEach

        val color = colorMap[cat.effectiveCategory]
        if (color != null) {
            segments.add(BarSegment(
                label = CategoryMeta.get(cat.effectiveCategory).displayName,
                amount = cat.totalAmount,
                color = color,
            ))
        } else if (selectedCategories == null) {
            // Not in top-5 and no explicit filter → group into Misc
            miscTotal += cat.totalAmount
        }
    }

    if (miscTotal > 0.0 && selectedCategories == null) {
        segments.add(BarSegment("Misc", miscTotal, miscColor))
    }

    return segments
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpendingHistorySheet(
    spendingHistoryByMonth: List<MonthlySpendingHistory>,
    spendingHistoryByMonthAndMerchant: List<MonthlyMerchantHistory>,
    allHistoryCategories: List<CategoryAmount>,
    topMerchantsForHistory: List<MerchantSpendSummary>,
    historySelectedCategories: Set<String>?,
    historySelectedMerchants: Set<String>?,
    onSetHistoryCategoryFilter: (Set<String>?) -> Unit,
    onSetHistoryMerchantFilter: (Set<String>?) -> Unit,
    onClearHistoryFilters: () -> Unit,
    selectedAccountNames: List<String>,
    currencyFmt: NumberFormat,
) {
    // Dialog navigation state — local to this sheet, not in ViewModel.
    // "category" | "merchant" | null
    var filterDialogPage by rememberSaveable { mutableStateOf<String?>(null) }

    // Resolve colors in composable scope so we can pass them to Canvas helpers.
    val miscColor = MaterialTheme.colorScheme.outlineVariant
    val palette = historyBarPalette

    val colorMap = remember(allHistoryCategories, historySelectedCategories) {
        buildHistoryColorMap(allHistoryCategories, historySelectedCategories, palette, miscColor)
    }

    // Filter dialogs
    if (filterDialogPage == "category") {
        HistoryCategoryFilterDialog(
            allCategories = allHistoryCategories,
            selectedCategories = historySelectedCategories,
            onConfirm = { selected ->
                onSetHistoryCategoryFilter(selected)
                filterDialogPage = null
            },
            onDismiss = { filterDialogPage = null },
            onNavigateToMerchants = { filterDialogPage = "merchant" },
        )
    }
    if (filterDialogPage == "merchant") {
        HistoryMerchantFilterDialog(
            allMerchants = topMerchantsForHistory,
            selectedMerchants = historySelectedMerchants,
            historySelectedCategories = historySelectedCategories,
            onConfirm = { selected ->
                onSetHistoryMerchantFilter(selected)
                filterDialogPage = null
            },
            onDismiss = { filterDialogPage = null },
            onNavigateToCategories = { filterDialogPage = "category" },
        )
    }

    // Build active filter summary for the label below the chart
    val hasActiveFilters = historySelectedCategories != null || historySelectedMerchants != null
    val filterSummaryText = remember(historySelectedCategories, historySelectedMerchants, allHistoryCategories, topMerchantsForHistory) {
        buildFilterSummaryText(historySelectedCategories, historySelectedMerchants, allHistoryCategories, topMerchantsForHistory)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        // ── Sheet header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Spending History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (selectedAccountNames.isNotEmpty()) {
                    Text(
                        text = selectedAccountNames.joinToString(", ") { it.shortCardName() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Filter entry point — FilterList icon in trailing slot
            IconButton(onClick = { filterDialogPage = "category" }) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = "Filter spending history",
                    tint = if (hasActiveFilters)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Stacked bar chart ─────────────────────────────────────────────────
        if (spendingHistoryByMonth.isNotEmpty()) {
            HistoryStackedBarChart(
                spendingHistoryByMonth = spendingHistoryByMonth,
                spendingHistoryByMonthAndMerchant = spendingHistoryByMonthAndMerchant,
                allHistoryCategories = allHistoryCategories,
                historySelectedCategories = historySelectedCategories,
                historySelectedMerchants = historySelectedMerchants,
                colorMap = colorMap,
                palette = palette,
                miscColor = miscColor,
                currencyFmt = currencyFmt,
            )

            // ── Active filter summary label + Clear inline ──────────────────
            AnimatedVisibility(
                visible = hasActiveFilters,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = filterSummaryText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onClearHistoryFilters,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        } else {
            // Empty state when no history loaded yet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No spending history yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Horizontally scrollable stacked bar chart.
 *
 * Bars are 48dp wide with 10dp gaps. Canvas height is 160dp.
 * Y-axis labels ($1k, $2k, ...) drawn on the left side, scaled to max month total.
 * Gridlines at each Y label.
 * Pre-scrolled to the rightmost (newest) month on first composition.
 *
 * Tapping a bar shows an [AnimatedVisibility] tooltip above the tapped bar.
 */
@Composable
private fun HistoryStackedBarChart(
    spendingHistoryByMonth: List<MonthlySpendingHistory>,
    spendingHistoryByMonthAndMerchant: List<MonthlyMerchantHistory>,
    allHistoryCategories: List<CategoryAmount>,
    historySelectedCategories: Set<String>?,
    historySelectedMerchants: Set<String>?,
    colorMap: Map<String, Color>,
    palette: List<Color>,
    miscColor: Color,
    currencyFmt: NumberFormat,
) {
    // Bar chart is ordered oldest→newest (left→right). History comes newest-first so reverse it.
    val orderedHistory = remember(spendingHistoryByMonth) { spendingHistoryByMonth.reversed() }
    val orderedMerchantHistory = remember(spendingHistoryByMonthAndMerchant) {
        spendingHistoryByMonthAndMerchant.reversed()
    }
    val merchantHistoryByMonth = remember(orderedMerchantHistory) {
        orderedMerchantHistory.associateBy { it.month }
    }

    val density = LocalDensity.current
    val barWidthDp = 48.dp
    val gapDp = 10.dp
    val chartHeightDp = 160.dp
    val yAxisWidthDp = 44.dp
    val barWidthPx = with(density) { barWidthDp.toPx() }
    val gapPx = with(density) { gapDp.toPx() }
    val chartHeightPx = with(density) { chartHeightDp.toPx() }

    val totalCanvasWidthDp = yAxisWidthDp + (barWidthDp + gapDp) * orderedHistory.size + gapDp

    // Max total across all months — determines Y-axis scale.
    val maxMonthTotal = remember(orderedHistory, historySelectedCategories, historySelectedMerchants) {
        orderedHistory.maxOfOrNull { month ->
            val merchantHistory = merchantHistoryByMonth[month.month]
            val segs = buildBarSegments(
                month, merchantHistory, historySelectedCategories, historySelectedMerchants,
                colorMap, miscColor, allHistoryCategories, palette,
            )
            segs.sumOf { it.amount }
        } ?: 1.0
    }

    // Gridline values: 3–4 nice round levels from 0 to maxMonthTotal
    val gridValues = remember(maxMonthTotal) { buildGridValues(maxMonthTotal) }

    // Tooltip state — local rememberSaveable; NOT in ViewModel
    var tooltipBarIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    // Scroll state — pre-scroll to rightmost bar on first composition
    val scrollState = rememberScrollState()
    LaunchedEffect(orderedHistory.size) {
        if (orderedHistory.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // Resolve text colors in composable scope for use inside Canvas
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(modifier = Modifier.fillMaxWidth()) {
        // Fixed Y-axis labels drawn in a separate Box so they stay visible during scroll
        Box(
            modifier = Modifier
                .width(yAxisWidthDp)
                .height(chartHeightDp + 20.dp) // +20dp for month labels area
                .align(Alignment.TopStart),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartH = chartHeightPx
                gridValues.forEach { value ->
                    val y = chartH - (value / maxMonthTotal * chartH).toFloat()
                    // Gridline stub on Y-axis side
                    drawLine(
                        color = gridlineColor,
                        start = Offset(size.width - 4.dp.toPx(), y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    // Y-label text using nativeCanvas for precise positioning
                    val label = formatYLabel(value)
                    val paint = android.graphics.Paint().apply {
                        color = labelColor.toArgb()
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        size.width - 6.dp.toPx(),
                        y + 4.dp.toPx(),
                        paint,
                    )
                }
            }
        }

        // Scrollable chart area — starts to the right of the Y-axis
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = yAxisWidthDp)
                .horizontalScroll(scrollState),
        ) {
            Box(modifier = Modifier.width(totalCanvasWidthDp - yAxisWidthDp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeightDp + 20.dp) // extra for month labels
                        .pointerInput(orderedHistory, historySelectedCategories, historySelectedMerchants) {
                            detectTapGestures { offset ->
                                val xStart = gapPx
                                val tappedIndex = ((offset.x - xStart) / (barWidthPx + gapPx))
                                    .toInt()
                                    .coerceIn(0, orderedHistory.size - 1)
                                // Only register tap if within a bar, not the gap
                                val barLeft = xStart + tappedIndex * (barWidthPx + gapPx)
                                if (offset.x >= barLeft && offset.x <= barLeft + barWidthPx) {
                                    tooltipBarIndex = if (tooltipBarIndex == tappedIndex) null else tappedIndex
                                }
                            }
                        },
                ) {
                    val chartH = chartHeightPx

                    // Draw horizontal gridlines across the full canvas width
                    gridValues.forEach { value ->
                        val y = chartH - (value / maxMonthTotal * chartH).toFloat()
                        drawLine(
                            color = gridlineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    // Draw bars
                    orderedHistory.forEachIndexed { index, monthHistory ->
                        val merchantHistory = merchantHistoryByMonth[monthHistory.month]
                        val segments = buildBarSegments(
                            monthHistory, merchantHistory,
                            historySelectedCategories, historySelectedMerchants,
                            colorMap, miscColor, allHistoryCategories, palette,
                        )
                        val totalHeight = segments.sumOf { it.amount }
                        val barLeft = gapPx + index * (barWidthPx + gapPx)
                        var currentY = chartH

                        segments.forEach { seg ->
                            val segH = if (totalHeight > 0)
                                (seg.amount / maxMonthTotal * chartH).toFloat()
                            else 0f
                            val segTop = currentY - segH
                            drawRoundRect(
                                color = seg.color,
                                topLeft = Offset(barLeft, segTop),
                                size = Size(barWidthPx, segH),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                            )
                            currentY = segTop
                        }

                        // Month label below the bar
                        val label = monthHistory.month.format(barMonthFmt)
                        val labelPaint = android.graphics.Paint().apply {
                            color = onSurfaceColor.toArgb()
                            textSize = 9.dp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            barLeft + barWidthPx / 2f,
                            chartH + 14.dp.toPx(),
                            labelPaint,
                        )
                    }
                }

                // Tooltip overlay — rendered as a Compose Surface above the canvas
                tooltipBarIndex?.let { idx ->
                    if (idx in orderedHistory.indices) {
                        val monthHistory = orderedHistory[idx]
                        val merchantHistory = merchantHistoryByMonth[monthHistory.month]
                        val segments = buildBarSegments(
                            monthHistory, merchantHistory,
                            historySelectedCategories, historySelectedMerchants,
                            colorMap, miscColor, allHistoryCategories, palette,
                        )
                        // Position tooltip above the tapped bar
                        val barLeftDp = gapDp + (barWidthDp + gapDp) * idx
                        val tooltipTotal = segments.sumOf { it.amount }

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .offset(x = barLeftDp)
                                .wrapContentWidth(unbounded = true),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .requiredWidthIn(min = 140.dp, max = 200.dp)
                                    .padding(bottom = chartHeightDp - (tooltipTotal / maxMonthTotal * chartHeightDp.value).dp + 8.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = monthHistory.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    segments.forEach { seg ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(seg.color),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = seg.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                            )
                                            Text(
                                                text = currencyFmt.format(seg.amount),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    )
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Total",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = currencyFmt.format(tooltipTotal),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Builds 3–4 round gridline values scaled to the max month total. */
private fun buildGridValues(maxTotal: Double): List<Double> {
    if (maxTotal <= 0.0) return listOf(0.0)
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(maxTotal)))
    val step = when {
        maxTotal / magnitude <= 2 -> magnitude / 4
        maxTotal / magnitude <= 5 -> magnitude / 2
        else -> magnitude
    }
    val values = mutableListOf<Double>()
    var v = step
    while (v <= maxTotal && values.size < 4) {
        values.add(v)
        v += step
    }
    return values.ifEmpty { listOf(maxTotal) }
}

/** Formats a Y-axis dollar value as "$1k", "$2.5k", "$10k", etc. */
private fun formatYLabel(value: Double): String {
    return when {
        value >= 1000 -> {
            val k = value / 1000
            if (k == k.toLong().toDouble()) "$${k.toLong()}k" else "$${"%.1f".format(k)}k"
        }
        else -> "$${"%.0f".format(value)}"
    }
}

/** Builds the compact filter summary string shown below the chart. */
private fun buildFilterSummaryText(
    selectedCategories: Set<String>?,
    selectedMerchants: Set<String>?,
    allCategories: List<CategoryAmount>,
    allMerchants: List<MerchantSpendSummary>,
): String {
    val parts = mutableListOf<String>()

    if (selectedCategories != null) {
        val sorted = allCategories
            .filter { it.effectiveCategory in selectedCategories }
            .map { CategoryMeta.get(it.effectiveCategory).displayName }
        when {
            sorted.isEmpty() -> parts.add("No categories")
            sorted.size <= 2 -> parts.add(sorted.joinToString(", "))
            else -> parts.add("${sorted.take(2).joinToString(", ")} +${sorted.size - 2}")
        }
    }

    if (selectedMerchants != null) {
        val sorted = allMerchants
            .filter { it.merchantName in selectedMerchants }
            .map { it.merchantName }
        when {
            sorted.isEmpty() -> parts.add("No merchants")
            sorted.size <= 2 -> parts.add(sorted.joinToString(", "))
            else -> parts.add("${sorted.take(2).joinToString(", ")} +${sorted.size - 2}")
        }
    }

    return parts.joinToString(" · ").ifEmpty { "" }
}

// ── History Category Filter Dialog ────────────────────────────────────────────

@Composable
private fun HistoryCategoryFilterDialog(
    allCategories: List<CategoryAmount>,
    selectedCategories: Set<String>?,
    onConfirm: (Set<String>?) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToMerchants: () -> Unit,
) {
    // Dialog-local checked state: initialize from external state.
    // null selectedCategories (all) → all boxes checked
    val allKeys = remember(allCategories) { allCategories.map { it.effectiveCategory }.toSet() }
    var checkedKeys by remember(selectedCategories, allKeys) {
        mutableStateOf(selectedCategories ?: allKeys)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { checkedKeys = allKeys },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Select All", style = MaterialTheme.typography.labelMedium) }
                TextButton(
                    onClick = { checkedKeys = emptySet() },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Clear All", style = MaterialTheme.typography.labelMedium) }
            }
        },
        text = {
            Column {
                LazyColumn(
                    modifier = Modifier.requiredWidthIn(min = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(allCategories, key = { it.effectiveCategory }) { cat ->
                        val meta = CategoryMeta.get(cat.effectiveCategory)
                        val checked = cat.effectiveCategory in checkedKeys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checkedKeys = if (checked)
                                        checkedKeys - cat.effectiveCategory
                                    else
                                        checkedKeys + cat.effectiveCategory
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    checkedKeys = if (it)
                                        checkedKeys + cat.effectiveCategory
                                    else
                                        checkedKeys - cat.effectiveCategory
                                },
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = meta.emoji,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                text = meta.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // Merchants navigation button at bottom of list
                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                // Apply current category selection first, then navigate
                                val result = if (checkedKeys == allKeys) null
                                    else if (checkedKeys.isEmpty()) emptySet()
                                    else checkedKeys
                                onConfirm(result)
                                onNavigateToMerchants()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Merchants →",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // null = all selected (boxes match the full set); emptySet = explicit clear
                val result = when {
                    checkedKeys == allKeys -> null
                    checkedKeys.isEmpty() -> emptySet()
                    else -> checkedKeys
                }
                onConfirm(result)
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── History Merchant Filter Dialog ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryMerchantFilterDialog(
    allMerchants: List<MerchantSpendSummary>,
    selectedMerchants: Set<String>?,
    historySelectedCategories: Set<String>?,
    onConfirm: (Set<String>?) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToCategories: () -> Unit,
) {
    // Smart filtering: if category filter is active, only show merchants in those categories
    val visibleMerchants = remember(allMerchants, historySelectedCategories) {
        if (historySelectedCategories == null) allMerchants
        else allMerchants.filter { it.primaryCategory in historySelectedCategories }
    }
    val allKeys = remember(visibleMerchants) { visibleMerchants.map { it.merchantName }.toSet() }
    var checkedKeys by remember(selectedMerchants, allKeys) {
        mutableStateOf(selectedMerchants ?: allKeys)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredMerchants = remember(visibleMerchants, searchQuery) {
        if (searchQuery.isBlank()) visibleMerchants
        else visibleMerchants.filter { it.merchantName.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onNavigateToCategories,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text("← Categories", style = MaterialTheme.typography.labelMedium) }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { checkedKeys = allKeys },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("Select All", style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = { checkedKeys = emptySet() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("Clear All", style = MaterialTheme.typography.labelMedium) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search merchants") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.requiredWidthIn(min = 280.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filteredMerchants, key = { it.merchantName }) { merchant ->
                    val checked = merchant.merchantName in checkedKeys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checkedKeys = if (checked)
                                    checkedKeys - merchant.merchantName
                                else
                                    checkedKeys + merchant.merchantName
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                checkedKeys = if (it)
                                    checkedKeys + merchant.merchantName
                                else
                                    checkedKeys - merchant.merchantName
                            },
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = merchant.merchantName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val result = when {
                    checkedKeys == allKeys -> null
                    checkedKeys.isEmpty() -> emptySet()
                    else -> checkedKeys
                }
                onConfirm(result)
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Shared labeled amount ─────────────────────────────────────────────────────

@Composable
internal fun LabeledAmount(
    label: String,
    amount: Double?,
    currencyFmt: NumberFormat,
    modifier: Modifier = Modifier,
    // Caller can override the amount text style; defaults to bodySmall for compact contexts.
    amountStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (amount != null) currencyFmt.format(amount) else "—",
            style = amountStyle,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
