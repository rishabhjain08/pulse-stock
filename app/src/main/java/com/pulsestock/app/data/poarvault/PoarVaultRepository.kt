package com.pulsestock.app.data.poarvault

import androidx.sqlite.db.SimpleSQLiteQuery
import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * Returned after setting a single-transaction override when Plaid knows the merchant name.
 * The ViewModel uses this to prompt the user: "Apply to [otherCount] other transactions?"
 */
data class MerchantRuleProposal(
    val merchantName: String,
    val category: String,
    val otherCount: Int,
)

/** One row of the per-account date window used when building category-breakdown queries. */
data class AccountDateRange(
    val accountId: String,
    val startDate: String, // "yyyy-MM-dd"
    val endDate: String,   // "yyyy-MM-dd"
)

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
        val ids = db.dao().allInstitutionIds()
        PulseLog.d("PoarVaultRepo", "refreshAll: ${ids.size} institution(s)")
        ids.forEach { id ->
            refreshInstitution(id)
            refreshTransactions(id)
            refreshLiabilities(id)
        }
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
            PulseLog.d("PoarVaultRepo", "refreshLiabilities: accountId=${liability.accountId} statementBal=${liability.lastStatementBalance} statementDate=${liability.lastStatementIssueDate} dueDate=${liability.nextPaymentDueDate}")
            db.dao().updateLiability(
                accountId = liability.accountId,
                balance = liability.lastStatementBalance,
                minPay = liability.minimumPaymentAmount,
                dueDate = liability.nextPaymentDueDate,
                statementDate = liability.lastStatementIssueDate,
            )
        }
    }

    // ── Category breakdown ────────────────────────────────────────────────────

    /**
     * Returns a reactive Flow of category spending. Each [AccountDateRange] specifies which
     * transactions to include per card — this handles Statement mode where each card has its own
     * billing-cycle window, as well as uniform-window modes (all entries share the same dates).
     */
    fun watchCategoryBreakdown(ranges: List<AccountDateRange>): Flow<List<CategorySpend>> {
        if (ranges.isEmpty()) return flowOf(emptyList())
        val query = buildCategoryBreakdownQuery(ranges)
        return db.dao().watchCategoryBreakdownRaw(query)
    }

    suspend fun getTransactionsForCategory(
        ranges: List<AccountDateRange>,
        category: String,
    ): List<PlaidTransaction> {
        if (ranges.isEmpty()) return emptyList()
        val query = buildTransactionsForCategoryQuery(ranges, category)
        return db.dao().getTransactionsForCategoryRaw(query)
    }

    /**
     * Sets the override for a single transaction, then checks whether Plaid knows the merchant
     * name and whether other transactions share it.
     *
     * Returns a [MerchantRuleProposal] when the user should be prompted to apply the override
     * to all matching transactions, or null when no prompt is needed (clear override, or no
     * merchantName available, or this is the only transaction for that merchant).
     */
    suspend fun setCategoryOverride(
        transactionId: String,
        override: String?,
    ): MerchantRuleProposal? {
        // Read merchantName before writing so we don't need a second read.
        val merchantName = db.dao()
            .getTransactionsByIds(listOf(transactionId))
            .firstOrNull()?.merchantName
        db.dao().setCategoryOverride(transactionId, override)
        if (override == null || merchantName == null) return null
        val otherCount = db.dao().countOtherTransactionsForMerchant(merchantName, transactionId)
        return if (otherCount > 0) MerchantRuleProposal(merchantName, override, otherCount) else null
    }

    /** Applies [category] to all existing transactions for [merchantName] and saves the rule. */
    suspend fun applyRuleToAllMatching(merchantName: String, category: String) {
        db.dao().applyOverrideToMerchant(merchantName, category)
        db.dao().upsertCategoryRule(CategoryRule(merchantName, category))
        PulseLog.d("PoarVaultRepo", "applyRuleToAllMatching: $merchantName → $category")
    }

    fun watchCustomCategories(): Flow<List<String>> = db.dao().watchCustomCategories()

    suspend fun saveCustomCategory(name: String) {
        db.dao().upsertCustomCategory(CustomCategory(name))
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    suspend fun refreshTransactions(institutionId: String) {
        val token = tokens.getAccessToken(institutionId) ?: run {
            PulseLog.w("PoarVaultRepo", "refreshTransactions: no token for $institutionId")
            return
        }
        val endDate = LocalDate.now().toString()
        val startDate = LocalDate.now().minusDays(89).toString()
        PulseLog.d("PoarVaultRepo", "refreshTransactions: fetching $institutionId $startDate → $endDate")
        val resp = api.getTransactions(token, startDate, endDate)
        PulseLog.d("PoarVaultRepo", "refreshTransactions: total=${resp.totalTransactions} returned=${resp.transactions.size}")
        val nonPending = resp.transactions.filter { !it.pending }
        PulseLog.d("PoarVaultRepo", "refreshTransactions: ${nonPending.size} non-pending transactions")

        val existingOverrides = db.dao()
            .getOverridesForIds(nonPending.map { it.transactionId })
            .associate { it.transactionId to it.categoryOverride }

        // Pre-load all rules so we don't query per-transaction in the loop.
        val rules = db.dao().getAllCategoryRules().associate { it.merchantName to it.category }

        val transactions = nonPending.map { t ->
            // Priority: explicit per-transaction override > saved merchant rule > none
            val override = existingOverrides[t.transactionId]
                ?: t.merchantName?.let { rules[it] }
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
                categoryOverride = override,
                merchantName = t.merchantName,
            )
        }
        db.dao().upsertTransactions(transactions)
        PulseLog.d("PoarVaultRepo", "refreshTransactions: upserted ${transactions.size} transactions")
    }

    // ── SQL builders ──────────────────────────────────────────────────────────

    private fun buildCategoryBreakdownQuery(ranges: List<AccountDateRange>): SimpleSQLiteQuery {
        val sb = StringBuilder(
            """SELECT COALESCE(pt.categoryOverride, pt.pfcDetailed, pt.pfcPrimary, pt.category, 'OTHER') AS effectiveCategory,
               SUM(pt.amount) AS totalAmount,
               COUNT(*) AS txCount
        FROM plaid_transactions pt
        INNER JOIN accounts a ON pt.accountId = a.accountId
        WHERE a.type = 'credit' AND ("""
        )
        val args = mutableListOf<Any>()
        ranges.forEachIndexed { i, r ->
            if (i > 0) sb.append(" OR ")
            sb.append("(pt.accountId = ? AND pt.date >= ? AND pt.date <= ?)")
            args.addAll(listOf(r.accountId, r.startDate, r.endDate))
        }
        sb.append(") GROUP BY effectiveCategory ORDER BY totalAmount DESC")
        return SimpleSQLiteQuery(sb.toString(), args.toTypedArray())
    }

    private fun buildTransactionsForCategoryQuery(
        ranges: List<AccountDateRange>,
        category: String,
    ): SimpleSQLiteQuery {
        val sb = StringBuilder(
            """SELECT pt.* FROM plaid_transactions pt
        INNER JOIN accounts a ON pt.accountId = a.accountId
        WHERE a.type = 'credit'
          AND COALESCE(pt.categoryOverride, pt.pfcDetailed, pt.pfcPrimary, pt.category, 'OTHER') = ?
          AND ("""
        )
        val args = mutableListOf<Any>(category)
        ranges.forEachIndexed { i, r ->
            if (i > 0) sb.append(" OR ")
            sb.append("(pt.accountId = ? AND pt.date >= ? AND pt.date <= ?)")
            args.addAll(listOf(r.accountId, r.startDate, r.endDate))
        }
        sb.append(") ORDER BY pt.date DESC")
        return SimpleSQLiteQuery(sb.toString(), args.toTypedArray())
    }
}
