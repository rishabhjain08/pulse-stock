package com.pulsestock.app.data.poarvault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitwiseDao {

    // Inbox: not dismissed, and (not yet linked OR auto-match still pending approval)
    @Query("""
        SELECT * FROM splitwise_expenses
        WHERE isDismissed = 0
        AND (linkedPlaidId IS NULL OR isAutoMatched = 1)
        ORDER BY date DESC
    """)
    fun watchInbox(): Flow<List<SplitwiseExpense>>

    @Query("""
        SELECT COUNT(*) FROM splitwise_expenses
        WHERE isDismissed = 0
        AND (linkedPlaidId IS NULL OR isAutoMatched = 1)
    """)
    fun watchInboxCount(): Flow<Int>

    // IGNORE so refreshing from the API never overwrites linkedPlaidId / isDismissed / isAutoMatched.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpenses(expenses: List<SplitwiseExpense>)

    @Query("SELECT * FROM splitwise_expenses WHERE isDismissed = 0 AND linkedPlaidId IS NULL AND isAutoMatched = 0")
    suspend fun getUnlinkedExpenses(): List<SplitwiseExpense>

    @Query("SELECT MAX(pageOffset) FROM splitwise_expenses")
    suspend fun maxPageOffset(): Int?

    @Query("UPDATE splitwise_expenses SET linkedPlaidId = :plaidId, isAutoMatched = 0 WHERE id = :expenseId")
    suspend fun linkTransaction(expenseId: Long, plaidId: String)

    @Query("UPDATE splitwise_expenses SET linkedPlaidId = NULL, isAutoMatched = 0 WHERE id = :expenseId")
    suspend fun unlink(expenseId: Long)

    @Query("UPDATE splitwise_expenses SET isDismissed = 1 WHERE id = :expenseId")
    suspend fun dismiss(expenseId: Long)

    @Query("UPDATE splitwise_expenses SET linkedPlaidId = :plaidId, isAutoMatched = 1 WHERE id = :expenseId")
    suspend fun setAutoMatch(expenseId: Long, plaidId: String)

    // Accept: keep linkedPlaidId, just clear the pending flag → drops out of inbox
    @Query("UPDATE splitwise_expenses SET isAutoMatched = 0 WHERE id = :expenseId")
    suspend fun acceptAutoMatch(expenseId: Long)

    // Reject: clear both — expense returns to inbox without ⚡
    @Query("UPDATE splitwise_expenses SET linkedPlaidId = NULL, isAutoMatched = 0 WHERE id = :expenseId")
    suspend fun rejectAutoMatch(expenseId: Long)
}
