package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.CategoryRole
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.PayUnit
import io.github.randomtools3377.checklist.core.WorkDay
import io.github.randomtools3377.checklist.core.WorkHours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The budget in the database: categories, money spent and received, rewards, changed days, and the backup. */
class BudgetDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val day = LocalDate(2026, 11, 9)

    @AfterTest
    fun close() = db.close()

    private suspend fun category(name: String) = dao.moneyCategories().first { it.name == name }

    @Test
    fun categoriesStartOnceAndRewardsStays() = runTest {
        repository.startBudget()
        repository.startBudget()
        assertEquals(9, dao.moneyCategories().size)
        val rewards = category("Rewards")
        assertEquals(CategoryRole.REWARDS.name, rewards.role)

        repository.removeMoneyCategory(rewards.id)
        repository.removeMoneyCategory(category("Fun").id)
        assertEquals(false, category("Rewards").archived)
        assertEquals(true, category("Fun").archived)

        repository.moveMoneyCategory(category("Other").id, -1)
        val order = dao.moneyCategories().filter { !it.archived }.sortedBy { it.position }.map { it.name }
        assertEquals(listOf("Food", "Home", "Bills", "Transport", "Health", "Shopping", "Other", "Rewards"), order)

        repository.saveMoneyCategory(io.github.randomtools3377.checklist.core.MoneyCategory(name = "  Pets ", icon = "🐶", limitCents = 5_000))
        assertEquals(5_000, category("Pets").limitCents)
    }

    @Test
    fun rewardsBoughtAreSpentUntilReturned() = runTest {
        repository.rewardBought(purchaseId = 7, name = "Film", value = 12.5, day = day)
        val spent = dao.allMoney().single()
        assertEquals(1_250, spent.cents)
        assertEquals(MoneyKind.SPENT.name, spent.kind)
        assertEquals(category("Rewards").id, spent.categoryId)
        assertEquals("Film", spent.note)
        assertEquals(7, spent.purchaseId)
        // Counting the same purchase again doesn't add it twice.
        repository.rewardBought(purchaseId = 7, name = "Film", value = 12.5, day = day)
        assertEquals(1, dao.allMoney().size)

        repository.rewardReturned(7)
        assertTrue(dao.allMoney().isEmpty())
    }

    @Test
    fun moneyIsSavedChangedAndRemoved() = runTest {
        repository.startBudget()
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 4_550, categoryId = category("Food").id, note = " Groceries ", day = day))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.RECEIVED, cents = 10_000, note = "Gift", day = day))
        val month = dao.observeMoney(day.minusDays(8).toEpochDay(), day.toEpochDay()).first()
        assertEquals(2, month.size)
        val groceries = repository.moneyEntry(month.first { it.note == "Groceries" }.id)!!
        repository.saveMoney(groceries.copy(cents = 5_000))
        assertEquals(5_000, repository.moneyEntry(groceries.id)!!.cents)
        repository.removeMoney(groceries.id)
        assertNull(repository.moneyEntry(groceries.id))
    }

    @Test
    fun theBudgetGoesInTheBackup() = runTest {
        repository.startBudget()
        repository.savePay(PayPlan(20.0, PayUnit.HOUR, List(7) { if (it < 5) WorkHours(540, 1020, 30) else null }, takeHome = 80))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 999, categoryId = category("Fun").id, day = day))
        repository.setWorkDay(WorkDay(day, paid = true))
        repository.setWorkDay(WorkDay(day.plusDays(1), WorkHours(600, 780)))
        repository.setWorkDay(WorkDay(day.plusDays(2)))
        repository.usualWorkDay(day.plusDays(2))

        val text = repository.exportBackup()
        val backup = Backup.decode(text)
        assertEquals(9, backup.moneyCategories.size)
        assertEquals(listOf(999L), backup.money.map { it.cents })
        assertEquals(listOf(WorkDay(day, paid = true), WorkDay(day.plusDays(1), WorkHours(600, 780))), backup.workDays)

        dao.deleteAllMoney()
        dao.deleteWorkDays()
        repository.restore(backup)
        assertEquals(1, dao.allMoney().size)
        assertEquals(2, dao.allWorkDays().size)
        assertEquals(80, PayPlan.decode(dao.setting(PayPlan.SETTING))!!.takeHome)
    }
}
