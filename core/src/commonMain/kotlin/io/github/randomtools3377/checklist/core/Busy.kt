package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDateTime

/**
 * Something in the user's own phone calendar: read on the phone, never sent anywhere. Pacing
 * counts timed ones as busy time; all-day ones are only shown.
 */
data class Busy(val start: LocalDateTime, val end: LocalDateTime, val title: String, val allDay: Boolean = false)

object BusyTime {
    /** Minutes of timed events between [from] and [to], overlaps counted once. */
    fun minutes(busy: List<Busy>, from: LocalDateTime, to: LocalDateTime): Long {
        if (to <= from) return 0
        val spans = busy.filter { !it.allDay && it.end > from && it.start < to }
            .map { maxOf(it.start, from) to minOf(it.end, to) }
            .sortedBy { it.first }
        var total = 0L
        var currentStart: LocalDateTime? = null
        var currentEnd: LocalDateTime? = null
        for ((start, end) in spans) {
            if (currentEnd == null || start > currentEnd) {
                if (currentStart != null) total += Pace.minutesBetween(currentStart, currentEnd!!)
                currentStart = start
                currentEnd = end
            } else if (end > currentEnd) {
                currentEnd = end
            }
        }
        if (currentStart != null) total += Pace.minutesBetween(currentStart, currentEnd!!)
        return total
    }

    /** The events that touch [day]'s hours, from when it starts to when it ends (see [Days.dayOf]). */
    fun on(busy: List<Busy>, day: kotlinx.datetime.LocalDate, dayEndHour: Int): List<Busy> =
        busy.filter { Days.dayOf(it.start, dayEndHour) == day || (it.allDay && it.start.date == day) }.sortedBy { it.start }
}
