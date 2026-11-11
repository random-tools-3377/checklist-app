package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.round

/*
 * Mood and your goals. With the mood check-in on, the user says how they feel each morning, and
 * again once everything due that day is done, and can note what was behind it. Over the last 90
 * days, each habit's days done are compared with its days due and not done, and each noted thing
 * (like "Slept well") with the days it wasn't noted, to find what goes with a better mood (in the
 * code, goals that "lift") and what goes with a lower one (goals that "weigh"), with kind ways
 * into the goals that weigh. The words only ever say what goes together: never that a goal or a
 * thing caused a mood, and never that a goal is to blame.
 */

/**
 * One habit's mood numbers over the last [MoodInsights.DAYS] days. [doneDays] and [notDoneDays]
 * are the days with a mood when it was done, and when it was due and not done. [moodDone] and
 * [moodNotDone] are the average moods on those days (the mood after everything was done if
 * given, else the morning's), once there are enough of them (see [Rules.moodMinDays]). [rise] is
 * the average change from the morning to after everything was done, on the [riseDays] days it
 * was done with both moods.
 */
data class GoalMood(
    val goal: Goal,
    val doneDays: Int,
    val notDoneDays: Int,
    val moodDone: Double?,
    val moodNotDone: Double?,
    val riseDays: Int = 0,
    val rise: Double? = null,
) {
    /** How much better the mood is on days it was done; below 0 when it is worse. */
    val difference: Double? get() = if (moodDone != null && moodNotDone != null) moodDone - moodNotDone else null
}

/**
 * One noted thing's mood numbers over the last [MoodInsights.DAYS] days: the days with a mood it
 * was noted on, and the days with a mood it wasn't, with the average mood on each once there are
 * enough of them (see [Rules.moodMinDays]).
 */
data class FactorMood(
    val factor: String,
    val notedDays: Int,
    val otherDays: Int,
    val moodNoted: Double?,
    val moodOther: Double?,
) {
    /** How much better the mood is on days it was noted; below 0 when it is lower. */
    val difference: Double? get() = if (moodNoted != null && moodOther != null) moodNoted - moodOther else null
}

/**
 * What the moods show: the goals that go with a better mood ([lifts]) and with a lower one
 * ([weighs]), each biggest first, and how many goals had enough days to be [compared] at all;
 * and the same for the things noted at the check-ins.
 */
data class MoodFindings(
    val lifts: List<GoalMood>,
    val weighs: List<GoalMood>,
    val compared: Int,
    val betterFactors: List<FactorMood> = emptyList(),
    val lowerFactors: List<FactorMood> = emptyList(),
    val factorsCompared: Int = 0,
) {
    /** Whether any goal or noted thing had enough moods to compare yet. */
    val enough: Boolean get() = compared > 0 || factorsCompared > 0

    /** Whether anything at all goes with a better or lower mood. */
    val anything: Boolean get() = lifts.isNotEmpty() || weighs.isNotEmpty() || betterFactors.isNotEmpty() || lowerFactors.isNotEmpty()

    companion object {
        val NONE = MoodFindings(emptyList(), emptyList(), 0)
    }
}

/** A way into a goal that goes with a lower mood. */
enum class WayKind {
    /** Do it right after a goal that goes with a better mood; with habit chains, a button sets that up. */
    AFTER,

    /** Make it smaller, in its editor. */
    SMALLER,

    /** Pay half as much again for it, in one tap. */
    PAY_MORE,

    /** Do it early in the day. Words only. */
    EARLY,
}

/** One suggestion: what it says, the goal to do it after, whether a chain can be set, and new points. */
data class Way(val kind: WayKind, val text: String, val after: Goal? = null, val canChain: Boolean = false, val points: Int? = null)

object MoodInsights {
    /** How far back the moods are looked at. */
    const val DAYS = 90

    /** How many goals that go with a better mood the after-done card names. */
    const val TOP = 3

    /** The suggestions offered for each goal that weighs, at most. */
    const val MOST_WAYS = 3

