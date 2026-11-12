package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** How one time a goal was due went. [OPEN]: neither done, skipped nor missed (yet). */
enum class TimeState { DONE, SKIPPED, MISSED, OPEN }

/** One time a habit was due: the [day] its window ended, and the day the window [start]ed. */
data class GoalTime(val day: LocalDate, val start: LocalDate, val state: TimeState)

/** What a day shows on a goal's calendar. */
enum class GoalDayMark(val mark: String, val word: String) {
    DONE("✓", "done"),
    SKIPPED("–", "skipped"),
    MISSED("✕", "missed"),
}

/** Done out of the times it was due. A skipped time is due and never done. */
data class GoalRate(val done: Int, val due: Int) {
    val percent: Int? get() = if (due == 0) null else done * 100 / due
}

/** A project's step, and the day it was done, if it is. */
data class StepDone(val step: Step, val day: LocalDate?)

/** Everything a goal's own page shows. */
data class GoalPageView(
    val goal: Goal,
    val today: LocalDate,
    /** The earliest day the calendar has anything on: where "earlier" stops. */
    val firstDay: LocalDate,
    /** Each time it was due, oldest first. Empty for a project. */
    val times: List<GoalTime>,
    val marks: Map<LocalDate, GoalDayMark>,
    /** Times done in a row up to now, and the most ever; null for a project. */
    val currentRun: Int?,
    val bestRun: Int?,
    val last30: GoalRate?,
    val last90: GoalRate?,
    val ticks: Int,
    /** Points it has earned: ticks, finishing bonuses and challenges. */
    val points: Int,
    /** Its skips and misses that still stand, newest first. */
    val lapses: List<Lapse>,
    /** Its ticks with a note, newest first. */
    val notes: List<Tick>,
    /** For a goal with an amount: how much in all, and the seconds timed with Start and Stop. */
    val amount: Double?,
    val seconds: Int,
    /** For a project: its steps in order, each with the day it was done. */
    val steps: List<StepDone>,
    /** For a project: the day its last step was done, once every step is. */
    val finishedOn: LocalDate?,
)

/**
 * A goal's own page: its calendar, runs, how often it got done, and what it earned. The day
 * rules are those of the streaks and the review: a habit counts on the day its window ends, only
 * from the day it was added, a skip is never done, and today counts once it is done.
 */
object GoalPage {
    /**
     * Each time [goal] (a habit or a to-do with a date) was due up to [today], oldest first. Done
     * when ticked as often as it needs; skipped when a skip covered it; missed when a miss was
     * recorded; otherwise open. A week, month or year only partly in the goal's dates counts once
     * it is done or skipped, as in the review.
     */
    fun times(
        goal: Goal,
        today: LocalDate,
        ticks: List<Tick>,
        lapses: List<Lapse>,
        countsFrom: (Goal) -> LocalDate? = { it.addedOn() },
    ): List<GoalTime> {
        val repeat = goal.repeat ?: return emptyList()
        if (!goal.isHabitLike) return emptyList()
        val own = ticks.filter { it.goalId == goal.id && it.stepId == null && it.period == goal.period }
        val live = lapses.filter { it.goalId == goal.id && !it.forgotten }
        val from = countsFrom(goal)
            ?: (own.map { it.periodStart } + live.map { it.periodStart }).minOrNull()
            ?: today
        val windows = PastWindows(listOf(goal), ticks, lapses)
        val byStart = own.groupBy { it.periodStart }
        val skipsByStart = live.filter { it.kind == LapseKind.SKIP }.groupingBy { it.periodStart }.eachCount()
        val missed = live.filter { it.kind == LapseKind.MISS }.map { it.periodStart }.toSet()
        val times = mutableListOf<GoalTime>()
        var day = from
        while (day <= today) {
            val window = windows.window(goal, day)?.takeIf { it.end == day }
            if (window != null) {
                val count = goal.timesDone(byStart[window.start].orEmpty())
                val skips = skipsByStart[window.start] ?: 0
                val finished = count >= goal.target
                val covered = !finished && (if (window.period == Period.DAILY) skips > 0 else count + skips >= goal.target)
                if (finished || covered || !repeat.isPartial(window)) {
                    val state = when {
                        finished -> TimeState.DONE
                        window.start in missed -> TimeState.MISSED
                        skips > 0 -> TimeState.SKIPPED
                        else -> TimeState.OPEN
                    }
                    times += GoalTime(day, window.start, state)
                }
            }
            day = day.plusDays(1)
        }
        return times
    }

    /** Done of due among [times] from [from] to [today]. Today counts once it is done. */
    fun rate(times: List<GoalTime>, from: LocalDate, today: LocalDate): GoalRate {
        val counted = times.filter { it.day >= from && it.day <= today && !(it.day == today && it.state == TimeState.OPEN) }
        return GoalRate(counted.count { it.state == TimeState.DONE }, counted.size)
    }

    /**
     * Times done in a row: the run now, and the longest ever. A skipped time neither adds to a
     * run nor ends it, and today's doesn't end one while it can still be done.
     */
    fun runs(times: List<GoalTime>, today: LocalDate): Pair<Int, Int> {
        var run = 0
        var best = 0
        for (time in times) {
            when (time.state) {
                TimeState.DONE -> {
                    run++
                    best = maxOf(best, run)
                }
                TimeState.SKIPPED -> {}
                TimeState.MISSED -> run = 0
                TimeState.OPEN -> if (time.day < today) run = 0
            }
        }
        return run to best
    }

