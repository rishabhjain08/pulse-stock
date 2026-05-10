package com.pulsestock.app.ui.finances

import com.pulsestock.app.data.poarvault.AccountEntity
import com.pulsestock.app.data.poarvault.ExpenseWithLinks
import com.pulsestock.app.data.poarvault.PlaidTransaction
import com.pulsestock.app.data.poarvault.SplitwiseExpense
import com.pulsestock.app.data.poarvault.usesWindowHeuristic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Test helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Creates a minimal AccountEntity.
 * [usesWindowHeuristic] = true when BOTH lastStatementDate AND nextDueDate are null
 * (i.e. a business CC that Plaid never loads liability dates for).
 */
private fun account(
    id: String,
    lastStatementDate: String? = null,
    nextDueDate: String? = null,
): AccountEntity = AccountEntity(
    accountId = id,
    institutionId = "inst_test",
    name = id,
    type = "credit",
    subtype = "credit card",
    currentBalance = null,
    availableBalance = null,
    currencyCode = "USD",
    lastStatementDate = lastStatementDate,
    nextDueDate = nextDueDate,
)

/** Business CC: both Plaid liability dates absent → usesWindowHeuristic=true. */
private fun businessCC(id: String) = account(id)

/** Personal CC with a statement date → usesWindowHeuristic=false. */
private fun personalCC(id: String) = account(id, lastStatementDate = "2024-03-15")

/** Personal CC with only a due date → usesWindowHeuristic=false. */
private fun personalCCDueOnly(id: String) = account(id, nextDueDate = "2024-04-05")

/** Builds a [FinancesUiState] with the given accounts and optional explicit selection. */
private fun stateWith(
    accounts: List<AccountEntity>,
    selectedIds: Set<String>? = null,
    window: SpendingWindow = SpendingWindow.LAST_30_DAYS,
    pendingSnackbar: Boolean = false,
) = FinancesUiState(
    creditAccounts = accounts,
    selectedSpendingAccountIds = selectedIds,
    spendingWindow = window,
    pendingBusinessCardSnackbar = pendingSnackbar,
)

/** Minimal SplitwiseExpense used in displayedList tests. */
private fun testExpense(id: Long, isDismissed: Boolean = false, isAutoMatched: Boolean = false) =
    SplitwiseExpense(
        id = id,
        description = "test $id",
        date = "2024-01-01",
        totalAmount = 10.0,
        currencyCode = "USD",
        isDismissed = isDismissed,
        isAutoMatched = isAutoMatched,
    )

/** Minimal PlaidTransaction (needed to populate ExpenseWithLinks.linkedTransactions). */
private fun testTx(id: String) = PlaidTransaction(
    transactionId = id,
    accountId = "acc_1",
    institutionId = "inst_1",
    name = "Test merchant",
    amount = 10.0,
    date = "2024-01-01",
)

// ─────────────────────────────────────────────────────────────────────────────
// 1. usesWindowHeuristic extension property
// ─────────────────────────────────────────────────────────────────────────────

class UsesWindowHeuristicTest {

    @Test
    fun bothDatesNull_isBusinessCC() {
        assertTrue(account("biz", lastStatementDate = null, nextDueDate = null).usesWindowHeuristic)
    }

    @Test
    fun lastStatementDateNonNull_nextDueDateNull_isPersonalCC() {
        assertFalse(account("p", lastStatementDate = "2024-03-15", nextDueDate = null).usesWindowHeuristic)
    }

    @Test
    fun lastStatementDateNull_nextDueDateNonNull_isPersonalCC() {
        assertFalse(account("p", lastStatementDate = null, nextDueDate = "2024-04-05").usesWindowHeuristic)
    }

