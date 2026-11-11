package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun aGoalDoneSeveralTimesADayKeepsItsTimes() {
        val settings = PaceSettings(dayEndHour = 0, defaultFinish = LocalTime(21, 0), dayStart = LocalTime(9, 0))
        // Its own times, 9 am and 9 pm: a time for the day changes nothing.
        val meds = Goal(id = 1, kind = GoalKind.HABIT, name = "Meds", icon = "💊", points = 2, repeat = Repeat.everyDay(monday), target = 2, times = listOf(LocalTime(9, 0), LocalTime(21, 0)))
        val medsMoved = meds.copy(repeat = Retimes.set(meds.repeat!!, monday, 12 * 60, monday))
        assertNull(Retimes.on(medsMoved, monday).at)
        assertNull(Retimes.timeOn(medsMoved, monday))
        assertEquals(listOf(LocalDateTime(2026, 11, 9, 9, 0), LocalDateTime(2026, 11, 9, 21, 0)), Pace.slots(Retimes.on(medsMoved, monday), monday, settings).map { it.at })
        // Spread over the day: still spread, not all at one time.
        val water = Goal(id = 2, kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 1, repeat = Repeat.everyDay(monday), target = 4)
        val waterMoved = water.copy(repeat = Retimes.set(water.repeat!!, monday, 15 * 60, monday))
        val row = TodayList.build(monday, listOf(waterMoved), emptyList(), emptyList(), 0).dueToday.single()
        assertNull(row.goal.at)
        assertEquals(4, Pace.slots(row.goal, monday, settings).size)
        assertTrue(Alerts.between(listOf(waterMoved), LocalDateTime(2026, 11, 9, 0, 0), LocalDateTime(2026, 11, 10, 0, 0), 0).none { it.at == LocalDateTime(2026, 11, 9, 15, 0) })
        // One clock time for all of them (saved by an older version): not spread, so it moves like any timed goal.
        val stretch = Goal(id = 3, kind = GoalKind.HABIT, name = "Stretch", icon = "🧘", points = 1, repeat = Repeat.everyDay(monday), target = 3, at = LocalTime(8, 0))
        assertTrue(!Retimes.keepsTimes(stretch))
        assertEquals(LocalTime(18, 0), Retimes.on(stretch.copy(repeat = Retimes.set(stretch.repeat!!, monday, 18 * 60, monday)), monday).at)
        assertTrue(Retimes.keepsTimes(meds) && Retimes.keepsTimes(water))
    }

    @Test
    fun theTimelineRunsToTheEndOfTheUsersDay() {
        // The day ends at 4 am: 1 am is the night after, at the end of the day.
        assertEquals(25 * 60, DayHours.minuteOf(LocalTime(1, 0), 4))
        assertEquals(9 * 60, DayHours.minuteOf(LocalTime(9, 0), 4))
        assertEquals(60, DayHours.minuteOf(LocalTime(1, 0), 0))
        assertEquals(28, DayHours.endHour(4))
        // It starts when the day starts, or earlier for an earlier goal, and a 1 am goal doesn't pull it back.
        assertEquals(6, DayHours.startHour(6, listOf(25 * 60, 9 * 60), 4))
        assertEquals(5, DayHours.startHour(6, listOf(5 * 60 + 30), 4))
        assertEquals(4, DayHours.startHour(3, emptyList(), 4))
        assertEquals(6, DayHours.startHour(6, emptyList(), 0))
        // Dragged past midnight it lands in the small hours, snapped to a quarter hour, never past the day's end.
        assertEquals(60 + 15, DayHours.drop(25 * 60 + 10, 6, 4))
        assertEquals(3 * 60 + 45, DayHours.drop(30 * 60, 6, 4))
        assertEquals(23 * 60 + 45, DayHours.drop(24 * 60 + 30, 6, 0))
        assertEquals(6 * 60, DayHours.drop(2 * 60, 6, 0))
    }
}
