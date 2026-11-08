package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** "1st", "2nd", "3rd", "11th", "22nd". */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

fun monthShort(month: Int): String =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[(month - 1).coerceIn(0, 11)]

/** "20 Nov 2026". */
fun dateText(date: LocalDate): String = "${date.dayOfMonth} ${monthShort(date.monthNumber)} ${date.year}"

private fun weekdaysText(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "every day"
    (1..5).toSet() -> "weekdays"
    setOf(6, 7) -> "weekends"
    else -> days.sorted().joinToString(", ") { weekdayShort(it) }
}

private fun timesText(times: Int): String = when (times) {
    1 -> "Once"
    2 -> "Twice"
    else -> "$times times"
}

private fun everyMonths(interval: Int): String = if (interval <= 1) "every month" else "every $interval months"

/**
 * The repeat in plain words, like "Mon, Wed, Fri", "3 times a week" or "First Monday of every
 * month". [target] is how many ticks finish it, used for "times per".
 */
fun Repeat.describe(target: Int = 1): String {
    val main = when (kind) {
        RepeatKind.EVERY_N_DAYS -> if (interval <= 1) "Every day" else "Every $interval days"
        RepeatKind.WEEKDAYS -> {
            val days = weekdaysText(weekdays)
            when {
                weekdays.isEmpty() -> "No days chosen"
                interval <= 1 -> days.replaceFirstChar { it.uppercase() }
                else -> "Every $interval weeks: $days"
            }
        }
        RepeatKind.TIMES_PER -> {
            val period = when (per) {
                Period.MONTHLY -> "month"
                Period.YEARLY -> "year"
                else -> "week"
            }
            "${timesText(target)} a $period"
        }
        RepeatKind.MONTH_DATES -> {
            val dates = monthDays.sortedWith(compareBy { if (it == Repeat.LAST_DAY) 99 else it })
                .map { if (it == Repeat.LAST_DAY) "last day" else ordinal(it) }
            val list = when (dates.size) {
                0 -> "no dates chosen"
                1 -> dates[0]
                else -> dates.dropLast(1).joinToString(", ") + " and " + dates.last()
            }
            "The $list of ${everyMonths(interval)}"
        }
        RepeatKind.MONTH_WEEKDAY -> {
            val which = when (weekOfMonth) {
                Repeat.LAST_WEEK -> "last"
                1 -> "first"
                2 -> "second"
                3 -> "third"
                else -> "fourth"
            }
            val day = weekdays.firstOrNull()?.let { weekdayShort(it) } ?: "day"
            "The $which $day of ${everyMonths(interval)}"
        }
        RepeatKind.YEARLY -> "Every year on $day ${monthShort(month)}"
        RepeatKind.ONCE -> "Once, on ${dateText(start)}"
    }
    return if (end != null && kind != RepeatKind.ONCE) "$main, until ${dateText(end)}" else main
}
