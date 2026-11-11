package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * Prices that follow what a reward costs in money. The weekly treat budget links money to points:
 * doing everything due in a week earns that much in rewards. So a point is worth the budget over
 * what a full week of goals pays at Normal, and a reward's price is its money value in points.
 *
 * The rate keeps in step with the goals: adding goals, or making them pay more, raises it, and
 * the prices of rewards that follow money move with it. It only moves once it has drifted more
 * than [DRIFT] from the one in use, so prices don't shift by a point or two from day to day.
 */
object MoneyPrices {
    /** The setting holding the rate in use (see [encode]). */
    const val RATE = "money_rate"

    /** How far what the goals pay can drift from the rate in use before prices move: a tenth, unless the user changed it (see [Rules]). */
    val DRIFT: Double get() = Rules.current.moneyDriftPercent / 100.0

    /** A full week is worked out over 13 weeks, so goals due monthly count fairly. */
    const val DAYS = 91

    /** Points for 1 of money, and the weekly treat budget it was worked out with. */
    data class Rate(val pointsPerUnit: Double, val budget: Double)

    /**
     * The rate from the goals as they are now: what a week of doing everything due would pay at
     * Normal, over the [budget]. Null without a budget or a repeating goal that pays.
     */
    fun fresh(goals: List<Goal>, today: LocalDate, budget: Double?): Rate? {
        if (budget == null || budget <= 0.0) return null
        val perDay = Suggest.expectedPerDay(goals, today, DAYS) ?: return null
        return Rate(perDay * 7 / budget, budget)
    }

    /**
     * The rate to use: [fresh] when there is none yet, when the budget changed, or when what the
     * goals pay has drifted more than [DRIFT] from [stored]; otherwise [stored]. Without a fresh
     * one (no budget, or no goals that pay), prices stay where they are.
     */
    fun next(stored: Rate?, fresh: Rate?): Rate? = when {
        fresh == null -> stored
        stored == null || stored.budget != fresh.budget -> fresh
        kotlin.math.abs(fresh.pointsPerUnit / stored.pointsPerUnit - 1.0) > DRIFT -> fresh
        else -> stored
    }

    /** What [value] in money costs in points at [rate], rounded to something easy to read. */
    fun price(value: Double, rate: Rate): Int = Suggest.nice(value * rate.pointsPerUnit)

    /** The rewards whose price follows money and needs to change at [rate], with their new price. */
    fun changes(rewards: List<Reward>, rate: Rate): Map<Long, Int> =
        rewards.filter { !it.archived && it.followsMoney && it.value != null && it.value > 0.0 }
            .associate { it.id to price(it.value!!, rate) }
            .filter { (id, price) -> rewards.first { it.id == id }.price != price }

    /** Stored as "points per unit|budget", like "4.2|50.0". */
    fun encode(rate: Rate): String = "${rate.pointsPerUnit}|${rate.budget}"

    fun decode(text: String?): Rate? {
        val parts = text?.split("|") ?: return null
        if (parts.size != 2) return null
        val points = parts[0].toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() } ?: return null
        val budget = parts[1].toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() } ?: return null
        return Rate(points, budget)
    }
}
