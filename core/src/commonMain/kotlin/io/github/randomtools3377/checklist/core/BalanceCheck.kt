package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** How a reward's price sits against what the user usually earns. */
enum class PriceVerdict { TOO_CHEAP, FAIR, OUT_OF_REACH }

/** A reward in the balance check: how many days of earning it takes, and what it could cost instead. */
data class RewardCheck(val reward: Reward, val days: Double?, val verdict: PriceVerdict?, val suggested: Int?)

/** A goal that could pay differently: one nearly always done, or one often missed. */
enum class GoalHint { PAY_LESS, PAY_MORE }

/** A goal in the balance check: how often it was done over the last 4 weeks, and what it could pay. */
data class GoalCheck(val goal: Goal, val due: Int, val done: Int, val hint: GoalHint?, val suggested: Int?) {
    val share: Double get() = if (due == 0) 0.0 else done.toDouble() / due
}

/**
 * The balance check in the shop: whether prices suit what the user earns, and whether goals pay
 * what suits how often they get done.
 */
object BalanceCheck {
    /** Under half a day of earning is too cheap; over 2 months is out of reach. */
    const val CHEAP_DAYS = 0.5
    const val FAR_DAYS = 60.0

    /** Goals are judged over the last 4 weeks, once they were due at least 4 times. */
    const val DAYS = 28
    const val MIN_DUE = 4

    /** Nearly always done (95% or more) could pay less; done half the time or less could pay more. */
    const val EASY_SHARE = 0.95
    const val HARD_SHARE = 0.5

    /**
     * Each reward with its days of earning at [perDay] (what the user earns now) and a verdict.
     * A suggested price comes from its size or money value where it has one ([perDayAtNormal]
     * and [budget], see [Suggest.price]), or else, for one too cheap or out of reach, a day's or
     * six weeks' worth.
     */
    fun rewards(rewards: List<Reward>, perDay: Double?, perDayAtNormal: Double?, budget: Double?): List<RewardCheck> =
        rewards.filter { !it.archived }.map { reward ->
            val days = perDay?.takeIf { it > 0 }?.let { reward.price / it }
            val verdict = days?.let {
                when {
                    it < CHEAP_DAYS -> PriceVerdict.TOO_CHEAP
                    it > FAR_DAYS -> PriceVerdict.OUT_OF_REACH
                    else -> PriceVerdict.FAIR
                }
            }
            val fromValue = Suggest.price(reward.size, reward.value, perDayAtNormal, budget)
            val suggested = fromValue ?: when (verdict) {
                PriceVerdict.TOO_CHEAP -> perDay?.let { Suggest.nice(it) }
                PriceVerdict.OUT_OF_REACH -> perDay?.let { Suggest.nice(it * RewardSize.HUGE.days) }
                else -> null
            }
            RewardCheck(reward, days, verdict, suggested?.takeIf { it != reward.price })
        }

    /**
     * Each goal due at least [MIN_DUE] times over the last [DAYS] days (from when it was made,
     * [madeOn]), with how many of those it was done, and a hint where it could pay differently:
     * three quarters of its points if nearly always done, half as much again if often missed.
     */
    fun goals(goals: List<Goal>, ticks: List<Tick>, today: LocalDate, madeOn: (Goal) -> LocalDate = { today.minusDays(DAYS) }): List<GoalCheck> {
        val from = today.minusDays(DAYS)
        return goals.filter { !it.archived && it.isHabitLike && it.points > 0 }.mapNotNull { goal ->
            val repeat = goal.repeat ?: return@mapNotNull null
            val start = maxOf(from, madeOn(goal))
            var due = 0
            var done = 0
            var day = start
            while (day < today) {
                val window = repeat.window(day)
                if (window != null && window.end == day && window.start >= start) {
                    due++
                    if (goal.timesDone(ticks.filter { it.countsToward(goal, window.start) }) >= goal.target) done++
                }
                day = day.plusDays(1)
            }
            if (due < MIN_DUE) return@mapNotNull null
            val share = done.toDouble() / due
            val hint = when {
                share >= EASY_SHARE -> GoalHint.PAY_LESS
                share <= HARD_SHARE -> GoalHint.PAY_MORE
                else -> null
            }
            val suggested = when (hint) {
                GoalHint.PAY_LESS -> maxOf(1, goal.points * 3 / 4).takeIf { it < goal.points }
                GoalHint.PAY_MORE -> goal.points * 3 / 2
                null -> null
            }
            GoalCheck(goal, due, done, hint, suggested)
        }
    }
}
