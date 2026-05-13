package com.pulsestock.app.ui.finances

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsestock.app.data.poarvault.AccountDateRange
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.BalanceSnapshot
import com.pulsestock.app.data.poarvault.MerchantRuleProposal
import com.pulsestock.app.data.poarvault.CategorySpend
import com.pulsestock.app.ui.finances.CategoryMeta
import com.pulsestock.app.data.poarvault.CustomCategory
import com.pulsestock.app.data.poarvault.ExpenseWithLinks
import com.pulsestock.app.data.poarvault.MonthlySpendRow
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.PoarVaultApi
import com.pulsestock.app.data.poarvault.PoarVaultDatabase
import com.pulsestock.app.data.poarvault.PoarVaultRepository
import com.pulsestock.app.data.poarvault.SplitwiseApi
import com.pulsestock.app.data.poarvault.SplitwiseExpense
import com.pulsestock.app.data.poarvault.SplitwiseRepository
import com.pulsestock.app.data.poarvault.TokenStore
import com.pulsestock.app.data.poarvault.effectiveCategory
import com.pulsestock.app.data.poarvault.usesWindowHeuristic
import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class ReconcileFilter { TO_LINK, LINKED, DISMISSED, ALL }

enum class SpendingWindow(val label: String) {
    STATEMENT("Statement"),
    THIS_CYCLE("Current Cycle"),
    LAST_30_DAYS("Last 30 days"),
    THIS_MONTH("This month"),
}

data class LinkSheetState(
    val expense: SplitwiseExpense,
    val suggested: List<PlaidTransaction>,
    val all: List<PlaidTransaction>,
)

private data class BreakdownTrigger(
    val window: SpendingWindow,
    val selectedIds: Set<String>?,
    val accounts: List<AccountEntity>,
    val customMap: Map<String, CustomCategory>
)

private data class HistoryTrigger(
    val allAccountIds: List<String>,
    val historySelectedIds: Set<String>?,
    val customMap: Map<String, CustomCategory>
)

/** One category bucket within a monthly spending history entry. */
data class CategoryAmount(
    val effectiveCategory: String,
    val totalAmount: Double,
)

/**
 * Aggregated merchant for the history filter.
 * [primaryCategory] = the effectiveCategory that appears most often across all 12 months
 * for this merchant — used for smart filtering when a category filter is active.
 */
data class MerchantSpendSummary(
    val merchantName: String,
    val totalAmount: Double,
    val primaryCategory: String,
)

/**
 * Per-merchant, per-month spending total. Mirrors [MonthlySpendingHistory] but keyed by
 * merchantName. Used when [FinancesUiState.historySelectedMerchants] is non-null so the
 * chart can render merchant-segmented bars instead of category-segmented bars.
 */
data class MonthlyMerchantAmount(
    val merchantName: String,
    val totalAmount: Double,
)

data class MonthlyMerchantHistory(
    val month: YearMonth,
    val merchants: List<MonthlyMerchantAmount>,
)

/** Monthly spending total + per-category breakdown for the history sheet. */
data class MonthlySpendingHistory(
    val month: YearMonth,
    val totalAmount: Double,
    val categories: List<CategoryAmount>,
)

/** One account's balance data point within a monthly balance snapshot entry. */
data class AccountBalancePoint(
    val accountId: String,
    val accountName: String,
    val statementBalance: Double?,
    val currentBalance: Double?,
)

/** Latest-per-account balance snapshot for a given calendar month, for the history sheet. */
data class MonthlyBalanceSnapshot(
    val month: YearMonth,
    val snapshots: List<AccountBalancePoint>,
)

data class PendingApplyState(
    val transactionIds: List<String>,
    val categoryId: String?,
    val proposals: List<MerchantRuleProposal>,
    val approvedMerchantNames: List<String> = emptyList()
)

enum class ManageSortOrder { NAME, AMOUNT, DATE }