    /**
     * What each day shows on the calendar. A habit's day is the day its time was due. A goal
     * counted per week, month or year, a to-do without a date and a project also show each day
     * something was ticked as done, and the day a skip or miss belongs to.
     */
    fun marks(goal: Goal, times: List<GoalTime>, ticks: List<Tick>, lapses: List<Lapse>): Map<LocalDate, GoalDayMark> {
        val marks = mutableMapOf<LocalDate, GoalDayMark>()
        for (lapse in lapses.filter { it.goalId == goal.id && !it.forgotten }.sortedBy { it.kind == LapseKind.MISS }) {
            marks[lapse.day] = if (lapse.kind == LapseKind.MISS) GoalDayMark.MISSED else GoalDayMark.SKIPPED
        }
        val own = ticks.filter { it.goalId == goal.id }
        if (goal.kind == GoalKind.PROJECT) {
            own.filter { it.stepId != null }.forEach { marks[it.day] = GoalDayMark.DONE }
        } else {
            val daily = goal.period == Period.DAILY
            own.filter { it.stepId == null }.groupBy { it.periodStart to it.period }.forEach { (key, group) ->
                // A day's ticks count once they make it done; a week's, month's or year's each show on their day.
                if (key.second == Period.DAILY && daily) {
                    if (goal.timesDone(group) >= goal.target) marks[key.first] = GoalDayMark.DONE
                } else {
                    group.forEach { marks[it.day] = GoalDayMark.DONE }
                }
            }
        }
        for (time in times) {
            when (time.state) {
                TimeState.DONE -> if (time.day == time.start) marks[time.day] = GoalDayMark.DONE
                TimeState.SKIPPED -> if (marks[time.day] != GoalDayMark.DONE) marks[time.day] = GoalDayMark.SKIPPED
                TimeState.MISSED -> if (marks[time.day] != GoalDayMark.DONE) marks[time.day] = GoalDayMark.MISSED
                TimeState.OPEN -> {}
            }
        }
        return marks
    }

    fun of(
        goal: Goal,
        today: LocalDate,
        steps: List<Step>,
        ticks: List<Tick>,
        lapses: List<Lapse>,
        entries: List<Entry>,
        countsFrom: (Goal) -> LocalDate? = { it.addedOn() },
    ): GoalPageView {
        val ownTicks = ticks.filter { it.goalId == goal.id && it.day <= today }
        val ownLapses = lapses.filter { it.goalId == goal.id && !it.forgotten }
        val times = times(goal, today, ownTicks, ownLapses, countsFrom)
        val marks = marks(goal, times, ownTicks, ownLapses)
        val habit = goal.isHabitLike && goal.repeat != null
        val runs = if (habit) runs(times, today) else null
        val project = goal.kind == GoalKind.PROJECT
        // A project's ticks are its steps; a habit's or a to-do's are its own (a to-do's steps pay nothing).
        val counted = ownTicks.filter { (it.stepId != null) == project }
        val active = if (project) steps.filter { it.goalId == goal.id && !it.archived }.sortedWith(compareBy({ it.position }, { it.id })) else emptyList()
        val stepDays = ownTicks.filter { it.stepId != null }.associate { it.stepId to it.day }
        val stepsDone = active.map { StepDone(it, stepDays[it.id]) }
        return GoalPageView(
            goal = goal,
            today = today,
            firstDay = (marks.keys + times.map { it.day } + listOfNotNull(countsFrom(goal)?.takeIf { it <= today }, today)).min(),
            times = times,
            marks = marks,
            currentRun = runs?.first,
            bestRun = runs?.second,
            last30 = if (habit) rate(times, today.minusDays(29), today) else null,
            last90 = if (habit) rate(times, today.minusDays(89), today) else null,
            ticks = counted.size,
            points = entries.filter { it.goalId == goal.id && it.kind in Experience.KINDS }.sumOf { it.amount },
            lapses = ownLapses.sortedWith(compareByDescending<Lapse> { it.day }.thenByDescending { it.at }),
            notes = ownTicks.filter { !it.note.isNullOrBlank() }.sortedByDescending { it.at },
            amount = if (goal.hasAmount) counted.sumOf { it.amount ?: goal.amount ?: 0.0 } else null,
            seconds = counted.sumOf { it.seconds ?: 0 },
            steps = stepsDone,
            finishedOn = stepsDone.takeIf { it.isNotEmpty() && it.all { s -> s.day != null } }?.maxOf { it.day!! },
        )
    }

    /** The days of [first]'s month as a calendar: blanks before the 1st so Monday comes first. */
    fun monthCells(first: LocalDate): List<LocalDate?> {
        val start = Days.periodStart(first, Period.MONTHLY)
        val end = Days.periodEnd(first, Period.MONTHLY)
        val days = generateSequence(start) { it.plusDays(1).takeIf { next -> next <= end } }.toList()
        return List(start.dayOfWeek.isoDayNumber - 1) { null } + days
    }

    /** "312 pages", "5.5 miles". */
    fun amountText(amount: Double, unit: String?): String =
        Amounts.format(amount) + (unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")

    /** "9 h 40 min", "45 min", "under a minute". */
    fun timeText(seconds: Int): String {
        val minutes = seconds / 60
        return when {
            minutes < 1 -> "under a minute"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }
}
