package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** The kinds of message a late nudge can be. They take turns, so no two in a row read the same. */
enum class NudgeTone(val label: String, val hint: String) {
    ENCOURAGING("Encouraging", "Warm and short: there's still time."),
    GAIN("What you'd gain", "The points it pays, your streak, and how close a reward is."),
    SMALLER("Make it smaller", "Just start, with the smallest bit of it."),
    PLAYFUL("Playful", "Light and kind."),
}

/**
 * How late nudges are set up right now: whether they are [on] at all (Settings), the numbers from
 * "Points and rules", and the goals whose nudges were stopped for [stoppedDay] ("Stop for today").
 */
data class NudgePlan(
    val on: Boolean = true,
    /** A goal is nudged once it is this many minutes late. */
    val lateAfter: Int = Rules.current.nudgeLateMinutes,
    /** Then again every this many minutes. */
    val every: Int = Rules.current.nudgeEveryMinutes,
    /** The most nudges for one time of a goal in a day. */
    val most: Int = Rules.current.nudgeMost,
    val stoppedDay: LocalDate? = null,
    val stopped: Set<Long> = emptySet(),
) {
    /** Whether [goalId]'s nudges were stopped for [day]. */
    fun stoppedOn(goalId: Long, day: LocalDate): Boolean = stoppedDay == day && goalId in stopped

    companion object {
        /** No nudges at all. */
        val OFF = NudgePlan(on = false)
    }
}

/** What a nudge has to go on besides the goal: only real numbers, as they are when it is written. */
data class NudgeFacts(
    /** What one tick of the goal pays right now, with the difficulty, the boost and the streak bonus. */
    val points: Int,
    /** What the user calls points. */
    val name: PointsName = PointsName.current,
    /** The main streak, in days. */
    val streak: Int = 0,
    /** The reward the user starred to save for, with its icon; null with none, or while the shop is locked. */
    val reward: String? = null,
    /** Points still missing for [reward]. */
    val rewardLeft: Int = 0,
    /** For a goal done several times a day: how many are done. */
    val count: Int = 0,
)

/** A nudge in words, and the [tone] it took. */
data class NudgeWords(val title: String, val text: String, val tone: NudgeTone)

/**
 * Late nudges: when a goal with a clock time, or its own finish-by time, is late, a notification
 * comes every so often until it is done, skipped or the day ends, each one worded differently.
 * They are on for every such goal unless turned off in its editor ([Reminder.nudge]) or all at
 * once in Settings ([SETTING]); "Stop for today" ends them for one goal for one day ([STOPPED]).
 */
object Nudges {
    /** The Settings switch "Nudges when something is late": "0" turns every nudge off. */
    const val SETTING = "late_nudges"

    /** The kinds of message that are on, as their names with commas between; missing means all of them. */
    const val TONES = "late_nudge_tones"

    /** The goals whose nudges were stopped for one day: "2026-11-12|3,7". An older day counts for nothing. */
    const val STOPPED = "late_nudges_stopped"

    fun isOn(setting: String?): Boolean = setting != "0"

    /** The kinds of message that are on, in their order. With every one turned off, Encouraging alone. */
    fun tones(setting: String?): List<NudgeTone> {
        if (setting == null) return NudgeTone.entries
        val names = setting.split(",").map { it.trim() }.toSet()
        return NudgeTone.entries.filter { it.name in names }.ifEmpty { listOf(NudgeTone.ENCOURAGING) }
    }

    /** The setting for [tones] turned on. */
    fun encodeTones(tones: Collection<NudgeTone>): String = NudgeTone.entries.filter { it in tones }.joinToString(",") { it.name }

