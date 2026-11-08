package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderTest {
    private val day = LocalDate(2026, 11, 11)
    private val twoPm = LocalDateTime(2026, 11, 11, 14, 0)
    private val tenPm = PaceSettings(dayEndHour = 0, defaultFinish = LocalTime(22, 0))

    private fun row(id: Long, done: Boolean = false, minutes: Int? = null, at: LocalTime? = null, finishBy: LocalTime? = null, position: Int = 0) = HabitRow(
        Goal(
            id = id, kind = GoalKind.HABIT, name = "G$id", icon = "⭐", points = 1, repeat = Repeat.everyDay(day),
            minutes = minutes, at = at, finishBy = finishBy, position = position,
        ),
        if (done) 1 else 0,
        Window(day, day, Period.DAILY),
    )

    private fun order(rows: List<HabitRow>, usual: Map<Long, Int> = emptyMap()): List<Long> {
        val pace = Pace.today(twoPm, day, rows, tenPm)
        return Order.recommend(twoPm, day, rows, tenPm, usual, pace).map { it.row.goal.id }
    }

    @Test
    fun anEarlierFinishByComesFirst() {
        assertEquals(listOf(2L, 1L), order(listOf(row(1), row(2, finishBy = LocalTime(16, 0)))))
    }

    @Test
    fun goalsUsuallyDoneByNowMoveUpThenTheUsualTimeOfDay() {
        // 1 is usually done at 8pm, 2 at 10am (already passed), 3 at 3pm.
        val usual = mapOf(1L to 20 * 60, 2L to 10 * 60, 3L to 15 * 60)
        assertEquals(listOf(2L, 3L, 1L), order(listOf(row(1), row(2), row(3)), usual))
    }

    @Test
    fun quickOnesBeforeLongOnes() {
        assertEquals(listOf(2L, 1L), order(listOf(row(1, minutes = 90), row(2, minutes = 10))))
    }

    @Test
    fun aClockTimeGoalSitsAtItsTimeAndDoneGoalsGoLast() {
        // Three untimed share 420 minutes, 140 each: by 4:20, then the 5pm block, then 7:40 and 10pm.
        val rows = listOf(row(1), row(2), row(3, at = LocalTime(17, 0), minutes = 60), row(4), row(5, done = true))
        val planned = Order.recommend(twoPm, day, rows, tenPm, emptyMap(), Pace.today(twoPm, day, rows, tenPm))
        assertEquals(listOf(1L, 3L, 2L, 4L, 5L), planned.map { it.row.goal.id })
        assertTrue(planned[1].fixed)
        assertEquals(LocalDateTime(2026, 11, 11, 17, 0), planned[1].time)
    }

    @Test
    fun theUsualMinuteIsTheMiddleTime() {
        assertEquals(600, usualMinuteOfDay(listOf(900, 540, 600)))
    }
}
