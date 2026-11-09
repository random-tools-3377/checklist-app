package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetimesTest {
    private val monday = LocalDate(2026, 11, 9)

    @Test
    fun aTimeForOneDayOnlyIsFollowedByTodayRemindersAndTheAgenda() {
        val base = Goal(id = 1, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 5, repeat = Repeat.everyDay(monday), at = LocalTime(7, 0))
        val moved = base.copy(repeat = Retimes.set(base.repeat!!, monday, 18 * 60 + 30, monday))
        assertEquals(LocalTime(18, 30), Retimes.timeOn(moved, monday))
        assertEquals(LocalTime(7, 0), Retimes.timeOn(moved, monday.plusDays(1)))
        assertEquals(LocalTime(18, 30), TodayList.build(monday, listOf(moved), emptyList(), emptyList(), 0).dueToday.single().goal.at)
        assertEquals(LocalTime(7, 0), TodayList.build(monday.plusDays(1), listOf(moved), emptyList(), emptyList(), 0).dueToday.single().goal.at)
        val alerts = Alerts.between(listOf(moved), LocalDateTime(2026, 11, 9, 0, 0), LocalDateTime(2026, 11, 10, 23, 0), 0)
        assertEquals(listOf(LocalDateTime(2026, 11, 9, 18, 30), LocalDateTime(2026, 11, 10, 7, 0)), alerts.map { it.at })
        assertEquals(LocalTime(18, 30), CalendarView.agenda(monday, listOf(moved), emptyList()).single().time)

        // An untimed goal can get a time for one day too, and back to none.
        val untimed = base.copy(at = null)
        val timed = untimed.copy(repeat = Retimes.set(untimed.repeat!!, monday, 9 * 60, monday))
        assertEquals(LocalTime(9, 0), Retimes.timeOn(timed, monday))
        val back = timed.copy(repeat = Retimes.set(timed.repeat!!, monday, null, monday))
        assertNull(Retimes.timeOn(back, monday))
        // Old ones are let go.
        assertEquals(listOf(monday.plusDays(100)), Retimes.set(timed.repeat!!, monday.plusDays(100), 600, monday.plusDays(100)).retimes.map { it.day })
    }
}
