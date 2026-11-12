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
    val checks = mutableListOf<Check>()
    override suspend fun checks(goalId: Long, periodStart: LocalDate): List<Check> = checks.filter { it.goalId == goalId && it.periodStart == periodStart }
    override suspend fun addCheck(check: Check): Long = nextId++.also { checks += check.copy(id = it) }
    override suspend fun removeCheck(id: Long) { checks.removeAll { it.id == id } }
    override suspend fun addTick(tick: Tick): Long = nextId++.also { ticks += tick.copy(id = it) }
    override suspend fun removeTick(tickId: Long) { ticks.removeAll { it.id == tickId } }
    override suspend fun addEntry(entry: Entry): Long = nextId++.also { entries += entry.copy(id = it) }
    override suspend fun entriesForTicks(tickIds: List<Long>): List<Entry> = entries.filter { it.tickId in tickIds }
    override suspend fun tick(id: Long): Tick? = ticks.find { it.id == id }
    override suspend fun updateTick(tick: Tick) { ticks[ticks.indexOfFirst { it.id == tick.id }] = tick }
    override suspend fun updateEntry(entry: Entry) { entries[entries.indexOfFirst { it.id == entry.id }] = entry }
    override suspend fun removeEntry(entryId: Long) { entries.removeAll { it.id == entryId } }
    override suspend fun balance(): Int = entries.sumOf { it.amount }
    override suspend fun balanceOn(day: LocalDate): Int = entries.filter { it.day <= day }.sumOf { it.amount }
    override suspend fun reward(id: Long): Reward? = rewards.find { it.id == id }
    override suspend fun purchase(id: Long): Purchase? = purchases.find { it.id == id }
    override suspend fun purchasesOf(rewardId: Long): List<Purchase> = purchases.filter { it.rewardId == rewardId }
    override suspend fun addPurchase(purchase: Purchase): Long = nextId++.also { purchases += purchase.copy(id = it) }
    override suspend fun markUsed(purchaseId: Long, at: Long) {
        val i = purchases.indexOfFirst { it.id == purchaseId }
        purchases[i] = purchases[i].copy(usedAt = at)
    }

    override suspend fun markReturned(purchaseId: Long, at: Long) {
        val i = purchases.indexOfFirst { it.id == purchaseId }
        purchases[i] = purchases[i].copy(returnedAt = at)
    }

    val lapses = mutableListOf<Lapse>()
    val penalties = mutableListOf<Penalty>()
    val penaltyTaskList = mutableListOf<PenaltyTask>()
    val settings = mutableMapOf<String, String>()

    fun addPenaltyTask(name: String): PenaltyTask = PenaltyTask(id = nextId++, name = name).also { penaltyTaskList += it }

    override suspend fun activeGoals(): List<Goal> = goals.filter { !it.archived }
    override suspend fun setRepeat(goalId: Long, repeat: Repeat?) {
        val i = goals.indexOfFirst { it.id == goalId }
        if (i >= 0) goals[i] = goals[i].copy(repeat = repeat)
    }
    override suspend fun habitTicksSince(day: LocalDate): List<Tick> = ticks.filter { it.stepId == null && it.periodStart >= day }
    override suspend fun lapses(goalId: Long, from: LocalDate, to: LocalDate): List<Lapse> =
        lapses.filter { it.goalId == goalId && it.day >= from && it.day <= to }
    override suspend fun lapse(id: Long): Lapse? = lapses.find { it.id == id }
    override suspend fun addLapse(lapse: Lapse): Long = nextId++.also { lapses += lapse.copy(id = it) }
    override suspend fun updateLapse(lapse: Lapse) {
        lapses[lapses.indexOfFirst { it.id == lapse.id }] = lapse
    }
    override suspend fun removeLapse(id: Long) { lapses.removeAll { it.id == id } }
    override suspend fun entriesForLapse(lapseId: Long): List<Entry> = entries.filter { it.lapseId == lapseId }
    override suspend fun addPenalty(penalty: Penalty): Long = nextId++.also { penalties += penalty.copy(id = it) }
    override suspend fun penalty(id: Long): Penalty? = penalties.find { it.id == id }
    override suspend fun updatePenalty(penalty: Penalty) {
        penalties[penalties.indexOfFirst { it.id == penalty.id }] = penalty
    }
    override suspend fun penaltyForLapse(lapseId: Long): Penalty? = penalties.find { it.lapseId == lapseId }
    override suspend fun openPenalties(): List<Penalty> = penalties.filter { it.doneAt == null && it.cancelledAt == null }
    override suspend fun penaltyTasks(): List<PenaltyTask> = penaltyTaskList.filter { !it.archived }
    override suspend fun setting(name: String): String? = settings[name]
    override suspend fun putSetting(name: String, value: String) {
        settings[name] = value
    }

    val gapList = mutableListOf<Gap>()

    override suspend fun allLapses(from: LocalDate, to: LocalDate): List<Lapse> = lapses.filter { it.day >= from && it.day <= to }
    override suspend fun entries(from: LocalDate, to: LocalDate): List<Entry> = entries.filter { it.day >= from && it.day <= to }
    override suspend fun tickedDays(from: LocalDate, to: LocalDate): Set<LocalDate> =
        ticks.map { it.day }.filter { it >= from && it <= to }.toSet()
    override suspend fun gaps(): List<Gap> = gapList.toList()
    override suspend fun gap(id: Long): Gap? = gapList.find { it.id == id }
    override suspend fun addGap(gap: Gap): Long = nextId++.also { gapList += gap.copy(id = it) }
    override suspend fun updateGap(gap: Gap) {
        gapList[gapList.indexOfFirst { it.id == gap.id }] = gap
    }
    override suspend fun removeGap(id: Long) {
        gapList.removeAll { it.id == id }
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
