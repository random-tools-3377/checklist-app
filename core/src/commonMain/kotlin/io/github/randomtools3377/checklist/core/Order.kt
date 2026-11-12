package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** A habit in the recommended order, with when to do it. */
data class Planned(
    val row: HabitRow,
    /** For a goal with a clock time, that time. Otherwise roughly when to have it done by. */
    val time: LocalDateTime?,
    /** True when [time] is its clock time rather than an estimate. */
    val fixed: Boolean,
    /** Its clock time or finish time has already passed. */
    val late: Boolean,
)

/**
 * The recommended order for today's habits, meant to make each one more likely to get done:
 * goals with a clock time sit at that time; the rest go earliest finish-by first, then the ones
 * usually done by now (falling behind), then the ones usually done around this time of day, then
 * quick ones before long ones. Habits with nothing left to do, done or skipped, go last. Timed
 * events from the phone's calendar ([busy]) are stepped around like goals with a clock time, so
 * the rough times of the untimed goals allow for them.
 */
object Order {
    /** Minutes assumed for a tick with no length when there is no pace to go by. */
    private const val UNKNOWN_LENGTH = 30

    fun recommend(
        now: LocalDateTime,
        day: LocalDate,
        rows: List<HabitRow>,
        settings: PaceSettings,
        /** Minute of the day each goal is usually ticked at, from the user's own history. */
        usualMinute: Map<Long, Int>,
        pace: TodayPace?,
        busy: List<Busy> = emptyList(),
    ): List<Planned> {
        val nowMinute = now.hour * 60 + now.minute
        val far = LocalDateTime(day.plusDays(2), kotlinx.datetime.LocalTime(0, 0))
        val left = rows.filter { !it.settled }
        // A habit done several times a day goes by its next repetition: at the time the user
        // set for it, or by the time it was spread to.
        val next = left.associateWith { Pace.nextSlot(it, day, settings) }
        fun clockTime(row: HabitRow): LocalDateTime? =
            row.goal.at?.let { Pace.on(day, it, settings.dayEndHour) } ?: next[row]?.takeIf { it.fixed }?.at
        fun dueOf(row: HabitRow): LocalDateTime? = next[row]?.takeIf { !it.fixed }?.at ?: Pace.dueAt(row.goal, day, settings)
        // Ticks to lay out now: only the next repetition of one with its times set or spread.
        fun ticksNow(row: HabitRow): Int = if (next[row] != null) 1 else row.left

        val clocked = left.mapNotNull { row -> clockTime(row)?.let { Planned(row, it, fixed = true, late = it < now) } }
            .sortedBy { it.time }

        val untimed = left.filter { clockTime(it) == null }.sortedWith(
            compareBy<HabitRow>(
                { dueOf(it) ?: far },
                { usualMinute[it.goal.id]?.let { usual -> if (usual < nowMinute) 0 else 1 } ?: 1 },
                { usualMinute[it.goal.id] ?: Int.MAX_VALUE },
                { it.goal.minutes ?: UNKNOWN_LENGTH },
                { it.goal.position },
            ),
        )

        // Lay the untimed ones out from now, stepping around the clock-time blocks and what is
        // left of the calendar's timed events.
        val each = pace?.minutesEach
        var cursor = now
        var busyUntil = now
        // Each thing in the way: when it starts, how long it takes, and whether it is a calendar event.
        val blocks = (
            clocked.map { Triple(it.time!!, (it.row.goal.minutes ?: 0).toLong() * ticksNow(it.row), false) } +
                busy.filter { !it.allDay && it.end > now }.map { maxOf(it.start, now).let { from -> Triple(from, Pace.minutesBetween(from, it.end), true) } }
            ).sortedBy { it.first }.toMutableList()
        val planned = untimed.map { row ->
            val minutes = (row.goal.minutes ?: each ?: UNKNOWN_LENGTH).toLong() * ticksNow(row)
            cursor = Pace.plusMinutes(cursor, minutes)
            while (blocks.isNotEmpty() && blocks.first().first < cursor) {
                val (from, length, event) = blocks.removeAt(0)
                if (!event) {
                    cursor = Pace.plusMinutes(cursor, length)
                } else {
                    // Events that overlap each other are only stepped around once.
                    val end = Pace.plusMinutes(from, length)
                    val already = if (from < busyUntil) Pace.minutesBetween(from, minOf(end, busyUntil)) else 0
                    cursor = Pace.plusMinutes(cursor, (length - already).coerceAtLeast(0))
                    if (end > busyUntil) busyUntil = end
                }
            }
            val due = dueOf(row)
            Planned(row, if (each == null && row.goal.minutes == null) null else cursor, fixed = false, late = due != null && due < now)
        }

        val merged = (clocked + planned).sortedWith(compareBy<Planned>({ it.time ?: far }))
        // Keep the untimed ones in their recommended order relative to each other.
        val untimedQueue = planned.toMutableList()
        val result = merged.map { if (it.fixed) it else untimedQueue.removeAt(0) }
        val done = rows.filter { it.settled }.map { Planned(it, null, fixed = it.goal.at != null || it.goal.times.isNotEmpty(), late = false) }
        return result + done
    }
}