    /**
     * Each active habit's moods, and each noted thing's, over the [DAYS] days before [today]
     * (today is still going, so it waits), sorted into what goes with a better mood and what goes
     * with a lower one. Each needs at least [Rules.moodMinDays] days each way; a difference of
     * [Rules.moodNotableTenths] tenths or more counts.
     */
    fun find(goals: List<Goal>, ticks: List<Tick>, moods: List<Mood>, today: LocalDate, rules: Rules = Rules.current): MoodFindings {
        val from = today.minusDays(DAYS)
        val to = today.minusDays(1)
        val moodByDay = moods.filter { it.day >= from && it.day <= to }.associateBy { it.day }
        if (moodByDay.isEmpty()) return MoodFindings.NONE
        val minDays = rules.moodMinDays.coerceAtLeast(1)
        // A hair under, so 0.5 from averages that don't come out exact still counts.
        val notable = rules.moodNotableTenths / 10.0 - 0.000001
        val ticksByGoal = ticks.filter { it.stepId == null }.groupBy { it.goalId }

        fun average(values: List<Int>): Double? = if (values.size >= minDays) values.average() else null

        val all = goals.filter { it.kind == GoalKind.HABIT && !it.archived && it.repeat != null }.map { goal ->
            val mine = ticksByGoal[goal.id].orEmpty()
            val byDay = mine.groupBy { it.day }
            val byWindow = mine.groupBy { it.periodStart }
            val done = mutableListOf<Mood>()
            val notDone = mutableListOf<Mood>()
            for ((day, mood) in moodByDay) {
                when (doneOn(goal, day, byWindow, byDay[day].orEmpty())) {
                    true -> done += mood
                    false -> notDone += mood
                    null -> {}
                }
            }
            val rises = done.mapNotNull { mood -> mood.after?.let { it - mood.score } }
            GoalMood(
                goal = goal,
                doneDays = done.size,
                notDoneDays = notDone.size,
                moodDone = average(done.map { it.last }),
                moodNotDone = average(notDone.map { it.last }),
                riseDays = rises.size,
                rise = average(rises),
            )
        }
        val lifts = all.filter { (it.difference ?: 0.0) >= notable }
            .sortedWith(compareByDescending<GoalMood> { it.difference }.thenByDescending { it.doneDays }.thenBy { it.goal.position })
        val liftIds = lifts.map { it.goal.id }.toSet()
        val weighs = all.filter { it.goal.id !in liftIds && ((it.difference ?: 0.0) <= -notable || (it.rise ?: 0.0) <= -notable) }
            .sortedWith(compareByDescending<GoalMood> { weight(it) }.thenByDescending { it.doneDays }.thenBy { it.goal.position })
        val factors = factorsOf(moodByDay.values.toList(), ::average)
        val better = factors.filter { (it.difference ?: 0.0) >= notable }
            .sortedWith(compareByDescending<FactorMood> { it.difference }.thenByDescending { it.notedDays }.thenBy { it.factor.lowercase() })
        val lower = factors.filter { (it.difference ?: 0.0) <= -notable }
            .sortedWith(compareBy<FactorMood> { it.difference }.thenByDescending { it.notedDays }.thenBy { it.factor.lowercase() })
        return MoodFindings(
            lifts, weighs, compared = all.count { it.difference != null || it.rise != null },
            betterFactors = better, lowerFactors = lower, factorsCompared = factors.count { it.difference != null },
        )
    }

    /** Each thing noted on any of [moods]' days: the mood on days it was noted against days it wasn't. */
    private fun factorsOf(moods: List<Mood>, average: (List<Int>) -> Double?): List<FactorMood> {
        // One name for each thing, whatever its capitals, as it was first written.
        val names = MoodFactors.tidy(moods.sortedBy { it.day }.flatMap { it.factors })
        return names.map { name ->
            val (noted, other) = moods.partition { MoodFactors.has(it.factors, name) }
            FactorMood(name, noted.size, other.size, average(noted.map { it.last }), average(other.map { it.last }))
        }
    }

    /** How much a goal weighs: the bigger of the drop on days it is done and the fall over those days. */
    private fun weight(m: GoalMood): Double = maxOf(-(m.difference ?: 0.0), -(m.rise ?: 0.0))

    /**
     * Whether [goal] was done on [day] (true), due and not done (false), or neither, or only
     * partly done, which isn't counted either way (null). A goal counted over a week, month or
     * year is done on a day it was ticked, and not done on a day it was still to do and wasn't.
     */
    private fun doneOn(goal: Goal, day: LocalDate, ticksByWindow: Map<LocalDate, List<Tick>>, ticksThatDay: List<Tick>): Boolean? {
        val window = goal.repeat?.window(day)
            // Not due that day by its repeat as it is now: a "since last done" goal has moved on.
            ?: return if (ticksThatDay.isNotEmpty()) true else null
        val ticks = ticksByWindow[window.start].orEmpty()
        if (window.period == Period.DAILY) {
            val inWindow = ticks.filter { it.countsToward(goal, window.start) }
            return when {
                goal.timesDone(inWindow) >= goal.target -> true
                inWindow.isEmpty() -> false
                else -> null
            }
        }
        if (ticksThatDay.isNotEmpty()) return true
        val before = ticks.filter { it.countsToward(goal, window.start) && it.day < day }
        return if (goal.timesDone(before) < goal.target) false else null
    }

    /**
     * Whether everything due today is done, for the check-in once it is: at least one goal due
     * and done, and every other one done too. A goal skipped today is left out.
     */
    fun allDone(dueToday: List<HabitRow>): Boolean {
        val counted = dueToday.filter { !it.skipped }
        return counted.isNotEmpty() && counted.all { it.count >= it.goal.target }
    }

    /** The after-done check-in's question; gentler after a morning that started low. */
    fun question(morning: Int?): String =
        if (morning != null && morning <= 2) "You started the day feeling low. You've done everything — how do you feel now?"
        else "You've done everything for today. How do you feel now?"

    /**
     * What the card says to the answer: only how the mood moved since the morning. It never says
     * why; the user can note that themselves (see [PART_QUESTION]).
     */
    fun answer(morning: Int?, after: Int): String = when {
        morning == null -> "Feeling ${Moods.face(after)} now."
        after > morning -> "Up from ${Moods.face(morning)} to ${Moods.face(after)}."
        after == morning -> "Same as this morning."
        else -> "Down from ${Moods.face(morning)} to ${Moods.face(after)} — that's all right."
    }

