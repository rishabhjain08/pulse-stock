package com.pulsestock.app.data.poarvault

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * One row from the monthly spending history query: the year-month bucket, one category code,
 * and the summed amount for that bucket. The ViewModel aggregates these into display groups.
 */
data class MonthlySpendRow(
    val month: String,             // "yyyy-MM"
    val effectiveCategory: String,
    val totalAmount: Double,
)

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
        db.withTransaction {
            // Collect account IDs before cascade-deleting the institution so we can
            // wipe tables that have no FK relationship to institutions.
            val accountIds = db.dao().accountIdsForInstitution(institutionId)
            if (accountIds.isNotEmpty()) {
                db.dao().deleteTransactionsByAccounts(accountIds)
                db.dao().deleteSnapshotsByAccounts(accountIds)
            }
            db.dao().deleteInstitution(institutionId) // cascades → accounts
        }
    }

    suspend fun refreshInstitution(institutionId: String) {
        val token = tokens.getAccessToken(institutionId) ?: return
        PulseLog.d("PoarVaultRepo", "refreshInstitution: fetching balances for $institutionId")
        val balances = api.getBalances(token)
        val now = System.currentTimeMillis()
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
                lastRefreshed = now,
            )
        }
        db.dao().upsertAccounts(accounts)
        val creditCount = accounts.count { it.type == "credit" }
        PulseLog.d("PoarVaultRepo", "refreshInstitution: upserted ${accounts.size} account(s), $creditCount are 'credit'")
        // Write a balance snapshot for each credit account so the history sheet has
        // current-balance data even before liabilities load. statementBalance is null here
        // and gets filled in by refreshLiabilities if available.
        accounts.filter { it.type == "credit" }.forEach { account ->
            db.dao().insertBalanceSnapshot(
                BalanceSnapshot(
                    accountId = account.accountId,
                    capturedAt = now,
                    statementBalance = null,
                    currentBalance = account.currentBalance,
                )
            )
        }
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
        val liabilityCapturedAt = System.currentTimeMillis()
        resp.liabilities.credit?.forEach { liability ->
            PulseLog.d("PoarVaultRepo", "refreshLiabilities: accountId=${liability.accountId} statementBal=${liability.lastStatementBalance} statementDate=${liability.lastStatementIssueDate} dueDate=${liability.nextPaymentDueDate}")
            db.dao().updateLiability(
                accountId = liability.accountId,
                balance = liability.lastStatementBalance,
                minPay = liability.minimumPaymentAmount,
                dueDate = liability.nextPaymentDueDate,
                statementDate = liability.lastStatementIssueDate,
            )
            // Write a snapshot with statementBalance populated (currentBalance from the
            // accounts list in the liabilities response, if available). Monthly aggregation
            // uses MAX(capturedAt) per account per month, so the liabilities snapshot —
            // written seconds after the balances snapshot — will be the one surfaced.
            val currentBal = resp.accounts.find { it.accountId == liability.accountId }?.balances?.current
            db.dao().insertBalanceSnapshot(
                BalanceSnapshot(
                    accountId = liability.accountId,
                    capturedAt = liabilityCapturedAt,
                    statementBalance = liability.lastStatementBalance,
                    currentBalance = currentBal,
                )
            )
        }
    }

    // ── Balance snapshot history ──────────────────────────────────────────────

    /**
     * Returns a Flow of all snapshots for [accountIds] captured within the last 12 months.
     * The ViewModel groups these by calendar month and takes the latest per account per month.
     */
    fun watchSnapshotsForAccounts(accountIds: List<String>): Flow<List<BalanceSnapshot>> {
        if (accountIds.isEmpty()) return flowOf(emptyList())
        val twelveMonthsAgoMillis = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MONTH, -12)
        }.timeInMillis
        return db.dao().getSnapshotsForAccounts(accountIds, twelveMonthsAgoMillis)
    }

    // ── Spending history ──────────────────────────────────────────────────────

    /**
     * One-shot query: returns all spending rows for the last 12 months grouped by
     * (month, effectiveCategory) for the given account IDs. Called from the ViewModel
     * whenever the relevant state changes.
     */
    suspend fun getMonthlySpendingHistory(accountIds: List<String>): List<MonthlySpendRow> {
        return getMonthlySpendingHistoryWithRaw(accountIds).first
    }

    /**
     * Same as [getMonthlySpendingHistory] but also returns the raw [PlaidTransaction] list in
     * a single fetch. The ViewModel uses the raw list to derive merchant-level aggregates without
     * a second database round-trip.
     *
     * Returns (categoryRows, rawTransactions).
     */
    suspend fun getMonthlySpendingHistoryWithRaw(
        accountIds: List<String>,
    ): Pair<List<MonthlySpendRow>, List<PlaidTransaction>> {
        if (accountIds.isEmpty()) return Pair(emptyList(), emptyList())
        val query = buildMonthlySpendingQuery(accountIds)
        val txns = db.dao().getTransactionsForCategoryRaw(query)
        val rows = txns
            .groupBy { tx -> tx.date.take(7) } // "yyyy-MM"
            .flatMap { (monthStr, monthTxns) ->
                monthTxns.groupBy { tx ->
                    tx.categoryOverride ?: tx.category ?: tx.pfcPrimary ?: "OTHER"
                }.map { (cat, catTxns) ->
                    MonthlySpendRow(
                        month = monthStr,
                        effectiveCategory = cat,
                        totalAmount = catTxns.sumOf { it.amount },
                    )
                }
            }
        return Pair(rows, txns)
    }

    private fun buildMonthlySpendingQuery(accountIds: List<String>): SimpleSQLiteQuery {
        // Fetch all transactions for the given accounts over the last 12 months.
        // Category grouping is done in Kotlin after fetch to reuse the effectiveCategory
        // priority chain without duplicating complex SQL COALESCE logic.
        val placeholders = accountIds.joinToString(",") { "?" }
        val twelveMonthsAgo = java.time.LocalDate.now().minusDays(364).toString()
        val sql = """
            SELECT pt.* FROM plaid_transactions pt
            INNER JOIN accounts a ON pt.accountId = a.accountId
            WHERE a.type = 'credit'
              AND pt.amount > 0
              AND COALESCE(pt.categoryOverride, pt.pfcDetailed, pt.pfcPrimary, pt.category, '') NOT LIKE 'TRANSFER%'
              AND COALESCE(pt.categoryOverride, pt.pfcDetailed, pt.pfcPrimary, pt.category, '') NOT LIKE 'LOAN_PAYMENT%'
              AND pt.accountId IN ($placeholders)
              AND pt.date >= ?
            ORDER BY pt.date DESC
        """.trimIndent()
        val args = (accountIds + listOf(twelveMonthsAgo)).toTypedArray()
        return SimpleSQLiteQuery(sql, args)
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
        categories: List<String>,
    ): List<PlaidTransaction> {
        if (ranges.isEmpty() || categories.isEmpty()) return emptyList()
        val query = buildTransactionsForCategoryQuery(ranges, categories)
        return db.dao().getTransactionsForCategoryRaw(query)
    }

    suspend fun getTransactionsForWindow(ranges: List<AccountDateRange>): List<PlaidTransaction> {
        if (ranges.isEmpty()) return emptyList()
        val query = buildTransactionsForWindowQuery(ranges)
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

    suspend fun countTransactionsWithOverride(category: String): Int =
        db.dao().countTransactionsWithOverride(category)

    suspend fun deleteCustomCategory(name: String) {
        db.dao().deleteCustomCategory(name)
        db.dao().clearOverrideByCategory(name)
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    suspend fun refreshTransactions(institutionId: String) {
        val token = tokens.getAccessToken(institutionId) ?: run {
            PulseLog.w("PoarVaultRepo", "refreshTransactions: no token for $institutionId")
            return
        }
        val endDate = LocalDate.now().toString()
        val startDate = LocalDate.now().minusDays(730).toString()
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
            """SELECT COALESCE(pt.categoryOverride, pt.category, pt.pfcPrimary, 'OTHER') AS effectiveCategory,
               SUM(pt.amount) AS totalAmount,
               COUNT(*) AS txCount
        FROM plaid_transactions pt
        INNER JOIN accounts a ON pt.accountId = a.accountId
        WHERE a.type = 'credit' 
          AND pt.amount > 0
          AND COALESCE(pt.categoryOverride, pt.category, '') NOT LIKE 'TRANSFER%'
          AND COALESCE(pt.categoryOverride, pt.category, '') NOT LIKE 'LOAN_PAYMENT%'
          AND (
"""
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
        categories: List<String>,
    ): SimpleSQLiteQuery {
        val placeholders = categories.joinToString(",") { "?" }
        val sb = StringBuilder(
            """SELECT pt.* FROM plaid_transactions pt
        INNER JOIN accounts a ON pt.accountId = a.accountId
        WHERE a.type = 'credit'
          AND COALESCE(pt.categoryOverride, pt.category, pt.pfcPrimary, 'OTHER') IN ($placeholders)
          AND ("""
        )
        val args = mutableListOf<Any>()
        args.addAll(categories)
        ranges.forEachIndexed { i, r ->
            if (i > 0) sb.append(" OR ")
            sb.append("(pt.accountId = ? AND pt.date >= ? AND pt.date <= ?)")
            args.addAll(listOf(r.accountId, r.startDate, r.endDate))
        }
        sb.append(") ORDER BY pt.date DESC")
        return SimpleSQLiteQuery(sb.toString(), args.toTypedArray())
    }

    private fun buildTransactionsForWindowQuery(ranges: List<AccountDateRange>): SimpleSQLiteQuery {
        val sb = StringBuilder(
            """SELECT pt.* FROM plaid_transactions pt
        INNER JOIN accounts a ON pt.accountId = a.accountId
        WHERE a.type = 'credit' AND ("""
        )
        val args = mutableListOf<Any>()
        ranges.forEachIndexed { i, r ->
            if (i > 0) sb.append(" OR ")
            sb.append("(pt.accountId = ? AND pt.date >= ? AND pt.date <= ?)")
            args.addAll(listOf(r.accountId, r.startDate, r.endDate))
        }
        sb.append(") ORDER BY pt.date DESC")
        return SimpleSQLiteQuery(sb.toString(), args.toTypedArray())
    }
}
