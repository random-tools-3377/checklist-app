package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** A habit whose last day has passed, or a project with every step done, and the day that was. */
data class CompletedGoal(val goal: Goal, val day: LocalDate)

/**
 * Goals done and over with, which move out of the main Goals list into "Completed" at the
 * bottom: habits whose last day has passed, and finished projects. A habit can be brought back
 * by moving its end date on, and then it is back in the main list.
 */
object Completed {
    /**
     * The last day [goal], a habit, can come round on, once that day is before [today]; null
     * while it can still come round. A goal counted per week, month or year has until the end of
     * its last one; "some days after it's last done" goes on until it is given an end.
     */
    fun habitLastDay(goal: Goal, today: LocalDate): LocalDate? {
        if (goal.kind != GoalKind.HABIT) return null
        val repeat = goal.repeat ?: return null
        val moved = repeat.moves.maxOfOrNull { it.to }
        val last = when (repeat.kind) {
            RepeatKind.ONCE -> repeat.start
            RepeatKind.AFTER_DONE -> return null
            RepeatKind.TIMES_PER -> repeat.end?.let { Days.periodEnd(it, repeat.windowPeriod) } ?: return null
            else -> repeat.end ?: return null
        }.let { day -> if (moved != null && moved > day) moved else day }
        return last.takeIf { it < today }
    }

    /**
     * Splits [goals] into those still going, in their order, and those completed, newest first.
     * [finished] holds the day each finished project's last step was done.
     */
    fun split(goals: List<Goal>, finished: Map<Long, LocalDate>, today: LocalDate): Pair<List<Goal>, List<CompletedGoal>> {
        val done = goals.mapNotNull { goal ->
            when (goal.kind) {
                GoalKind.HABIT -> habitLastDay(goal, today)?.let { CompletedGoal(goal, it) }
                GoalKind.PROJECT -> finished[goal.id]?.let { CompletedGoal(goal, it) }
                GoalKind.TODO -> null
            }
        }
        val ids = done.map { it.goal.id }.toSet()
        return goals.filter { it.id !in ids } to done.sortedByDescending { it.day }
    }
}
