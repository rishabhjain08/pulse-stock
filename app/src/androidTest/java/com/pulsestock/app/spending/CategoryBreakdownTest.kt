package com.pulsestock.app.spending

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.InstitutionEntity
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.PoarVaultDao
import com.pulsestock.app.data.poarvault.PoarVaultDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryBreakdownTest {

    private lateinit var db: PoarVaultDatabase
    private lateinit var dao: PoarVaultDao

    @Before
    fun setup() = runBlocking {
        db = PoarVaultDatabase.getInMemory(ApplicationProvider.getApplicationContext())
        dao = db.dao()
        dao.upsertInstitution(InstitutionEntity("ins_1", "Test Bank"))
        dao.upsertAccounts(listOf(
            account("cc_1", "ins_1", type = "credit"),
            account("dep_1", "ins_1", type = "depository"),
        ))
    }

    @After
    fun teardown() {
        db.close()
    }

    // ── Account type filter ───────────────────────────────────────────────────

    @Test
    fun onlyCreditAccountTransactionsAreIncluded() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 50.0, pfcPrimary = "FOOD_AND_DRINK"),
            tx("t2", accountId = "dep_1", date = "2026-04-10", amount = 200.0, pfcPrimary = "TRANSFER_OUT"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].effectiveCategory).isEqualTo("FOOD_AND_DRINK")
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(50.0)
    }

    // ── Date filter ───────────────────────────────────────────────────────────

    @Test
    fun transactionsBeforeStartDateExcluded() = runTest {
        dao.upsertTransactions(listOf(
            tx("old", accountId = "cc_1", date = "2026-03-31", amount = 99.0, pfcPrimary = "SHOPS"),
            tx("new", accountId = "cc_1", date = "2026-04-01", amount = 55.0, pfcPrimary = "SHOPS"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(55.0)
    }

    @Test
    fun transactionOnStartDateIsIncluded() = runTest {
        dao.upsertTransactions(listOf(
            tx("on", accountId = "cc_1", date = "2026-04-09", amount = 30.0, pfcPrimary = "FOOD_AND_DRINK"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-09").first()
        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(30.0)
    }

    // ── Category grouping ─────────────────────────────────────────────────────

    @Test
    fun transactionsSameCategoryAreGrouped() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 20.0, pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS"),
            tx("t2", accountId = "cc_1", date = "2026-04-11", amount = 35.0, pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS"),
            tx("t3", accountId = "cc_1", date = "2026-04-12", amount = 15.0, pfcDetailed = "FOOD_AND_DRINK_COFFEE"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown).hasSize(2)
        val restaurant = breakdown.first { it.effectiveCategory == "FOOD_AND_DRINK_RESTAURANTS" }
        assertThat(restaurant.totalAmount).isWithin(0.01).of(55.0)
        assertThat(restaurant.txCount).isEqualTo(2)
    }

    @Test
    fun sortedByAmountDescending() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 10.0, pfcPrimary = "ENTERTAINMENT"),
            tx("t2", accountId = "cc_1", date = "2026-04-11", amount = 200.0, pfcPrimary = "SHOPS"),
            tx("t3", accountId = "cc_1", date = "2026-04-12", amount = 50.0, pfcPrimary = "FOOD_AND_DRINK"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("SHOPS")
        assertThat(breakdown[1].effectiveCategory).isEqualTo("FOOD_AND_DRINK")
        assertThat(breakdown[2].effectiveCategory).isEqualTo("ENTERTAINMENT")
    }

    // ── Effective category priority ───────────────────────────────────────────

    @Test
    fun categoryOverrideTakesPriorityOverAllOthers() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0,
                category = "Food", pfcPrimary = "FOOD_AND_DRINK", pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS",
                categoryOverride = "MY_CUSTOM"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("MY_CUSTOM")
    }

    @Test
    fun pfcDetailedTakesPriorityOverPfcPrimary() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0,
                pfcPrimary = "FOOD_AND_DRINK", pfcDetailed = "FOOD_AND_DRINK_COFFEE"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("FOOD_AND_DRINK_COFFEE")
    }

    @Test
    fun pfcPrimaryUsedWhenNoDetailed() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0,
                pfcPrimary = "TRANSPORTATION"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("TRANSPORTATION")
    }

    @Test
    fun legacyCategoryUsedWhenNoPfc() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0, category = "Restaurants"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("Restaurants")
    }

    @Test
    fun otherUsedWhenAllCategoryFieldsNull() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("OTHER")
    }

    // ── getTransactionsForCategory ────────────────────────────────────────────

    @Test
    fun getTransactionsForCategory_returnsMatchingTxns() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 20.0, pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS"),
            tx("t2", accountId = "cc_1", date = "2026-04-11", amount = 15.0, pfcDetailed = "FOOD_AND_DRINK_COFFEE"),
            tx("t3", accountId = "cc_1", date = "2026-04-12", amount = 35.0, pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS"),
        ))
        val txns = dao.getTransactionsForCategory("2026-04-01", "FOOD_AND_DRINK_RESTAURANTS")
        assertThat(txns).hasSize(2)
        assertThat(txns.map { it.transactionId }).containsExactly("t1", "t3")
    }

    @Test
    fun getTransactionsForCategory_excludesOtherAccounts() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 20.0, pfcPrimary = "SHOPS"),
            tx("t2", accountId = "dep_1", date = "2026-04-10", amount = 99.0, pfcPrimary = "SHOPS"),
        ))
        val txns = dao.getTransactionsForCategory("2026-04-01", "SHOPS")
        assertThat(txns).hasSize(1)
        assertThat(txns[0].transactionId).isEqualTo("t1")
    }

    // ── setCategoryOverride + live query update ───────────────────────────────

    @Test
    fun settingOverrideMovesTransactionToNewCategory() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0, pfcPrimary = "SHOPS"),
        ))
        dao.setCategoryOverride("t1", "PERSONAL_CARE")
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].effectiveCategory).isEqualTo("PERSONAL_CARE")
    }

    @Test
    fun clearingOverrideRevealsPfcCategory() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0,
                pfcPrimary = "FOOD_AND_DRINK", categoryOverride = "MY_CUSTOM"),
        ))
        dao.setCategoryOverride("t1", null)
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("FOOD_AND_DRINK")
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    fun noTransactions_returnsEmptyList() = runTest {
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown).isEmpty()
    }

    @Test
    fun onlyDepositoryTransactions_returnsEmptyList() = runTest {
        dao.upsertTransactions(listOf(
            tx("t1", accountId = "dep_1", date = "2026-04-10", amount = 500.0, pfcPrimary = "TRANSFER_OUT"),
        ))
        val breakdown = dao.watchCategoryBreakdown("2026-04-01").first()
        assertThat(breakdown).isEmpty()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun account(id: String, institutionId: String, type: String) = AccountEntity(
        accountId = id,
        institutionId = institutionId,
        name = "$type Account",
        type = type,
        subtype = null,
        currentBalance = null,
        availableBalance = null,
        currencyCode = null,
    )

    private fun tx(
        id: String,
        accountId: String,
        date: String,
        amount: Double,
        category: String? = null,
        pfcPrimary: String? = null,
        pfcDetailed: String? = null,
        categoryOverride: String? = null,
    ) = PlaidTransaction(
        transactionId = id,
        accountId = accountId,
        institutionId = "ins_1",
        name = "Test Merchant",
        amount = amount,
        date = date,
        category = category,
        pfcPrimary = pfcPrimary,
        pfcDetailed = pfcDetailed,
        categoryOverride = categoryOverride,
    )
}
