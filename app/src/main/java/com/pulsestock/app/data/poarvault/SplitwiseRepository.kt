package com.pulsestock.app.data.poarvault

import androidx.room.withTransaction
import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import kotlin.math.abs

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

    // Returns count of raw expenses fetched from API (0 = end of history)
    suspend fun loadExpenses(loadOlder: Boolean = false): Int {
        val token = tokens.getSplitwiseToken() ?: run {
            PulseLog.w("SplitwiseRepo", "loadExpenses: no token, skipping")
            return 0
        }
        val userId = tokens.getSplitwiseUserId()
        val offset = if (loadOlder) {
            (db.splitwiseDao().maxPageOffset() ?: -20) + 20
        } else {
            0
        }
        PulseLog.d("SplitwiseRepo", "loadExpenses: userId=$userId offset=$offset loadOlder=$loadOlder")
        val response = splitwiseApi.getExpenses(token, offset)
        PulseLog.d("SplitwiseRepo", "loadExpenses: fetched ${response.expenses.size} raw expenses")
        val entities = response.expenses
            .filter { !it.payment && it.deletedAt == null }
            .also { PulseLog.d("SplitwiseRepo", "loadExpenses: after payment/deleted filter → ${it.size}") }
            .filter { exp ->
                exp.users.any { u ->
                    u.userId == userId && (u.paidShare.toDoubleOrNull() ?: 0.0) > 0.0
                }
            }
            .also { PulseLog.d("SplitwiseRepo", "loadExpenses: after paidShare filter → ${it.size}") }
            .map { it.toEntity(offset, userId) }
        entities.forEach { e ->
            PulseLog.d("SplitwiseRepo", "loadExpenses: entity id=${e.id} '${e.description}' date=${e.date} paid=${e.paidShare} owned=${e.ownedShare}")
        }
        db.splitwiseDao().insertExpenses(entities)
        // UPDATE shares separately — INSERT IGNORE skips existing rows, leaving paidShare/ownedShare stale
        entities.forEach { db.splitwiseDao().updateShares(it.id, it.paidShare, it.ownedShare) }
        PulseLog.d("SplitwiseRepo", "loadExpenses: inserted/updated ${entities.size} expenses")
        if (!loadOlder) runAutoMatch()
        return response.expenses.size
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
                PulseLog.d("SplitwiseRepo", "runAutoMatch: matched expense ${expense.id} → tx ${matches[0].transactionId}")
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
        db.withTransaction {
            db.splitwiseDao().nukeAllLinks()
            db.splitwiseDao().nukeAllExpenses()
        }
        PulseLog.d("SplitwiseRepo", "disconnect: token cleared and DB wiped")
    }

    private fun daysBetween(d1: String, d2: String): Long {
        val date1 = java.time.LocalDate.parse(d1)
        val date2 = java.time.LocalDate.parse(d2)
        return abs(java.time.temporal.ChronoUnit.DAYS.between(date1, date2))
    }
}
