package com.pulsestock.app.ui.finances

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsestock.app.data.poarvault.AccountEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.YearMonth

enum class ReconcileFilter { TO_LINK, LINKED, DISMISSED, ALL }

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
    val includeReimbursements: Boolean = false,
    val isSplitwiseLoading: Boolean = false,
) {
    val displayedList: List<ExpenseWithLinks> get() = when (filter) {
        ReconcileFilter.TO_LINK   -> allWithLinks.filter { it.isUnlinked || it.isPendingAutoMatch }
        ReconcileFilter.LINKED    -> allWithLinks.filter { it.isReconciled }
        ReconcileFilter.DISMISSED -> allWithLinks.filter { it.isDismissed }
        ReconcileFilter.ALL       -> allWithLinks
    }
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

    private val _uiState = MutableStateFlow(FinancesUiState())
    val uiState: StateFlow<FinancesUiState> = _uiState.asStateFlow()

    init {
        // Auto-refresh Splitwise on every session so share amounts are always current
        if (splitwiseRepo.isConnected()) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isSplitwiseLoading = true)
                try {
                    splitwiseRepo.loadExpenses(loadOlder = false)
                } catch (e: Exception) {
                    PulseLog.w("FinancesVM", "auto-load Splitwise failed: ${e.message}")
                } finally {
                    _uiState.value = _uiState.value.copy(isSplitwiseLoading = false)
                }
            }
        }
        viewModelScope.launch {
            db.dao().watchCreditCardAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    creditAccounts = accounts,
                    isSplitwiseConnected = splitwiseRepo.isConnected(),
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
        viewModelScope.launch {
            _uiState
                .map { it.selectedMonth }
                .distinctUntilChanged()
                .flatMapLatest { month ->
                    PulseLog.d("FinancesVM", "reimbursable: querying month=$month (prefix=${month})")
                    splitwiseRepo.watchMonthlyReimbursable(month)
                }
                .collect { reimbursable ->
                    PulseLog.d("FinancesVM", "reimbursable: month=${_uiState.value.selectedMonth} → $$reimbursable")
                    _uiState.value = _uiState.value.copy(monthlyReimbursable = reimbursable)
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
                splitwiseRepo.loadExpenses(loadOlder = false)
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
                splitwiseRepo.loadExpenses(loadOlder = true)
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
        _uiState.value = _uiState.value.copy(
            selectedMonth = _uiState.value.selectedMonth.minusMonths(1)
        )
    }

    fun nextMonth() {
        val next = _uiState.value.selectedMonth.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _uiState.value = _uiState.value.copy(selectedMonth = next)
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
}
