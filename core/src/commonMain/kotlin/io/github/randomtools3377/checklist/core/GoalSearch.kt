package io.github.randomtools3377.checklist.core

/** Search in the Goals list: every word typed has to be found in a goal's name or category. */
object GoalSearch {
    fun matches(goal: Goal, query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val text = (goal.name + " " + goal.category.orEmpty()).lowercase()
        return words.all { it in text }
    }

    fun filter(goals: List<Goal>, query: String): List<Goal> = goals.filter { matches(it, query) }
}
