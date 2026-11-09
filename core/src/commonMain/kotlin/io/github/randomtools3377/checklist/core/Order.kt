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
 * quick ones before long ones. Done habits go last.
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
    ): List<Planned> {
        val nowMinute = now.hour * 60 + now.minute
        val far = LocalDateTime(day.plusDays(2), kotlinx.datetime.LocalTime(0, 0))
        val left = rows.filter { !it.done }
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

        // Lay the untimed ones out from now, stepping around the clock-time blocks.
        val each = pace?.minutesEach
        var cursor = now
        val blocks = clocked.toMutableList()
        val planned = untimed.map { row ->
            val minutes = (row.goal.minutes ?: each ?: UNKNOWN_LENGTH).toLong() * ticksNow(row)
            cursor = Pace.plusMinutes(cursor, minutes)
            while (blocks.isNotEmpty() && blocks.first().time!! < cursor) {
                val block = blocks.removeAt(0)
                cursor = Pace.plusMinutes(cursor, (block.row.goal.minutes ?: 0).toLong() * ticksNow(block.row))
            }
            val due = dueOf(row)
            Planned(row, if (each == null && row.goal.minutes == null) null else cursor, fixed = false, late = due != null && due < now)
        }

        val merged = (clocked + planned).sortedWith(compareBy<Planned>({ it.time ?: far }))
        // Keep the untimed ones in their recommended order relative to each other.
        val untimedQueue = planned.toMutableList()
        val result = merged.map { if (it.fixed) it else untimedQueue.removeAt(0) }
        val done = rows.filter { it.done }.map { Planned(it, null, fixed = it.goal.at != null || it.goal.times.isNotEmpty(), late = false) }
        return result + done
    }
}

/** The minute of the day a goal is usually ticked at: the middle of the times it was ticked. */
fun usualMinuteOfDay(minutes: List<Int>): Int? {
    if (minutes.isEmpty()) return null
    val sorted = minutes.sorted()
    return sorted[sorted.size / 2]
}
