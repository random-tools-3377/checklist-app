package io.github.randomtools3377.checklist.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which pages sit behind the privacy lock, so they show it and close when it closes. */
class LockedPageTest {
    private val budgetPages = listOf("budget-pay", "money/0", "money/4/in", "budget-categories", "work-day/20400", "budget-bills", "bill/3", "budget-report")

    @Test
    fun everyBudgetPageIsBehindTheBudgetLock() {
        val locked = Locks(budget = true)
        budgetPages.forEach { assertEquals("The budget", lockedPage(it, locked, emptySet()), it) }
        budgetPages.forEach { assertNull(lockedPage(it, Locks(shop = true, review = true), emptySet()), it) }
    }

    @Test
    fun theShopReviewAndMoodPagesFollowTheirLocks() {
        assertEquals("The shop", lockedPage("reward/2", Locks(shop = true), emptySet()))
        assertEquals("The shop", lockedPage("balance-check", Locks(shop = true), emptySet()))
        assertEquals("The badges page", lockedPage("badges", Locks(review = true), emptySet()))
        assertEquals("The mood journal", lockedPage("mood-journal", Locks(reasons = true), emptySet()))
        assertEquals("Mood and your goals", lockedPage("mood-goals", Locks(review = true), emptySet()))
        assertNull(lockedPage("reward/2", Locks(budget = true), emptySet()))
    }

    @Test
    fun onlyAPrivateGoalsEditorIsLocked() {
        val locks = Locks(privateGoals = true)
        assertEquals("This private goal", lockedPage("habit/7", locks, setOf(7L)))
        assertEquals("This private goal", lockedPage("project/7", locks, setOf(7L)))
        assertNull(lockedPage("habit/8", locks, setOf(7L)))
        assertNull(lockedPage("habit/0", locks, setOf(7L)))
        assertNull(lockedPage("habit/7", Locks(budget = true), setOf(7L)))
    }

    @Test
    fun tabsAndOtherPagesShowTheirOwnLockOrNone() {
        val all = Locks(history = true, shop = true, privateGoals = true, reasons = true, gaps = true, review = true, budget = true)
        assertTrue(all.anyOn)
        listOf("today", "budget", "shop", "review", "history", "settings", "plan-week", "done").forEach { assertNull(lockedPage(it, all, emptySet()), it) }
        assertTrue(!Locks().anyOn)
    }
}
