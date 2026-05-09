package com.pulsestock.app.data.poarvault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PoarVaultDao {

    @Transaction
    @Query("SELECT * FROM institutions ORDER BY addedAt DESC")
    fun watchInstitutions(): Flow<List<InstitutionWithAccounts>>

    @Query("SELECT * FROM accounts WHERE type = 'credit' ORDER BY name ASC")
    fun watchCreditCardAccounts(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstitution(institution: InstitutionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<AccountEntity>)

    @Query("DELETE FROM institutions WHERE institutionId = :id")
    suspend fun deleteInstitution(id: String)

    @Query("SELECT institutionId FROM institutions")
    suspend fun allInstitutionIds(): List<String>

    @Query("""
        UPDATE accounts
        SET statementBalance = :balance, minimumPayment = :minPay, nextDueDate = :dueDate
        WHERE accountId = :accountId
    """)
    suspend fun updateLiability(accountId: String, balance: Double?, minPay: Double?, dueDate: String?)

    // ── Transactions ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<PlaidTransaction>)

    @Query("SELECT * FROM plaid_transactions WHERE transactionId IN (:ids)")
    suspend fun getTransactionsByIds(ids: List<String>): List<PlaidTransaction>

    @Query("""
        SELECT pt.* FROM plaid_transactions pt
        INNER JOIN accounts a ON pt.accountId = a.accountId
        WHERE a.type = 'credit'
        ORDER BY pt.date DESC
        LIMIT 300
    """)
    suspend fun getRecentCreditTransactions(): List<PlaidTransaction>

    // ── Category breakdown ────────────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(pt.categoryOverride, pt.pfcDetailed, pt.pfcPrimary, pt.category, 'OTHER') AS effectiveCategory,
               SUM(pt.amount) AS totalAmount,
               COUNT(*) AS txCount
        FROM plaid_transactions pt
        INNER JOIN accounts a ON pt.accountId = a.accountId
        WHERE a.type = 'credit'
          AND substr(pt.date, 1, 7) = :monthPrefix
        GROUP BY COALESCE(pt.categoryOverride, pt.pfcDetailed, pt.pfcPrimary, pt.category, 'OTHER')
        ORDER BY SUM(pt.amount) DESC
    """)
    fun watchMonthlyCategoryBreakdown(monthPrefix: String): Flow<List<CategorySpend>>

    @Query("""
        SELECT pt.* FROM plaid_transactions pt
        INNER JOIN accounts a ON pt.accountId = a.accountId
        WHERE a.type = 'credit'
          AND substr(pt.date, 1, 7) = :monthPrefix
          AND COALESCE(pt.categoryOverride, pt.pfcDetailed, pt.pfcPrimary, pt.category, 'OTHER') = :category
        ORDER BY pt.date DESC
    """)
    suspend fun getTransactionsForCategory(monthPrefix: String, category: String): List<PlaidTransaction>

    @Query("UPDATE plaid_transactions SET categoryOverride = :override WHERE transactionId = :id")
    suspend fun setCategoryOverride(id: String, override: String?)

    @Query("SELECT transactionId, categoryOverride FROM plaid_transactions WHERE transactionId IN (:ids)")
    suspend fun getOverridesForIds(ids: List<String>): List<TransactionOverride>

    @Query("SELECT DISTINCT categoryOverride FROM plaid_transactions WHERE categoryOverride IS NOT NULL ORDER BY categoryOverride ASC")
    fun watchCustomCategories(): Flow<List<String>>
}
