package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MoneyPricesTest {
    private val monday = LocalDate(2026, 11, 9)

    // 5 a day, and 7 twice a week: a full week pays 49.
    private val goals = listOf(
        Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(monday)),
        Goal(id = 2, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 7, repeat = Repeat(RepeatKind.TIMES_PER, monday, per = Period.WEEKLY), target = 2),
    )

    @Test
    fun aFullWeekOfGoalsEarnsTheWeeklyBudget() {
        val rate = MoneyPrices.fresh(goals, monday, budget = 7.0)!!
        assertEquals(7.0, rate.pointsPerUnit, 0.0001)
        assertEquals(175, MoneyPrices.price(25.0, rate))
        assertEquals(25, MoneyPrices.price(3.5, rate)) // 24.5, rounded to 5
        assertEquals(1, MoneyPrices.price(0.01, rate))
        // A bigger budget makes each point worth more money, so prices in points come down.
        assertEquals(1.0, MoneyPrices.fresh(goals, monday, budget = 49.0)!!.pointsPerUnit, 0.0001)
    }

    @Test
    fun noRateWithoutABudgetOrGoalsThatPay() {
        assertNull(MoneyPrices.fresh(goals, monday, budget = null))
        assertNull(MoneyPrices.fresh(goals, monday, budget = 0.0))
        assertNull(MoneyPrices.fresh(emptyList(), monday, budget = 20.0))
        val project = Goal(id = 3, kind = GoalKind.PROJECT, name = "Shed", icon = "🛖", points = 50, repeat = null)
        assertNull(MoneyPrices.fresh(listOf(project), monday, budget = 20.0))
    }

    @Test
    fun theRateOnlyMovesOnceItHasDriftedOrTheBudgetChanged() {
        val stored = MoneyPrices.Rate(10.0, 20.0)
        assertSame(stored, MoneyPrices.next(stored, MoneyPrices.Rate(10.9, 20.0)))
        assertSame(stored, MoneyPrices.next(stored, MoneyPrices.Rate(9.1, 20.0)))
        assertEquals(MoneyPrices.Rate(11.5, 20.0), MoneyPrices.next(stored, MoneyPrices.Rate(11.5, 20.0)))
        assertEquals(MoneyPrices.Rate(8.5, 20.0), MoneyPrices.next(stored, MoneyPrices.Rate(8.5, 20.0)))
        assertEquals(MoneyPrices.Rate(10.2, 25.0), MoneyPrices.next(stored, MoneyPrices.Rate(10.2, 25.0)))
        assertEquals(MoneyPrices.Rate(3.0, 20.0), MoneyPrices.next(null, MoneyPrices.Rate(3.0, 20.0)))
        // With nothing to work it out from, prices stay where they are.
        assertSame(stored, MoneyPrices.next(stored, null))
        assertNull(MoneyPrices.next(null, null))
    }

    @Test
    fun onlyRewardsThatFollowMoneyAreRepriced() {
        val rate = MoneyPrices.Rate(4.0, 50.0)
        val rewards = listOf(
            Reward(id = 1, name = "Film", icon = "🎬", price = 30, value = 12.0, followsMoney = true),
            Reward(id = 2, name = "Book", icon = "📚", price = 30, value = 12.0),
            Reward(id = 3, name = "Old", icon = "🧸", price = 30, value = 12.0, followsMoney = true, archived = true),
            Reward(id = 4, name = "Meal", icon = "🍜", price = 80, value = 20.0, followsMoney = true),
            Reward(id = 5, name = "Walk", icon = "🌳", price = 10, followsMoney = true),
        )
        assertEquals(mapOf(1L to 50), MoneyPrices.changes(rewards, rate))
    }

    @Test
    fun theRateIsStoredAsText() {
        val rate = MoneyPrices.Rate(4.25, 50.0)
        assertEquals(rate, MoneyPrices.decode(MoneyPrices.encode(rate)))
        assertNull(MoneyPrices.decode(null))
        assertNull(MoneyPrices.decode(""))
        assertNull(MoneyPrices.decode("4.25"))
        assertNull(MoneyPrices.decode("0|50"))
        assertNull(MoneyPrices.decode("x|50"))
    }

    @Test
    fun followingMoneyGoesInTheBackup() {
        val backup = Backup(
            exportedAt = 0,
            goals = emptyList(),
            steps = emptyList(),
            ticks = emptyList(),
            entries = emptyList(),
            rewards = listOf(Reward(id = 1, name = "Film", icon = "🎬", price = 50, value = 12.0, followsMoney = true)),
            purchases = emptyList(),
            settings = emptyMap(),
        )
        val back = Backup.decode(Backup.encode(backup))
        assertTrue(back.rewards.single().followsMoney)
        // Files from before format 23 have no such field: those rewards keep the price set by hand.
        val older = Backup.encode(backup).replace(Regex(""",\s*"followsMoney": true"""), "")
        assertFalse(older.contains("followsMoney"))
        assertFalse(Backup.decode(older).rewards.single().followsMoney)
    }
}
