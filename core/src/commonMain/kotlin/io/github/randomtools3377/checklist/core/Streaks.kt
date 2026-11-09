package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** What a day needs for the main streak: a number of goals done, or a percentage of those due. */
@Serializable
enum class StreakTarget { COUNT, PERCENT }

@Serializable
data class StreakSettings(val target: StreakTarget = StreakTarget.PERCENT, val value: Int = 90) {
    fun describe(): String = if (target == StreakTarget.COUNT) {
        if (value == 1) "1 goal a day" else "$value goals a day"
    } else {
        "$value% of each day's goals"
    }
}

/** One day's record: goals due and done, and the skips and misses that belong to it. */
data class DayRecord(val day: LocalDate, val due: Int, val done: Int, val misses: Int, val skips: Int, val overLimit: Int)

/** The current length, in days, of each streak. */
data class Streaks(
    /** Days meeting the user's target. */
    val done: Int,
    val noMisses: Int,
    val noSkips: Int,
    /** Days without going over the limit. */
    val withinLimit: Int,
    /** Days with everything due done. */
    val perfect: Int,
) {
    /** True when every streak is running at once. */
    val allRunning: Boolean get() = listOf(done, noMisses, noSkips, withinLimit, perfect).all { it > 0 }
}

object StreakCounter {
    /**
     * Each day's record from [from] to [to]. A single-day habit counts on its day; one due so
     * many times per week, month or year counts on the day its window ends. A skipped goal counts
     * as done for the day, but still breaks the no-skip streak.
     */
    fun records(from: LocalDate, to: LocalDate, goals: List<Goal>, ticks: List<Tick>, lapses: List<Lapse>): List<DayRecord> {
        val habits = goals.filter { it.kind == GoalKind.HABIT && it.repeat != null }
        val lapsesByDay = lapses.filter { !it.forgotten }.groupBy { it.day }
        // Counted once up front, so a long stretch of history stays quick to go through.
        val tickCounts = HashMap<Triple<Long, Period?, LocalDate>, Int>()
        for (tick in ticks) {
            if (tick.stepId != null) continue
            val key = Triple(tick.goalId, tick.period, tick.periodStart)
            tickCounts[key] = (tickCounts[key] ?: 0) + 1
        }
        val skipCounts = HashMap<Pair<Long, LocalDate>, Int>()
        for (lapse in lapses) {
            if (lapse.forgotten || lapse.kind != LapseKind.SKIP) continue
            val key = lapse.goalId to lapse.periodStart
            skipCounts[key] = (skipCounts[key] ?: 0) + 1
        }
        val records = mutableListOf<DayRecord>()
        var day = from
        while (day <= to) {
            var due = 0
            var done = 0
            for (goal in habits) {
                val window = goal.repeat!!.window(day) ?: continue
                if (window.end != day) continue
                due++
                val count = tickCounts[Triple(goal.id, goal.period, window.start)] ?: 0
                val skips = skipCounts[goal.id to window.start] ?: 0
                val finished = if (window.period == Period.DAILY) count >= goal.target || skips > 0 else count + skips >= goal.target
                if (finished) done++
            }
            val onDay = lapsesByDay[day].orEmpty()
            records += DayRecord(
                day = day,
                due = due,
                done = done,
                misses = onDay.count { it.kind == LapseKind.MISS },
                skips = onDay.count { it.kind == LapseKind.SKIP },
                overLimit = onDay.count { it.overLimit },
            )
            day = day.plusDays(1)
        }
        return records
    }

    internal fun meetsTarget(record: DayRecord, settings: StreakSettings): Boolean = when (settings.target) {
        StreakTarget.COUNT -> record.done >= minOf(settings.value, record.due)
        StreakTarget.PERCENT -> record.done * 100 >= settings.value * record.due
    }

    /**
     * Counts back from [today]. Days with nothing due neither count nor break a streak. Today
     * counts once it already qualifies. While it is still going it doesn't break the done and
     * perfect-day streaks, but a skip or going over the limit today has already happened, so
     * those break right away.
     */
    fun count(records: List<DayRecord>, today: LocalDate, settings: StreakSettings): Streaks {
        fun streak(todayCanBreak: Boolean = false, test: (DayRecord) -> Boolean): Int {
            var length = 0
            for (record in records.sortedByDescending { it.day }) {
                if (record.day > today) continue
                if (record.due == 0 && record.misses == 0 && record.skips == 0) continue
                val ok = test(record)
                if (record.day == today && !ok && !todayCanBreak) continue
                if (!ok) break
                length++
            }
            return length
        }
        return Streaks(
            done = streak { meetsTarget(it, settings) },
            noMisses = streak { it.misses == 0 },
            noSkips = streak(todayCanBreak = true) { it.skips == 0 },
            withinLimit = streak(todayCanBreak = true) { it.overLimit == 0 },
            perfect = streak { it.due > 0 && it.done >= it.due },
        )
    }
}
