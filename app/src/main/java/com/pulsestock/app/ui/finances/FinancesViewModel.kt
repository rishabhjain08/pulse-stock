package com.pulsestock.app.ui.finances

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.PoarVaultApi
import com.pulsestock.app.data.poarvault.PoarVaultDatabase
import com.pulsestock.app.data.poarvault.PoarVaultRepository
import com.pulsestock.app.data.poarvault.SplitwiseApi
import com.pulsestock.app.data.poarvault.SplitwiseExpense
import com.pulsestock.app.data.poarvault.SplitwiseRepository
import com.pulsestock.app.data.poarvault.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LinkSheetState(
    val expense: SplitwiseExpense,
    val suggested: List<PlaidTransaction>,
    val all: List<PlaidTransaction>,
)

data class FinancesUiState(
    val creditAccounts: List<AccountEntity> = emptyList(),
    val inbox: List<SplitwiseExpense> = emptyList(),
    val inboxCount: Int = 0,
    val autoMatchedTx: Map<String, PlaidTransaction> = emptyMap(),
    val isSplitwiseConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val linkSheet: LinkSheetState? = null,
    val error: String? = null,
)

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
        viewModelScope.launch {
            db.dao().watchCreditCardAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    creditAccounts = accounts,
                    isSplitwiseConnected = splitwiseRepo.isConnected(),
                )
            }
        }
        viewModelScope.launch {
            splitwiseRepo.inbox.collect { expenses ->
                val autoMatchedIds = expenses.filter { it.isAutoMatched }.mapNotNull { it.linkedPlaidId }
                val autoMatchedTx = if (autoMatchedIds.isNotEmpty()) {
                    db.dao().getTransactionsByIds(autoMatchedIds).associateBy { it.transactionId }
                } else emptyMap()
                _uiState.value = _uiState.value.copy(inbox = expenses, autoMatchedTx = autoMatchedTx)
            }
        }
        viewModelScope.launch {
            splitwiseRepo.inboxCount.collect { count ->
                _uiState.value = _uiState.value.copy(inboxCount = count)
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

    fun openLinkSheet(expense: SplitwiseExpense) {
        viewModelScope.launch {
            val all = db.dao().getRecentCreditTransactions()
            val suggested = splitwiseRepo.suggestedMatches(expense, all)
            _uiState.value = _uiState.value.copy(linkSheet = LinkSheetState(expense, suggested, all))
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

    fun dismiss(expenseId: Long) {
        viewModelScope.launch { splitwiseRepo.dismiss(expenseId) }
    }

    fun acceptMatch(expenseId: Long) {
        viewModelScope.launch { splitwiseRepo.acceptMatch(expenseId) }
    }

    fun rejectMatch(expenseId: Long) {
        viewModelScope.launch { splitwiseRepo.rejectMatch(expenseId) }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
