package io.github.randomtools3377.checklist.core

import java.time.LocalDate

/** A habit on today's list, with how many times it has been ticked this period. */
data class HabitRow(val goal: Goal, val count: Int) {
    val done: Boolean get() = count >= goal.target
}

data class StepRow(val step: Step, val done: Boolean)

/** A project on today's list. [finishedOn] is the day its last step was done, if it is finished. */
data class ProjectRow(val goal: Goal, val steps: List<StepRow>, val finishedOn: LocalDate?) {
    val doneCount: Int get() = steps.count { it.done }
    val finished: Boolean get() = finishedOn != null
}

data class TodayView(
    val day: LocalDate,
    val balance: Int,
    val daily: List<HabitRow>,
    val weekly: List<HabitRow>,
    val monthly: List<HabitRow>,
    val projects: List<ProjectRow>,
) {
    val isEmpty: Boolean get() = daily.isEmpty() && weekly.isEmpty() && monthly.isEmpty() && projects.isEmpty()
}

object TodayList {
    /**
     * Today's list: each habit with its count for the current period, and each project that is
     * not finished, or was finished today.
     */
    fun build(
        day: LocalDate,
        goals: List<Goal>,
        steps: List<Step>,
        ticks: List<Tick>,
        balance: Int,
    ): TodayView {
        val active = goals.filter { !it.archived }
        val habits = active.filter { it.kind == GoalKind.HABIT && it.period != null }.map { goal ->
            val start = Days.periodStart(day, goal.period!!)
            HabitRow(goal, ticks.count { it.countsToward(goal, start) })
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
            daily = habits.filter { it.goal.period == Period.DAILY },
            weekly = habits.filter { it.goal.period == Period.WEEKLY },
            monthly = habits.filter { it.goal.period == Period.MONTHLY },
            projects = projects,
        )
    }
}
