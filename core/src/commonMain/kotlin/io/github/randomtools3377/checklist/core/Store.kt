package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** Where goals, ticks, the ledger and the shop are kept. The app keeps them in a database. */
interface Store {
    /** Runs [block] so that either all of its changes are kept or none are. */
    suspend fun <T> transaction(block: suspend () -> T): T

    /** Hour of the morning when the user's day ends, 0 for midnight. */
    suspend fun dayEndHour(): Int

    suspend fun goal(id: Long): Goal?
    suspend fun step(id: Long): Step?

    /** The project's steps that have not been removed. */
    suspend fun activeSteps(goalId: Long): List<Step>

    /** The habit's ticks whose period starts on [periodStart]. */
    suspend fun habitTicks(goalId: Long, periodStart: LocalDate): List<Tick>

    /** The tick that marked [stepId] done, if it is done. */
    suspend fun stepTick(stepId: Long): Tick?

    /** Every step tick of the project. */
    suspend fun stepTicks(goalId: Long): List<Tick>

    suspend fun addTick(tick: Tick): Long
    suspend fun removeTick(tickId: Long)

    suspend fun addEntry(entry: Entry): Long
    suspend fun entriesForTicks(tickIds: List<Long>): List<Entry>
    suspend fun removeEntry(entryId: Long)

    /** Sum of the whole ledger. */
    suspend fun balance(): Int

    suspend fun reward(id: Long): Reward?
    suspend fun purchase(id: Long): Purchase?
    suspend fun addPurchase(purchase: Purchase): Long
    suspend fun markUsed(purchaseId: Long, at: Long)
    suspend fun markReturned(purchaseId: Long, at: Long)

    // Skips, misses and penalties

    suspend fun activeGoals(): List<Goal>

    /** Habit ticks whose window starts on or after [day]. */
    suspend fun habitTicksSince(day: LocalDate): List<Tick>

    /** Skips and misses of [goalId] whose day falls from [from] to [to], both included. */
    suspend fun lapses(goalId: Long, from: LocalDate, to: LocalDate): List<Lapse>
    suspend fun lapse(id: Long): Lapse?
    suspend fun addLapse(lapse: Lapse): Long
    suspend fun updateLapse(lapse: Lapse)

    suspend fun entriesForLapse(lapseId: Long): List<Entry>

    suspend fun addPenalty(penalty: Penalty): Long
    suspend fun penalty(id: Long): Penalty?
    suspend fun updatePenalty(penalty: Penalty)
    suspend fun penaltyForLapse(lapseId: Long): Penalty?

    /** Penalties not yet done or cancelled, oldest first. */
    suspend fun openPenalties(): List<Penalty>

    /** The user's own penalty tasks that haven't been removed. */
    suspend fun penaltyTasks(): List<PenaltyTask>

    suspend fun setting(name: String): String?
    suspend fun putSetting(name: String, value: String)

    // Gaps

    /** Every skip and miss whose day falls from [from] to [to]. */
    suspend fun allLapses(from: LocalDate, to: LocalDate): List<Lapse>

    /** Ledger entries whose day falls from [from] to [to]. */
    suspend fun entries(from: LocalDate, to: LocalDate): List<Entry>

    /** Days from [from] to [to] with at least one tick (habit or step). */
    suspend fun tickedDays(from: LocalDate, to: LocalDate): Set<LocalDate>

    suspend fun gaps(): List<Gap>
    suspend fun gap(id: Long): Gap?
    suspend fun addGap(gap: Gap): Long
    suspend fun updateGap(gap: Gap)
}
