package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** How the week is going against the user's weekly points target. */
data class WeekProgress(val weekStart: LocalDate, val earned: Int, val target: Int) {
    val reached: Boolean get() = target > 0 && earned >= target
    val left: Int get() = (target - earned).coerceAtLeast(0)

    /** Share of the target earned so far, 0 to 1. */
    val share: Float get() = if (target <= 0) 0f else (earned.toFloat() / target).coerceIn(0f, 1f)

    fun describe(): String = if (reached) "Weekly target reached: $earned of $target" else "This week: $earned of $target · $left to go"
}

/**
 * A number of points to earn each week, Monday to Sunday, if the user sets one. Points earned
 * are the same as the review's: ticks, bonuses and refunds. Reaching it pays nothing extra.
 */
object WeeklyTarget {
    private val EARNED = setOf(EntryKind.TICK, EntryKind.BONUS, EntryKind.REFUND)

    fun progress(entries: List<Entry>, today: LocalDate, target: Int): WeekProgress {
        val start = Days.periodStart(today, Period.WEEKLY)
        val earned = entries.filter { it.kind in EARNED && it.day >= start && it.day <= today }.sumOf { it.amount }
        return WeekProgress(start, earned, target)
    }

    /** Each week the target was reached, up to [until]: the week's Monday and the day it was reached. */
    fun reachedWeeks(entries: List<Entry>, target: Int, until: LocalDate): List<Pair<LocalDate, LocalDate>> {
        if (target <= 0) return emptyList()
        return entries.filter { it.kind in EARNED && it.day <= until }
            .groupBy { Days.periodStart(it.day, Period.WEEKLY) }
            .mapNotNull { (week, inWeek) ->
                var sum = 0
                inWeek.sortedWith(compareBy({ it.day }, { it.at })).firstOrNull { sum += it.amount; sum >= target }?.let { week to it.day }
            }
            .sortedBy { it.first }
    }
}
