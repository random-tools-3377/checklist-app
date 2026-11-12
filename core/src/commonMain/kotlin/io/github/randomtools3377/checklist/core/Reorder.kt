package io.github.randomtools3377.checklist.core

/** Moving one thing up or down a list in an editor: a project's steps, a to-do's steps, a routine's items. */
object Reorder {
    /**
     * Moves the one at [index] [by] places (-1 is up, 1 is down), in place. Nothing else changes,
     * so a step keeps its id and with it its tick. Returns false, changing nothing, at either end.
     */
    fun <T> move(list: MutableList<T>, index: Int, by: Int): Boolean {
        val to = index + by
        if (index !in list.indices || to !in list.indices || by == 0) return false
        list.add(to, list.removeAt(index))
        return true
    }
}
