package io.github.randomtools3377.checklist.core

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class TodayListTest {
    private val wednesday = LocalDate(2026, 11, 11)

    private fun tick(goalId: Long, day: LocalDate, period: Period?, stepId: Long? = null) = Tick(
        goalId = goalId, stepId = stepId, at = 0, day = day, period = period,
        periodStart = if (period == null) day else Days.periodStart(day, period),
    )

    @Test
    fun habitsAreGroupedAndCountedForTheirCurrentPeriod() {
        val water = Goal(id = 1, kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 1, period = Period.DAILY, target = 8)
        val gym = Goal(id = 2, kind = GoalKind.HABIT, name = "Gym", icon = "🏋", points = 10, period = Period.WEEKLY, target = 3)
        val ticks = listOf(
            tick(1, wednesday, Period.DAILY),
            tick(1, wednesday, Period.DAILY),
            tick(1, wednesday.minusDays(1), Period.DAILY), // yesterday: not today's
            tick(2, wednesday.minusDays(2), Period.WEEKLY), // Monday: same week
        )

        val view = TodayList.build(wednesday, listOf(water, gym), emptyList(), ticks, balance = 12)

        assertEquals(listOf(HabitRow(water, 2)), view.daily)
        assertEquals(listOf(HabitRow(gym, 1)), view.weekly)
        assertTrue(view.monthly.isEmpty())
        assertEquals(12, view.balance)
    }

    @Test
    fun finishedProjectsStayOnTheListOnlyForTheDayTheyWereFinished() {
        val project = Goal(id = 1, kind = GoalKind.PROJECT, name = "Shelf", icon = "🔨", points = 0, period = null)
        val steps = listOf(Step(id = 10, goalId = 1, name = "Buy", points = 5, position = 0))
        val ticks = listOf(tick(1, wednesday, null, stepId = 10))

        val sameDay = TodayList.build(wednesday, listOf(project), steps, ticks, 0)
        assertEquals(wednesday, sameDay.projects.single().finishedOn)

        val nextDay = TodayList.build(wednesday.plusDays(1), listOf(project), steps, ticks, 0)
        assertTrue(nextDay.projects.isEmpty())
    }

    @Test
    fun removedGoalsAndStepsAreLeftOut() {
        val gone = Goal(id = 1, kind = GoalKind.HABIT, name = "Old", icon = "⭐", points = 1, period = Period.DAILY, archived = true)
        val project = Goal(id = 2, kind = GoalKind.PROJECT, name = "P", icon = "⭐", points = 0, period = null)
        val steps = listOf(
            Step(id = 10, goalId = 2, name = "Kept", points = 1, position = 1),
            Step(id = 11, goalId = 2, name = "Removed", points = 1, position = 0, archived = true),
        )

        val view = TodayList.build(wednesday, listOf(gone, project), steps, emptyList(), 0)

        assertTrue(view.daily.isEmpty())
        assertEquals(listOf("Kept"), view.projects.single().steps.map { it.step.name })
    }
}
