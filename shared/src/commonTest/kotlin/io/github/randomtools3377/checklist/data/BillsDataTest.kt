package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Bill
import io.github.randomtools3377.checklist.core.BillEvery
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.SavingGoal
import io.github.randomtools3377.checklist.core.SavingProgress
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bills counted as spent on their days, saving goals, and both in the backup. */
class BillsDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val today = LocalDate(2026, 11, 11)

    @AfterTest
    fun close() = db.close()

    @Test
    fun aBillIsCountedOnItsDayOnceFromWhenItWasAdded() = runTest {
        // Monthly on the 1st since September, added today: September, October and November 1st aren't counted.
        repository.saveBill(Bill(name = "Phone", icon = "📱", cents = 2_500, first = LocalDate(2026, 9, 1)), today)
        // Weekly from today: today's counts.
        repository.saveBill(Bill(name = "Paper", icon = "📰", cents = 300, every = BillEvery.WEEKS, first = today), today)
        repository.countBills(today)
        assertEquals(listOf("Paper"), dao.allMoney().map { it.note })
        repository.countBills(today)
        assertEquals(1, dao.allMoney().size)

        // On December 1st: the phone bill, and three more papers.
        repository.countBills(LocalDate(2026, 12, 1))
        val counted = dao.allMoney().map { it.toCore() }
        assertEquals(1, counted.count { it.note == "Phone" && it.day == LocalDate(2026, 12, 1) })
        assertEquals(listOf(today, today.plusDays(7), today.plusDays(14)), counted.filter { it.note == "Paper" }.map { it.day })
        assertTrue(counted.all { it.kind == MoneyKind.SPENT && it.billId != null })

        // A stopped bill isn't counted again.
        repository.removeBill(dao.allBills().first { it.name == "Paper" }.id)
        repository.countBills(LocalDate(2026, 12, 31))
        assertEquals(3, dao.allMoney().count { it.note == "Paper" })
    }

    @Test
    fun aMonthlyBillWithAnOldFirstDateIsCountedOnItsDay() = runTest {
        repository.saveBill(Bill(name = "Rent", icon = "🏠", cents = 90_000, first = LocalDate(2024, 11, 11)), today.minusDays(1))
        repository.countBills(today)
        assertEquals(listOf(today), dao.allMoney().map { it.toCore().day })
    }

    @Test
    fun noMoreComesOutOfASavingGoalThanIsInIt() = runTest {
        repository.saveSavingGoal(SavingGoal(name = "Bike", icon = "🚲", targetCents = 30_000))
        val bike = dao.allSavingGoals().single().toCore()
        repository.moveSaving(bike.id, 5_000, out = false, day = today, name = bike.name)
        repository.moveSaving(bike.id, 50_000, out = true, day = today, name = bike.name)
        repository.moveSaving(bike.id, 1_000, out = true, day = today, name = bike.name)
        val money = dao.allMoney().map { it.toCore() }
        assertEquals(2, money.size)
        assertEquals(setOf(MoneyKind.SAVED to 5_000L, MoneyKind.TAKEN_OUT to 5_000L), money.map { it.kind to it.cents }.toSet())

        // Removed with what's in it taken out first, so it isn't left counted as saved.
        repository.moveSaving(bike.id, 2_000, out = false, day = today, name = bike.name)
        repository.removeSavingGoal(bike.id, takeOutOn = today)
        assertTrue(dao.allSavingGoals().single().archived)
        assertEquals(0L, dao.allMoney().map { it.toCore() }.sumOf { if (it.kind == MoneyKind.SAVED) it.cents else -it.cents })
        // Removed as it is, what's in it stays saved.
        repository.saveSavingGoal(SavingGoal(name = "Trip", icon = "✈️", targetCents = 30_000))
        val trip = dao.allSavingGoals().first { it.name == "Trip" }.toCore()
        repository.moveSaving(trip.id, 3_000, out = false, day = today, name = trip.name)
        repository.removeSavingGoal(trip.id)
        assertEquals(1, dao.allMoney().count { it.savingId == trip.id })
    }

    @Test
    fun savingGoalsFillUpAndGoInTheBackup() = runTest {
        repository.saveSavingGoal(SavingGoal(name = "Bike", icon = "🚲", targetCents = 30_000))
        val bike = dao.allSavingGoals().single().toCore()
        repository.moveSaving(bike.id, 20_000, out = false, day = today, name = bike.name)
        repository.moveSaving(bike.id, 5_000, out = true, day = today, name = bike.name)
        val progress = SavingProgress.of(dao.observeSavingGoals().first().map { it.toCore() }, dao.observeSavingMoney().first().map { it.toCore() })
        assertEquals(15_000, progress.single().savedCents)

        repository.saveBill(Bill(name = "Rent", icon = "🏠", cents = 90_000, first = today), today)
        val backup = Backup.decode(repository.exportBackup())
        assertEquals(listOf("Rent"), backup.bills.map { it.name })
        assertEquals(listOf("Bike"), backup.savingGoals.map { it.name })
        dao.deleteBills()
        dao.deleteSavingGoals()
        repository.restore(backup)
        assertEquals(1, dao.allBills().size)
        assertEquals(1, dao.allSavingGoals().size)
        assertEquals(2, dao.allMoney().count { it.savingId == bike.id })
    }
}
