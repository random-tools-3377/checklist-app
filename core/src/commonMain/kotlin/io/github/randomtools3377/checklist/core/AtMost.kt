package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * "At most" goals, like no more than 2 coffees or under 2 hours of screen time. The user adds
 * each one as it happens ("+1", or an amount in the goal's unit), and the card shows how much
 * is left under the limit. A day that ends at or under the limit is done and pays the goal's
 * points: when the day ends (see [Lapses.closeDays]), or straight away with "That's all for
 * today" (an ordinary tick, see [Book.tickHabit]). Going over the limit is a miss straight
 * away, like "I slipped" on a goal to quit (see [Lapses.use]).
 *
 * Each use is a tick of the goal with no period (see [Tick.isUse]), so nothing that counts
 * ticks toward a goal being done ever counts one. Which goals are "at most", and each one's
 * limit, is kept in the setting [SETTING] by goal id, so it travels in the backup file.
 */
object AtMost {
    /** The limits, as "12:2,15:120" for goals 12 and 15. */
    const val SETTING = "at_most_goals"

    /** The most a limit, or one entry, can be. */
    const val MAX = Amounts.MAX

    fun decode(text: String?): Map<Long, Double> = text.orEmpty().split(",").mapNotNull { part ->
        val (id, limit) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        val goalId = id.trim().toLongOrNull() ?: return@mapNotNull null
        val value = limit.trim().toDoubleOrNull()?.let { Amounts.tidy(it) } ?: return@mapNotNull null
        goalId to value
    }.toMap()

    fun encode(limits: Map<Long, Double>): String =
        limits.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${Amounts.format(it.value)}" }

    /** [text] with [goalId]'s limit set to [limit], or taken out when it is null. */
    fun with(text: String?, goalId: Long, limit: Double?): String {
        val tidy = limit?.let { Amounts.tidy(it) }
        val limits = decode(text)
        return encode(if (tidy == null) limits - goalId else limits + (goalId to tidy))
    }

    /** [text] with only the goals that still exist. */
    fun kept(text: String?, goalIds: Set<Long>): String = encode(decode(text).filterKeys { it in goalIds })

    /**
     * Whether a goal that repeats like [repeat] can be an "at most" goal: its limit is for a
     * day, so not one counted over a week, month or year, nor one due "some days after it's
     * last done".
     */
    fun fits(repeat: Repeat?): Boolean =
        repeat != null && repeat.kind != RepeatKind.TIMES_PER && repeat.kind != RepeatKind.AFTER_DONE

    /** How much of [goal] was used in the window that starts on [periodStart]; [ticks] may hold others. */
    fun used(goal: Goal, ticks: List<Tick>, periodStart: LocalDate): Double =
        ticks.filter { it.isUse && it.goalId == goal.id && it.periodStart == periodStart }.sumOf { it.amount ?: 1.0 }

    /** Over the limit, allowing for rounding in decimals. Exactly at the limit is still under it. */
    fun over(used: Double, limit: Double): Boolean = used > limit + 1e-6

    /** How much is left under the limit; 0 at or over it. */
    fun left(used: Double, limit: Double): Double = (limit - used).coerceAtLeast(0.0).let { if (it < 1e-6) 0.0 else it }

    /** [ticks] without the uses of "at most" goals, for whatever counts ticks as things done. */
    fun withoutUses(ticks: List<Tick>): List<Tick> = ticks.filter { !it.isUse }

    /** "2 coffees", "120 minutes", or just "2" without a unit. */
    fun limitText(goal: Goal): String = amountText(goal.limit ?: 0.0, goal)

    private fun amountText(amount: Double, goal: Goal): String =
        Amounts.format(amount) + (goal.unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")

    /**
     * "1 of 2 used, 1 left", "45 of 120 minutes used, 75 left", "2 of 2 used, none left", or
     * past the limit "3 of 2 used, 1 over".
     */
    fun text(used: Double, goal: Goal): String {
        val limit = goal.limit ?: 0.0
        val start = "${Amounts.format(used)} of ${amountText(limit, goal)} used"
        return when {
            over(used, limit) -> "$start, ${Amounts.format(used - limit)} over"
            left(used, limit) == 0.0 -> "$start, none left"
            else -> "$start, ${Amounts.format(left(used, limit))} left"
        }
    }

    /** What the evening check says of one still under its limit and not yet confirmed. */
    const val STILL_UNDER = "still under your limit"
}

/** What adding to an "at most" goal did (see [Lapses.use]). */
sealed interface UseResult {
    /**
     * Added: [used] of [limit] now. Going over made [slip], the day's miss, unless the day was
     * skipped or already missed; [tookBack] is what an earlier "That's all for today" had paid,
     * taken back.
     */
    data class Added(val tickId: Long, val used: Double, val limit: Double, val slip: Lapse? = null, val penaltyId: Long? = null, val tookBack: Int = 0) : UseResult {
        val over: Boolean get() = AtMost.over(used, limit)
    }

    /** The goal isn't due today. */
    data object NotDue : UseResult

    /** No such "at most" goal, or not an amount above 0. */
    data object NotFound : UseResult
}
