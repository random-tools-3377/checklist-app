package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.random.Random

/** What a skip would mean, shown before the user confirms it. */
sealed interface SkipCheck {
    /** It can be skipped. Over the limit it needs a reason, costs [cost] points and a penalty task. */
    data class Allowed(val used: Int, val allowed: Int, val overLimit: Boolean, val cost: Int) : SkipCheck
    data object NotDue : SkipCheck
    data object AlreadyDone : SkipCheck
    data object NotFound : SkipCheck
}

sealed interface SkipResult {
    data class Skipped(val lapse: Lapse, val penaltyId: Long?) : SkipResult

    /** Over the limit, a skip needs a reason. */
    data object NeedsReason : SkipResult
    data class NotAllowed(val check: SkipCheck) : SkipResult
}

/**
 * Skips, misses, the allowance and penalties.
 *
 * One allowance per goal, shared by skips and misses, counted per calendar week (from Monday) or
 * month. Within it, a skip needs nothing and a miss only its reason. Over the limit, each one
 * needs a reason, costs half the points the goal pays (rounded down), and owes a penalty task
 * that is done right away: for a skip, the moment it is confirmed; for a miss, the next time the
 * app opens.
 */
class Lapses(
    private val store: Store,
    private val book: Book,
    private val random: Random = Random.Default,
) {
    companion object {
        /** The last day checked for misses, as an ISO date. */
        const val CHECKED_THROUGH = "misses_checked_through"

        /** The first day skips, misses and gaps are tracked from, as an ISO date. */
        const val TRACKING_SINCE = "tracking_since"

        /** Days of history checked at most, so a long time away doesn't take forever. */
        const val MAX_DAYS_BACK = 400
    }

    /** Half of what the goal pays: its points for a habit, its finishing bonus for a project. */
    fun cost(goal: Goal): Int = (if (goal.kind == GoalKind.PROJECT) goal.bonus else goal.points) / 2

    /** Skips and misses that count against the allowance of the week or month containing [day]. */
    private suspend fun used(goal: Goal, day: LocalDate): Int {
        val window = goal.allowanceWindow
        val from = Days.periodStart(day, window)
        val to = Days.periodEnd(day, window)
        if (goal.kind == GoalKind.TODO) {
            // To-dos share one allowance between them.
            val todos = store.activeGoals().filter { it.kind == GoalKind.TODO }.map { it.id }.toSet()
            return store.allLapses(from, to).count { it.goalId in todos && !it.forgotten }
        }
        return store.lapses(goal.id, from, to).count { !it.forgotten }
    }

    /** Whether the habit can be skipped today, and what it would cost. */
    suspend fun checkSkip(goalId: Long): SkipCheck {
        val goal = store.goal(goalId)
        val repeat = goal?.repeat
        if (goal == null || goal.archived || !goal.isHabitLike || repeat == null) return SkipCheck.NotFound
        val today = book.today()
        val window = repeat.window(today) ?: return SkipCheck.NotDue
        val ticks = store.habitTicks(goalId, window.start).filter { it.countsToward(goal, window.start) }
        val skips = store.lapses(goalId, window.start, window.end).filter { it.kind == LapseKind.SKIP && it.periodStart == window.start }
        val times = goal.timesDone(ticks)
        val done = if (window.period == Period.DAILY) times >= goal.target || skips.isNotEmpty() else times + skips.size >= goal.target
        if (done) return SkipCheck.AlreadyDone
        val used = used(goal, today)
        val allowed = book.difficulty(today).allowed(goal.allowed)
        val over = used >= allowed
        return SkipCheck.Allowed(used, allowed, over, if (over) cost(goal) else 0)
    }

    /**
     * Skips the habit for today (for a "times per" habit, one of its times). Within the
     * allowance no reason is needed. Over the limit, [reason] is required.
     */
    suspend fun skip(goalId: Long, reason: Reason?, note: String?, reasonText: String? = null): SkipResult = store.transaction {
        val check = checkSkip(goalId)
        if (check !is SkipCheck.Allowed) return@transaction SkipResult.NotAllowed(check)
        if (check.overLimit && reason == null) return@transaction SkipResult.NeedsReason
        val goal = store.goal(goalId)!!
        val today = book.today()
        val window = goal.repeat!!.window(today)!!
        record(goal, LapseKind.SKIP, today, window.period, window.start, check.overLimit, reason, note, reasonText)
    }

    /** Saves a skip or miss, with its penalty when over the limit. */
    private suspend fun record(
        goal: Goal,
        kind: LapseKind,
        day: LocalDate,
        period: Period?,
        periodStart: LocalDate,
        overLimit: Boolean,
        reason: Reason?,
        note: String?,
        reasonText: String? = null,
    ): SkipResult.Skipped {
        val now = book.millis()
        val cost = if (overLimit) cost(goal) else 0
        val lapse = Lapse(
            goalId = goal.id, kind = kind, day = day, period = period, periodStart = periodStart, at = now,
            overLimit = overLimit, cost = cost, reason = reason, note = note?.takeIf { it.isNotBlank() }, label = goal.name,
            reasonText = reasonText?.takeIf { reason == Reason.OTHER && it.isNotBlank() },
        )
        val id = store.addLapse(lapse)
        var penaltyId: Long? = null
        if (overLimit) {
            if (cost > 0) {
                // Counted on the day it belongs to, so a week's losses add up where they happened.
                store.addEntry(
                    Entry(at = now, day = day, kind = EntryKind.PENALTY, amount = -cost, goalId = goal.id, label = goal.name, lapseId = id),
                )
            }
            penaltyId = store.addPenalty(Penalty(lapseId = id, createdAt = now))
        }
        return SkipResult.Skipped(lapse.copy(id = id), penaltyId)
    }

    /**
     * Finds what was missed since the last check: every habit whose time ran out (a due day, or
     * the end of its week, month or year) without being done or skipped, and every project whose
     * deadline passed unfinished. Runs when the app opens and when the day ends. The first time,
     * it only notes where to start, so nothing from before is counted.
     */
    suspend fun closeDays(): List<Lapse> = store.transaction {
        val today = book.today()
        val yesterday = today.minusDays(1)
        val checked = store.setting(CHECKED_THROUGH)?.let { LocalDate.parse(it) }
        if (checked == null) {
            store.putSetting(CHECKED_THROUGH, yesterday.toString())
            store.putSetting(TRACKING_SINCE, today.toString())
            return@transaction emptyList()
        }
        if (checked >= yesterday) return@transaction emptyList()
        val from = maxOf(checked.plusDays(1), yesterday.minusDays(MAX_DAYS_BACK))
        val goals = store.activeGoals()
        val ticks = store.habitTicksSince(Days.earliestOpenDay(from).minusDays(7))
        val found = mutableListOf<Lapse>()

        var day = from
        while (day <= yesterday) {
            for (goal in goals) {
                if (goal.kind == GoalKind.TODO && goal.repeat == null) {
                    // An undated to-do done on this day takes it as its date; one not done waits.
                    if (store.habitTicks(goal.id, day).any { it.stepId == null }) store.setRepeat(goal.id, Repeat(RepeatKind.ONCE, day))
                    continue
                }
                if (goal.isHabitLike) {
                    val repeat = goal.repeat ?: continue
                    val window = repeat.window(day) ?: continue
                    if (window.end != day) continue // its time hasn't run out yet
                    val count = goal.timesDone(ticks.filter { it.countsToward(goal, window.start) })
                    val skips = store.lapses(goal.id, window.start, window.end)
                        .count { it.kind == LapseKind.SKIP && it.periodStart == window.start }
                    val done = if (window.period == Period.DAILY) count >= goal.target || skips > 0 else count + skips >= goal.target
                    if (done) continue
                    val over = used(goal, day) >= book.difficulty(day).allowed(goal.allowed)
                    found += record(goal, LapseKind.MISS, day, window.period, window.start, over, null, null).lapse
                } else if (goal.deadline == day) {
                    val steps = store.activeSteps(goal.id)
                    val doneSteps = store.stepTicks(goal.id).mapNotNull { it.stepId }.toSet()
                    if (steps.isNotEmpty() && steps.all { it.id in doneSteps }) continue
                    val over = used(goal, day) >= book.difficulty(day).allowed(goal.allowed)
                    found += record(goal, LapseKind.MISS, day, null, day, over, null, null).lapse
                }
            }
            day = day.plusDays(1)
        }
        store.putSetting(CHECKED_THROUGH, yesterday.toString())
        found
    }

    /** Gives a miss (or a skip over the limit) its reason and an optional note. */
    suspend fun explain(lapseId: Long, reason: Reason, note: String?, reasonText: String? = null) = store.transaction {
        val lapse = store.lapse(lapseId) ?: return@transaction
        store.updateLapse(
            lapse.copy(
                reason = reason,
                note = note?.takeIf { it.isNotBlank() },
                reasonText = reasonText?.takeIf { reason == Reason.OTHER && it.isNotBlank() },
            ),
        )
    }

    /**
     * "I forgot to tick it": the miss is undone. The goal is ticked for the day it was missed,
     * paying its points, and any penalty the miss caused is refunded and its penalty task
     * dropped if it wasn't done yet.
     */
    suspend fun forgotToTick(lapseId: Long): Boolean = store.transaction {
        val lapse = store.lapse(lapseId)
        if (lapse == null || lapse.kind != LapseKind.MISS || lapse.forgotten || lapse.period == null) return@transaction false
        val goal = store.goal(lapse.goalId) ?: return@transaction false
        val now = book.millis()
        val tickId = store.addTick(
            Tick(goalId = goal.id, stepId = null, at = now, day = lapse.day, period = lapse.period, periodStart = lapse.periodStart),
        )
        store.addEntry(
            Entry(at = now, day = book.today(), kind = EntryKind.TICK, amount = book.difficulty(lapse.day).pay(goal.points), goalId = goal.id, tickId = tickId, label = goal.name),
        )
        if (lapse.cost > 0) {
            store.addEntry(
                Entry(at = now, day = book.today(), kind = EntryKind.REFUND, amount = lapse.cost, goalId = goal.id, label = goal.name, lapseId = lapse.id),
            )
        }
        store.penaltyForLapse(lapse.id)?.let { penalty ->
            if (penalty.doneAt == null && penalty.cancelledAt == null) store.updatePenalty(penalty.copy(cancelledAt = now))
        }
        store.updateLapse(lapse.copy(forgotten = true, reason = lapse.reason ?: Reason.FORGOT))
        true
    }

    /** The penalty task to do now, if any. */
    suspend fun openPenalty(): Penalty? = store.openPenalties().firstOrNull()

    /** Pays the penalty off by making up [goalId]: doing it now, for no points. */
    suspend fun makeUp(penaltyId: Long, goalId: Long): Penalty? = store.transaction {
        val penalty = store.penalty(penaltyId) ?: return@transaction null
        val goal = store.goal(goalId) ?: return@transaction null
        penalty.copy(choice = PenaltyChoice.MAKE_UP, goalId = goalId, taskId = null, label = "${goal.icon} ${goal.name}")
            .also { store.updatePenalty(it) }
    }

    /** Pays the penalty off with one of the user's penalty tasks, drawn at random. Null if there are none. */
    suspend fun drawTask(penaltyId: Long): Penalty? = store.transaction {
        val penalty = store.penalty(penaltyId) ?: return@transaction null
        val tasks = store.penaltyTasks()
        if (tasks.isEmpty()) return@transaction null
        val task = tasks[random.nextInt(tasks.size)]
        penalty.copy(choice = PenaltyChoice.TASK, taskId = task.id, goalId = null, label = task.name).also { store.updatePenalty(it) }
    }

    /** The penalty task was done. Penalty tasks pay nothing. */
    suspend fun finishPenalty(penaltyId: Long): Boolean = store.transaction {
        val penalty = store.penalty(penaltyId) ?: return@transaction false
        if (penalty.choice == null || penalty.doneAt != null) return@transaction false
        store.updatePenalty(penalty.copy(doneAt = book.millis()))
        true
    }
}
