package io.github.randomtools3377.checklist.core

/**
 * The user's own categories for goals, like "Health", "Home" or "Work". A category is just a
 * name on the goal: there is no list to keep, so one goes away when no goal uses it.
 */
object Categories {
    /** The longest a category name can be. */
    const val LENGTH = 30

    /**
     * Tidies what the user typed: trimmed, inner spaces collapsed, cut to [LENGTH]. Blank means
     * no category. A name matching one already in use, ignoring case, takes that one's spelling,
     * so "health" joins "Health" rather than starting a second one.
     */
    fun tidy(input: String?, existing: List<String>): String? {
        val text = input?.trim()?.replace(Regex("\\s+"), " ")?.take(LENGTH)?.trim().orEmpty()
        if (text.isEmpty()) return null
        return existing.firstOrNull { it.equals(text, ignoreCase = true) } ?: text
    }

    /** The categories the goals use, in the order of the first goal in each. */
    fun of(goals: List<Goal>): List<String> =
        goals.filter { !it.archived }.sortedBy { it.position }.mapNotNull { it.category }.distinct()

    /** Whether [goal] belongs in [filter]: null shows everything. */
    fun shows(filter: String?, goal: Goal): Boolean = filter == null || goal.category == filter
}

/** How one category went over a stretch: its habits' times due and done, and its goals' points. */
data class CategorySummary(
    /** Null for goals without a category. */
    val name: String?,
    val due: Int,
    val done: Int,
    val points: Int,
) {
    /** Share of the times due that got done, 0 to 1. */
    val consistency: Double get() = if (due == 0) 0.0 else done.toDouble() / due
}
