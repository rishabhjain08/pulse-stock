package com.pulsestock.app.data.poarvault

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Room entities ─────────────────────────────────────────────────────────────

@Entity(tableName = "splitwise_expenses")
data class SplitwiseExpense(
    @PrimaryKey val id: Long,
    val description: String,
    val date: String,           // "2024-01-15"
    val totalAmount: Double,
    val currencyCode: String,
    val pageOffset: Int = 0,
    val cachedAt: Long = System.currentTimeMillis(),
    // Inbox state — never overwritten by a cache refresh (IGNORE on conflict)
    val isDismissed: Boolean = false,
    val isAutoMatched: Boolean = false,
    val paidShare: Double = 0.0,  // how much the current user paid for this expense
    val ownedShare: Double = 0.0, // how much the current user owes for this expense
)

// Many-to-many junction: one Splitwise expense ↔ many Plaid transactions
@Entity(
    tableName = "splitwise_plaid_links",
    primaryKeys = ["expenseId", "plaidTransactionId"],
)
data class SplitwisePlaidLink(
    val expenseId: Long,
    val plaidTransactionId: String,
)

data class ExpenseWithLinks(
    @Embedded val expense: SplitwiseExpense,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId",
        associateBy = Junction(
            value = SplitwisePlaidLink::class,
            parentColumn = "expenseId",
            entityColumn = "plaidTransactionId",
        )
    )
    val linkedTransactions: List<PlaidTransaction>,
) {
    val isDismissed: Boolean get() = expense.isDismissed
    val isReconciled: Boolean get() = !isDismissed && linkedTransactions.isNotEmpty() && !expense.isAutoMatched
    val isPendingAutoMatch: Boolean get() = !isDismissed && expense.isAutoMatched && linkedTransactions.isNotEmpty()
    val isUnlinked: Boolean get() = !isDismissed && linkedTransactions.isEmpty() && !expense.isAutoMatched
}

@Entity(tableName = "plaid_transactions")
data class PlaidTransaction(
    @PrimaryKey val transactionId: String,
    val accountId: String,
    val institutionId: String,
    val name: String,
    val amount: Double,         // positive = debit (Plaid convention)
    val date: String,           // "2024-01-15"
    val category: String? = null,
    val pfcPrimary: String? = null,    // e.g. "FOOD_AND_DRINK"
    val pfcDetailed: String? = null,   // e.g. "FOOD_AND_DRINK_RESTAURANTS"
    val categoryOverride: String? = null,
    // Plaid's already-normalized merchant name ("Starbucks", "Amazon") — used for rule matching.
    // Null when Plaid doesn't provide one; rules are skipped for those transactions.
    val merchantName: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)

/** A persisted rule: every transaction from [merchantName] gets [category] as its override. */
@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey val merchantName: String,
    val category: String,
)

@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey val name: String,
)

/**
 * Point-in-time balance snapshot written on every sync.
 * Multiple snapshots per account per day are fine — the history sheet groups by
 * strftime('%Y-%m', ...) and takes the latest per account per month.
 */
@Entity(
    tableName = "balance_snapshots",
    indices = [androidx.room.Index("accountId")],
)
data class BalanceSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val capturedAt: Long,           // epoch millis — System.currentTimeMillis() at sync time
    val statementBalance: Double?,  // null for business CCs or if liabilities not loaded yet
    val currentBalance: Double?,    // from /balances
)

// Effective category priority: categoryOverride > category > pfcPrimary
val PlaidTransaction.effectiveCategory: String
    get() = categoryOverride ?: category ?: pfcPrimary ?: "OTHER"

data class CategorySpend(
    val effectiveCategory: String,
    val totalAmount: Double,
    val txCount: Int,
)

data class TransactionOverride(
    val transactionId: String,
    val categoryOverride: String?,
)

// ── Splitwise API response models (direct app → Splitwise calls) ──────────────

@Serializable data class SplitwiseUserInner(val id: Long)

@Serializable data class SplitwiseCurrentUserResponse(val user: SplitwiseUserInner)

@Serializable data class SplitwiseUserShareApi(
    @SerialName("user_id") val userId: Long,
    @SerialName("paid_share") val paidShare: String,
    @SerialName("owed_share") val ownedShare: String = "0",
)

@Serializable data class SplitwiseExpenseApi(
    val id: Long,
    val description: String,
    val date: String,
    val cost: String,
    @SerialName("currency_code") val currencyCode: String? = null,
    val payment: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val users: List<SplitwiseUserShareApi> = emptyList(),
) {
    fun toEntity(pageOffset: Int, userId: Long = -1L): SplitwiseExpense {
        val myShare = users.find { it.userId == userId }
        return SplitwiseExpense(
            id = id,
            description = description,
            date = date.take(10),   // "2024-01-15T00:00:00Z" → "2024-01-15"
            totalAmount = cost.toDoubleOrNull() ?: 0.0,
            currencyCode = currencyCode ?: "USD",
            pageOffset = pageOffset,
            paidShare = myShare?.paidShare?.toDoubleOrNull() ?: 0.0,
            ownedShare = myShare?.ownedShare?.toDoubleOrNull() ?: 0.0,
        )
    }
}

@Serializable data class SplitwiseExpensesResponse(
    val expenses: List<SplitwiseExpenseApi>,
)
