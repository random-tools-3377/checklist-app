package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one order Today, the Today widget, the quick settings tile and the morning plan all ask for. */
class DayOrderTest {
    private val day = LocalDate(2026, 11, 11)
    private val twoPm = LocalDateTime(2026, 11, 11, 14, 0)
    private val tenPm = PaceSettings(dayEndHour = 0, defaultFinish = LocalTime(22, 0))

    private fun row(
        id: Long, done: Boolean = false, skipped: Boolean = false, minutes: Int? = null, at: LocalTime? = null,
        amount: Double? = null, progress: Double = 0.0,
    ) = HabitRow(
        Goal(
            id = id, kind = GoalKind.HABIT, name = "G$id", icon = "⭐", points = 1, repeat = Repeat.everyDay(day),
            minutes = minutes, at = at, position = id.toInt(), amount = amount, unit = amount?.let { "pages" },
        ),
        if (done) 1 else 0,
        Window(day, day, Period.DAILY),
        skips = if (skipped) 1 else 0,
        progress = progress,
    )

    private fun ids(order: DayOrdered) = order.planned.map { it.row.goal.id }

    @Test
    fun theUsersOwnOrderIsKeptAndNextIsTheFirstStillToDo() {
        val rows = listOf(row(1, done = true), row(2, skipped = true), row(3), row(4, at = LocalTime(9, 0)))
        val order = DayOrder.of(twoPm, day, rows, tenPm, own = true)
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(order))
        assertEquals(3L, order.next?.row?.goal?.id)
        // One with a clock time has it, and is late once it has passed.
        assertTrue(order.planned[3].fixed && order.planned[3].late)
    }

    @Test
    fun theRecommendedOrderUsesTheUsualTimes() {
        val rows = listOf(row(1), row(2), row(3))
        // 1 is usually done at 8 pm, 2 at 10 am (passed), 3 at 3 pm.
        val usual = mapOf(1L to 20 * 60, 2L to 10 * 60, 3L to 15 * 60)
        val order = DayOrder.of(twoPm, day, rows, tenPm, own = false, usualMinute = usual)
        assertEquals(listOf(2L, 3L, 1L), ids(order))
        assertEquals(2L, order.next?.row?.goal?.id)
        // The user's own order ignores them.
        assertEquals(1L, DayOrder.of(twoPm, day, rows, tenPm, own = true, usualMinute = usual).next?.row?.goal?.id)
    }

    @Test
    fun nothingIsNextOnceEverythingIsDoneOrSkipped() {
        val rows = listOf(row(1, done = true), row(2, skipped = true))
        assertNull(DayOrder.of(twoPm, day, rows, tenPm, own = false).next)
        assertNull(DayOrder.of(twoPm, day, rows, tenPm, own = true).next)
    }

    @Test
    fun aGoalWithAnAmountTakesAsLongAsWhatIsLeftOfIt() {
        // 20 pages, 5 read: the 15 left at 2 minutes a page (from a timing) take half an hour.
        val reading = row(1, amount = 20.0, progress = 5.0)
        val timing = Tick(goalId = 1, stepId = null, at = 0, day = day.minusDays(1), period = Period.DAILY, periodStart = day.minusDays(1), amount = 10.0, seconds = 1200)
        val order = DayOrder.of(twoPm, day, listOf(reading, row(2, minutes = 45)), tenPm, own = false, ticks = listOf(timing))
        assertEquals(30, order.rows.first().goal.minutes)
        // Quick ones before long ones: the reading (30 min) before the 45-minute goal.
        assertEquals(listOf(1L, 2L), ids(order))
        // Without the timing nothing is known about it, and it counts as untimed.
        assertNull(DayOrder.of(twoPm, day, listOf(reading), tenPm, own = false).rows.first().goal.minutes)
    }

    @Test
    fun aTypedTimeForTheWholeAmountIsSharedOut() {
        val reading = row(1, amount = 20.0, progress = 10.0, minutes = 40)
        assertEquals(20, DayOrder.timed(listOf(reading), emptyList()).single().goal.minutes)
    }

    @Test
    fun busyTimeFromTheCalendarIsSteppedAroundAndCountedInThePace() {
        // A 30-minute goal and one at 3 pm. Free, the first is done by 2:30, before the 3 pm one.
        val rows = listOf(row(1, minutes = 30), row(2, at = LocalTime(15, 0), minutes = 15))
        val free = DayOrder.of(twoPm, day, rows, tenPm, own = false)
        assertEquals(listOf(1L, 2L), ids(free))
        assertEquals(LocalDateTime(2026, 11, 11, 14, 30), free.planned[0].time)
        // With a meeting from 2:15 to 3:15, it can't be finished before 3: the 3 pm goal is next.
        val meeting = Busy(LocalDateTime(2026, 11, 11, 14, 15), LocalDateTime(2026, 11, 11, 15, 15), "Meeting")
        val busy = DayOrder.of(twoPm, day, rows, tenPm, own = false, busy = listOf(meeting, meeting.copy(title = "The same again")))
        assertEquals(listOf(2L, 1L), ids(busy))
        assertEquals(2L, busy.next?.row?.goal?.id)
        // Stepped around once, though two events cover the same hour; then the 15 minutes of the 3 pm goal.
        assertEquals(LocalDateTime(2026, 11, 11, 15, 45), busy.planned[1].time)
        assertEquals(60, busy.pace?.busyMinutes)
        // An all-day event and another day's event take no time.
        val other = listOf(
            Busy(LocalDateTime(2026, 11, 11, 0, 0), LocalDateTime(2026, 11, 12, 0, 0), "Holiday", allDay = true),
            Busy(LocalDateTime(2026, 11, 12, 14, 15), LocalDateTime(2026, 11, 12, 15, 15), "Tomorrow"),
        )
        assertEquals(listOf(1L, 2L), ids(DayOrder.of(twoPm, day, rows, tenPm, own = false, busy = other)))
    }

    @Test
    fun theMorningPlanStartsWithTheSameGoal() {
        val rows = listOf(row(1), row(2), row(3))
        val usual = mapOf(1L to 20 * 60, 2L to 10 * 60, 3L to 15 * 60)
        val view = TodayView(day, 0, rows, emptyList(), emptyList(), emptyList(), emptyList())
        for (own in listOf(true, false)) {
            val order = DayOrder.of(twoPm, day, rows, tenPm, own, usual)
            assertEquals(order.next, MorningPlan.of(twoPm, view, tenPm, usual, own)?.first)
            assertEquals(order.next, MorningPlan.of(view, order)?.first)
        }
        assertEquals(1L, MorningPlan.of(twoPm, view, tenPm, usual, own = true)?.first?.row?.goal?.id)
        assertEquals(2L, MorningPlan.of(twoPm, view, tenPm, usual, own = false)?.first?.row?.goal?.id)
    }
}
