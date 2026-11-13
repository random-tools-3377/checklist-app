package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * Comebacks: days the user got back to their day's target right after a day that fell short of
 * it. The rule is the main streak's own (see [StreakCounter.mainDay]): a day counts as reached
 * or not reached by the user's target, and a day with nothing due, or with everything due
 * skipped, is passed over, so the day before a comeback is the last day that had something to do.
 * Counted from the day tracking began, never further back: before it, nothing was looked for.
 */
object Comebacks {
    /**
     * The days that were comebacks, in order. [records] are the streaks' own day records;
     * [since] is the day tracking began, and with none there are no comebacks. Today, while it
     * is still going and hasn't reached the target, is neither a comeback nor a day that fell
     * short.
     */
    fun days(records: List<DayRecord>, today: LocalDate, settings: StreakSettings, since: LocalDate?): List<LocalDate> {
        if (since == null) return emptyList()
        val found = mutableListOf<LocalDate>()
        var fellShort = false
        for (record in records.filter { it.day in since..today }.sortedBy { it.day }) {
            val reached = StreakCounter.mainDay(record, settings) ?: continue
            if (record.day == today && !reached) continue
            if (reached && fellShort) found += record.day
            fellShort = !reached
        }
        return found
    }

    /** The day tracking began, as kept in the settings (see [Lapses.TRACKING_SINCE]); null when it hasn't, or can't be read. */
    fun since(setting: String?): LocalDate? =
        setting?.let { try { LocalDate.parse(it) } catch (e: IllegalArgumentException) { null } }
}
