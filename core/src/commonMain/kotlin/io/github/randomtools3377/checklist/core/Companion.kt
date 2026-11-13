package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * Character check-ins (an extra, with the character): the character's face follows how things
 * are going, and it talks to the user on Today: it checks in after a slip and suggests one small
 * thing to get back on track, answers the mood check-in, gentle on a hard day and glad on a
 * good one, and cheers when everything is done or a level is reached. All the lines are the
 * app's own.
 */

/**
 * The character's face. Calm, happy, proud, tired and worried came first; then sleepy (late at
 * night), surprised (a lucky tick), determined (a focus timer, a boss), relieved (a late goal
 * done after all) and cheering (arms up).
 */
enum class Expression { CALM, HAPPY, PROUD, TIRED, WORRIED, SLEEPY, SURPRISED, DETERMINED, RELIEVED, CHEERING }

/**
 * What the character does, once, as a short sequence (see [CharacterMoves]): three hops, a wave, a nod,
 * a stretch or a little dance. Breathing and blinking go on by themselves.
 */
enum class CharacterMove { NONE, HOP, WAVE, NOD, STRETCH, DANCE }

/**
 * What was done a moment ago, for a line about it: kept for a few seconds ([Checkins.JUST_NOW_MS]).
 * [goal] is the goal's name, or null for one that must not be named (a locked private goal).
 */
data class JustNow(
    val kind: Kind,
    val goal: String?,
    /** Its clock time or finish time had already passed when it was ticked. */
    val late: Boolean = false,
    /** The tick finished the goal for today; false for one more of a goal done several times. */
    val finished: Boolean = true,
) {
    enum class Kind { TICK, SKIP }
}

/** Something in the game the character notices, in a face and one line on its own card. */
sealed interface CompanionEvent {
    /** A skill reached [level], maybe earning [gear]. Shown until it has been seen, like a new level. */
    data class SkillUp(val area: SkillArea, val level: Int, val gear: Gear? = null) : CompanionEvent

    /** A lucky tick paid [points] more. */
    data class Lucky(val points: Int, val double: Boolean) : CompanionEvent

    /** A daily quest was finished; [left] more are still open today. */
    data class QuestDone(val left: Int) : CompanionEvent

    /** The items in hand were used on the boss. */
    data class BossHit(val dealt: Int, val beaten: Boolean) : CompanionEvent
}

/** What the character knows about one goal still to do, for saying why it suggests it. */
data class SuggestHint(
    /** The minute of the day it is usually ticked at, from the user's own ticks. */
    val usualMinute: Int? = null,
    /** Its clock time or finish time has passed. */
    val late: Boolean = false,
)

/** The next piece of gear and the experience its skill still needs for it. */
data class GearNear(val gear: Gear, val left: Long)

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
    /** Goals skipped today. They are in neither [doneToday] nor [dueToday]: a skip is never done. */
    val skippedToday: Int = 0,
    /** "My day starts at", if set: the morning is the two hours from then. */
    val dayStart: LocalTime? = null,
    /** The hour the user's day ends (0 for midnight): "late" and "sleepy" count back from it. */
    val dayEndHour: Int = 0,
    /** It is getting late from this many hours before the day ends (see [Rules.companionLateHours]). */
    val lateHours: Int = Rules.current.companionLateHours,
    /** What was ticked or skipped a moment ago, if anything. */
    val justNow: JustNow? = null,
    /** Something in the game that happened a moment ago. */
    val event: CompanionEvent? = null,
    /** A skill that levelled up since the user last looked. */
    val skillUp: CompanionEvent.SkillUp? = null,
    /** Minutes more than there is time for today, 0 when it all fits, null with no pace. */
    val shortBy: Int? = null,
    /** What a day needs for the main streak, to say when today counts or one more would do it. */
    val streakRule: StreakSettings? = null,
    /** A streak shield kept the main streak going yesterday. */
    val shieldSaved: Boolean = false,
    /** What is known about the goals in [left], by goal id. */
    val hints: Map<Long, SuggestHint> = emptyMap(),
    /** The character's level and the experience left to the next. */
    val level: Int? = null,
    val toNextLevel: Long? = null,
    /** The gear nearest to being earned. */
    val gearNear: GearNear? = null,
    /** Points earned this week and the weekly target, when one is set. */
    val weekEarned: Int? = null,
    val weekTarget: Int? = null,
    /** Points ahead of last week by this time (negative when behind), with the race on. */
    val raceAhead: Int? = null,
    /** The starred reward's name and what is still missing for it. Null while the shop is locked. */
    val savingName: String? = null,
    val savingLeft: Int? = null,
    /** More is done today than on any earlier day this week. */
    val bestDayThisWeek: Boolean = false,
)

