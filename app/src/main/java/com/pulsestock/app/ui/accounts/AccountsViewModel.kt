package com.pulsestock.app.ui.accounts

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsestock.app.BuildConfig
import com.pulsestock.app.PulseLog
import com.pulsestock.app.data.poarvault.InstitutionWithAccounts
import com.pulsestock.app.data.poarvault.PoarVaultApi
import com.pulsestock.app.data.poarvault.PoarVaultDatabase
import com.pulsestock.app.data.poarvault.PoarVaultRepository
import com.pulsestock.app.data.poarvault.SplitwiseApi
import com.pulsestock.app.data.poarvault.SplitwiseAuthBus
import com.pulsestock.app.data.poarvault.SplitwiseRepository
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
    val syncingIds: Set<String> = emptySet(),
    val isSplitwiseConnected: Boolean = false,
    val isSplitwiseConnecting: Boolean = false,
    val error: String? = null,
)

class AccountsViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val tokens = TokenStore(ctx)
    private val db = PoarVaultDatabase.get(ctx, tokens.getOrCreatePassphrase())
    private val api = PoarVaultApi()
    private val splitwiseApi = SplitwiseApi()
    private val repo = PoarVaultRepository(api, db, tokens)
    private val splitwiseRepo = SplitwiseRepository(api, splitwiseApi, db, tokens)

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val _linkToken = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val linkToken: SharedFlow<String> = _linkToken.asSharedFlow()

    private val _launchUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val launchUrl: SharedFlow<String> = _launchUrl.asSharedFlow()

    private val userId: String
        get() = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)

    init {
        viewModelScope.launch {
            repo.institutions.collect { list ->
                _uiState.value = _uiState.value.copy(
                    institutions = list,
                    isInitialLoad = false,
                    isSplitwiseConnected = splitwiseRepo.isConnected(),
                )
            }
        }
        viewModelScope.launch {
            SplitwiseAuthBus.code.collect { code ->
                PulseLog.d("AccountsVM", "SplitwiseAuthBus delivered code (${code.length} chars)")
                handleOAuthCode(code)
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

    fun syncInstitution(institutionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncingIds = _uiState.value.syncingIds + institutionId)
            try {
                repo.refreshInstitution(institutionId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Sync failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(syncingIds = _uiState.value.syncingIds - institutionId)
            }
        }
    }

    fun connectSplitwise() {
        val url = "https://secure.splitwise.com/oauth/authorize" +
            "?response_type=code" +
            "&client_id=${BuildConfig.SPLITWISE_CONSUMER_KEY}" +
            "&redirect_uri=pulsestock%3A%2F%2Fsplitwise%2Fcallback"
        PulseLog.d("AccountsVM", "connectSplitwise: emitting OAuth URL (client_id=${BuildConfig.SPLITWISE_CONSUMER_KEY})")
        viewModelScope.launch { _launchUrl.emit(url) }
    }

    fun disconnectSplitwise() {
        splitwiseRepo.disconnect()
        _uiState.value = _uiState.value.copy(isSplitwiseConnected = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun handleOAuthCode(code: String) {
        viewModelScope.launch {
            PulseLog.d("AccountsVM", "handleOAuthCode: starting token exchange")
            _uiState.value = _uiState.value.copy(isSplitwiseConnecting = true)
            try {
                splitwiseRepo.handleOAuthCode(code)
                PulseLog.d("AccountsVM", "handleOAuthCode: success — Splitwise connected")
                _uiState.value = _uiState.value.copy(isSplitwiseConnected = true)
            } catch (e: Exception) {
                PulseLog.e("AccountsVM", "handleOAuthCode: failed", e)
                _uiState.value = _uiState.value.copy(error = "Splitwise connect failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isSplitwiseConnecting = false)
            }
        }
    }
}
