package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * Quick to-dos: one-off tasks that pay points. A to-do with a date is due that day like a
 * habit: undone after it, it is a miss, with a reason, the allowance and penalties. To-dos share
 * one allowance between them. A to-do without a date waits on Today until it is done and is never
 * missed; the day it is done becomes its date.
 */
object Todos {
    /** Points a to-do pays unless the user sets otherwise. */
    const val POINTS = 2

    /** The icon a to-do gets unless the user picks one. */
    const val ICON = "☑️"

    /** A new to-do, due on [date] if there is one. */
    fun of(name: String, points: Int = POINTS, date: LocalDate? = null, icon: String = ICON, position: Int = 0, createdAt: Long = 0): Goal = Goal(
        kind = GoalKind.TODO,
        name = name.trim().take(80),
        icon = icon,
        points = points.coerceIn(0, 10_000),
        repeat = date?.let { Repeat(RepeatKind.ONCE, it) },
        position = position,
        createdAt = createdAt,
    )

    /** The date a to-do is due on, if it has one. */
    fun dateOf(goal: Goal): LocalDate? = goal.repeat?.takeIf { goal.kind == GoalKind.TODO }?.start
}
