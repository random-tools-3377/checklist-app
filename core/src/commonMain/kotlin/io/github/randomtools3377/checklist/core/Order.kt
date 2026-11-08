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
        val left = rows.filter { !it.done }
        val clocked = left.filter { it.goal.at != null }
            .map { row ->
                val at = Pace.on(day, row.goal.at!!, settings.dayEndHour)
                Planned(row, at, fixed = true, late = at < now)
            }
            .sortedBy { it.time }

        val untimed = left.filter { it.goal.at == null }.sortedWith(
            compareBy<HabitRow>(
                { Pace.dueAt(it.goal, day, settings) ?: LocalDateTime(day.plusDays(2), kotlinx.datetime.LocalTime(0, 0)) },
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
            val minutes = (row.goal.minutes ?: each ?: UNKNOWN_LENGTH).toLong() * row.left
            cursor = Pace.plusMinutes(cursor, minutes)
            while (blocks.isNotEmpty() && blocks.first().time!! < cursor) {
                val block = blocks.removeAt(0)
                cursor = Pace.plusMinutes(cursor, (block.row.goal.minutes ?: 0).toLong() * block.row.left)
            }
            val due = Pace.dueAt(row.goal, day, settings)
            Planned(row, if (each == null && row.goal.minutes == null) null else cursor, fixed = false, late = due != null && due < now)
        }

        val merged = (clocked + planned).sortedWith(
            compareBy<Planned>({ it.time ?: LocalDateTime(day.plusDays(2), kotlinx.datetime.LocalTime(0, 0)) }),
        )
        // Keep the untimed ones in their recommended order relative to each other.
        val untimedQueue = planned.toMutableList()
        val result = merged.map { if (it.fixed) it else untimedQueue.removeAt(0) }
        val done = rows.filter { it.done }.map { Planned(it, null, fixed = it.goal.at != null, late = false) }
        return result + done
    }
}

/** The minute of the day a goal is usually ticked at: the middle of the times it was ticked. */
fun usualMinuteOfDay(minutes: List<Int>): Int? {
    if (minutes.isEmpty()) return null
    val sorted = minutes.sorted()
    return sorted[sorted.size / 2]
}
