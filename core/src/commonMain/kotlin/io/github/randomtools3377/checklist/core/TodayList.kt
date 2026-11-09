package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * A habit that is due now, with how many times it has been ticked in its current [window], and
 * how many skips it has there. A skip covers the whole day for a single-day habit, or one of the
 * times for a "times per" habit.
 */
data class HabitRow(
    val goal: Goal,
    val count: Int,
    val window: Window,
    val skips: Int = 0,
    /** A routine's checklist, each item with whether it is ticked in this window. Empty for other habits. */
    val items: List<StepRow> = emptyList(),
    /** For a goal with an amount: how much has been entered in this window. */
    val progress: Double = 0.0,
) {
    /** For a goal with an amount: how much is left to do in this window. */
    val amountLeft: Double get() = ((goal.amount ?: 0.0) - progress).coerceAtLeast(0.0)

    val isRoutine: Boolean get() = items.isNotEmpty()
    val skipped: Boolean get() = skips > 0 && count < goal.target
    val done: Boolean
        get() = count >= goal.target || if (window.period == Period.DAILY) skips > 0 else count + skips >= goal.target
    val left: Int get() = if (done) 0 else (goal.target - count - skips).coerceAtLeast(0)
}

data class StepRow(val step: Step, val done: Boolean)

/** A project on today's list. [finishedOn] is the day its last step was done, if it is finished. */
data class ProjectRow(val goal: Goal, val steps: List<StepRow>, val finishedOn: LocalDate?) {
    val doneCount: Int get() = steps.count { it.done }
    val left: Int get() = steps.size - doneCount
    val finished: Boolean get() = finishedOn != null
}

data class TodayView(
    val day: LocalDate,
    val balance: Int,
    /** Habits due on this day: every-day, chosen weekdays, dates of the month, yearly, once. */
    val dueToday: List<HabitRow>,
    /** "Times per week" habits, and so on. */
    val thisWeek: List<HabitRow>,
    val thisMonth: List<HabitRow>,
    val thisYear: List<HabitRow>,
    val projects: List<ProjectRow>,
) {
    val isEmpty: Boolean
        get() = dueToday.isEmpty() && thisWeek.isEmpty() && thisMonth.isEmpty() && thisYear.isEmpty() && projects.isEmpty()
}

object TodayList {
    /**
     * Today's list: each habit that is due now with its count for the current window, and each
     * project that is not finished, or was finished today. Habits are in the user's own order.
     */
    fun build(
        day: LocalDate,
        goals: List<Goal>,
        steps: List<Step>,
        ticks: List<Tick>,
        balance: Int,
        lapses: List<Lapse> = emptyList(),
        /** Routine items ticked; only those in each routine's current window count. */
        checks: List<Check> = emptyList(),
    ): TodayView {
        val active = goals.filter { !it.archived }.sortedWith(compareBy({ it.position }, { it.id }))
        val habits = active.filter { it.kind == GoalKind.HABIT }.mapNotNull { goal ->
            val window = goal.repeat?.window(day) ?: return@mapNotNull null
            val skips = lapses.count { it.goalId == goal.id && it.kind == LapseKind.SKIP && it.periodStart == window.start && !it.forgotten }
            val windowTicks = ticks.filter { it.countsToward(goal, window.start) }
            val count = goal.timesDone(windowTicks)
            // A routine's items: ticked if ticked in this window, or all of them once it is done.
            val ticked = checks.filter { it.goalId == goal.id && it.periodStart == window.start }.map { it.stepId }.toSet()
            val items = steps.filter { it.goalId == goal.id && !it.archived }
                .sortedWith(compareBy({ it.position }, { it.id }))
                .map { StepRow(it, it.id in ticked || count >= goal.target) }
            HabitRow(goal, count, window, skips, items, progress = if (goal.hasAmount) goal.progressOf(windowTicks) else 0.0)
        }
        val stepTicks = ticks.filter { it.stepId != null }.associateBy { it.stepId }
        val projects = active.filter { it.kind == GoalKind.PROJECT }.map { goal ->
            val rows = steps.filter { it.goalId == goal.id && !it.archived }
                .sortedWith(compareBy({ it.position }, { it.id }))
                .map { StepRow(it, stepTicks.containsKey(it.id)) }
            val finishedOn = if (rows.isNotEmpty() && rows.all { it.done }) {
                rows.mapNotNull { stepTicks[it.step.id]?.day }.maxOrNull()
            } else {
                null
            }
            ProjectRow(goal, rows, finishedOn)
        }.filter { it.finishedOn == null || it.finishedOn == day }

        return TodayView(
            day = day,
            balance = balance,
            dueToday = habits.filter { it.window.period == Period.DAILY },
            thisWeek = habits.filter { it.window.period == Period.WEEKLY },
            thisMonth = habits.filter { it.window.period == Period.MONTHLY },
            thisYear = habits.filter { it.window.period == Period.YEARLY },
            projects = projects,
        )
    }
}
