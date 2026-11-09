package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyAndHoursTest {
    @Test
    fun everyFewHoursFillsTheTimes() {
        assertEquals(listOf(LocalTime(8, 0), LocalTime(10, 0), LocalTime(12, 0), LocalTime(14, 0)), Pace.everyFewHours(LocalTime(8, 0), 2, 4))
        // None past midnight.
        assertEquals(listOf(LocalTime(20, 30), LocalTime(23, 30)), Pace.everyFewHours(LocalTime(20, 30), 3, 5))
    }

    @Test
    fun theWeeklySummaryComesOnSundayEvening() {
        // 9 Nov 2026 is a Monday.
        val alerts = Alerts.weekly(LocalDateTime(2026, 11, 9, 0, 0), LocalDateTime(2026, 11, 23, 0, 0), WeeklySummary.AT, dayEndHour = 0)
        assertEquals(listOf(LocalDateTime(2026, 11, 15, 18, 0), LocalDateTime(2026, 11, 22, 18, 0)), alerts.map { it.at })
        assertEquals(setOf(AlertKind.WEEKLY), alerts.map { it.kind }.toSet())
    }

    @Test
    fun theWeeklySummarySaysHowItWent() {
        val day = LocalDate(2026, 11, 9)
        val water = Goal(id = 1, kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 1, repeat = Repeat.everyDay(day))
        val run = Goal(id = 2, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 5, repeat = Repeat.everyDay(day))
        val review = ReviewSummary(
            from = day, to = day.plusDays(6),
            goals = listOf(GoalSummary(water, 7, 7, 0, 0, 7, 7, 7, 7), GoalSummary(run, 7, 4, 1, 2, 4, 20, 2, 0)),
            reasons = emptyList(), earned = 27, spent = 0, lost = 5, days = emptyList(), gaps = emptyList(), weekdays = emptyMap(),
        )
        assertEquals("11 of 14 done (78%) · +27 points · −5 lost · 2 missed · 1 skipped · best: 💧 Water", WeeklySummary.of(review).describe())
        assertEquals("11 of 14 done (78%) · +27 points · 🎯 27 of 50 · −5 lost · 2 missed · 1 skipped · best: 💧 Water", WeeklySummary.of(review).describe(target = 50))
        assertEquals("11 of 14 done (78%) · +27 points · 🎯 target reached · −5 lost · 2 missed · 1 skipped · best: 💧 Water", WeeklySummary.of(review).describe(target = 20))
    }
}
