package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionsTest {
    @Test
    fun sectionsKeepTheirOrderAndWhichAreHidden() {
        val stored = "MOOD,STREAKS,-BALANCE,CHARACTER,QUESTS,RACE,BOSS,MONEY,WEEKLY_TARGET,PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS"
        val choices = Sections.decode(stored)
        assertEquals(listOf(TodaySection.MOOD, TodaySection.STREAKS, TodaySection.BALANCE, TodaySection.CHARACTER), choices.take(4).map { it.section })
        assertEquals(false, choices[2].shown)
        assertEquals(TodaySection.entries.size, choices.size)
        assertEquals(stored, Sections.encode(choices))
        assertEquals(choices, Sections.decode(Sections.encode(choices)))
        assertEquals(Sections.DEFAULT, Sections.decode(null))
        assertEquals(Sections.DEFAULT, Sections.decode("NOT_A_SECTION,,"))
    }

    @Test
    fun aSectionANewerVersionAddsGoesWhereItUsuallyIs() {
        // Stored before the mood, the character's cards and Today's pay had sections of their own:
        // the mood goes first, as it was, and the rest just after the balance, shown.
        val older = Sections.decode("STREAKS,-BALANCE,WEEKLY_TARGET,PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS")
        assertEquals(
            listOf(
                TodaySection.MOOD, TodaySection.STREAKS, TodaySection.BALANCE, TodaySection.CHARACTER, TodaySection.QUESTS,
                TodaySection.RACE, TodaySection.BOSS, TodaySection.MONEY, TodaySection.WEEKLY_TARGET,
            ),
            older.take(9).map { it.section },
        )
        assertEquals(true, older[3].shown)
        // With nothing before it stored, it goes first.
        assertEquals(TodaySection.MOOD, Sections.decode("DUE_TODAY").first().section)
    }

    @Test
    fun anOrderSavedWithTheBetsSectionKeepsTheRestOfIt() {
        // Saved from 0.61.0 on, with Bets moved up and Pace hidden: Bets is left out quietly.
        val saved = "MOOD,BETS,STREAKS,-BALANCE,CHARACTER,QUESTS,RACE,BOSS,MONEY,WEEKLY_TARGET,-PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS"
        val choices = Sections.decode(saved)
        assertEquals(TodaySection.entries.size, choices.size)
        assertEquals(listOf(TodaySection.MOOD, TodaySection.STREAKS, TodaySection.BALANCE), choices.take(3).map { it.section })
        assertEquals(listOf(TodaySection.BALANCE, TodaySection.PACE), choices.filter { !it.shown }.map { it.section })
        assertEquals(saved.replace("BETS,", ""), Sections.encode(choices))
        // Hidden, or the only thing stored, it is left out all the same.
        assertEquals(Sections.decode(saved.replace("BETS,", "")), Sections.decode(saved.replace("BETS,", "-BETS,")))
        assertEquals(Sections.DEFAULT, Sections.decode("BETS"))
        assertEquals(Sections.DEFAULT, Sections.decode("-BETS"))
    }

    @Test
    fun sectionsMoveAndHide() {
        val moved = Sections.move(Sections.DEFAULT, 1, -1)
        assertEquals(listOf(TodaySection.BALANCE, TodaySection.MOOD, TodaySection.CHARACTER), moved.take(3).map { it.section })
        assertEquals(Sections.DEFAULT, Sections.move(Sections.DEFAULT, 0, -1)) // already at the top
        val hidden = Sections.toggle(Sections.DEFAULT, TodaySection.PACE)
        assertEquals("MOOD,BALANCE,CHARACTER,QUESTS,RACE,BOSS,MONEY,WEEKLY_TARGET,STREAKS,-PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS", Sections.encode(hidden))
    }

    @Test
    fun tabsKeepTheUsersOrder() {
        val tabs = TabChoice.decode("SHOP", null, "GOALS,TODAY,REVIEW")
        assertEquals(listOf(AppTabKind.GOALS, AppTabKind.TODAY, AppTabKind.REVIEW, AppTabKind.PLAN, AppTabKind.BUDGET), tabs.shown)
        assertEquals(listOf(AppTabKind.TODAY, AppTabKind.GOALS), tabs.moved(AppTabKind.TODAY, -1).take(2))
        assertEquals(tabs.order, tabs.moved(AppTabKind.GOALS, -1))
        assertEquals(tabs.order, TabChoice.decode(null, null, TabChoice.encodeOrder(tabs.order)).order)
        assertEquals(AppTabKind.entries, TabChoice.decode(null, null, null).order)
    }

    @Test
    fun todayIsAlwaysATabAndTheFirstTabFallsBackToIt() {
        val tabs = TabChoice.decode("GOALS,TODAY,SHOP", "SHOP")
        assertEquals(setOf(AppTabKind.GOALS, AppTabKind.SHOP), tabs.hidden)
        // Budget is in the list too; the app leaves it out while Budget is off.
        assertEquals(listOf(AppTabKind.TODAY, AppTabKind.PLAN, AppTabKind.REVIEW, AppTabKind.BUDGET), tabs.shown)
        assertEquals(AppTabKind.TODAY, tabs.opensOn) // Shop is hidden
        assertEquals(AppTabKind.REVIEW, TabChoice.decode("GOALS", "REVIEW").opensOn)
        assertEquals("GOALS,SHOP", TabChoice.encodeHidden(setOf(AppTabKind.SHOP, AppTabKind.GOALS, AppTabKind.TODAY)))
        assertEquals(TabChoice(), TabChoice.decode(null, null))
    }

    @Test
    fun theBudgetTabIsNotOpenedWhileBudgetIsOff() {
        val tabs = TabChoice.decode(null, "BUDGET")
        assertEquals(AppTabKind.BUDGET, tabs.opensOn(budgetOn = true))
        assertEquals(AppTabKind.TODAY, tabs.opensOn(budgetOn = false))
        assertEquals(AppTabKind.TODAY, TabChoice.decode("BUDGET", "BUDGET").opensOn(budgetOn = true))
        assertEquals(AppTabKind.REVIEW, TabChoice.decode(null, "REVIEW").opensOn(budgetOn = false))
    }
}
