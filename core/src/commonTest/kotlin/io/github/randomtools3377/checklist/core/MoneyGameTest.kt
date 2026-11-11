package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyGameTest {
    private val first = LocalDate(2026, 11, 1) // November: 30 days
    private val weekdays = List(7) { if (it < 5) WorkHours(9 * 60, 17 * 60) else null }
    private val plan = PayPlan(3000.0, PayUnit.MONTH, weekdays)

    private fun spend(day: Int, cents: Long, category: Long? = 1, bill: Long? = null) =
        MoneyEntry(kind = MoneyKind.SPENT, cents = cents, categoryId = category, day = LocalDate(2026, 11, day), billId = bill)

    @Test
    fun eachDayHasAShareOfTheMonthAfterBills() {
        val entries = listOf(spend(1, 60_000, bill = 9), MoneyEntry(kind = MoneyKind.RECEIVED, cents = 30_000, day = LocalDate(2026, 11, 3)))
        // (3000 + 300 − 600) over 30 days.
        assertEquals(90.0, MoneyGame.share(plan, first, emptyMap(), entries)!!, 0.0001)
        assertNull(MoneyGame.share(null, first, emptyMap(), emptyList()))
        assertEquals(10.0, MoneyGame.share(null, first, emptyMap(), listOf(MoneyEntry(kind = MoneyKind.RECEIVED, cents = 30_000, day = first)))!!, 0.0001)
    }

    @Test
    fun goodDaysPayAndMakeAStreak() {
        val entries = listOf(spend(2, 5_000), spend(3, 15_000), spend(5, 9_999), spend(5, 1_000, bill = 9))
        val share = { _: LocalDate -> 100.0 }
        val paid = MoneyGame.settle(LocalDate(2026, 11, 2), LocalDate(2026, 11, 5), share, entries, emptyList())
        // The 2nd within its share, the 3rd over, the 4th spent nothing, the 5th within (bills aside).
        assertEquals(listOf(2 to 3, 4 to 5, 5 to 3), paid.map { it.day.dayOfMonth to it.points })
        assertEquals(2, MoneyGame.streak(first, LocalDate(2026, 11, 5), share, entries))
        assertEquals(0, MoneyGame.streak(first, LocalDate(2026, 11, 3), share, entries))
        assertEquals(0, MoneyGame.streak(first, LocalDate(2026, 11, 5), { null }, entries))
    }

    @Test
    fun aMonthWithinEveryLimitPaysABonusWhenItEnds() {
        val food = MoneyCategory(id = 1, name = "Food", icon = "🍎", limitCents = 20_000)
        val fun_ = MoneyCategory(id = 2, name = "Fun", icon = "🎉")
        val entries = listOf(spend(10, 19_000), spend(12, 50_000, category = 2))
        assertEquals(true, MoneyGame.withinLimits(first, listOf(food, fun_), entries))
        assertNull(MoneyGame.withinLimits(first, listOf(fun_), entries))
        val paid = MoneyGame.settle(LocalDate(2026, 11, 30), LocalDate(2026, 12, 1), { null }, entries, listOf(food, fun_))
        assertEquals(listOf(MoneyGame.MONTH_BONUS), paid.map { it.points })
        assertEquals(false, MoneyGame.withinLimits(first, listOf(food), entries + spend(20, 2_000)))
    }

    @Test
    fun savingGoalsPayOnceWhenReached() {
        val bike = SavingProgress(SavingGoal(id = 1, name = "Bike", icon = "🚲", targetCents = 100), 100)
        val trip = SavingProgress(SavingGoal(id = 2, name = "Trip", icon = "✈️", targetCents = 100), 50)
        assertEquals(listOf(1L), MoneyGame.savedUp(listOf(bike, trip), emptySet()).map { it.goal.id })
        assertEquals(emptyList(), MoneyGame.savedUp(listOf(bike, trip), setOf(1L)))
        assertEquals(setOf(1L, 7L), MoneyGame.decodeIds(MoneyGame.encodeIds(setOf(7L, 1L))))
    }
}
