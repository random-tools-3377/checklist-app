package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** One month of the year in review: how much of what was due got done, points earned, and mood. */
data class MonthSummary(val month: Int, val due: Int, val done: Int, val earned: Int, val mood: Double?) {
    val share: Double? get() = if (due == 0) null else done.toDouble() / due
}

/** The year in review: each month summed up, from the review of the whole year. */
object YearReview {
    fun months(summary: ReviewSummary, moods: List<Mood>, year: Int, today: LocalDate): List<MonthSummary> = (1..12).map { month ->
        fun inMonth(day: LocalDate) = day.year == year && day.monthNumber == month
        // Only days that have come: the rest of the year is still ahead.
        val days = summary.days.filter { inMonth(it.day) && it.day <= today }
        val moodsHere = moods.filter { inMonth(it.day) }.map { it.score }
        MonthSummary(
            month = month,
            due = days.sumOf { it.due },
            done = days.sumOf { it.done },
            earned = summary.earnedByDay.filterKeys { inMonth(it) }.values.sum(),
            mood = moodsHere.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    /** The week planning card belongs to the week starting on this Monday. */
    fun weekOf(day: LocalDate): LocalDate = Boost.weekOf(day)
}
