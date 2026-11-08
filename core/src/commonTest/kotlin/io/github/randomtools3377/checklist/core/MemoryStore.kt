package io.github.randomtools3377.checklist.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** A [Store] kept in memory, for tests. */
class MemoryStore : Store {
    var dayEnd = 0
    val goals = mutableListOf<Goal>()
    val steps = mutableListOf<Step>()
    val ticks = mutableListOf<Tick>()
    val entries = mutableListOf<Entry>()
    val rewards = mutableListOf<Reward>()
    val purchases = mutableListOf<Purchase>()
    private var nextId = 1L

    fun addGoal(goal: Goal): Goal = goal.copy(id = nextId++).also { goals += it }
    fun addStep(step: Step): Step = step.copy(id = nextId++).also { steps += it }
    fun addReward(reward: Reward): Reward = reward.copy(id = nextId++).also { rewards += it }

    override suspend fun <T> transaction(block: suspend () -> T): T = block()
    override suspend fun dayEndHour(): Int = dayEnd
    override suspend fun goal(id: Long): Goal? = goals.find { it.id == id }
    override suspend fun step(id: Long): Step? = steps.find { it.id == id }
    override suspend fun activeSteps(goalId: Long): List<Step> = steps.filter { it.goalId == goalId && !it.archived }
    override suspend fun habitTicks(goalId: Long, periodStart: LocalDate): List<Tick> =
        ticks.filter { it.goalId == goalId && it.stepId == null && it.periodStart == periodStart }
    override suspend fun stepTick(stepId: Long): Tick? = ticks.find { it.stepId == stepId }
    override suspend fun stepTicks(goalId: Long): List<Tick> = ticks.filter { it.goalId == goalId && it.stepId != null }
    override suspend fun addTick(tick: Tick): Long = nextId++.also { ticks += tick.copy(id = it) }
    override suspend fun removeTick(tickId: Long) { ticks.removeAll { it.id == tickId } }
    override suspend fun addEntry(entry: Entry): Long = nextId++.also { entries += entry.copy(id = it) }
    override suspend fun entriesForTicks(tickIds: List<Long>): List<Entry> = entries.filter { it.tickId in tickIds }
    override suspend fun removeEntry(entryId: Long) { entries.removeAll { it.id == entryId } }
    override suspend fun balance(): Int = entries.sumOf { it.amount }
    override suspend fun reward(id: Long): Reward? = rewards.find { it.id == id }
    override suspend fun purchase(id: Long): Purchase? = purchases.find { it.id == id }
    override suspend fun addPurchase(purchase: Purchase): Long = nextId++.also { purchases += purchase.copy(id = it) }
    override suspend fun markUsed(purchaseId: Long, at: Long) {
        val i = purchases.indexOfFirst { it.id == purchaseId }
        purchases[i] = purchases[i].copy(usedAt = at)
    }
}

/** A clock the test can move. It runs in UTC. */
class TestClock(start: LocalDateTime) : Clock {
    private var now: Instant = start.toInstant(TimeZone.UTC)

    fun set(moment: LocalDateTime) {
        now = moment.toInstant(TimeZone.UTC)
    }

    override fun now(): Instant = now
}
