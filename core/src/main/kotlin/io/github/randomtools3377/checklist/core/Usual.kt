package io.github.randomtools3377.checklist.core

import java.time.LocalDate
import kotlin.math.roundToInt

/** How long a price takes to earn, judged from the user's own history. */
object Usual {
    /** How many finished days, at most, make up "your usual". */
    const val WINDOW_DAYS = 28

    /**
     * Average points earned per day (ticks and bonuses, not spending), over up to [WINDOW_DAYS]
     * finished days before [today]. Days with nothing earned count as zero. On the first day of
     * use, today's earnings so far are used. Null when there is nothing to go on yet.
     *
     * @param earnedByDay points earned on each day; days that are missing earned nothing.
     * @param firstDay the first day anything was earned, or null if nothing ever was.
     */
    fun pointsPerDay(earnedByDay: Map<LocalDate, Int>, firstDay: LocalDate?, today: LocalDate): Double? {
        if (firstDay == null || firstDay > today) return null
        val from = maxOf(firstDay, today.minusDays(WINDOW_DAYS.toLong()))
        val to = today.minusDays(1)
        val days = if (from > to) listOf(today) else generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
        val average = days.sumOf { earnedByDay[it] ?: 0 }.toDouble() / days.size
        return average.takeIf { it > 0.0 }
    }

    /** "≈ 4 days of your usual" for [price], or null when there is no usual yet. */
    fun describe(price: Int, pointsPerDay: Double?): String? {
        if (pointsPerDay == null || pointsPerDay <= 0.0 || price <= 0) return null
        val days = price / pointsPerDay
        if (days < 0.75) return "less than a day of your usual"
        val whole = days.roundToInt().coerceAtLeast(1)
        return if (whole == 1) "≈ 1 day of your usual" else "≈ $whole days of your usual"
    }
}
