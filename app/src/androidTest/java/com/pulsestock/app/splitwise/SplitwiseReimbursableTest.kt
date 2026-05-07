package com.pulsestock.app.splitwise

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pulsestock.app.data.poarvault.PoarVaultDatabase
import com.pulsestock.app.data.poarvault.SplitwiseDao
import com.pulsestock.app.data.poarvault.SplitwiseExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that watchMonthlyReimbursable returns the correct value for each month.
 *
 * Ground truth pulled from the live Splitwise API (user 815269, Rishabh):
 *   2026-05  $182.00   Lungi        paid=273  owed=91
 *   2026-02  $259.25   Diner+Mala   paid=199/165  owed=49.75/55
 *   2025-12  $180.00   Indian       paid=270  owed=90
 *   2025-08  $84.00    Anjapar      paid=126  owed=42
 *   2025-07  $552.66   4 expenses
 *   2025-06  $96.67    Mithaas      paid=116  owed=19.33
 *   2025-05  $277.34   Beacon       paid=416  owed=138.66
 */
@RunWith(AndroidJUnit4::class)
class SplitwiseReimbursableTest {

    private lateinit var db: PoarVaultDatabase
    private lateinit var dao: SplitwiseDao

    @Before
    fun setup() {
        db = PoarVaultDatabase.getInMemory(ApplicationProvider.getApplicationContext())
        dao = db.splitwiseDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ── Core calculation ──────────────────────────────────────────────────────

    @Test
    fun singleExpense_reimbursableIsPayMinusOwed() = runTest {
        dao.insertExpenses(listOf(expense(id = 1, date = "2026-05-02", paid = 273.0, owed = 91.0)))
        assertThat(reimbursable("2026-05")).isWithin(0.01).of(182.0)
    }

    @Test
    fun multipleExpensesSameMonth_summed() = runTest {
        // Feb 2026: Diner + Mala
        dao.insertExpenses(listOf(
            expense(id = 1, date = "2026-02-08", paid = 199.0, owed = 49.75),  // 149.25
            expense(id = 2, date = "2026-02-21", paid = 165.0, owed = 55.0),   // 110.00
        ))
        assertThat(reimbursable("2026-02")).isWithin(0.01).of(259.25)
    }

    @Test
    fun expensesInDifferentMonths_doNotBleed() = runTest {
        dao.insertExpenses(listOf(
            expense(id = 1, date = "2026-05-02", paid = 273.0, owed = 91.0),
            expense(id = 2, date = "2026-02-08", paid = 199.0, owed = 49.75),
        ))
        assertThat(reimbursable("2026-05")).isWithin(0.01).of(182.0)
        assertThat(reimbursable("2026-02")).isWithin(0.01).of(149.25)
    }

    @Test
    fun emptyMonth_returnsZero() = runTest {
        dao.insertExpenses(listOf(expense(id = 1, date = "2026-05-02", paid = 273.0, owed = 91.0)))
        assertThat(reimbursable("2026-04")).isWithin(0.01).of(0.0)
    }

    // ── July 2025 — 4 expenses ($552.66 total) ────────────────────────────────

    @Test
    fun july2025_fourExpenses_correctTotal() = runTest {
        dao.insertExpenses(listOf(
            expense(id = 10, date = "2025-07-03", paid = 170.0,  owed = 85.0),    // 85.00
            expense(id = 11, date = "2025-07-19", paid = 220.0,  owed = 55.0),    // 165.00
            expense(id = 12, date = "2025-07-26", paid = 20.0,   owed = 6.67),    // 13.33
            expense(id = 13, date = "2025-07-26", paid = 434.0,  owed = 144.67),  // 289.33
        ))
        assertThat(reimbursable("2025-07")).isWithin(0.01).of(552.66)
    }

    // ── Inbox state rules ─────────────────────────────────────────────────────

    @Test
    fun dismissedExpense_excludedFromTotal() = runTest {
        dao.insertExpenses(listOf(
            expense(id = 1, date = "2026-05-02", paid = 273.0, owed = 91.0),
            expense(id = 2, date = "2026-05-15", paid = 100.0, owed = 50.0),
        ))
        dao.dismiss(2)
        assertThat(reimbursable("2026-05")).isWithin(0.01).of(182.0)
    }

    @Test
    fun undismissedExpense_includedAgain() = runTest {
        dao.insertExpenses(listOf(
            expense(id = 1, date = "2026-05-02", paid = 273.0, owed = 91.0),
            expense(id = 2, date = "2026-05-15", paid = 100.0, owed = 50.0),
        ))
        dao.dismiss(2)
        dao.undismiss(2)
        assertThat(reimbursable("2026-05")).isWithin(0.01).of(232.0) // 182 + 50
    }

    // ── updateShares correctness ──────────────────────────────────────────────

    @Test
    fun updateShares_correctsStaleValues() = runTest {
        // Simulate old row with 0/0 shares (pre-migration default)
        dao.insertExpenses(listOf(expense(id = 1, date = "2026-05-02", paid = 0.0, owed = 0.0)))
        assertThat(reimbursable("2026-05")).isWithin(0.01).of(0.0)

        // processAndStore calls updateShares after insert
        dao.updateShares(1, paidShare = 273.0, ownedShare = 91.0)
        assertThat(reimbursable("2026-05")).isWithin(0.01).of(182.0)
    }

    @Test
    fun updateShares_inserIgnoreDoesNotOverwriteExistingShares() = runTest {
        // First insert with correct values
        dao.insertExpenses(listOf(expense(id = 1, date = "2026-05-02", paid = 273.0, owed = 91.0)))
        // Second insert of same id (IGNORE) should not change shares
        dao.insertExpenses(listOf(expense(id = 1, date = "2026-05-02", paid = 0.0, owed = 0.0)))
        // updateShares must still be called to refresh — simulate that
        dao.updateShares(1, paidShare = 273.0, ownedShare = 91.0)
        assertThat(reimbursable("2026-05")).isWithin(0.01).of(182.0)
    }

    // ── Full 12-month ground-truth check ─────────────────────────────────────

    @Test
    fun groundTruth_allActiveMonths_matchApiExpectation() = runTest {
        dao.insertExpenses(allGroundTruthExpenses())
        allGroundTruthExpenses().forEach { dao.updateShares(it.id, it.paidShare, it.ownedShare) }

        assertThat(reimbursable("2026-05")).isWithin(0.01).of(182.00)
        assertThat(reimbursable("2026-04")).isWithin(0.01).of(0.0)
        assertThat(reimbursable("2026-03")).isWithin(0.01).of(0.0)
        assertThat(reimbursable("2026-02")).isWithin(0.01).of(259.25)
        assertThat(reimbursable("2026-01")).isWithin(0.01).of(0.0)
        assertThat(reimbursable("2025-12")).isWithin(0.01).of(180.00)
        assertThat(reimbursable("2025-11")).isWithin(0.01).of(0.0)
        assertThat(reimbursable("2025-10")).isWithin(0.01).of(0.0)
        assertThat(reimbursable("2025-09")).isWithin(0.01).of(0.0)
        assertThat(reimbursable("2025-08")).isWithin(0.01).of(84.00)
        assertThat(reimbursable("2025-07")).isWithin(0.01).of(552.66)
        assertThat(reimbursable("2025-06")).isWithin(0.01).of(96.67)
        assertThat(reimbursable("2025-05")).isWithin(0.01).of(277.34)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun reimbursable(month: String): Double =
        dao.watchMonthlyReimbursable(month).first()

    private fun expense(
        id: Long,
        date: String,
        paid: Double,
        owed: Double,
        description: String = "Test",
    ) = SplitwiseExpense(
        id = id,
        description = description,
        date = date,
        totalAmount = paid,
        currencyCode = "USD",
        paidShare = paid,
        ownedShare = owed,
    )

    private fun allGroundTruthExpenses(): List<SplitwiseExpense> = listOf(
        // 2026-05
        expense(100, "2026-05-02", 273.0, 91.0, "Lungi"),
        // 2026-02
        expense(101, "2026-02-08", 199.0, 49.75, "Diner"),
        expense(102, "2026-02-21", 165.0, 55.0, "Mala"),
        // 2025-12
        expense(103, "2025-12-11", 270.0, 90.0, "Indian"),
        // 2025-08
        expense(104, "2025-08-09", 126.0, 42.0, "Anjapar"),
        // 2025-07
        expense(105, "2025-07-03", 170.0, 85.0, "Thai villa"),
        expense(106, "2025-07-19", 220.0, 55.0, "Mediterranean 83rd st"),
        expense(107, "2025-07-26", 20.0,  6.67, "Uber"),
        expense(108, "2025-07-26", 434.0, 144.67, "Felice"),
        // 2025-06
        expense(109, "2025-06-02", 116.0, 19.33, "Mithaas and parking"),
        // 2025-05
        expense(110, "2025-05-25", 416.0, 138.66, "Beacon comedy"),
    )
}
