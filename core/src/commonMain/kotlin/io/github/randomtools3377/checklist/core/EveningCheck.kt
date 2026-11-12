package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** One thing still open on the evening check. */
data class EveningItem(
    val goalId: Long,
    /** Its icon and name. A private goal behind the privacy lock is only "🔒 A private goal". */
    val name: String,
    /** How far along it is, like "3 of 8" or "1 more this week"; null when it is simply to do. */
    val left: String? = null,
    /** A private goal that isn't named: nothing else is said about it, and no button ticks it. */
    val hidden: Boolean = false,
    /** What a button that ticks it says: "Done", "+1" or "Avoided". Null for a project, whose steps are ticked in the app. */
    val tick: String? = null,
) {
    /** Its line in the list. */
    val words: String get() = if (left == null) name else "$name · $left"
}

/** When an evening check comes: at [at], for the user's [day]. */
data class EveningTime(val day: LocalDate, val at: LocalDateTime)

/**
 * What the evening check says on [day]: everything still open, as Today lists it (the day's goals
 * in the user's own order, then goals counted per week, month or year, then projects). Never empty.
 */
data class EveningList(val day: LocalDate, val items: List<EveningItem>) {
    /** "3 still open today". */
    val title: String get() = if (items.size == 1) "1 still open today" else "${items.size} still open today"

    /**
     * A line for each thing, [most] at the most and then "and 2 more". Private goals that aren't
     * named come last and share one line.
     */
    fun lines(most: Int = EveningCheck.MOST_LINES): List<String> {
        val hidden = items.count { it.hidden }
        val all = items.filter { !it.hidden }.map { it.words } + when (hidden) {
            0 -> emptyList()
            1 -> listOf("🔒 ${PrivateGoals.NAME}")
            else -> listOf("🔒 $hidden private goals")
        }
        return if (all.size <= most) all else all.take(most) + "and ${items.size - most} more"
    }

    /** The first one a button on the notification can tick: never a private goal that isn't named. */
    val firstToTick: EveningItem? get() = items.firstOrNull { it.tick != null && !it.hidden }
}

/**
 * The evening check (off until the user picks a time): one notification that lists what is still
 * open today, so it can be done before it becomes a miss. The list and its words are worked out
 * here, so every phone and the computer say the same.
 */
object EveningCheck {
    /** The time of day it comes, as minutes after midnight. Missing or empty while it is off. */
    const val SETTING = "evening_check_minute"

    /** The time the picker starts at. */
    val SUGGESTED: LocalTime = LocalTime(21, 0)

    /** The earliest it can come: noon, so it is in the later part of the day. */
    const val FROM_HOUR = 12

    /** How many lines the list shows before "and 2 more". */
    const val MOST_LINES = 6

    fun decode(text: String?): LocalTime? =
        text?.trim()?.toIntOrNull()?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime(it / 60, it % 60) }

    fun encode(time: LocalTime?): String = time?.let { (it.hour * 60 + it.minute).toString() } ?: ""

    /**
     * Whether the evening check can come at [time]: from noon until the day ends. With the day
     * ending at 4 am, 1:30 am is still before it ends; with the day ending at midnight, 1:30 am
     * is already the next day, when everything is open again.
     */
    fun allowed(time: LocalTime, dayEndHour: Int): Boolean =
        time.hour >= FROM_HOUR || time.hour < dayEndHour.coerceIn(0, Days.MAX_DAY_END_HOUR)

    /**
     * The evening checks from [from] (included) to [to] (not included): one each day at [at].
     * None while [at] isn't [allowed], like after the day's end was moved earlier. Whether
     * anything is still open is looked at when each one is shown or handed over.
     */
    fun between(from: LocalDateTime, to: LocalDateTime, at: LocalTime, dayEndHour: Int): List<EveningTime> {
        if (!allowed(at, dayEndHour)) return emptyList()
        val times = mutableListOf<EveningTime>()
        var day = from.date.minusDays(1)
        while (day <= to.date) {
            times += EveningTime(day, Pace.on(day, at, dayEndHour))
            day = day.plusDays(1)
        }
        return times.filter { it.at >= from && it.at < to }
    }

    /**
     * What is still open on [view]'s day, or null when nothing is, so nothing is sent on a day
     * when everything is done. Open means:
     * - a goal or to-do due today that is neither done nor skipped (a "since last done" goal
     *   that is late is due every day until it is done, so it stays on the list);
     * - a goal counted so many times a week, month or year that is still short on the last day
     *   of that week, month or year (a skip covers one of its times);
     * - a project whose deadline is today and isn't finished.
     * A goal already missed today ("I slipped") isn't open any more, and a week, month or year
     * only partly inside a goal's dates is never missed, so it isn't listed. To-dos without a
     * date and projects without a deadline today are never missed, so they aren't either.
     * [lapses] are the skips and misses around that day. With [hidePrivate], while the privacy
     * lock covers private goals, a private goal is only "A private goal". Reasons and notes are
     * never part of it.
     */
    fun of(view: TodayView, lapses: List<Lapse> = emptyList(), hidePrivate: Boolean = false): EveningList? {
        val day = view.day
        fun missed(row: HabitRow) =
            lapses.any { it.goalId == row.goal.id && it.kind == LapseKind.MISS && it.periodStart == row.window.start && !it.forgotten }
        val today = view.dueToday.filter { it.count < it.goal.target && it.skips == 0 && !missed(it) }
        val lastDay = (view.thisWeek + view.thisMonth + view.thisYear).filter { row ->
            row.window.end == day && timesLeft(row) > 0 && row.goal.repeat?.isPartial(row.window) != true && !missed(row)
        }
        val deadlines = view.projects.filter { !it.finished && it.goal.deadline == day }
        val items = (today + lastDay).map { item(it, hidePrivate) } + deadlines.map { item(it, hidePrivate) }
        return if (items.isEmpty()) null else EveningList(day, items)
    }

    /** How many of a "times per" goal's times are neither done nor skipped. */
    private fun timesLeft(row: HabitRow): Int = row.goal.target - row.count - row.skips

    private fun hidden(goal: Goal) = EveningItem(goal.id, "🔒 ${PrivateGoals.NAME}", hidden = true)

    private fun item(row: HabitRow, hidePrivate: Boolean): EveningItem {
        val goal = row.goal
        if (hidePrivate && goal.private) return hidden(goal)
        val left = when {
            row.window.period == Period.WEEKLY -> "${timesLeft(row)} more this week"
            row.window.period == Period.MONTHLY -> "${timesLeft(row)} more this month"
            row.window.period == Period.YEARLY -> "${timesLeft(row)} more this year"
            goal.hasAmount -> Amounts.progressText(row.progress, goal)
            goal.target > 1 -> "${row.count} of ${goal.target}"
            else -> null
        }
        return EveningItem(goal.id, "${goal.icon} ${goal.name}", left, tick = if (goal.quit) "Avoided" else if (goal.target > 1) "+1" else "Done")
    }

    private fun item(project: ProjectRow, hidePrivate: Boolean): EveningItem {
        val goal = project.goal
        if (hidePrivate && goal.private) return hidden(goal)
        val steps = when (project.left) {
            0 -> ""
            1 -> "1 step left, "
            else -> "${project.left} steps left, "
        }
        return EveningItem(goal.id, "${goal.icon} ${goal.name}", "${steps}deadline today")
    }
}
