package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** When a stretch counts as a gap. The user can change both numbers. */
@Serializable
data class GapSettings(
    /** Days in a row with something due and nothing ticked. */
    val days: Int = 2,
    /** Points lost to penalties within one week. */
    val points: Int = 50,
)

/**
 * Gaps: stretches when it looks like the user stopped for a while. A gap is flagged after the
 * day ends, and asked about when the app opens, before anything else. One reason and one penalty
 * task cover it; the misses inside it don't need their own reasons or penalty tasks, but their
 * points still come off.
 */
class Gaps(private val store: Store, private val book: Book) {
    companion object {
        const val SETTING_DAYS = "gap_days"
        const val SETTING_POINTS = "gap_points"

        /** How far back gaps are looked for. */
        const val LOOK_BACK_DAYS = 60

        /**
         * Stretches that were flagged as a gap and turned out not to be one (see
         * [clearIfForgotten]), as "from..to" ISO dates separated by ";". Their days aren't flagged again.
         */
        const val CLEARED = "gaps_cleared"

        /** The stretches kept in [CLEARED], as first and last day. */
        fun cleared(text: String?): List<Pair<LocalDate, LocalDate>> = text.orEmpty().split(';').mapNotNull { part ->
            val days = part.split("..").mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (days.size == 2 && days[0] <= days[1]) days[0] to days[1] else null
        }
    }

    suspend fun settings(): GapSettings {
        val defaults = GapSettings()
        return GapSettings(
            days = store.setting(SETTING_DAYS)?.toIntOrNull()?.coerceAtLeast(1) ?: defaults.days,
            points = store.setting(SETTING_POINTS)?.toIntOrNull()?.coerceAtLeast(1) ?: defaults.points,
        )
    }

    /**
     * Days from [from] to [to] when at least one habit was due or a project deadline fell. A goal
     * due so many times a week, month or year counts on the days it was ticked, and on the last
     * day of its window when that ended with times still to do. On its other days nothing of it
     * is due, so a week already done doesn't make a quiet weekend look like a gap. A habit counts
     * only from the day it was added or brought back, like in the streaks.
     */
    private fun dueDays(from: LocalDate, to: LocalDate, goals: List<Goal>, ticks: List<Tick>): Set<LocalDate> {
        val days = mutableSetOf<LocalDate>()
        val byWindow = ticks.filter { it.stepId == null }.groupBy { Triple(it.goalId, it.period, it.periodStart) }
        val firstDays = goals.associate { it.id to it.addedOn() }
        fun due(goal: Goal, day: LocalDate): Boolean {
            if (!goal.isHabitLike) return goal.deadline == day
            if (firstDays[goal.id]?.let { day < it } == true) return false
            val repeat = goal.repeat ?: return false
            if (repeat.kind != RepeatKind.TIMES_PER) return repeat.isDueOn(day)
            val window = repeat.window(day) ?: return false
            val mine = byWindow[Triple(goal.id, window.period, window.start)].orEmpty()
            return mine.any { it.day == day } || (window.end == day && goal.timesDone(mine) < goal.target)
        }
        var day = from
        while (day <= to) {
            if (goals.any { due(it, day) }) days += day
            day = day.plusDays(1)
        }
        return days
    }

