package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * A habit that is due now, with how many times it has been ticked in its current [window], and
 * how many skips it has there. A skip covers the whole day for a single-day habit, or one of the
 * times for a "times per" habit. A skip is never counted as done: [done] is ticked, and
 * [settled] is "nothing left to do", done or skipped.
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
    /** Of [skips], those made on the day shown, which can still be taken back (see [Lapses.takeBackSkip]). */
    val skipsToday: Int = 0,
    /** For an "at most" goal: it went over its limit in this window, which is a miss already. */
    val slipped: Boolean = false,
) {
    /** For an "at most" goal: [progress], what was used, is over the limit. */
    val overLimit: Boolean get() = goal.limit?.let { AtMost.over(progress, it) } == true

    /** For a goal with an amount: how much is left to do in this window, every time of it. */
    val amountLeft: Double get() = ((goal.amount ?: 0.0) * goal.target - progress).coerceAtLeast(0.0)

    val isRoutine: Boolean get() = items.isNotEmpty()
    val skipped: Boolean get() = skips > 0 && !done

    /** Really done: ticked as often as it needs. A skip never makes it done. */
    val done: Boolean get() = count >= goal.target

    /** Nothing left to do in this window: done, or the rest of it skipped. */
    val settled: Boolean
        get() = done || if (window.period == Period.DAILY) skips > 0 else count + skips >= goal.target
    val left: Int get() = if (settled) 0 else (goal.target - count - skips).coerceAtLeast(0)

    /** It has a skip made today, which can be taken back until the day ends. */
    val canTakeBackSkip: Boolean get() = skipsToday > 0
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
    /** To-dos without a date: open ones, and ones done today. */
    val todos: List<TodoRow> = emptyList(),
    /**
     * "At most" goals due on this day (see [AtMost]), with what was used as [HabitRow.progress].
     * They are kept apart from [dueToday]: there is nothing to do for one, so it is never what's
     * next, never paced or reminded of, and never holds back "everything done".
     */
    val limits: List<HabitRow> = emptyList(),
) {
    val isEmpty: Boolean
        get() = limits.isEmpty() && dueToday.isEmpty() && thisWeek.isEmpty() && thisMonth.isEmpty() && thisYear.isEmpty() && projects.isEmpty() && todos.isEmpty()
}

/** An undated to-do on Today: open, or done today, with its steps if it has any. */
data class TodoRow(val goal: Goal, val done: Boolean, val items: List<StepRow> = emptyList())

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
        val all = active.filter { it.isHabitLike }.map { Retimes.on(it, day) }.mapNotNull { goal ->
            val window = goal.repeat?.window(day) ?: doneTodayAfterDone(goal, day, ticks) ?: return@mapNotNull null
            val skipped = lapses.filter { it.goalId == goal.id && it.kind == LapseKind.SKIP && it.periodStart == window.start && !it.forgotten }
            val skips = skipped.size
            val windowTicks = ticks.filter { it.countsToward(goal, window.start) }
            val count = goal.timesDone(windowTicks)
            // A routine's items: ticked if ticked in this window, or all of them once it is done.
            // A to-do's steps stay ticked until it is done, like a project's.
            val ticked = if (goal.kind == GoalKind.TODO) {
                ticks.filter { it.goalId == goal.id && it.stepId != null }.mapNotNull { it.stepId }.toSet()
            } else {
                checks.filter { it.goalId == goal.id && it.periodStart == window.start }.map { it.stepId }.toSet()
            }
            val items = steps.filter { it.goalId == goal.id && !it.archived }
                .sortedWith(compareBy({ it.position }, { it.id }))
                .map { StepRow(it, it.id in ticked || count >= goal.target) }
            HabitRow(
                goal, count, window, skips, items,
                progress = when {
                    goal.atMost -> AtMost.used(goal, ticks, window.start)
                    goal.hasAmount -> goal.progressOf(windowTicks)
                    else -> 0.0
                },
                skipsToday = skipped.count { it.day == day },
                slipped = goal.atMost &&
                    lapses.any { it.goalId == goal.id && it.kind == LapseKind.MISS && it.periodStart == window.start && !it.forgotten },
            )
        }
        val (limits, habits) = all.partition { it.goal.atMost }
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
            todos = active.filter { it.kind == GoalKind.TODO && it.repeat == null }.map { goal ->
                val done = Todos.doneOn(goal, day, ticks)
                val items = steps.filter { it.goalId == goal.id && !it.archived }
                    .sortedWith(compareBy({ it.position }, { it.id }))
                    .map { StepRow(it, stepTicks.containsKey(it.id) || done) }
                TodoRow(goal, done, items)
            },
            limits = limits,
        )
    }
}

/**
 * A "since last done" goal done on [day] has already moved on to its next day; it still shows on
 * [day], done, in the window its tick went to.
 */
private fun doneTodayAfterDone(goal: Goal, day: LocalDate, ticks: List<Tick>): Window? {
    if (goal.repeat?.kind != RepeatKind.AFTER_DONE) return null
    val tick = ticks.firstOrNull { it.goalId == goal.id && it.stepId == null && it.day == day } ?: return null
    return Window(tick.periodStart, tick.periodStart, Period.DAILY)
}
