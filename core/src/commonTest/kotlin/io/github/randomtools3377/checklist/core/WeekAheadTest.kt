package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekAheadTest {
    // A Wednesday.
    private val wednesday = LocalDate(2026, 11, 11)

    private fun goal(id: Long, kind: GoalKind = GoalKind.HABIT, deadline: LocalDate? = null) =
        Goal(id = id, kind = kind, name = "G$id", icon = "⭐", points = 1, repeat = Repeat.everyDay(wednesday), deadline = deadline)

    private fun row(id: Long, on: LocalDate, kind: GoalKind = GoalKind.HABIT, count: Int = 0) =
        HabitRow(goal(id, kind), count, Window(on, on, Period.DAILY))

    private fun view(day: LocalDate, due: List<HabitRow> = emptyList(), week: List<HabitRow> = emptyList(), projects: List<ProjectRow> = emptyList()) =
        TodayView(day, 0, due, week, emptyList(), emptyList(), projects)

    @Test
    fun itRunsFromTodayToSundayWithFlexibleGoalsDeadlinesToDosAndEvents() {
        val monday = wednesday.minusDays(2)
        val twice = HabitRow(goal(5).copy(target = 3), 1, Window(monday, monday.plusDays(6), Period.WEEKLY))
        val friday = wednesday.plusDays(2)
        val projects = listOf(
            ProjectRow(goal(7, GoalKind.PROJECT, deadline = wednesday.plusDays(4)), emptyList(), null),
            ProjectRow(goal(6, GoalKind.PROJECT, deadline = friday), emptyList(), null),
            // Next week's, a finished one and one with no deadline are left out.
            ProjectRow(goal(8, GoalKind.PROJECT, deadline = wednesday.plusDays(5)), emptyList(), null),
            ProjectRow(goal(9, GoalKind.PROJECT, deadline = friday), emptyList(), wednesday),
            ProjectRow(goal(10, GoalKind.PROJECT), emptyList(), null),
        )
        val days = (0 until 14).map { d ->
            val on = wednesday.plusDays(d)
            view(
                on,
                due = listOf(row(1, on)) +
                    (if (on == friday) listOf(row(2, on, GoalKind.TODO), row(3, on, GoalKind.TODO, count = 1)) else emptyList()) +
                    (if (d == 6) listOf(row(4, on, GoalKind.TODO)) else emptyList()),
                week = listOf(twice),
                projects = projects,
            )
        }
        val events = listOf(
            Busy(LocalDateTime(2026, 11, 12, 9, 0), LocalDateTime(2026, 11, 12, 10, 0), "Dentist"),
            Busy(LocalDateTime(2026, 11, 13, 0, 0), LocalDateTime(2026, 11, 14, 0, 0), "Day out", allDay = true),
            Busy(LocalDateTime(2026, 11, 17, 9, 0), LocalDateTime(2026, 11, 17, 10, 0), "Next week"),
        )
        val week = WeekAhead.of(days, events, dayEndHour = 0)
        assertEquals((0..4).map { wednesday.plusDays(it) }, week.days.map { it.day })
        assertEquals(listOf(5L), week.flexible.map { it.goal.id })
        assertEquals(2, week.flexible.single().left)
        assertEquals(listOf(6L, 7L), week.deadlines.map { it.goal.id })
        // The to-do still open this week, with its day; not the done one, nor next week's.
        assertEquals(listOf(friday to 2L), week.todos.map { it.first to it.second.goal.id })
        assertTrue(week.hasEvents)
        assertEquals(listOf(emptyList(), listOf("Dentist"), listOf("Day out"), emptyList(), emptyList()), week.days.map { day -> day.events.map { it.title } })
    }

    @Test
    fun withoutTheCalendarThereAreNoEventsAndNoDaysMeansNothing() {
        assertFalse(WeekAhead.of(listOf(view(wednesday)), emptyList(), 0).hasEvents)
        assertTrue(WeekAhead.of(emptyList(), emptyList(), 0).days.isEmpty())
    }
}
