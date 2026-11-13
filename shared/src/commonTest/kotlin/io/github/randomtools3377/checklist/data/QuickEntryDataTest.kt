package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Tick
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What makes typing an amount or spending quicker: the id to undo by, the category used last, and a goal's latest amounts. */
class QuickEntryDataTest {
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

    private suspend fun category(name: String) = dao.moneyCategories().first { it.name == name }.id

    @Test
    fun savingSpendingGivesBackItsIdSoItCanBeUndone() = runTest {
        repository.startBudget()
        val id = repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 1_200, categoryId = category("Food"), day = day))
        assertEquals(1_200, repository.moneyEntry(id)!!.cents)
        // Changing it keeps its id.
        assertEquals(id, repository.saveMoney(repository.moneyEntry(id)!!.copy(cents = 1_500)))
        // Undo takes it off again.
        repository.removeMoney(id)
        assertNull(repository.moneyEntry(id))
    }

    @Test
    fun theCategoryUsedLastIsTheNewestSpendingTypedIn() = runTest {
        repository.startBudget()
        assertNull(dao.lastSpentCategory())
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 1_200, categoryId = category("Food"), day = day, at = 1_000))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 900, categoryId = category("Fun"), day = LocalDate(2026, 11, 1), at = 2_000))
        // Typed in last, though dated earlier.
        assertEquals(category("Fun"), dao.lastSpentCategory())
        // Money in, a reward bought in the shop and a bill counted by itself aren't spending typed in.
        repository.saveMoney(MoneyEntry(kind = MoneyKind.RECEIVED, cents = 5_000, day = day, at = 3_000))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 700, categoryId = category("Rewards"), day = day, at = 4_000, purchaseId = 3))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 700, categoryId = category("Bills"), day = day, at = 5_000, billId = 2))
        assertEquals(category("Fun"), dao.lastSpentCategory())
    }

    @Test
    fun aGoalsLatestAmountsGiveItsUsualOnes() = runTest {
        fun tick(goalId: Long, at: Long, amount: Double?) =
            Tick(goalId = goalId, stepId = null, at = at, day = day, period = Period.DAILY, periodStart = day, amount = amount).toEntity()
        listOf(tick(1, 1, 10.0), tick(1, 2, 20.0), tick(1, 3, 10.0), tick(1, 4, null), tick(2, 5, 99.0), tick(1, 6, 5.0)).forEach { dao.insertTick(it) }
        val latest = dao.latestAmounts(1, Amounts.USUAL_FROM).map { it.toCore() }
        assertEquals(listOf(5.0, 10.0, 20.0, 10.0), latest.map { it.amount })
        assertEquals(listOf(10.0, 5.0, 20.0), Amounts.usual(latest))
        assertEquals(listOf(5.0, 10.0), dao.latestAmounts(1, 2).map { it.amount })
    }
}