/** Today's goals in order: what Today, the Today widget, the quick settings tile and the morning plan all show. */
data class DayOrdered(
    /** The rows the order was made from, each goal with an amount given the time what is left takes. */
    val rows: List<HabitRow>,
    /** In the order to show them: the recommended one, or the user's own. */
    val planned: List<Planned>,
    val pace: TodayPace?,
    /** Today's events from the phone's calendar that were counted. */
    val busy: List<Busy> = emptyList(),
) {
    /** What's next: the first goal in the order that still needs doing. */
    val next: Planned? get() = planned.firstOrNull { !it.row.settled }
}

/**
 * The one place that decides the order of today's goals and which is next, so every part of the
 * app agrees: Today's list, the Today widget, the quick settings tile and the morning plan.
 */
object DayOrder {
    /**
     * [rows] (the habits due on [day], in the user's own order) put in order at [now].
     *
     * @param own the user's own order rather than the recommended one.
     * @param usualMinute the minute of the day each goal is usually ticked at (see [usualMinuteOfDay]).
     * @param busy events from the phone's calendar, where it is turned on and allowed.
     * @param ticks the habit ticks, for how long goals with an amount still take (see [timed]).
     */
    fun of(
        now: LocalDateTime,
        day: LocalDate,
        rows: List<HabitRow>,
        settings: PaceSettings,
        own: Boolean,
        usualMinute: Map<Long, Int> = emptyMap(),
        busy: List<Busy> = emptyList(),
        ticks: List<Tick> = emptyList(),
    ): DayOrdered {
        val due = timed(rows, ticks)
        val today = BusyTime.on(busy, day, settings.dayEndHour)
        val pace = Pace.today(now, day, due, settings, today)
        val planned = if (own) {
            // The user's own order, as it is; each with its clock time, if it has one.
            due.map { row ->
                val at = row.goal.at?.let { Pace.on(day, it, settings.dayEndHour) } ?: Pace.nextSlot(row, day, settings)?.takeIf { it.fixed }?.at
                Planned(row, at, fixed = at != null, late = at != null && !row.settled && at < now)
            }
        } else {
            Order.recommend(now, day, due, settings, usualMinute, pace, today)
        }
        return DayOrdered(due, planned, pace, today)
    }

    /**
     * [rows] with each goal with an amount given the time what is left of it takes: its share of
     * a typed time, or the usual speed learned from its timings in [ticks]. Pacing and the order
     * plan with that.
     */
    fun timed(rows: List<HabitRow>, ticks: List<Tick>): List<HabitRow> {
        if (rows.none { it.goal.hasAmount }) return rows
        val byGoal = ticks.filter { it.stepId == null }.groupBy { it.goalId }
        return rows.map { row ->
            if (!row.goal.hasAmount) return@map row
            val rate = Amounts.minutesPerUnit(byGoal[row.goal.id].orEmpty())
            row.copy(goal = row.goal.copy(minutes = Amounts.minutesFor(row.goal, row.amountLeft, rate)))
        }
    }
}

/** The minute of the day a goal is usually ticked at: the middle of the times it was ticked. */
fun usualMinuteOfDay(minutes: List<Int>): Int? {
    if (minutes.isEmpty()) return null
    val sorted = minutes.sorted()
    return sorted[sorted.size / 2]
}
