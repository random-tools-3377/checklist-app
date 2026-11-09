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
    /** For a mystery reward, [prize] is what came out of it. */
    data class Bought(val purchaseId: Long, val price: Int, val balanceAfter: Int, val prize: String? = null) : BuyResult

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

    /** The difficulty on [day], which sets what goals pay (see [Difficulty]). */
    suspend fun difficulty(day: LocalDate): Difficulty = DifficultySetting.of(store).on(day)

    /** Today's difficulty. */
    suspend fun difficulty(): Difficulty = difficulty(today())

    /** Whether [goal] is the boosted goal (see [Boost]) in the week of [day]. */
    suspend fun boosted(goal: Goal, day: LocalDate): Boolean {
        if (!Feature.isOn(store, Feature.BOOST)) return false
        val monday = Boost.weekOf(day)
        val lapses = store.allLapses(monday.minusDays(Boost.DAYS), monday.minusDays(1))
        return Boost.goalFor(day, store.activeGoals(), lapses)?.id == goal.id
    }

    /** What [goal]'s points pay on [day]: its difficulty, the boost and the streak bonus (see [payFor]). */
    suspend fun payer(goal: Goal, day: LocalDate): (Int) -> Int {
        val rate = difficulty(day)
        val boost = boosted(goal, day)
        val streak = streakPercent(day)
        return { points -> payFor(points, rate, boost, streak) }
    }

    /** The streak bonus on [day], from the main streak to the end of the day before (see [StreakBonus]). */
    suspend fun streakPercent(day: LocalDate): Int {
        if (!Feature.isOn(store, Feature.STREAK_BONUS)) return 100
        val settings = StreakBonus.settingsOf(store.setting(StreakBonus.TARGET), store.setting(StreakBonus.VALUE))
        val yesterday = day.minusDays(1)
        val from = day.minusDays(120)
        val records = StreakCounter.records(from, yesterday, store.activeGoals(), store.habitTicksSince(Days.earliestOpenDay(from)), store.allLapses(from, yesterday))
        return StreakBonus.percentFor(StreakCounter.count(records, yesterday, settings).done)
    }

    companion object {
        /** A tick's note is kept short, like "ran 5 km". */
        const val NOTE_LENGTH = 200
    }

    /** The user's current day, after the day-end setting. */
    suspend fun today(): LocalDate = Days.dayOf(clock.now().toLocalDateTime(timeZone()), store.dayEndHour())

    suspend fun balance(): Int = store.balance()

    /**
     * Changes a tick: its time on the same day (when it was really done), and its note. The
     * points stay as they were; the ledger's entries for it move to the new time too.
     */
    suspend fun editTick(tickId: Long, at: Long?, note: String?): Boolean = store.transaction {
        val tick = store.tick(tickId) ?: return@transaction false
        val newAt = at ?: tick.at
        store.updateTick(tick.copy(at = newAt, note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(NOTE_LENGTH)))
        if (newAt != tick.at) store.entriesForTicks(listOf(tickId)).forEach { store.updateEntry(it.copy(at = newAt)) }
        true
    }

    /** Ticks a habit once for its current period, paying its points and, on finishing, its bonus. */
    suspend fun tickHabit(goalId: Long): TickResult = store.transaction {
        val goal = store.goal(goalId)
        if (goal != null && !goal.archived && goal.kind == GoalKind.TODO && goal.repeat == null) return@transaction tickUndatedTodo(goal)
        val repeat = goal?.repeat
        if (goal == null || goal.archived || !goal.isHabitLike || repeat == null) {
            return@transaction TickResult.NotFound
        }
        val day = today()
        val window = repeat.window(day) ?: return@transaction TickResult.NotDue
        val start = window.start
        val ticks = store.habitTicks(goalId, start).filter { it.countsToward(goal, start) }
        // A goal with an amount: "Done" enters what is left of the time being worked on.
        if (goal.hasAmount) {
            val progress = goal.progressOf(ticks)
            if (Amounts.times(progress, goal.amount!!) >= goal.target) return@transaction TickResult.AlreadyDone
            return@transaction logAmount(goalId, Amounts.leftOfThisTime(progress, goal.amount))
        }
        if (ticks.size >= goal.target) return@transaction TickResult.AlreadyDone

        val pay = payer(goal, day)
        val at = millis()
        val tickId = store.addTick(
            Tick(goalId = goalId, stepId = null, at = at, day = day, period = window.period, periodStart = start),
        )
        store.addEntry(
            Entry(at = at, day = day, kind = EntryKind.TICK, amount = pay(goal.points), goalId = goalId, tickId = tickId, label = goal.name),
        )

        val finished = ticks.size + 1 >= goal.target
        var bonus = 0
        if (finished && goal.target > 1 && goal.bonus > 0 && bonusesOn(ticks).isEmpty()) {
            bonus = pay(goal.bonus)
            store.addEntry(
                Entry(at = at, day = day, kind = EntryKind.BONUS, amount = bonus, goalId = goalId, tickId = tickId, label = goal.name),
            )
        }
        if (finished) nextAfterDone(goal, day)
        if (finished) bonus += payChallenge(goal, day, tickId, pay)
        TickResult.Ticked(points = pay(goal.points), bonus = bonus, finished = finished)
    }

    /**
     * An undated to-do done: ticked for today, paying its points. When the day closes it takes
     * today as its date (see [Lapses.closeDays]), so from then on it is an ordinary dated one, done.
     */
    private suspend fun tickUndatedTodo(goal: Goal): TickResult {
        val day = today()
        if (store.habitTicks(goal.id, day).any { it.stepId == null }) return TickResult.AlreadyDone
        val at = millis()
        val tickId = store.addTick(Tick(goalId = goal.id, stepId = null, at = at, day = day, period = Period.DAILY, periodStart = day))
        val points = payer(goal, day)(goal.points)
        store.addEntry(Entry(at = at, day = day, kind = EntryKind.TICK, amount = points, goalId = goal.id, tickId = tickId, label = goal.name))
        return TickResult.Ticked(points = points, bonus = 0, finished = true)
    }

    /**
     * Enters how much of a goal's amount was done, like 8 pages, and how long it took if it was
     * timed. Each whole amount reached pays the goal's points, and reaching all of a goal's times
     * pays its bonus; entries in between pay nothing. Once every time is done, more can't be
     * entered until it is due again.
     */
    suspend fun logAmount(goalId: Long, amount: Double, seconds: Int? = null): TickResult = store.transaction {
        val goal = store.goal(goalId)
        val repeat = goal?.repeat
        if (goal == null || goal.archived || !goal.isHabitLike || repeat == null || !goal.hasAmount) {
            return@transaction TickResult.NotFound
        }
        val day = today()
        val window = repeat.window(day) ?: return@transaction TickResult.NotDue
        val ticks = store.habitTicks(goalId, window.start).filter { it.countsToward(goal, window.start) }
        val before = goal.progressOf(ticks)
        val timesBefore = Amounts.times(before, goal.amount!!)
        if (timesBefore >= goal.target) return@transaction TickResult.AlreadyDone
        val entered = Amounts.tidy(amount) ?: return@transaction TickResult.NotFound
        val at = millis()
        val tickId = store.addTick(
            Tick(
                goalId = goalId, stepId = null, at = at, day = day, period = window.period, periodStart = window.start,
                amount = entered, seconds = seconds?.takeIf { it > 0 },
            ),
        )
        val timesAfter = Amounts.times(before + entered, goal.amount).coerceAtMost(goal.target)
        val newTimes = timesAfter - timesBefore
        val pay = payer(goal, day)
        val points = pay(goal.points) * newTimes
        if (points != 0) {
            store.addEntry(
                Entry(at = at, day = day, kind = EntryKind.TICK, amount = points, goalId = goalId, tickId = tickId, label = goal.name),
            )
        }
        val finished = timesAfter >= goal.target
        var bonus = 0
        if (finished && goal.target > 1 && goal.bonus > 0) {
            bonus = pay(goal.bonus)
            store.addEntry(
                Entry(at = at, day = day, kind = EntryKind.BONUS, amount = bonus, goalId = goalId, tickId = tickId, label = goal.name),
            )
        }
        if (finished) nextAfterDone(goal, day)
        if (finished) bonus += payChallenge(goal, day, tickId, pay)
        TickResult.Ticked(points = points, bonus = bonus, finished = finished)
    }

    /**
     * Takes back the latest tick of the habit's current period and the points it paid. If that
     * leaves the habit unfinished, its finishing bonus is taken back too.
     */
    suspend fun undoHabitTick(goalId: Long): Boolean = store.transaction {
        val goal = store.goal(goalId)
        if (goal != null && goal.kind == GoalKind.TODO && goal.repeat == null) {
            // An undated to-do done today: take that back.
            val last = store.habitTicks(goalId, today()).filter { it.stepId == null }.maxByOrNull { it.id } ?: return@transaction false
            removeTickAndItsEntries(last.id)
            return@transaction true
        }
        val repeat = goal?.repeat
        if (goal == null || !goal.isHabitLike || repeat == null) return@transaction false
        if (repeat.kind == RepeatKind.AFTER_DONE && repeat.window(today()) == null) return@transaction undoAfterDone(goal, repeat)
        val start = repeat.window(today())?.start ?: return@transaction false
        val ticks = store.habitTicks(goalId, start).filter { it.countsToward(goal, start) }
        val last = ticks.maxByOrNull { it.id } ?: return@transaction false

        removeTickAndItsEntries(last.id)
        val left = ticks - last
        if (left.size < goal.target) bonusesOn(left).forEach { store.removeEntry(it.id) }
        true
    }

    /**
     * Ticks one item of a routine for its current window. When that was the last one, the
     * routine itself is ticked: it pays its points and counts as done. Items pay nothing alone.
     */
    suspend fun checkItem(stepId: Long): TickResult = store.transaction {
        val step = store.step(stepId)
        val goal = step?.let { store.goal(it.goalId) }
        if (goal?.kind == GoalKind.TODO) return@transaction tickStep(stepId)
        if (step == null || step.archived || goal == null || goal.archived || goal.kind != GoalKind.HABIT) {
            return@transaction TickResult.NotFound
        }
        val window = goal.repeat?.window(today()) ?: return@transaction TickResult.NotDue
        val checks = store.checks(goal.id, window.start)
        if (checks.any { it.stepId == stepId }) return@transaction TickResult.AlreadyDone
        store.addCheck(Check(goalId = goal.id, stepId = stepId, periodStart = window.start, at = millis()))
        val ticked = checks.map { it.stepId }.toSet() + stepId
        if (store.activeSteps(goal.id).all { it.id in ticked }) {
            val result = tickHabit(goal.id)
            if (result is TickResult.Ticked) return@transaction result
        }
        TickResult.Ticked(points = 0, bonus = 0, finished = false)
    }

    /** Takes back one item of a routine. If the routine was done, it isn't any more, and its points go back. */
    suspend fun uncheckItem(stepId: Long): Boolean = store.transaction {
        val step = store.step(stepId) ?: return@transaction false
        val goal = store.goal(step.goalId) ?: return@transaction false
        if (goal.kind == GoalKind.TODO) return@transaction undoStep(stepId)
        val window = goal.repeat?.window(today()) ?: return@transaction false
        val check = store.checks(goal.id, window.start).find { it.stepId == stepId } ?: return@transaction false
        store.removeCheck(check.id)
        val ticks = store.habitTicks(goal.id, window.start).filter { it.countsToward(goal, window.start) }
        if (ticks.size >= goal.target) undoHabitTick(goal.id)
        true
    }

    /** "Done" on a routine: ticks every item still open, which ticks the routine. */
    suspend fun finishRoutine(goalId: Long): TickResult = store.transaction {
        val goal = store.goal(goalId) ?: return@transaction TickResult.NotFound
        if (goal.kind == GoalKind.TODO) return@transaction finishTodo(goalId)
        val window = goal.repeat?.window(today()) ?: return@transaction TickResult.NotDue
        val ticked = store.checks(goalId, window.start).map { it.stepId }.toSet()
        val open = store.activeSteps(goalId).filter { it.id !in ticked }
        if (open.isEmpty()) return@transaction tickHabit(goalId)
        var result: TickResult = TickResult.AlreadyDone
        for (item in open) result = checkItem(item.id)
        result
    }

    /** Takes back a finished routine: the routine's tick and every item ticked in this window. */
    suspend fun undoRoutine(goalId: Long): Boolean = store.transaction {
        val goal = store.goal(goalId) ?: return@transaction false
        if (goal.kind == GoalKind.TODO) return@transaction undoTodo(goalId)
        val window = goal.repeat?.window(today()) ?: return@transaction false
        store.checks(goalId, window.start).forEach { store.removeCheck(it.id) }
        undoHabitTick(goalId)
    }

    /** Marks a project step done, paying its points and, if it was the last one, the project bonus. */
    suspend fun tickStep(stepId: Long): TickResult = store.transaction {
        val step = store.step(stepId)
        val goal = step?.let { store.goal(it.goalId) }
        if (step == null || step.archived || goal == null || goal.archived || goal.kind == GoalKind.HABIT) {
            return@transaction TickResult.NotFound
        }
        if (store.stepTick(stepId) != null) return@transaction TickResult.AlreadyDone
        if (goal.kind == GoalKind.TODO) return@transaction tickTodoStep(goal, step)

        val day = today()
        val pay = payer(goal, day)
        val at = millis()
        val tickId = store.addTick(
            Tick(goalId = goal.id, stepId = stepId, at = at, day = day, period = null, periodStart = day),
        )
        store.addEntry(
            Entry(
                at = at, day = day, kind = EntryKind.TICK, amount = pay(step.points),
                goalId = goal.id, tickId = tickId, label = "${goal.name}: ${step.name}",
            ),
        )

        val stepTicks = store.stepTicks(goal.id)
        val done = stepTicks.mapNotNull { it.stepId }.toSet()
        val finished = store.activeSteps(goal.id).all { it.id in done }
        var bonus = 0
        if (finished && goal.bonus > 0 && bonusesOn(stepTicks).isEmpty()) {
            bonus = pay(goal.bonus)
            store.addEntry(
                Entry(at = at, day = day, kind = EntryKind.BONUS, amount = bonus, goalId = goal.id, tickId = tickId, label = goal.name),
            )
        }
        TickResult.Ticked(points = pay(step.points), bonus = bonus, finished = finished)
    }

    /**
     * A step of a to-do ticked. Steps pay nothing alone; ticking the last one does the to-do,
     * which pays its points. They stay ticked from day to day until it is done.
     */
    private suspend fun tickTodoStep(goal: Goal, step: Step): TickResult {
        val day = today()
        store.addTick(Tick(goalId = goal.id, stepId = step.id, at = millis(), day = day, period = null, periodStart = day))
        val ticked = store.stepTicks(goal.id).mapNotNull { it.stepId }.toSet()
        if (store.activeSteps(goal.id).all { it.id in ticked }) {
            val result = tickHabit(goal.id)
            if (result is TickResult.Ticked) return result
        }
        return TickResult.Ticked(points = 0, bonus = 0, finished = false)
    }

    /**
     * The tick that finishes a challenge pays its bonus, once per attempt; undoing the tick takes
     * it back. Returns what was paid.
     */
    private suspend fun payChallenge(goal: Goal, day: LocalDate, tickId: Long, pay: (Int) -> Int): Int {
        val challenge = goal.challenge ?: return 0
        val ticks = store.habitTicksSince(challenge.from.minusDays(7)).filter { it.goalId == goal.id }
        val lapses = store.allLapses(challenge.from, day).filter { it.goalId == goal.id }
        val bonuses = store.entries(challenge.from, day).filter { it.goalId == goal.id && it.kind == EntryKind.CHALLENGE }
        val progress = Challenges.progress(goal, ticks, lapses, bonuses, day) ?: return 0
        if (progress.done < progress.days || bonuses.any { it.day >= progress.startedOn }) return 0
        val amount = pay(challenge.bonus)
        if (amount <= 0) return 0
        store.addEntry(
            Entry(at = millis(), day = day, kind = EntryKind.CHALLENGE, amount = amount, goalId = goal.id, tickId = tickId, label = "${goal.name}: ${challenge.days} days"),
        )
        return amount
    }

    /** A "since last done" goal just done on [day]: it is next due [Repeat.interval] days on. */
    private suspend fun nextAfterDone(goal: Goal, day: LocalDate) {
        val repeat = goal.repeat ?: return
        if (repeat.kind != RepeatKind.AFTER_DONE) return
        store.setRepeat(goal.id, repeat.copy(start = day.plusDays(repeat.interval.coerceAtLeast(1)), lateSince = null))
    }

    /**
     * Takes back today's tick of a "since last done" goal that was done: it is due today again,
     * and still late if it was late before (so it isn't missed twice).
     */
    private suspend fun undoAfterDone(goal: Goal, repeat: Repeat): Boolean {
        val day = today()
        val last = store.habitTicks(goal.id, day).filter { it.stepId == null }.maxByOrNull { it.id } ?: return false
        removeTickAndItsEntries(last.id)
        store.setRepeat(goal.id, repeat.copy(start = day, lateSince = lateSince(goal, day)))
        return true
    }

    /** The first day of the current late stretch of a "since last done" goal before [day], if any. */
    suspend fun lateSince(goal: Goal, day: LocalDate): LocalDate? {
        val from = day.minusDays(400)
        val lastDone = store.habitTicksSince(from).filter { it.goalId == goal.id && it.stepId == null && it.day < day }.maxOfOrNull { it.day }
        return store.lapses(goal.id, from, day.minusDays(1))
            .filter { !it.forgotten && (lastDone == null || it.day > lastDone) }
            .minOfOrNull { it.day }
    }

    /** Done on a to-do with steps: ticks the steps still open, then the to-do. */
    private suspend fun finishTodo(goalId: Long): TickResult {
        val goal = store.goal(goalId) ?: return TickResult.NotFound
        val day = today()
        val ticked = store.stepTicks(goalId).mapNotNull { it.stepId }.toSet()
        store.activeSteps(goalId).filter { it.id !in ticked }.forEach { step ->
            store.addTick(Tick(goalId = goal.id, stepId = step.id, at = millis(), day = day, period = null, periodStart = day))
        }
        return tickHabit(goalId)
    }

    /** Takes back a done to-do with steps: its tick, and every step. */
    private suspend fun undoTodo(goalId: Long): Boolean {
        store.stepTicks(goalId).forEach { removeTickAndItsEntries(it.id) }
        return undoHabitTick(goalId)
    }

    /** Marks a project step not done, taking back its points and the project bonus if it was paid. */
    suspend fun undoStep(stepId: Long): Boolean = store.transaction {
        val step = store.step(stepId) ?: return@transaction false
        val tick = store.stepTick(stepId) ?: return@transaction false
        removeTickAndItsEntries(tick.id)
        // A to-do with a step taken back isn't done any more.
        if (store.goal(step.goalId)?.kind == GoalKind.TODO) {
            undoHabitTick(step.goalId)
            return@transaction true
        }
        // The project is no longer finished, so its bonus no longer stands.
        bonusesOn(store.stepTicks(step.goalId)).forEach { store.removeEntry(it.id) }
        true
    }

    /** Buys a reward if its price is not more than the balance. */
    /**
     * Buys a reward, which then waits in the inventory. A mystery reward draws one of its prizes
     * with [random]; the prize is what goes in the inventory, at the mystery reward's price.
     */
    suspend fun buy(rewardId: Long, random: kotlin.random.Random = kotlin.random.Random.Default): BuyResult = store.transaction {
        val found = store.reward(rewardId)
        if (found == null || found.archived) return@transaction BuyResult.NotFound
        // Bought already this week: it costs more each time (see [RepeatPrice]).
        val reward = found.copy(price = priceNow(found))
        val balance = store.balance()
        if (reward.price > balance) return@transaction BuyResult.TooExpensive(reward.price, balance)

        val at = millis()
        val prize = if (reward.isMystery) Mystery.draw(reward.prizes, random) else null
        val purchaseId = store.addPurchase(
            Purchase(rewardId = reward.id, name = prize ?: reward.name, icon = reward.icon, price = reward.price, at = at),
        )
        store.addEntry(
            Entry(
                at = at, day = today(), kind = EntryKind.PURCHASE, amount = -reward.price,
                rewardId = reward.id, purchaseId = purchaseId, label = if (prize != null) "${reward.name}: $prize" else reward.name,
            ),
        )
        BuyResult.Bought(purchaseId = purchaseId, price = reward.price, balanceAfter = balance - reward.price, prize = prize)
    }

    /** What [reward] costs now: its price, raised for each time it was bought this week and kept. */
    suspend fun priceNow(reward: Reward): Int {
        val monday = Boost.weekOf(today())
        val zone = timeZone()
        val dayEnd = store.dayEndHour()
        val thisWeek = store.purchasesOf(reward.id).count { purchase ->
            purchase.returnedAt == null &&
                Days.dayOf(kotlinx.datetime.Instant.fromEpochMilliseconds(purchase.at).toLocalDateTime(zone), dayEnd) >= monday
        }
        return RepeatPrice.now(reward.price, thisWeek, on = Feature.isOn(store, Feature.REPEAT_PRICE))
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
