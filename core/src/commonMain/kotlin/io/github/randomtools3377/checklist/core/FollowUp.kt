package io.github.randomtools3377.checklist.core

/**
 * After a goal made with "Describe it" or from a level-up idea is saved, one small next step is
 * offered in the bar at the bottom: a reminder a little before its time, or doing it right
 * after another goal. Only one, only for things that are on, and "No thanks" is doing nothing.
 */
sealed interface FollowUp {
    /** The question in the bar, and the word on its button. */
    val question: String
    val button: String

    /** An earlier alert before the goal's clock time. */
    data class RemindBefore(val goalId: Long, val minutes: Int, override val question: String) : FollowUp {
        override val button: String get() = "Add"
    }

    /** A reminder at the goal's clock time, when it has none. */
    data class RemindAt(val goalId: Long, override val question: String) : FollowUp {
        override val button: String get() = "Add"
    }

    /** Doing it right after another goal, as a habit chain. */
    data class After(val goalId: Long, val afterId: Long, override val question: String) : FollowUp {
        override val button: String get() = "Yes"
    }

    companion object {
        const val BEFORE_MINUTES = 10

        /**
         * The one follow-up for [goal], just saved, among the user's [goals], with the extras
         * [on]. A goal with a time and no reminder is offered one; with habit chains on, a goal
         * with no time is offered a place right after the daily habit before it in the list;
         * a goal with a time and a reminder is offered an earlier alert. Else nothing.
         */
        fun pick(goal: Goal, goals: List<Goal>, on: Set<Feature>): FollowUp? {
            if (goal.kind != GoalKind.HABIT || goal.archived || goal.quit || goal.atMost) return null
            val at = goal.at
            val reminder = goal.reminder
            if (at != null && reminder != null && !reminder.onTime) {
                return RemindAt(goal.id, "Add a reminder at ${clockText(at)}?")
            }
            if (at == null && Feature.CHAINS in on && goal.after == null) {
                val before = Chains.choices(goal, goals)
                    .filter { it.kind == GoalKind.HABIT && !it.private && !it.quit && it.repeat?.kind == RepeatKind.EVERY_N_DAYS }
                    .sortedBy { it.position }.lastOrNull { it.position < goal.position }
                if (before != null) return After(goal.id, before.id, "Do it right after ${before.name}?")
            }
            if (at != null && (reminder == null || reminder.onTime) && reminder?.minutesBefore == null && goal.times.isEmpty()) {
                return RemindBefore(goal.id, BEFORE_MINUTES, "Also remind you $BEFORE_MINUTES minutes before ${clockText(at)}?")
            }
            return null
        }
    }
}
