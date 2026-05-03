package com.pulsestock.app.data.poarvault

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "institutions")
data class InstitutionEntity(
    @PrimaryKey val institutionId: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(
        entity = InstitutionEntity::class,
        parentColumns = ["institutionId"],
        childColumns = ["institutionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("institutionId")],
)
data class AccountEntity(
    @PrimaryKey val accountId: String,
    val institutionId: String,
    val name: String,
    val type: String,
    val subtype: String?,
    val currentBalance: Double?,
    val availableBalance: Double?,
    val currencyCode: String?,
    val lastRefreshed: Long = 0L,
    // Populated by /liabilities endpoint — credit cards only, null until Liabilities product is linked.
    val statementBalance: Double? = null,
    val minimumPayment: Double? = null,
    val nextDueDate: String? = null,
)

data class InstitutionWithAccounts(
    @Embedded val institution: InstitutionEntity,
    @Relation(parentColumn = "institutionId", entityColumn = "institutionId")
    val accounts: List<AccountEntity>,
)

@Serializable data class LinkTokenResponse(val link_token: String)

@Serializable data class ExchangeResponse(
    val access_token: String,
    val item_id: String,
)

@Serializable data class PlaidApiBalance(
    val available: Double? = null,
    val current: Double? = null,
    @SerialName("iso_currency_code") val isoCurrencyCode: String? = null,
)

@Serializable data class PlaidApiAccount(
    @SerialName("account_id") val accountId: String,
    val name: String,
    val type: String,
    val subtype: String? = null,
    val balances: PlaidApiBalance,
)

@Serializable data class BalancesResponse(val accounts: List<PlaidApiAccount>)

// ── Liabilities ───────────────────────────────────────────────────────────────

@Serializable data class PlaidCreditLiability(
    @SerialName("account_id") val accountId: String,
    @SerialName("last_statement_balance") val lastStatementBalance: Double? = null,
    @SerialName("minimum_payment_amount") val minimumPaymentAmount: Double? = null,
    @SerialName("next_payment_due_date") val nextPaymentDueDate: String? = null,
)

@Serializable data class PlaidLiabilitiesData(
    val credit: List<PlaidCreditLiability>? = null,
)

@Serializable data class PlaidLiabilitiesResponse(
    val liabilities: PlaidLiabilitiesData,
    val accounts: List<PlaidApiAccount>,
)

// ── Transactions ─────────────────────────────────────────────────────────────

@Serializable data class PlaidApiTransaction(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("account_id") val accountId: String,
    val name: String,
    val amount: Double,
    val date: String,
    val category: List<String>? = null,
    val pending: Boolean = false,
)

@Serializable data class PlaidTransactionsResponse(
    val accounts: List<PlaidApiAccount>,
    val transactions: List<PlaidApiTransaction>,
    @SerialName("total_transactions") val totalTransactions: Int = 0,
)

// ── Splitwise OAuth ───────────────────────────────────────────────────────────

@Serializable data class SplitwiseAuthResponse(val access_token: String)
