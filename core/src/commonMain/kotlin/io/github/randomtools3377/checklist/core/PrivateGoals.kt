package io.github.randomtools3377.checklist.core

/** What stands in for goals marked private while the privacy lock is on for them. */
object PrivateGoals {
    /** Said in place of a private goal's name, as its reminders do. */
    const val NAME = "A private goal"

    /** The ids of the goals marked private. */
    fun ids(goals: List<Goal>): Set<Long> = goals.filter { it.private }.map { it.id }.toSet()

    /**
     * The points history with the [hidden] goals unnamed: their entries keep their amount and
     * time, but say "A private goal" and lose their tick, so the tick's note isn't shown.
     */
    fun unnamed(entries: List<Entry>, hidden: Set<Long>): List<Entry> =
        if (hidden.isEmpty()) entries
        else entries.map { entry -> if (entry.goalId?.let { it in hidden } == true) entry.copy(label = NAME, tickId = null) else entry }
}
