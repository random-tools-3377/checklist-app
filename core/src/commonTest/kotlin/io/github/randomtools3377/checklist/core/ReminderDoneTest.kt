package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reminders stop by the same rule Today uses to show a goal done. */
class ReminderDoneTest {
    // 9 Nov 2026 is a Monday.
    private val day = LocalDate(2026, 11, 9)

    private fun tick(goal: Goal, window: Window, amount: Double? = null, at: Long = 0) =
        Tick(goalId = goal.id, stepId = null, at = at, day = day, period = window.period, periodStart = window.start, amount = amount)

    private fun skip(goal: Goal, window: Window, forgotten: Boolean = false) = Lapse(
        goalId = goal.id, kind = LapseKind.SKIP, day = day, period = window.period, periodStart = window.start,
        at = 0, overLimit = false, forgotten = forgotten, label = goal.name,
    )

    @Test
    fun anAmountIsRemindedUntilAllOfItIsEntered() {
        val read = Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(day), amount = 20.0, unit = "pages")
        val window = read.repeat!!.window(day)!!
        assertTrue(Alerts.stillToDo(read, window, listOf(tick(read, window, 5.0)), emptyList()))
        assertFalse(Alerts.stillToDo(read, window, listOf(tick(read, window, 5.0), tick(read, window, 15.0)), emptyList()))
        // Done several times: "1 of 3" counts whole amounts, not entries.
        val often = read.copy(target = 3)
        assertEquals(1, Alerts.timesDone(often, window, listOf(tick(often, window, 10.0), tick(often, window, 10.0), tick(often, window, 5.0))))
    }

    @Test
    fun aSkipCountsAsOneOfTheTimesOfATimesPerWeekGoal() {
        val gym = Goal(id = 2, kind = GoalKind.HABIT, name = "Gym", icon = "🏋", points = 5, repeat = Repeat.timesPer(Period.WEEKLY, day), target = 3)
        val window = gym.repeat!!.window(day)!!
        val twoTicks = listOf(tick(gym, window, at = 1), tick(gym, window, at = 2))
        assertTrue(Alerts.stillToDo(gym, window, twoTicks, emptyList()))
        assertFalse(Alerts.stillToDo(gym, window, twoTicks, listOf(skip(gym, window))))
        // A skip taken back as forgotten doesn't count.
        assertTrue(Alerts.stillToDo(gym, window, twoTicks, listOf(skip(gym, window, forgotten = true))))
    }

    @Test
    fun aSkipCoversTheDayAndEachTimeHasItsOwnReminder() {
        val pills = Goal(id = 3, kind = GoalKind.HABIT, name = "Pills", icon = "💊", points = 1, repeat = Repeat.everyDay(day), target = 2)
        val window = pills.repeat!!.window(day)!!
        assertFalse(Alerts.stillToDo(pills, window, emptyList(), listOf(skip(pills, window))))
        val one = listOf(tick(pills, window))
        assertFalse(Alerts.stillToDo(pills, window, one, emptyList(), number = 1))
        assertTrue(Alerts.stillToDo(pills, window, one, emptyList(), number = 2))
        // Another goal's ticks don't count.
        val other = pills.copy(id = 4)
        assertTrue(Alerts.stillToDo(other, window, one, emptyList(), number = 1))
    }

    @Test
    fun keepRemindingNeverCrowdsOutTheNextDaysReminders() {
        // Five goals at 9 am that keep reminding fill a day with 65 alerts.
        val goals = (1L..5L).map { id ->
            Goal(
                id = id, kind = GoalKind.HABIT, name = "Goal $id", icon = "⭐", points = 1, repeat = Repeat.everyDay(day),
                reminder = Reminder(at = kotlinx.datetime.LocalTime(9, 0), againEvery = Reminder.AGAIN_EVERY),
            )
        }
        val from = kotlinx.datetime.LocalDateTime(day, kotlinx.datetime.LocalTime(8, 0))
        val alerts = Alerts.between(goals, from, kotlinx.datetime.LocalDateTime(day.plusDays(3), kotlinx.datetime.LocalTime(8, 0)), dayEndHour = 0)
        assertTrue(alerts.count { it.day == day } > 60)
        val kept = Alerts.keep(alerts, 60)
        assertEquals(60, kept.size)
        assertEquals(kept.sortedBy { it.at }, kept)
        // Every first reminder of the three days is kept; the repeats fill what is left.
        assertEquals(15, kept.count { it.kind == AlertKind.REMINDER })
        assertEquals(setOf(day, day.plusDays(1), day.plusDays(2)), kept.filter { it.kind == AlertKind.REMINDER }.map { it.day }.toSet())
        assertEquals(alerts.filter { it.kind == AlertKind.AGAIN }.take(45), kept.filter { it.kind == AlertKind.AGAIN })
        // Fewer than fit: all of them.
        assertEquals(alerts.take(10), Alerts.keep(alerts.take(10), 60))
    }

    @Test
    fun aLockedReminderSaysOnlyWhen() {
        val at = kotlinx.datetime.LocalDateTime(day, kotlinx.datetime.LocalTime(8, 50))
        assertEquals("In 10 min", Alerts.lockedWords(Alert(1, day, at, AlertKind.BEFORE, minutesBefore = 10)))
        assertEquals("Due now", Alerts.lockedWords(Alert(1, day, at, AlertKind.ON_TIME)))
        assertEquals("Time's up", Alerts.lockedWords(Alert(1, day, at, AlertKind.FOCUS_DONE)))
    }

    @Test
    fun theWeeklySummaryNeverNamesALockedPrivateGoal() {
        val water = Goal(id = 1, kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 1, repeat = Repeat.everyDay(day))
        val secret = Goal(id = 2, kind = GoalKind.HABIT, name = "Secret", icon = "🤫", points = 5, repeat = Repeat.everyDay(day), private = true)
        val review = ReviewSummary(
            from = day, to = day.plusDays(6),
            goals = listOf(GoalSummary(water, 7, 5, 0, 2, 5, 5, 3, 3), GoalSummary(secret, 7, 7, 0, 0, 7, 35, 7, 7)),
            reasons = emptyList(), earned = 40, spent = 0, lost = 0, days = emptyList(), gaps = emptyList(), weekdays = emptyMap(),
        )
        assertEquals("best: 🤫 Secret", WeeklySummary.of(review).describe().substringAfterLast(" · "))
        val locked = WeeklySummary.of(review, hidePrivate = true).describe()
        assertEquals("best: 💧 Water", locked.substringAfterLast(" · "))
        assertFalse("Secret" in locked)
    }
}
