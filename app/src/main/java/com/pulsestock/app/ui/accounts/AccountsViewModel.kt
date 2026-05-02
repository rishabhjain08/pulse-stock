package com.pulsestock.app.ui.accounts

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsestock.app.data.poarvault.InstitutionWithAccounts
import com.pulsestock.app.data.poarvault.PoarVaultApi
import com.pulsestock.app.data.poarvault.PoarVaultDatabase
import com.pulsestock.app.data.poarvault.PoarVaultRepository
import com.pulsestock.app.data.poarvault.TokenStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountsUiState(
    val institutions: List<InstitutionWithAccounts> = emptyList(),
    val isInitialLoad: Boolean = true,
    val isSyncing: Boolean = false,
    val error: String? = null,
)

class AccountsViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val tokens = TokenStore(ctx)
    private val db = PoarVaultDatabase.get(ctx, tokens.getOrCreatePassphrase())
    private val api = PoarVaultApi()
    private val repo = PoarVaultRepository(api, db, tokens)

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val _linkToken = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val linkToken: SharedFlow<String> = _linkToken.asSharedFlow()

    private val userId: String
        get() = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)

    init {
        // Only reads from the local DB — no Plaid API calls on startup.
        viewModelScope.launch {
            repo.institutions.collect { list ->
                _uiState.value = _uiState.value.copy(institutions = list, isInitialLoad = false)
            }
        }
    }

    fun requestLinkToken() {
        viewModelScope.launch {
            try {
                _linkToken.emit(repo.fetchLinkToken(userId))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Couldn't start bank connection: ${e.message}")
            }
        }
    }

    fun onLinkSuccess(publicToken: String, institutionId: String, institutionName: String) {
        viewModelScope.launch {
            try {
                repo.onLinkSuccess(publicToken, institutionId, institutionName)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Couldn't connect bank: ${e.message}")
            }
        }
    }

    fun disconnect(institutionId: String) {
        viewModelScope.launch { repo.disconnect(institutionId) }
    }

    fun sync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            try {
                repo.refreshAll()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Sync failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
