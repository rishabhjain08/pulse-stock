package com.pulsestock.app.data.poarvault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
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

    @Query("UPDATE accounts SET currentBalance = :balance WHERE accountId = :accountId")
    suspend fun updateCurrentBalance(accountId: String, balance: Double)

    @Query("SELECT * FROM accounts WHERE institutionId = :institutionId")
    suspend fun getAccountsForInstitution(institutionId: String): List<AccountEntity>

    @Query("SELECT accountId FROM accounts WHERE institutionId = :institutionId")
    suspend fun accountIdsForInstitution(institutionId: String): List<String>

    @Query("DELETE FROM institutions WHERE institutionId = :id")
    suspend fun deleteInstitution(id: String)

    @Query("DELETE FROM plaid_transactions WHERE accountId IN (:accountIds)")
    suspend fun deleteTransactionsByAccounts(accountIds: List<String>)

    @Query("DELETE FROM balance_snapshots WHERE accountId IN (:accountIds)")
    suspend fun deleteSnapshotsByAccounts(accountIds: List<String>)

    @Query("SELECT institutionId FROM institutions")
    suspend fun allInstitutionIds(): List<String>

    @Query("""
        UPDATE accounts
        SET statementBalance = :balance, minimumPayment = :minPay, nextDueDate = :dueDate,
            lastStatementDate = :statementDate
        WHERE accountId = :accountId
    """)
    suspend fun updateLiability(
        accountId: String,
        balance: Double?,
        minPay: Double?,
        dueDate: String?,
        statementDate: String?,
    )

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

    // ── Category breakdown — dynamic per-account date windows ────────────────
    // observedEntities tells Room which tables to watch for automatic re-emission

    @RawQuery(observedEntities = [PlaidTransaction::class, AccountEntity::class])
    fun watchCategoryBreakdownRaw(query: SupportSQLiteQuery): Flow<List<CategorySpend>>

    @RawQuery(observedEntities = [PlaidTransaction::class, AccountEntity::class])
    suspend fun getTransactionsForCategoryRaw(query: SupportSQLiteQuery): List<PlaidTransaction>

    @RawQuery(observedEntities = [PlaidTransaction::class, AccountEntity::class])
    fun watchTransactionsForCategoryRaw(query: SupportSQLiteQuery): Flow<List<PlaidTransaction>>

    // ── Category overrides ────────────────────────────────────────────────────

    @Query("UPDATE plaid_transactions SET overrideCategoryId = :categoryId WHERE transactionId = :id")
    suspend fun setCategoryOverride(id: String, categoryId: String?)

    @Query("UPDATE plaid_transactions SET overrideCategoryId = :categoryId WHERE merchantName = :merchantName")
    suspend fun applyOverrideToMerchant(merchantName: String, categoryId: String)

    /** Count transactions for this merchant that are NOT the transaction just overridden. */
    @Query("SELECT COUNT(*) FROM plaid_transactions WHERE merchantName = :merchantName AND transactionId != :excludeId")
    suspend fun countOtherTransactionsForMerchant(merchantName: String, excludeId: String): Int

    @Query("SELECT COUNT(*) FROM plaid_transactions WHERE merchantName = :merchantName AND transactionId NOT IN (:excludeIds)")
    suspend fun countTransactionsForMerchantExcludingIds(merchantName: String, excludeIds: List<String>): Int

    @Query("SELECT transactionId, overrideCategoryId FROM plaid_transactions WHERE transactionId IN (:ids)")
    suspend fun getOverridesForIds(ids: List<String>): List<TransactionOverride>

    @Query("SELECT * FROM custom_categories ORDER BY isCore DESC, name ASC")
    fun watchCustomCategories(): Flow<List<CustomCategory>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertCustomCategory(category: CustomCategory)

    @Query("DELETE FROM custom_categories WHERE id = :id AND isCore = 0")
    suspend fun deleteCustomCategory(id: String)

    @Query("SELECT COUNT(*) FROM plaid_transactions WHERE overrideCategoryId = :categoryId")
    suspend fun countTransactionsWithOverride(categoryId: String): Int

    @Query("UPDATE plaid_transactions SET overrideCategoryId = NULL WHERE overrideCategoryId = :categoryId")
    suspend fun clearOverrideByCategory(categoryId: String)

    // ── Balance snapshots ─────────────────────────────────────────────────────

    @Insert
    suspend fun insertBalanceSnapshot(snapshot: BalanceSnapshot)

    /**
     * Returns all snapshots for the given accounts captured at or after [since] (epoch millis).
     * The history sheet groups these by calendar month and takes the latest per account per month.
     */
    @Query("""
        SELECT * FROM balance_snapshots
        WHERE accountId IN (:accountIds) AND capturedAt >= :since
        ORDER BY capturedAt DESC
    """)
    fun getSnapshotsForAccounts(accountIds: List<String>, since: Long): Flow<List<BalanceSnapshot>>

    // ── Category rules ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryRule(rule: CategoryRule)

    @Query("DELETE FROM category_rules WHERE merchantName = :merchantName")
    suspend fun deleteCategoryRule(merchantName: String)

    @Query("UPDATE plaid_transactions SET overrideCategoryId = NULL WHERE merchantName = :merchantName")
    suspend fun clearOverridesForMerchant(merchantName: String)

    @Query("SELECT COUNT(*) FROM plaid_transactions WHERE merchantName = :merchantName AND overrideCategoryId IS NOT NULL AND transactionId != :excludeId")
    suspend fun countOverridesForMerchant(merchantName: String, excludeId: String): Int

    @Query("SELECT COUNT(*) FROM plaid_transactions WHERE merchantName = :merchantName")
    suspend fun countTransactionsForMerchant(merchantName: String): Int

    @Query("SELECT * FROM category_rules WHERE merchantName = :merchantName LIMIT 1")
    suspend fun getRuleForMerchant(merchantName: String): CategoryRule?

    @Query("SELECT * FROM category_rules")
    suspend fun getAllCategoryRules(): List<CategoryRule>
}
