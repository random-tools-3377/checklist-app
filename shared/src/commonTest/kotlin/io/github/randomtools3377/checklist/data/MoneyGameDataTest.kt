package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Bill
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyGame
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.PayUnit
import io.github.randomtools3377.checklist.core.SavingGoal
import io.github.randomtools3377.checklist.core.WorkHours
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The budget game pays for each day once, from when it started, and for a saving goal reached once. */
class MoneyGameDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())

    @AfterTest
    fun close() = db.close()

    private fun nov(day: Int) = LocalDate(2026, 11, day)

    @Test
    fun daysArePaidOnceAndSavingGoalsOnce() = runTest {
        // 3,000 a month over 30 days: 100 a day.
        repository.savePay(PayPlan(3000.0, PayUnit.MONTH, List(7) { if (it < 5) WorkHours(540, 1020) else null }))
        repository.setSetting(MoneyGame.FROM, nov(2).toEpochDay().toString())
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 5_000, day = nov(2)))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 50_000, day = nov(4)))

        val paid = repository.settleMoneyGame(nov(5))
        assertEquals(listOf(nov(2) to MoneyGame.GOOD_DAY, nov(3) to MoneyGame.NO_SPEND_DAY), paid.map { it.day to it.points })
        assertEquals(8, dao.balance())
        assertEquals(emptyList(), repository.settleMoneyGame(nov(5)))

        repository.saveSavingGoal(SavingGoal(name = "Bike", icon = "🚲", targetCents = 10_000))
        val bike = dao.allSavingGoals().single().id
        repository.moveSaving(bike, 10_000, out = false, day = nov(5), name = "Bike")
        assertEquals(listOf(MoneyGame.SAVED_UP), repository.settleMoneyGame(nov(5)).map { it.points })
        assertEquals(emptyList(), repository.settleMoneyGame(nov(5)))
        assertEquals(28, dao.balance())
    }

    @Test
    fun turnedBackOnItStartsAgainFromThatDay() = runTest {
        repository.savePay(PayPlan(3000.0, PayUnit.MONTH, List(7) { if (it < 5) WorkHours(540, 1020) else null }))
        // Never started: nothing to start again.
        repository.restartMoneyGame(nov(3))
        assertEquals(null, dao.setting(MoneyGame.FROM))
        repository.setSetting(MoneyGame.FROM, nov(2).toEpochDay().toString())
        assertEquals(listOf(nov(2)), repository.settleMoneyGame(nov(3)).map { it.day })
        // Paid up to yesterday: turning it off and on again the same day changes nothing.
        repository.restartMoneyGame(nov(3))
        assertEquals(nov(2).toEpochDay().toString(), dao.setting(MoneyGame.FROM))
        // Off from the 3rd, back on on the 11th: the days it was off aren't paid.
        repository.restartMoneyGame(nov(11))
        assertEquals(emptyList(), repository.settleMoneyGame(nov(11)))
        assertEquals(0, repository.moneyGameToday(nov(11)).streakBefore)
        assertEquals(listOf(nov(11)), repository.settleMoneyGame(nov(12)).map { it.day })
    }

    @Test
    fun theStreakCountsBackToTheStartFromAllTheMoney() = runTest {
        repository.savePay(PayPlan(3000.0, PayUnit.MONTH, List(7) { if (it < 5) WorkHours(540, 1020) else null }))
        repository.setSetting(MoneyGame.FROM, LocalDate(2026, 8, 1).toEpochDay().toString())
        // Over the share on September 20th; good every day since.
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 50_000, day = LocalDate(2026, 9, 20)))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 2_000, day = nov(3)))
        val today = repository.moneyGameToday(nov(11))
        // September 21st to November 10th.
        assertEquals(10 + 31 + 10, today.streakBefore)
        assertEquals(100.0, today.share!!, 0.0001)
        assertEquals(0L, today.spentCents)
    }

    @Test
    fun aBillLaterInTheMonthComesOffEachDaysShareFromTheStart() = runTest {
        repository.savePay(PayPlan(3000.0, PayUnit.MONTH, List(7) { if (it < 5) WorkHours(540, 1020) else null }))
        repository.setSetting(MoneyGame.FROM, nov(2).toEpochDay().toString())
        // Rent of 900 on the 28th: (3000 − 900) over 30 days is 70 a day from the start.
        repository.saveBill(Bill(name = "Rent", icon = "🏠", cents = 90_000, first = LocalDate(2026, 10, 28)), nov(2))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 6_000, day = nov(2)))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 8_000, day = nov(3)))
        assertEquals(listOf(nov(2) to MoneyGame.GOOD_DAY), repository.settleMoneyGame(nov(4)).map { it.day to it.points })
        assertEquals(70.0, repository.moneyGameToday(nov(4)).share!!, 0.0001)
        // Once it's counted on the 28th, the share and the streak stay as they were.
        repository.countBills(nov(28))
        assertEquals(1, dao.allMoney().count { it.billId != null })
        val after = repository.moneyGameToday(nov(28))
        assertEquals(70.0, after.share!!, 0.0001)
        assertEquals(24, after.streakBefore) // the 4th to the 27th, nothing spent
    }
}
