package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** One day's goal moved to another day: due on [to] instead of [from]. */
@Serializable
data class Move(val from: LocalDate, val to: LocalDate)

/**
 * Moving one day's goal to another day ("do it tomorrow instead"). It is free, but only to
 * another day of the same Monday-to-Sunday week, from today on, where the goal isn't due already;
 * if it isn't done on the new day, that is a miss as usual. Goals due across a week, month or
 * year don't need moving.
 */
object Moves {
    /** How long moves are kept after their day, so the review and misses still see them. */
    const val KEEP_DAYS = 70

    /** The days [goal]'s time on [from] can move to. Empty if it can't be moved. */
    fun choices(goal: Goal, from: LocalDate, today: LocalDate): List<LocalDate> {
        val repeat = goal.repeat ?: return emptyList()
        if (goal.kind != GoalKind.HABIT || repeat.kind == RepeatKind.TIMES_PER || repeat.kind == RepeatKind.AFTER_DONE) return emptyList()
        if (from < today || !repeat.isDueOn(from)) return emptyList()
        val monday = Days.periodStart(from, Period.WEEKLY)
        return (0..6).map { monday.plusDays(it) }.filter { it >= today && it != from && !repeat.isDueOn(it) }
    }

    /**
     * [repeat] with its time on [from] moved to [to]. A time already moved is moved on, and moving
     * it back to the day it came from takes the move away.
     */
    fun move(repeat: Repeat, from: LocalDate, to: LocalDate): Repeat {
        val earlier = repeat.moves.firstOrNull { it.to == from }
        val others = repeat.moves - listOfNotNull(earlier)
        val original = earlier?.from ?: from
        val moved = if (original == to) others else others + Move(original, to)
        return repeat.copy(moves = moved.sortedBy { it.from })
    }

    /** The day a time on [day] was moved from, if it was moved there. */
    fun movedFrom(repeat: Repeat?, day: LocalDate): LocalDate? = repeat?.moves?.firstOrNull { it.to == day }?.from

    /** Moves kept when a goal's repeat is edited: the recent and coming ones. */
    fun keep(moves: List<Move>, today: LocalDate): List<Move> =
        moves.filter { maxOf(it.from, it.to) >= today.minusDays(KEEP_DAYS) }
}
