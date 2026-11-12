package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * What a day gets back when a miss on it turns out to be a forgotten tick (see
 * [Lapses.forgotToTick]). Once everything due on that day is done, none of it skipped, the day
 * is perfect after all:
 * - its perfect-day bonus is paid, if it wasn't (see [PerfectDays]);
 * - the combo of perfect days carries on through it, as if it had been perfect all along: the
 *   day pays for its place in the combo, each perfect day after it is topped up to what it would
 *   have paid, and the combo stands where it would have (see [Combos]). A ×12 before the day
 *   stays a ×12 that grew, instead of starting again.
 *
 * Nothing is paid twice: a day whose bonus was paid is left alone, and a day in the combo only
 * ever gets the difference to what its new place pays. A day that hasn't ended, or that the
 * day's end hasn't looked at yet, is left to the day's end. The day's streaks need nothing
 * here: they are counted from the ticks and misses each time.
 */
class DayBack(private val store: Store, private val book: Book) {
    companion object {
        /** How far back the combo before the day is looked for. */
        const val LOOK_BACK_DAYS = Gaps.LOOK_BACK_DAYS

        /** The number of days in a combo's line in the points history, like 12 for "Combo ×12". */
        fun comboOf(entry: Entry): Int? =
            if (entry.kind == EntryKind.PERFECT_DAY && entry.label.startsWith(Combos.LABEL)) entry.label.removePrefix(Combos.LABEL).trim().toIntOrNull() else null
    }

    /** Pays what [day] is owed now that a miss on it was a forgotten tick. Returns what was paid. */
    suspend fun restore(day: LocalDate): List<Entry> = store.transaction {
        Rules.load(store)
        val yesterday = book.today().minusDays(1)
        // Today isn't over: what it earns is paid when it ends, as usual.
        if (day > yesterday || !isPerfect(day)) return@transaction emptyList()
        perfectDay(day) + combo(day)
    }

    /** Whether everything due on [day] is done now, with none of it skipped. */
    private suspend fun isPerfect(day: LocalDate): Boolean {
        val from = Days.earliestOpenDay(day)
        val lapses = store.allLapses(from, day)
        // A habit's miss still standing on the day, like one of a goal removed since, means not everything was done.
        if (lapses.any { it.day == day && it.kind == LapseKind.MISS && !it.forgotten && it.period != null }) return false
        val record = StreakCounter.records(day, day, store.activeGoals(), store.habitTicksSince(from), lapses).single()
        return PerfectDays.isPerfect(record)
    }

    private suspend fun dayOf(setting: String): LocalDate? = store.setting(setting)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** The perfect-day bonus for [day], if it is on, counted that day, and wasn't paid. */
    private suspend fun perfectDay(day: LocalDate): List<Entry> {
        val bonus = PerfectDays.bonus(store)
        if (bonus <= 0) return emptyList()
        // Not looked at yet: the day's end pays it.
        val through = dayOf(PerfectDays.THROUGH) ?: return emptyList()
        if (day > through) return emptyList()
        if (dayOf(PerfectDays.SINCE)?.let { day < it } == true) return emptyList()
        if (store.entries(day, day).any { it.kind == EntryKind.PERFECT_DAY && it.label == PerfectDays.LABEL }) return emptyList()
        val entry = Entry(at = book.millis(), day = day, kind = EntryKind.PERFECT_DAY, amount = bonus, label = PerfectDays.LABEL)
        return listOf(entry.copy(id = store.addEntry(entry)))
    }

    /**
     * The combo, with [day] perfect after all. The combo before it comes from the nearest earlier
     * day with something due: what that day's line in the points history says, or none. From
     * [day] on, each day that was paid as part of a combo moves up to its new place, until a day
     * that was left undone; if none was, the combo the next day builds on moves up too.
     */
    private suspend fun combo(day: LocalDate): List<Entry> {
        if (!Feature.isOn(store, Feature.COMBOS)) return emptyList()
        val through = dayOf(Combos.THROUGH) ?: return emptyList()
        if (day > through) return emptyList()
        val since = dayOf(Combos.SINCE)
        if (since != null && day < since) return emptyList()
        val from = maxOf(day.minusDays(LOOK_BACK_DAYS), since ?: day.minusDays(LOOK_BACK_DAYS))
        val open = Days.earliestOpenDay(from)
        val lapses = store.allLapses(open, through)
        val due = StreakCounter.records(from, through, store.activeGoals(), store.habitTicksSince(open), lapses)
            .associate { it.day to (it.due > 0) }
        // A habit's miss still standing on a day left it undone too, even when its goal has been
        // removed since and nothing else was due: the combo ended there.
        val missed = lapses.filter { it.kind == LapseKind.MISS && !it.forgotten && it.period != null }.map { it.day }.toSet()
        fun undone(d: LocalDate) = due[d] == true || d in missed
        // Each day's combo lines, latest last: a day topped up before has more than one.
        val lines = store.entries(from, through).filter { comboOf(it) != null }.groupBy { it.day }
        fun comboOn(d: LocalDate): Int? = lines[d]?.maxOf { comboOf(it) ?: 0 }

        var combo = 0
        var before = day.minusDays(1)
        while (before >= from) {
            val was = comboOn(before)
            if (was != null) {
                combo = was
                break
            }
            if (undone(before)) break // left undone, or combos weren't counting: it starts from here
            before = before.minusDays(1)
        }

        val paid = mutableListOf<Entry>()
        var unbroken = true
        var d = day
        while (d <= through) {
            val was = comboOn(d)
            if (d == day || was != null) {
                // Never below what the day already stands at.
                combo = maxOf(combo + 1, was ?: 0)
                if (was == null || combo > was) {
                    val owed = Combos.pay(combo) - Combos.pay(was ?: 0)
                    val label = "${Combos.LABEL}$combo"
                    if (was == null || owed > 0) {
                        val entry = Entry(at = book.millis(), day = d, kind = EntryKind.PERFECT_DAY, amount = owed.coerceAtLeast(0), label = label)
                        paid += entry.copy(id = store.addEntry(entry))
                    } else {
                        // Past the most a combo pays, nothing more is owed: its line just says the new number.
                        lines[d]?.maxByOrNull { comboOf(it) ?: 0 }?.let { store.updateEntry(it.copy(label = label)) }
                    }
                }
            } else if (undone(d)) {
                unbroken = false
                break
            }
            d = d.plusDays(1)
        }
        if (unbroken) {
            val stored = store.setting(Combos.COUNT)?.toIntOrNull() ?: 0
            store.putSetting(Combos.COUNT, maxOf(combo, stored).toString())
        }
        return paid
    }
}
