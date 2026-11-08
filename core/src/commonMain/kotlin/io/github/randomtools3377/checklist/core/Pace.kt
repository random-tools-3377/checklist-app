package io.github.randomtools3377.checklist.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.math.floor

/** What pacing needs from Settings. [defaultFinish] is null until the user sets it. */
data class PaceSettings(val dayEndHour: Int, val defaultFinish: LocalTime?)

/** How quickly to go to get through what is left today. */
data class TodayPace(
    /** Ticks left that have no length set. They share the free time evenly. */
    val untimedLeft: Int,
    /** Minutes each untimed tick gets, or null when every tick left has a length. */
    val minutesEach: Int?,
    /** Minutes set aside for ticks with a length. */
    val reservedMinutes: Int,
    /** When the last paced thing is due. */
    val finishAt: LocalDateTime,
    /** When the next untimed one should be done by. */
    val nextBy: LocalDateTime?,
    /** Minutes more than there is time for, or 0. */
    val shortBy: Int,
    /** Goals left with no finish time, because none is set for them or in Settings. */
    val unpaced: Int,
)

/** The pace for a goal that spans days: a "times per" habit or a project with a deadline. */
data class LongPace(val left: Int, val daysLeft: Int, val finish: LocalDate) {
    /** "2 left · about one every 2 days", "5 left · about 2 a day", "3 left · finish date passed". */
    fun describe(): String {
        val start = "$left left"
        return when {
            daysLeft <= 0 -> "$start · the finish date has passed"
            daysLeft >= left -> {
                val every = floor(daysLeft.toDouble() / left).toInt()
                if (every <= 1) "$start · about one a day" else "$start · about one every $every days"
            }
            else -> "$start · about ${ceil(left.toDouble() / daysLeft).toInt()} a day"
        }
    }
}

object Pace {
    /** [time] on the user's [day]. A time before the day-end hour belongs to the night after. */
    fun on(day: LocalDate, time: LocalTime, dayEndHour: Int): LocalDateTime =
        if (time.hour < dayEndHour) LocalDateTime(day.plusDays(1), time) else LocalDateTime(day, time)

    fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long =
        (to.toInstant(TimeZone.UTC) - from.toInstant(TimeZone.UTC)).inWholeMinutes

    fun plusMinutes(moment: LocalDateTime, minutes: Long): LocalDateTime =
        moment.toInstant(TimeZone.UTC).plus(minutes, DateTimeUnit.MINUTE).toLocalDateTime(TimeZone.UTC)

    /** When a habit due today should be finished: its clock time plus length, its own finish-by, or the default. */
    fun dueAt(goal: Goal, day: LocalDate, settings: PaceSettings): LocalDateTime? {
        goal.at?.let { at -> return plusMinutes(on(day, at, settings.dayEndHour), (goal.minutes ?: 0).toLong()) }
        val time = goal.finishBy ?: settings.defaultFinish ?: return null
        return on(day, time, settings.dayEndHour)
    }

    /**
     * The pace for today's habits that are not done. Ticks with a length keep it; the rest share
     * what is left evenly, with earlier finish times taken into account. Null when nothing that
     * is left has a finish time.
     */
    fun today(now: LocalDateTime, day: LocalDate, rows: List<HabitRow>, settings: PaceSettings): TodayPace? {
        val left = rows.filter { !it.done }
        val paced = left.mapNotNull { row -> dueAt(row.goal, day, settings)?.let { row to it } }
        val unpaced = left.size - paced.size
        if (paced.isEmpty()) return null

        var reserved = 0L
        var untimed = 0
        var best = Double.POSITIVE_INFINITY
        var short = 0L
        for ((due, group) in paced.groupBy { it.second }.entries.sortedBy { it.key }.map { it.key to it.value }) {
            for ((row, _) in group) {
                val minutes = row.goal.minutes
                if (minutes != null) reserved += minutes.toLong() * row.left else untimed += row.left
            }
            val free = minutesBetween(now, due) - reserved
            if (free < 0) short = maxOf(short, -free)
            if (untimed > 0) best = minOf(best, free.toDouble() / untimed)
        }
        val each = if (untimed == 0) null else best.coerceAtLeast(0.0).toInt()
        return TodayPace(
            untimedLeft = untimed,
            minutesEach = each,
            reservedMinutes = reserved.toInt(),
            finishAt = paced.maxOf { it.second },
            nextBy = each?.takeIf { it > 0 }?.let { plusMinutes(now, it.toLong()) },
            shortBy = short.toInt(),
            unpaced = unpaced,
        )
    }

    /** The day a "times per" habit should be finished by in its current window, if the user set one. */
    fun finishDate(goal: Goal, window: Window): LocalDate? {
        val day = goal.finishDay ?: return null
        return when (window.period) {
            Period.DAILY -> window.start
            Period.WEEKLY -> window.start.plusDays((day - 1).coerceIn(0, 6))
            Period.MONTHLY -> {
                val length = lengthOfMonth(window.start.year, window.start.monthNumber)
                LocalDate(window.start.year, window.start.monthNumber, if (day == Repeat.LAST_DAY) length else day.coerceIn(1, length))
            }
            Period.YEARLY -> {
                val month = (day / 100).coerceIn(1, 12)
                LocalDate(window.start.year, month, (day % 100).coerceIn(1, lengthOfMonth(window.start.year, month)))
            }
        }
    }

    /** Pace for a "times per" habit, if it has a finish day and isn't done. */
    fun forHabit(row: HabitRow, today: LocalDate): LongPace? {
        if (row.done) return null
        val finish = finishDate(row.goal, row.window) ?: return null
        return LongPace(row.left, daysLeft(today, finish), finish)
    }

    /** Pace for a project, if it has a deadline and isn't finished. No deadline is never assumed. */
    fun forProject(project: ProjectRow, today: LocalDate): LongPace? {
        val deadline = project.goal.deadline ?: return null
        if (project.finished || project.left == 0) return null
        return LongPace(project.left, daysLeft(today, deadline), deadline)
    }

    /** Days from [today] to [finish], counting both. */
    private fun daysLeft(today: LocalDate, finish: LocalDate): Int = finish.toEpochDays() - today.toEpochDays() + 1
}
