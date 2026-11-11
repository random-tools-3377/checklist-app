package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionsTest {
    @Test
    fun sectionsKeepTheirOrderAndWhichAreHidden() {
        val stored = "STREAKS,-BALANCE,MONEY,WEEKLY_TARGET,PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS"
        val choices = Sections.decode(stored)
        assertEquals(listOf(TodaySection.STREAKS, TodaySection.BALANCE, TodaySection.MONEY), choices.take(3).map { it.section })
        assertEquals(false, choices[1].shown)
        assertEquals(TodaySection.entries.size, choices.size)
        assertEquals(stored, Sections.encode(choices))
        assertEquals(choices, Sections.decode(Sections.encode(choices)))
        assertEquals(Sections.DEFAULT, Sections.decode(null))
        assertEquals(Sections.DEFAULT, Sections.decode("NOT_A_SECTION,,"))
    }

    @Test
    fun aSectionANewerVersionAddsGoesWhereItUsuallyIs() {
        // Stored before Today's pay was added: it comes in just after the balance, shown.
        val older = Sections.decode("STREAKS,-BALANCE,WEEKLY_TARGET,PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS")
        assertEquals(
            listOf(TodaySection.STREAKS, TodaySection.BALANCE, TodaySection.MONEY, TodaySection.WEEKLY_TARGET),
            older.take(4).map { it.section },
        )
        assertEquals(true, older[2].shown)
        // With nothing before it stored, it goes first.
        assertEquals(TodaySection.BALANCE, Sections.decode("DUE_TODAY").first().section)
    }

    @Test
    fun sectionsMoveAndHide() {
        val moved = Sections.move(Sections.DEFAULT, 3, -1)
        assertEquals(listOf(TodaySection.BALANCE, TodaySection.MONEY, TodaySection.STREAKS, TodaySection.WEEKLY_TARGET), moved.take(4).map { it.section })
        assertEquals(Sections.DEFAULT, Sections.move(Sections.DEFAULT, 0, -1)) // already at the top
        val hidden = Sections.toggle(Sections.DEFAULT, TodaySection.PACE)
        assertEquals("BALANCE,MONEY,WEEKLY_TARGET,STREAKS,-PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS", Sections.encode(hidden))
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
}