data class FinancesUiState(
    val creditAccounts: List<AccountEntity> = emptyList(),
    val allWithLinks: List<ExpenseWithLinks> = emptyList(),
    val inboxCount: Int = 0,
    val filter: ReconcileFilter = ReconcileFilter.TO_LINK,
    val isSplitwiseConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val linkSheet: LinkSheetState? = null,
    val error: String? = null,
    val selectedMonth: YearMonth = YearMonth.now(),
    val monthlyReimbursable: Double = 0.0,
    val currentMonthReimbursable: Double = 0.0,
    val includeReimbursements: Boolean = false,
    val isSplitwiseLoading: Boolean = false,
    // Spending card state
    val spendingWindow: SpendingWindow = SpendingWindow.LAST_30_DAYS,
    // null = all credit accounts selected; non-null = explicit subset
    val selectedSpendingAccountIds: Set<String>? = null,
    // Date range label shown as subtitle (e.g. "Apr 12 – May 9")
    val spendingDateRangeLabel: String? = null,
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    // Maps canonical effectiveCategory code → all sibling codes seen in breakdown data.
    // Built alongside the breakdown so drill-through can fetch all synonymous Plaid codes.
    val categoryCodeGroups: Map<String, List<String>> = emptyMap(),
    val categoryDrillDown: String? = null,
    val drillDownTransactions: List<PlaidTransaction> = emptyList(),
    val overridingTransaction: PlaidTransaction? = null,
    val customCategories: List<CustomCategory> = emptyList(),
    val customCategoriesMap: Map<String, CustomCategory> = emptyMap(),
    // Tracks the state of an in-progress override operation that requires confirmation.
    val pendingApplyState: PendingApplyState? = null,
    // Bulk categorization mode
    val isBulkMode: Boolean = false,
    val bulkSelectedIds: Set<String> = emptySet(),
    val isBulkPickerOpen: Boolean = false,
    // All-transactions mode: show every credit tx for the window, not filtered by category
    val allTransactionsMode: Boolean = false,
    val groupByMerchant: Boolean = true,
    val manageSortOrder: ManageSortOrder = ManageSortOrder.AMOUNT,
    // When true, the UI shows a confirmation dialog before removing overrides for multiple selected transactions.
    val showBulkRemovalWarning: Boolean = false,
    // Transaction IDs recategorized in the current Manage session (reset when sheet closes)
    val sessionCategorizedIds: Set<String> = emptySet(),
    // One-shot flag: true when the ViewModel just auto-excluded business CCs due to a
    // STATEMENT/THIS_CYCLE window selection. The screen consumes this to show a Snackbar
    // and immediately clears it so the snackbar only fires once per transition.
    val pendingBusinessCardSnackbar: Boolean = false,
    // History sheet data — pre-computed in the ViewModel, ordered most-recent first
    val spendingHistoryByMonth: List<MonthlySpendingHistory> = emptyList(),
    val balanceSnapshotsByMonth: List<MonthlyBalanceSnapshot> = emptyList(),
    // Merchant-keyed parallel to spendingHistoryByMonth — used when merchant filter is active
    val spendingHistoryByMonthAndMerchant: List<MonthlyMerchantHistory> = emptyList(),
    // All categories present in 12-month data, sorted by total spend desc
    val allHistoryCategories: List<CategoryAmount> = emptyList(),
    // All merchants present in 12-month data, sorted by total spend desc
    val topMerchantsForHistory: List<MerchantSpendSummary> = emptyList(),
    // null = all selected (default); emptySet() = user explicitly cleared everything
    val historySelectedCategories: Set<String>? = null,
    val historySelectedMerchants: Set<String>? = null,
    val historySelectedAccountIds: Set<String>? = null,
    // Splitwise reimbursable per calendar month for the last 12 months (history overlay)
    val reimbursableByMonth: Map<YearMonth, Double> = emptyMap(),
) {
    val displayedList: List<ExpenseWithLinks> get() = when (filter) {
        ReconcileFilter.TO_LINK   -> allWithLinks.filter { it.isUnlinked || it.isPendingAutoMatch }
        ReconcileFilter.LINKED    -> allWithLinks.filter { it.isReconciled }
        ReconcileFilter.DISMISSED -> allWithLinks.filter { it.isDismissed }
        ReconcileFilter.ALL       -> allWithLinks
    }

    /** Accounts currently contributing to the Spending card. */
    val effectiveSpendingAccounts: List<AccountEntity> get() =
        if (selectedSpendingAccountIds == null) creditAccounts
        else creditAccounts.filter { it.accountId in selectedSpendingAccountIds }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FinancesViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val tokens = TokenStore(ctx)
    private val db = PoarVaultDatabase.get(ctx, tokens.getOrCreatePassphrase())
    private val api = PoarVaultApi()
    private val splitwiseApi = SplitwiseApi()
    private val repo = PoarVaultRepository(api, db, tokens)
    private val splitwiseRepo = SplitwiseRepository(api, splitwiseApi, db, tokens)

    private val _uiState = MutableStateFlow(FinancesUiState(isSplitwiseConnected = splitwiseRepo.isConnected()))
    val uiState: StateFlow<FinancesUiState> = _uiState.asStateFlow()

    init {
        // Auto-refresh Splitwise on every session so share amounts are always current
        if (splitwiseRepo.isConnected()) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isSplitwiseLoading = true)
                try {
                    splitwiseRepo.refreshExpenses()
                } catch (e: Exception) {
                    PulseLog.w("FinancesVM", "auto-refresh Splitwise failed: ${e.message}")
                } finally {
                    _uiState.value = _uiState.value.copy(isSplitwiseLoading = false)
                }
            }
        }
        viewModelScope.launch {
            CategoryMeta.coreCategories.forEach { repo.saveCustomCategory(it) }
        }
        viewModelScope.launch {
            db.dao().watchCreditCardAccounts().collect { accounts ->
                val prev = _uiState.value
                // If selection was null or empty (initial state), select all accounts
                val selectedIds = if (prev.selectedSpendingAccountIds.isNullOrEmpty() && accounts.isNotEmpty()) {
                    accounts.map { it.accountId }.toSet()
                } else {
                    prev.selectedSpendingAccountIds
                }
                _uiState.value = prev.copy(
                    creditAccounts = accounts,
                    isSplitwiseConnected = splitwiseRepo.isConnected(),
                    selectedSpendingAccountIds = selectedIds,
                )
            }
        }
        viewModelScope.launch {
            splitwiseRepo.allWithLinks.collect { list ->
                _uiState.value = _uiState.value.copy(allWithLinks = list)
            }
        }
        viewModelScope.launch {
            splitwiseRepo.inboxCount.collect { count ->
                _uiState.value = _uiState.value.copy(inboxCount = count)
            }
        }
        // Browsed month — updates the card display
        viewModelScope.launch {
            _uiState
                .map { it.selectedMonth }
                .distinctUntilChanged()
                .flatMapLatest { month ->
                    splitwiseRepo.watchMonthlyReimbursable(month)
                }
                .collect { reimbursable ->
                    _uiState.value = _uiState.value.copy(monthlyReimbursable = reimbursable)
                }
        }
        // Current month — always tracks now() for the CC offset toggle
        viewModelScope.launch {
            splitwiseRepo.watchMonthlyReimbursable(YearMonth.now())
                .collect { reimbursable ->
                    _uiState.value = _uiState.value.copy(currentMonthReimbursable = reimbursable)
                }
        }
        // 12-month reimbursable map for the history overlay
        viewModelScope.launch {
            val months = (0..11).map { YearMonth.now().minusMonths(it.toLong()) }
            val flows = months.map { month -> splitwiseRepo.watchMonthlyReimbursable(month) }
            combine(flows) { amounts ->
                months.indices.associate { i -> months[i] to amounts[i] }
            }.collect { map ->
                _uiState.value = _uiState.value.copy(reimbursableByMonth = map)
            }
        }
        // Spending category breakdown — re-queries whenever window, selection, accounts, or categories change
        viewModelScope.launch {
            _uiState
                .map { BreakdownTrigger(it.spendingWindow, it.selectedSpendingAccountIds, it.creditAccounts, it.customCategoriesMap) }
                .distinctUntilChanged()
                .flatMapLatest { trigger ->
                    val effectiveAccounts = if (trigger.selectedIds == null) trigger.accounts
                        else trigger.accounts.filter { it.accountId in trigger.selectedIds }
                    if (effectiveAccounts.isEmpty()) return@flatMapLatest flowOf(emptyList<CategorySpend>() to trigger.customMap)
                    val ranges = buildAccountDateRanges(trigger.window, effectiveAccounts)
                    val label = buildDateRangeLabel(trigger.window, ranges, effectiveAccounts)
                    _uiState.value = _uiState.value.copy(spendingDateRangeLabel = label)
                    repo.watchCategoryBreakdown(ranges).map { it to trigger.customMap }
                }
                .collect { (breakdown, customMap) ->
                    // Group by display name so codes like ENTERTAINMENT_GYMS and
                    // PERSONAL_CARE_GYMS (both "Gym") or unmapped codes like TRAVEL_FLIGHTS
                    // ("Flights") merge into one row. Store all sibling codes per canonical
                    // code so drill-through can query all of them, not just the first.
                    val grouped = breakdown.groupBy { CategoryMeta.resolveMeta(it.effectiveCategory, customMap).displayName }
                    val merged = grouped.values.map { rows ->
                        rows.reduce { acc, row ->
                            CategorySpend(
                                effectiveCategory = acc.effectiveCategory,
                                totalAmount = acc.totalAmount + row.totalAmount,
                                txCount = acc.txCount + row.txCount,
                            )
                        }
                    }.sortedByDescending { it.totalAmount }
                    val codeGroups = grouped.values.associate { rows ->
                        rows.first().effectiveCategory to rows.map { it.effectiveCategory }
                    }
                    PulseLog.d("FinancesVM", "categoryBreakdown: ${merged.size} categories (${breakdown.size} raw)")
                    _uiState.value = _uiState.value.copy(categoryBreakdown = merged, categoryCodeGroups = codeGroups)
                }
        }
        // Auto-load balances + transactions + liabilities on session start
        viewModelScope.launch {
            try {
                PulseLog.d("FinancesVM", "auto-load: starting full refresh")
                repo.refreshAll()
                PulseLog.d("FinancesVM", "auto-load: done")
            } catch (e: Exception) {
                PulseLog.w("FinancesVM", "auto-load failed: ${e.message}")
            }
        }
        viewModelScope.launch {
            repo.watchCustomCategories().collect { categories ->
                _uiState.value = _uiState.value.copy(
                    customCategories = categories,
                    customCategoriesMap = categories.associateBy { it.id }
                )
            }
        }
        // Balance snapshot history — re-queries whenever credit accounts list changes
        viewModelScope.launch {
            _uiState
                .map { it.creditAccounts.map { a -> a.accountId } }
                .distinctUntilChanged()
                .flatMapLatest { accountIds ->
                    if (accountIds.isEmpty()) flowOf(emptyList())
                    else repo.watchSnapshotsForAccounts(accountIds)
                }
                .collect { snapshots ->
                    val accounts = _uiState.value.creditAccounts
                    val byMonth = aggregateBalanceSnapshots(snapshots, accounts)
                    _uiState.value = _uiState.value.copy(balanceSnapshotsByMonth = byMonth)
                }
        }
        // Spending history — always uses all credit accounts as the universe; re-queries when
        // credit accounts list or the history-specific account filter or categories change.
        viewModelScope.launch {
            _uiState
                .map { HistoryTrigger(it.creditAccounts.map { a -> a.accountId }, it.historySelectedAccountIds, it.customCategoriesMap) }
                .distinctUntilChanged()
                .flatMapLatest { trigger ->
                    val accountIds = if (trigger.historySelectedIds == null) trigger.allAccountIds
                        else trigger.allAccountIds.filter { it in trigger.historySelectedIds }
                    
                    repo.watchMonthlySpendingHistoryWithRaw(accountIds).map { data ->
                        Triple(data, trigger.customMap, accountIds)
                    }
                }
                .collect { (data, customMap, accountIds) ->
                    val (categoryRows, rawTxns) = data
                    
                    val filteredTxns = if (accountIds.isEmpty()) emptyList()
                        else rawTxns.filter { it.accountId in accountIds }
                    
                    val history = aggregateSpendingHistory(categoryRows, customMap)
                    val (merchantHistory, merchantSummaries) = aggregateMerchantHistory(filteredTxns)
                    val allCategories = aggregateAllHistoryCategories(categoryRows, customMap)
                    _uiState.value = _uiState.value.copy(
                        spendingHistoryByMonth = history,
                        spendingHistoryByMonthAndMerchant = merchantHistory,
                        topMerchantsForHistory = merchantSummaries,
                        allHistoryCategories = allCategories,
                    )
                }
        }

        // Reactive Category Drill-down
        viewModelScope.launch {
            _uiState
                .map { Triple(it.categoryDrillDown, it.spendingWindow, it.creditAccounts) }
                .distinctUntilChanged { old, new -> 
                    // Only re-trigger if category or window changed. 
                    // Selection/accounts changes handled by the inner fetch logic.
                    old.first == new.first && old.second == new.second
                }
                .flatMapLatest { (category, window, _) ->
                    if (category == null) return@flatMapLatest flowOf(emptyList<PlaidTransaction>())
                    val ranges = currentSpendingRanges()
                    val allCodes = _uiState.value.categoryCodeGroups[category] ?: listOf(category)
                    repo.watchTransactionsForCategory(ranges, allCodes)
                }
                .collect { txns ->
                    if (_uiState.value.categoryDrillDown != null) {
                        _uiState.value = _uiState.value.copy(drillDownTransactions = txns)
                    }
                }
        }

        // Reactive All Transactions Drill-down
        viewModelScope.launch {
            _uiState
                .map { Pair(it.allTransactionsMode, it.spendingWindow) }
                .distinctUntilChanged()
                .flatMapLatest { (active, _) ->
                    if (!active) return@flatMapLatest flowOf(emptyList<PlaidTransaction>())
                    repo.watchTransactionsForWindow(currentSpendingRanges())
                }
                .collect { txns ->
                    if (_uiState.value.allTransactionsMode) {
                        _uiState.value = _uiState.value.copy(drillDownTransactions = txns)
                    }
                }
        }
    }

    fun sync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            try {
                db.dao().allInstitutionIds().forEach { id ->
                    repo.refreshTransactions(id)
                    repo.refreshLiabilities(id)
                }
                splitwiseRepo.refreshExpenses()
                _uiState.value = _uiState.value.copy(isSplitwiseConnected = splitwiseRepo.isConnected())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Sync failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                splitwiseRepo.loadOlderExpenses()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Load failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun setFilter(filter: ReconcileFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun openLinkSheet(item: ExpenseWithLinks) {
        viewModelScope.launch {
            val alreadyLinked = item.linkedTransactions.map { it.transactionId }.toSet()
            val all = db.dao().getRecentCreditTransactions()
                .filter { it.transactionId !in alreadyLinked }
            val suggested = splitwiseRepo.suggestedMatches(item.expense, all)
            _uiState.value = _uiState.value.copy(
                linkSheet = LinkSheetState(item.expense, suggested, all)
            )
        }
    }

    fun closeLinkSheet() {
        _uiState.value = _uiState.value.copy(linkSheet = null)
    }

    fun linkTransaction(expenseId: Long, plaidId: String) {
        viewModelScope.launch {
            splitwiseRepo.linkTransaction(expenseId, plaidId)
            closeLinkSheet()
        }
    }

    fun unlinkTransaction(expenseId: Long, plaidId: String) {
        viewModelScope.launch { splitwiseRepo.unlinkTransaction(expenseId, plaidId) }
    }

    fun dismiss(expenseId: Long) {
        viewModelScope.launch { splitwiseRepo.dismiss(expenseId) }
    }

    fun undismiss(expenseId: Long) {
        viewModelScope.launch { splitwiseRepo.undismiss(expenseId) }
    }

    fun acceptMatch(expenseId: Long) {
        viewModelScope.launch { splitwiseRepo.acceptMatch(expenseId) }
    }

    fun rejectMatch(expenseId: Long) {
        viewModelScope.launch { splitwiseRepo.rejectMatch(expenseId) }
    }

    fun previousMonth() {
        val newMonth = _uiState.value.selectedMonth.minusMonths(1)
        _uiState.value = _uiState.value.copy(selectedMonth = newMonth)
        loadOlderIfNeeded()
    }

    fun nextMonth() {
        val next = _uiState.value.selectedMonth.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _uiState.value = _uiState.value.copy(selectedMonth = next)
        }
    }

    private fun loadOlderIfNeeded() {
        if (_uiState.value.isSplitwiseLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSplitwiseLoading = true)
            try {
                do {
                    val result = splitwiseRepo.loadOlderExpenses()
                    val target = _uiState.value.selectedMonth
                    if (db.splitwiseDao().countExpensesForMonth(target.toString()) > 0) break
                    val oldestRaw = result.oldestRawDate ?: break
                    if (YearMonth.parse(oldestRaw.take(7)).isBefore(target)) break
                } while (result.rawCount > 0)
            } catch (e: Exception) {
                PulseLog.w("FinancesVM", "loadOlderIfNeeded failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isSplitwiseLoading = false)
            }
        }
    }

    // ── Spending window & account selection ───────────────────────────────────

    fun setSpendingWindow(window: SpendingWindow) {
        _uiState.value = computeWindowChange(_uiState.value, window)
    }

    fun clearBusinessCardSnackbar() {
        _uiState.value = _uiState.value.copy(pendingBusinessCardSnackbar = false)
    }

    fun toggleSpendingAccount(accountId: String) {
        val state = _uiState.value
        val newSelection = computeSpendingAccountToggle(
            accountId,
            state.selectedSpendingAccountIds,
            state.creditAccounts.map { it.accountId },
        )
        _uiState.value = state.copy(selectedSpendingAccountIds = newSelection)
    }

    // ── Category drill-down ───────────────────────────────────────────────────

    fun openCategoryDrillDown(category: String) {
        viewModelScope.launch {
            val ranges = currentSpendingRanges()
            val allCodes = _uiState.value.categoryCodeGroups[category] ?: listOf(category)
            val txns = repo.getTransactionsForCategory(ranges, allCodes)
            _uiState.value = _uiState.value.copy(
                categoryDrillDown = category,
                drillDownTransactions = txns,
            )
        }
    }

    fun closeCategoryDrillDown() {
        _uiState.value = _uiState.value.copy(
            categoryDrillDown = null,
            allTransactionsMode = false,
            drillDownTransactions = emptyList(),
            overridingTransaction = null,
            isBulkMode = false,
            bulkSelectedIds = emptySet(),
            isBulkPickerOpen = false,
            sessionCategorizedIds = emptySet(),
        )
    }

    fun openAllTransactionsDrillDown() {
        viewModelScope.launch {
            val txns = repo.getTransactionsForWindow(currentSpendingRanges())
            _uiState.value = _uiState.value.copy(
                allTransactionsMode = true,
                groupByMerchant = true,
                drillDownTransactions = txns,
                isBulkMode = true,
                bulkSelectedIds = emptySet(),
            )
        }
    }

    fun startOverride(tx: PlaidTransaction) {
        _uiState.value = _uiState.value.copy(overridingTransaction = tx)
    }

    fun cancelOverride() {
        _uiState.value = _uiState.value.copy(overridingTransaction = null)
    }

    fun applyOverride(transactionId: String, categoryId: String?) {
        if (categoryId == null) return
        viewModelScope.launch {
            val proposal = repo.proposeCategoryOverride(transactionId, categoryId)
            if (proposal != null) {
                _uiState.value = _uiState.value.copy(
                    pendingApplyState = PendingApplyState(listOf(transactionId), categoryId, listOf(proposal))
                )
            } else {
                repo.executeCategoryOverrides(listOf(transactionId), categoryId, emptyList())
                _uiState.value = _uiState.value.copy(overridingTransaction = null)
            }
        }
    }

    fun removeOverride(transactionId: String) {
        viewModelScope.launch {
            repo.executeCategoryOverrides(listOf(transactionId), null, emptyList())
            _uiState.value = _uiState.value.copy(overridingTransaction = null)
        }
    }

    fun removeRuleForMerchant(merchantName: String) {
        viewModelScope.launch {
            repo.deleteRuleAndClearOverrides(merchantName)
            _uiState.value = _uiState.value.copy(overridingTransaction = null)
        }
    }

    fun saveCustomCategory(name: String) {
        viewModelScope.launch { repo.saveCustomCategory(CustomCategory(id = java.util.UUID.randomUUID().toString(), name = name)) }
    }

    fun deleteCustomCategory(name: String) {
        viewModelScope.launch { repo.deleteCustomCategory(name) }
    }

    suspend fun countTransactionsWithOverride(name: String): Int =
        repo.countTransactionsWithOverride(name)

    suspend fun countOverridesForMerchant(merchantName: String, excludeId: String): Int =
        repo.countOverridesForMerchant(merchantName, excludeId)

    private fun executePendingApply(state: PendingApplyState) {
        viewModelScope.launch {
            repo.executeCategoryOverrides(state.transactionIds, state.categoryId, state.approvedMerchantNames)
            val current = _uiState.value
            _uiState.value = current.copy(
                pendingApplyState = null,
                overridingTransaction = null,
                isBulkPickerOpen = false,
                isBulkMode = current.allTransactionsMode, // Keep active if in All Transactions view
                bulkSelectedIds = emptySet(),
                sessionCategorizedIds = current.sessionCategorizedIds + state.transactionIds
            )
        }
    }

    fun confirmApplyToAll() {
        val pending = _uiState.value.pendingApplyState ?: return
        val currentProposal = pending.proposals.firstOrNull() ?: return
        
        val newApproved = pending.approvedMerchantNames + currentProposal.merchantName
        val remainingProposals = pending.proposals.drop(1)
        
        val newState = pending.copy(
            approvedMerchantNames = newApproved,
            proposals = remainingProposals
        )
        
        if (remainingProposals.isEmpty()) {
            executePendingApply(newState)
        } else {
            _uiState.value = _uiState.value.copy(pendingApplyState = newState)
        }
    }

    fun confirmJustThisOne() {
        val pending = _uiState.value.pendingApplyState ?: return
        val remainingProposals = pending.proposals.drop(1)
        
        val newState = pending.copy(proposals = remainingProposals)
        
        if (remainingProposals.isEmpty()) {
            executePendingApply(newState)
        } else {
            _uiState.value = _uiState.value.copy(pendingApplyState = newState)
        }
    }

    fun cancelApplyOverride() {
        _uiState.value = _uiState.value.copy(pendingApplyState = null)
    }

    // ── Bulk categorization mode ──────────────────────────────────────────────

    fun enterBulkMode() {
        _uiState.value = _uiState.value.copy(isBulkMode = true, bulkSelectedIds = emptySet())
    }

    fun exitBulkMode() {
        _uiState.value = _uiState.value.copy(
            isBulkMode = false,
            bulkSelectedIds = emptySet(),
            isBulkPickerOpen = false,
        )
    }

    fun toggleBulkSelection(transactionId: String) {
        val current = _uiState.value.bulkSelectedIds
        _uiState.value = _uiState.value.copy(
            bulkSelectedIds = if (transactionId in current) current - transactionId else current + transactionId
        )
    }

    fun openBulkPicker() {
        _uiState.value = _uiState.value.copy(isBulkPickerOpen = true)
    }

    fun closeBulkPicker() {
        _uiState.value = _uiState.value.copy(isBulkPickerOpen = false)
    }

    fun toggleGroupByMerchant() {
        _uiState.value = _uiState.value.copy(groupByMerchant = !_uiState.value.groupByMerchant)
    }

    fun toggleManageSortOrder(target: ManageSortOrder) {
        _uiState.value = _uiState.value.copy(manageSortOrder = target)
    }

    fun startBulkRemoveOverride() {
        val selectedIds = _uiState.value.bulkSelectedIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val proposals = repo.proposeRuleRemovalForIds(selectedIds)
            if (proposals.isNotEmpty()) {
                // Rules exist: show the merged Rule Removal dialog (with 'Remove for all' / 'Just selected')
                _uiState.value = _uiState.value.copy(
                    pendingApplyState = PendingApplyState(selectedIds, null, proposals)
                )
            } else {
                // No rules: show the simple confirmation dialog
                _uiState.value = _uiState.value.copy(showBulkRemovalWarning = true)
            }
        }
    }

    fun closeBulkRemovalWarning() {
        _uiState.value = _uiState.value.copy(showBulkRemovalWarning = false)
    }

    fun confirmBulkRemoveOverride() {
        val selectedIds = _uiState.value.bulkSelectedIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            repo.executeCategoryOverrides(selectedIds, null, emptyList())
            _uiState.value = _uiState.value.copy(
                showBulkRemovalWarning = false,
                bulkSelectedIds = emptySet(),
                sessionCategorizedIds = _uiState.value.sessionCategorizedIds + selectedIds
            )
        }
    }

    fun applyBulkCategory(category: String) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.bulkSelectedIds.toList()
            if (selectedIds.isEmpty()) return@launch

            val proposals = repo.proposeCategoryOverrideForIds(selectedIds, category)
            if (proposals.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    pendingApplyState = PendingApplyState(selectedIds, category, proposals)
                )
            } else {
                repo.executeCategoryOverrides(selectedIds, category, emptyList())
                _uiState.value = _uiState.value.copy(
                    isBulkPickerOpen = false,
                    isBulkMode = _uiState.value.allTransactionsMode, // Keep active if in All Transactions view
                    bulkSelectedIds = emptySet(),
                    sessionCategorizedIds = _uiState.value.sessionCategorizedIds + selectedIds,
                )
            }
        }
    }

    fun toggleIncludeReimbursements() {
        _uiState.value = _uiState.value.copy(
            includeReimbursements = !_uiState.value.includeReimbursements
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ── History filter functions ───────────────────────────────────────────────

    /** null = all selected; emptySet() = user explicitly cleared everything */
    fun setHistoryCategoryFilter(selected: Set<String>?) {
        _uiState.value = _uiState.value.copy(historySelectedCategories = selected)
    }

    /** null = all selected; emptySet() = user explicitly cleared everything */
    fun setHistoryMerchantFilter(selected: Set<String>?) {
        _uiState.value = _uiState.value.copy(historySelectedMerchants = selected)
    }

    fun setHistoryAccountFilter(selected: Set<String>?) {
        _uiState.value = _uiState.value.copy(historySelectedAccountIds = selected)
    }

    fun clearHistoryFilters() {
        _uiState.value = _uiState.value.copy(
            historySelectedCategories = null,
            historySelectedMerchants = null,
            historySelectedAccountIds = null,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun currentSpendingRanges(): List<AccountDateRange> {
        val state = _uiState.value
        val accounts = state.effectiveSpendingAccounts
        return buildAccountDateRanges(state.spendingWindow, accounts)
    }

    private fun buildAccountDateRanges(
        window: SpendingWindow,
        accounts: List<AccountEntity>,
    ): List<AccountDateRange> {
        val today = LocalDate.now()
        return when (window) {
            SpendingWindow.LAST_30_DAYS -> accounts.map { a ->
                AccountDateRange(a.accountId, today.minusDays(29).toString(), today.toString())
            }
            SpendingWindow.THIS_MONTH -> accounts.map { a ->
                AccountDateRange(a.accountId, today.withDayOfMonth(1).toString(), today.toString())
            }
            SpendingWindow.STATEMENT -> accounts.map { a ->
                val (start, end) = statementWindow(a, today)
                AccountDateRange(a.accountId, start, end)
            }
            SpendingWindow.THIS_CYCLE -> accounts.map { a ->
                // Transactions since the statement closed — these make up the current balance
                val statementClose = a.lastStatementDate
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: a.nextDueDate
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?.minusDays(21)
                val start = statementClose?.plusDays(1) ?: today.minusDays(29)
                AccountDateRange(a.accountId, start.toString(), today.toString())
            }
        }
    }

    /**
     * Returns the statement window for a credit card.
     * Prefers Plaid's actual last_statement_issue_date as the close date.
     * Falls back to dueDate-21d heuristic, then to last 30 days.
     * Start is always close-30d (standard 30-day billing cycle).
     */
    private fun statementWindow(account: AccountEntity, today: LocalDate): Pair<String, String> {
        val close = account.lastStatementDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: account.nextDueDate
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.minusDays(21)
        return if (close != null) {
            close.minusDays(30).toString() to close.toString()
        } else {
            today.minusDays(29).toString() to today.toString()
        }
    }

    // ── History aggregation ───────────────────────────────────────────────────

    /**
     * Groups raw [MonthlySpendRow] items (one per category per month) into
     * [MonthlySpendingHistory] objects ordered most-recent first.
     * Applies the same display-name merging as the main breakdown (e.g. two Gym codes → one row).
     */
    private fun aggregateSpendingHistory(rows: List<MonthlySpendRow>, customCategoriesMap: Map<String, CustomCategory>): List<MonthlySpendingHistory> {
        return rows
            .groupBy { it.month } // "yyyy-MM"
            .entries
            .sortedByDescending { it.key }
            .map { (monthStr, monthRows) ->
                // Merge sibling categories by display name — same logic as the main breakdown.
                val byDisplayName = monthRows.groupBy { CategoryMeta.resolveMeta(it.effectiveCategory, customCategoriesMap).displayName }
                val mergedCategories = byDisplayName.values.map { group ->
                    CategoryAmount(
                        effectiveCategory = group.first().effectiveCategory,
                        totalAmount = group.sumOf { it.totalAmount },
                    )
                }.sortedByDescending { it.totalAmount }

                val yearMonth = runCatching { YearMonth.parse(monthStr) }.getOrNull()
                    ?: return@map null
                MonthlySpendingHistory(
                    month = yearMonth,
                    totalAmount = mergedCategories.sumOf { it.totalAmount },
                    categories = mergedCategories,
                )
            }
            .filterNotNull()
    }

    /**
     * Aggregates all categories present across 12 months of history, sorted by total spend desc.
     * Used to populate the category filter dialog in the history sheet.
     */
    private fun aggregateAllHistoryCategories(rows: List<MonthlySpendRow>, customCategoriesMap: Map<String, CustomCategory>): List<CategoryAmount> {
        val byDisplayName = rows.groupBy { CategoryMeta.resolveMeta(it.effectiveCategory, customCategoriesMap).displayName }
        return byDisplayName.values.map { group ->
            CategoryAmount(
                effectiveCategory = group.first().effectiveCategory,
                totalAmount = group.sumOf { it.totalAmount },
            )
        }.sortedByDescending { it.totalAmount }
    }

    /**
     * Groups raw [PlaidTransaction]s into monthly merchant history and a merchant summary list.
     * Merchants with null [PlaidTransaction.merchantName] use [PlaidTransaction.name] as the key
     * (Plaid doesn't always resolve a canonical merchant name).
     *
     * Returns (monthlyMerchantHistory sorted most-recent first, merchantSummaries sorted by total desc).
     */
    private fun aggregateMerchantHistory(
        rawTxns: List<PlaidTransaction>,
    ): Pair<List<MonthlyMerchantHistory>, List<MerchantSpendSummary>> {
        // Monthly merchant history
        val monthlyHistory = rawTxns
            .groupBy { it.date.take(7) } // "yyyy-MM"
            .entries
            .sortedByDescending { it.key }
            .mapNotNull { (monthStr, txns) ->
                val yearMonth = runCatching { YearMonth.parse(monthStr) }.getOrNull()
                    ?: return@mapNotNull null
                val merchants = txns
                    .groupBy { it.merchantName ?: it.name }
                    .map { (merchant, mTxns) ->
                        MonthlyMerchantAmount(
                            merchantName = merchant,
                            totalAmount = mTxns.sumOf { it.amount },
                        )
                    }
                    .sortedByDescending { it.totalAmount }
                MonthlyMerchantHistory(month = yearMonth, merchants = merchants)
            }

        // Merchant summaries: total 12-month spend + primary category (mode)
        val summaries = rawTxns
            .groupBy { it.merchantName ?: it.name }
            .map { (merchant, txns) ->
                // Primary category = most common effectiveCategory for this merchant
                val primaryCategory = txns
                    .groupBy { it.effectiveCategory }
                    .maxBy { (_, v) -> v.size }
                    .key
                MerchantSpendSummary(
                    merchantName = merchant,
                    totalAmount = txns.sumOf { it.amount },
                    primaryCategory = primaryCategory,
                )
            }
            .sortedByDescending { it.totalAmount }

        return Pair(monthlyHistory, summaries)
    }

    /**
     * Groups raw [BalanceSnapshot]s into [MonthlyBalanceSnapshot] objects ordered most-recent
     * first. Takes the latest snapshot per account per calendar month.
     */
    private fun aggregateBalanceSnapshots(
        snapshots: List<BalanceSnapshot>,
        accounts: List<AccountEntity>,
    ): List<MonthlyBalanceSnapshot> {
        val accountMap = accounts.associateBy { it.accountId }
        // Group by (month, accountId), take the latest snapshot per group.
        val latestPerAccountPerMonth = snapshots
            .groupBy { snapshot ->
                // Convert epoch millis to "yyyy-MM" using Calendar to avoid java.time
                // timezone nuances on older devices — we only need the month bucket.
                val cal = java.util.Calendar.getInstance()
                cal.timeInMillis = snapshot.capturedAt
                val y = cal.get(java.util.Calendar.YEAR)
                val m = cal.get(java.util.Calendar.MONTH) + 1
                "${y}-${m.toString().padStart(2, '0')}" to snapshot.accountId
            }
            .mapValues { (_, group) -> group.maxBy { it.capturedAt } }

        // Re-group by month, then build AccountBalancePoint per account.
        return latestPerAccountPerMonth.entries
            .groupBy { (key, _) -> key.first } // group by "yyyy-MM"
            .entries
            .sortedByDescending { it.key }
            .mapNotNull { (monthStr, entries) ->
                val yearMonth = runCatching { YearMonth.parse(monthStr) }.getOrNull() ?: return@mapNotNull null
                val points = entries
                    .map { (_, snapshot) ->
                        val account = accountMap[snapshot.accountId]
                        AccountBalancePoint(
                            accountId = snapshot.accountId,
                            accountName = account?.name ?: snapshot.accountId,
                            statementBalance = snapshot.statementBalance,
                            currentBalance = snapshot.currentBalance,
                        )
                    }
                    .sortedBy { it.accountName }
                MonthlyBalanceSnapshot(month = yearMonth, snapshots = points)
            }
    }

    private fun buildDateRangeLabel(
        window: SpendingWindow,
        ranges: List<AccountDateRange>,
        accounts: List<AccountEntity>,
    ): String? {
        if (ranges.isEmpty()) return null
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        return when (window) {
            SpendingWindow.THIS_MONTH -> null
            SpendingWindow.LAST_30_DAYS -> {
                val start = LocalDate.parse(ranges.first().startDate).format(fmt)
                val end = LocalDate.parse(ranges.first().endDate).format(fmt)
                "$start – $end"
            }
            SpendingWindow.STATEMENT -> {
                if (accounts.size == 1) {
                    val r = ranges.first()
                    "${LocalDate.parse(r.startDate).format(fmt)} – ${LocalDate.parse(r.endDate).format(fmt)}"
                } else {
                    val earliest = ranges.minOf { LocalDate.parse(it.startDate) }
                    val latest = ranges.maxOf { LocalDate.parse(it.endDate) }
                    "${earliest.format(fmt)} – ${latest.format(fmt)} · ${accounts.size} cards"
                }
            }
            SpendingWindow.THIS_CYCLE -> {
                // Show "since <close date>" per card, or union start if multiple
                if (accounts.size == 1) {
                    val start = LocalDate.parse(ranges.first().startDate).format(fmt)
                    "Since $start"
                } else {
                    val earliest = ranges.minOf { LocalDate.parse(it.startDate) }
                    "Since ${earliest.format(fmt)} · ${accounts.size} cards"
                }
            }
        }
    }
}

/**
 * Pure function extracted for unit testability.
 * Returns the new [selectedSpendingAccountIds] after toggling [accountId]:
 * - If tapping would empty the selection, returns null (= all selected).
 * - Otherwise adds or removes [accountId] from the current set.
 */
internal fun computeSpendingAccountToggle(
    accountId: String,
    current: Set<String>?,
    allAccountIds: List<String>,
): Set<String>? {
    val currentSet = current ?: allAccountIds.toSet()
    val updated = if (accountId in currentSet) currentSet - accountId else currentSet + accountId
    return if (updated.isEmpty()) null else updated
}

/**
 * Pure function extracted for unit testability.
 * Returns a new [FinancesUiState] reflecting [window] selection with:
 * - STATEMENT: business CCs (those where [usesWindowHeuristic] is true) excluded from
 *   the selection; [pendingBusinessCardSnackbar] set when any were actually removed.
 * - Any other window: business CCs restored into the selection; snackbar cleared.
 *
 * "null" selection means all accounts selected. The function only produces null when
 * the resulting set equals the full account list (normalizing explicit-all back to null).
 */
internal fun computeWindowChange(state: FinancesUiState, window: SpendingWindow): FinancesUiState {
    val businessCardIds = state.creditAccounts
        .filter { it.usesWindowHeuristic }
        .map { it.accountId }
        .toSet()

    val usesStatementAnchor = window == SpendingWindow.STATEMENT

    val newSelectedIds: Set<String>?
    val showSnackbar: Boolean

    if (usesStatementAnchor && businessCardIds.isNotEmpty()) {
        // Expand null (= all) to an explicit set before removing business CCs, so the
        // removal is precise and user's personal-card chip state is preserved.
        val currentEffective = state.selectedSpendingAccountIds
            ?: state.creditAccounts.map { it.accountId }.toSet()
        val excluded = currentEffective - businessCardIds
        // If every selected account was a business CC, the result is empty — treat as
        // "no personal cards exist" and use null so the card shows an empty-state message
        // rather than silently hiding all data.
        newSelectedIds = if (excluded.isEmpty()) null else excluded
        showSnackbar = currentEffective.intersect(businessCardIds).isNotEmpty()
    } else {
        // Non-statement window: restore business CCs into the selection. Expand the
        // current set (or use all) and add back every business CC that was absent.
        val currentEffective = state.selectedSpendingAccountIds
            ?: state.creditAccounts.map { it.accountId }.toSet()
        val restored = currentEffective + businessCardIds
        // If restored equals the full account list, normalize back to null (= all).
        val allIds = state.creditAccounts.map { it.accountId }.toSet()
        newSelectedIds = if (restored == allIds) null else restored
        showSnackbar = false
    }

    return state.copy(
        spendingWindow = window,
        selectedSpendingAccountIds = newSelectedIds,
        pendingBusinessCardSnackbar = showSnackbar,
    )
}
