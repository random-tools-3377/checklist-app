package io.github.randomtools3377.checklist.data

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
}
