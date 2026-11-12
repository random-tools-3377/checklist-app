package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** A project in a stretch of the review: achieved on [finishedOn], or its deadline missed on [missedDeadline]. */
data class ProjectLine(val goal: Goal, val finishedOn: LocalDate?, val missedDeadline: LocalDate?)

/** What a goal with an amount came to in a stretch: how much, and the seconds timed with Start and Stop. */
data class AmountTotal(val goal: Goal, val amount: Double, val seconds: Int) {
    /** "Reading: 312 pages, 9 h 40 min"; without the time when none of it was timed. */
    val text: String
        get() = "${goal.name}: ${GoalPage.amountText(amount, goal.unit)}" + if (seconds > 0) ", ${GoalPage.timeText(seconds)}" else ""
}

/** What was finished in a stretch of the review. */
data class StretchDone(val projects: List<ProjectLine>, val todos: List<DoneItem>, val amounts: List<AmountTotal>) {
    val isEmpty: Boolean get() = projects.isEmpty() && todos.isEmpty() && amounts.isEmpty()
}

/**
 * What was finished from one day to another, for the review: projects achieved and to-dos done
 * (as the Done list has them), a project's deadline missed in the stretch shown against it, and
 * for each goal with an amount, how much was done and the time it took.
 */
object StretchDoneList {
    /**
     * [ticks] are every tick there is (a project is achieved when all its steps are ticked,
     * whenever that was). Removed goals are left out, and with [hidePrivate] private ones too.
     */
    fun of(
        from: LocalDate,
        to: LocalDate,
        goals: List<Goal>,
        steps: List<Step>,
        ticks: List<Tick>,
        lapses: List<Lapse>,
        hidePrivate: Boolean = false,
    ): StretchDone {
        val shown = goals.filter { !it.archived && !(hidePrivate && it.private) }
        val done = DoneList.of(shown, steps, ticks).filter { it.day >= from && it.day <= to }
        val achieved = done.filter { it.goal.kind == GoalKind.PROJECT }.associateBy { it.goal.id }
        val missed = lapses.filter { it.kind == LapseKind.MISS && !it.forgotten && it.day >= from && it.day <= to }
            .groupBy { it.goalId }.mapValues { (_, list) -> list.maxOf { it.day } }
        val projects = shown.filter { it.kind == GoalKind.PROJECT && (it.id in achieved || it.id in missed) }
            .map { ProjectLine(it, achieved[it.id]?.day, missed[it.id]) }
        val amounts = shown.filter { it.isHabitLike && it.hasAmount }.mapNotNull { goal ->
            val own = ticks.filter { it.goalId == goal.id && it.stepId == null && it.day >= from && it.day <= to }
            if (own.isEmpty()) null else AmountTotal(goal, own.sumOf { it.amount ?: goal.amount ?: 0.0 }, own.sumOf { it.seconds ?: 0 })
        }
        return StretchDone(projects, done.filter { it.goal.kind == GoalKind.TODO }, amounts)
    }
}
