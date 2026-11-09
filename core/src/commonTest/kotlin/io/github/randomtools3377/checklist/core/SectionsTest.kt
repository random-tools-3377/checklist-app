package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionsTest {
    @Test
    fun sectionsKeepTheirOrderAndWhichAreHidden() {
        val choices = Sections.decode("STREAKS,-BALANCE,DUE_TODAY")
        assertEquals(listOf(TodaySection.STREAKS, TodaySection.BALANCE, TodaySection.DUE_TODAY), choices.take(3).map { it.section })
        assertEquals(false, choices[1].shown)
        // The rest follow, shown, in their usual order.
        assertEquals(TodaySection.entries.size, choices.size)
        assertEquals(TodaySection.WEEKLY_TARGET, choices[3].section)
        assertEquals(true, choices.drop(3).all { it.shown })
        assertEquals(choices, Sections.decode(Sections.encode(choices)))
        assertEquals(Sections.DEFAULT, Sections.decode(null))
        assertEquals(Sections.DEFAULT, Sections.decode("NOT_A_SECTION,,"))
    }

    @Test
    fun sectionsMoveAndHide() {
        val moved = Sections.move(Sections.DEFAULT, 2, -1)
        assertEquals(listOf(TodaySection.BALANCE, TodaySection.STREAKS, TodaySection.WEEKLY_TARGET), moved.take(3).map { it.section })
        assertEquals(Sections.DEFAULT, Sections.move(Sections.DEFAULT, 0, -1)) // already at the top
        val hidden = Sections.toggle(Sections.DEFAULT, TodaySection.PACE)
        assertEquals("BALANCE,WEEKLY_TARGET,STREAKS,-PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS", Sections.encode(hidden))
    }

    @Test
    fun todayIsAlwaysATabAndTheFirstTabFallsBackToIt() {
        val tabs = TabChoice.decode("GOALS,TODAY,SHOP", "SHOP")
        assertEquals(setOf(AppTabKind.GOALS, AppTabKind.SHOP), tabs.hidden)
        assertEquals(listOf(AppTabKind.TODAY, AppTabKind.PLAN, AppTabKind.REVIEW), tabs.shown)
        assertEquals(AppTabKind.TODAY, tabs.opensOn) // Shop is hidden
        assertEquals(AppTabKind.REVIEW, TabChoice.decode("GOALS", "REVIEW").opensOn)
        assertEquals("GOALS,SHOP", TabChoice.encodeHidden(setOf(AppTabKind.SHOP, AppTabKind.GOALS, AppTabKind.TODAY)))
        assertEquals(TabChoice(), TabChoice.decode(null, null))
    }
}
