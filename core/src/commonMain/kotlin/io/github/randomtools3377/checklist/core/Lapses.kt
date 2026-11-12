package io.github.randomtools3377.checklist.core

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

/** What a skip would mean, shown before the user confirms it. */
sealed interface SkipCheck {
    /** It can be skipped. Over the limit it needs a reason, costs [cost] points and a penalty task. */
    data class Allowed(val used: Int, val allowed: Int, val overLimit: Boolean, val cost: Int) : SkipCheck
    data object NotDue : SkipCheck
    data object AlreadyDone : SkipCheck
    data object NotFound : SkipCheck
}

/** A skip that was taken back, and the penalty points that came back with it (0 within the allowance). */
data class SkipTakenBack(val skip: Lapse, val refunded: Int)

/**
 * Takes the skip [skip] back, as if it never happened. The skip itself goes, and with it the
 * allowance it used and what it did to the streaks. Over the limit, its penalty points come back
 * with an entry in the points history, and its penalty task is dropped if it wasn't done yet (one
 * already done stays done). Nothing is left pointing at the skip: its number could be given to
 * a later skip or miss once the data has been through a backup. A skip or miss recorded after it
 * that only went over the limit because of it is put back within the allowance too (see
 * [freeAllowance]). Returns the points given back for the skip itself. The user's [Rules] must
 * be in use.
 */
internal suspend fun Store.takeBack(skip: Lapse, at: Long): Int {
    val entries = entriesForLapse(skip.id)
    val taken = -entries.filter { it.kind == EntryKind.PENALTY }.sumOf { it.amount }
    entries.forEach { updateEntry(it.copy(lapseId = null)) }
    if (taken > 0) {
        // On the skip's own day, as the penalty was, so that day's losses come out as they were.
        addEntry(Entry(at = at, day = skip.day, kind = EntryKind.PENALTY, amount = taken, goalId = skip.goalId, label = skip.label))
    }
    penaltyForLapse(skip.id)?.let { penalty ->
        val open = penalty.doneAt == null && penalty.cancelledAt == null
        updatePenalty(penalty.copy(lapseId = null, cancelledAt = if (open) at else penalty.cancelledAt))
    }
    removeLapse(skip.id)
    freeAllowance(skip, at)
    return taken.coerceAtLeast(0)
}

/**
 * The allowance [skip] used is free again now that it is gone. A skip or miss recorded after it
 * that went over the limit only because the skip had used the allowance up is within it after
 * all: its penalty points come back, its penalty task is dropped if it wasn't done yet, and it
 * no longer counts as over the limit. One that would have gone over anyway stays as it is.
 */
