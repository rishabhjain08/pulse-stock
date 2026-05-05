package com.pulsestock.app.data.poarvault

import androidx.room.withTransaction
import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.YearMonth
import kotlin.math.abs

// Returned by loadExpenses(loadOlder=true) so the caller can detect end-of-history
// and avoid fetching past a target month that has no paid expenses.
data class LoadOlderResult(val rawCount: Int, val oldestRawDate: String?)

class SplitwiseRepository(
    private val api: PoarVaultApi,
    private val splitwiseApi: SplitwiseApi,
    private val db: PoarVaultDatabase,
    private val tokens: TokenStore,
) {
    val allWithLinks: Flow<List<ExpenseWithLinks>> = db.splitwiseDao().watchAllWithLinks()
    val inboxCount: Flow<Int> = db.splitwiseDao().watchInboxCount()

    fun watchMonthlyReimbursable(month: YearMonth): Flow<Double> =
        db.splitwiseDao().watchMonthlyReimbursable(month.toString())

    fun isConnected(): Boolean = tokens.getSplitwiseToken() != null

    suspend fun handleOAuthCode(code: String) {
        PulseLog.d("SplitwiseRepo", "handleOAuthCode: calling Lambda /splitwise-auth")
        val response = api.exchangeSplitwiseCode(code)
        PulseLog.d("SplitwiseRepo", "handleOAuthCode: token received, storing")
        tokens.putSplitwiseToken(response.access_token)
        PulseLog.d("SplitwiseRepo", "handleOAuthCode: fetching Splitwise current user")
        val user = splitwiseApi.getCurrentUser(response.access_token)
        PulseLog.d("SplitwiseRepo", "handleOAuthCode: user ID=${user.user.id}, storing")
        tokens.putSplitwiseUserId(user.user.id)
    }

    // Incremental refresh using updated_after — fetches everything added/changed since last sync.
    // On first run (no lastSyncedAt) falls back to a single page to avoid downloading all history.
    // Call this from VM init and from Accounts sync.
    suspend fun refreshExpenses() {
        val token = tokens.getSplitwiseToken() ?: run {
            PulseLog.w("SplitwiseRepo", "refreshExpenses: no token, skipping")
            return
        }
        val userId = tokens.getSplitwiseUserId()
        val updatedAfter = tokens.getLastSplitwiseSyncAt()
        val syncStartedAt = Instant.now().toString()
        PulseLog.d("SplitwiseRepo", "refreshExpenses: userId=$userId updatedAfter=$updatedAfter")

        if (updatedAfter != null) {
            // Incremental: loop pages until fewer than limit results (all changes fetched)
            var offset = 0
            var batchSize: Int
            do {
                val response = splitwiseApi.getExpenses(token, offset, updatedAfter = updatedAfter)
                batchSize = response.expenses.size
                PulseLog.d("SplitwiseRepo", "refreshExpenses: page offset=$offset → $batchSize raw")
                processAndStore(response.expenses, pageOffset = 0, userId)
                offset += batchSize
            } while (batchSize == 20)
        } else {
            // First sync: single page, historical navigation will load older pages on demand
            val response = splitwiseApi.getExpenses(token, 0)
            PulseLog.d("SplitwiseRepo", "refreshExpenses: first sync → ${response.expenses.size} raw")
            processAndStore(response.expenses, pageOffset = 0, userId)
        }

        tokens.putLastSplitwiseSyncAt(syncStartedAt)
        runAutoMatch()
    }

    // Offset-based historical pagination. Advances a TokenStore watermark after every fetch
    // so pages with 0 stored expenses (paidShare filter) still advance the cursor — preventing
    // the same dead page from being re-fetched on the next navigation.
    suspend fun loadOlderExpenses(): LoadOlderResult {
        val token = tokens.getSplitwiseToken() ?: return LoadOlderResult(0, null)
        val userId = tokens.getSplitwiseUserId()
        val offset = tokens.getMaxFetchedOffset() + 20
        PulseLog.d("SplitwiseRepo", "loadOlderExpenses: offset=$offset")
        val response = splitwiseApi.getExpenses(token, offset)
        PulseLog.d("SplitwiseRepo", "loadOlderExpenses: ${response.expenses.size} raw")
        processAndStore(response.expenses, pageOffset = offset, userId)
        tokens.putMaxFetchedOffset(offset)  // advance regardless of how many were stored
        val oldestDate = response.expenses.minOfOrNull { it.date.take(10) }
        return LoadOlderResult(response.expenses.size, oldestDate)
    }

    private suspend fun processAndStore(
        rawExpenses: List<SplitwiseExpenseApi>,
        pageOffset: Int,
        userId: Long,
    ) {
        val entities = rawExpenses
            .filter { !it.payment && it.deletedAt == null }
            .filter { exp -> exp.users.any { u -> u.userId == userId && (u.paidShare.toDoubleOrNull() ?: 0.0) > 0.0 } }
            .map { it.toEntity(pageOffset, userId) }
        entities.forEach { e ->
            PulseLog.d("SplitwiseRepo", "store: id=${e.id} '${e.description}' date=${e.date} paid=${e.paidShare} owned=${e.ownedShare}")
        }
        db.splitwiseDao().insertExpenses(entities)
        entities.forEach { db.splitwiseDao().updateShares(it.id, it.paidShare, it.ownedShare) }
        PulseLog.d("SplitwiseRepo", "processAndStore: stored ${entities.size} of ${rawExpenses.size}")
    }

    suspend fun runAutoMatch() {
        val unlinked = db.splitwiseDao().getUnlinkedExpenses()
        val recentTx = db.dao().getRecentCreditTransactions()
        PulseLog.d("SplitwiseRepo", "runAutoMatch: ${unlinked.size} unlinked, ${recentTx.size} CC transactions")
        if (unlinked.isEmpty()) return
        var matchCount = 0
        for (expense in unlinked) {
            val matches = recentTx.filter { tx ->
                abs(tx.amount - expense.totalAmount) < 0.01 && daysBetween(expense.date, tx.date) <= 3
            }
            PulseLog.d("SplitwiseRepo", "runAutoMatch: expense ${expense.id} '${expense.description}' → ${matches.size} candidate(s)")
            if (matches.size == 1) {
                db.withTransaction {
                    db.splitwiseDao().insertLink(SplitwisePlaidLink(expense.id, matches[0].transactionId))
                    db.splitwiseDao().setAutoMatchPending(expense.id)
                }
                matchCount++
            }
        }
        PulseLog.d("SplitwiseRepo", "runAutoMatch: done — $matchCount new auto-matches")
    }

    fun suggestedMatches(expense: SplitwiseExpense, candidates: List<PlaidTransaction>): List<PlaidTransaction> =
        candidates.filter { tx ->
            abs(tx.amount - expense.totalAmount) < 0.01 && daysBetween(expense.date, tx.date) <= 3
        }

    suspend fun linkTransaction(expenseId: Long, plaidId: String) {
        db.splitwiseDao().insertLink(SplitwisePlaidLink(expenseId, plaidId))
        PulseLog.d("SplitwiseRepo", "linkTransaction: expense $expenseId ↔ tx $plaidId")
    }

    suspend fun unlinkTransaction(expenseId: Long, plaidId: String) {
        db.splitwiseDao().deleteLink(expenseId, plaidId)
        PulseLog.d("SplitwiseRepo", "unlinkTransaction: expense $expenseId ↛ tx $plaidId")
    }

    suspend fun dismiss(expenseId: Long) = db.splitwiseDao().dismiss(expenseId)

    suspend fun undismiss(expenseId: Long) = db.splitwiseDao().undismiss(expenseId)

    suspend fun acceptMatch(expenseId: Long) = db.splitwiseDao().clearAutoMatch(expenseId)

    suspend fun rejectMatch(expenseId: Long) {
        db.withTransaction {
            db.splitwiseDao().clearAutoMatch(expenseId)
            db.splitwiseDao().deleteLinksForExpense(expenseId)
        }
    }

    suspend fun disconnect() {
        tokens.removeSplitwiseToken()
        tokens.removeLastSplitwiseSyncAt()
        tokens.removeMaxFetchedOffset()
        db.withTransaction {
            db.splitwiseDao().nukeAllLinks()
            db.splitwiseDao().nukeAllExpenses()
        }
        PulseLog.d("SplitwiseRepo", "disconnect: token + cursors cleared, DB wiped")
    }

    private fun daysBetween(d1: String, d2: String): Long {
        val date1 = java.time.LocalDate.parse(d1)
        val date2 = java.time.LocalDate.parse(d2)
        return abs(java.time.temporal.ChronoUnit.DAYS.between(date1, date2))
    }
}
