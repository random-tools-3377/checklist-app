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
    /** On a hard day: how many things are done today, each one a small win to show. */
    val wins: Int = 0,
    val hardDay: Boolean = false,
) {
    /** It jumps for joy at a new level, when everything is done, and for each win on a hard day. */
    val jumps: Boolean get() = expression == Expression.PROUD || (hardDay && wins > 0)
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
        // A hard day: gentle, one small thing at a time, and each one done is counted as a win
        // and said so, with leave to stop there, rather than the same nudge again.
        if (f.mood != null && f.mood <= 2 && !allDone) {
            val wins = f.doneToday
            fun more(text: String) = smallText?.let { text.replace("{next}", it) } ?: ""
            val line = when {
                wins == 0 && smallText != null -> pick(
                    "Sounds like a hard day. Be kind to yourself. If you'd like, just one small thing: $smallText.",
                    "Rough day? That's all right. Maybe just $smallText, and call it a win.",
                    "Hard days happen. One small step is plenty: $smallText.",
                )
                wins == 0 -> pick("Sounds like a hard day. Rest counts too.", "Go easy on yourself today.")
                wins == 1 -> pick(
                    "You did it! On a hard day, that one counts double. You could stop here" + (more(", or if you've got a little more in you: {next}.").ifEmpty { "." }),
                    "That's a win, on a day that didn't feel like it. Rest if you need to" + (more(", or try one more small one: {next}.").ifEmpty { "." }),
                )
                wins == 2 -> pick(
                    "Two done on a hard day. That's real progress." + more(" Another, or a rest? Both are fine: {next}."),
                    "Look at that: two wins already today." + more(" Only if it feels good: {next}."),
                )
                else -> pick(
                    "$wins done today, on a day like this. That's brilliant." + more(" Keep going only if you want to: {next}."),
                    "$wins wins on a hard day. I'm proud of us." + more(" Next, if you like: {next}."),
                )
            }
            return CompanionSay(
                expression = when {
                    wins == 0 -> Expression.CALM
                    wins < 3 -> Expression.HAPPY
                    else -> Expression.PROUD
                },
                line = line,
                suggestion = small,
                wins = wins,
                hardDay = true,
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
