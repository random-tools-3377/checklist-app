package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
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
        val from = maxOf(firstDay, today.minusDays(WINDOW_DAYS))
        val to = today.minusDays(1)
        val days = if (from > to) listOf(today) else generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
        val average = days.sumOf { earnedByDay[it] ?: 0 }.toDouble() / days.size
        return average.takeIf { it > 0.0 }
    }

    /**
     * The one "days of earning" figure: how many whole days [price] takes at [pointsPerDay], what
     * the user actually earns a day. 0 stands for less than a day (under three quarters of one);
     * from there it is rounded to the nearest day. Null when there is no usual yet, or no price.
     * Every screen that says how long a price takes asks here, so the same price shows the same
     * number everywhere.
     */
    fun days(price: Int, pointsPerDay: Double?): Int? {
        if (pointsPerDay == null || pointsPerDay <= 0.0 || price <= 0) return null
        val days = price / pointsPerDay
        if (days < 0.75) return 0
        return days.roundToInt().coerceAtLeast(1)
    }

    /** "≈ 4 days of your usual" for [price], or null when there is no usual yet. */
    fun describe(price: Int, pointsPerDay: Double?): String? = when (val days = days(price, pointsPerDay)) {
        null -> null
        0 -> "less than a day of your usual"
        1 -> "≈ 1 day of your usual"
        else -> "≈ $days days of your usual"
    }

    /** "≈ 4 days", for a line that already says what the days are of; null when there is no usual yet. */
    fun short(price: Int, pointsPerDay: Double?): String? = when (val days = days(price, pointsPerDay)) {
        null -> null
        0 -> "less than a day"
        1 -> "≈ 1 day"
        else -> "≈ $days days"
    }

    /** "about 4 more days" to earn the [left] that is still missing; null when there is no usual yet, or nothing left. */
    fun more(left: Int, pointsPerDay: Double?): String? = when (val days = days(left, pointsPerDay)) {
        null -> null
        0 -> "less than a day more"
        1 -> "about 1 more day"
        else -> "about $days more days"
    }
}

/**
 * The one shop reward the user starred as the thing they are saving for, at what it costs now,
 * against the [balance] and what they usually earn a day ([perDay], see [Usual.pointsPerDay]).
 */
data class SavingFor(val reward: Reward, val balance: Int, val perDay: Double?) {
    /** The balance covers it. */
    val canBuy: Boolean get() = balance >= reward.price

    /** What counts toward it: nothing while the balance is below zero. */
    val have: Int get() = balance.coerceIn(0, reward.price)

    /** What is still missing, which is more than the price while the balance is below zero. */
    val left: Int get() = (reward.price.toLong() - balance).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    /** How full the bar is, 0 to 1. */
    val progress: Float get() = if (reward.price <= 0) 1f else have.toFloat() / reward.price

    /** "about 4 more days"; null once it can be bought, or when there is no usual yet. */
    val more: String? get() = if (canBuy) null else Usual.more(left, perDay)

    /** "about 4 more days at your usual pace", for Today. */
    val pace: String? get() = more?.let { "$it at your usual pace" }

    /** "120 of 300 points", in the user's own word for points. */
    fun count(name: PointsName = PointsName.current): String = "$have of ${name.amount(reward.price)}"

    /** One short line for the morning plan. */
    fun morningLine(name: PointsName = PointsName.current): String =
        if (canBuy) "${reward.icon} ${reward.name}: you can buy it now"
        else "Saving for ${reward.icon} ${reward.name}: ${count(name)}" + (more?.let { ", $it" } ?: "")

    companion object {
        /** The setting that holds the starred reward's id; empty or missing when none is starred. */
        const val SETTING = "shop_saving_for"

        /** The id [setting] holds, or null. */
        fun starred(setting: String?): Long? = setting?.toLongOrNull()?.takeIf { it > 0 }

        /**
         * What the user is saving for: the reward [setting] names, if it is still in the shop
         * ([rewards], each at what it costs now). Null with no star, or once the reward is removed.
         */
        fun of(setting: String?, rewards: List<Reward>, balance: Int, perDay: Double?): SavingFor? {
            val id = starred(setting) ?: return null
            val reward = rewards.firstOrNull { it.id == id && !it.archived } ?: return null
            return SavingFor(reward, balance, perDay)
        }
    }
}
