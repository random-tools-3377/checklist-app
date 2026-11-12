package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * How one habit went over a stretch of days, counting each time it was due. A time is [done] only
 * when it was really ticked; one covered by a skip stays in [due] and counts under [skipped], so
 * skipping never raises a done percentage.
 */
data class GoalSummary(
    val goal: Goal,
    val due: Int,
    val done: Int,
    val skipped: Int,
    val missed: Int,
    /** Ticks in the stretch. */
    val ticks: Int,
    /** Points it earned in the stretch. */
    val points: Int,
    /** Longest run of times in a row it was done, within the stretch. A skipped time neither adds to a run nor ends it. */
    val longestRun: Int,
    /** The run it is on now, if the stretch reaches today. */
    val currentRun: Int,
) {
    /** Share of the times it was due that it got done, 0 to 1. */
    val consistency: Double get() = if (due == 0) 0.0 else done.toDouble() / due
}

/** Everything the review shows for a stretch of days. */
data class ReviewSummary(
    val from: LocalDate,
    val to: LocalDate,
    val goals: List<GoalSummary>,
    /** How often each reason was given, most first, as the user sees it (their own ones by their words). */
    val reasons: List<Pair<String, Int>>,
    val earned: Int,
    /** Rewards bought, less ones returned. Below 0 when more came back than was bought. */
    val spent: Int,
    val lost: Int,
    val days: List<DayRecord>,
    val gaps: List<Gap>,
    /** Due and really done (ticked, not skipped) for each day of the week, Monday = 1. */
    val weekdays: Map<Int, Pair<Int, Int>>,
    /** Points earned on each day of the stretch (ticks, bonuses, refunds), for the chart. */
    val earnedByDay: Map<LocalDate, Int> = emptyMap(),
    /** Points lost on each day of the stretch (penalties), as positive numbers. */
    val lostByDay: Map<LocalDate, Int> = emptyMap(),
    /**
     * Each category the goals use, in their order, then goals without one. Empty when no goal
     * has a category.
     */
    val categories: List<CategorySummary> = emptyList(),
    /** Savings interest paid in the stretch. */
    val interest: Int = 0,
    /**
     * Bets on yourself in the stretch, from before bets were removed: stakes placed (taken off),
     * wins (paid) and stakes given back, together. 0 for a stretch with none.
     */
    val bets: Int = 0,
) {
    val mostSkipped: GoalSummary? get() = goals.filter { it.skipped > 0 }.maxByOrNull { it.skipped }
    val mostMissed: GoalSummary? get() = goals.filter { it.missed > 0 }.maxByOrNull { it.missed }
    val mostConsistent: GoalSummary? get() = goals.filter { it.due > 0 }.maxWithOrNull(compareBy({ it.consistency }, { it.due }))
    val mostTicked: GoalSummary? get() = goals.filter { it.ticks > 0 }.maxByOrNull { it.ticks }

    private fun rate(day: Int): Double? = weekdays[day]?.takeIf { it.first > 0 }?.let { it.second.toDouble() / it.first }

    /** Each weekday with something due, and its share done; empty when they all went the same. */
    private val rates: Map<Int, Double>
        get() = (1..7).mapNotNull { day -> rate(day)?.let { day to it } }.toMap().takeIf { it.values.distinct().size > 1 }.orEmpty()
    val bestWeekday: Int? get() = rates.maxByOrNull { it.value }?.key
    val worstWeekday: Int? get() = rates.minByOrNull { it.value }?.key
}

object Review {
    /**
     * Due and done from the habits, and points (ticks and bonuses) from every goal, per category.
     * A removed goal's points stay with its category, never under "No category".
     */
    private fun byCategory(goals: List<Goal>, summaries: List<GoalSummary>, entries: List<Entry>): List<CategorySummary> {
        val names = Categories.of(goals)
        if (names.isEmpty()) return emptyList()
        val categoryOf = goals.associate { it.id to it.category }
        val points = entries.filter { it.goalId != null && (it.kind == EntryKind.TICK || it.kind == EntryKind.BONUS) }
            .groupBy { categoryOf[it.goalId] }.mapValues { (_, e) -> e.sumOf { it.amount } }
        return (names + listOf<String?>(null)).map { name ->
            val inIt = summaries.filter { it.goal.category == name }
            CategorySummary(name, inIt.sumOf { it.due }, inIt.sumOf { it.done }, points[name] ?: 0)
        }.filter { it.name != null || it.due > 0 || it.points > 0 }
    }

    /** The day, the Monday-to-Sunday week, or the calendar month containing [day]. */
    fun range(day: LocalDate, period: Period): Pair<LocalDate, LocalDate> = Days.periodStart(day, period) to Days.periodEnd(day, period)