private suspend fun Store.freeAllowance(skip: Lapse, at: Long) {
    // Every allowance the skip counted against starts and ends within its week and its month.
    val from = minOf(Days.periodStart(skip.day, Period.WEEKLY), Days.periodStart(skip.day, Period.MONTHLY))
    val to = maxOf(Days.periodEnd(skip.day, Period.WEEKLY), Days.periodEnd(skip.day, Period.MONTHLY))
    // To-dos share one allowance between them; every other goal has its own.
    val todos = activeGoals().filter { it.kind == GoalKind.TODO }.map { it.id }.toSet()
    val shared = allLapses(from, to).filter { !it.forgotten && (it.goalId == skip.goalId || (skip.goalId in todos && it.goalId in todos)) }
    val later = shared.filter { it.overLimit && it.id > skip.id }.sortedBy { it.id }
    if (later.isEmpty()) return
    val difficulty = DifficultySetting.of(this)
    for (lapse in later) {
        val goal = goal(lapse.goalId) ?: continue
        val start = Days.periodStart(lapse.day, goal.allowanceWindow)
        val end = Days.periodEnd(lapse.day, goal.allowanceWindow)
        if (skip.day < start || skip.day > end) continue
        // What had been used when it was recorded, now without the skip.
        val used = shared.count { it.id < lapse.id && it.day >= start && it.day <= end }
        // Only one the skip itself pushed over: with it, the allowance was just used up.
        if (used != difficulty.on(lapse.day).allowed(goal.allowed) - 1) continue
        val paid = -entriesForLapse(lapse.id).filter { it.kind == EntryKind.PENALTY }.sumOf { it.amount }
        if (paid > 0) {
            addEntry(Entry(at = at, day = lapse.day, kind = EntryKind.PENALTY, amount = paid, goalId = lapse.goalId, label = lapse.label, lapseId = lapse.id))
        }
        penaltyForLapse(lapse.id)?.takeIf { it.doneAt == null && it.cancelledAt == null }?.let { updatePenalty(it.copy(cancelledAt = at)) }
        updateLapse(lapse.copy(overLimit = false, cost = 0))
    }
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
 * needs a reason, costs half the points the goal pays (rounded down; the share is the user's to
 * change, see [Rules.penaltyPercent]), and owes a penalty task
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

        /**
         * Days back misses are recorded at most: as far back as gaps are looked for, so after a
         * long time away every miss found can be part of one gap, not each owe its own reason.
         * Days before that are still gone through, so to-dos and "since last done" goals move on.
         */
        const val MISSES_DAYS_BACK = Gaps.LOOK_BACK_DAYS

        /**
         * How many days back a miss opened later, from the review or the points history, still
         * offers "I forgot to tick it": the same two weeks a penalty can be made up over. On the
         * misses screen and the gap check-in, where a miss is first asked about, it is always offered.
         */
        const val FORGET_DAYS = 14

        /**
         * Whether a miss opened later can still be said to be a forgotten tick on [today]: a
         * habit's miss (a project's missed deadline never is) from the last [FORGET_DAYS] days.
         */
        fun canForgetLater(lapse: Lapse, today: LocalDate): Boolean =
            lapse.kind == LapseKind.MISS && !lapse.forgotten && lapse.period != null && lapse.day >= today.minusDays(FORGET_DAYS)

        /**
         * Whether [lapse] came from "I slipped" on a goal to quit (see [slip]): a miss of such a
         * goal recorded during its own day. A miss found once the day had ended, because it wasn't
         * ticked as avoided, is an ordinary miss. [quit] says whether its goal is one to quit.
         */
        fun isSlip(lapse: Lapse, quit: Boolean, dayEndHour: Int, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
            quit && lapse.kind == LapseKind.MISS &&
                Days.dayOf(Instant.fromEpochMilliseconds(lapse.at).toLocalDateTime(zone), dayEndHour) == lapse.day
    }

    /**
     * What going over the limit costs: half (see [Rules.penaltyPercent]) of what the goal pays,
     * rounded down: its points for a habit, its finishing bonus for a project.
     */
    fun cost(goal: Goal): Int {
        val pays = if (goal.kind == GoalKind.PROJECT) goal.bonus else goal.points
        return (pays.toLong() * Rules.current.penaltyPercent / 100).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

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
        Rules.load(store)
        val goal = store.goal(goalId)
        val repeat = goal?.repeat
        if (goal == null || goal.archived || !goal.isHabitLike || repeat == null) return SkipCheck.NotFound
        val today = book.today()
        val window = repeat.window(today) ?: return SkipCheck.NotDue
        val ticks = store.habitTicks(goalId, window.start).filter { it.countsToward(goal, window.start) }
        val skips = store.lapses(goalId, window.start, window.end).filter { it.kind == LapseKind.SKIP && it.periodStart == window.start && !it.forgotten }
        val times = goal.timesDone(ticks)
        // Nothing left of it to skip: done, or skipped already.
        val settled = if (window.period == Period.DAILY) times >= goal.target || skips.isNotEmpty() else times + skips.size >= goal.target
        if (settled) return SkipCheck.AlreadyDone
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
        Rules.load(store)
        val check = checkSkip(goalId)
        if (check !is SkipCheck.Allowed) return@transaction SkipResult.NotAllowed(check)
        if (check.overLimit && reason == null) return@transaction SkipResult.NeedsReason
        val goal = store.goal(goalId)!!
        val today = book.today()
        val window = goal.repeat!!.window(today)!!
        record(goal, LapseKind.SKIP, today, window.period, window.start, check.overLimit, reason, note, reasonText)
    }

    /**
     * Takes back a skip of [goalId] made today, as if it never happened: the allowance it used,
     * its penalty points and open penalty task over the limit, and what it did to the streaks
     * (see [takeBack]). For a goal counted so many times a week, month or year, it takes back one
     * skip of its times, the latest made today. A skip can only be taken back until the day it
     * was made on ends. Null when there is none to take back.
     */
    suspend fun takeBackSkip(goalId: Long): SkipTakenBack? = store.transaction {
        Rules.load(store)
        val today = book.today()
        val skip = store.lapses(goalId, today, today).filter { it.kind == LapseKind.SKIP && !it.forgotten }.maxByOrNull { it.id }
            ?: return@transaction null
        SkipTakenBack(skip, store.takeBack(skip, book.millis()))
    }

    /** Takes back the skip [lapseId], if it is a skip made today (see [takeBackSkip]). */
    suspend fun takeBackSkipById(lapseId: Long): SkipTakenBack? = store.transaction {
        Rules.load(store)
        val skip = store.lapse(lapseId)?.takeIf { it.kind == LapseKind.SKIP && !it.forgotten && it.day == book.today() }
            ?: return@transaction null
        SkipTakenBack(skip, store.takeBack(skip, book.millis()))
    }

    /** The skip the penalty task [penalty] is owed for, if it was made today and so can still be taken back. */
    suspend fun skipBehind(penalty: Penalty): Lapse? {
        val lapse = penalty.lapseId?.let { store.lapse(it) } ?: return null
        return lapse.takeIf { it.kind == LapseKind.SKIP && !it.forgotten && it.day == book.today() }
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
        Rules.load(store)
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
        val missesFrom = yesterday.minusDays(MISSES_DAYS_BACK)
        val goals = store.activeGoals()
        // Like the streaks, a goal is never missed on a day before it was added or brought back.
        val addedDays = goals.associate { it.id to it.addedOn() }
        val ticks = store.habitTicksSince(Days.earliestOpenDay(from).minusDays(7))
        val found = mutableListOf<Lapse>()

        // "Since last done" goals move on day by day while they are late, so keep their latest repeat.
        val repeats = goals.associate { it.id to it.repeat }.toMutableMap()
        // One whose day is before the days looked at here would never come round again (its date
        // was set in the past): it is due today instead, with no miss.
        for (goal in goals) {
            val stuck = repeats[goal.id]?.takeIf { it.kind == RepeatKind.AFTER_DONE && it.start < from } ?: continue
            val moved = stuck.copy(start = today)
            repeats[goal.id] = moved
            store.setRepeat(goal.id, moved)
        }
        var day = from
        while (day <= yesterday) {
            for (goal in goals) {
                val afterDone = repeats[goal.id]?.takeIf { it.kind == RepeatKind.AFTER_DONE }
                if (afterDone != null) {
                    // Due on its start. After a long time away that can be before the days checked
                    // here; then it counts on the first of them, so it still moves on.
                    val dueOn = if (afterDone.start < from && afterDone.start > checked) from else afterDone.start
                    if (day != dueOn) continue
                    val start = afterDone.start
                    if (goal.timesDone(ticks.filter { it.countsToward(goal, start) }) >= goal.target) {
                        // Already done on its day, like a goal switched to this in the editor after
                        // being ticked (a tick usually moves it on at once): the next counts from then.
                        val next = afterDone.copy(start = day.plusDays(afterDone.interval.coerceAtLeast(1)), lateSince = null)
                        repeats[goal.id] = next
                        store.setRepeat(goal.id, next)
                        continue
                    }
                    val skipped = store.lapses(goal.id, start, day).any { it.kind == LapseKind.SKIP && it.periodStart == start && !it.forgotten }
                    // One miss on the day it was due; after that it stays on Today with no more.
                    if (!skipped && afterDone.lateSince == null && day >= missesFrom) {
                        val over = used(goal, day) >= book.difficulty(day).allowed(goal.allowed)
                        found += record(goal, LapseKind.MISS, day, Period.DAILY, day, over, null, null).lapse
                    }
                    val moved = afterDone.copy(start = day.plusDays(1), lateSince = afterDone.lateSince ?: day)
                    repeats[goal.id] = moved
                    store.setRepeat(goal.id, moved)
                    continue
                }
                if (goal.kind == GoalKind.TODO && goal.repeat == null) {
                    // An undated to-do done on this day takes it as its date; one not done waits.
                    if (store.habitTicks(goal.id, day).any { it.stepId == null }) store.setRepeat(goal.id, Repeat(RepeatKind.ONCE, day))
                    continue
                }
                if (day < missesFrom) continue
                if (addedDays[goal.id]?.let { day < it } == true) continue
                if (goal.isHabitLike) {
                    val repeat = goal.repeat ?: continue
                    val window = repeat.window(day) ?: continue
                    if (window.end != day) continue // its time hasn't run out yet
                    val count = goal.timesDone(ticks.filter { it.countsToward(goal, window.start) })
                    val windowLapses = store.lapses(goal.id, window.start, window.end)
                    // Already missed, by "I slipped" on a goal to quit: not twice.
                    if (windowLapses.any { it.kind == LapseKind.MISS && it.periodStart == window.start && !it.forgotten }) continue
                    val skips = windowLapses.count { it.kind == LapseKind.SKIP && it.periodStart == window.start && !it.forgotten }
                    // Done, or skipped: either way there is nothing left of it to miss.
                    val settled = if (window.period == Period.DAILY) count >= goal.target || skips > 0 else count + skips >= goal.target
                    if (settled) continue
                    // A first or last week, month or year only partly in the goal's dates isn't missed.
                    if (repeat.isPartial(window)) continue
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
     * "I forgot to tick it": the miss is undone. The goal is done for the day, week, month or
     * year it was missed in: what was left of it is ticked, paying its points and any finishing bonus
     * (see [Book.finishMissedWindow]). Any penalty the miss caused is refunded and its penalty
     * task dropped if it wasn't done yet. The day gets back what it would have had (see
     * [DayBack]): its perfect-day bonus and its place in the combo, once everything due on it is
     * done. Inside a gap, the gap goes away once every miss in it has turned out to be a
     * forgotten tick (see [Gaps.clearIfForgotten]). It works the same from the misses screen,
     * the gap check-in, and later from the review or the points history.
     */
    suspend fun forgotToTick(lapseId: Long): Boolean = store.transaction {
        Rules.load(store)
        val lapse = store.lapse(lapseId)
        if (lapse == null || lapse.kind != LapseKind.MISS || lapse.forgotten || lapse.period == null) return@transaction false
        val goal = store.goal(lapse.goalId) ?: return@transaction false
        val now = book.millis()
        if (lapse.cost > 0) {
            store.addEntry(
                Entry(at = now, day = book.today(), kind = EntryKind.REFUND, amount = lapse.cost, goalId = goal.id, label = goal.name, lapseId = lapse.id),
            )
        }
        // A "since last done" goal done after all: the next one counts from that day. Only while
        // it is still late from this miss; corrected later, once it has been done since, it has moved on.
        goal.repeat?.takeIf { it.kind == RepeatKind.AFTER_DONE && it.lateSince == lapse.day }?.let { r ->
            store.setRepeat(goal.id, r.copy(start = maxOf(lapse.day.plusDays(r.interval.coerceAtLeast(1)), book.today()), lateSince = null))
        }
        store.penaltyForLapse(lapse.id)?.let { penalty ->
            if (penalty.doneAt == null && penalty.cancelledAt == null) store.updatePenalty(penalty.copy(cancelledAt = now))
        }
        store.updateLapse(lapse.copy(forgotten = true, reason = lapse.reason ?: Reason.FORGOT))
        // Ticked once the miss is undone, so a challenge it completes isn't held back by it.
        book.finishMissedWindow(goal, lapse.day, lapse.period, lapse.periodStart)
        // The day gets back what it would have had, if it is now perfect after all.
        DayBack(store, book).restore(lapse.day)
        // A gap whose every miss turned out to be a forgotten tick was no gap.
        lapse.gapId?.let { Gaps(store, book).clearIfForgotten(it) }
        true
    }

    /**
     * "I slipped" on a goal to quit: today is a miss straight away, with a reason asked for like
     * any miss, and over the limit a penalty. A tick for today ("Avoided") is taken back.
     */
    suspend fun slip(goalId: Long): Lapse? = store.transaction {
        Rules.load(store)
        val goal = store.goal(goalId) ?: return@transaction null
        val today = book.today()
        val window = goal.repeat?.window(today) ?: return@transaction null
        if (store.lapses(goal.id, window.start, window.end).any { it.kind == LapseKind.MISS && it.periodStart == window.start && !it.forgotten }) {
            return@transaction null
        }
        while (store.habitTicks(goal.id, window.start).any { it.countsToward(goal, window.start) }) {
            if (!book.undoHabitTick(goal.id)) break
        }
        val over = used(goal, today) >= book.difficulty(today).allowed(goal.allowed)
        record(goal, LapseKind.MISS, today, window.period, window.start, over, null, null).lapse
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
