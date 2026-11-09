package io.github.randomtools3377.checklist.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Which day and which period a moment belongs to. Weeks start on Monday. */
object Days {
    /** Latest hour the user can pick for the end of the day. */
    const val MAX_DAY_END_HOUR = 11

    /**
     * The user's day for [moment]. With the day ending at 4am, 1:30am on Tuesday still counts as
     * Monday, so a late night does not start a new day.
     */
    fun dayOf(moment: LocalDateTime, dayEndHour: Int): LocalDate {
        val hour = dayEndHour.coerceIn(0, MAX_DAY_END_HOUR)
        return if (moment.hour < hour) moment.date.minusDays(1) else moment.date
    }

    /** First day of the [period] that contains [day]. */
    fun periodStart(day: LocalDate, period: Period): LocalDate = when (period) {
        Period.DAILY -> day
        Period.WEEKLY -> day.minusDays(day.dayOfWeek.isoDayNumber - 1)
        Period.MONTHLY -> LocalDate(day.year, day.month, 1)
        Period.YEARLY -> LocalDate(day.year, 1, 1)
    }

    /** Last day of the [period] that contains [day]. */
    fun periodEnd(day: LocalDate, period: Period): LocalDate = when (period) {
        Period.DAILY -> day
        Period.WEEKLY -> periodStart(day, period).plusDays(6)
        Period.MONTHLY -> LocalDate(day.year, day.month, 1).plus(1, DateTimeUnit.MONTH).minusDays(1)
        Period.YEARLY -> LocalDate(day.year, 12, 31)
    }

    /** The earliest day whose ticks can still count toward any window that contains [day]. */
    fun earliestOpenDay(day: LocalDate): LocalDate =
        minOf(periodStart(day, Period.WEEKLY), periodStart(day, Period.YEARLY))
}

fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Int): LocalDate = minus(days, DateTimeUnit.DAY)

/**
 * How many of [goal]'s times [ticks] (those of one window) make done: one per tick, or, for a
 * goal with an amount, one once the amounts entered add up to it. A tick without an amount, from
 * before the goal had one, counts as the whole amount.
 */
fun Goal.timesDone(ticks: List<Tick>): Int =
    if (hasAmount) (if (Amounts.reached(progressOf(ticks), amount!!)) 1 else 0) else ticks.size

/** How much of a goal's amount [ticks] (those of one window) add up to. */
fun Goal.progressOf(ticks: List<Tick>): Double = ticks.sumOf { it.amount ?: amount ?: 1.0 }

/** Whether [tick] counts toward [goal]'s period that starts on [periodStart]. */
fun Tick.countsToward(goal: Goal, periodStart: LocalDate): Boolean =
    goalId == goal.id && stepId == null && period == goal.period && this.periodStart == periodStart