    /** The day and goals [setting] holds. */
    fun stopped(setting: String?): Pair<LocalDate, Set<Long>>? {
        val parts = setting?.split("|") ?: return null
        if (parts.size != 2) return null
        val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        return day to parts[1].split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** [setting] with [goalId]'s nudges stopped for [day] too. Other days' goals are dropped: they are over. */
    fun stop(setting: String?, goalId: Long, day: LocalDate): String {
        val before = stopped(setting)?.takeIf { it.first == day }?.second.orEmpty()
        return "$day|" + (before + goalId).sorted().joinToString(",")
    }

    /** The plan for the settings as stored, with the numbers from [rules]. */
    fun plan(on: String?, stopped: String?, rules: Rules = Rules.current): NudgePlan {
        val held = stopped(stopped)
        return NudgePlan(isOn(on), rules.nudgeLateMinutes, rules.nudgeEveryMinutes, rules.nudgeMost, held?.first, held?.second.orEmpty())
    }

    /**
     * Whether a goal of this kind can be nudged at all: a habit due on single days, to do rather
     * than to quit or to stay under. Projects, to-dos and goals counted per week, month or year
     * are not.
     */
    fun rightKind(goal: Goal): Boolean =
        goal.kind == GoalKind.HABIT && !goal.archived && !goal.atMost && !goal.quit && goal.repeat?.windowPeriod == Period.DAILY

    /** Whether [goal] has something to be late for: a clock time, a time for each one, or its own finish-by time. */
    fun hasTime(goal: Goal): Boolean = goal.at != null || goal.times.isNotEmpty() || goal.finishBy != null

    /** Whether [goal] is nudged when it is late, as it is set up: the right kind, with a time, and its own switch on. */
    fun applies(goal: Goal, plan: NudgePlan): Boolean =
        plan.on && rightKind(goal) && (goal.reminder?.nudge ?: true)

    /**
     * When [goal] is due on [day], each with which one of several a day it is (0 otherwise): its
     * time for each one, or its clock time (that day's, if the timeline gave it one), or else its
     * own finish-by time. The default finish time in Settings is nobody's own, so it doesn't count.
     */
    fun dueMoments(goal: Goal, day: LocalDate, dayEndHour: Int): List<Pair<Int, LocalDateTime>> {
        val slots = Pace.slots(Retimes.on(goal, day), day, PaceSettings(dayEndHour, null)).filter { it.fixed }
        if (slots.isNotEmpty()) return slots.map { it.number to it.at }
        val time = Retimes.timeOn(goal, day) ?: goal.finishBy ?: return emptyList()
        return listOf(0 to Pace.on(day, time, dayEndHour))
    }

    /**
     * The nudges for [goal] on [day], a day it is due: the first once it is [NudgePlan.lateAfter]
     * minutes late, then every [NudgePlan.every] minutes until its next time of the day or the
     * end of the day, at most [NudgePlan.most]. None while they are off, for the goal or for
     * all, or stopped for that day. Whether the goal is still to do is checked when each fires.
     */
    fun on(goal: Goal, day: LocalDate, dayEndHour: Int, plan: NudgePlan): List<Alert> {
        if (!applies(goal, plan) || plan.stoppedOn(goal.id, day)) return emptyList()
        val moments = dueMoments(goal, day, dayEndHour).sortedBy { it.second }
        val dayEnd = LocalDateTime(day.plusDays(1), LocalTime(dayEndHour, 0))
        val every = plan.every.coerceAtLeast(1)
        val result = mutableListOf<Alert>()
        moments.forEachIndexed { i, (number, due) ->
            val until = moments.getOrNull(i + 1)?.second?.let { minOf(it, dayEnd) } ?: dayEnd
            var late = plan.lateAfter.coerceAtLeast(0)
            var nth = 1
            while (nth <= plan.most) {
                val at = Pace.plusMinutes(due, late.toLong())
                if (at >= until) break
                result += Alert(goal.id, day, at, AlertKind.NUDGE, number = number, late = late, nth = nth)
                late += every
                nth++
            }
        }
        return result
    }

    /**
     * Whether Today offers "No more nudges today" on [goal]'s card at [now]: it is late, and a
     * nudge for something not yet done ([count] of its times are) is still to come on [day].
     */
    fun canStop(goal: Goal, day: LocalDate, now: LocalDateTime, count: Int, dayEndHour: Int, plan: NudgePlan): Boolean {
        val open = on(goal, day, dayEndHour, plan).filter { it.number == 0 || it.number > count }
        return open.any { it.at > now } && open.any { Pace.plusMinutes(it.at, -it.late.toLong()) <= now }
    }

    /** "40 min late", "1 h 10 min late", "2 h late", or "due now" at the very moment. */
    fun lateText(minutes: Int): String = when {
        minutes <= 0 -> "due now"
        minutes < 60 -> "$minutes min late"
        minutes % 60 == 0 -> "${minutes / 60} h late"
        else -> "${minutes / 60} h ${minutes % 60} min late"
    }

    /** Which tone the [nth] nudge of [goalId] takes on [day]: round the [tones] that are on, from a start that differs by day and goal. */
    fun tone(goalId: Long, day: LocalDate, nth: Int, tones: List<NudgeTone>): NudgeTone {
        val on = tones.ifEmpty { listOf(NudgeTone.ENCOURAGING) }
        return on[(start(goalId, day) + (nth - 1).coerceAtLeast(0)) % on.size]
    }

    /** Where a goal's nudges start on a day, so mornings don't always begin the same. */
    private fun start(goalId: Long, day: LocalDate): Int = ((day.toEpochDays().toLong() * 7 + goalId * 3) % 60).toInt().let { if (it < 0) it + 60 else it }

    /**
     * What the [nth] nudge of the day says for [goal], [late] minutes late. The title is the goal
     * and how late it is; the text takes the tone whose turn it is, worded differently each time.
     * With [locked], for a private goal behind the privacy lock, nothing is named: not the goal,
     * not its numbers, not a reward. Nothing in any of it blames, and every number is a real one
     * from [facts].
     */
    fun words(goal: Goal, day: LocalDate, nth: Int, late: Int, facts: NudgeFacts, tones: List<NudgeTone> = NudgeTone.entries, locked: Boolean = false): NudgeWords {
        val tone = tone(goal.id, day, nth, tones)
        val turn = start(goal.id, day) + (nth - 1).coerceAtLeast(0)
        if (locked) return NudgeWords("🔒 ${PrivateGoals.NAME}", LOCKED[turn % LOCKED.size], tone)
        val name = goal.name
        val text = when (tone) {
            NudgeTone.ENCOURAGING -> encouraging(name, turn)
            NudgeTone.GAIN -> gain(name, facts, turn) ?: encouraging(name, turn)
            NudgeTone.SMALLER -> smaller(goal, facts, turn)
            NudgeTone.PLAYFUL -> playful(name, turn)
        }
        return NudgeWords("${goal.icon} $name · ${lateText(late)}", text, tone)
    }

    /** What a locked private goal's nudge says: kind, and about nothing in particular. */
    private val LOCKED = listOf(
        "Still time today. You've got this.",
        "Still open today, and there's time.",
        "Whenever you're ready.",
        "One step at a time.",
        "No rush. It's here when you have a moment.",
    )

    /**
     * The text whose turn it is. Each tone's list has 5 or 7 texts (or, for "Make it smaller", up
     * to 9), so going round up to four tones still works through all of a list.
     */
    private fun pick(texts: List<String>, turn: Int): String = texts[turn % texts.size]

    private fun encouraging(name: String, turn: Int): String = pick(
        listOf(
            "Still time for $name. You've got this.",
            "$name is still open today, and there's time.",
            "Whenever you're ready: $name.",
            "A few minutes for $name? You can do it.",
            "$name can still happen today. One step at a time.",
            "No rush. $name is here when you have a moment.",
            "Today still has room for $name. You can do this.",
        ),
        turn,
    )

    /** Only what is really there to gain; null when the goal pays nothing. */
    private fun gain(name: String, facts: NudgeFacts, turn: Int): String? {
        if (facts.points <= 0) return null
        val pay = facts.name.amount(facts.points)
        val first = pick(
            listOf(
                "+$pay for $name, waiting for you.",
                "$name is worth $pay today.",
                "Tick $name and $pay are yours.".let { if (facts.points == 1) "Tick $name and $pay is yours." else it },
                "$pay on the table for $name.",
                "One tick of $name: +$pay.",
            ),
            turn,
        )
        val more = buildList {
            if (facts.streak >= 2) add("It helps keep your ${facts.streak}-day streak going.")
            if (facts.reward != null && facts.rewardLeft > 0) {
                add(
                    if (facts.points >= facts.rewardLeft) "That's all you need for ${facts.reward}."
                    else "That's ${facts.name.amount(facts.points)} closer to ${facts.reward}.",
                )
            }
        }
        return if (more.isEmpty()) first else "$first ${more[(turn / 5) % more.size]}"
    }

    private fun smaller(goal: Goal, facts: NudgeFacts, turn: Int): String {
        val name = goal.name
        val minutes = goal.minutes
        val texts = buildList {
            if (goal.hasAmount) {
                val unit = goal.unit?.takeIf { it.isNotBlank() }
                // The next small piece: about a tenth of it, in a round number.
                val piece = Amounts.format(smallPiece(goal.amount ?: 0.0))
                add(if (unit != null) "Just $piece $unit of $name is a start." else "Just $piece of $name is a start.")
                add("Enter even a little of $name. It all adds up.")
            }
            if (goal.target > 1 && goal.severalADay && facts.count < goal.target) {
                add("Just the next one: $name, ${facts.count + 1} of ${goal.target}.")
                add("One more of $name is all for now: ${facts.count + 1} of ${goal.target}.")
            }
            if (minutes != null && minutes in 1..5) {
                add("$name only takes about $minutes min. Quick one.")
                add("About $minutes min and $name is done.")
            } else {
                add("Just the first 5 minutes of $name counts as a start.")
                add("Set a timer for 5 minutes of $name. Stopping after that is fine.")
            }
            add("Just start; you can stop after 2 minutes.")
            add("Do the smallest bit of $name you can think of. That's a start.")
            add("Only the first step of $name. See how it feels from there.")
        }
        return pick(texts, turn)
    }

    /** About a tenth of [amount], rounded to a handy number, and never nothing. */
    internal fun smallPiece(amount: Double): Double {
        val tenth = amount / 10
        return when {
            amount <= 0 -> 1.0
            tenth >= 1 -> kotlin.math.floor(tenth)
            amount >= 1 -> if (amount >= 2) 1.0 else 0.5
            else -> amount / 2
        }
    }

    private fun playful(name: String, turn: Int): String = pick(
        listOf(
            "$name called. It's free whenever you are. 😊",
            "Plot twist: $name is still up for it.",
            "$name is saving you a seat. 🪑",
            "Future you says thanks in advance for $name. 🙌",
            "Knock knock. It's $name, wondering if now's good.",
            "$name is warmed up and ready when you are. ✨",
            "Psst. $name is still on today's guest list.",
        ),
        turn,
    )
}