    @Test
    fun bothDatesNonNull_isPersonalCC() {
        assertFalse(account("p", lastStatementDate = "2024-03-15", nextDueDate = "2024-04-05").usesWindowHeuristic)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. FinancesUiState.effectiveSpendingAccounts
// ─────────────────────────────────────────────────────────────────────────────

class EffectiveSpendingAccountsTest {

    private val p1 = personalCC("p1")
    private val p2 = personalCC("p2")
    private val b1 = businessCC("b1")

    @Test
    fun nullSelection_returnsAllCreditAccounts() {
        val state = stateWith(listOf(p1, p2, b1), selectedIds = null)
        assertEquals(listOf(p1, p2, b1), state.effectiveSpendingAccounts)
    }

    @Test
    fun emptySetSelection_returnsEmptyList() {
        val state = stateWith(listOf(p1, p2, b1), selectedIds = emptySet())
        assertTrue(state.effectiveSpendingAccounts.isEmpty())
    }

    @Test
    fun singleIdSelection_returnsOnlyThatAccount() {
        val state = stateWith(listOf(p1, p2, b1), selectedIds = setOf("p1"))
        assertEquals(listOf(p1), state.effectiveSpendingAccounts)
    }

    @Test
    fun selectionContainsUnknownId_silentlyOmitsIt_noCrash() {
        val state = stateWith(listOf(p1, p2), selectedIds = setOf("p1", "unknown_id"))
        assertEquals(listOf(p1), state.effectiveSpendingAccounts)
    }

    @Test
    fun selectionWithMultipleIds_returnsMatchingSubset() {
        val state = stateWith(listOf(p1, p2, b1), selectedIds = setOf("p1", "b1"))
        assertEquals(listOf(p1, b1), state.effectiveSpendingAccounts)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. FinancesUiState.displayedList
//
// Flag semantics in ExpenseWithLinks (from SplitwiseModels.kt):
//   isDismissed        = expense.isDismissed
//   isReconciled       = !isDismissed && linkedTransactions.isNotEmpty() && !expense.isAutoMatched
//   isPendingAutoMatch = !isDismissed && expense.isAutoMatched && linkedTransactions.isNotEmpty()
//   isUnlinked         = !isDismissed && linkedTransactions.isEmpty() && !expense.isAutoMatched
// ─────────────────────────────────────────────────────────────────────────────

class DisplayedListTest {

    /** Produces an unlinked expense: no links, not dismissed, not auto-matched. */
    private fun unlinked(id: Long) =
        ExpenseWithLinks(expense = testExpense(id), linkedTransactions = emptyList())

    /** Produces a reconciled expense: has links, not dismissed, not auto-matched. */
    private fun reconciled(id: Long) =
        ExpenseWithLinks(expense = testExpense(id), linkedTransactions = listOf(testTx("tx_$id")))

    /** Produces a pending auto-match expense: has links AND isAutoMatched=true. */
    private fun pendingAutoMatch(id: Long) =
        ExpenseWithLinks(
            expense = testExpense(id, isAutoMatched = true),
            linkedTransactions = listOf(testTx("tx_auto_$id")),
        )

    /** Produces a dismissed expense. */
    private fun dismissed(id: Long) =
        ExpenseWithLinks(expense = testExpense(id, isDismissed = true), linkedTransactions = emptyList())

    @Test
    fun toLink_filterReturnsOnlyUnlinkedAndPendingAutoMatch() {
        val items = listOf(unlinked(1), pendingAutoMatch(2), reconciled(3), dismissed(4))
        val state = FinancesUiState(filter = ReconcileFilter.TO_LINK, allWithLinks = items)
        val displayed = state.displayedList
        assertEquals(2, displayed.size)
        assertTrue(displayed.all { it.isUnlinked || it.isPendingAutoMatch })
    }

    @Test
    fun linked_filterReturnsOnlyReconciled() {
        val items = listOf(unlinked(1), reconciled(2), reconciled(3), dismissed(4))
        val state = FinancesUiState(filter = ReconcileFilter.LINKED, allWithLinks = items)
        assertEquals(2, state.displayedList.size)
        assertTrue(state.displayedList.all { it.isReconciled })
    }

    @Test
    fun dismissed_filterReturnsOnlyDismissed() {
        val items = listOf(unlinked(1), reconciled(2), dismissed(3), dismissed(4))
        val state = FinancesUiState(filter = ReconcileFilter.DISMISSED, allWithLinks = items)
        assertEquals(2, state.displayedList.size)
        assertTrue(state.displayedList.all { it.isDismissed })
    }

    @Test
    fun all_filterReturnsEverything() {
        val items = listOf(unlinked(1), reconciled(2), dismissed(3), pendingAutoMatch(4))
        val state = FinancesUiState(filter = ReconcileFilter.ALL, allWithLinks = items)
        assertEquals(4, state.displayedList.size)
    }

    @Test
    fun emptyList_allFilter_returnsEmpty() {
        val state = FinancesUiState(filter = ReconcileFilter.ALL, allWithLinks = emptyList())
        assertTrue(state.displayedList.isEmpty())
    }

    @Test
    fun toLink_emptyList_returnsEmpty() {
        val state = FinancesUiState(filter = ReconcileFilter.TO_LINK, allWithLinks = emptyList())
        assertTrue(state.displayedList.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. computeWindowChange — the pure business logic behind setSpendingWindow
// ─────────────────────────────────────────────────────────────────────────────

class ComputeWindowChangeTest {

    private val personal1 = personalCC("p1")
    private val personal2 = personalCC("p2")
    private val business1 = businessCC("b1")
    private val business2 = businessCC("b2")

    // ── STATEMENT: only personal CCs → no exclusion, no snackbar ─────────────

    @Test
    fun statement_onlyPersonalCCs_noExclusionNoSnackbar() {
        val state = stateWith(listOf(personal1, personal2))
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertEquals(SpendingWindow.STATEMENT, result.spendingWindow)
        assertFalse("no snackbar when no business CCs present", result.pendingBusinessCardSnackbar)
        // No business CCs to exclude; stays null (all selected)
        assertNull(result.selectedSpendingAccountIds)
    }

    // ── STATEMENT: only business CCs → all excluded, result is null, snackbar fires ─

    @Test
    fun statement_onlyBusinessCCs_allExcluded_resultIsNull_snackbarFires() {
        val state = stateWith(listOf(business1, business2))
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertEquals(SpendingWindow.STATEMENT, result.spendingWindow)
        assertTrue("snackbar must fire when business CCs are excluded", result.pendingBusinessCardSnackbar)
        // All selected accounts were business CCs; excluded set is empty.
        // Spec: when no personal cards exist at all, return null (not empty set) to avoid
        // showing an "empty breakdown" when the real issue is "no personal cards linked".
        assertNull(
            "when ALL accounts are business CCs the result must be null, not emptySet()",
            result.selectedSpendingAccountIds,
        )
    }

    // ── STATEMENT: mixed → business excluded, personal remain, snackbar fires ─

    @Test
    fun statement_mixedCCs_businessExcluded_personalRemain_snackbarFires() {
        val state = stateWith(listOf(personal1, personal2, business1))
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertEquals(SpendingWindow.STATEMENT, result.spendingWindow)
        assertTrue(result.pendingBusinessCardSnackbar)
        assertEquals(setOf("p1", "p2"), result.selectedSpendingAccountIds)
    }

    // ── THIS_CYCLE: business CCs present → NO exclusion (regression guard) ────
    // Bug: an earlier version excluded business CCs on THIS_CYCLE too. This test
    // documents the fixed behaviour: only STATEMENT triggers the exclusion.

    @Test
    fun thisCycle_businessCCsPresent_noExclusion_noSnackbar() {
        val state = stateWith(listOf(personal1, business1))
        val result = computeWindowChange(state, SpendingWindow.THIS_CYCLE)

        assertEquals(SpendingWindow.THIS_CYCLE, result.spendingWindow)
        assertFalse("THIS_CYCLE must NOT exclude business CCs", result.pendingBusinessCardSnackbar)
        // null = all accounts remain selected
        assertNull(result.selectedSpendingAccountIds)
    }

    @Test
    fun thisCycle_onlyBusinessCCs_noExclusion_noSnackbar() {
        val state = stateWith(listOf(business1, business2))
        val result = computeWindowChange(state, SpendingWindow.THIS_CYCLE)

        assertFalse(result.pendingBusinessCardSnackbar)
        assertNull(result.selectedSpendingAccountIds)
    }

    @Test
    fun lastThirtyDays_businessCCsPresent_noExclusion_noSnackbar() {
        val state = stateWith(listOf(personal1, business1))
        val result = computeWindowChange(state, SpendingWindow.LAST_30_DAYS)

        assertFalse(result.pendingBusinessCardSnackbar)
        assertNull(result.selectedSpendingAccountIds)
    }

    @Test
    fun thisMonth_businessCCsPresent_noExclusion_noSnackbar() {
        val state = stateWith(listOf(personal1, business1))
        val result = computeWindowChange(state, SpendingWindow.THIS_MONTH)

        assertFalse(result.pendingBusinessCardSnackbar)
        assertNull(result.selectedSpendingAccountIds)
    }

    // ── Switching back from STATEMENT → business CCs restored ────────────────

    @Test
    fun switchFromStatement_toLast30Days_businessCCsRestored_normalizesToNull() {
        // After STATEMENT: b1 was auto-excluded; only p1 remains selected.
        val stateAfterStatement = stateWith(
            accounts = listOf(personal1, business1),
            selectedIds = setOf("p1"),
            window = SpendingWindow.STATEMENT,
        )
        val result = computeWindowChange(stateAfterStatement, SpendingWindow.LAST_30_DAYS)

        assertEquals(SpendingWindow.LAST_30_DAYS, result.spendingWindow)
        assertFalse(result.pendingBusinessCardSnackbar)
        // Restored == all accounts → normalized to null
        assertNull(result.selectedSpendingAccountIds)
    }

    @Test
    fun switchFromStatement_toThisCycle_businessCCsRestored_normalizesToNull() {
        val stateAfterStatement = stateWith(
            accounts = listOf(personal1, business1),
            selectedIds = setOf("p1"),
            window = SpendingWindow.STATEMENT,
        )
        val result = computeWindowChange(stateAfterStatement, SpendingWindow.THIS_CYCLE)

        assertEquals(SpendingWindow.THIS_CYCLE, result.spendingWindow)
        assertFalse(result.pendingBusinessCardSnackbar)
        assertNull(result.selectedSpendingAccountIds)
    }

    // ── STATEMENT with manually-deselected personal CC ────────────────────────

    @Test
    fun statement_userManuallyDeselectedPersonalCC_businessExcluded_personalDeselectionPreserved() {
        // p1 + b1 selected; p2 deliberately NOT selected by user
        val state = stateWith(
            accounts = listOf(personal1, personal2, business1),
            selectedIds = setOf("p1", "b1"),
        )
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertTrue(result.pendingBusinessCardSnackbar)
        // b1 removed; p1 stays; p2 was never selected and remains absent
        assertEquals(setOf("p1"), result.selectedSpendingAccountIds)
    }

    // ── Switching back: custom selection — business restored, other deselections kept ─

    @Test
    fun switchFromStatement_customSelection_businessRestored_manualDeselectionPreserved() {
        // After STATEMENT: only p1 selected (b1 was auto-excluded; p2 manually absent)
        val stateAfterStatement = stateWith(
            accounts = listOf(personal1, personal2, business1),
            selectedIds = setOf("p1"),
            window = SpendingWindow.STATEMENT,
        )
        val result = computeWindowChange(stateAfterStatement, SpendingWindow.LAST_30_DAYS)

        // b1 re-added; p2 must stay absent (it was the user's intentional deselection)
        assertEquals(setOf("p1", "b1"), result.selectedSpendingAccountIds)
        assertFalse(result.pendingBusinessCardSnackbar)
    }

    @Test
    fun switchFromStatement_multipleBusinessCCs_allRestored_manualDeselectionPreserved() {
        // After STATEMENT: p1 only (b1 + b2 auto-excluded; p2 manually absent)
        val stateAfterStatement = stateWith(
            accounts = listOf(personal1, personal2, business1, business2),
            selectedIds = setOf("p1"),
            window = SpendingWindow.STATEMENT,
        )
        val result = computeWindowChange(stateAfterStatement, SpendingWindow.LAST_30_DAYS)

        // b1 + b2 restored; p2 still absent
        assertEquals(setOf("p1", "b1", "b2"), result.selectedSpendingAccountIds)
    }

    @Test
    fun switchFromStatement_restoredEqualsAllAccounts_normalizesToNull() {
        // p1 + b1 are ALL the accounts; after restoring b1 we have the full set → null
        val stateAfterStatement = stateWith(
            accounts = listOf(personal1, business1),
            selectedIds = setOf("p1"),
            window = SpendingWindow.STATEMENT,
        )
        val result = computeWindowChange(stateAfterStatement, SpendingWindow.LAST_30_DAYS)
        assertNull("restored == all accounts → must normalize to null", result.selectedSpendingAccountIds)
    }

    // ── Snackbar fires exactly once per auto-exclusion transition ─────────────

    @Test
    fun snackbar_setToTrueOnFirstStatementSwitch() {
        val state = stateWith(listOf(personal1, business1))
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)
        assertTrue(result.pendingBusinessCardSnackbar)
    }

    @Test
    fun snackbar_doesNotReFireWhenBusinessCCAlreadyAbsent() {
        // Simulate: already in STATEMENT, b1 was already auto-excluded (flag consumed/cleared)
        val alreadyInStatement = stateWith(
            accounts = listOf(personal1, business1),
            selectedIds = setOf("p1"),          // b1 absent from selection
            window = SpendingWindow.STATEMENT,
            pendingSnackbar = false,
        )
        // Request STATEMENT again (idempotent re-selection of the same window)
        val result = computeWindowChange(alreadyInStatement, SpendingWindow.STATEMENT)

        // b1 is not in currentEffective (setOf("p1")), so intersect(businessCardIds) is empty
        assertFalse(
            "snackbar must not re-fire when business CC already absent from selection",
            result.pendingBusinessCardSnackbar,
        )
        assertEquals(setOf("p1"), result.selectedSpendingAccountIds)
    }

    @Test
    fun snackbar_doesNotFireForNonStatementWindows() {
        val state = stateWith(listOf(personal1, business1))
        assertFalse(computeWindowChange(state, SpendingWindow.THIS_CYCLE).pendingBusinessCardSnackbar)
        assertFalse(computeWindowChange(state, SpendingWindow.LAST_30_DAYS).pendingBusinessCardSnackbar)
        assertFalse(computeWindowChange(state, SpendingWindow.THIS_MONTH).pendingBusinessCardSnackbar)
    }

    // ── No snackbar when user had already manually excluded all business CCs ──

    @Test
    fun statement_businessCCAlreadyManuallyDeselected_noSnackbar() {
        // User had previously deselected b1 manually before switching to STATEMENT
        val state = stateWith(
            accounts = listOf(personal1, business1),
            selectedIds = setOf("p1"),          // b1 already absent
        )
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertFalse(
            "snackbar must not fire when business CC was already absent from selection",
            result.pendingBusinessCardSnackbar,
        )
        assertEquals(setOf("p1"), result.selectedSpendingAccountIds)
    }

    @Test
    fun statement_allBusinessCCsAlreadyManuallyDeselected_noSnackbar_noChange() {
        // Two business CCs, both already absent from explicit selection
        val state = stateWith(
            accounts = listOf(personal1, business1, business2),
            selectedIds = setOf("p1"),
        )
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertFalse(result.pendingBusinessCardSnackbar)
        assertEquals(setOf("p1"), result.selectedSpendingAccountIds)
    }

    // ── Two business CCs + one personal: both business excluded at once ───────

    @Test
    fun statement_twoBusinessCCs_onePersonal_bothBusinessExcluded_snackbarFires() {
        val state = stateWith(listOf(personal1, business1, business2))
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertTrue(result.pendingBusinessCardSnackbar)
        assertEquals(setOf("p1"), result.selectedSpendingAccountIds)
    }

    // ── No accounts at all — no crash ─────────────────────────────────────────

    @Test
    fun statement_noAccounts_noSnackbar_selectionNull() {
        val state = stateWith(emptyList())
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertFalse(result.pendingBusinessCardSnackbar)
        assertNull(result.selectedSpendingAccountIds)
    }

    // ── personalCCDueOnly (only nextDueDate set) still counts as personal ─────

    @Test
    fun statement_dueOnlyPersonalCC_notExcluded() {
        val dueOnly = personalCCDueOnly("due_only")
        val state = stateWith(listOf(dueOnly, business1))
        val result = computeWindowChange(state, SpendingWindow.STATEMENT)

        assertTrue(result.pendingBusinessCardSnackbar)
        assertEquals(setOf("due_only"), result.selectedSpendingAccountIds)
    }
}
