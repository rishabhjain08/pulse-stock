package com.pulsestock.app.spending

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pulsestock.app.data.poarvault.AccountDateRange
import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.CategoryRule
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
class CategoryRuleTest {

    private lateinit var db: PoarVaultDatabase
    private lateinit var repo: PoarVaultRepository

    @Before
    fun setup() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = PoarVaultDatabase.getInMemory(ctx)
        repo = PoarVaultRepository(PoarVaultApi(), db, TokenStore(ctx))
        db.dao().upsertInstitution(InstitutionEntity("ins_1", "Test Bank"))
        db.dao().upsertAccounts(listOf(account("cc_1", "ins_1")))
    }

    @After
    fun teardown() = db.close()

    // ── setCategoryOverride — proposal generation ─────────────────────────────

    @Test
    fun noProposalWhenMerchantNameIsNull() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", merchantName = null, amount = 10.0),
        ))
        val proposal = repo.setCategoryOverride("t1", "FOOD_AND_DRINK")
        assertThat(proposal).isNull()
    }

    @Test
    fun noProposalWhenOnlyOneTransactionForMerchant() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", merchantName = "Starbucks", amount = 5.0),
        ))
        val proposal = repo.setCategoryOverride("t1", "FOOD_AND_DRINK")
        assertThat(proposal).isNull()
    }

    @Test
    fun proposalReturnedWhenOtherTransactionsExistForMerchant() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", merchantName = "Starbucks", amount = 5.0),
            tx("t2", merchantName = "Starbucks", amount = 6.0),
            tx("t3", merchantName = "Starbucks", amount = 7.0),
        ))
        val proposal = repo.setCategoryOverride("t1", "FOOD_AND_DRINK")
        assertThat(proposal).isNotNull()
        assertThat(proposal!!.merchantName).isEqualTo("Starbucks")
        assertThat(proposal.category).isEqualTo("FOOD_AND_DRINK")
        assertThat(proposal.otherCount).isEqualTo(2) // t2 and t3
    }

    @Test
    fun noProposalWhenClearingOverride() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", merchantName = "Starbucks", amount = 5.0),
            tx("t2", merchantName = "Starbucks", amount = 6.0),
        ))
        // First set an override, then clear it
        repo.setCategoryOverride("t1", "FOOD_AND_DRINK")
        val proposal = repo.setCategoryOverride("t1", null)
        assertThat(proposal).isNull()
    }

    // ── applyRuleToAllMatching ─────────────────────────────────────────────────

    @Test
    fun applyRuleOverridesAllMatchingTransactions() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", merchantName = "Amazon", amount = 50.0),
            tx("t2", merchantName = "Amazon", amount = 30.0),
            tx("t3", merchantName = "Starbucks", amount = 5.0), // different merchant
        ))
        repo.applyRuleToAllMatching("Amazon", "SHOPS")

        val all = db.dao().getTransactionsByIds(listOf("t1", "t2", "t3"))
        val amazon = all.filter { it.merchantName == "Amazon" }
        val starbucks = all.first { it.merchantName == "Starbucks" }
        assertThat(amazon.all { it.categoryOverride == "SHOPS" }).isTrue()
        assertThat(starbucks.categoryOverride).isNull() // untouched
    }

    @Test
    fun applyRulePersistsToRulesTable() = runTest {
        db.dao().upsertTransactions(listOf(tx("t1", merchantName = "Netflix", amount = 15.0)))
        repo.applyRuleToAllMatching("Netflix", "ENTERTAINMENT")

        val rule = db.dao().getRuleForMerchant("Netflix")
        assertThat(rule).isNotNull()
        assertThat(rule!!.category).isEqualTo("ENTERTAINMENT")
    }

    @Test
    fun applyRuleOverwritesPreviousRule() = runTest {
        db.dao().upsertCategoryRule(CategoryRule("Uber", "TRANSPORTATION"))
        repo.applyRuleToAllMatching("Uber", "FOOD_AND_DRINK") // user changed their mind

        val rule = db.dao().getRuleForMerchant("Uber")
        assertThat(rule!!.category).isEqualTo("FOOD_AND_DRINK")
    }

    // ── refreshTransactions rule auto-apply ────────────────────────────────────
    // We test this at the DAO + repo level by seeding rules and simulating the
    // transaction-building logic the repo uses in refreshTransactions.

    @Test
    fun existingRuleAppliedToNewTransactionWithMatchingMerchant() = runTest {
        // Seed a rule: Starbucks → FOOD_AND_DRINK
        db.dao().upsertCategoryRule(CategoryRule("Starbucks", "FOOD_AND_DRINK"))

        // Pre-load rules (as refreshTransactions does)
        val rules = db.dao().getAllCategoryRules().associate { it.merchantName to it.category }

        // Simulate a new incoming transaction with no existing override
        val newTx = tx("t_new", merchantName = "Starbucks", amount = 4.5)
        val resolvedOverride = null ?: newTx.merchantName?.let { rules[it] }

        assertThat(resolvedOverride).isEqualTo("FOOD_AND_DRINK")
    }

    @Test
    fun existingPerTransactionOverrideTakesPriorityOverRule() = runTest {
        db.dao().upsertCategoryRule(CategoryRule("Amazon", "SHOPS"))
        db.dao().upsertTransactions(listOf(tx("t1", merchantName = "Amazon", amount = 99.0)))
        // User explicitly overrode this one transaction to a different category
        db.dao().setCategoryOverride("t1", "PERSONAL_CARE")

        val rules = db.dao().getAllCategoryRules().associate { it.merchantName to it.category }
        val existingOverrides = db.dao().getOverridesForIds(listOf("t1"))
            .associate { it.transactionId to it.categoryOverride }

        val tx = db.dao().getTransactionsByIds(listOf("t1")).first()
        val resolvedOverride = existingOverrides[tx.transactionId] ?: tx.merchantName?.let { rules[it] }

        assertThat(resolvedOverride).isEqualTo("PERSONAL_CARE") // explicit override wins
    }

    @Test
    fun noRuleAppliedWhenMerchantNameIsNull() = runTest {
        db.dao().upsertCategoryRule(CategoryRule("Starbucks", "FOOD_AND_DRINK"))
        val rules = db.dao().getAllCategoryRules().associate { it.merchantName to it.category }

        val txNoMerchant = tx("t1", merchantName = null, amount = 5.0)
        val resolvedOverride = null ?: txNoMerchant.merchantName?.let { rules[it] }

        assertThat(resolvedOverride).isNull()
    }

    // ── Category breakdown reflects applied rules ──────────────────────────────

    @Test
    fun breakdownReflectsRuleAppliedToAllMatching() = runTest {
        db.dao().upsertTransactions(listOf(
            tx("t1", merchantName = "Starbucks", amount = 5.0, pfcPrimary = "FOOD_AND_DRINK"),
            tx("t2", merchantName = "Starbucks", amount = 6.0, pfcPrimary = "FOOD_AND_DRINK"),
        ))
        // Apply a custom override to all Starbucks transactions
        repo.applyRuleToAllMatching("Starbucks", "PERSONAL_CARE")

        val breakdown = repo.watchCategoryBreakdown(
            listOf(AccountDateRange("cc_1", "2026-04-01", "2026-04-30"))
        ).first()

        assertThat(breakdown).hasSize(1)
        assertThat(breakdown[0].effectiveCategory).isEqualTo("PERSONAL_CARE")
        assertThat(breakdown[0].totalAmount).isWithin(0.01).of(11.0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun account(id: String, institutionId: String) = AccountEntity(
        accountId = id, institutionId = institutionId, name = "Credit Card",
        type = "credit", subtype = null, currentBalance = null,
        availableBalance = null, currencyCode = null,
    )

    private fun tx(
        id: String,
        merchantName: String?,
        amount: Double,
        pfcPrimary: String? = null,
    ) = PlaidTransaction(
        transactionId = id,
        accountId = "cc_1",
        institutionId = "ins_1",
        name = merchantName ?: "Unknown Merchant",
        amount = amount,
        date = "2026-04-15",
        merchantName = merchantName,
        pfcPrimary = pfcPrimary,
    )
}
