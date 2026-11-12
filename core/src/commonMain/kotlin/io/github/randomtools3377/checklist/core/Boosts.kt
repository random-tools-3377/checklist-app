package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * The boost: the goal missed most over the 4 weeks before this week (2 misses or more) pays 50%
 * more all week, to make it more tempting. Chosen afresh each Monday. It can be turned off. The
 * numbers are the user's to change (see [Rules]).
 */
object Boost {
    /** "0" turns it off. */
    const val SETTING = "boost"
    /** What the boosted goal pays, in percent. */
    val PERCENT: Int get() = 100 + Rules.current.boostExtraPercent
    val MIN_MISSES: Int get() = Rules.current.boostMinMisses
    const val DAYS = 28

    /** The Monday that starts the week of [day]. */
    fun weekOf(day: LocalDate): LocalDate = day.minusDays(day.dayOfWeek.isoDayNumber - 1)

    /** The boosted goal in the week of [day]: the one with most misses, earliest in the user's order on a tie. */
    fun goalFor(day: LocalDate, goals: List<Goal>, lapses: List<Lapse>): Goal? {
        val monday = weekOf(day)
        val from = monday.minusDays(DAYS)
        val misses = lapses.filter { it.kind == LapseKind.MISS && !it.forgotten && it.day >= from && it.day < monday }
            .groupingBy { it.goalId }.eachCount()
        val least = MIN_MISSES
        return goals.filter { !it.archived && it.points > 0 && (misses[it.id] ?: 0) >= least }
            .sortedWith(compareByDescending<Goal> { misses[it.id] ?: 0 }.thenBy { it.position }.thenBy { it.id })
            .firstOrNull()
    }
}

/**
 * What a goal pays for [points] on a day: its difficulty, and the boost if it is the boosted
 * goal, multiplied together and rounded down once. Something paying more than 0 pays at least 1.
 */
fun payFor(points: Int, difficulty: Difficulty, boosted: Boolean, streakPercent: Int = 100): Int {
    if (points <= 0) return points
    val percent = difficulty.percent.toLong() * (if (boosted) Boost.PERCENT else 100) * streakPercent / 10_000
    return maxOf(1, (points * percent / 100).toInt())
}

/**
 * The streak bonus: a tick pays 10% more from a 7-day main streak, 20% from 30 and 30% from 100
 * (unless the user changed them, see [Rules]), counted to the end of yesterday so it holds all
 * day. It can be turned off.
 */
object StreakBonus {
    /** "0" turns it off. */
    const val SETTING = "streak_bonus"

    /** The settings the main streak's target is kept in (see [StreakSettings]). */
    const val TARGET = "streak_target"
    const val VALUE = "streak_value"

    /** What a tick pays with a main streak of [streak] days, in percent: the step with the longest streak reached. */
    fun percentFor(streak: Int): Int {
        val r = Rules.current
        val steps = listOf(
            r.streakBonus3Days to r.streakBonus3Percent,
            r.streakBonus2Days to r.streakBonus2Percent,
            r.streakBonus1Days to r.streakBonus1Percent,
        )
        return 100 + (steps.sortedByDescending { it.first }.firstOrNull { streak >= it.first }?.second ?: 0)
    }

    fun settingsOf(target: String?, value: String?): StreakSettings {
        val defaults = StreakSettings()
        val kind = StreakTarget.entries.firstOrNull { it.name == target } ?: defaults.target
        return StreakSettings(kind, value?.toIntOrNull() ?: defaults.value)
    }
}

/**
 * The perfect-day bonus: a day when everything due was done, none of it skipped, pays a bonus
 * when it ends. Days count from the first time the app looked, never further back.
 */
class PerfectDays(private val store: Store, private val book: Book) {
    companion object {
        /** The bonus in points; "0" turns it off. */
        const val BONUS = "perfect_day_bonus"
        const val DEFAULT_BONUS = 10

        /** The last day looked at. */
        const val THROUGH = "perfect_through"

        /**
         * The first day the bonus counts from: the day after the last one that ended while it
         * was off, so a day put right later (see [DayBack]) isn't paid for a time it was off.
         * Not set for someone who has had it on since before this was kept.
         */
        const val SINCE = "perfect_since"

        /** What the bonus's line in the points history says. */
        const val LABEL = "Perfect day"

        /** The bonus while the feature is on; off (0) until the user turns it on. */
        suspend fun bonus(store: Store): Int = store.setting(BONUS)?.toIntOrNull()?.coerceIn(0, 10_000) ?: 0

        /**
         * Whether [record]'s day is perfect: something was due, everything due was done, and
         * nothing was skipped. Today's "Perfect day so far" uses the same test as the bonus.
         */
        fun isPerfect(record: DayRecord): Boolean = record.due > 0 && record.done >= record.due && record.skips == 0
    }

    /** Pays the bonus for every perfect day that ended since the last time. Returns what was paid. */
    suspend fun pay(): List<Entry> = store.transaction {
        val yesterday = book.today().minusDays(1)
        val through = store.setting(THROUGH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (through == null) {
            store.putSetting(THROUGH, yesterday.toString())
            store.putSetting(SINCE, yesterday.plusDays(1).toString())
            return@transaction emptyList()
        }
        val bonus = bonus(store)
        val paid = mutableListOf<Entry>()
        var day = through.plusDays(1)
        // A day that ended while it was off isn't paid: it counts from today.
        if (bonus <= 0 && day <= yesterday) store.putSetting(SINCE, yesterday.plusDays(1).toString())
        if (day <= yesterday && bonus > 0) {
            val goals = store.activeGoals()
            val ticks = store.habitTicksSince(Days.earliestOpenDay(day))
            val lapses = store.allLapses(day, yesterday)
            for (record in StreakCounter.records(day, yesterday, goals, ticks, lapses)) {
                if (isPerfect(record)) {
                    val entry = Entry(at = book.millis(), day = record.day, kind = EntryKind.PERFECT_DAY, amount = bonus, label = "Perfect day")
                    paid += entry.copy(id = store.addEntry(entry))
                }
            }
        }
        if (yesterday > through) store.putSetting(THROUGH, yesterday.toString())
        paid
    }
}

/**
 * A reward costs more for each time it was already bought that week (Monday to Sunday, not
 * counting ones returned): 25% of its price each time (see [Rules]), rounded down. It can be
 * turned off.
 */
object RepeatPrice {
    /** "0" turns it off. */
    const val SETTING = "repeat_price"
    val RISE_PERCENT: Int get() = Rules.current.repeatPriceRisePercent

    fun now(price: Int, boughtThisWeek: Int, on: Boolean = true): Int =
        if (!on || boughtThisWeek <= 0) price
        else (price + price.toLong() * RISE_PERCENT * boughtThisWeek / 100).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
