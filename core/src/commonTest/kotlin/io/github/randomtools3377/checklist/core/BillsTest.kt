package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillsTest {
    private val rent = Bill(id = 1, name = "Rent", icon = "🏠", cents = 90_000, first = LocalDate(2026, 1, 31))
    private val gym = Bill(id = 2, name = "Gym", icon = "🏋️", cents = 1_500, every = BillEvery.WEEKS, count = 2, first = LocalDate(2026, 11, 2))

    @Test
    fun monthlyBillsKeepTheirDateOrTheLastDay() {
        assertEquals(
            listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), LocalDate(2026, 3, 31), LocalDate(2026, 4, 30)),
            Bills.due(rent, LocalDate(2026, 1, 1), LocalDate(2026, 4, 30)),
        )
        val quarterly = rent.copy(count = 3, first = LocalDate(2026, 2, 15))
        assertEquals(listOf(LocalDate(2026, 5, 15), LocalDate(2026, 8, 15)), Bills.due(quarterly, LocalDate(2026, 3, 1), LocalDate(2026, 9, 1)))
        assertEquals(LocalDate(2027, 2, 15), Bills.nth(quarterly, 4))
        assertEquals("every 3 months", quarterly.howOften)
        assertEquals("every month", rent.howOften)
    }

    @Test
    fun weeklyBillsAndEndings() {
        assertEquals(
            listOf(LocalDate(2026, 11, 16), LocalDate(2026, 11, 30)),
            Bills.due(gym, LocalDate(2026, 11, 10), LocalDate(2026, 12, 13)),
        )
        val ending = gym.copy(until = LocalDate(2026, 11, 20))
        assertEquals(listOf(LocalDate(2026, 11, 2), LocalDate(2026, 11, 16)), Bills.due(ending, LocalDate(2026, 1, 1), LocalDate(2027, 1, 1)))
        assertNull(Bills.next(ending, LocalDate(2026, 11, 21)))
        assertEquals(LocalDate(2026, 11, 16), Bills.next(gym, LocalDate(2026, 11, 3)))
        assertEquals(LocalDate(2026, 11, 16), Bills.next(gym, LocalDate(2026, 11, 16)))
        assertEquals(15.0 * 52 / 12 / 2, Bills.monthly(gym), 0.0001)
        assertEquals(900.0, Bills.monthly(rent), 0.0001)
    }

    @Test
    fun billsAreCountedOnceFromWhenTheBudgetStarted() {
        val started = LocalDate(2026, 3, 10)
        // Never counted: from when the budget started, not from January.
        assertEquals(listOf(LocalDate(2026, 3, 31), LocalDate(2026, 4, 30)), Bills.toCount(rent, LocalDate(2026, 5, 1), started))
        // Counted up to the end of April: May's comes next.
        val counted = rent.copy(countedTo = LocalDate(2026, 4, 30))
        assertEquals(emptyList(), Bills.toCount(counted, LocalDate(2026, 5, 30), started))
        assertEquals(listOf(LocalDate(2026, 5, 31)), Bills.toCount(counted, LocalDate(2026, 5, 31), started))
        assertEquals(emptyList(), Bills.toCount(counted.copy(archived = true), LocalDate(2026, 6, 1), started))
    }

    @Test
    fun savingGoalsAddUpWhatWentInAndOut() {
        val bike = SavingGoal(id = 1, name = "Bike", icon = "🚲", targetCents = 50_000, position = 1)
        val trip = SavingGoal(id = 2, name = "Trip", icon = "✈️", targetCents = 10_000, position = 0)
        val day = LocalDate(2026, 11, 9)
        val entries = listOf(
            MoneyEntry(kind = MoneyKind.SAVED, cents = 20_000, savingId = 1, day = day),
            MoneyEntry(kind = MoneyKind.TAKEN_OUT, cents = 5_000, savingId = 1, day = day),
            MoneyEntry(kind = MoneyKind.SAVED, cents = 12_000, savingId = 2, day = day),
            MoneyEntry(kind = MoneyKind.SPENT, cents = 999, day = day),
        )
        val progress = SavingProgress.of(listOf(bike, trip), entries)
        assertEquals(listOf("Trip", "Bike"), progress.map { it.goal.name })
        assertTrue(progress[0].reached)
        assertEquals(0, progress[0].leftCents)
        assertEquals(15_000, progress[1].savedCents)
        assertFalse(progress[1].reached)
        assertEquals(0.3f, progress[1].share, 0.0001f)

        // Saving comes off what's left in the month, like spending.
        val month = Budget.month(Budget.monthOf(day), null, emptyMap(), entries, emptyList(), kotlinx.datetime.LocalDateTime(2026, 11, 30, 12, 0))
        assertEquals(27_000, month.savedCents)
        assertEquals(999, month.spentCents)
        assertEquals(-(27_000 + 999) / 100.0, month.leftNow, 0.001)
    }
}