/**
 * What the character shows and says: its face, what it does, a line, maybe a goal to start with
 * and why, maybe the mood question, and whether it is cheering a new level.
 */
data class CompanionSay(
    val expression: Expression,
    val line: String,
    val suggestion: Goal? = null,
    val askMood: Boolean = false,
    /** A new level, of the character or a skill: cheered until "Yay!" or the character page is opened. */
    val levelUp: Boolean = false,
    /** On a hard day: how many things are done today, each one a small win to show. */
    val wins: Int = 0,
    val hardDay: Boolean = false,
    /** What it does with this line: it hops at a new level and for a win on a hard day, dances when everything is done. */
    val move: CharacterMove = CharacterMove.NONE,
    /** Why [suggestion] is the one to start with, like "it's the quickest one left". */
    val reason: String? = null,
)

/** Other screens the character can stand on, each with its own face and a kind line (see [Checkins.at]). */
enum class Scene { MISSES, PENALTY, GAP, FOCUS, FOCUS_DONE, CELEBRATION, SETUP, WIDGET }

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

    /** What was left undone yesterday: due, neither done nor skipped (a skipped goal isn't in what was due). */
    fun leftYesterday(records: List<DayRecord>, today: LocalDate): Int =
        records.firstOrNull { it.day == today.minusDays(1) }?.let { (it.due - it.done).coerceAtLeast(0) } ?: 0

    /** The easiest goal left: the quickest, then the smallest. */
    fun easiest(left: List<Goal>): Goal? = left.minWithOrNull(compareBy<Goal>({ it.minutes ?: 15 }, { it.points }, { it.position }))

    /** A skill level the user has seen for each area, as "FITNESS:3,MIND:5", so a new one is cheered once. */
    const val SKILLS_SEEN = "character_skills_seen"

    /** The day the character last waved hello, so it waves once a day. */
    const val WAVED = "character_waved"

    /** How long a line about what was just done stays. */
    const val JUST_NOW_MS = 6_000L

    /** The morning is this long from "My day starts at". */
    const val MORNING_MINUTES = 120

    /** It is sleepy for this long before the day ends. */
    const val SLEEPY_MINUTES = 120

    /** "Around now" for a goal's usual time: within this many minutes. */
    const val AROUND_MINUTES = 60

    fun encodeSkills(levels: Map<SkillArea, Int>): String = SkillArea.entries.joinToString(",") { "${it.name}:${levels[it] ?: 1}" }

    fun decodeSkills(text: String?): Map<SkillArea, Int>? {
        if (text.isNullOrBlank()) return null
        return text.split(",").mapNotNull { part ->
            val (name, level) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val area = SkillArea.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            area to (level.toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
    }

    /**
     * The skill that levelled up since [seen], with the gear the new levels brought: the one that
     * earned gear first, then the highest level. Null when nothing is new, or nothing was seen yet.
     */
    fun skillUp(sheet: CharacterSheet, seen: Map<SkillArea, Int>?): CompanionEvent.SkillUp? {
        if (seen == null) return null
        return SkillArea.entries.mapNotNull { area ->
            val level = sheet.skillLevel(area).level
            val before = seen[area] ?: 1
            if (level <= before) return@mapNotNull null
            val gear = Gear.entries.filter { it.area == area && it.level in (before + 1)..level }.maxByOrNull { it.level }
            CompanionEvent.SkillUp(area, level, gear)
        }.maxWithOrNull(compareBy<CompanionEvent.SkillUp>({ if (it.gear != null) 1 else 0 }, { it.level }))
    }

    /** The gear nearest to being earned, with the experience its skill still needs. */
    fun gearNear(sheet: CharacterSheet): GearNear? = Outfit.next(sheet)
        .map { GearNear(it, Outfit.left(sheet, it)) }
        .minWithOrNull(compareBy({ it.left }, { it.gear.ordinal }))

    /** How many more goals done today make it count for the main streak; 0 once it does, null with nothing due. */
    fun streakNeed(due: Int, done: Int, settings: StreakSettings): Int? {
        if (due <= 0) return null
        var more = 0
        while (done + more < due && !StreakCounter.meetsTarget(DayRecord(LocalDate(2000, 1, 1), due, done + more, 0, 0, 0), settings)) more++
        return more
    }

    /** Minutes since the user's day began, at its day end hour. */
    internal fun minutesInto(now: LocalTime, dayEndHour: Int): Int = (now.hour * 60 + now.minute - dayEndHour * 60).mod(DAY_MINUTES)

    /** It is getting late: within [CompanionFacts.lateHours] of the day's end. */
    fun isLate(f: CompanionFacts): Boolean = DAY_MINUTES - minutesInto(f.now, f.dayEndHour) <= f.lateHours * 60

    /** Nearly the end of the user's day: time to be sleepy and gentle. */
    fun isSleepy(f: CompanionFacts): Boolean = DAY_MINUTES - minutesInto(f.now, f.dayEndHour) <= minOf(SLEEPY_MINUTES, f.lateHours * 60)

    /** The first two hours from "My day starts at". */
    fun isMorning(f: CompanionFacts): Boolean {
        val start = f.dayStart ?: return false
        return (f.now.hour * 60 + f.now.minute - start.hour * 60 - start.minute).mod(DAY_MINUTES) < MORNING_MINUTES
    }

    private const val DAY_MINUTES = 24 * 60

    /**
     * Why [goal] is the one to start with, from what is known: "it's the quickest one left and
     * you usually do it around now". Null when there is nothing to say.
     */
    fun reason(goal: Goal, f: CompanionFacts): String? {
        val hint = f.hints[goal.id]
        val nowMinute = f.now.hour * 60 + f.now.minute
        val parts = buildList {
            when {
                f.left.size == 1 -> add("it's the only one left")
                easiest(f.left)?.id == goal.id -> add(if (goal.minutes != null) "it's the quickest one left" else "it's a small one to start with")
            }
            val usual = hint?.usualMinute
            if (usual != null) {
                val apart = (usual - nowMinute).mod(DAY_MINUTES).let { minOf(it, DAY_MINUTES - it) }
                if (apart <= AROUND_MINUTES) add("you usually do it around now")
            }
            if (hint?.late == true) add("its time has come round")
        }.take(2)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" and ")
    }

    /** The face, move and line for the character standing on another screen. The [day] picks the wording. */
    fun at(scene: Scene, day: LocalDate): CompanionSay {
        fun pick(vararg lines: String) = lines[day.toEpochDays().mod(lines.size)]
        return when (scene) {
            Scene.MISSES -> CompanionSay(Expression.CALM, pick("It happens. Just say what got in the way.", "No blame here. A quick reason and we move on.", "Yesterday is done. Let's note it and carry on."))
            Scene.PENALTY -> CompanionSay(Expression.DETERMINED, pick("One small task and it's behind us.", "Let's get this one done together.", "A quick one, then back to today."))
            Scene.GAP -> CompanionSay(Expression.HAPPY, pick("Good to see you again. What happened?", "Welcome back. Tell me about it, if you like.", "You're here now; that's what counts."), move = CharacterMove.WAVE)
            Scene.FOCUS -> CompanionSay(Expression.DETERMINED, pick("Heads down. I'm right here.", "Just this, for now.", "One thing at a time."))
            Scene.FOCUS_DONE -> CompanionSay(Expression.RELIEVED, pick("That's the session. Nicely focused.", "Done. Take a breath.", "Time's up. Good work."), move = CharacterMove.STRETCH)
            Scene.CELEBRATION -> CompanionSay(Expression.CHEERING, pick("Yes! Look at that!", "You did it!", "That's a big one!"), move = CharacterMove.DANCE)
            Scene.SETUP -> CompanionSay(Expression.HAPPY, pick("Hello! Let's set things up.", "Hi! This only takes a minute.", "Welcome! Let's get started."), move = CharacterMove.WAVE)
            Scene.WIDGET -> CompanionSay(Expression.CALM, pick("Ready when you are.", "Here when you need me.", "One thing at a time."))
        }
    }

    /**
     * What the character says now. First what holds for the day (see [steady]); over that, for
     * a few seconds, a line about what was just ticked or skipped, or about something in the
     * game. A new level, a hard day and a finished day keep their own words.
     */
    fun say(f: CompanionFacts): CompanionSay {
        val steady = steady(f)
        if (steady.levelUp || steady.hardDay) return steady
        val allDone = f.dueToday > 0 && f.doneToday >= f.dueToday
        f.event?.let { event -> react(event, f)?.let { return steady.copy(expression = it.expression, line = it.line, move = it.move) } }
        val just = f.justNow ?: return steady
        if (allDone && just.kind == JustNow.Kind.TICK) return steady
        return justNow(just, f).let { steady.copy(expression = it.expression, line = it.line, move = it.move) }
    }

    /** Which of [lines] today: they take turns by the day, so yesterday's wording never comes straight back. */
    private fun turn(day: LocalDate, lines: List<String>, shift: Int = 0): String = lines[(day.toEpochDays() + shift).mod(lines.size)]

    private fun react(event: CompanionEvent, f: CompanionFacts): CompanionSay? {
        fun pick(vararg lines: String) = turn(f.day, lines.toList())
        return when (event) {
            is CompanionEvent.Lucky -> CompanionSay(
                Expression.SURPRISED,
                if (event.double) pick("Whoa, a lucky tick! Twice the points: +${event.points}.", "Lucky! That one paid twice: +${event.points}.")
                else pick("Whoa, a lucky chest! +${event.points}.", "A chest! +${event.points}. Didn't see that coming."),
                move = CharacterMove.HOP,
            )
            is CompanionEvent.QuestDone -> CompanionSay(
                Expression.CHEERING,
                when (event.left) {
                    0 -> pick("Quest done. That's every quest today!", "The last quest, done. A clean sweep!")
                    1 -> pick("Quest done! 1 more open today.", "That's a quest finished. 1 to go.")
                    else -> pick("Quest done! ${event.left} more open today.", "That's a quest finished. ${event.left} to go.")
                },
                move = CharacterMove.NOD,
            )
            is CompanionEvent.BossHit ->
                if (event.beaten) CompanionSay(Expression.CHEERING, pick("The boss is beaten! We did it!", "Down it goes! Boss beaten."), move = CharacterMove.HOP)
                else CompanionSay(Expression.DETERMINED, pick("A hit for ${event.dealt}! It's wobbling.", "${event.dealt} off the boss. Keep at it."), move = CharacterMove.NOD)
            is CompanionEvent.SkillUp -> null
        }
    }

    private fun justNow(just: JustNow, f: CompanionFacts): CompanionSay {
        fun pick(vararg lines: String) = turn(f.day, lines.toList(), f.doneToday)
        val name = just.goal
        if (just.kind == JustNow.Kind.SKIP) {
            return CompanionSay(
                Expression.CALM,
                if (name != null) pick("$name is skipped for today. On to what's next.", "$name: skipped today. That's all right.")
                else pick("Skipped for today. On to what's next.", "That one's skipped today. That's all right."),
            )
        }
        if (!just.finished) {
            return CompanionSay(
                Expression.HAPPY,
                if (name != null) pick("$name: one more in.", "Another one for $name.") else pick("One more in.", "Another one ticked."),
                move = CharacterMove.NOD,
            )
        }
        val count = if (f.dueToday > 0 && f.doneToday in 1..f.dueToday) " That's ${f.doneToday} of ${f.dueToday}." else ""
        if (just.late) {
            return CompanionSay(
                Expression.RELIEVED,
                if (name != null) pick("$name was running late and it's done. Phew.", "$name: done after all. Phew!")
                else pick("That one was running late and it's done. Phew.", "Done after all. Phew!"),
                move = CharacterMove.NOD,
            )
        }
        val need = f.streakRule?.let { streakNeed(f.dueToday, f.doneToday, it) }
        val before = f.streakRule?.let { streakNeed(f.dueToday, f.doneToday - 1, it) }
        val tail = when {
            need == 1 -> if (f.mainStreak > 0) " One more keeps the streak." else " One more and today counts for a streak."
            need == 0 && before != null && before > 0 -> if (f.mainStreak > 0) " Today counts for your streak now." else " Today counts for a streak now."
            f.shortBy != null && f.shortBy > 0 -> " It's a full day: one at a time."
            f.shortBy == 0 && f.left.isNotEmpty() -> " You're ahead: no rush now."
            else -> ""
        }
        return CompanionSay(
            Expression.HAPPY,
            (if (name != null) pick("$name done.", "$name: done!", "That's $name done.") else pick("Done.", "That's one done.")) + count + tail,
            move = CharacterMove.NOD,
        )
    }

    /** One more thing worth saying, with the app's own numbers; they take turns as the day goes. */
    private fun aside(f: CompanionFacts): String {
        val asides = buildList {
            if (f.mainStreak in 2..6) add("${f.mainStreak} days in a row so far.")
            if (f.level != null && f.toNextLevel != null && f.toNextLevel > 0) add("${f.toNextLevel} to level ${f.level + 1}.")
            if (f.savingName != null && f.savingLeft != null) {
                add(if (f.savingLeft > 0) "${f.savingLeft} more for ${f.savingName}." else "You've got enough for ${f.savingName} now!")
            }
            if (f.weekEarned != null && f.weekTarget != null && f.weekTarget > 0) {
                add(if (f.weekEarned >= f.weekTarget) "This week's target is reached!" else "${f.weekTarget - f.weekEarned} to this week's target.")
            }
            if (f.raceAhead != null && f.raceAhead > 0) add("You're ${f.raceAhead} ahead of last week.")
            if (f.bestDayThisWeek && f.doneToday > 0) add("Best day this week so far!")
            f.gearNear?.let { add("${it.gear.area.label} is ${it.left} away from its ${it.gear.label.lowercase()}.") }
        }
        return if (asides.isEmpty()) "" else " " + turn(f.day, asides, f.doneToday)
    }

    /** What holds for the day, whatever was just done. */
    fun steady(f: CompanionFacts): CompanionSay {
        // A few ways to say each thing; they take turns by the day, so it doesn't change on
        // every look and never says yesterday's again.
        fun pick(vararg lines: String) = turn(f.day, lines.toList())
        val small = easiest(f.left)
        val smallText = small?.let { "${it.icon} ${it.name}" + (it.minutes?.let { m -> " ($m min)" } ?: "") }
        val why = small?.let { reason(it, f) }
        val allDone = f.dueToday > 0 && f.doneToday >= f.dueToday
        val togo = f.dueToday - f.doneToday

        f.newLevel?.let { level ->
            return CompanionSay(
                Expression.PROUD,
                pick("Level $level! Look at us go!", "We made it to level $level!", "Level $level. All those ticks add up!", "Hello, level $level!"),
                levelUp = true,
                move = CharacterMove.HOP,
            )
        }
        // A skill's new level, and the gear it brought.
        f.skillUp?.let { up ->
            val area = up.area.label
            val line = up.gear?.let { gear ->
                pick("$area is level ${up.level}: new ${gear.label.lowercase()}!", "Level ${up.level} in $area. Look, a ${gear.label.lowercase()}!")
            } ?: pick("$area is level ${up.level} now!", "$area just reached level ${up.level}.", "Level ${up.level} in $area. It's growing!")
            return CompanionSay(if (up.gear != null) Expression.CHEERING else Expression.HAPPY, line, levelUp = true, move = CharacterMove.HOP)
        }
        // The mood check-in: asked once a day, then answered.
        if (f.askMood && f.mood == null && !allDone) {
            return CompanionSay(
                Expression.CALM,
                pick("How are you feeling today?", "Before we start: how's today feeling?", "Quick check-in: how are you doing?", "First things first: how are you?"),
                askMood = true,
            )
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
                move = if (wins > 0) CharacterMove.HOP else CharacterMove.NONE,
                reason = why,
            )
        }
        if (allDone && f.skippedToday > 0) {
            // Nothing left to do, but not everything was done: glad, without the cheer for a full day.
            return CompanionSay(
                Expression.HAPPY,
                pick("That's everything for today. Nicely done.", "Nothing left for today. Good work on what you did.", "Today's list is clear. Well done."),
            )
        }
        if (allDone) {
            // The day after a rough one, with everything done again: said, with no reminder of what went wrong.
            val comeback = f.missedYesterday > 0 || f.daysAway >= 2
            return CompanionSay(
                if (comeback) Expression.CHEERING else Expression.PROUD,
                when {
                    f.mood != null && f.mood <= 2 -> pick(
                        "Everything done, even on a hard day. That took something. Rest well.",
                        "All done, and on a hard day too. That took something. Be proud, and rest well.",
                    )
                    comeback -> pick("Back on track! Everything's done today.", "Back on track: all ${f.dueToday} done. That's the way.", "Everything done today. Back on track!")
                    else -> pick(
                        "Everything done today! I'm proud of us.",
                        "All done! Time for a well-earned rest.",
                        "That's the lot for today. Brilliant!",
                        "All ${f.dueToday} done. What a day!",
                    ) + aside(f)
                },
                move = CharacterMove.DANCE,
            )
        }
        // Nearly the end of the user's day: sleepy and gentle. One more is only named when it would keep the streak.
        if (isSleepy(f) && f.dueToday > 0) {
            val need = f.streakRule?.let { streakNeed(f.dueToday, f.doneToday, it) }
            if (need == 1 && f.mainStreak > 0 && smallText != null) {
                return CompanionSay(
                    Expression.SLEEPY,
                    pick(
                        "It's late. One more would keep the streak: $smallText. Or rest; that's fine too.",
                        "Nearly bedtime. $smallText would keep the ${f.mainStreak}-day streak going, if you've got it in you.",
                    ),
                    suggestion = small,
                    reason = why,
                )
            }
            return CompanionSay(
                Expression.SLEEPY,
                if (f.doneToday > 0) pick(
                    "It's late. What's done is done; the rest can wait.",
                    "${f.doneToday} done today. That counts. Time to wind down.",
                    "It's late. You did ${f.doneToday} today; sleep well.",
                ) else pick(
                    "It's late. The rest can wait; tomorrow is a fresh start.",
                    "Time to wind down. Tomorrow is a new day.",
                ),
            )
        }
        // Back after a few days away.
        if (f.daysAway >= 2) {
            return CompanionSay(
                Expression.HAPPY,
                when {
                    f.doneToday > 0 -> pick("Welcome back! That's the first one. Let's keep it going.", "You're back, and already ticking. Lovely.")
                    smallText == null -> pick("Welcome back! Good to see you.", "Good to have you back!")
                    else -> pick(
                        "Welcome back! No catching up needed: let's just start with $smallText.",
                        "Good to have you back! Let's ease in with $smallText.",
                        "You're back! One small thing is a fine start: $smallText.",
                    )
                },
                suggestion = if (f.doneToday > 0) null else small,
                move = if (f.doneToday > 0) CharacterMove.NONE else CharacterMove.WAVE,
                reason = if (f.doneToday > 0) null else why,
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
                reason = why,
            )
        }
        // Late in the user's day with a lot left.
        if (isLate(f) && f.dueToday > 0 && f.doneToday * 2 < f.dueToday && small != null) {
            return CompanionSay(
                Expression.TIRED,
                pick(
                    "It's getting late. Even one more helps: $smallText?",
                    "Long day? There's still time for $smallText.",
                    "The day's winding down. How about $smallText?",
                ),
                suggestion = small,
                reason = why,
            )
        }
        // A shield kept the streak going yesterday.
        if (f.shieldSaved && f.doneToday == 0) {
            return CompanionSay(
                Expression.RELIEVED,
                pick(
                    "Phew: a shield kept your streak safe yesterday. ${f.mainStreak} days and counting.",
                    "A shield saved the streak yesterday. Still ${f.mainStreak} days!",
                ) + (smallText?.let { " First up: $it?" } ?: ""),
                suggestion = small,
                reason = why,
            )
        }
        // Good morning, by the user's own day.
        if (isMorning(f) && f.doneToday == 0) {
            return CompanionSay(
                Expression.HAPPY,
                when {
                    smallText == null -> pick("Good morning! Nothing due today. Enjoy it!", "Morning! A free day. Rest up!")
                    else -> pick(
                        "Good morning! $togo for today. First up: $smallText?",
                        "Morning! A good stretch, then $smallText?",
                        "Good morning! Let's ease in with $smallText.",
                    )
                } + aside(f),
                suggestion = small,
                move = CharacterMove.STRETCH,
                reason = why,
            )
        }
        if (f.mood != null && f.mood >= 4 && togo > 0) {
            return CompanionSay(
                Expression.HAPPY,
                pick("You're feeling good! Let's make the most of it: $togo to go.", "Great mood, great day. $togo left!", "Feeling good, and $togo to go. Let's roll.") + aside(f),
            )
        }
        if (f.mainStreak >= 7) {
            return CompanionSay(
                Expression.HAPPY,
                pick("${f.mainStreak} days in a row! Let's keep it alive.", "That's a ${f.mainStreak}-day streak. On we go!", "${f.mainStreak} days and counting. Let's go.") + aside(f),
            )
        }
        if (f.doneToday > 0) {
            return CompanionSay(
                Expression.HAPPY,
                (if (smallText == null) pick("Nice going!", "Good work today!") else pick("Nice going! $togo left.", "Good start. Next up: $smallText?", "${f.doneToday} done, $togo to go. Next: $smallText?")) + aside(f),
                suggestion = small,
                reason = why,
            )
        }
        return if (small != null) {
            CompanionSay(
                Expression.CALM,
                pick("Ready when you are. First up: $smallText?", "Let's start with something easy: $smallText.", "$togo for today. How about $smallText first?") + aside(f),
                suggestion = small,
                reason = why,
            )
        } else {
            CompanionSay(Expression.CALM, pick("Nothing due today. Enjoy it!", "A free day. Rest up!", "Nothing on the list today. Have a good one!") + aside(f))
        }
    }
}
