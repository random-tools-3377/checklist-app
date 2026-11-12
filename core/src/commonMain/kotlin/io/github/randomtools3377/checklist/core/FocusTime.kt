package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * One go at the focus timer: how many minutes were really focused on a goal, on the user's day
 * it ended. Time paused isn't in it, and a break is never one of these.
 */
@Serializable
data class FocusSession(
    val id: Long = 0,
    val goalId: Long,
    val day: LocalDate,
    /** When the timer was started, in epoch milliseconds. */
    val startedAt: Long,
    val minutes: Int,
)

/** A goal's focus time over a stretch. */
data class GoalFocus(val goal: Goal, val minutes: Int, val sessions: Int)

/** Focus time over a stretch: all of it, and each goal's, most first. */
data class FocusTotals(val minutes: Int, val sessions: Int, val goals: List<GoalFocus>) {
    val any: Boolean get() = minutes > 0
}

/** The rules for focus time kept, and the optional break after a session. */
object FocusTime {
    /** The setting for "Break after a focus session": minutes, or absent or "0" for off. */
    const val BREAK_SETTING = "focus_break"

    /** The breaks offered in Settings, in minutes; 0 is off, which it is to start. */
    val BREAK_CHOICES = listOf(0, 5, 10, 15)

    /** The longest one session can be, so a damaged timer can't add days. */
    const val MAX_MINUTES = 24 * 60

    /** How long the break is, from its setting: 0 for off, or for anything that isn't offered. */
    fun breakMinutes(setting: String?): Int = setting?.toIntOrNull()?.takeIf { it in BREAK_CHOICES } ?: 0

    /**
     * The time really focused, in milliseconds: the timer's length less what was left on it when
     * it ended. The countdown stands still while paused, so pauses aren't counted.
     */
    fun focusedMillis(totalMillis: Long, leftMillis: Long): Long =
        (totalMillis - leftMillis.coerceAtLeast(0)).coerceIn(0, totalMillis.coerceAtLeast(0))

    /** Whole minutes focused, to the nearest one. Under half a minute is none. */
    fun minutes(focusedMillis: Long): Int = ((focusedMillis.coerceAtLeast(0) + 30_000) / 60_000).coerceAtMost(MAX_MINUTES.toLong()).toInt()

    /**
     * The session to keep when a timer ends, by running out, "Done now" or being stopped; null
     * when it was a break ([isBreak]) or under half a minute was focused.
     */
    fun session(goalId: Long, isBreak: Boolean, totalMillis: Long, leftMillis: Long, startedAt: Long, day: LocalDate): FocusSession? {
        if (isBreak || goalId <= 0) return null
        val minutes = minutes(focusedMillis(totalMillis, leftMillis))
        if (minutes <= 0) return null
        return FocusSession(goalId = goalId, day = day, startedAt = startedAt, minutes = minutes)
    }

    /** The minutes focused on [goalId] from [from] to [to], both days included. */
    fun goalMinutes(sessions: List<FocusSession>, goalId: Long, from: LocalDate, to: LocalDate): Int =
        sessions.filter { it.goalId == goalId && it.day >= from && it.day <= to }.sumOf { it.minutes }

    /**
     * Focus time from [from] to [to], for the review: the total, and each goal's, most first.
     * [goals] holds removed goals too, so their time still counts. With [hidePrivate], private
     * goals and their time are left out, as in the rest of the review. Time on a goal that is no
     * longer there at all counts in the total only.
     */
    fun totals(sessions: List<FocusSession>, goals: List<Goal>, from: LocalDate, to: LocalDate, hidePrivate: Boolean = false): FocusTotals {
        val byId = goals.associateBy { it.id }
        val shown = sessions.filter { session ->
            session.minutes > 0 && session.day >= from && session.day <= to && !(hidePrivate && byId[session.goalId]?.private == true)
        }
        val each = shown.groupBy { it.goalId }
            .mapNotNull { (id, mine) -> byId[id]?.let { GoalFocus(it, mine.sumOf { s -> s.minutes }, mine.size) } }
            .sortedWith(compareByDescending<GoalFocus> { it.minutes }.thenBy { it.goal.name.lowercase() })
        return FocusTotals(shown.sumOf { it.minutes }, shown.size, each)
    }
}
