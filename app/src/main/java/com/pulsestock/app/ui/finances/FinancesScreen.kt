package com.pulsestock.app.ui.finances

import com.pulsestock.app.data.poarvault.CustomCategory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.state.ToggleableState
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
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.pulsestock.app.BuildConfig
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.CategorySpend
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.effectiveCategory
import com.pulsestock.app.data.poarvault.plaidFallbackCategory
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
                customCategoriesMap = state.customCategoriesMap,
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
                customCategoriesMap = state.customCategoriesMap,
                onPick = { category -> vm.applyOverride(overridingTx.transactionId, category) },
                onRemoveOverride = { vm.removeOverride(overridingTx.transactionId) },
                onRemoveRule = vm::removeRuleForMerchant,
                onSaveCustomCategory = { name -> vm.saveCustomCategory(name) },
                onDeleteCustomCategory = { name -> vm.deleteCustomCategory(name) },
                countTransactionsWithOverride = vm::countTransactionsWithOverride,
                countOverridesForMerchant = vm::countOverridesForMerchant,
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
                customCategoriesMap = state.customCategoriesMap,
                onPick = { category -> vm.applyBulkCategory(category) },
                onRemoveOverride = {},
                onRemoveRule = {},
                onSaveCustomCategory = { name -> vm.saveCustomCategory(name) },
                onDeleteCustomCategory = { name -> vm.deleteCustomCategory(name) },
                countTransactionsWithOverride = { name -> vm.countTransactionsWithOverride(name) },
                countOverridesForMerchant = { 0 },
                onDismiss = vm::closeBulkPicker,
                isBulkMode = true,
                bulkCount = state.bulkSelectedIds.size,
            )
        }
    }

    // Merchant rule confirmation dialog
    val pendingApply = state.pendingApplyState
    if (pendingApply != null && pendingApply.proposals.isNotEmpty()) {
        val proposal = pendingApply.proposals.first()
        val proposalMeta = CategoryMeta.resolveMeta(pendingApply.categoryId, state.customCategoriesMap)
        AlertDialog(
            onDismissRequest = vm::cancelApplyOverride,
            title = { Text("Apply override?") },
            text = {
                Text(
                    "Also set ${proposalMeta.emoji} ${proposalMeta.displayName} for " +
                    "${proposal.otherCount} other ${proposal.merchantName} " +
                    "transaction${if (proposal.otherCount == 1) "" else "s"} " +
                    "and save as a rule for future syncs."
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::confirmApplyToAll,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Apply to all") }
                    Button(
                        onClick = vm::confirmJustThisOne,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                    ) { Text(if (pendingApply.transactionIds.size > 1) "Just selected" else "Just this one") }
                }
            }
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
                            isSplitwiseConnected = state.isSplitwiseConnected,
                            includeReimbursements = state.includeReimbursements,
                            onToggleReimbursements = vm::toggleIncludeReimbursements,
                            currentMonthReimbursable = state.currentMonthReimbursable,
                            reimbursableByMonth = state.reimbursableByMonth,
                            spendingHistoryByMonth = state.spendingHistoryByMonth,
                            spendingHistoryByMonthAndMerchant = state.spendingHistoryByMonthAndMerchant,
                            allHistoryCategories = state.allHistoryCategories,
                            topMerchantsForHistory = state.topMerchantsForHistory,
                            historySelectedCategories = state.historySelectedCategories,
                            historySelectedMerchants = state.historySelectedMerchants,
                            onSetHistoryCategoryFilter = vm::setHistoryCategoryFilter,
                            onSetHistoryMerchantFilter = vm::setHistoryMerchantFilter,
                            onSetHistoryAccountFilter = vm::setHistoryAccountFilter,
                            onClearHistoryFilters = vm::clearHistoryFilters,
                            balanceSnapshotsByMonth = state.balanceSnapshotsByMonth,
                            allCreditAccounts = state.creditAccounts,
                            historySelectedAccountIds = state.historySelectedAccountIds,
                            customCategoriesMap = state.customCategoriesMap,
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

/**
 * Converts Plaid's ALL-CAPS raw merchant names to title case.
 * e.g. "AUTOMATIC PAYMENT - THANK" → "Automatic Payment - Thank"
 */
private fun String.toTitleCase(): String =
    split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercaseChar() }
    }

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
    isSplitwiseConnected: Boolean,
    includeReimbursements: Boolean,
    onToggleReimbursements: () -> Unit,
    currentMonthReimbursable: Double,
    reimbursableByMonth: Map<YearMonth, Double>,
    spendingHistoryByMonth: List<MonthlySpendingHistory>,
    spendingHistoryByMonthAndMerchant: List<MonthlyMerchantHistory>,
    allHistoryCategories: List<CategoryAmount>,
    topMerchantsForHistory: List<MerchantSpendSummary>,
    historySelectedCategories: Set<String>?,
    historySelectedMerchants: Set<String>?,
    onSetHistoryCategoryFilter: (Set<String>?) -> Unit,
    onSetHistoryMerchantFilter: (Set<String>?) -> Unit,
    onSetHistoryAccountFilter: (Set<String>?) -> Unit,
    onClearHistoryFilters: () -> Unit,
    balanceSnapshotsByMonth: List<MonthlyBalanceSnapshot>,
    allCreditAccounts: List<AccountEntity>,
    historySelectedAccountIds: Set<String>?,
    customCategoriesMap: Map<String, CustomCategory>,
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
                onSetHistoryAccountFilter = onSetHistoryAccountFilter,
                onClearHistoryFilters = onClearHistoryFilters,
                allCreditAccounts = allCreditAccounts,
                historySelectedAccountIds = historySelectedAccountIds,
                isSplitwiseConnected = isSplitwiseConnected,
                includeReimbursements = includeReimbursements,
                onToggleReimbursements = onToggleReimbursements,
                reimbursableByMonth = reimbursableByMonth,
                currencyFmt = currencyFmt,
                customCategoriesMap = customCategoriesMap,
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
                    // Manage icon is always visible — an empty state with Manage mode active
                    // is valid (user may want to set up category rules before transactions appear).
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
            // Splitwise reimbursable chip — disabled for windows that don't align with calendar month
            val reimbursableChipEnabled = spendingWindow == SpendingWindow.LAST_30_DAYS ||
                spendingWindow == SpendingWindow.THIS_MONTH
            if (isSplitwiseConnected) {
                Spacer(Modifier.height(4.dp))
                FilterChip(
                    selected = includeReimbursements,
                    onClick = { if (reimbursableChipEnabled) onToggleReimbursements() },
                    enabled = reimbursableChipEnabled,
                    label = { Text("Subtract Splitwise") },
                    leadingIcon = if (includeReimbursements) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
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
                    reimbursableAmount = if (includeReimbursements) currentMonthReimbursable else 0.0,
                    includeReimbursements = includeReimbursements,
                    customCategoriesMap = customCategoriesMap,
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
                val totalAmount = breakdown.sumOf { it.totalAmount }
                displayList.forEachIndexed { index, spend ->
                    CategoryRow(spend, totalAmount, onCategoryTap, currencyFmt, customCategoriesMap)
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
    reimbursableAmount: Double,
    includeReimbursements: Boolean,
    customCategoriesMap: Map<String, CustomCategory>,
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

    val reimburseOverlayColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
    val netAmount = (total - reimbursableAmount.toFloat()).coerceAtLeast(0f)

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
            // Reimbursable overlay arc — drawn on top of category arcs
            if (includeReimbursements && reimbursableAmount > 0) {
                val reimburseAngle = ((reimbursableAmount / total).toFloat()
                    .coerceIn(0f, 1f) * 360f * progress)
                drawArc(
                    color = reimburseOverlayColor,
                    startAngle = -90f,
                    sweepAngle = reimburseAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
            }
        }
        // Center label: net or total spend
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (includeReimbursements) currencyFmt.format(netAmount.toDouble())
                       else currencyFmt.format(total.toDouble()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (includeReimbursements) "net" else "total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    spend: CategorySpend,
    totalSpend: Double,
    onTap: (String) -> Unit,
    currencyFmt: NumberFormat,
    customCategoriesMap: Map<String, CustomCategory>,
) {
    val meta = CategoryMeta.resolveMeta(spend.effectiveCategory, customCategoriesMap)
    val percent = if (totalSpend > 0) (spend.totalAmount / totalSpend * 100).roundToInt() else 0
    
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
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            // Emoji glyph — decorative; meaning conveyed by adjacent displayName text.
            Text(
                text = meta.emoji,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meta.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${spend.txCount} transaction${if (spend.txCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = currencyFmt.format(spend.totalAmount),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    customCategoriesMap: Map<String, CustomCategory>,
) {
    val meta = CategoryMeta.resolveMeta(category ?: "OTHER", customCategoriesMap)
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
                            val txMeta = CategoryMeta.resolveMeta(tx.effectiveCategory, customCategoriesMap)
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
                        if (tx.overrideCategoryId != null) {
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
    customCategories: List<CustomCategory>,
    customCategoriesMap: Map<String, CustomCategory>,
    onPick: (String) -> Unit,
    onRemoveOverride: () -> Unit,
    onRemoveRule: (String) -> Unit,
    onSaveCustomCategory: (String) -> Unit,
    onDeleteCustomCategory: (String) -> Unit,
    countTransactionsWithOverride: suspend (String) -> Int,
    countOverridesForMerchant: suspend (String) -> Int,
    onDismiss: () -> Unit,
    // Bulk mode: no pre-selection, custom title/subtitle
    isBulkMode: Boolean = false,
    bulkCount: Int = 0,
) {
    var customInput by rememberSaveable { mutableStateOf("") }
    var showChangeHint by remember { mutableStateOf(false) }
    var showAlreadyDefaultHint by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Pair<CustomCategory, Int>?>(null) }
    var pendingRemoveOverride by remember { mutableStateOf<Int?>(null) } // Number of matching overrides

    LaunchedEffect(showChangeHint, showAlreadyDefaultHint) {
        if (showChangeHint || showAlreadyDefaultHint) {
            kotlinx.coroutines.delay(3000)
            showChangeHint = false
            showAlreadyDefaultHint = false
        }
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    // In bulk mode there is no single pre-selected category — treat as unset.
    val effectiveCat = if (isBulkMode) null else (transaction?.effectiveCategory ?: "OTHER")
    val resolvedMeta = effectiveCat?.let { CategoryMeta.resolveMeta(it, customCategoriesMap) }

    val coreCategories = customCategories.filter { it.isCore }
        .sortedWith(compareByDescending<CustomCategory> { resolvedMeta?.displayName == it.name }
            .thenBy { it.name })

    val userCategories = customCategories.filter { !it.isCore }
        .sortedWith(compareByDescending<CustomCategory> { resolvedMeta?.displayName == it.name }
            .thenBy { it.name })

    // Effective category not covered by either list — surface it at top of Categories
    val isOrphan = effectiveCat != null && 
        customCategories.none { it.name == resolvedMeta?.displayName }

    val defaultMeta = transaction?.let { 
        CategoryMeta.getMetaForPlaidCode(it.plaidFallbackCategory) 
    }
    val defaultName = defaultMeta?.displayName ?: "its default category"

    pendingRemoveOverride?.let { otherCount ->
        AlertDialog(
            onDismissRequest = { pendingRemoveOverride = null },
            title = { Text("Remove override?") },
            text = {
                val msg = if (otherCount > 1 && !transaction?.merchantName.isNullOrBlank()) {
                    "This transaction will go back to $defaultName. " +
                    "Also remove overrides for $otherCount other ${transaction!!.merchantName} transactions?"
                } else {
                    "This transaction will go back to $defaultName."
                }
                Text(msg)
            },
            confirmButton = {
                // If the transaction has a merchantName, we allow removing for all.
                // If not, we just show a single "Remove" button.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!transaction?.merchantName.isNullOrBlank() && otherCount > 1) {
                        Button(
                            onClick = { onRemoveRule(transaction!!.merchantName!!); pendingRemoveOverride = null },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text("Remove for all") }
                    }
                    Button(
                        onClick = { onRemoveOverride(); pendingRemoveOverride = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (transaction?.merchantName.isNullOrBlank() || otherCount <= 1) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (transaction?.merchantName.isNullOrBlank() || otherCount <= 1) MaterialTheme.colorScheme.onError
                                          else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                    ) { Text("Just this one") }
                }
            }
        )
    }

    pendingDelete?.let { (cat, count) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove \"${cat.name}\"?") },
            text = {
                Text(
                    "$count transaction${if (count == 1) "" else "s"} using this category will " +
                    "revert to their default automatic category."
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDeleteCustomCategory(cat.id); pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = showChangeHint || showAlreadyDefaultHint,
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
                        text = if (showAlreadyDefaultHint) "This is the default category and cannot be unset. Select a different category to change it."
                               else "Tap a different category to change it",
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
        if (userCategories.isNotEmpty()) {
            Text(
                text = "Your categories",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            userCategories.forEach { cat ->
                val selected = !isBulkMode && resolvedMeta?.displayName == cat.name
                CategoryPickerRow(
                    emoji = cat.emoji,
                    label = cat.name,
                    selected = selected,
                    onPick = {
                        if (selected) {
                            if (transaction?.overrideCategoryId != null) {
                                scope.launch {
                                    val count = transaction.merchantName?.let { countOverridesForMerchant(it) } ?: 0
                                    pendingRemoveOverride = count
                                }
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showAlreadyDefaultHint = true
                            }
                        } else onPick(cat.id)
                    },
                    onDelete = {
                        scope.launch {
                            val count = countTransactionsWithOverride(cat.id)
                            if (count > 0) pendingDelete = cat to count
                            else onDeleteCustomCategory(cat.id)
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
        }

        // "Categories" — selected floats to top, rest sorted alphabetically.
        Text(
            text = "Categories",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        
        if (isOrphan && resolvedMeta != null) {
            CategoryPickerRow(
                emoji = resolvedMeta.emoji,
                label = resolvedMeta.displayName,
                selected = true,
                onPick = {
                    if (transaction?.overrideCategoryId != null) {
                        scope.launch {
                            val count = transaction.merchantName?.let { countOverridesForMerchant(it) } ?: 0
                            pendingRemoveOverride = count
                        }
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAlreadyDefaultHint = true
                    }
                },
            )
        }
        
        coreCategories.forEach { cat ->
            val selected = !isBulkMode && resolvedMeta?.displayName == cat.name
            CategoryPickerRow(
                emoji = cat.emoji,
                label = cat.name,
                selected = selected,
                onPick = {
                    if (selected) {
                        if (transaction?.overrideCategoryId != null) {
                            scope.launch {
                                val count = transaction.merchantName?.let { countOverridesForMerchant(it) } ?: 0
                                pendingRemoveOverride = count
                            }
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAlreadyDefaultHint = true
                        }
                    } else onPick(cat.id)
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
                    imageVector = Icons.Outlined.Delete,
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

private fun last12MonthSlots(): List<String> {
    val result = mutableListOf<String>()
    val now = java.time.YearMonth.now()
    for (i in 11 downTo 0) result.add(now.minusMonths(i.toLong()).toString())
    return result
}

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
    customCategoriesMap: Map<String, CustomCategory>,
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
                label = CategoryMeta.resolveMeta(cat.effectiveCategory, customCategoriesMap).displayName,
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
    onSetHistoryAccountFilter: (Set<String>?) -> Unit,
    onClearHistoryFilters: () -> Unit,
    allCreditAccounts: List<AccountEntity>,
    historySelectedAccountIds: Set<String>?,
    isSplitwiseConnected: Boolean,
    includeReimbursements: Boolean,
    onToggleReimbursements: () -> Unit,
    reimbursableByMonth: Map<YearMonth, Double>,
    currencyFmt: NumberFormat,
    customCategoriesMap: Map<String, CustomCategory>,
) {
    var filterDialogOpen by rememberSaveable { mutableStateOf(false) }
    var tooltipBarIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    // Resolve colors in composable scope so we can pass them to Canvas helpers.
    val miscColor = MaterialTheme.colorScheme.outlineVariant
    val palette = historyBarPalette

    val colorMap = remember(allHistoryCategories, historySelectedCategories) {
        buildHistoryColorMap(allHistoryCategories, historySelectedCategories, palette, miscColor)
    }

    val totalCategoryCount = allHistoryCategories.size
    val totalMerchantCount = topMerchantsForHistory.size
    val totalAccountCount = allCreditAccounts.size
    val categoryFiltered = historySelectedCategories != null && historySelectedCategories.size < totalCategoryCount
    val merchantFiltered = historySelectedMerchants != null && historySelectedMerchants.size < totalMerchantCount
    val accountFiltered = historySelectedAccountIds != null && historySelectedAccountIds.size < totalAccountCount
    val anyFiltered = categoryFiltered || merchantFiltered || accountFiltered

    if (filterDialogOpen) {
        HistoryFilterDialog(
            allCategories = allHistoryCategories,
            selectedCategories = historySelectedCategories,
            allMerchants = topMerchantsForHistory,
            selectedMerchants = historySelectedMerchants,
            historySelectedCategories = historySelectedCategories,
            onSetCategoryFilter = onSetHistoryCategoryFilter,
            onSetMerchantFilter = onSetHistoryMerchantFilter,
            allCreditAccounts = allCreditAccounts,
            historySelectedAccountIds = historySelectedAccountIds,
            onSetAccountFilter = onSetHistoryAccountFilter,
            onDismiss = { filterDialogOpen = false },
            customCategoriesMap = customCategoriesMap,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
            .pointerInput(tooltipBarIndex) {
                // Non-consuming Initial-pass intercept: any tap anywhere in the sheet
                // dismisses the tooltip, then the event still reaches its real target.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && tooltipBarIndex != null) {
                            tooltipBarIndex = null
                        }
                    }
                }
            },
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
                val cardLabel = if (historySelectedAccountIds == null) {
                    "All cards"
                } else {
                    allCreditAccounts
                        .filter { it.accountId in historySelectedAccountIds }
                        .joinToString(", ") { it.name.shortCardName() }
                        .ifEmpty { "All cards" }
                }
                Text(
                    text = "Last 12 months · $cardLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isSplitwiseConnected) {
                    Spacer(Modifier.height(6.dp))
                    FilterChip(
                        selected = includeReimbursements,
                        onClick = onToggleReimbursements,
                        label = { Text("Subtract Splitwise") },
                        leadingIcon = if (includeReimbursements) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null,
                    )
                }
            }
            // Fix 5 — filter icon + count badges compound element
            // Tapping the icon OR the badge row opens the unified filter dialog.
            Row(
                modifier = Modifier
                    .clickable(onClickLabel = "Open filter") { filterDialogOpen = true }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = "Filter spending history",
                    tint = if (anyFiltered) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                if (anyFiltered) {
                    Text(
                        text = "Filtered",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Stacked bar chart ─────────────────────────────────────────────────
        if (allCreditAccounts.isNotEmpty()) {
            // Always render the 12-month grid. Months with no data (or filtered-out
            // accounts/merchants) simply draw no bars — same behaviour as unselecting
            // all merchants, which the user confirmed they prefer over hiding the chart.
            HistoryStackedBarChart(
                spendingHistoryByMonth = spendingHistoryByMonth,
                spendingHistoryByMonthAndMerchant = spendingHistoryByMonthAndMerchant,
                allHistoryCategories = allHistoryCategories,
                historySelectedCategories = historySelectedCategories,
                historySelectedMerchants = historySelectedMerchants,
                colorMap = colorMap,
                palette = palette,
                customCategoriesMap = customCategoriesMap,
                miscColor = miscColor,
                currencyFmt = currencyFmt,
                tooltipBarIndex = tooltipBarIndex,
                onTooltipChange = { tooltipBarIndex = it },
                includeReimbursements = includeReimbursements,
                reimbursableByMonth = reimbursableByMonth,
            )

            Spacer(Modifier.height(8.dp))

            HistoryChartLegend(
                colorMap = colorMap,
                allHistoryCategories = allHistoryCategories,
                historySelectedCategories = historySelectedCategories,
                historySelectedMerchants = historySelectedMerchants,
                topMerchantsForHistory = topMerchantsForHistory,
                customCategoriesMap = customCategoriesMap,
            )
        } else {
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
    tooltipBarIndex: Int?,
    onTooltipChange: (Int?) -> Unit,
    includeReimbursements: Boolean = false,
    reimbursableByMonth: Map<YearMonth, Double> = emptyMap(),
    customCategoriesMap: Map<String, CustomCategory>,
) {
    val monthSlots = remember { last12MonthSlots() }

    val dataByMonth = remember(spendingHistoryByMonth) {
        spendingHistoryByMonth.associateBy { it.month.toString() }
    }
    val merchantDataByMonth = remember(spendingHistoryByMonthAndMerchant) {
        spendingHistoryByMonthAndMerchant.associateBy { it.month.toString() }
    }

    val density = LocalDensity.current
    val barSlotDp = 36.dp
    val barWidthDp = 22.dp
    val chartHeightDp = 160.dp
    val yAxisWidthDp = 44.dp
    val edgePaddingDp = 8.dp
    val barSlotPx = with(density) { barSlotDp.toPx() }
    val barWidthPx = with(density) { barWidthDp.toPx() }
    val chartHeightPx = with(density) { chartHeightDp.toPx() }
    val edgePaddingPx = with(density) { edgePaddingDp.toPx() }
    // Each bar is centered within its slot; startPadding = offset from slot-left to bar-left
    val startPadding = (barSlotPx - barWidthPx) / 2f
    val totalContentWidthPx = barSlotPx * 12 + edgePaddingPx * 2

    val rawMaxMonthTotal = remember(monthSlots, dataByMonth, merchantDataByMonth, historySelectedCategories, historySelectedMerchants) {
        monthSlots.maxOfOrNull { slot ->
            val monthData = dataByMonth[slot] ?: return@maxOfOrNull 0.0
            val merchantData = merchantDataByMonth[slot]
            val segs = buildBarSegments(
                monthData, merchantData, historySelectedCategories, historySelectedMerchants,
                colorMap, miscColor, allHistoryCategories, palette, customCategoriesMap,
            )
            segs.sumOf { it.amount }
        } ?: 1.0
    }

    val gridValues = remember(rawMaxMonthTotal) { buildGridValues(rawMaxMonthTotal) }
    val maxMonthTotal = gridValues.last()

    val panOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    var canvasWidthPx by remember { mutableStateOf(0f) }

    fun clampOffset(offset: Float, width: Float): Float =
        offset.coerceIn(-(totalContentWidthPx - width).coerceAtLeast(0f), 0f)

    // Set Animatable bounds so fling cannot overshoot past first/last bar
    LaunchedEffect(canvasWidthPx) {
        if (canvasWidthPx > 0f) {
            val minOff = -(totalContentWidthPx - canvasWidthPx).coerceAtLeast(0f)
            panOffset.updateBounds(minOff, 0f)
            panOffset.snapTo(minOff) // start with newest months visible on right
        }
    }

    val velocityTracker = remember { VelocityTracker() }

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val netLineColor = MaterialTheme.colorScheme.secondary

    Box(modifier = Modifier.fillMaxWidth()) {
        // Y-axis labels (static, not clipped by chart scroll)
        Box(
            modifier = Modifier
                .width(yAxisWidthDp)
                .height(chartHeightDp + 20.dp)
                .align(Alignment.TopStart),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartH = chartHeightPx
                gridValues.forEach { value ->
                    val y = chartH - (value / maxMonthTotal * chartH).toFloat()
                    drawLine(
                        color = gridlineColor,
                        start = Offset(size.width - 4.dp.toPx(), y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
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

        // Pannable chart area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = yAxisWidthDp)
                .height(chartHeightDp + 20.dp)
                .pointerInput(monthSlots, historySelectedCategories, historySelectedMerchants) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var totalDragPx = 0f
                        drag(down.id) { change ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val delta = (change.position - change.previousPosition).x
                            totalDragPx += kotlin.math.abs(delta)
                            val newOff = clampOffset(panOffset.value + delta, canvasWidthPx)
                            scope.launch { panOffset.snapTo(newOff) }
                            change.consume()
                        }
                        if (totalDragPx <= viewConfiguration.touchSlop) {
                            // Tap: find bar under finger
                            val tapX = down.position.x
                            val tapY = down.position.y
                            if (tapY <= chartHeightPx) {
                                val rawIndex = ((tapX - edgePaddingPx - startPadding - panOffset.value) / barSlotPx).toInt()
                                val tappedIndex = rawIndex.coerceIn(0, 11)
                                val barLeft = edgePaddingPx + tappedIndex * barSlotPx + panOffset.value + startPadding
                                if (tapX >= barLeft && tapX <= barLeft + barWidthPx) {
                                    onTooltipChange(if (tooltipBarIndex == tappedIndex) null else tappedIndex)
                                } else {
                                    onTooltipChange(null)
                                }
                            } else {
                                onTooltipChange(null)
                            }
                        } else {
                            // Fling — bounds already set on Animatable, no manual clamp needed
                            val velocity = velocityTracker.calculateVelocity().x
                            scope.launch { panOffset.animateDecay(velocity, decay) }
                        }
                    }
                },
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasWidthPx = it.width.toFloat() },
            ) {
                val chartH = chartHeightPx

                gridValues.forEach { value ->
                    val y = chartH - (value / maxMonthTotal * chartH).toFloat()
                    drawLine(
                        color = gridlineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                monthSlots.forEachIndexed { index, slot ->
                    val barLeft = edgePaddingPx + index * barSlotPx + panOffset.value + startPadding
                    val barRight = barLeft + barWidthPx
                    if (barRight < 0f || barLeft > size.width) return@forEachIndexed

                    val monthData = dataByMonth[slot]
                    if (monthData != null) {
                        val merchantData = merchantDataByMonth[slot]
                        val segments = buildBarSegments(
                            monthData, merchantData,
                            historySelectedCategories, historySelectedMerchants,
                            colorMap, miscColor, allHistoryCategories, palette, customCategoriesMap,
                        )
                        val totalHeight = segments.sumOf { it.amount }
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
                        // Net-spend line: horizontal tick at (total - reimbursable) height
                        if (includeReimbursements && totalHeight > 0) {
                            val yearMonth = java.time.YearMonth.parse(slot)
                            val reimbursable = reimbursableByMonth[yearMonth] ?: 0.0
                            if (reimbursable > 0) {
                                val netAmount = (totalHeight - reimbursable).coerceAtLeast(0.0)
                                val lineY = chartH - (netAmount / maxMonthTotal * chartH).toFloat()
                                drawLine(
                                    color = netLineColor,
                                    start = Offset(barLeft - 2.dp.toPx(), lineY),
                                    end = Offset(barLeft + barWidthPx + 2.dp.toPx(), lineY),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    }

                    val yearMonth = java.time.YearMonth.parse(slot)
                    val label = yearMonth.format(barMonthFmt)
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

            // Tooltip — clamped so it never overflows the left or right edge
            tooltipBarIndex?.let { idx ->
                if (idx in 0..11) {
                    val slot = monthSlots[idx]
                    val monthData = dataByMonth[slot]
                    if (monthData != null) {
                        val merchantData = merchantDataByMonth[slot]
                        val segments = buildBarSegments(
                            monthData, merchantData,
                            historySelectedCategories, historySelectedMerchants,
                            colorMap, miscColor, allHistoryCategories, palette, customCategoriesMap,
                        )
                        val tooltipTotal = segments.sumOf { it.amount }
                        val tooltipMaxWidthDp = 200.dp
                        val chartWidthDp = with(density) { canvasWidthPx.toDp() }
                        val rawBarLeftDp = with(density) {
                            (edgePaddingPx + idx * barSlotPx + panOffset.value + startPadding).toDp()
                        }
                        // Clamp so tooltip stays fully within chart bounds
                        val clampedX = rawBarLeftDp.coerceIn(
                            0.dp,
                            (chartWidthDp - tooltipMaxWidthDp).coerceAtLeast(0.dp),
                        )

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.offset(x = clampedX),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .requiredWidthIn(min = 140.dp, max = tooltipMaxWidthDp)
                                    .padding(bottom = chartHeightDp - (tooltipTotal / maxMonthTotal * chartHeightDp.value).dp + 8.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = monthData.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
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
                                                text = seg.label.toTitleCase(),
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
                                    if (includeReimbursements) {
                                        val tooltipMonth = java.time.YearMonth.parse(slot)
                                        val monthReimbursable = reimbursableByMonth[tooltipMonth] ?: 0.0
                                        if (monthReimbursable > 0) {
                                            val netTotal = (tooltipTotal - monthReimbursable).coerceAtLeast(0.0)
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = "Net",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(
                                                    text = currencyFmt.format(netTotal),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.secondary,
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
    }
}

/**
 * Wrapping legend row showing color dot + label for each category (or merchant) visible
 * in the chart. Only entries with non-zero spend in the selected data are shown.
 * "Misc" always appears last in [miscColor] = outlineVariant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryChartLegend(
    colorMap: Map<String, Color>,
    allHistoryCategories: List<CategoryAmount>,
    historySelectedCategories: Set<String>?,
    historySelectedMerchants: Set<String>?,
    topMerchantsForHistory: List<MerchantSpendSummary>,
    customCategoriesMap: Map<String, CustomCategory>,
) {
    // In merchant-filter mode the legend shows merchants; otherwise categories.
    // Build a list of (label, color) pairs in chart draw order.
    val entries: List<Pair<String, Color>> = if (historySelectedMerchants != null) {
        topMerchantsForHistory
            .filter { it.merchantName in historySelectedMerchants }
            .mapIndexed { i, m ->
                m.merchantName.toTitleCase() to historyBarPalette[i % historyBarPalette.size]
            }
    } else {
        // Category mode: show top-5 from colorMap (insertion order = palette assignment order),
        // then Misc if it exists.
        val categoryEntries = allHistoryCategories
            .filter { cat ->
                val isSelected = historySelectedCategories == null ||
                    cat.effectiveCategory in historySelectedCategories
                isSelected && cat.effectiveCategory in colorMap
            }
            .take(5)
            .map { cat ->
                CategoryMeta.resolveMeta(cat.effectiveCategory, customCategoriesMap).displayName to
                    colorMap[cat.effectiveCategory]!!
            }
        val miscEntry = if (colorMap.containsKey("Misc")) listOf("Misc" to colorMap["Misc"]!!) else emptyList()
        categoryEntries + miscEntry
    }

    if (entries.isEmpty()) return

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        // 8dp horizontal between legend chips, 4dp vertical when wrapping to next line.
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = color)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Rounds [rawMax] up to the next "nice" round-number ceiling — 1, 2, 5, or 10 × 10^N.
 * Ensures the chart's top tick is always ≥ the tallest bar so bars never clip.
 * Examples:
 *   rawMax = 3,241  → niceMax = 5,000
 *   rawMax = 850    → niceMax = 1,000
 *   rawMax = 12,000 → niceMax = 20,000
 */
private fun niceMax(rawMax: Double): Double {
    if (rawMax <= 0) return 1000.0
    val magnitude = 10.0.pow(floor(log10(rawMax)))
    val normalized = rawMax / magnitude
    // Finer steps produce a tighter ceiling so bars fill more of the chart height.
    // e.g. rawMax=320 → normalized=3.2 → nice=4.0 → ceiling=$400 (was $500 with coarse steps)
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 1.5 -> 1.5
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 3.0 -> 3.0
        normalized <= 4.0 -> 4.0
        normalized <= 5.0 -> 5.0
        normalized <= 6.0 -> 6.0
        normalized <= 8.0 -> 8.0
        else              -> 10.0
    }
    return nice * magnitude
}

/**
 * Builds exactly 5 evenly spaced gridline values from $0 to [niceMax] of [maxTotal].
 *
 * Returns ticks at 0%, 25%, 50%, 75%, 100% of niceMax. The top tick is always
 * ≥ the tallest bar, so no bar ever clips the chart ceiling.
 */
private fun buildGridValues(maxTotal: Double): List<Double> {
    val ceiling = niceMax(maxTotal)
    return listOf(0.0, 0.25, 0.5, 0.75, 1.0).map { it * ceiling }
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

// ── Unified History Filter Dialog (Fixes 2, 3, 4) ────────────────────────────
//
// Single dialog with a SingleChoiceSegmentedButtonRow at the top toggling between
// "Categories" and "Merchants" tabs. Both tabs share HistoryFilterTabContent which
// renders a "Select all" first row, a search field, and a scrollable item list
// sorted by 12-month spend descending. Changes are applied to the ViewModel
// immediately on every tap — no local staging, no Done/Cancel buttons.

// Fix 3: ModalBottomSheet replaces AlertDialog for the history filter.
// Fix 1: No title Text above the segmented button — the segmented button is self-explanatory.
// Fix 4: Plain if/else replaces AnimatedContent — eliminates tab-switch LazyColumn recompose lag.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterDialog(
    allCategories: List<CategoryAmount>,
    selectedCategories: Set<String>?,
    allMerchants: List<MerchantSpendSummary>,
    selectedMerchants: Set<String>?,
    historySelectedCategories: Set<String>?,
    onSetCategoryFilter: (Set<String>?) -> Unit,
    onSetMerchantFilter: (Set<String>?) -> Unit,
    allCreditAccounts: List<AccountEntity>,
    historySelectedAccountIds: Set<String>?,
    onSetAccountFilter: (Set<String>?) -> Unit,
    onDismiss: () -> Unit,
    customCategoriesMap: Map<String, CustomCategory>,
) {
    // Tab order: 0 = Cards, 1 = Categories, 2 = Merchants
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // ── Derived sets (cascade chain: Cards → Categories → Merchants) ──────────
    val allAccountKeys = remember(allCreditAccounts) {
        allCreditAccounts.map { it.accountId }.toSet()
    }

    val allCategoryKeys = remember(allCategories) {
        allCategories.map { it.effectiveCategory }.toSet()
    }
    // Intersect stored category selection with what's still visible after card filter.
    // If everything visible is selected (or nothing was explicitly filtered), treat as null.
    val effectiveCategorySelection = remember(selectedCategories, allCategoryKeys) {
        val intersected = selectedCategories?.intersect(allCategoryKeys) ?: allCategoryKeys
        if (intersected == allCategoryKeys) null else intersected
    }
    val categoryFiltered = effectiveCategorySelection != null

    // Merchants narrowed by the effective (cascaded) category selection.
    val visibleMerchants = remember(allMerchants, effectiveCategorySelection) {
        if (effectiveCategorySelection == null) allMerchants
        else allMerchants.filter { it.primaryCategory in effectiveCategorySelection }
    }
    val allMerchantKeys = remember(visibleMerchants) {
        visibleMerchants.map { it.merchantName }.toSet()
    }
    // Same intersection logic for merchants.
    val effectiveMerchantSelection = remember(selectedMerchants, allMerchantKeys) {
        val intersected = selectedMerchants?.intersect(allMerchantKeys) ?: allMerchantKeys
        if (intersected == allMerchantKeys) null else intersected
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    label = { Text("Cards") },
                )
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    label = { Text("Categories") },
                )
                SegmentedButton(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    label = { Text("Merchants") },
                )
            }

            if (selectedTab == 0) {
                HistoryFilterTabContent(
                    items = allCreditAccounts.map { account ->
                        FilterItem(
                            key = account.accountId,
                            label = account.name,
                            prefix = null,
                            totalAmount = 0.0,
                        )
                    },
                    allKeys = allAccountKeys,
                    checkedKeys = historySelectedAccountIds ?: allAccountKeys,
                    onSetFilter = { newSet ->
                        onSetAccountFilter(if (newSet == allAccountKeys) null else newSet)
                    },
                )
            } else if (selectedTab == 1) {
                HistoryFilterTabContent(
                    items = allCategories.map { cat ->
                        FilterItem(
                            key = cat.effectiveCategory,
                            label = CategoryMeta.resolveMeta(cat.effectiveCategory, customCategoriesMap).displayName,
                            prefix = CategoryMeta.resolveMeta(cat.effectiveCategory, customCategoriesMap).emoji,
                            totalAmount = cat.totalAmount,
                        )
                    },
                    allKeys = allCategoryKeys,
                    checkedKeys = effectiveCategorySelection ?: allCategoryKeys,
                    onSetFilter = { newSet ->
                        onSetCategoryFilter(if (newSet == allCategoryKeys) null else newSet)
                    },
                )
            } else {
                HistoryFilterTabContent(
                    items = visibleMerchants.map { m ->
                        FilterItem(
                            key = m.merchantName,
                            label = m.merchantName.toTitleCase(),
                            prefix = null,
                            totalAmount = m.totalAmount,
                        )
                    },
                    allKeys = allMerchantKeys,
                    checkedKeys = effectiveMerchantSelection ?: allMerchantKeys,
                    onSetFilter = { newSet ->
                        onSetMerchantFilter(if (newSet == allMerchantKeys) null else newSet)
                    },
                )
            }
        }
    }
}

/** Lightweight data holder for a single filter item in the unified tab. */
private data class FilterItem(
    val key: String,
    val label: String,
    val prefix: String?,       // emoji or null
    val totalAmount: Double,
)

/**
 * Shared tab content used by both Categories and Merchants tabs.
 *
 * Items are pre-sorted by 12-month spend descending (caller provides them sorted).
 * Search field is always visible. "Select all" is always the first list row.
 * [onSetFilter] receives the complete new desired set — the caller normalises
 * all-selected to null before forwarding to the ViewModel.
 */
@Composable
private fun HistoryFilterTabContent(
    items: List<FilterItem>,
    allKeys: Set<String>,
    checkedKeys: Set<String>,
    onSetFilter: (Set<String>) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.requiredWidthIn(min = 280.dp)) {
        // Search bar — always visible (both tabs)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search…") },
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
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // "Select all" row — always first, not filtered by search
            item(key = "__select_all__") {
                val triState = when {
                    checkedKeys == allKeys -> ToggleableState.On
                    checkedKeys.isEmpty() -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // On → clear all; Off/Indeterminate → select all
                            if (triState == ToggleableState.On) onSetFilter(emptySet())
                            else onSetFilter(allKeys)
                        }
                        .padding(horizontal = 0.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(
                        state = triState,
                        onClick = {
                            if (triState == ToggleableState.On) onSetFilter(emptySet())
                            else onSetFilter(allKeys)
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Select all",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            items(filteredItems, key = { it.key }) { item ->
                val checked = item.key in checkedKeys
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSet = if (checked) checkedKeys - item.key else checkedKeys + item.key
                            onSetFilter(newSet)
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { ticked ->
                            val newSet = if (ticked) checkedKeys + item.key else checkedKeys - item.key
                            onSetFilter(newSet)
                        },
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    if (item.prefix != null) {
                        Text(
                            text = item.prefix,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(24.dp),
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
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
