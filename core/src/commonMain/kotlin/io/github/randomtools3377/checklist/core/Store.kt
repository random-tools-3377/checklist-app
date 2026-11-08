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
}
