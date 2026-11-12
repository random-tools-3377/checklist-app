package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** A day of the week being planned: what is due on it, and its events in the phone's calendar. */
data class WeekAheadDay(val day: LocalDate, val due: List<HabitRow>, val events: List<Busy>)

/**
 * What "Plan your week" lists for the rest of the week, today to Sunday: each day, the flexible
 * goals (so many times a week) with how many are left, the project deadlines and the dated
 * to-dos that fall in it, and the calendar's events day by day.
 */
data class WeekAhead(
    val days: List<WeekAheadDay>,
    /** Goals due a number of times this week, on any days. */
    val flexible: List<HabitRow>,
    /** Unfinished projects with a deadline from today to Sunday, soonest first. */
    val deadlines: List<ProjectRow>,
    /** To-dos with a date from today to Sunday, still open, with their day. */
    val todos: List<Pair<LocalDate, HabitRow>>,
) {
    val hasEvents: Boolean get() = days.any { it.events.isNotEmpty() }

    companion object {
        /**
         * @param days today and the days after it, each as Today would show it (the planner's days).
         * @param events events from the phone's calendar; empty while it is off or not allowed.
         */
        fun of(days: List<TodayView>, events: List<Busy>, dayEndHour: Int): WeekAhead {
            val today = days.firstOrNull() ?: return WeekAhead(emptyList(), emptyList(), emptyList(), emptyList())
            val nextMonday = Boost.weekOf(today.day).plusDays(7)
            val rest = days.filter { it.day < nextMonday }
            return WeekAhead(
                days = rest.map { WeekAheadDay(it.day, it.dueToday, BusyTime.on(events, it.day, dayEndHour)) },
                flexible = today.thisWeek,
                deadlines = today.projects
                    .filter { project -> !project.finished && project.goal.deadline?.let { it >= today.day && it < nextMonday } == true }
                    .sortedBy { it.goal.deadline },
                todos = rest.flatMap { view -> view.dueToday.filter { it.goal.kind == GoalKind.TODO && !it.settled }.map { view.day to it } },
            )
        }
    }
}
