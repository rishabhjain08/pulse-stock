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

    suspend fun refreshLiabilities(institutionId: String) {
        val token = tokens.getAccessToken(institutionId) ?: return
        val resp = api.getLiabilities(token)
        resp.liabilities.credit?.forEach { liability ->
            db.dao().updateLiability(
                accountId = liability.accountId,
                balance = liability.lastStatementBalance,
                minPay = liability.minimumPaymentAmount,
                dueDate = liability.nextPaymentDueDate,
            )
        }
    }

    suspend fun refreshTransactions(institutionId: String) {
        val token = tokens.getAccessToken(institutionId) ?: return
        val endDate = java.time.LocalDate.now().toString()
        val startDate = java.time.LocalDate.now().minusDays(89).toString()
        val resp = api.getTransactions(token, startDate, endDate)
        val transactions = resp.transactions
            .filter { !it.pending }
            .map { t ->
                PlaidTransaction(
                    transactionId = t.transactionId,
                    accountId = t.accountId,
                    institutionId = institutionId,
                    name = t.name,
                    amount = t.amount,
                    date = t.date,
                    category = t.category?.firstOrNull(),
                )
            }
        db.dao().upsertTransactions(transactions)
    }
}
