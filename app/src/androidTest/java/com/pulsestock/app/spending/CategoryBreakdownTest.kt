package com.pulsestock.app.spending

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pulsestock.app.data.poarvault.AccountDateRange
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.CustomCategory
import com.pulsestock.app.data.poarvault.InstitutionEntity
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.PoarVaultApi
import com.pulsestock.app.data.poarvault.PoarVaultDatabase
import com.pulsestock.app.data.poarvault.PoarVaultRepository
import com.pulsestock.app.data.poarvault.TokenStore
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
    private lateinit var repo: PoarVaultRepository

    @Before
    fun setup() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = PoarVaultDatabase.getInMemory(ctx)
        // TokenStore and PoarVaultApi are unused in these tests (no network calls)
        repo = PoarVaultRepository(PoarVaultApi(), db, TokenStore(ctx))
        db.dao().upsertInstitution(InstitutionEntity("ins_1", "Test Bank"))
        db.dao().upsertAccounts(listOf(
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
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 50.0, pfcPrimary = "FOOD_AND_DRINK"),
            tx("t2", accountId = "dep_1", date = "2026-04-10", amount = 200.0, pfcPrimary = "TRANSFER_OUT"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].effectiveCategory).isEqualTo("FOOD_AND_DRINK")
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(50.0)
    }

    // ── Date filter ───────────────────────────────────────────────────────────

    @Test
    fun transactionsBeforeStartDateExcluded() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("old", accountId = "cc_1", date = "2026-03-31", amount = 99.0, pfcPrimary = "SHOPS"),
            tx("new", accountId = "cc_1", date = "2026-04-01", amount = 55.0, pfcPrimary = "SHOPS"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(55.0)
    }

    @Test
    fun transactionsAfterEndDateExcluded() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("in", accountId = "cc_1", date = "2026-04-30", amount = 40.0, pfcPrimary = "FOOD_AND_DRINK"),
            tx("out", accountId = "cc_1", date = "2026-05-01", amount = 60.0, pfcPrimary = "FOOD_AND_DRINK"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(40.0)
    }

    @Test
    fun transactionOnStartAndEndDatesIncluded() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("start", accountId = "cc_1", date = "2026-04-09", amount = 30.0, pfcPrimary = "FOOD_AND_DRINK"),
            tx("end",   accountId = "cc_1", date = "2026-05-09", amount = 20.0, pfcPrimary = "FOOD_AND_DRINK"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-09", "2026-05-09")).first()
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(50.0)
    }

    // ── Category grouping ─────────────────────────────────────────────────────

    @Test
    fun transactionsSameCategoryAreGrouped() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 20.0, pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS"),
            tx("t2", accountId = "cc_1", date = "2026-04-11", amount = 35.0, pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS"),
            tx("t3", accountId = "cc_1", date = "2026-04-12", amount = 15.0, pfcDetailed = "FOOD_AND_DRINK_COFFEE"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown).hasSize(2)
        val restaurant = breakdown.first { it.effectiveCategory == "FOOD_AND_DRINK_RESTAURANTS" }
        assertThat(restaurant.totalAmount).isWithin(0.01).of(55.0)
        assertThat(restaurant.txCount).isEqualTo(2)
    }

    @Test
    fun sortedByAmountDescending() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 10.0, pfcPrimary = "ENTERTAINMENT"),
            tx("t2", accountId = "cc_1", date = "2026-04-11", amount = 200.0, pfcPrimary = "SHOPS"),
            tx("t3", accountId = "cc_1", date = "2026-04-12", amount = 50.0, pfcPrimary = "FOOD_AND_DRINK"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("SHOPS")
        assertThat(breakdown[1].effectiveCategory).isEqualTo("FOOD_AND_DRINK")
        assertThat(breakdown[2].effectiveCategory).isEqualTo("ENTERTAINMENT")
    }

    // ── Per-card date windows (Statement mode union) ──────────────────────────

    @Test
    fun perCardDateWindows_eachCardUsesItsOwnRange() = runTest {
        db.dao().upsertAccounts(listOf(account("cc_2", "ins_1", type = "credit")))
        db.dao().upsertTransactions(listOf(
            // cc_1 window: Apr 1–Apr 30
            tx("a1", accountId = "cc_1", date = "2026-04-15", amount = 100.0, pfcPrimary = "FOOD_AND_DRINK"),
            tx("a2", accountId = "cc_1", date = "2026-05-05", amount = 999.0, pfcPrimary = "FOOD_AND_DRINK"), // outside cc_1 window
            // cc_2 window: May 1–May 31
            tx("b1", accountId = "cc_2", date = "2026-05-05", amount = 50.0, pfcPrimary = "FOOD_AND_DRINK"),
            tx("b2", accountId = "cc_2", date = "2026-04-15", amount = 999.0, pfcPrimary = "FOOD_AND_DRINK"), // outside cc_2 window
        ))
        val multiCardRanges = listOf(
            AccountDateRange("cc_1", "2026-04-01", "2026-04-30"),
            AccountDateRange("cc_2", "2026-05-01", "2026-05-31"),
        )
        val breakdown = repo.watchCategoryBreakdown(multiCardRanges).first()
        assertThat(breakdown).hasSize(1)
        // Only a1 ($100) and b1 ($50) are in their respective windows
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(150.0)
        assertThat(breakdown[0].txCount).isEqualTo(2)
    }

    // ── Effective category priority ───────────────────────────────────────────

    @Test
    fun categoryOverrideTakesPriorityOverAllOthers() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0,
                category = "Food", pfcPrimary = "FOOD_AND_DRINK", pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS",
                overrideCategoryId = "MY_CUSTOM"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("MY_CUSTOM")
    }

    @Test
    fun pfcDetailedTakesPriorityOverPfcPrimary() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0,
                pfcPrimary = "FOOD_AND_DRINK", pfcDetailed = "FOOD_AND_DRINK_COFFEE"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("FOOD_AND_DRINK_COFFEE")
    }

    @Test
    fun pfcPrimaryUsedWhenNoDetailed() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0, pfcPrimary = "TRANSPORTATION"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("TRANSPORTATION")
    }

    @Test
    fun legacyCategoryUsedWhenNoPfc() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0, category = "Restaurants"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("Restaurants")
    }

    @Test
    fun otherUsedWhenAllCategoryFieldsNull() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("OTHER")
    }

    // ── getTransactionsForCategory ────────────────────────────────────────────

    @Test
    fun getTransactionsForCategory_returnsMatchingTxns() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 20.0, pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS"),
            tx("t2", accountId = "cc_1", date = "2026-04-11", amount = 15.0, pfcDetailed = "FOOD_AND_DRINK_COFFEE"),
            tx("t3", accountId = "cc_1", date = "2026-04-12", amount = 35.0, pfcDetailed = "FOOD_AND_DRINK_RESTAURANTS"),
        ))
        val txns = repo.getTransactionsForCategory(
            ranges("cc_1", "2026-04-01", "2026-04-30"), listOf("FOOD_AND_DRINK_RESTAURANTS")
        )
        assertThat(txns).hasSize(2)
        assertThat(txns.map { it.transactionId }).containsExactly("t1", "t3")
    }

    @Test
    fun getTransactionsForCategory_excludesDepositoryAccounts() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1",  date = "2026-04-10", amount = 20.0, pfcPrimary = "SHOPS"),
            tx("t2", accountId = "dep_1", date = "2026-04-10", amount = 99.0, pfcPrimary = "SHOPS"),
        ))
        val txns = repo.getTransactionsForCategory(
            ranges("cc_1", "2026-04-01", "2026-04-30"), listOf("SHOPS")
        )
        assertThat(txns).hasSize(1)
        assertThat(txns[0].transactionId).isEqualTo("t1")
    }

    // ── setCategoryOverride + live query update ───────────────────────────────

    @Test
    fun settingOverrideMovesTransactionToNewCategory() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0, pfcPrimary = "SHOPS"),
        ))
        repo.executeCategoryOverrides(listOf("t1"), "PERSONAL_CARE", emptyList())
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].effectiveCategory).isEqualTo("PERSONAL_CARE")
    }

    @Test
    fun clearingOverrideRevealsPfcCategory() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 40.0,
                pfcPrimary = "FOOD_AND_DRINK", overrideCategoryId = "MY_CUSTOM"),
        ))
        repo.executeCategoryOverrides(listOf("t1"), null, emptyList())
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown[0].effectiveCategory).isEqualTo("FOOD_AND_DRINK")
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    fun noTransactions_returnsEmptyList() = runTest {
        val breakdown = repo.watchCategoryBreakdown(ranges("cc_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown).isEmpty()
    }

    @Test
    fun emptyRangeList_returnsEmptyList() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "cc_1", date = "2026-04-10", amount = 50.0, pfcPrimary = "SHOPS"),
        ))
        val breakdown = repo.watchCategoryBreakdown(emptyList()).first()
        assertThat(breakdown).isEmpty()
    }

    @Test
    fun onlyDepositoryTransactions_returnsEmptyList() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", accountId = "dep_1", date = "2026-04-10", amount = 500.0, pfcPrimary = "TRANSFER_OUT"),
        ))
        val breakdown = repo.watchCategoryBreakdown(ranges("dep_1", "2026-04-01", "2026-04-30")).first()
        assertThat(breakdown).isEmpty()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ranges(accountId: String, start: String, end: String) =
        listOf(AccountDateRange(accountId, start, end))

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
        overrideCategoryId: String? = null,
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
        overrideCategoryId = overrideCategoryId,
    )
}
