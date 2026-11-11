package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * The budget game, an extra of its own. Each day has a share of the month's money: the month's
 * pay and other money in, less its bills, spread evenly over its days. A day that spends no more
 * than its share (bills and savings aside) is a good money day and pays points; a day with no
 * spending pays more. Good days in a row make a money streak. A month that stays within every
 * category's limit pays a bonus, and so does reaching a saving goal, once each.
 */
object MoneyGame {
    /** Settings: the day the game started, the last day paid for, and the saving goals paid for. */
    const val FROM = "money_game_from"
    const val TO = "money_game_to"
    const val GOALS = "money_game_goals"

    /** What the game pays: the user's to change (see [Rules]). */
    val GOOD_DAY: Int get() = Rules.current.goodMoneyDay
    val NO_SPEND_DAY: Int get() = Rules.current.noSpendDay
    val MONTH_BONUS: Int get() = Rules.current.moneyMonthBonus
    val SAVED_UP: Int get() = Rules.current.savedUp

    enum class Day { NO_SPEND, WITHIN, OVER }

    /** Points for the budget game, with what they were for. */
    data class Payout(val day: LocalDate, val points: Int, val label: String)

    private fun inMonth(entries: List<MoneyEntry>, first: LocalDate) =
        entries.filter { it.day.year == first.year && it.day.month == first.month }

    /** A day's share of [first]'s month, in money; null when there is nothing to share out. */
    fun share(plan: PayPlan?, first: LocalDate, changes: Map<LocalDate, WorkDay>, entries: List<MoneyEntry>): Double? {
        val month = inMonth(entries, first)
        val pay = plan?.takeIf { it.complete }?.let { p -> Pay.monthDays(first).sumOf { Pay.day(p, it, changes[it]).total } } ?: 0.0
        val received = month.filter { it.kind == MoneyKind.RECEIVED }.sumOf { it.cents } / 100.0
        val bills = month.filter { it.kind == MoneyKind.SPENT && it.billId != null }.sumOf { it.cents } / 100.0
        val pool = pay + received - bills
        return if (pay + received <= 0.0) null else maxOf(0.0, pool) / Pay.monthDays(first).size
    }

    /** What [day] spent, bills aside, in cents. */
    fun spent(day: LocalDate, entries: List<MoneyEntry>): Long =
        entries.filter { it.day == day && it.kind == MoneyKind.SPENT && it.billId == null }.sumOf { it.cents }

    fun day(spentCents: Long, share: Double): Day = when {
        spentCents == 0L -> Day.NO_SPEND
        spentCents <= Budget.cents(share) -> Day.WITHIN
        else -> Day.OVER
    }

    /** Whether [first]'s month kept within every category limit; null when no category has one. */
    fun withinLimits(first: LocalDate, categories: List<MoneyCategory>, entries: List<MoneyEntry>): Boolean? {
        val limited = categories.filter { !it.archived && it.limitCents != null }
        if (limited.isEmpty()) return null
        val month = inMonth(entries, first).filter { it.kind == MoneyKind.SPENT }
        return limited.all { category -> month.filter { it.categoryId == category.id }.sumOf { it.cents } <= category.limitCents!! }
    }

    /**
     * The points owed for each day from [from] to [to] (both included), and for each month that
     * ended in that time. [shareOf] gives a day's share, or null for a day with nothing to share.
     */
    fun settle(
        from: LocalDate,
        to: LocalDate,
        shareOf: (LocalDate) -> Double?,
        entries: List<MoneyEntry>,
        categories: List<MoneyCategory>,
    ): List<Payout> {
        val payouts = mutableListOf<Payout>()
        var d = from
        while (d <= to) {
            shareOf(d)?.let { share ->
                when (day(spent(d, entries), share)) {
                    Day.NO_SPEND -> payouts += Payout(d, NO_SPEND_DAY, "Money: a no-spend day")
                    Day.WITHIN -> payouts += Payout(d, GOOD_DAY, "Money: a day within its share")
                    Day.OVER -> {}
                }
            }
            val next = d.plusDays(1)
            if (next.month != d.month && withinLimits(Budget.monthOf(d), categories, entries) == true) {
                payouts += Payout(d, MONTH_BONUS, "Money: a month within every limit")
            }
            d = next
        }
        return payouts
    }

    /** Good money days in a row up to [to], counting back no further than [from]. */
    fun streak(from: LocalDate, to: LocalDate, shareOf: (LocalDate) -> Double?, entries: List<MoneyEntry>): Int {
        var count = 0
        var d = to
        while (d >= from) {
            val share = shareOf(d) ?: break
            if (day(spent(d, entries), share) == Day.OVER) break
            count++
            d = d.minusDays(1)
        }
        return count
    }

    /** Saving goals reached and not yet paid for, among [paid] ids. */
    fun savedUp(savings: List<SavingProgress>, paid: Set<Long>): List<SavingProgress> =
        savings.filter { it.reached && it.goal.id !in paid }

    fun decodeIds(text: String?): Set<Long> = text.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()

    fun encodeIds(ids: Set<Long>): String = ids.sorted().joinToString(",")
}
