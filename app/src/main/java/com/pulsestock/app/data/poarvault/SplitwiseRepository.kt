package com.pulsestock.app.data.poarvault

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
        val response = api.exchangeSplitwiseCode(code)
        tokens.putSplitwiseToken(response.access_token)
        val user = splitwiseApi.getCurrentUser(response.access_token)
        tokens.putSplitwiseUserId(user.user.id)
    }

    suspend fun loadExpenses(loadOlder: Boolean = false) {
        val token = tokens.getSplitwiseToken() ?: return
        val userId = tokens.getSplitwiseUserId()
        val offset = if (loadOlder) {
            (db.splitwiseDao().maxPageOffset() ?: -20) + 20
        } else {
            0
        }
        val response = splitwiseApi.getExpenses(token, offset)
        val entities = response.expenses
            .filter { !it.payment && it.deletedAt == null }
            .filter { exp ->
                exp.users.any { u ->
                    u.userId == userId && (u.paidShare.toDoubleOrNull() ?: 0.0) > 0.0
                }
            }
            .map { it.toEntity(offset) }
        db.splitwiseDao().insertExpenses(entities)
        if (!loadOlder) runAutoMatch()
    }

    suspend fun runAutoMatch() {
        val unlinked = db.splitwiseDao().getUnlinkedExpenses()
        if (unlinked.isEmpty()) return
        val recentTx = db.dao().getRecentCreditTransactions()
        for (expense in unlinked) {
            val matches = recentTx.filter { tx ->
                abs(tx.amount - expense.totalAmount) < 0.01 && daysBetween(expense.date, tx.date) <= 3
            }
            if (matches.size == 1) db.splitwiseDao().setAutoMatch(expense.id, matches[0].transactionId)
        }
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
