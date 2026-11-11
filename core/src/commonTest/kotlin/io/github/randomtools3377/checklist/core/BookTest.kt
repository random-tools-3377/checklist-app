package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class BookTest {
    private val store = MemoryStore()

    // Wednesday 11 November 2026, 10:00.
    private val clock = TestClock(LocalDateTime(2026, 11, 11, 10, 0))
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }

    private val start = LocalDate(2026, 1, 1)

    private fun habit(points: Int, period: Period = Period.DAILY, target: Int = 1, bonus: Int = 0, repeat: Repeat? = null) =
        store.addGoal(
            Goal(
                kind = GoalKind.HABIT, name = "Habit", icon = "⭐", points = points,
                repeat = repeat ?: if (period == Period.DAILY) Repeat.everyDay(start) else Repeat.timesPer(period, start),
                target = target, bonus = bonus,
            ),
        )

    private fun project(bonus: Int, vararg stepPoints: Int): Pair<Goal, List<Step>> {
        val goal = store.addGoal(Goal(kind = GoalKind.PROJECT, name = "Project", icon = "⭐", points = 0, repeat = null, bonus = bonus))
        val steps = stepPoints.mapIndexed { i, p -> store.addStep(Step(goalId = goal.id, name = "Step $i", points = p, position = i)) }
        return goal to steps
    }

    private fun reward(price: Int) = store.addReward(Reward(name = "Reward", icon = "🎁", price = price))

    @Test
    fun aLuckyExtraGoesWithItsOwnTickOnly() = runTest {
        // A counted goal: 2 a day, 3 for finishing. The first tick was lucky (+12, linked to it).
        val water = habit(points = 5, target = 2, bonus = 3)
        book.tickHabit(water.id)
        val first = store.ticks.single()
        store.addEntry(Entry(at = 0, day = first.day, kind = EntryKind.BONUS, amount = 12, tickId = first.id, label = "Lucky chest"))
        val second = book.tickHabit(water.id) as TickResult.Ticked
        assertEquals(3, second.bonus, "the lucky extra isn't taken for a finishing bonus")
        assertEquals(5 + 12 + 5 + 3, book.balance())
        // Taking the second back takes its finishing bonus, but not the first tick's luck.
        book.undoHabitTick(water.id)
        assertEquals(5 + 12, book.balance())
        // Taking the first back takes its luck too, so ticking again can't pay it twice.
        book.undoHabitTick(water.id)
        assertEquals(0, book.balance())
    }

    @Test
    fun tickingEarnsThePointsTheGoalPays() = runTest {
        val goal = habit(points = 10)

        val result = book.tickHabit(goal.id)

        assertEquals(TickResult.Ticked(points = 10, bonus = 0, finished = true), result)
        assertEquals(10, book.balance())
    }

    @Test
    fun aDoneHabitCannotBeTickedAgainInTheSamePeriod() = runTest {
        val goal = habit(points = 10)
        book.tickHabit(goal.id)

        assertEquals(TickResult.AlreadyDone, book.tickHabit(goal.id))
        assertEquals(10, book.balance())
    }

    @Test
    fun aDailyHabitCanBeTickedAgainTheNextDay() = runTest {
        val goal = habit(points = 10)
        book.tickHabit(goal.id)
        clock.set(LocalDateTime(2026, 11, 12, 9, 0))

        assertTrue(book.tickHabit(goal.id) is TickResult.Ticked)
        assertEquals(20, book.balance())
    }

    @Test
    fun undoingATickTakesItsPointsBack() = runTest {
        val goal = habit(points = 10)
        book.tickHabit(goal.id)

        assertTrue(book.undoHabitTick(goal.id))

        assertEquals(0, book.balance())
        assertTrue(store.ticks.isEmpty())
        assertTrue(book.tickHabit(goal.id) is TickResult.Ticked)
    }

    @Test
    fun undoingWithNothingTickedChangesNothing() = runTest {
        val goal = habit(points = 10)

        assertFalse(book.undoHabitTick(goal.id))
        assertEquals(0, book.balance())
    }

    @Test
    fun aCountedHabitPaysEachTickPlusABonusForFinishing() = runTest {
        val goal = habit(points = 2, target = 3, bonus = 5)

        assertEquals(TickResult.Ticked(2, 0, false), book.tickHabit(goal.id))
        assertEquals(TickResult.Ticked(2, 0, false), book.tickHabit(goal.id))
        assertEquals(TickResult.Ticked(2, 5, true), book.tickHabit(goal.id))
        assertEquals(TickResult.AlreadyDone, book.tickHabit(goal.id))

        assertEquals(2 + 2 + 2 + 5, book.balance())
    }

    @Test
    fun undoingTheFinishingTickTakesBackTheBonusToo() = runTest {
        val goal = habit(points = 2, target = 2, bonus = 5)
        book.tickHabit(goal.id)
        book.tickHabit(goal.id)

        book.undoHabitTick(goal.id)

        assertEquals(2, book.balance())
        // Finishing again pays the bonus again, once.
        assertEquals(TickResult.Ticked(2, 5, true), book.tickHabit(goal.id))
        assertEquals(9, book.balance())
    }

    @Test
    fun aWeeklyHabitCountsTicksFromMondayToSunday() = runTest {
        val goal = habit(points = 3, period = Period.WEEKLY, target = 2)
        book.tickHabit(goal.id) // Wednesday
        clock.set(LocalDateTime(2026, 11, 15, 20, 0)) // Sunday, same week
        assertEquals(TickResult.Ticked(3, 0, true), book.tickHabit(goal.id))
        assertEquals(TickResult.AlreadyDone, book.tickHabit(goal.id))

        clock.set(LocalDateTime(2026, 11, 16, 8, 0)) // Monday, a new week
        assertEquals(TickResult.Ticked(3, 0, false), book.tickHabit(goal.id))
    }

    @Test
    fun aTickBeforeTheDayEndsCountsForTheDayBefore() = runTest {
        store.dayEnd = 4
        val goal = habit(points = 10)
        clock.set(LocalDateTime(2026, 11, 11, 23, 30))
        book.tickHabit(goal.id)

        clock.set(LocalDateTime(2026, 11, 12, 1, 30)) // still Wednesday for the user
        assertEquals(TickResult.AlreadyDone, book.tickHabit(goal.id))

        clock.set(LocalDateTime(2026, 11, 12, 4, 0)) // Thursday has started
        assertTrue(book.tickHabit(goal.id) is TickResult.Ticked)
    }

    @Test
    fun editingAGoalDoesNotChangePointsAlreadyEarned() = runTest {
        val goal = habit(points = 10)
        book.tickHabit(goal.id)

        store.goals[0] = goal.copy(points = 50)

        assertEquals(10, book.balance())
        clock.set(LocalDateTime(2026, 11, 12, 9, 0))
        book.tickHabit(goal.id)
        assertEquals(60, book.balance())
    }

    @Test
    fun eachProjectStepPaysAndTheLastOnePaysTheBonus() = runTest {
        val (_, steps) = project(20, 5, 7)

        assertEquals(TickResult.Ticked(5, 0, false), book.tickStep(steps[0].id))
        assertEquals(TickResult.Ticked(7, 20, true), book.tickStep(steps[1].id))
        assertEquals(TickResult.AlreadyDone, book.tickStep(steps[1].id))

        assertEquals(32, book.balance())
    }

    @Test
    fun undoingAStepOfAFinishedProjectTakesBackTheStepAndTheBonus() = runTest {
        val (_, steps) = project(20, 5, 7)
        book.tickStep(steps[0].id)
        book.tickStep(steps[1].id) // finishes, pays the bonus

        assertTrue(book.undoStep(steps[0].id))

        assertEquals(7, book.balance())
        assertEquals(TickResult.Ticked(5, 20, true), book.tickStep(steps[0].id))
        assertEquals(32, book.balance())
    }

    @Test
    fun buyingSpendsThePrice() = runTest {
        val goal = habit(points = 30)
        book.tickHabit(goal.id)
        val coffee = reward(price = 20)

        val result = book.buy(coffee.id)

        assertTrue(result is BuyResult.Bought)
        assertEquals(10, (result as BuyResult.Bought).balanceAfter)
        assertEquals(10, book.balance())
        assertEquals(1, store.purchases.size)
        assertEquals(null, store.purchases[0].usedAt)
    }

    @Test
    fun aRewardCanBeBoughtWithExactlyTheBalance() = runTest {
        book.tickHabit(habit(points = 20).id)

        assertTrue(book.buy(reward(price = 20).id) is BuyResult.Bought)
        assertEquals(0, book.balance())
    }

    @Test
    fun aPriceAboveTheBalanceCannotBeBought() = runTest {
        book.tickHabit(habit(points = 10).id)
        val treat = reward(price = 11)

        assertEquals(BuyResult.TooExpensive(price = 11, balance = 10), book.buy(treat.id))
        assertEquals(10, book.balance())
        assertTrue(store.purchases.isEmpty())
    }

    @Test
    fun aRewardCanBeBoughtAgainAndAgain() = runTest {
        // No limit on buying; with the repeat price off, each costs the same (see BoostsTest for the rise).
        store.putSetting(RepeatPrice.SETTING, "0")
        book.tickHabit(habit(points = 30).id)
        val treat = reward(price = 10)

        repeat(3) { assertTrue(book.buy(treat.id) is BuyResult.Bought) }
        assertTrue(book.buy(treat.id) is BuyResult.TooExpensive)
        assertEquals(0, book.balance())
    }

    @Test
    fun undoingATickAfterSpendingCanMakeTheBalanceNegativeAndClosesTheShop() = runTest {
        val goal = habit(points = 10)
        book.tickHabit(goal.id)
        val treat = reward(price = 10)
        book.buy(treat.id)

        book.undoHabitTick(goal.id)

        assertEquals(-10, book.balance())
        assertTrue(book.buy(reward(price = 0).id) is BuyResult.TooExpensive)
    }

    @Test
    fun usingABoughtRewardTakesItOutOfTheInventoryOnce() = runTest {
        book.tickHabit(habit(points = 10).id)
        val bought = book.buy(reward(price = 5).id) as BuyResult.Bought

        assertTrue(book.use(bought.purchaseId))
        assertFalse(book.use(bought.purchaseId))
        assertEquals(5, book.balance())
    }

    @Test
    fun removedGoalsAndRewardsCannotBeUsed() = runTest {
        val goal = habit(points = 10)
        store.goals[0] = goal.copy(archived = true)
        val treat = reward(price = 0)
        store.rewards[0] = treat.copy(archived = true)

        assertEquals(TickResult.NotFound, book.tickHabit(goal.id))
        assertEquals(BuyResult.NotFound, book.buy(treat.id))
    }

    @Test
    fun aHabitCannotBeTickedOnADayItIsNotDue() = runTest {
        // Due on Mondays and Fridays; the clock says Wednesday.
        val goal = habit(points = 5, repeat = Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1, 5)))

        assertEquals(TickResult.NotDue, book.tickHabit(goal.id))
        clock.set(LocalDateTime(2026, 11, 13, 9, 0)) // Friday
        assertEquals(TickResult.Ticked(5, 0, true), book.tickHabit(goal.id))
    }

    @Test
    fun aYearlyCountedHabitCountsAcrossTheYear() = runTest {
        val goal = habit(points = 1, period = Period.YEARLY, target = 2)
        book.tickHabit(goal.id)
        clock.set(LocalDateTime(2026, 12, 30, 9, 0))
        assertEquals(TickResult.Ticked(1, 0, true), book.tickHabit(goal.id))
        clock.set(LocalDateTime(2027, 1, 2, 9, 0))
        assertEquals(TickResult.Ticked(1, 0, false), book.tickHabit(goal.id))
    }

    @Test
    fun returningAnUnusedRewardRefundsItsPrice() = runTest {
        book.tickHabit(habit(points = 10).id)
        val bought = book.buy(reward(price = 7).id) as BuyResult.Bought

        assertTrue(book.returnPurchase(bought.purchaseId))

        assertEquals(10, book.balance())
        assertFalse(book.returnPurchase(bought.purchaseId))
        assertFalse(book.use(bought.purchaseId))
    }

    @Test
    fun aUsedRewardCannotBeReturned() = runTest {
        book.tickHabit(habit(points = 10).id)
        val bought = book.buy(reward(price = 7).id) as BuyResult.Bought
        book.use(bought.purchaseId)

        assertFalse(book.returnPurchase(bought.purchaseId))
        assertEquals(3, book.balance())
    }
}
