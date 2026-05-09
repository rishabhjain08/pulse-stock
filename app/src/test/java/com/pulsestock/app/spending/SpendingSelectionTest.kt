package com.pulsestock.app.spending

import com.pulsestock.app.ui.finances.computeSpendingAccountToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for spending account chip selection logic.
 *
 * Regression covered: deselecting the last chip previously produced an empty set,
 * resulting in "No credit card transactions" even though cards were connected.
 * The fix resets to null (= all selected) instead of keeping an empty set.
 */
class SpendingSelectionTest {

    private val allIds = listOf("cc_1", "cc_2", "cc_3")

    // ── Deselect-last-chip regression ─────────────────────────────────────────

    @Test
    fun deselectingLastChipResetsToAllSelected() {
        // Only cc_1 is selected; tapping it should reset to null (all selected), not {}
        val result = computeSpendingAccountToggle("cc_1", setOf("cc_1"), allIds)
        assertNull("deselecting last chip must reset to null (all selected)", result)
    }

    @Test
    fun deselectingLastChipWhenNullCurrentResetsToAllSelected() {
        // null means all selected — treating allIds as the current set
        // Tapping the only account in a single-card setup should reset, not clear
        val singleId = listOf("cc_1")
        val result = computeSpendingAccountToggle("cc_1", null, singleId)
        assertNull(result)
    }

    // ── Normal deselect ───────────────────────────────────────────────────────

    @Test
    fun deselectingOneOfMultipleRemovesIt() {
        val result = computeSpendingAccountToggle("cc_1", setOf("cc_1", "cc_2", "cc_3"), allIds)
        assertEquals(setOf("cc_2", "cc_3"), result)
    }

    @Test
    fun deselectingOneOfTwoLeavesOne() {
        val result = computeSpendingAccountToggle("cc_2", setOf("cc_1", "cc_2"), allIds)
        assertEquals(setOf("cc_1"), result)
    }

    // ── Select (add) ──────────────────────────────────────────────────────────

    @Test
    fun selectingDeselectedChipAddsIt() {
        val result = computeSpendingAccountToggle("cc_3", setOf("cc_1", "cc_2"), allIds)
        assertEquals(setOf("cc_1", "cc_2", "cc_3"), result)
    }

    @Test
    fun selectingChipWhenNullExpandsFromAllMinusIt() {
        // null = all selected → tapping cc_1 deselects it (leaves cc_2, cc_3)
        val result = computeSpendingAccountToggle("cc_1", null, allIds)
        assertEquals(setOf("cc_2", "cc_3"), result)
    }

    // ── Already-all-selected edge cases ───────────────────────────────────────

    @Test
    fun explicitFullSetBehavesLikeNull() {
        // Explicit set with all IDs is equivalent to null — deselecting one removes it
        val result = computeSpendingAccountToggle("cc_2", setOf("cc_1", "cc_2", "cc_3"), allIds)
        assertEquals(setOf("cc_1", "cc_3"), result)
    }

    @Test
    fun unknownAccountIdIsAddedToCurrentSet() {
        val result = computeSpendingAccountToggle("cc_new", setOf("cc_1"), allIds)
        assertEquals(setOf("cc_1", "cc_new"), result)
    }
}
