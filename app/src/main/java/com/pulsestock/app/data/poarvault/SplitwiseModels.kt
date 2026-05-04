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
    val isReconciled: Boolean get() = linkedTransactions.isNotEmpty() && !expense.isAutoMatched
    val isPendingAutoMatch: Boolean get() = expense.isAutoMatched && linkedTransactions.isNotEmpty()
    val isUnlinked: Boolean get() = linkedTransactions.isEmpty() && !expense.isAutoMatched
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
    val cachedAt: Long = System.currentTimeMillis(),
)

// ── Splitwise API response models (direct app → Splitwise calls) ──────────────

@Serializable data class SplitwiseUserInner(val id: Long)

@Serializable data class SplitwiseCurrentUserResponse(val user: SplitwiseUserInner)

@Serializable data class SplitwiseUserShareApi(
    @SerialName("user_id") val userId: Long,
    @SerialName("paid_share") val paidShare: String,
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
    fun toEntity(pageOffset: Int) = SplitwiseExpense(
        id = id,
        description = description,
        date = date.take(10),   // "2024-01-15T00:00:00Z" → "2024-01-15"
        totalAmount = cost.toDoubleOrNull() ?: 0.0,
        currencyCode = currencyCode ?: "USD",
        pageOffset = pageOffset,
    )
}

@Serializable data class SplitwiseExpensesResponse(
    val expenses: List<SplitwiseExpenseApi>,
)
