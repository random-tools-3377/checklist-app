package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BalanceCheckTest {
    private val monday = LocalDate(2026, 11, 9)

    @Test
    fun pricesAreJudgedByDaysOfEarning() {
        val rewards = listOf(
            Reward(id = 1, name = "Sweet", icon = "🍬", price = 4),
            Reward(id = 2, name = "Film", icon = "🎬", price = 60),
            Reward(id = 3, name = "Trip", icon = "✈️", price = 2000),
            Reward(id = 4, name = "Coffee", icon = "☕", price = 10, size = RewardSize.SMALL),
        )
        val checks = BalanceCheck.rewards(rewards, perDay = 20.0, perDayAtNormal = 20.0, budget = null)
        assertEquals(listOf(PriceVerdict.TOO_CHEAP, PriceVerdict.FAIR, PriceVerdict.OUT_OF_REACH, PriceVerdict.FAIR), checks.map { it.verdict })
        assertEquals(3.0, checks[1].days)
        assertEquals(20, checks[0].suggested) // a day's worth
        assertNull(checks[1].suggested)
        assertEquals(840, checks[2].suggested) // six weeks' worth
        assertEquals(20, checks[3].suggested) // from its size
        assertNull(BalanceCheck.rewards(rewards, perDay = null, perDayAtNormal = null, budget = null)[0].verdict)
    }

    @Test
    fun goalsDoneNearlyAlwaysOrOftenMissedGetHints() {
        val start = monday.minusDays(28)
        val always = Goal(id = 1, kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 4, repeat = Repeat.everyDay(start))
        val rarely = Goal(id = 2, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 10, repeat = Repeat.everyDay(start))
        val new = Goal(id = 3, kind = GoalKind.HABIT, name = "Stretch", icon = "🧘", points = 2, repeat = Repeat.everyDay(start))
        val ticks = (1..28).flatMap { d ->
            val day = monday.minusDays(d)
            listOfNotNull(
                Tick(goalId = 1, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day),
                if (d % 4 == 0) Tick(goalId = 2, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day) else null,
            )
        }
        val checks = BalanceCheck.goals(listOf(always, rarely, new), ticks, monday) { if (it.id == 3L) monday.minusDays(2) else start }
        assertEquals(listOf(1L, 2L), checks.map { it.goal.id }) // too new to judge
        assertEquals(GoalHint.PAY_LESS, checks[0].hint)
        assertEquals(3, checks[0].suggested)
        assertEquals(28, checks[0].due)
        assertEquals(GoalHint.PAY_MORE, checks[1].hint)
        assertEquals(15, checks[1].suggested)
        assertEquals(7, checks[1].done)
    }

    @Test
    fun interestIsPaidOnAPositiveBalanceAtTheEndOfEachWeek() = runTest {
        val store = MemoryStore()
        val clock = TestClock(LocalDateTime(2026, 11, 4, 10, 0)) // a Wednesday
        val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
        val interest = Interest(store, book)
        store.putSetting(Feature.INTEREST.key, Feature.INTEREST.onValue)
        assertTrue(interest.pay().isEmpty()) // starts counting from here
        store.addEntry(Entry(at = 0, day = LocalDate(2026, 11, 5), kind = EntryKind.TICK, amount = 120, label = "Read"))

        // Two weeks later: last week's and the week before's.
        clock.set(LocalDateTime(2026, 11, 18, 10, 0))
        val paid = interest.pay()
        assertEquals(listOf(2, 2), paid.map { it.amount }) // 2% of 120, then of 122, rounded down
        assertEquals(listOf(LocalDate(2026, 11, 9), LocalDate(2026, 11, 16)), paid.map { it.day })
        assertEquals(124, book.balance())
        assertTrue(interest.pay().isEmpty()) // not twice

        // Off, or a balance below zero, pays nothing.
        store.putSetting(Interest.PERCENT, "0")
        clock.set(LocalDateTime(2026, 11, 25, 10, 0))
        assertTrue(interest.pay().isEmpty())
    }
}
