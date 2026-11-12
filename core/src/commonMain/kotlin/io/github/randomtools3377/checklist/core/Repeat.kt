package io.github.randomtools3377.checklist.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** The ways a habit can repeat. */
@Serializable
enum class RepeatKind {
    /** Every day, or every 2nd, 3rd, … day. */
    EVERY_N_DAYS,

    /** Chosen days of the week, every week or every 2nd, 3rd, … week. */
    WEEKDAYS,

    /** A number of times per week, month or year, on any days. */
    TIMES_PER,

    /** Chosen dates of the month, every month or every few months. */
    MONTH_DATES,

    /** One weekday of the month, like the first Monday or the last Friday. */
    MONTH_WEEKDAY,

    /** One date every year. */
    YEARLY,

    /** Once, on the start date. */
    ONCE,

    /**
     * [Repeat.interval] days after it was last done. [Repeat.start] is the day it is next due.
     * Not done that day, it is one miss, then it stays on Today until done with no more misses
     * ([Repeat.lateSince]), and the next one counts from when it is done.
     */
    AFTER_DONE,
}

/**
 * When a habit is due. Every kind is due on single days, except [RepeatKind.TIMES_PER], which
 * is due across a whole week, month or year.
 */
@Serializable
data class Repeat(
    val kind: RepeatKind,
    /** The first day it can be due. Intervals count from here. For [RepeatKind.ONCE], the day. */
    val start: LocalDate,
    /** The last day it can be due, if the user set one. */
    val end: LocalDate? = null,
    /** Every this many days, weeks or months. */
    val interval: Int = 1,
    /** Days of the week as ISO numbers, Monday = 1 … Sunday = 7. One day for [RepeatKind.MONTH_WEEKDAY]. */
    val weekdays: Set<Int> = emptySet(),
    /** For [RepeatKind.TIMES_PER]: counted per week, month or year. */
    val per: Period? = null,
    /** Dates of the month, 1 to 31, and [LAST_DAY] for the last day. */
    val monthDays: Set<Int> = emptySet(),
    /** For [RepeatKind.MONTH_WEEKDAY]: 1 to 4 for the first to fourth, [LAST_WEEK] for the last. */
    val weekOfMonth: Int = 1,
    /** For [RepeatKind.YEARLY]: the month (1 to 12) and the day. */
    val month: Int = 1,
    val day: Int = 1,
    /** Single days moved to another day of the same week (see [Moves]). */
    val moves: List<Move> = emptyList(),
    /**
     * For [RepeatKind.AFTER_DONE]: the day it was first due and not done, while it stays on Today
     * after that. Null when it isn't late.
     */
    val lateSince: LocalDate? = null,
    /** Clock times changed for single days on the timeline (see [Retimes]). */
    val retimes: List<Retime> = emptyList(),
) {
    companion object {
        const val LAST_DAY = 0
        const val LAST_WEEK = -1

        fun everyDay(start: LocalDate) = Repeat(RepeatKind.EVERY_N_DAYS, start)
        fun timesPer(per: Period, start: LocalDate) = Repeat(RepeatKind.TIMES_PER, start, per = per)
    }

    /** Due every single day: "every day", or every day of the week picked, every week. */
    val isEveryDay: Boolean
        get() = when (kind) {
            RepeatKind.EVERY_N_DAYS -> interval <= 1
            RepeatKind.WEEKDAYS -> interval <= 1 && weekdays.containsAll((1..7).toList())
            else -> false
        }

    /** The kind of window a tick counts toward: a single day, or the whole week, month or year. */
    val windowPeriod: Period get() = if (kind == RepeatKind.TIMES_PER) per ?: Period.WEEKLY else Period.DAILY

    private fun inRange(date: LocalDate) = date >= start && (end == null || date <= end)

    /** Due on [date] by its pattern, before any moves. */
    private fun dueByPattern(date: LocalDate) = inRange(date) && matches(date)

    /** Due on [date] once moves are taken into account: not on a day moved away, and on a day moved to. */
    private fun dueAfterMoves(date: LocalDate) =
        moves.any { it.to == date } || (dueByPattern(date) && moves.none { it.from == date })

    /** Whether it is due on [date]. For [RepeatKind.TIMES_PER], whether [date]'s period is open. */
    fun isDueOn(date: LocalDate): Boolean = when (kind) {
        RepeatKind.TIMES_PER -> window(date) != null
        else -> dueAfterMoves(date)
    }

    /** Whether it would be due on [date] by its pattern alone, ignoring moves. */
    fun isDueByPattern(date: LocalDate): Boolean = kind != RepeatKind.TIMES_PER && dueByPattern(date)

    /**
     * The window [date] falls in, if the habit is due then: the day itself for single-day kinds,
     * or the week, month or year for [RepeatKind.TIMES_PER].
     */
    fun window(date: LocalDate): Window? {
        if (kind != RepeatKind.TIMES_PER) {
            return if (dueAfterMoves(date)) Window(date, date, Period.DAILY) else null
        }
        val period = windowPeriod
        val from = Days.periodStart(date, period)
        val to = Days.periodEnd(date, period)
        if (to < start || (end != null && from > end)) return null
        return Window(from, to, period)
    }

    /**
     * Whether [window], a week, month or year of a [RepeatKind.TIMES_PER] goal, is only partly
     * inside its start and end dates, like the week a goal was started on a Saturday. Such a
     * window isn't missed when it falls short; it counts toward streaks only once it is done.
     */
    fun isPartial(window: Window): Boolean =
        window.period != Period.DAILY && (window.start < start || (end != null && window.end > end))

    /** The first day on or after [from] when it is due, looking ahead up to [limitDays] days. */
    fun nextDue(from: LocalDate, limitDays: Int = 800): LocalDate? {
        if (kind == RepeatKind.TIMES_PER) return window(from)?.let { from } ?: if (from < start) start else null
        val movedTo = moves.map { it.to }.filter { it >= from }.minOrNull()
        val byPattern = run {
            if (kind == RepeatKind.ONCE || kind == RepeatKind.AFTER_DONE) return@run start.takeIf { it >= from && moves.none { m -> m.from == start } }
            var date = maxOf(from, start)
            repeat(limitDays) {
                if (end != null && date > end) return@run null
                if (matches(date) && moves.none { it.from == date }) return@run date
                date = date.plusDays(1)
            }
            null
        }
        return listOfNotNull(movedTo, byPattern).minOrNull()
    }

    private fun matches(date: LocalDate): Boolean = when (kind) {
        RepeatKind.EVERY_N_DAYS -> (date.toEpochDays() - start.toEpochDays()) % interval.coerceAtLeast(1) == 0
        RepeatKind.WEEKDAYS ->
            date.dayOfWeek.isoDayNumber in weekdays &&
                weeksBetween(start, date) % interval.coerceAtLeast(1) == 0
        RepeatKind.MONTH_DATES ->
            monthsBetween(start, date) % interval.coerceAtLeast(1) == 0 &&
                date.dayOfMonth in effectiveMonthDays(date)
        RepeatKind.MONTH_WEEKDAY ->
            monthsBetween(start, date) % interval.coerceAtLeast(1) == 0 &&
                date.dayOfWeek.isoDayNumber in weekdays &&
                if (weekOfMonth == LAST_WEEK) date.plusDays(7).month != date.month
                else (date.dayOfMonth - 1) / 7 + 1 == weekOfMonth
        RepeatKind.YEARLY -> date.monthNumber == month && date.dayOfMonth == minOf(day, lengthOfMonth(date.year, month))
        RepeatKind.ONCE, RepeatKind.AFTER_DONE -> date == start
        RepeatKind.TIMES_PER -> true
    }

    /**
     * The days of [date]'s month this falls on. A date the month doesn't have, like the 31st in
     * a 30-day month, falls on its last day.
     */
    private fun effectiveMonthDays(date: LocalDate): Set<Int> {
        val length = lengthOfMonth(date.year, date.monthNumber)
        return monthDays.map { if (it == LAST_DAY) length else minOf(it, length) }.toSet()
    }
}

/** A stretch of days a habit's ticks count toward. [period] is its kind. */
data class Window(val start: LocalDate, val end: LocalDate, val period: Period)

fun lengthOfMonth(year: Int, month: Int): Int =
    LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minusDays(1).dayOfMonth

private fun mondayOf(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.isoDayNumber - 1)

private fun weeksBetween(from: LocalDate, to: LocalDate): Int =
    abs(mondayOf(to).toEpochDays() - mondayOf(from).toEpochDays()) / 7

private fun monthsBetween(from: LocalDate, to: LocalDate): Int {
    return abs((to.year * 12 + to.monthNumber) - (from.year * 12 + from.monthNumber))
}

/** ISO day number to a short English name. */
fun weekdayShort(iso: Int): String = DayOfWeek(iso).name.take(3).lowercase().replaceFirstChar { it.uppercase() }
