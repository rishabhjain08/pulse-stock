package com.pulsestock.app.data.poarvault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitwiseDao {

    // All expenses including dismissed, with their linked CC transactions.
    // Room re-emits whenever either splitwise_expenses or splitwise_plaid_links changes.
    @Transaction
    @Query("SELECT * FROM splitwise_expenses ORDER BY date DESC")
    fun watchAllWithLinks(): Flow<List<ExpenseWithLinks>>

    // Inbox count: expenses that still need action (no links yet, or pending auto-match approval)
    @Query("""
        SELECT COUNT(*) FROM splitwise_expenses e
        WHERE e.isDismissed = 0
        AND (e.isAutoMatched = 1 OR NOT EXISTS (
            SELECT 1 FROM splitwise_plaid_links l WHERE l.expenseId = e.id
        ))
    """)
    fun watchInboxCount(): Flow<Int>

    // IGNORE: never overwrite isDismissed / isAutoMatched on a re-fetch
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpenses(expenses: List<SplitwiseExpense>)

    // Always update paidShare/ownedShare even for already-cached rows
    @Query("UPDATE splitwise_expenses SET paidShare = :paidShare, ownedShare = :ownedShare WHERE id = :id")
    suspend fun updateShares(id: Long, paidShare: Double, ownedShare: Double)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: SplitwisePlaidLink)

    @Query("DELETE FROM splitwise_plaid_links WHERE expenseId = :expenseId AND plaidTransactionId = :plaidId")
    suspend fun deleteLink(expenseId: Long, plaidId: String)

    @Query("DELETE FROM splitwise_plaid_links WHERE expenseId = :expenseId")
    suspend fun deleteLinksForExpense(expenseId: Long)

    // Expenses with no links and not pending auto-match — candidates for auto-matching
    @Query("""
        SELECT * FROM splitwise_expenses
        WHERE isDismissed = 0 AND isAutoMatched = 0
        AND NOT EXISTS (SELECT 1 FROM splitwise_plaid_links l WHERE l.expenseId = id)
    """)
    suspend fun getUnlinkedExpenses(): List<SplitwiseExpense>

    @Query("SELECT MAX(pageOffset) FROM splitwise_expenses")
    suspend fun maxPageOffset(): Int?

    @Query("UPDATE splitwise_expenses SET isDismissed = 1 WHERE id = :expenseId")
    suspend fun dismiss(expenseId: Long)

    @Query("UPDATE splitwise_expenses SET isDismissed = 0 WHERE id = :expenseId")
    suspend fun undismiss(expenseId: Long)

    @Query("UPDATE splitwise_expenses SET isAutoMatched = 1 WHERE id = :expenseId")
    suspend fun setAutoMatchPending(expenseId: Long)

    @Query("UPDATE splitwise_expenses SET isAutoMatched = 0 WHERE id = :expenseId")
    suspend fun clearAutoMatch(expenseId: Long)

    @Query("""
        SELECT COALESCE(SUM(paidShare - ownedShare), 0.0)
        FROM splitwise_expenses
        WHERE substr(date, 1, 7) = :monthPrefix AND isDismissed = 0
    """)
    fun watchMonthlyReimbursable(monthPrefix: String): Flow<Double>

    @Query("DELETE FROM splitwise_expenses")
    suspend fun nukeAllExpenses()

    @Query("DELETE FROM splitwise_plaid_links")
    suspend fun nukeAllLinks()
}
