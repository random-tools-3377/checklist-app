package io.github.randomtools3377.checklist.core

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed interface TickResult {
    /** The tick was recorded. [finished] is true when it completed the habit's period or the project. */
    data class Ticked(val points: Int, val bonus: Int, val finished: Boolean) : TickResult

    /** The habit already reached its target this period, or the step is already done. */
    data object AlreadyDone : TickResult

    /** The habit isn't due today. */
    data object NotDue : TickResult

    data object NotFound : TickResult
}

sealed interface BuyResult {
    data class Bought(val purchaseId: Long, val price: Int, val balanceAfter: Int) : BuyResult

    /** The price is more than the balance, so the reward can't be bought. */
    data class TooExpensive(val price: Int, val balance: Int) : BuyResult

    data object NotFound : BuyResult
}

/**
 * The points rules: ticking earns, undoing a tick takes its points back, buying spends, and a
 * reward can't be bought for more than the balance. The balance may go negative.
 */
class Book(
    private val store: Store,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    fun millis(): Long = clock.now().toEpochMilliseconds()

    /** The user's current day, after the day-end setting. */
    suspend fun today(): LocalDate = Days.dayOf(clock.now().toLocalDateTime(timeZone()), store.dayEndHour())

    suspend fun balance(): Int = store.balance()

    /** Ticks a habit once for its current period, paying its points and, on finishing, its bonus. */
    suspend fun tickHabit(goalId: Long): TickResult = store.transaction {
        val goal = store.goal(goalId)
        val repeat = goal?.repeat
        if (goal == null || goal.archived || goal.kind != GoalKind.HABIT || repeat == null) {
            return@transaction TickResult.NotFound
        }
        val day = today()
        val window = repeat.window(day) ?: return@transaction TickResult.NotDue
        val start = window.start
        val ticks = store.habitTicks(goalId, start).filter { it.countsToward(goal, start) }
        if (ticks.size >= goal.target) return@transaction TickResult.AlreadyDone

        val at = millis()
        val tickId = store.addTick(
            Tick(goalId = goalId, stepId = null, at = at, day = day, period = window.period, periodStart = start),
        )
        store.addEntry(
            Entry(at = at, day = day, kind = EntryKind.TICK, amount = goal.points, goalId = goalId, tickId = tickId, label = goal.name),
        )

        val finished = ticks.size + 1 >= goal.target
        var bonus = 0
        if (finished && goal.target > 1 && goal.bonus > 0 && bonusesOn(ticks).isEmpty()) {
            bonus = goal.bonus
            store.addEntry(
                Entry(at = at, day = day, kind = EntryKind.BONUS, amount = bonus, goalId = goalId, tickId = tickId, label = goal.name),
            )
        }
        TickResult.Ticked(points = goal.points, bonus = bonus, finished = finished)
    }

    /**
     * Takes back the latest tick of the habit's current period and the points it paid. If that
     * leaves the habit unfinished, its finishing bonus is taken back too.
     */
    suspend fun undoHabitTick(goalId: Long): Boolean = store.transaction {
        val goal = store.goal(goalId)
        val repeat = goal?.repeat
        if (goal == null || goal.kind != GoalKind.HABIT || repeat == null) return@transaction false
        val start = repeat.window(today())?.start ?: return@transaction false
        val ticks = store.habitTicks(goalId, start).filter { it.countsToward(goal, start) }
        val last = ticks.maxByOrNull { it.id } ?: return@transaction false

        removeTickAndItsEntries(last.id)
        val left = ticks - last
        if (left.size < goal.target) bonusesOn(left).forEach { store.removeEntry(it.id) }
        true
    }

    /** Marks a project step done, paying its points and, if it was the last one, the project bonus. */
    suspend fun tickStep(stepId: Long): TickResult = store.transaction {
        val step = store.step(stepId)
        val goal = step?.let { store.goal(it.goalId) }
        if (step == null || step.archived || goal == null || goal.archived || goal.kind != GoalKind.PROJECT) {
            return@transaction TickResult.NotFound
        }
        if (store.stepTick(stepId) != null) return@transaction TickResult.AlreadyDone

        val day = today()
        val at = millis()
        val tickId = store.addTick(
            Tick(goalId = goal.id, stepId = stepId, at = at, day = day, period = null, periodStart = day),
        )
        store.addEntry(
            Entry(
                at = at, day = day, kind = EntryKind.TICK, amount = step.points,
                goalId = goal.id, tickId = tickId, label = "${goal.name}: ${step.name}",
            ),
        )

        val stepTicks = store.stepTicks(goal.id)
        val done = stepTicks.mapNotNull { it.stepId }.toSet()
        val finished = store.activeSteps(goal.id).all { it.id in done }
        var bonus = 0
        if (finished && goal.bonus > 0 && bonusesOn(stepTicks).isEmpty()) {
            bonus = goal.bonus
            store.addEntry(
                Entry(at = at, day = day, kind = EntryKind.BONUS, amount = bonus, goalId = goal.id, tickId = tickId, label = goal.name),
            )
        }
        TickResult.Ticked(points = step.points, bonus = bonus, finished = finished)
    }

    /** Marks a project step not done, taking back its points and the project bonus if it was paid. */
    suspend fun undoStep(stepId: Long): Boolean = store.transaction {
        val step = store.step(stepId) ?: return@transaction false
        val tick = store.stepTick(stepId) ?: return@transaction false
        removeTickAndItsEntries(tick.id)
        // The project is no longer finished, so its bonus no longer stands.
        bonusesOn(store.stepTicks(step.goalId)).forEach { store.removeEntry(it.id) }
        true
    }

    /** Buys a reward if its price is not more than the balance. */
    suspend fun buy(rewardId: Long): BuyResult = store.transaction {
        val reward = store.reward(rewardId)
        if (reward == null || reward.archived) return@transaction BuyResult.NotFound
        val balance = store.balance()
        if (reward.price > balance) return@transaction BuyResult.TooExpensive(reward.price, balance)

        val at = millis()
        val purchaseId = store.addPurchase(
            Purchase(rewardId = reward.id, name = reward.name, icon = reward.icon, price = reward.price, at = at),
        )
        store.addEntry(
            Entry(
                at = at, day = today(), kind = EntryKind.PURCHASE, amount = -reward.price,
                rewardId = reward.id, purchaseId = purchaseId, label = reward.name,
            ),
        )
        BuyResult.Bought(purchaseId = purchaseId, price = reward.price, balanceAfter = balance - reward.price)
    }

    /** Takes a bought reward out of the inventory because it has been used. */
    suspend fun use(purchaseId: Long): Boolean = store.transaction {
        val purchase = store.purchase(purchaseId)
        if (purchase == null || purchase.usedAt != null || purchase.returnedAt != null) return@transaction false
        store.markUsed(purchaseId, millis())
        true
    }

    /** Returns a bought reward that hasn't been used, for a full refund of what it cost. */
    suspend fun returnPurchase(purchaseId: Long): Boolean = store.transaction {
        val purchase = store.purchase(purchaseId)
        if (purchase == null || purchase.usedAt != null || purchase.returnedAt != null) return@transaction false
        val at = millis()
        store.markReturned(purchaseId, at)
        store.addEntry(
            Entry(
                at = at, day = today(), kind = EntryKind.RETURN, amount = purchase.price,
                rewardId = purchase.rewardId, purchaseId = purchaseId, label = purchase.name,
            ),
        )
        true
    }

    private suspend fun bonusesOn(ticks: List<Tick>): List<Entry> =
        if (ticks.isEmpty()) emptyList()
        else store.entriesForTicks(ticks.map { it.id }).filter { it.kind == EntryKind.BONUS }

    private suspend fun removeTickAndItsEntries(tickId: Long) {
        store.entriesForTicks(listOf(tickId)).forEach { store.removeEntry(it.id) }
        store.removeTick(tickId)
    }
}
