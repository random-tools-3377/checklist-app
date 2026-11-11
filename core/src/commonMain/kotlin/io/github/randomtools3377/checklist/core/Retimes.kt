package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A goal's clock time changed for one day only, on the timeline: [minute] of the day. */
@Serializable
data class Retime(val day: LocalDate, val minute: Int)

/**
 * Times for one day: dragging a goal on the day timeline gives it a clock time that day only.
 * Kept with the goal's repeat, so reminders, pacing and the order all follow it. Goals done
 * several times a day keep their times: the ones set for each, or the ones spread over the day.
 */
object Retimes {
    /** How long past ones are kept. */
    const val KEEP_DAYS = 70

    /**
     * Whether [goal] keeps its own times, so the timeline can't give it one for a day. One done
     * several times a day with a single clock time instead (saved by an older version) isn't
     * spread, so it sits at that time and can be moved like any other.
     */
    fun keepsTimes(goal: Goal): Boolean = goal.times.isNotEmpty() || (goal.severalADay && goal.at == null)

    /** [goal]'s clock time on [day]: its time for that day if it was given one, or its usual one. */
    fun timeOn(goal: Goal, day: LocalDate): LocalTime? {
        if (keepsTimes(goal)) return goal.at
        return goal.repeat?.retimes?.firstOrNull { it.day == day }?.let { LocalTime(it.minute / 60, it.minute % 60) } ?: goal.at
    }

    /** [goal] as it is on [day]: with that day's time, if it was given one. */
    fun on(goal: Goal, day: LocalDate): Goal {
        if (keepsTimes(goal)) return goal
        val retime = goal.repeat?.retimes?.firstOrNull { it.day == day } ?: return goal
        return goal.copy(at = LocalTime(retime.minute / 60, retime.minute % 60))
    }

    /** [repeat] with [day]'s time set to [minute], or back to the usual time with null. */
    fun set(repeat: Repeat, day: LocalDate, minute: Int?, today: LocalDate): Repeat {
        val others = repeat.retimes.filter { it.day != day && it.day >= today.minusDays(KEEP_DAYS) }
        return repeat.copy(retimes = if (minute == null) others else others + Retime(day, minute.coerceIn(0, 24 * 60 - 1)))
    }
}

/**
 * The hours of the user's day, for the day timeline. The day runs to the day-end hour the night
 * after, so with the day ending at 4 am, 1 am comes after 11 pm, as it does for pacing and
 * reminders (see [Pace.on]). Times are minutes from the midnight the day starts on, so the small
 * hours are past 24 hours.
 */
object DayHours {
    private const val DAY = 24 * 60

    /** [time] in minutes from the midnight the day starts on: a time before the day-end hour is the night after. */
    fun minuteOf(time: LocalTime, dayEndHour: Int): Int = time.hour * 60 + time.minute + if (time.hour < dayEndHour) DAY else 0

    /** The hour after the last one shown: midnight, or the day-end hour the night after. */
    fun endHour(dayEndHour: Int): Int = 24 + dayEndHour

    /**
     * The first hour shown: when the user's day starts, or earlier for a goal set earlier
     * ([minutes] as from [minuteOf]), never before the day-end hour.
     */
    fun startHour(dayStartHour: Int, minutes: List<Int>, dayEndHour: Int): Int =
        minOf(maxOf(dayStartHour, dayEndHour), (minutes.minOrNull() ?: DAY) / 60).coerceIn(0, 23)

    /** Where a goal dragged to [minute] lands: on a quarter hour within the hours shown, as a minute of the clock. */
    fun drop(minute: Int, startHour: Int, dayEndHour: Int): Int {
        val snapped = ((minute / 15.0).roundToInt() * 15).coerceIn(startHour * 60, endHour(dayEndHour) * 60 - 15)
        return snapped % DAY
    }
}
