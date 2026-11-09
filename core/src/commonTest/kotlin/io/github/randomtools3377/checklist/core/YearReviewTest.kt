package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YearReviewTest {
    @Test
    fun eachMonthIsSummedUp() {
        val start = LocalDate(2026, 1, 1)
        val read = Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(start))
        val ticks = listOf(LocalDate(2026, 1, 5), LocalDate(2026, 1, 6), LocalDate(2026, 2, 1)).map {
            Tick(goalId = 1, stepId = null, at = 0, day = it, period = Period.DAILY, periodStart = it)
        }
        val entries = ticks.map { Entry(at = 0, day = it.day, kind = EntryKind.TICK, amount = 5, goalId = 1, label = "Read") }
        val (from, to) = Review.range(LocalDate(2026, 6, 1), Period.YEARLY)
        assertEquals(LocalDate(2026, 1, 1) to LocalDate(2026, 12, 31), from to to)
        val summary = Review.summarize(from = from, to = to, today = LocalDate(2026, 3, 1), goals = listOf(read), ticks = ticks, lapses = emptyList(), entries = entries, gaps = emptyList())
        val months = YearReview.months(summary, listOf(Mood(LocalDate(2026, 1, 5), 4), Mood(LocalDate(2026, 1, 6), 2)), 2026, today = LocalDate(2026, 3, 1))
        assertEquals(12, months.size)
        assertEquals(2, months[0].done)
        assertEquals(31, months[0].due)
        assertEquals(10, months[0].earned)
        assertEquals(3.0, months[0].mood)
        assertEquals(1, months[1].done)
        assertNull(months[5].share) // June hasn't come yet
    }
}
