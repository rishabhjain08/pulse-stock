package com.pulsestock.app.ui.finances

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsestock.app.data.poarvault.AccountDateRange
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.CategorySpend
import com.pulsestock.app.data.poarvault.ExpenseWithLinks
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.PoarVaultApi
import com.pulsestock.app.data.poarvault.PoarVaultDatabase
import com.pulsestock.app.data.poarvault.PoarVaultRepository
import com.pulsestock.app.data.poarvault.SplitwiseApi
import com.pulsestock.app.data.poarvault.SplitwiseExpense
import com.pulsestock.app.data.poarvault.SplitwiseRepository
import com.pulsestock.app.data.poarvault.TokenStore
import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    THIS_CYCLE("This cycle"),
    LAST_30_DAYS("Last 30 days"),
    THIS_MONTH("This month"),
}

data class LinkSheetState(
    val expense: SplitwiseExpense,
    val suggested: List<PlaidTransaction>,
    val all: List<PlaidTransaction>,
)

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
    val categoryDrillDown: String? = null,
    val drillDownTransactions: List<PlaidTransaction> = emptyList(),
    val overridingTransaction: PlaidTransaction? = null,
    val customCategories: List<String> = emptyList(),
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
            db.dao().watchCreditCardAccounts().collect { accounts ->
                val prev = _uiState.value
                // Initialize selection to all accounts on first load
                val selectedIds = prev.selectedSpendingAccountIds
                    ?: accounts.map { it.accountId }.toSet()
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
        // Spending category breakdown — re-queries whenever window, selection, or accounts change
        viewModelScope.launch {
            _uiState
                .map { Triple(it.spendingWindow, it.selectedSpendingAccountIds, it.creditAccounts) }
                .distinctUntilChanged()
                .flatMapLatest { (window, selectedIds, accounts) ->
                    val effectiveAccounts = if (selectedIds == null) accounts
                        else accounts.filter { it.accountId in selectedIds }
                    if (effectiveAccounts.isEmpty()) return@flatMapLatest flowOf(emptyList())
                    val ranges = buildAccountDateRanges(window, effectiveAccounts)
                    val label = buildDateRangeLabel(window, ranges, effectiveAccounts)
                    _uiState.value = _uiState.value.copy(spendingDateRangeLabel = label)
                    repo.watchCategoryBreakdown(ranges)
                }
                .collect { breakdown ->
                    PulseLog.d("FinancesVM", "categoryBreakdown: ${breakdown.size} categories")
                    _uiState.value = _uiState.value.copy(categoryBreakdown = breakdown)
                }
        }
        // Auto-load transactions on session start so Spending card is never empty
        viewModelScope.launch {
            try {
                PulseLog.d("FinancesVM", "auto-load: fetching transactions for all institutions")
                db.dao().allInstitutionIds().forEach { id -> repo.refreshTransactions(id) }
                PulseLog.d("FinancesVM", "auto-load: done")
            } catch (e: Exception) {
                PulseLog.w("FinancesVM", "auto-load transactions failed: ${e.message}")
            }
        }
        viewModelScope.launch {
            repo.watchCustomCategories().collect { categories ->
                _uiState.value = _uiState.value.copy(customCategories = categories)
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
        _uiState.value = _uiState.value.copy(spendingWindow = window)
    }

    fun toggleSpendingAccount(accountId: String) {
        val current = _uiState.value.selectedSpendingAccountIds
            ?: _uiState.value.creditAccounts.map { it.accountId }.toSet()
        val updated = if (accountId in current) current - accountId else current + accountId
        _uiState.value = _uiState.value.copy(selectedSpendingAccountIds = updated)
    }

    // ── Category drill-down ───────────────────────────────────────────────────

    fun openCategoryDrillDown(category: String) {
        viewModelScope.launch {
            val ranges = currentSpendingRanges()
            val txns = repo.getTransactionsForCategory(ranges, category)
            _uiState.value = _uiState.value.copy(
                categoryDrillDown = category,
                drillDownTransactions = txns,
            )
        }
    }

    fun closeCategoryDrillDown() {
        _uiState.value = _uiState.value.copy(
            categoryDrillDown = null,
            drillDownTransactions = emptyList(),
            overridingTransaction = null,
        )
    }

    fun startOverride(tx: PlaidTransaction) {
        _uiState.value = _uiState.value.copy(overridingTransaction = tx)
    }

    fun cancelOverride() {
        _uiState.value = _uiState.value.copy(overridingTransaction = null)
    }

    fun applyOverride(transactionId: String, category: String?) {
        viewModelScope.launch {
            repo.setCategoryOverride(transactionId, category)
            val drillCategory = _uiState.value.categoryDrillDown
            if (drillCategory != null) {
                val ranges = currentSpendingRanges()
                val txns = repo.getTransactionsForCategory(ranges, drillCategory)
                _uiState.value = _uiState.value.copy(
                    drillDownTransactions = txns,
                    overridingTransaction = null,
                )
            } else {
                _uiState.value = _uiState.value.copy(overridingTransaction = null)
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
