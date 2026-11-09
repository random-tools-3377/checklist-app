package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

/** A habit on a day's agenda. [time] is its clock time, if it has one. */
data class AgendaItem(val goal: Goal, val count: Int, val time: LocalTime?) {
    val done: Boolean get() = count >= goal.target
}

/** One day in the calendar: how many single-day habits were due and how many got done. */
data class DayCell(val date: LocalDate, val due: Int, val done: Int, val inMonth: Boolean, val isToday: Boolean)

/** What the calendar widget shows: today's agenda, this week, and this month. */
object CalendarView {
    /**
     * The habits due on [day], timed ones first in time order, then the rest in the user's
     * order. "Times per" habits are included while their window is open.
     */
    fun agenda(day: LocalDate, goals: List<Goal>, ticks: List<Tick>): List<AgendaItem> {
        val items = goals
            .filter { !it.archived && it.isHabitLike }
            .mapNotNull { goal ->
                val window = goal.repeat?.window(day) ?: return@mapNotNull null
                val count = goal.timesDone(ticks.filter { it.countsToward(goal, window.start) })
                // A habit done several times a day shows its next set time, or its first.
                val times = goal.times.sorted()
                AgendaItem(goal, count, goal.at ?: times.getOrNull(count) ?: times.firstOrNull())
            }
        return items.sortedWith(compareBy({ it.time == null }, { it.time }, { it.goal.position }, { it.goal.id }))
    }

    /** Single-day habits due on [day], and how many of them were done. */
    private fun counts(day: LocalDate, goals: List<Goal>, ticksByDay: Map<LocalDate, List<Tick>>): Pair<Int, Int> {
        var due = 0
        var done = 0
        val ticks = ticksByDay[day].orEmpty()
        for (goal in goals) {
            if (goal.archived || !goal.isHabitLike) continue
            val repeat = goal.repeat ?: continue
            if (repeat.kind == RepeatKind.TIMES_PER || !repeat.isDueOn(day)) continue
            due++
            if (goal.timesDone(ticks.filter { it.countsToward(goal, day) }) >= goal.target) done++
        }
        return due to done
    }

    private fun cells(days: List<LocalDate>, month: Int?, today: LocalDate, goals: List<Goal>, ticks: List<Tick>): List<DayCell> {
        val ticksByDay = ticks.filter { it.stepId == null && it.period == Period.DAILY }.groupBy { it.periodStart }
        return days.map { day ->
            val (due, done) = counts(day, goals, ticksByDay)
            DayCell(day, due, done, inMonth = month == null || day.monthNumber == month, isToday = day == today)
        }
    }

    /** Monday to Sunday of the week containing [today]. */
    fun week(today: LocalDate, goals: List<Goal>, ticks: List<Tick>): List<DayCell> {
        val monday = today.minusDays(today.dayOfWeek.isoDayNumber - 1)
        return cells((0 until 7).map { monday.plusDays(it) }, null, today, goals, ticks)
    }

    /** Six weeks from the Monday on or before the 1st of [today]'s month. */
    fun month(today: LocalDate, goals: List<Goal>, ticks: List<Tick>): List<DayCell> {
        val first = LocalDate(today.year, today.monthNumber, 1)
        val start = first.minusDays(first.dayOfWeek.isoDayNumber - 1)
        return cells((0 until 42).map { start.plusDays(it) }, today.monthNumber, today, goals, ticks)
    }

    /** The first day the month grid shows, so the widget knows how far back to load ticks. */
    fun monthGridStart(today: LocalDate): LocalDate {
        val first = LocalDate(today.year, today.monthNumber, 1)
        return first.minusDays(first.dayOfWeek.isoDayNumber - 1)
    }
}
