package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.random.Random

/*
 * Character check-ins (an extra, with the character): the character's face follows how things
 * are going, and it talks to the user on Today: it checks in after a slip and suggests one small
 * thing to get back on track, answers the mood check-in, gentle on a hard day and glad on a
 * good one, and cheers when everything is done or a level is reached. All the lines are the
 * app's own.
 */

/** The character's face. */
enum class Expression { CALM, HAPPY, PROUD, TIRED, WORRIED }

/** What the character knows about today, to decide what to say. */
data class CompanionFacts(
    val day: LocalDate,
    val now: LocalTime,
    /** Goals still to do today, for the suggestion. */
    val left: List<Goal>,
    val doneToday: Int,
    val dueToday: Int,
    /** Goals left undone yesterday (see [Checkins.leftYesterday]). */
    val missedYesterday: Int,
    val mainStreak: Int,
    /** Today's mood, 1 (bad) to 5 (great), if given. */
    val mood: Int?,
    /** Whether to ask how the user feels: the mood check-in is on, unanswered and not put off. */
    val askMood: Boolean,
    /** Days in a row, before today, with something due and nothing done (see [Checkins.daysAway]). */
    val daysAway: Int,
    /** A new level reached since the user last looked, if any. */
    val newLevel: Int? = null,
)

/**
 * What the character shows and says: its face, a line, maybe a goal to start with, maybe the
 * mood question, and whether it is cheering a new level.
 */
data class CompanionSay(
    val expression: Expression,
    val line: String,
    val suggestion: Goal? = null,
    val askMood: Boolean = false,
    val levelUp: Boolean = false,
) {
    /** It jumps for joy at a new level, and when everything is done. */
    val jumps: Boolean get() = expression == Expression.PROUD
}

object Checkins {
    /** The last level the user has seen, so a new one is cheered once. */
    const val LEVEL_SEEN = "character_level_seen"

    /**
     * Days in a row before [today] with something due and nothing done, counting back from
     * yesterday; days with nothing due are passed over. 0 when there was no done day before
     * them, so a new user isn't welcomed back.
     */
    fun daysAway(records: List<DayRecord>, today: LocalDate): Int {
        var away = 0
        for (record in records.filter { it.day < today }.sortedByDescending { it.day }) {
            if (record.due == 0) continue
            if (record.done > 0) return away
            away++
        }
        return 0
    }

    /** What was left undone yesterday: due, neither done nor skipped. */
    fun leftYesterday(records: List<DayRecord>, today: LocalDate): Int =
        records.firstOrNull { it.day == today.minusDays(1) }?.let { (it.due - it.done - it.skips).coerceAtLeast(0) } ?: 0

    /** The easiest goal left: the quickest, then the smallest. */
    fun easiest(left: List<Goal>): Goal? = left.minWithOrNull(compareBy<Goal>({ it.minutes ?: 15 }, { it.points }, { it.position }))

    fun say(f: CompanionFacts): CompanionSay {
        // A few ways to say each thing; the day picks one, so it doesn't change on every look.
        val random = Random(f.day.toEpochDays().toLong() * 37 + f.doneToday)
        fun pick(vararg lines: String) = lines[random.nextInt(lines.size)]
        val small = easiest(f.left)
        val smallText = small?.let { "${it.icon} ${it.name}" + (it.minutes?.let { m -> " ($m min)" } ?: "") }
        val allDone = f.dueToday > 0 && f.doneToday >= f.dueToday

        f.newLevel?.let { level ->
            return CompanionSay(Expression.PROUD, pick("Level $level! Look at us go!", "We made it to level $level!", "Level $level. All those ticks add up!"), levelUp = true)
        }
        // The mood check-in: asked once a day, then answered.
        if (f.askMood && f.mood == null && !allDone) {
            return CompanionSay(Expression.CALM, pick("How are you feeling today?", "Before we start: how's today feeling?", "Quick check-in: how are you doing?"), askMood = true)
        }
        if (f.mood != null && f.mood <= 2 && !allDone) {
            return CompanionSay(
                Expression.CALM,
                if (smallText != null) {
                    pick(
                        "Sounds like a hard day. Be kind to yourself. If you'd like, just one small thing: $smallText.",
                        "Rough day? That's all right. Maybe just $smallText, and call it a win.",
                        "Hard days happen. One small step is plenty: $smallText.",
                    )
                } else {
                    pick("Sounds like a hard day. Rest counts too.", "Go easy on yourself today.")
                },
                suggestion = small,
            )
        }
        if (allDone) {
            return CompanionSay(
                Expression.PROUD,
                if (f.mood != null && f.mood <= 2) "Everything done, even on a hard day. That took something. Rest well."
                else pick("Everything done today! I'm proud of us.", "All done! Time for a well-earned rest.", "That's the lot for today. Brilliant!"),
            )
        }
        // Back after a few days away.
        if (f.daysAway >= 2) {
            return CompanionSay(
                Expression.HAPPY,
                when {
                    f.doneToday > 0 -> pick("Welcome back! That's the first one. Let's keep it going.", "You're back, and already ticking. Lovely.")
                    smallText == null -> pick("Welcome back! Good to see you.", "Good to have you back!")
                    else -> pick("Welcome back! No catching up needed: let's just start with $smallText.", "Good to have you back! Let's ease in with $smallText.")
                },
                suggestion = if (f.doneToday > 0) null else small,
            )
        }
        // Yesterday slipped: no blame, one small step.
        if (f.missedYesterday > 0 && f.doneToday == 0 && small != null) {
            return CompanionSay(
                Expression.WORRIED,
                pick(
                    "Yesterday got away from us. No worries: let's start small with $smallText.",
                    "A few things slipped yesterday. Today's a fresh start. How about $smallText first?",
                    "Let's get back on track, one step at a time. First up: $smallText.",
                ),
                suggestion = small,
            )
        }
        // Late in the day with a lot left.
        if (f.now >= LocalTime(18, 0) && f.dueToday > 0 && f.doneToday * 2 < f.dueToday && small != null) {
            return CompanionSay(
                Expression.TIRED,
                pick("It's getting late. Even one more helps: $smallText?", "Long day? There's still time for $smallText.", "Let's not let today slip. How about $smallText?"),
                suggestion = small,
            )
        }
        if (f.mood != null && f.mood >= 4) {
            return CompanionSay(
                Expression.HAPPY,
                pick("You're feeling good! Let's make the most of it: ${f.dueToday - f.doneToday} to go.", "Great mood, great day. ${f.dueToday - f.doneToday} left!"),
            )
        }
        if (f.mainStreak >= 7) {
            return CompanionSay(Expression.HAPPY, pick("${f.mainStreak} days in a row! Let's keep it alive.", "That's a ${f.mainStreak}-day streak. On we go!"))
        }
        if (f.doneToday > 0) {
            return CompanionSay(
                Expression.HAPPY,
                if (smallText == null) "Nice going!" else pick("Nice going! ${f.dueToday - f.doneToday} left.", "Good start. Next up: $smallText?"),
                suggestion = small,
            )
        }
        return if (small != null) {
            CompanionSay(Expression.CALM, pick("Ready when you are. First up: $smallText?", "Let's start with something easy: $smallText."), suggestion = small)
        } else {
            CompanionSay(Expression.CALM, pick("Nothing due today. Enjoy it!", "A free day. Rest up!"))
        }
    }
}