    /**
     * Looks for new gaps up to yesterday, after [Lapses.closeDays]. Stretches already covered
     * by a gap aren't flagged again; a stretch that carries on from a gap not yet answered makes
     * that gap longer. Returns the gaps that are new or grew.
     */
    suspend fun find(): List<Gap> = store.transaction {
        val settings = settings()
        val yesterday = book.today().minusDays(1)
        // Only days since tracking began: history from before it says nothing about stopping.
        val since = store.setting(Lapses.TRACKING_SINCE)?.let { LocalDate.parse(it) }
            ?: store.setting(Lapses.CHECKED_THROUGH)?.let { LocalDate.parse(it).plusDays(1) }?.also {
                store.putSetting(Lapses.TRACKING_SINCE, it.toString())
            }
            ?: return@transaction emptyList()
        val from = maxOf(since, yesterday.minusDays(LOOK_BACK_DAYS))
        if (from > yesterday) return@transaction emptyList()
        val goals = store.activeGoals()
        val due = dueDays(from, yesterday, goals, store.habitTicksSince(Days.earliestOpenDay(from)))
        val ticked = store.tickedDays(from, yesterday)
        val existing = store.gaps().toMutableList()
        // A stretch that was a gap until every miss in it turned out to be a forgotten tick isn't flagged again.
        val cleared = cleared(store.setting(CLEARED))
        fun covered(day: LocalDate) = existing.any { day >= it.from && day <= it.to } || cleared.any { day >= it.first && day <= it.second }
        val changed = mutableListOf<Gap>()

        // Stretches of days with something due and nothing ticked. Days with nothing due don't break them.
        val stretches = mutableListOf<Triple<LocalDate, LocalDate, GapKind>>()
        var start: LocalDate? = null
        var end: LocalDate? = null
        var count = 0
        var day = from
        while (day <= yesterday.plusDays(1)) {
            val idle = day <= yesterday && day in due && day !in ticked
            val active = day > yesterday || (day in due && day in ticked)
            if (idle) {
                if (start == null) start = day
                end = day
                count++
            } else if (active) {
                if (start != null && end != null && count >= settings.days) stretches += Triple(start, end, GapKind.NOTHING_TICKED)
                start = null
                end = null
                count = 0
            }
            day = day.plusDays(1)
        }

        // A big point loss within one week. A penalty given back by "I forgot to tick it", or
        // because its skip was taken back, wasn't lost, and one on a day of a gap already answered
        // was asked about there.
        val undone = store.allLapses(from, yesterday).filter { it.forgotten }.map { it.id }.toSet()
        val answered = existing.filter { it.reason != null }
        val penalties = store.entries(from, yesterday).filter { it.kind == EntryKind.PENALTY }
        // Each penalty given back with its skip cancels the penalty it gave back: the same goal, day and amount.
        val givenBack = penalties.filter { it.isSkipTakenBack }.toMutableList()
        val losses = penalties.filter { entry ->
            if (entry.isSkipTakenBack) return@filter false
            val back = givenBack.firstOrNull { it.day == entry.day && it.goalId == entry.goalId && it.amount == -entry.amount }
            if (back != null) {
                givenBack.remove(back)
                return@filter false
            }
            entry.lapseId !in undone && answered.none { entry.day >= it.from && entry.day <= it.to } &&
                cleared.none { entry.day >= it.first && entry.day <= it.second }
        }
        var lossDay = from
        while (lossDay <= yesterday) {
            val weekEnd = lossDay.plusDays(6)
            val inWeek = losses.filter { it.day >= lossDay && it.day <= weekEnd }
            if (-inWeek.sumOf { it.amount } >= settings.points) {
                stretches += Triple(inWeek.minOf { it.day }, inWeek.maxOf { it.day }, GapKind.POINT_LOSS)
                lossDay = weekEnd
            }
            lossDay = lossDay.plusDays(1)
        }

        for ((stretchFrom, stretchTo, kind) in stretches.sortedBy { it.first }) {
            val newDays = generateSequence(stretchFrom) { it.plusDays(1) }.takeWhile { it <= stretchTo }.filter { !covered(it) }.toList()
            if (newDays.isEmpty()) continue
            val open = existing.find { it.reason == null && it.to.plusDays(1) >= newDays.first() && it.from <= newDays.last() }
            // After a gap already answered, the days that follow make a new one only if they are one on their own.
            if (open == null) {
                val enough = when (kind) {
                    GapKind.NOTHING_TICKED -> newDays.count { it in due && it !in ticked } >= settings.days
                    GapKind.POINT_LOSS -> -losses.filter { it.day in newDays }.sumOf { it.amount } >= settings.points
                }
                if (!enough) continue
            }
            val gap = if (open != null) {
                open.copy(from = minOf(open.from, newDays.first()), to = maxOf(open.to, newDays.last())).also {
                    store.updateGap(it)
                    existing[existing.indexOf(open)] = it
                }
            } else {
                val created = Gap(from = newDays.first(), to = newDays.last(), kind = kind, createdAt = book.millis())
                created.copy(id = store.addGap(created)).also {
                    existing += it
                    store.addPenalty(Penalty(lapseId = null, gapId = it.id, createdAt = book.millis()))
                }
            }
            cover(gap)
            changed += gap
        }
        changed
    }

