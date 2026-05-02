package com.pulsestock.app.data.poarvault

import kotlinx.coroutines.flow.Flow

class PoarVaultRepository(
    private val api: PoarVaultApi,
    private val db: PoarVaultDatabase,
    private val tokens: TokenStore,
) {
    val institutions: Flow<List<InstitutionWithAccounts>> = db.dao().watchInstitutions()

    suspend fun fetchLinkToken(userId: String): String = api.getLinkToken(userId).link_token

    suspend fun onLinkSuccess(publicToken: String, institutionId: String, institutionName: String) {
        val exchange = api.exchangeToken(publicToken)
        tokens.putAccessToken(institutionId, exchange.access_token)
        db.dao().upsertInstitution(InstitutionEntity(institutionId, institutionName))
        refreshInstitution(institutionId)
    }

    suspend fun refreshAll() {
        db.dao().allInstitutionIds().forEach { refreshInstitution(it) }
    }

    suspend fun disconnect(institutionId: String) {
        val token = tokens.getAccessToken(institutionId) ?: return
        runCatching { api.disconnect(token) }
        tokens.removeAccessToken(institutionId)
        db.dao().deleteInstitution(institutionId)
    }

    suspend fun refreshInstitution(institutionId: String) {
        val token = tokens.getAccessToken(institutionId) ?: return
        val balances = api.getBalances(token)
        val accounts = balances.accounts.map { a ->
            AccountEntity(
                accountId = a.accountId,
                institutionId = institutionId,
                name = a.name,
                type = a.type,
                subtype = a.subtype,
                currentBalance = a.balances.current,
                availableBalance = a.balances.available,
                currencyCode = a.balances.isoCurrencyCode,
                lastRefreshed = System.currentTimeMillis(),
            )
        }
        db.dao().upsertAccounts(accounts)
    }
}
