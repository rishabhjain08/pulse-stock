package com.pulsestock.app.ui.finances

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulsestock.app.BuildConfig
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.CategorySpend
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.effectiveCategory
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

    // Category drill-down sheet
    if (state.categoryDrillDown != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = vm::closeCategoryDrillDown,
            sheetState = sheetState,
            // M3 drag handle rendered by BottomSheetDefaults.DragHandle()
            dragHandle = { BottomSheetDefaults.DragHandle() },
            // Use surface container so the sheet has tonal lift above page background
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            CategoryDrillDownSheet(
                category = state.categoryDrillDown,
                transactions = state.drillDownTransactions,
                currencyFmt = currencyFmt,
                onEditCategory = vm::startOverride,
                onDismiss = vm::closeCategoryDrillDown,
            )
        }
    }

    // Category override picker sheet
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
                onClear = { vm.applyOverride(overridingTx.transactionId, null) },
                onDismiss = vm::cancelOverride,
            )
        }
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
                    items(state.creditAccounts, key = { it.accountId }) { account ->
                        CreditCardSummaryCard(account, currencyFmt)
                    }
                    item {
                        CreditCardTotalsRow(
                            accounts = state.creditAccounts,
                            reimbursable = state.currentMonthReimbursable,
                            includeReimbursements = state.includeReimbursements,
                            showToggle = state.isSplitwiseConnected,
                            onToggle = vm::toggleIncludeReimbursements,
                            currencyFmt = currencyFmt,
                        )
                    }
                    // FilterChip row — select which cards contribute to Spending
                    if (state.creditAccounts.size > 1) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.creditAccounts, key = { it.accountId }) { account ->
                                    val selected = state.selectedSpendingAccountIds
                                        ?.contains(account.accountId) ?: true
                                    FilterChip(
                                        selected = selected,
                                        onClick = { vm.toggleSpendingAccount(account.accountId) },
                                        label = { Text(account.name.shortCardName()) },
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
                            onCategoryTap = vm::openCategoryDrillDown,
                            currencyFmt = currencyFmt,
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

@Composable
internal fun CreditCardSummaryCard(account: AccountEntity, currencyFmt: NumberFormat) {
    // surfaceContainerLow gives gentle tonal lift above the page background.
    // tonalElevation = 1.dp on Card uses the M3 elevation overlay for tinting —
    // no drop shadow. Using the ElevatedCard shape convention (shapes.medium = 12dp
    // for list-level cards per this project's convention).
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
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
                LabeledAmount(
                    label = "Statement",
                    amount = account.statementBalance,
                    currencyFmt = currencyFmt,
                    amountStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                LabeledAmount(
                    label = "Current",
                    amount = account.currentBalance,
                    currencyFmt = currencyFmt,
                    amountStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
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

// ── Credit card totals row ────────────────────────────────────────────────────

@Composable
private fun CreditCardTotalsRow(
    accounts: List<AccountEntity>,
    reimbursable: Double,
    includeReimbursements: Boolean,
    showToggle: Boolean,
    onToggle: () -> Unit,
    currencyFmt: NumberFormat,
) {
    val hasStatements = accounts.any { it.statementBalance != null }
    val totalStatement = if (hasStatements) accounts.sumOf { it.statementBalance ?: 0.0 } else null
    val totalCurrent = accounts.sumOf { it.currentBalance ?: 0.0 }
    val statementDisplay = if (includeReimbursements) totalStatement?.minus(reimbursable) else totalStatement
    val currentDisplay = if (includeReimbursements) totalCurrent - reimbursable else totalCurrent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledAmount(
            label = if (includeReimbursements) "Net Statement" else "Total Statement",
            amount = statementDisplay,
            currencyFmt = currencyFmt,
            modifier = Modifier.weight(1f),
        )
        LabeledAmount(
            label = if (includeReimbursements) "Net Current" else "Total Current",
            amount = currentDisplay,
            currencyFmt = currencyFmt,
            modifier = Modifier.weight(1f),
        )
        if (showToggle) {
            // Toggle lives in-row with the totals it modifies (proximity principle).
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "Subtract Splitwise",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = includeReimbursements,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics {
                        contentDescription = "Subtract Splitwise reimbursements from totals"
                    },
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
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

@Composable
private fun CategoryBreakdownCard(
    breakdown: List<CategorySpend>,
    spendingWindow: SpendingWindow,
    dateRangeLabel: String?,
    onWindowChange: (SpendingWindow) -> Unit,
    onCategoryTap: (String) -> Unit,
    currencyFmt: NumberFormat,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val displayList = if (expanded || breakdown.size <= 4) breakdown else breakdown.take(4)
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
            // Header row: title + time-period dropdown
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
                SpendingWindowDropdown(selected = spendingWindow, onSelect = onWindowChange)
            }
            // Date range subtitle (shown for Statement and Last 30 days)
            if (dateRangeLabel != null) {
                Text(
                    text = dateRangeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
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
) {
    val meta = CategoryMeta.get(category ?: "OTHER")
    // Use a Column + verticalScroll so content can scroll without a nested LazyColumn inside
    // a ModalBottomSheet (nested lazy layouts cause measurement issues).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        // Sheet header — M3 pattern: left-aligned title with emoji badge + total amount on right.
        // No Surface wrapper needed — the sheet's containerColor provides the background.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji badge — decorative; meaning conveyed by displayName text beside it.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = meta.emoji,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
        transactions.forEach { tx ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    if (tx.categoryOverride != null) {
                        // "Overridden" badge — tertiary color indicates user-modified state
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
                    modifier = Modifier.padding(end = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // M3 IconButton provides 48dp×48dp touch target by default.
                IconButton(
                    onClick = { onEditCategory(tx) },
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Change category for ${tx.name}",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

// ── Category Override Picker ──────────────────────────────────────────────────

@Composable
private fun CategoryPickerSheet(
    transaction: PlaidTransaction?,
    customCategories: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var customInput by rememberSaveable { mutableStateOf("") }
    // verticalScroll so quickPicks list + custom field are reachable on small screens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        // Sheet header — M3 style: prominent title + transaction name as subtitle
        Text(
            text = "Change category",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (transaction?.name != null) {
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

        // Custom categories the user has used before
        if (customCategories.isNotEmpty()) {
            Text(
                text = "Your categories",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            customCategories.forEach { cat ->
                val meta = CategoryMeta.get(cat)
                CategoryPickerRow(
                    emoji = meta.emoji,
                    label = cat,
                    selected = transaction?.categoryOverride == cat,
                    onPick = { onPick(cat) },
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
        }

        // Plaid quick picks
        Text(
            text = "Categories",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        CategoryMeta.quickPicks.forEach { (code, meta) ->
            CategoryPickerRow(
                emoji = meta.emoji,
                label = meta.displayName,
                selected = transaction?.categoryOverride == code,
                onPick = { onPick(code) },
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        // Custom free-text entry
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
                    if (customInput.isNotBlank()) onPick(customInput.trim())
                },
                enabled = customInput.isNotBlank(),
            ) {
                Text("Set")
            }
        }

        // Remove override option
        if (transaction?.categoryOverride != null) {
            TextButton(
                onClick = onClear,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(
                    "Remove override (reset to automatic)",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun CategoryPickerRow(
    emoji: String,
    label: String,
    selected: Boolean,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Minimum touch target: padding(vertical = 12.dp) + bodyMedium (≈20sp) ≥ 48dp.
            .clickable(onClickLabel = "Select $label") { onPick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Emoji is decorative — meaning conveyed by the adjacent label text
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
            // M3 Check icon — semantically paired with color+weight cues already conveying
            // selection, so this is redundant/decorative from an a11y standpoint.
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
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
