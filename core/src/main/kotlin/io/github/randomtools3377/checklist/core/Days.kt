package io.github.randomtools3377.checklist.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

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
        return if (moment.hour < hour) moment.toLocalDate().minusDays(1) else moment.toLocalDate()
    }

    /** First day of the [period] that contains [day]. */
    fun periodStart(day: LocalDate, period: Period): LocalDate = when (period) {
        Period.DAILY -> day
        Period.WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.MONTHLY -> day.withDayOfMonth(1)
    }

    /** Last day of the [period] that contains [day]. */
    fun periodEnd(day: LocalDate, period: Period): LocalDate = when (period) {
        Period.DAILY -> day
        Period.WEEKLY -> periodStart(day, period).plusDays(6)
        Period.MONTHLY -> day.withDayOfMonth(day.lengthOfMonth())
    }

    /** The earliest day whose ticks can still count toward any period that contains [day]. */
    fun earliestOpenDay(day: LocalDate): LocalDate =
        minOf(periodStart(day, Period.WEEKLY), periodStart(day, Period.MONTHLY))
}

/** Whether [tick] counts toward [goal]'s period that starts on [periodStart]. */
fun Tick.countsToward(goal: Goal, periodStart: LocalDate): Boolean =
    goalId == goal.id && stepId == null && period == goal.period && this.periodStart == periodStart
