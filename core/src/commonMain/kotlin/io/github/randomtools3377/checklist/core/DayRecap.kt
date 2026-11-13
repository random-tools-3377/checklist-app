package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** What Today shows once everything due is done: the day so far, and what tonight will add. */
data class DayRecap(
    /** Goals really done today. Skipped ones are left out. */
    val done: Int,
    /** Points earned today so far. */
    val earned: Int,
    /** The day is perfect so far: everything due done and nothing skipped. */
    val perfect: Boolean,
    /** What the perfect-day bonus adds when the day ends; 0 when off or the day isn't perfect. */
    val perfectBonus: Int = 0,
    /** With combos on and a perfect day: the combo tonight, and what it pays. */
    val comboDays: Int = 0,
    val comboPoints: Int = 0,
    /** The main streak with today, when today counts for it; else 0. */
    val streak: Int = 0,
    /** One thing that stands out, like "Most points in a day this month". */
    val highlight: String? = null,
) {
    /** "5 done · +42 points", or "5 done". */
    fun headline(): String =
        (if (done == 1) "1 done" else "$done done") + if (earned > 0) " · +" + PointsName.current.amount(earned) else ""

    /** "Tonight: perfect day +10 · combo of 4 +8", or null when the day's end adds nothing. */
    fun tonight(): String? = listOfNotNull(
        "perfect day +$perfectBonus".takeIf { perfectBonus > 0 },
        "combo of $comboDays +$comboPoints".takeIf { comboPoints > 0 },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Tonight: ")

    /** "Your streak with today: 13 days". */
    fun streakLine(): String? = when {
        streak <= 0 -> null
        streak == 1 -> "Your streak with today: 1 day"
        else -> "Your streak with today: $streak days"
    }
}

/**
 * The recap for a done day. It only looks at what was really done: a skipped goal is left out of
 * the count, and a day with a skip is never called perfect.
 */
object DayRecaps {
    /** Points that count as earned, as in the review. */
    val EARNED = setOf(EntryKind.TICK, EntryKind.BONUS, EntryKind.REFUND, EntryKind.PERFECT_DAY, EntryKind.CHALLENGE)

    /** A day's points are "the most this month" only beside at least this many other days with points. */
    const val MOST_NEEDS_DAYS = 3

    /**
     * Whether Today's list is finished: something was due, nothing is left to do, and at least
     * one goal was really done (a day where everything was skipped has no recap).
     */
    fun finished(rows: List<HabitRow>): Boolean = rows.isNotEmpty() && rows.all { it.settled } && rows.any { it.done }

    /**
     * The recap of [day], or null while something is left. [rows] are today's goals, [record]
     * today's record, [entries] the points history from the first of the month to today,
     * [perfectBonus] the perfect-day bonus (0 when off), [combo] the combo through yesterday
     * while combos are on (null when off), [streaks] the streaks now. Without [highlights] (the
     * points history or the review is locked) nothing is said about other days.
     */
    fun of(
        day: LocalDate,
        rows: List<HabitRow>,
        record: DayRecord?,
        entries: List<Entry>,
        settings: StreakSettings,
        streaks: Streaks,
        perfectBonus: Int = 0,
        combo: Int? = null,
        highlights: Boolean = true,
    ): DayRecap? {
        if (!finished(rows)) return null
        val perfect = record?.perfect == true
        val earnedByDay = entries.filter { it.kind in EARNED && it.day <= day && it.day.year == day.year && it.day.monthNumber == day.monthNumber }
            .groupBy { it.day }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val earned = earnedByDay[day] ?: 0
        val others = earnedByDay.filter { it.key != day && it.value > 0 }.values
        val highlight = when {
            !highlights -> null
            earned > 0 && others.size >= MOST_NEEDS_DAYS && others.all { it < earned } -> "Most ${PointsName.current.many} in a day this month"
            perfect && streaks.perfect >= 2 -> "${streaks.perfect} perfect days in a row"
            else -> null
        }
        val counts = record != null && record.due > 0 && StreakCounter.meetsTarget(record, settings)
        return DayRecap(
            done = rows.count { it.done },
            earned = earned,
            perfect = perfect,
            perfectBonus = if (perfect) perfectBonus.coerceAtLeast(0) else 0,
            comboDays = if (perfect && combo != null) combo + 1 else 0,
            comboPoints = if (perfect && combo != null) Combos.pay(combo + 1) else 0,
            streak = if (counts) streaks.done else 0,
            highlight = highlight,
        )
    }
}

/** A few of the next day's goals by name, and how many more there are. */
data class ComingUp(val names: List<String>, val more: Int) {
    /** "🧘 Stretch, 📖 Read and 2 more". */
    fun text(): String = names.joinToString(", ") + when {
        more <= 0 -> ""
        else -> " and $more more"
    }
}

object Upcoming {
    /** How many of the next day's goals are named. */
    const val NAMED = 3

    /**
     * The first few goals of [next], tomorrow as Today would show it, or null when nothing is
     * due then. Private goals must already be left out of [next] while they are locked.
     */
    fun of(next: TodayView?): ComingUp? {
        val rows = next?.dueToday.orEmpty()
        if (rows.isEmpty()) return null
        return ComingUp(rows.take(NAMED).map { "${it.goal.icon} ${it.goal.name}" }, (rows.size - NAMED).coerceAtLeast(0))
    }
}
