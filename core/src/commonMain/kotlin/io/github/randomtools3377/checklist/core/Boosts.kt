package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * The boost: the goal missed most over the 4 weeks before this week (2 misses or more) pays 50%
 * more all week, to make it more tempting. Chosen afresh each Monday. It can be turned off.
 */
object Boost {
    /** "0" turns it off. */
    const val SETTING = "boost"
    const val PERCENT = 150
    const val MIN_MISSES = 2
    const val DAYS = 28

    /** The Monday that starts the week of [day]. */
    fun weekOf(day: LocalDate): LocalDate = day.minusDays(day.dayOfWeek.isoDayNumber - 1)

    /** The boosted goal in the week of [day]: the one with most misses, earliest in the user's order on a tie. */
    fun goalFor(day: LocalDate, goals: List<Goal>, lapses: List<Lapse>): Goal? {
        val monday = weekOf(day)
        val from = monday.minusDays(DAYS)
        val misses = lapses.filter { it.kind == LapseKind.MISS && !it.forgotten && it.day >= from && it.day < monday }
            .groupingBy { it.goalId }.eachCount()
        return goals.filter { !it.archived && it.points > 0 && (misses[it.id] ?: 0) >= MIN_MISSES }
            .sortedWith(compareByDescending<Goal> { misses[it.id] ?: 0 }.thenBy { it.position }.thenBy { it.id })
            .firstOrNull()
    }
}

/**
 * What a goal pays for [points] on a day: its difficulty, and the boost if it is the boosted
 * goal, multiplied together and rounded down once. Something paying more than 0 pays at least 1.
 */
fun payFor(points: Int, difficulty: Difficulty, boosted: Boolean): Int {
    if (points <= 0) return points
    val percent = difficulty.percent * (if (boosted) Boost.PERCENT else 100) / 100
    return maxOf(1, points * percent / 100)
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

        suspend fun bonus(store: Store): Int = store.setting(BONUS)?.toIntOrNull()?.coerceIn(0, 10_000) ?: DEFAULT_BONUS
    }

    /** Pays the bonus for every perfect day that ended since the last time. Returns what was paid. */
    suspend fun pay(): List<Entry> = store.transaction {
        val yesterday = book.today().minusDays(1)
        val through = store.setting(THROUGH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (through == null) {
            store.putSetting(THROUGH, yesterday.toString())
            return@transaction emptyList()
        }
        val bonus = bonus(store)
        val paid = mutableListOf<Entry>()
        var day = through.plusDays(1)
        if (day <= yesterday && bonus > 0) {
            val goals = store.activeGoals()
            val ticks = store.habitTicksSince(Days.earliestOpenDay(day))
            val lapses = store.allLapses(day, yesterday)
            for (record in StreakCounter.records(day, yesterday, goals, ticks, lapses)) {
                if (record.due > 0 && record.done >= record.due && record.skips == 0) {
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
 * counting ones returned): 25% of its price each time, rounded down. It can be turned off.
 */
object RepeatPrice {
    /** "0" turns it off. */
    const val SETTING = "repeat_price"
    const val RISE_PERCENT = 25

    fun now(price: Int, boughtThisWeek: Int, on: Boolean = true): Int =
        if (!on || boughtThisWeek <= 0) price else price + price * RISE_PERCENT * boughtThisWeek / 100
}
