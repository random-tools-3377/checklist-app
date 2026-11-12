package io.github.randomtools3377.checklist.core

/**
 * What a backup file, or the device itself, holds, counted for the "Replace everything?"
 * question: importing replaces all of it, so the user sees both sides first.
 */
data class BackupSummary(
    val habits: Int,
    val projects: Int,
    val todos: Int,
    val rewards: Int,
    val ticks: Int,
    val balance: Int,
    /** The budget: spending and money in, bills, saving goals, changed work days, or pay. */
    val budget: Boolean,
    /** The mood journal: any day with a mood. */
    val moods: Boolean,
    /** Focus time: any focus session kept. */
    val focus: Boolean,
) {
    /** Nothing a user made is in it: a new install, or just after "Erase everything". */
    val isEmpty: Boolean
        get() = habits == 0 && projects == 0 && todos == 0 && rewards == 0 && ticks == 0 && balance == 0 && !budget && !moods && !focus

    /** "3 habits, 1 project, 2 to-dos, 4 rewards and 120 ticks". */
    fun counts(): String = listed(
        listOf(
            count(habits, "habit", "habits"),
            count(projects, "project", "projects"),
            count(todos, "to-do", "to-dos"),
            count(rewards, "reward", "rewards"),
            count(ticks, "tick", "ticks"),
        ),
    )

    companion object {
        /** Goals that are still there count; removed ones are history. */
        fun of(backup: Backup): BackupSummary {
            val goals = backup.goals.filter { !it.archived }
            return BackupSummary(
                habits = goals.count { it.kind == GoalKind.HABIT },
                projects = goals.count { it.kind == GoalKind.PROJECT },
                todos = goals.count { it.kind == GoalKind.TODO },
                rewards = backup.rewards.count { !it.archived },
                ticks = backup.ticks.size,
                balance = backup.entries.sumOf { it.amount },
                budget = backup.money.isNotEmpty() || backup.bills.isNotEmpty() || backup.savingGoals.isNotEmpty() ||
                    backup.workDays.isNotEmpty() || !backup.settings[PayPlan.SETTING].isNullOrBlank(),
                moods = backup.moods.isNotEmpty(),
                focus = backup.focusSessions.isNotEmpty(),
            )
        }

        private fun count(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

        internal fun listed(parts: List<String>): String = when (parts.size) {
            0 -> ""
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }
}

/** What importing a file would take away without putting anything in its place. */
object ImportCheck {
    /**
     * The parts this device has and the file doesn't, in the app's own names: "the budget",
     * "the mood journal", "focus time". Empty when the file has something for each part in use.
     */
    fun missing(file: BackupSummary, device: BackupSummary): List<String> = listOfNotNull(
        "the budget".takeIf { device.budget && !file.budget },
        "the mood journal".takeIf { device.moods && !file.moods },
        "focus time".takeIf { device.focus && !file.focus },
    )

    /** The warning for [missing], or null when there is nothing to warn about. */
    fun warning(file: BackupSummary, device: BackupSummary, deviceWord: String): String? {
        val parts = missing(file, device)
        if (parts.isEmpty()) return null
        val names = BackupSummary.listed(parts)
        val it = if (parts.size == 1) "it" else "them"
        return "This file has nothing for ${names}, but this $deviceWord does. Importing removes $it from this $deviceWord."
    }
}
