package io.github.randomtools3377.checklist.core

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

/**
 * One day's record: goals due and done, and the skips and misses that belong to it. A goal
 * covered by a skip is in neither [due] nor [done]: it drops out of what the day counts, and
 * shows only in [skips], the skips made on the day.
 */
data class DayRecord(val day: LocalDate, val due: Int, val done: Int, val misses: Int, val skips: Int, val overLimit: Int) {
    /** Nothing happened on it: such a day neither counts for a streak nor breaks one. */
    val isEmpty: Boolean get() = due == 0 && misses == 0 && skips == 0

    /** A perfect day, the same everywhere: something was due, all of it was done, and nothing was skipped. */
    val perfect: Boolean get() = due > 0 && done >= due && skips == 0
}

/** The current length, in days, of each streak. */
data class Streaks(
    /** Days meeting the user's target. */
    val done: Int,
    val noMisses: Int,
    val noSkips: Int,
    /** Days without going over the limit. */
    val withinLimit: Int,
    /** Perfect days: everything due done, and nothing skipped. */
    val perfect: Int,
) {
    /** True when every streak is running at once. */
    val allRunning: Boolean get() = listOf(done, noMisses, noSkips, withinLimit, perfect).all { it > 0 }
}

/**
 * The day [Goal.createdAt] falls on in [zone]: the day the goal was added, or brought back. Null
 * when it isn't known. Streaks and records count a goal from this day on, never before it.
 */
fun Goal.addedOn(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate? =
    if (createdAt <= 0) null else Instant.fromEpochMilliseconds(createdAt).toLocalDateTime(zone).date

/**
 * Each habit's window on a day, looking back. A "since last done" goal's repeat only knows the
 * day it is next due, so the days it was due before come from its history: each day it was
 * done, and each day it was skipped or missed. Days it stayed late in between don't count.
 */
internal class PastWindows(goals: List<Goal>, ticks: List<Tick>, lapses: List<Lapse>) {
    private val afterDone: Map<Long, Set<LocalDate>> = goals.filter { it.repeat?.kind == RepeatKind.AFTER_DONE }.associate { goal ->
        val done = ticks.filter { it.goalId == goal.id && it.stepId == null && it.period == goal.period }
            .groupBy { it.periodStart }
            .filterValues { goal.timesDone(it) >= goal.target }.keys
        val lapsed = lapses.filter { it.goalId == goal.id && !it.forgotten && (it.kind == LapseKind.MISS || it.kind == LapseKind.SKIP) }
            .map { it.periodStart }
        goal.id to (done + lapsed)
    }

    fun window(goal: Goal, day: LocalDate): Window? {
        val repeat = goal.repeat ?: return null
        val past = afterDone[goal.id] ?: return repeat.window(day)
        return if (day in past) Window(day, day, Period.DAILY) else repeat.window(day)
    }
}

object StreakCounter {
    /**
     * Each day's record from [from] to [to]. A single-day habit counts on its day; one due so
     * many times per week, month or year counts on the day its window ends. A skip is never
     * counted as done: a goal whose day was skipped, or whose times were only reached with skips,
     * drops out of the day, neither due nor done, and its skip still breaks the Skip-free streak
     * on the day it was made. One done after all (ticked as often as it needs) is done, skip or
     * no skip. A goal counts only from the day [countsFrom] gives it (the day it was added, or
     * brought back), so a new, copied or returned goal doesn't turn earlier days into days not done.
     */
    fun records(
        from: LocalDate,
        to: LocalDate,
        goals: List<Goal>,
        ticks: List<Tick>,
        lapses: List<Lapse>,
        countsFrom: (Goal) -> LocalDate? = { it.addedOn() },
    ): List<DayRecord> {
        val habits = goals.filter { it.isHabitLike && it.repeat != null }
        val firstDays = habits.associate { it.id to countsFrom(it) }
        val windows = PastWindows(habits, ticks, lapses)
        val lapsesByDay = lapses.filter { !it.forgotten }.groupBy { it.day }
        // Counted once up front, so a long stretch of history stays quick to go through.
        val ticksByWindow = HashMap<Triple<Long, Period?, LocalDate>, MutableList<Tick>>()
        for (tick in ticks) {
            if (tick.stepId != null) continue
            ticksByWindow.getOrPut(Triple(tick.goalId, tick.period, tick.periodStart)) { mutableListOf() } += tick
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
                val first = firstDays[goal.id]
                if (first != null && day < first) continue
                val repeat = goal.repeat!!
                val window = windows.window(goal, day) ?: continue
                if (window.end != day) continue
                val count = goal.timesDone(ticksByWindow[Triple(goal.id, goal.period, window.start)].orEmpty())
                val skips = skipCounts[goal.id to window.start] ?: 0
                val finished = count >= goal.target
                // Skipped: it no longer needs doing, and drops out of what the day counts.
                if (!finished && (if (window.period == Period.DAILY) skips > 0 else count + skips >= goal.target)) continue
                // A week, month or year only partly in the goal's dates counts once it is done.
                if (!finished && repeat.isPartial(window)) continue
                due++
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

    /**
     * Whether [record]'s day reaches the main streak's target. A day whose only due thing was
     * missed, like a project's deadline or a "since last done" goal's day, doesn't.
     */
    internal fun meetsTarget(record: DayRecord, settings: StreakSettings): Boolean = when {
        record.due == 0 -> record.misses == 0
        settings.target == StreakTarget.COUNT -> record.done >= minOf(settings.value, record.due)
        else -> record.done * 100 >= settings.value * record.due
    }

    /**
     * What [record]'s day does to the main streak: true when it reaches the target, false when
     * it breaks the streak, and null when it does neither. That is a day with nothing due and
     * nothing missed, which includes a day where everything due was skipped: a skip never counts
     * as done, but it doesn't break the main streak either.
     */
    fun mainDay(record: DayRecord, settings: StreakSettings): Boolean? =
        if (record.due == 0 && record.misses == 0) null else meetsTarget(record, settings)

    /**
     * Counts back from [today]. Days with nothing due neither count nor break a streak. Today
     * counts once it already qualifies. While it is still going it doesn't break the done and
     * perfect-day streaks, but a skip or going over the limit today has already happened, so
     * those break right away. A day with a skip is never a perfect day, and breaks the Skip-free
     * streak; for the main streak its skipped goals simply aren't there (see [mainDay]).
     */
    fun count(records: List<DayRecord>, today: LocalDate, settings: StreakSettings): Streaks {
        fun streak(todayCanBreak: Boolean = false, test: (DayRecord) -> Boolean?): Int {
            var length = 0
            for (record in records.sortedByDescending { it.day }) {
                if (record.day > today) continue
                if (record.isEmpty) continue
                val ok = test(record) ?: continue
                if (record.day == today && !ok && !todayCanBreak) continue
                if (!ok) break
                length++
            }
            return length
        }
        return Streaks(
            done = streak { mainDay(it, settings) },
            noMisses = streak { it.misses == 0 },
            noSkips = streak(todayCanBreak = true) { it.skips == 0 },
            withinLimit = streak(todayCanBreak = true) { it.overLimit == 0 },
            perfect = streak { it.perfect },
        )
    }
}
