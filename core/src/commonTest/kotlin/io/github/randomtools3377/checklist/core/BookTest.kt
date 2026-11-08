package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDateTime

class BookTest {
    private val store = MemoryStore()

    // Wednesday 11 November 2026, 10:00.
    private val clock = TestClock(LocalDateTime(2026, 11, 11, 10, 0))
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }

    private fun habit(points: Int, period: Period = Period.DAILY, target: Int = 1, bonus: Int = 0) =
        store.addGoal(Goal(kind = GoalKind.HABIT, name = "Habit", icon = "⭐", points = points, period = period, target = target, bonus = bonus))

    private fun project(bonus: Int, vararg stepPoints: Int): Pair<Goal, List<Step>> {
        val goal = store.addGoal(Goal(kind = GoalKind.PROJECT, name = "Project", icon = "⭐", points = 0, period = null, bonus = bonus))
        val steps = stepPoints.mapIndexed { i, p -> store.addStep(Step(goalId = goal.id, name = "Step $i", points = p, position = i)) }
        return goal to steps
    }

    private fun reward(price: Int) = store.addReward(Reward(name = "Reward", icon = "🎁", price = price))

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
}
