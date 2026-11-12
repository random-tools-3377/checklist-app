package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalTime

/** Habit chains: a goal can come after another; ticking that one reminds of the next. */
object Chains {
    /** The goals that come right after [goalId] and are still to do on [view]'s day: not done, and not skipped. */
    fun next(goalId: Long, view: TodayView): List<HabitRow> =
        (view.dueToday + view.thisWeek + view.thisMonth + view.thisYear).filter { it.goal.after == goalId && !it.settled && !it.skipped }

    /**
     * Goals [goal] could come after: other active habits, leaving out any that would make a
     * loop back to it.
     */
    fun choices(goal: Goal, goals: List<Goal>): List<Goal> = goals.filter { other ->
        other.id != goal.id && !other.archived && other.isHabitLike && !leadsTo(other, goal.id, goals)
    }

    private fun leadsTo(start: Goal, target: Long, goals: List<Goal>): Boolean {
        var current: Goal? = start
        val seen = mutableSetOf<Long>()
        while (current != null && seen.add(current.id)) {
            if (current.after == target) return true
            current = goals.firstOrNull { it.id == current!!.after }
        }
        return false
    }
}

/** When goals are usually done, for reminders at the usual time. */
object UsualTimes {
    /** A goal needs this many ticks over the last 4 weeks before its usual time is trusted. */
    const val MIN_TICKS = 5

    /** The usual time of day for each goal, from the minutes of the day it was ticked at. */
    fun of(minutesByGoal: Map<Long, List<Int>>): Map<Long, LocalTime> = minutesByGoal
        .filterValues { it.size >= MIN_TICKS }
        .mapNotNull { (goalId, minutes) -> usualMinuteOfDay(minutes)?.let { goalId to LocalTime(it / 60, it % 60) } }
        .toMap()
}
