package io.github.randomtools3377.checklist.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Goals with an amount in the user's own unit, like 20 pages or 1 mile. How long one takes comes
 * from a time the user types in for the whole amount, or else is learned from their own Start and
 * Stop timings; a typed time wins until it is cleared.
 */
object Amounts {
    /** The most one goal's amount, or one entry, can be. */
    const val MAX = 100_000.0

    /** How many of the latest timings the usual speed is learned from. */
    const val TIMINGS = 10

    /** The longest a unit's name can be. */
    const val UNIT_LENGTH = 20

    /** Reached, allowing for rounding in decimals. */
    fun reached(progress: Double, amount: Double): Boolean = progress >= amount - 1e-6

    /** How many whole amounts [progress] makes, allowing for rounding in decimals. */
    fun times(progress: Double, amount: Double): Int = if (amount <= 0) 0 else kotlin.math.floor(progress / amount + 1e-6).toInt()

    /** What is left of the time being worked on now: the rest of the current whole amount. */
    fun leftOfThisTime(progress: Double, amount: Double): Double =
        ((times(progress, amount) + 1) * amount - progress).coerceAtLeast(0.0).let { if (it < 1e-6) amount else it }

    /** A positive amount rounded to two decimals, or null if it isn't one. */
    fun tidy(amount: Double): Double? =
        amount.takeIf { it.isFinite() && it > 0 }?.coerceAtMost(MAX)?.let { (it * 100).roundToLong() / 100.0 }?.takeIf { it > 0 }

    /** Reads what the user typed, with a point or a comma for decimals. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.let { tidy(it) }

    /** "12", "1.5", "0.25". */
    fun format(amount: Double): String {
        val rounded = (amount * 100).roundToLong()
        if (rounded % 100 == 0L) return (rounded / 100).toString()
        val text = (rounded / 100.0).toString()
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }

    /**
     * "12 of 20 pages", or for a goal done more than once, "1 of 3 times · 0.5 of 1 mile on the
     * next".
     */
    fun progressText(progress: Double, goal: Goal): String {
        val amount = goal.amount ?: 0.0
        val unit = goal.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        if (goal.target <= 1) return "${format(progress)} of ${format(amount)}$unit"
        val done = times(progress, amount).coerceAtMost(goal.target)
        if (done >= goal.target) return "$done of ${goal.target} times"
        val partial = (progress - done * amount).coerceAtLeast(0.0)
        return "$done of ${goal.target} times · ${format(partial)} of ${format(amount)}$unit on the next"
    }

    /**
     * The user's usual minutes for one unit, from the latest [TIMINGS] timed entries of a goal, or
     * null before there are any.
     */
    fun minutesPerUnit(ticks: List<Tick>): Double? {
        val timed = ticks.filter { (it.seconds ?: 0) > 0 && (it.amount ?: 0.0) > 0 }.sortedByDescending { it.at }.take(TIMINGS)
        val amount = timed.sumOf { it.amount!! }
        if (timed.isEmpty() || amount <= 0) return null
        return timed.sumOf { it.seconds!! } / 60.0 / amount
    }

    /**
     * Minutes to budget for what is [left] of [goal]'s amount: its share of the typed time (for
     * one whole amount) if there is one, otherwise the usual speed, otherwise unknown.
     */
    fun minutesFor(goal: Goal, left: Double, minutesPerUnit: Double?): Int? {
        val amount = goal.amount ?: return null
        if (left <= 0) return 0
        goal.minutes?.let { typed -> return ceil(typed * left / amount).toInt().coerceAtLeast(1) }
        return minutesPerUnit?.let { ceil(it * left).toInt().coerceAtLeast(1) }
    }

    /** True when two amounts are the same to the hundredth. */
    fun same(a: Double, b: Double): Boolean = abs(a - b) < 0.005
}
