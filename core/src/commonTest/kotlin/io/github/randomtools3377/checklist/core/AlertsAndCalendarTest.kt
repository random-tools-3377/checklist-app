package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlertsTest {
    private val monday = LocalDate(2026, 11, 9)
    private val start = LocalDate(2026, 1, 1)

    private fun habit(id: Long, repeat: Repeat, at: LocalTime? = null, reminder: Reminder? = null) = Goal(
        id = id, kind = GoalKind.HABIT, name = "G$id", icon = "⭐", points = 1, repeat = repeat, at = at, reminder = reminder,
    )

    @Test
    fun aClockTimeNotifiesOnTimeAndOptionallyBefore() {
        val gym = habit(1, Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1, 3)), at = LocalTime(18, 0), reminder = Reminder(minutesBefore = 10))
        val alerts = Alerts.between(listOf(gym), LocalDateTime(2026, 11, 9, 0, 0), LocalDateTime(2026, 11, 12, 0, 0), dayEndHour = 0)
        assertEquals(
            listOf(
                LocalDateTime(2026, 11, 9, 17, 50) to AlertKind.BEFORE,
                LocalDateTime(2026, 11, 9, 18, 0) to AlertKind.ON_TIME,
                LocalDateTime(2026, 11, 11, 17, 50) to AlertKind.BEFORE,
                LocalDateTime(2026, 11, 11, 18, 0) to AlertKind.ON_TIME,
            ),
            alerts.map { it.at to it.kind },
        )
    }

    @Test
    fun onTimeCanBeTurnedOffAndUntimedHabitsNeedAReminderTime() {
        val quiet = habit(1, Repeat.everyDay(start), at = LocalTime(7, 0), reminder = Reminder(onTime = false))
        val untimed = habit(2, Repeat.everyDay(start))
        val reminded = habit(3, Repeat.everyDay(start), reminder = Reminder(at = LocalTime(20, 30)))
        val alerts = Alerts.between(listOf(quiet, untimed, reminded), LocalDateTime(2026, 11, 9, 0, 0), LocalDateTime(2026, 11, 10, 0, 0), 0)
        assertEquals(listOf(3L to LocalDateTime(2026, 11, 9, 20, 30)), alerts.map { it.goalId to it.at })
    }

    @Test
    fun aLateNightTimeBelongsToTheDayBefore() {
        val night = habit(1, Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1)), at = LocalTime(1, 0))
        val alerts = Alerts.between(listOf(night), LocalDateTime(2026, 11, 9, 12, 0), LocalDateTime(2026, 11, 11, 0, 0), dayEndHour = 4)
        // Monday's 1am with the day ending at 4am is Tuesday 1am on the clock.
        assertEquals(listOf(Alert(1, monday, LocalDateTime(2026, 11, 10, 1, 0), AlertKind.ON_TIME)), alerts)
    }
}

class CalendarTest {
    private val start = LocalDate(2026, 1, 1)
    private val wednesday = LocalDate(2026, 11, 11)

    private fun tick(goalId: Long, day: LocalDate) =
        Tick(goalId = goalId, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day)

    @Test
    fun theAgendaPutsTimedHabitsFirstInTimeOrder() {
        val a = Goal(id = 1, kind = GoalKind.HABIT, name = "A", icon = "⭐", points = 1, repeat = Repeat.everyDay(start), position = 0)
        val b = Goal(id = 2, kind = GoalKind.HABIT, name = "B", icon = "⭐", points = 1, repeat = Repeat.everyDay(start), at = LocalTime(18, 0))
        val c = Goal(id = 3, kind = GoalKind.HABIT, name = "C", icon = "⭐", points = 1, repeat = Repeat.everyDay(start), at = LocalTime(7, 0))
        val gym = Goal(id = 4, kind = GoalKind.HABIT, name = "Gym", icon = "⭐", points = 1, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 3)
        val agenda = CalendarView.agenda(wednesday, listOf(a, b, c, gym), listOf(tick(1, wednesday)))
        assertEquals(listOf("C", "B", "A", "Gym"), agenda.map { it.goal.name })
        assertTrue(agenda[2].done)
    }

    @Test
    fun theWeekAndMonthCountDueAndDoneSingleDayHabits() {
        val daily = Goal(id = 1, kind = GoalKind.HABIT, name = "D", icon = "⭐", points = 1, repeat = Repeat.everyDay(start))
        val mondays = Goal(id = 2, kind = GoalKind.HABIT, name = "M", icon = "⭐", points = 1, repeat = Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1)))
        val ticks = listOf(tick(1, LocalDate(2026, 11, 9)), tick(2, LocalDate(2026, 11, 9)), tick(1, LocalDate(2026, 11, 10)))

        val week = CalendarView.week(wednesday, listOf(daily, mondays), ticks)
        assertEquals(LocalDate(2026, 11, 9), week.first().date)
        assertEquals(listOf(2 to 2, 1 to 1, 1 to 0), week.take(3).map { it.due to it.done })
        assertTrue(week[2].isToday)

        val month = CalendarView.month(wednesday, listOf(daily), ticks)
        assertEquals(42, month.size)
        assertEquals(LocalDate(2026, 10, 26), month.first().date) // Monday before 1 November
        assertTrue(!month.first().inMonth && month[6].inMonth)
    }
}
