package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** A goal's clock time changed for one day only, on the timeline: [minute] of the day. */
@Serializable
data class Retime(val day: LocalDate, val minute: Int)

/**
 * Times for one day: dragging a goal on the day timeline gives it a clock time that day only.
 * Kept with the goal's repeat, so reminders, pacing and the order all follow it. Goals done
 * several times a day with a time for each keep those.
 */
object Retimes {
    /** How long past ones are kept. */
    const val KEEP_DAYS = 70

    /** [goal]'s clock time on [day]: its time for that day if it was given one, or its usual one. */
    fun timeOn(goal: Goal, day: LocalDate): LocalTime? =
        goal.repeat?.retimes?.firstOrNull { it.day == day }?.let { LocalTime(it.minute / 60, it.minute % 60) } ?: goal.at

    /** [goal] as it is on [day]: with that day's time, if it was given one. */
    fun on(goal: Goal, day: LocalDate): Goal {
        val retime = goal.repeat?.retimes?.firstOrNull { it.day == day } ?: return goal
        if (goal.times.isNotEmpty()) return goal
        return goal.copy(at = LocalTime(retime.minute / 60, retime.minute % 60))
    }

    /** [repeat] with [day]'s time set to [minute], or back to the usual time with null. */
    fun set(repeat: Repeat, day: LocalDate, minute: Int?, today: LocalDate): Repeat {
        val others = repeat.retimes.filter { it.day != day && it.day >= today.minusDays(KEEP_DAYS) }
        return repeat.copy(retimes = if (minute == null) others else others + Retime(day, minute.coerceIn(0, 24 * 60 - 1)))
    }
}