    /**
     * Sums up [from] to [to]. A habit counts each time its window ends inside the stretch, up to
     * [today] (days still to come aren't counted yet): done only if ticked enough, skipped if a
     * skip covered it (never done, whatever the skip covered), missed if a miss was recorded.
     * [goals] may include removed ones: they aren't counted as due, but their points keep their category.
     * Points come from the ledger: earned (ticks, bonuses, refunds, perfect days, challenges),
     * spent (rewards, less returns), lost (penalties, less those of skips taken back), interest and bets. A habit counts only
     * from the day [countsFrom] gives it, the day it was added or brought back. With
     * [hidePrivate], for while the privacy lock is on for private goals, those goals are left
     * out with their ticks, skips, misses and points, as on Today.
     */
    fun summarize(
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        goals: List<Goal>,
        ticks: List<Tick>,
        lapses: List<Lapse>,
        entries: List<Entry>,
        gaps: List<Gap>,
        hidePrivate: Boolean = false,
        countsFrom: (Goal) -> LocalDate? = { it.addedOn() },
    ): ReviewSummary {
        val hidden = if (hidePrivate) PrivateGoals.ids(goals) else emptySet()
        if (hidden.isNotEmpty()) {
            return summarize(
                from, to, today,
                goals = goals.filter { it.id !in hidden },
                ticks = ticks.filter { it.goalId !in hidden },
                lapses = lapses.filter { it.goalId !in hidden },
                entries = entries.filter { entry -> entry.goalId?.let { it in hidden } != true },
                gaps = gaps,
                countsFrom = countsFrom,
            )
        }
        val live = lapses.filter { !it.forgotten }
        val inRange = entries.filter { it.day >= from && it.day <= to }
        val active = goals.filter { !it.archived }
        val habits = active.filter { it.isHabitLike && it.repeat != null }
        val windows = PastWindows(habits, ticks, lapses)
        val weekdayDue = IntArray(8)
        val weekdayDone = IntArray(8)
        val summaries = habits.map { goal ->
            var due = 0
            var done = 0
            var skipped = 0
            var missed = 0
            var run = 0
            var longest = 0
            var day = countsFrom(goal)?.let { maxOf(from, it) } ?: from
            while (day <= minOf(to, today)) {
                val window = windows.window(goal, day)?.takeIf { it.end == day }
                if (window != null) {
                    val count = goal.timesDone(ticks.filter { it.countsToward(goal, window.start) })
                    val skips = live.count { it.goalId == goal.id && it.kind == LapseKind.SKIP && it.periodStart == window.start }
                    val finished = count >= goal.target
                    // Nothing left of it to do because of a skip: skipped, never done.
                    val covered = !finished && (if (window.period == Period.DAILY) skips > 0 else count + skips >= goal.target)
                    // A week, month or year only partly in the goal's dates counts once it is done.
                    if (finished || covered || !goal.repeat!!.isPartial(window)) {
                        due++
                        if (skips > 0 && !finished) skipped++
                        if (live.any { it.goalId == goal.id && it.kind == LapseKind.MISS && it.periodStart == window.start }) missed++
                        weekdayDue[day.dayOfWeek.isoDayNumber]++
                        if (finished) {
                            done++
                            weekdayDone[day.dayOfWeek.isoDayNumber]++
                            run++
                            longest = maxOf(longest, run)
                        } else if (!covered && day < today) {
                            run = 0
                        }
                    }
                }
                day = day.plusDays(1)
            }
            val goalTicks = ticks.filter { it.goalId == goal.id && it.stepId == null && !it.isUse && it.day >= from && it.day <= to }
            val points = inRange.filter { it.goalId == goal.id && (it.kind == EntryKind.TICK || it.kind == EntryKind.BONUS) }.sumOf { it.amount }
            GoalSummary(goal, due, done, skipped, missed, goalTicks.size, points, longest, if (to >= today) run else 0)
        }

        val reasons = live.filter { it.day >= from && it.day <= to }.mapNotNull { reasonLabel(it.reason, it.reasonText) }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
        fun sum(vararg kinds: EntryKind) = inRange.filter { it.kind in kinds }.sumOf { it.amount }
        val earnedKinds = setOf(EntryKind.TICK, EntryKind.BONUS, EntryKind.REFUND, EntryKind.PERFECT_DAY, EntryKind.CHALLENGE)
        val earnedByDay = inRange.filter { it.kind in earnedKinds }.groupBy { it.day }.mapValues { (_, e) -> e.sumOf { it.amount } }
        // A skip taken back gave its penalty back the same day, so that day lost nothing by it.
        val lostByDay = inRange.filter { it.kind == EntryKind.PENALTY }.groupBy { it.day }.mapValues { (_, e) -> -e.sumOf { it.amount } }.filterValues { it != 0 }
        return ReviewSummary(
            from = from,
            to = to,
            goals = summaries,
            reasons = reasons,
            earned = sum(*earnedKinds.toTypedArray()),
            spent = -sum(EntryKind.PURCHASE, EntryKind.RETURN),
            lost = -sum(EntryKind.PENALTY),
            // Days still to come keep their place on the calendar, with nothing due yet.
            days = StreakCounter.records(from, to, active, ticks, lapses, countsFrom).map { if (it.day > today) it.copy(due = 0, done = 0) else it },
            gaps = gaps.filter { it.to >= from && it.from <= to },
            weekdays = (1..7).associateWith { weekdayDue[it] to weekdayDone[it] },
            earnedByDay = earnedByDay,
            lostByDay = lostByDay,
            categories = byCategory(goals, summaries, inRange),
            interest = sum(EntryKind.INTEREST),
            bets = sum(EntryKind.BET),
        )
    }
}
