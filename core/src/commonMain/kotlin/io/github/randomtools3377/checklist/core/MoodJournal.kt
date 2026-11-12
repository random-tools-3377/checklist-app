package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/*
 * The mood journal and the review's mood part: a stretch of moods summed up in a few numbers,
 * the journal's days narrowed to one noted thing or to words in the notes, and the daily mood
 * reminder. A day's mood here is the one the rest of the app shows: the later one when given.
 */

/**
 * A stretch of moods in short: on how many [days] a mood was given and their [average]; the
 * average of the stretch [before] it, over [beforeDays] days; and the things noted most, each with
 * the number of days it was noted on.
 */
data class MoodSummary(
    val days: Int,
    val average: Double?,
    val beforeDays: Int,
    val before: Double?,
    val top: List<Pair<String, Int>>,
) {
    /** How much higher the average is than in the stretch before; null without both. */
    val change: Double? get() = if (average != null && before != null) average - before else null
}

object MoodSummaries {
    /** How many noted things the summary names. */
    const val TOP = 3

    /** A change smaller than this, in mood points, is "about the same". */
    const val SAME = 0.15

    /**
     * The moods from [from] to [to] summed up, next to those from [beforeFrom] to [beforeTo] (the
     * week, month or year before). Each day counts with its later mood when one was given.
     */
    fun of(moods: List<Mood>, from: LocalDate, to: LocalDate, beforeFrom: LocalDate, beforeTo: LocalDate): MoodSummary {
        val here = moods.filter { it.day >= from && it.day <= to }
        val before = moods.filter { it.day >= beforeFrom && it.day <= beforeTo }
        return MoodSummary(
            days = here.size,
            average = here.takeIf { it.isNotEmpty() }?.map { it.last }?.average(),
            beforeDays = before.size,
            before = before.takeIf { it.isNotEmpty() }?.map { it.last }?.average(),
            top = used(here).take(TOP),
        )
    }

    /** The things noted on [moods]' days, most days first, each once whatever its capitals, with its days. */
    fun used(moods: List<Mood>): List<Pair<String, Int>> {
        val names = MoodFactors.tidy(moods.sortedBy { it.day }.flatMap { it.factors })
        return names.map { name -> name to moods.count { MoodFactors.has(it.factors, name) } }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
    }

    /** "Average mood 3.6 🙂 over 5 days"; null without a mood. */
    fun averageLine(summary: MoodSummary): String? = summary.average?.let { average ->
        "Average mood ${oneDecimal(average)} ${Moods.face(kotlin.math.round(average).toInt())} over ${if (summary.days == 1) "1 day" else "${summary.days} days"}"
    }

    /**
     * How the average compares with the stretch before, named by [stretch] ("week", "month" or
     * "year"): "A little higher than the week before (3.2)."; null without a mood here.
     */
    fun compareLine(summary: MoodSummary, stretch: String): String? {
        if (summary.average == null) return null
        val change = summary.change ?: return "No moods from the $stretch before to compare with."
        val was = "(${oneDecimal(summary.before ?: 0.0)})"
        return when {
            change >= 0.75 -> "Higher than the $stretch before $was."
            change >= SAME -> "A little higher than the $stretch before $was."
            change <= -0.75 -> "Lower than the $stretch before $was."
            change <= -SAME -> "A little lower than the $stretch before $was."
            else -> "About the same as the $stretch before $was."
        }
    }

    /** "Noted most: Slept well (4 days) · Work (3 days)"; null when nothing was noted. */
    fun topLine(summary: MoodSummary): String? = summary.top.takeIf { it.isNotEmpty() }
        ?.joinToString(" · ", prefix = "Noted most: ") { (name, days) -> "$name (${if (days == 1) "1 day" else "$days days"})" }

    /** A mood average to one decimal place, like 3.6. */
    fun oneDecimal(value: Double): String {
        val tenths = kotlin.math.round(value * 10).toInt()
        return "${tenths / 10}.${tenths % 10}"
    }

    /** What the review calls the stretch before a [period]'s. */
    fun stretch(period: Period): String = when (period) {
        Period.WEEKLY -> "week"
        Period.MONTHLY -> "month"
        Period.YEARLY -> "year"
        else -> "day"
    }
}

object MoodJournal {
    /**
     * The journal's days, newest first, narrowed down: only those from [from] to [to] when given,
     * only those with [factor] noted when given, and only those whose notes or noted things hold
     * every word of [query].
     */
    fun filter(days: List<Mood>, factor: String? = null, query: String = "", from: LocalDate? = null, to: LocalDate? = null): List<Mood> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return days.filter { mood ->
            (from == null || mood.day >= from) && (to == null || mood.day <= to) &&
                (factor == null || MoodFactors.has(mood.factors, factor)) &&
                words.all { word -> text(mood).contains(word) }
        }.sortedByDescending { it.day }
    }

    private fun text(mood: Mood): String = (listOfNotNull(mood.note, mood.afterNote) + mood.factors).joinToString("\n").lowercase()

    /**
     * What the journal calls a day's first answer: "Morning" when it was given that day before
     * noon ([givenAt] is when, in the user's own time), else "First check-in", which is truer for
     * one given later in the day or filled in afterwards.
     */
    fun firstLabel(day: LocalDate, givenAt: LocalDateTime?): String =
        if (givenAt != null && givenAt.date == day && givenAt.hour < 12) "Morning" else "First check-in"

    /** A day can be added to the journal for [today] or earlier. */
    fun canAdd(day: LocalDate, today: LocalDate): Boolean = day <= today
}

/** When a mood reminder comes: at [at], for the user's [day]. */
data class MoodReminderTime(val day: LocalDate, val at: LocalDateTime)

/**
 * The daily mood reminder (off until the user picks a time): one notification that only asks how
 * the user is feeling. It never shows a mood or a note, and isn't sent for a day whose mood was
 * already given. Its words are here, so every phone and the computer say the same.
 */
object MoodReminder {
    /** The time of day it comes, as minutes after midnight. Missing or empty while it is off. */
    const val SETTING = "mood_reminder_minute"

    /** The time the picker starts at. */
    val SUGGESTED: LocalTime = LocalTime(9, 0)

    const val TITLE = "How are you feeling?"
    const val TEXT = "Tap to check in."

    /** On a computer, where nothing is tapped. */
    const val TEXT_COMPUTER = "Open Checklist to check in."

    /** What the app is asked to open from the reminder: Today, with the mood check-in. */
    const val OPEN = "mood"

    fun decode(text: String?): LocalTime? = EveningCheck.decode(text)

    fun encode(time: LocalTime?): String = EveningCheck.encode(time)

    /**
     * The reminders from [from] (included) to [to] (not included): one each day at [at], left out
     * for the days in [given], whose mood is already in. A time before the day-end hour belongs
     * to the night after, like every other time of the user's day.
     */
    fun between(from: LocalDateTime, to: LocalDateTime, at: LocalTime, dayEndHour: Int, given: Set<LocalDate> = emptySet()): List<MoodReminderTime> {
        val times = mutableListOf<MoodReminderTime>()
        var day = from.date.minusDays(1)
        while (day <= to.date) {
            times += MoodReminderTime(day, Pace.on(day, at, dayEndHour.coerceIn(0, Days.MAX_DAY_END_HOUR)))
            day = day.plusDays(1)
        }
        return times.filter { it.at >= from && it.at < to && it.day !in given }
    }
}
