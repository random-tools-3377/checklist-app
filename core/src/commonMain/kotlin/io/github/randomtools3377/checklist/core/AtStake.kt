package io.github.randomtools3377.checklist.core

/**
 * How today stands when a notification is written: only real numbers, as they are right then.
 * Counts are of every goal, private ones included, since a count names nothing.
 */
data class DayStanding(
    /** Today's record so far, or null when it isn't known. */
    val record: DayRecord?,
    val settings: StreakSettings = StreakSettings(),
    /** The main streak up to the end of yesterday: the one today's goals keep going. */
    val streak: Int = 0,
    /** Goals on Today's list that still have something to do. */
    val left: Int = 0,
    /** False while the review is behind the privacy lock: then no streak is mentioned at all. */
    val showStreak: Boolean = true,
)

/** What the short note after a tick made away from the app says: "Done: 💧 Water, +5" and "2 left today". */
data class TickAnswer(val title: String, val text: String?) {
    /** Both in one line: "Done: 💧 Water, +5 · 2 left today". */
    val line: String get() = listOfNotNull(title, text).joinToString(" · ")
}

/**
 * What is at stake right now, as one short phrase for a notification: a goal's reminder, the
 * morning plan, the evening check and the note after a tick made on a reminder or a widget. Every
 * phone and the computer ask here, so they say the same. It is always an offer or good news,
 * never what went wrong: no moods, misses, penalties or free skips, and it never suggests a skip.
 * A private goal behind the lock gets no phrase at all (the callers leave it out).
 */
object AtStake {
    /** Today reaches the main streak's target with one more goal done, and doesn't yet. */
    private fun oneMoreCounts(s: DayStanding): Boolean {
        val record = s.record ?: return false
        return s.showStreak && record.due > 0 && NearWins.neededForStreak(record, s.settings) == 1
    }

    /**
     * What a reminder for one of today's goals adds: "This one makes today count for your
     * streak: day 13", or "Last one for today". [row] is the goal on Today's list as it stands;
     * null unless it is due today alone (not counted over a week, month or year), still open,
     * and, for a goal done several times a day, on its last time.
     */
    fun reminder(row: HabitRow, s: DayStanding): String? {
        if (row.window.period != Period.DAILY || row.settled || row.goal.atMost) return null
        if (!row.isRoutine && !row.goal.hasAmount && row.left > 1) return null
        if (oneMoreCounts(s)) {
            return "This one makes today count for your streak" + if (s.streak > 0) ": day ${s.streak + 1}" else ""
        }
        return if (s.left == 1) "Last one for today" else null
    }

    /** What the morning plan adds while a streak is running and something is due: "Day 13 of your streak". */
    fun morning(s: DayStanding): String? {
        val record = s.record ?: return null
        return if (s.showStreak && s.streak > 0 && record.due > 0) "Day ${s.streak + 1} of your streak" else null
    }

    /**
     * What the evening check adds, in Today's own words (see [NearWins]): while today doesn't
     * count for the streak yet, "1 more keeps your 12-day streak"; once it does, "2 more for a
     * perfect day", unless something was skipped today, since such a day is never one.
     */
    fun evening(s: DayStanding): String? {
        val record = s.record ?: return null
        if (!s.showStreak || record.due == 0) return null
        val needed = NearWins.neededForStreak(record, s.settings)
        return when {
            needed > 0 -> NearWins.streakOffer(needed, s.streak)
            record.skips == 0 && record.done < record.due -> NearWins.perfectOffer(record.due - record.done)
            else -> null
        }
    }

    /**
     * What a tick just made changed, from how things stand after it ([s]): "Today now counts for
     * your streak: day 13", then "2 left today" or "That's everything for today". [finished] is
     * whether the tick finished its goal, and [countsToday] whether that goal is one of today's.
     */
    fun afterTick(finished: Boolean, countsToday: Boolean, s: DayStanding): String? {
        val record = s.record
        val streak = if (finished && countsToday && s.showStreak && record != null && record.due > 0 && record.done > 0 &&
            StreakCounter.meetsTarget(record, s.settings) && !StreakCounter.meetsTarget(record.copy(done = record.done - 1), s.settings)
        ) {
            "Today now counts for your streak: day ${s.streak + 1}"
        } else {
            null
        }
        val left = when {
            s.left > 0 -> "${s.left} left today"
            finished && countsToday -> EVERYTHING
            else -> null
        }
        return listOfNotNull(streak, left).joinToString(" · ").ifEmpty { null }
    }

    const val EVERYTHING = "That's everything for today"

    /**
     * The note after a tick made on a reminder or a widget. [name] is the goal with its icon, or
     * "🔒 A private goal" with [hidden], which then says nothing more. [paid] is what the tick
     * paid; [progress] how far a goal done several times is, like "3 of 8", while it isn't
     * finished. With [stakes] null (its switch is off) it is only "Done: 💧 Water, +5".
     */
    fun answer(name: String, paid: Int, finished: Boolean, countsToday: Boolean, progress: String?, hidden: Boolean, stakes: DayStanding?): TickAnswer {
        val title = "Done: $name" + if (paid > 0) ", +$paid" else ""
        if (hidden || stakes == null) return TickAnswer(title, null)
        val text = listOfNotNull(progress.takeIf { !finished }, afterTick(finished, countsToday, stakes)).joinToString(" · ").ifEmpty { null }
        return TickAnswer(title, text)
    }

    /** Joins a reminder's own words and what is at stake: "Now · +5 · Last one for today". */
    fun with(words: String, stake: String?): String = if (stake == null) words else "$words · $stake"
}