    /** Asked under the answer, over the things that can be behind a mood. */
    const val PART_QUESTION = "What played a part?"

    /** Asked under the morning's mood, over the same things. */
    const val BEHIND_QUESTION = "What's behind it?"

    /**
     * "Goals that often go with a better mood: 🏃 Run, 📖 Read.", the biggest first; null when
     * none is known.
     */
    fun liftsLine(findings: MoodFindings, most: Int = TOP): String? =
        findings.lifts.take(most).takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = "Goals that often go with a better mood: ", postfix = ".") { "${it.goal.icon} ${it.goal.name}" }

    /**
     * On a morning that starts low: the goal still to do today that goes with a better mood most,
     * to start with; null when none is known.
     */
    fun startWith(findings: MoodFindings, rows: List<HabitRow>): Goal? {
        val open = rows.filter { !it.done && !it.skipped }.map { it.goal.id }.toSet()
        return findings.lifts.firstOrNull { it.goal.id in open }?.goal
    }

    fun startWithLine(goal: Goal): String = "Start with ${goal.icon} ${goal.name} — it usually lifts you."

    /** The numbers behind a goal, in plain words. */
    fun describe(m: GoalMood): String {
        val difference = m.difference
        val rise = m.rise
        val main = when {
            difference != null && difference >= 0.05 -> "On days you do it, your mood is about ${tenths(difference)} higher"
            difference != null && difference <= -0.05 -> "On days you do it, your mood is about ${tenths(difference)} lower"
            difference != null -> "On days you do it, your mood is about the same"
            else -> null
        }
        val fall = rise?.takeIf { it <= -0.05 }
        return when {
            main != null && fall != null -> "$main, and it drops about ${tenths(fall)} from the morning to the end of those days."
            main != null -> "$main."
            fall != null -> "On days you do it, your mood drops about ${tenths(fall)} from the morning to the end of the day."
            else -> "Not enough moods yet."
        }
    }

    /** How many days the numbers come from. */
    fun basis(m: GoalMood): String = "From ${days(m.doneDays)} done and ${days(m.notDoneDays)} not done."

    /** A noted thing's numbers, in plain words: "Slept well: your mood is about 1.1 higher on days you note it." */
    fun describe(f: FactorMood): String {
        val difference = f.difference ?: return "${f.factor}: not enough moods yet."
        return when {
            difference >= 0.05 -> "${f.factor}: your mood is about ${tenths(difference)} higher on days you note it."
            difference <= -0.05 -> "${f.factor}: your mood is about ${tenths(difference)} lower on days you note it."
            else -> "${f.factor}: your mood is about the same on days you note it."
        }
    }

    fun basis(f: FactorMood): String = "From ${days(f.notedDays)} noted and ${days(f.otherDays)} not."

    /** Said under every list of what goes with a mood. */
    const val CAVEAT = "These go together; it doesn't say what causes what."

    private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

    /** A mood difference to one decimal place, like 0.8, without its sign. */
    fun tenths(value: Double): String {
        val t = round(abs(value) * 10).toInt()
        return "${t / 10}.${t % 10}"
    }

    /** Half as much again, rounded, and at least one more. */
    fun payMore(points: Int): Int = points + maxOf(1, (points + 1) / 2)

    /**
     * Up to [MOST_WAYS] kind ways into a goal that goes with a lower mood: doing it right after
     * the goal that goes with a better mood most (with a chain when one can be set), making it
     * smaller, paying more for it, and getting it out of the way early.
     */
    fun ways(weigh: GoalMood, findings: MoodFindings, goals: List<Goal>): List<Way> {
        val goal = weigh.goal
        val ways = mutableListOf<Way>()
        val lift = findings.lifts.firstOrNull { it.goal.id != goal.id && goal.after != it.goal.id }?.goal
        if (lift != null) {
            ways += Way(
                WayKind.AFTER,
                "Do it right after ${lift.icon} ${lift.name}: days you do that one tend to go with a better mood, so pairing them may make this one easier.",
                after = lift,
                canChain = Chains.choices(goal, goals).any { it.id == lift.id },
            )
        }
        ways += Way(WayKind.SMALLER, "Make it smaller. A shorter, easier version still counts, and it's much easier to start.")
        val more = payMore(goal.points)
        ways += Way(WayKind.PAY_MORE, "Pay more for it: ${PointsName.current.amount(more)} instead of ${goal.points}, so it feels worth it.", points = more)
        ways += Way(WayKind.EARLY, "Get it out of the way early, before the day gets busy.")
        return ways.take(MOST_WAYS)
    }

    /** When there is nothing to show yet. */
    const val NOT_ENOUGH = "Keep checking in: after a couple of weeks of moods, this shows which goals and things go with a better mood."

    /** When goals or things could be compared but none goes with a clearly different mood. */
    const val NOTHING_STANDS_OUT = "Nothing goes with a clearly better or lower mood so far. Keep checking in, and this fills in as you go."
}
