package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

enum class AlertKind {
    /** At the habit's clock time. */
    ON_TIME,

    /** An earlier alert, some minutes before the clock time. */
    BEFORE,

    /** A habit without a clock time, reminded at a time of day. */
    REMINDER,

    /** The morning plan: today's list and the pace, when the user's day starts. Not for one goal. */
    MORNING,

    /** "Keep reminding": again, some minutes after the first reminder, while it isn't done. */
    AGAIN,

    /** The weekly summary, on Sunday. Not for one goal. */
    WEEKLY,

    /** A focus timer ran out. */
    FOCUS_DONE,
}

/**
 * One notification due at [at], for [goalId] on the user's [day]. For a habit done several times
 * a day with a time for each, [number] is which one (1 for the first); otherwise 0.
 */
data class Alert(
    val goalId: Long,
    val day: LocalDate,
    val at: LocalDateTime,
    val kind: AlertKind,
    val minutesBefore: Int = 0,
    val number: Int = 0,
)

object Alerts {
    /**
     * Every notification from [from] (included) to [to] (not included). A habit counted per
     * week, month or year is reminded each day until its window is done; whether it is done is
     * checked when the alert fires.
     */
    fun between(goals: List<Goal>, from: LocalDateTime, to: LocalDateTime, dayEndHour: Int, defaultFinish: LocalTime? = null): List<Alert> {
        val alerts = mutableListOf<Alert>()
        for (goal in goals) {
            if (goal.archived || !goal.isHabitLike) continue
            val repeat = goal.repeat ?: continue
            val reminder = goal.reminder ?: Reminder()
            var day = from.date.minusDays(1)
            while (day <= to.date) {
                val firstOfDay = alerts.size
                if (repeat.isDueOn(day)) {
                    val at = Retimes.timeOn(goal, day)
                    val slots = Pace.slots(Retimes.on(goal, day), day, PaceSettings(dayEndHour, null)).filter { it.fixed }
                    if (slots.isNotEmpty()) {
                        // A time for each one, each with its own reminder.
                        for (slot in slots) {
                            if (reminder.onTime) alerts += Alert(goal.id, day, slot.at, AlertKind.ON_TIME, number = slot.number)
                            reminder.minutesBefore?.takeIf { it > 0 }?.let { before ->
                                alerts += Alert(goal.id, day, Pace.plusMinutes(slot.at, -before.toLong()), AlertKind.BEFORE, before, slot.number)
                            }
                        }
                    } else if (at != null) {
                        val time = Pace.on(day, at, dayEndHour)
                        if (reminder.onTime) alerts += Alert(goal.id, day, time, AlertKind.ON_TIME)
                        reminder.minutesBefore?.takeIf { it > 0 }?.let { before ->
                            alerts += Alert(goal.id, day, Pace.plusMinutes(time, -before.toLong()), AlertKind.BEFORE, before)
                        }
                    } else {
                        reminder.at?.let { alerts += Alert(goal.id, day, Pace.on(day, it, dayEndHour), AlertKind.REMINDER) }
                    }
                    reminder.againEvery?.takeIf { it > 0 }?.let { every ->
                        alerts += again(goal, day, alerts.subList(firstOfDay, alerts.size).toList(), every, dayEndHour, defaultFinish)
                    }
                }
                day = day.plusDays(1)
            }
        }
        return alerts.filter { it.at >= from && it.at < to }.sortedBy { it.at }
    }

    /**
     * The morning plans from [from] (included) to [to] (not included): one each day at
     * [dayStart]. Whether there is anything to plan is checked when it fires.
     */
    fun mornings(from: LocalDateTime, to: LocalDateTime, dayStart: LocalTime, dayEndHour: Int): List<Alert> {
        val alerts = mutableListOf<Alert>()
        var day = from.date.minusDays(1)
        while (day <= to.date) {
            alerts += Alert(MORNING_GOAL, day, Pace.on(day, dayStart, dayEndHour), AlertKind.MORNING)
            day = day.plusDays(1)
        }
        return alerts.filter { it.at >= from && it.at < to }
    }

    /** Most times "keep reminding" repeats one reminder, so a forgotten goal doesn't buzz all night. */
    const val MAX_AGAIN = 12

    /**
     * "Keep reminding": after each of the day's reminders, again every [every] minutes until the
     * next one, or the goal's finish time, or the end of the day. Whether it's done by then is
     * checked when each one fires.
     */
    private fun again(goal: Goal, day: LocalDate, first: List<Alert>, every: Int, dayEndHour: Int, defaultFinish: LocalTime?): List<Alert> {
        val starts = first.filter { it.kind != AlertKind.BEFORE }.sortedBy { it.at }
        if (starts.isEmpty()) return emptyList()
        val dayEnd = LocalDateTime(day.plusDays(1), LocalTime(dayEndHour, 0))
        val finish = (goal.finishBy ?: defaultFinish)?.let { Pace.on(day, it, dayEndHour) }?.let { minOf(it, dayEnd) } ?: dayEnd
        val result = mutableListOf<Alert>()
        starts.forEachIndexed { i, start ->
            // Up to the next reminder, or for the last one up to and including the finish time.
            val next = starts.getOrNull(i + 1)?.at
            var at = Pace.plusMinutes(start.at, every.toLong())
            var n = 0
            while ((next == null || at < next) && at <= finish && n < MAX_AGAIN) {
                result += Alert(goal.id, day, at, AlertKind.AGAIN, number = start.number)
                at = Pace.plusMinutes(at, every.toLong())
                n++
            }
        }
        return result
    }

    /** The weekly summaries from [from] (included) to [to] (not included): Sundays at [at]. */
    fun weekly(from: LocalDateTime, to: LocalDateTime, at: LocalTime, dayEndHour: Int): List<Alert> {
        val alerts = mutableListOf<Alert>()
        var day = from.date.minusDays(1)
        while (day <= to.date) {
            if (day.dayOfWeek.isoDayNumber == 7) alerts += Alert(MORNING_GOAL, day, Pace.on(day, at, dayEndHour), AlertKind.WEEKLY)
            day = day.plusDays(1)
        }
        return alerts.filter { it.at >= from && it.at < to }
    }

    /** The goal id a morning plan carries: it is about the whole day, not one goal. */
    const val MORNING_GOAL = 0L
}

/** What the morning plan says: what is due today, the first thing to do, and the pace. */
data class MorningPlan(
    /** Habits due today, and still to do. */
    val goals: Int,
    /** Of them, what to do first in the recommended order, with its time if it has one. */
    val first: Planned?,
    /** "Times per" habits and projects still open. */
    val alsoOpen: Int,
    val pace: TodayPace?,
    /** To-dos without a date still waiting. */
    val todos: Int = 0,
) {
    companion object {
        /** The plan for [view] at [now], or null when there's nothing to plan today. */
        fun of(now: LocalDateTime, view: TodayView, settings: PaceSettings, usual: Map<Long, Int> = emptyMap()): MorningPlan? {
            val left = view.dueToday.filter { !it.done }
            val open = (view.thisWeek + view.thisMonth + view.thisYear).count { !it.done } + view.projects.count { !it.finished }
            val todos = view.todos.count { !it.done }
            if (left.isEmpty() && open == 0 && todos == 0) return null
            val pace = Pace.today(now, view.day, view.dueToday, settings)
            val first = Order.recommend(now, view.day, view.dueToday, settings, usual, pace).firstOrNull { !it.row.done }
            return MorningPlan(left.size, first, open, pace, todos)
        }
    }
}
