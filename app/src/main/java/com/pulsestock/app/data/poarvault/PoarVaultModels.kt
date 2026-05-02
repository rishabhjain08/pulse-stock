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