    /** The misses inside a gap share its reason, and their own penalty tasks are dropped for the gap's one. */
    private suspend fun cover(gap: Gap) {
        for (lapse in store.allLapses(gap.from, gap.to)) {
            if (lapse.kind != LapseKind.MISS || lapse.forgotten || lapse.gapId == gap.id) continue
            store.updateLapse(
                lapse.copy(
                    gapId = gap.id,
                    reason = lapse.reason ?: gap.reason,
                    note = lapse.note ?: gap.note,
                    reasonText = if (lapse.reason == null) gap.reasonText else lapse.reasonText,
                ),
            )
            store.penaltyForLapse(lapse.id)?.let { penalty ->
                if (penalty.doneAt == null && penalty.cancelledAt == null && penalty.choice == null) {
                    store.updatePenalty(penalty.copy(cancelledAt = book.millis()))
                }
            }
        }
    }

    /**
     * The misses inside [gap] that still stand, oldest first: the check-in lists them, each with
     * its own "I forgot to tick it". One turned into a forgotten tick drops out.
     */
    suspend fun misses(gap: Gap): List<Lapse> =
        store.allLapses(gap.from, gap.to).filter { it.gapId == gap.id && it.kind == LapseKind.MISS && !it.forgotten }

    /**
     * After "I forgot to tick it" on a miss inside the gap [gapId] (see [Lapses.forgotToTick]):
     * when every miss in it has turned out to be a forgotten tick, it was no gap. It goes away,
     * with its penalty task if that wasn't done yet, and its days are remembered so what is left
     * of them, like a skipped day, isn't flagged again. While a miss still stands, the gap stays
     * as it is: its one reason and one penalty task cover the rest. Returns true when it went away.
     */
    suspend fun clearIfForgotten(gapId: Long): Boolean = store.transaction {
        val gap = store.gap(gapId) ?: return@transaction false
        val inGap = store.allLapses(gap.from, gap.to).filter { it.gapId == gap.id && it.kind == LapseKind.MISS }
        if (inGap.isEmpty() || inGap.any { !it.forgotten }) return@transaction false
        val now = book.millis()
        store.openPenalties().filter { it.gapId == gap.id }.forEach { store.updatePenalty(it.copy(cancelledAt = now)) }
        inGap.forEach { store.updateLapse(it.copy(gapId = null)) }
        store.removeGap(gap.id)
        // Kept only as far back as gaps are looked for.
        val oldest = book.today().minusDays(LOOK_BACK_DAYS + 1)
        val kept = (cleared(store.setting(CLEARED)) + (gap.from to gap.to)).filter { it.second >= oldest }
        store.putSetting(CLEARED, kept.joinToString(";") { "${it.first}..${it.second}" })
        true
    }

    /** Answers the gap check-in: one reason and an optional note for the whole stretch. */
    suspend fun explain(gapId: Long, reason: Reason, note: String?, reasonText: String? = null) = store.transaction {
        val gap = store.gap(gapId) ?: return@transaction
        val answered = gap.copy(
            reason = reason,
            note = note?.takeIf { it.isNotBlank() },
            reasonText = reasonText?.takeIf { reason == Reason.OTHER && it.isNotBlank() },
        )
        store.updateGap(answered)
        for (lapse in store.allLapses(gap.from, gap.to)) {
            if (lapse.gapId == gap.id && lapse.reason == null) {
                store.updateLapse(lapse.copy(reason = reason, note = answered.note, reasonText = answered.reasonText))
            }
        }
    }
}
