package com.pulsestock.app.data.poarvault

import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

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
        val token = tokens.getAccessToken(institutionId) ?: run {
            PulseLog.w("PoarVaultRepo", "refreshLiabilities: no token for $institutionId")
            return
        }
        PulseLog.d("PoarVaultRepo", "refreshLiabilities: fetching for $institutionId")
        val resp = api.getLiabilities(token)
        val creditCount = resp.liabilities.credit?.size ?: 0
        PulseLog.d("PoarVaultRepo", "refreshLiabilities: $creditCount credit liability item(s)")
        resp.liabilities.credit?.forEach { liability ->
            PulseLog.d("PoarVaultRepo", "refreshLiabilities: accountId=${liability.accountId} statementBal=${liability.lastStatementBalance} minPay=${liability.minimumPaymentAmount} dueDate=${liability.nextPaymentDueDate}")
            db.dao().updateLiability(
                accountId = liability.accountId,
                balance = liability.lastStatementBalance,
                minPay = liability.minimumPaymentAmount,
                dueDate = liability.nextPaymentDueDate,
            )
        }
    }

    fun watchMonthlyCategoryBreakdown(month: YearMonth): Flow<List<CategorySpend>> =
        db.dao().watchMonthlyCategoryBreakdown(month.toString())

    suspend fun getTransactionsForCategory(month: YearMonth, category: String): List<PlaidTransaction> =
        db.dao().getTransactionsForCategory(month.toString(), category)

    suspend fun setCategoryOverride(transactionId: String, override: String?) =
        db.dao().setCategoryOverride(transactionId, override)

    fun watchCustomCategories(): Flow<List<String>> = db.dao().watchCustomCategories()

    suspend fun refreshTransactions(institutionId: String) {
        val token = tokens.getAccessToken(institutionId) ?: run {
            PulseLog.w("PoarVaultRepo", "refreshTransactions: no token for $institutionId")
            return
        }
        val endDate = java.time.LocalDate.now().toString()
        val startDate = java.time.LocalDate.now().minusDays(89).toString()
        PulseLog.d("PoarVaultRepo", "refreshTransactions: fetching $institutionId $startDate → $endDate")
        val resp = api.getTransactions(token, startDate, endDate)
        PulseLog.d("PoarVaultRepo", "refreshTransactions: total=${resp.totalTransactions} returned=${resp.transactions.size}")
        val nonPending = resp.transactions.filter { !it.pending }
        PulseLog.d("PoarVaultRepo", "refreshTransactions: ${nonPending.size} non-pending transactions")

        // Preserve any user-set category overrides — upsert replaces whole rows
        val existingOverrides = db.dao()
            .getOverridesForIds(nonPending.map { it.transactionId })
            .associate { it.transactionId to it.categoryOverride }

        val transactions = nonPending.map { t ->
            PlaidTransaction(
                transactionId = t.transactionId,
                accountId = t.accountId,
                institutionId = institutionId,
                name = t.name,
                amount = t.amount,
                date = t.date,
                category = t.category?.firstOrNull(),
                pfcPrimary = t.personalFinanceCategory?.primary,
                pfcDetailed = t.personalFinanceCategory?.detailed,
                categoryOverride = existingOverrides[t.transactionId],
            )
        }
        db.dao().upsertTransactions(transactions)
        PulseLog.d("PoarVaultRepo", "refreshTransactions: upserted ${transactions.size} transactions")
    }
}
