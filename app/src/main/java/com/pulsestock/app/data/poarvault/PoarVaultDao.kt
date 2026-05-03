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
}
