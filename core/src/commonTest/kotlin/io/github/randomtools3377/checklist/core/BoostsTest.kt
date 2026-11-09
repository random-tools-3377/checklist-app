package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoostsTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0)) // Monday
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val monday = LocalDate(2026, 11, 9)

    private fun miss(goalId: Long, day: LocalDate) =
        Lapse(goalId = goalId, kind = LapseKind.MISS, day = day, period = Period.DAILY, periodStart = day, at = 0, overLimit = false, label = "")

    @Test
    fun theGoalMissedMostIsBoostedForTheWeek() = runTest {
        assertTrue(!book.boosted(Goal(kind = GoalKind.HABIT, name = "x", icon = "x", points = 1, repeat = null), monday)) // off to start with
        store.putSetting(Feature.BOOST.key, Feature.BOOST.onValue)
        val read = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(monday.minusDays(40)), position = 0))
        val run = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 10, repeat = Repeat.everyDay(monday.minusDays(40)), position = 1))
        listOf(3, 5).forEach { store.addLapse(miss(read.id, monday.minusDays(it))) }
        listOf(2, 4, 6).forEach { store.addLapse(miss(run.id, monday.minusDays(it))) }
        store.addLapse(miss(read.id, monday)) // this week's don't count until next week
        assertEquals(run.id, Boost.goalFor(monday.plusDays(3), store.goals, store.lapses)?.id)
        assertNull(Boost.goalFor(monday.plusDays(7 * 6), store.goals, store.lapses)) // too long ago

        assertTrue(book.boosted(run, monday))
        assertEquals(15, (book.tickHabit(run.id) as TickResult.Ticked).points)
        assertEquals(5, (book.tickHabit(read.id) as TickResult.Ticked).points)
        // With Easy as well: 10 × 150% × 150%, rounded down once.
        assertEquals(22, payFor(10, Difficulty.EASY, boosted = true))
        // Turned off.
        store.putSetting(Boost.SETTING, "0")
        assertTrue(!book.boosted(run, monday))
    }

    @Test
    fun aPerfectDayPaysWhenItEnds() = runTest {
        store.putSetting(Feature.PERFECT_DAY.key, Feature.PERFECT_DAY.onValue)
        val read = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(monday)))
        val days = PerfectDays(store, book)
        assertTrue(days.pay().isEmpty()) // starts looking from today
        book.tickHabit(read.id)

        clock.set(LocalDateTime(2026, 11, 10, 10, 0))
        val paid = days.pay()
        assertEquals(listOf(PerfectDays.DEFAULT_BONUS), paid.map { it.amount })
        assertEquals(monday, paid.single().day)
        assertTrue(days.pay().isEmpty())

        // Tuesday not done: nothing on Wednesday.
        clock.set(LocalDateTime(2026, 11, 11, 10, 0))
        assertTrue(days.pay().isEmpty())
    }

    @Test
    fun aRewardCostsMoreEachTimeItIsBoughtInAWeek() = runTest {
        store.putSetting(Feature.REPEAT_PRICE.key, Feature.REPEAT_PRICE.onValue)
        val coffee = store.addReward(Reward(name = "Coffee", icon = "☕", price = 20))
        store.addEntry(Entry(at = 0, day = monday, kind = EntryKind.TICK, amount = 200, label = "Start"))
        assertEquals(20, (book.buy(coffee.id) as BuyResult.Bought).price)
        val second = book.buy(coffee.id) as BuyResult.Bought
        assertEquals(25, second.price)
        assertEquals(30, book.priceNow(coffee))
        // A returned one doesn't count, and its refund is what was paid.
        assertTrue(book.returnPurchase(second.purchaseId))
        assertEquals(25, book.priceNow(coffee))
        assertEquals(180, book.balance())
        // Next week it is back to its price.
        clock.set(LocalDateTime(2026, 11, 16, 10, 0))
        assertEquals(20, book.priceNow(coffee))
        store.putSetting(RepeatPrice.SETTING, "0")
        clock.set(LocalDateTime(2026, 11, 9, 12, 0))
        assertEquals(20, book.priceNow(coffee))
    }
}
