package com.pulsestock.app.data.poarvault

import com.pulsestock.app.PulseLog
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

class SplitwiseRepository(
    private val api: PoarVaultApi,
    private val splitwiseApi: SplitwiseApi,
    private val db: PoarVaultDatabase,
    private val tokens: TokenStore,
) {
    val inbox: Flow<List<SplitwiseExpense>> = db.splitwiseDao().watchInbox()
    val inboxCount: Flow<Int> = db.splitwiseDao().watchInboxCount()

    fun isConnected(): Boolean = tokens.getSplitwiseToken() != null

    suspend fun handleOAuthCode(code: String) {
        PulseLog.d("SplitwiseRepo", "handleOAuthCode: calling Lambda /splitwise-auth")
        val response = api.exchangeSplitwiseCode(code)
        PulseLog.d("SplitwiseRepo", "handleOAuthCode: token received from Lambda, storing")
        tokens.putSplitwiseToken(response.access_token)
        PulseLog.d("SplitwiseRepo", "handleOAuthCode: fetching Splitwise current user")
        val user = splitwiseApi.getCurrentUser(response.access_token)
        PulseLog.d("SplitwiseRepo", "handleOAuthCode: user ID=${user.user.id}, storing")
        tokens.putSplitwiseUserId(user.user.id)
    }

    suspend fun loadExpenses(loadOlder: Boolean = false) {
        val token = tokens.getSplitwiseToken() ?: run {
            PulseLog.w("SplitwiseRepo", "loadExpenses: no token stored, skipping")
            return
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
            .map { it.toEntity(offset) }
        db.splitwiseDao().insertExpenses(entities)
        PulseLog.d("SplitwiseRepo", "loadExpenses: inserted ${entities.size} expenses (IGNORE conflict)")
        if (!loadOlder) runAutoMatch()
    }

    suspend fun runAutoMatch() {
        val unlinked = db.splitwiseDao().getUnlinkedExpenses()
        val recentTx = db.dao().getRecentCreditTransactions()
        PulseLog.d("SplitwiseRepo", "runAutoMatch: ${unlinked.size} unlinked expenses, ${recentTx.size} CC transactions")
        if (unlinked.isEmpty()) return
        var matchCount = 0
        for (expense in unlinked) {
            val matches = recentTx.filter { tx ->
                abs(tx.amount - expense.totalAmount) < 0.01 && daysBetween(expense.date, tx.date) <= 3
            }
            PulseLog.d("SplitwiseRepo", "runAutoMatch: expense ${expense.id} '${expense.description}' \$${expense.totalAmount} → ${matches.size} candidate(s)")
            if (matches.size == 1) {
                db.splitwiseDao().setAutoMatch(expense.id, matches[0].transactionId)
                PulseLog.d("SplitwiseRepo", "runAutoMatch: auto-matched expense ${expense.id} → tx ${matches[0].transactionId} '${matches[0].name}'")
                matchCount++
            }
        }
        PulseLog.d("SplitwiseRepo", "runAutoMatch: done — $matchCount new auto-matches")
    }

    fun suggestedMatches(expense: SplitwiseExpense, transactions: List<PlaidTransaction>): List<PlaidTransaction> =
        transactions.filter { tx ->
            abs(tx.amount - expense.totalAmount) < 0.01 && daysBetween(expense.date, tx.date) <= 3
        }

    suspend fun linkTransaction(expenseId: Long, plaidId: String) =
        db.splitwiseDao().linkTransaction(expenseId, plaidId)

    suspend fun dismiss(expenseId: Long) = db.splitwiseDao().dismiss(expenseId)

    suspend fun acceptMatch(expenseId: Long) = db.splitwiseDao().acceptAutoMatch(expenseId)

    suspend fun rejectMatch(expenseId: Long) = db.splitwiseDao().rejectAutoMatch(expenseId)

    fun disconnect() = tokens.removeSplitwiseToken()

    private fun daysBetween(d1: String, d2: String): Long {
        val date1 = java.time.LocalDate.parse(d1)
        val date2 = java.time.LocalDate.parse(d2)
        return abs(java.time.temporal.ChronoUnit.DAYS.between(date1, date2))
    }
}
