package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** A finished project or a done to-do, and the day it was finished. */
data class DoneItem(val goal: Goal, val day: LocalDate, val at: Long)

/** What's done and over with: finished projects and done to-dos, newest first. */
object DoneList {
    fun of(goals: List<Goal>, steps: List<Step>, ticks: List<Tick>): List<DoneItem> {
        val byGoal = ticks.groupBy { it.goalId }
        return goals.filter { !it.archived }.mapNotNull { goal ->
            val own = byGoal[goal.id].orEmpty()
            when (goal.kind) {
                GoalKind.PROJECT -> {
                    val active = steps.filter { it.goalId == goal.id && !it.archived }
                    val stepTicks = own.filter { it.stepId != null }.associateBy { it.stepId }
                    if (active.isEmpty() || active.any { it.id !in stepTicks }) return@mapNotNull null
                    val last = active.mapNotNull { stepTicks[it.id] }.maxBy { it.at }
                    DoneItem(goal, last.day, last.at)
                }
                GoalKind.TODO -> own.filter { it.stepId == null }.maxByOrNull { it.at }?.let { DoneItem(goal, it.day, it.at) }
                GoalKind.HABIT -> null
            }
        }.sortedWith(compareByDescending<DoneItem> { it.day }.thenByDescending { it.at })
    }
}
