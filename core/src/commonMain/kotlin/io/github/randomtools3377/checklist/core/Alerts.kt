package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

enum class AlertKind {
    /** At the habit's clock time. */
    ON_TIME,

    /** An earlier alert, some minutes before the clock time. */
    BEFORE,

    /** A habit without a clock time, reminded at a time of day. */
    REMINDER,
}

/** One notification due at [at], for [goalId] on the user's [day]. */
data class Alert(val goalId: Long, val day: LocalDate, val at: LocalDateTime, val kind: AlertKind, val minutesBefore: Int = 0)

object Alerts {
    /**
     * Every notification from [from] (included) to [to] (not included). A habit counted per
     * week, month or year is reminded each day until its window is done; whether it is done is
     * checked when the alert fires.
     */
    fun between(goals: List<Goal>, from: LocalDateTime, to: LocalDateTime, dayEndHour: Int): List<Alert> {
        val alerts = mutableListOf<Alert>()
        for (goal in goals) {
            if (goal.archived || goal.kind != GoalKind.HABIT) continue
            val repeat = goal.repeat ?: continue
            val reminder = goal.reminder ?: Reminder()
            var day = from.date.minusDays(1)
            while (day <= to.date) {
                if (repeat.isDueOn(day)) {
                    val at = goal.at
                    if (at != null) {
                        val time = Pace.on(day, at, dayEndHour)
                        if (reminder.onTime) alerts += Alert(goal.id, day, time, AlertKind.ON_TIME)
                        reminder.minutesBefore?.takeIf { it > 0 }?.let { before ->
                            alerts += Alert(goal.id, day, Pace.plusMinutes(time, -before.toLong()), AlertKind.BEFORE, before)
                        }
                    } else {
                        reminder.at?.let { alerts += Alert(goal.id, day, Pace.on(day, it, dayEndHour), AlertKind.REMINDER) }
                    }
                }
                day = day.plusDays(1)
            }
        }
        return alerts.filter { it.at >= from && it.at < to }.sortedBy { it.at }
    }
}
