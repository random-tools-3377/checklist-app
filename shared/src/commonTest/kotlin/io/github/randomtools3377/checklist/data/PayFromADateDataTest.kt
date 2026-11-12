package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Bill
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.Pay
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.PayPlans
import io.github.randomtools3377.checklist.core.PayUnit
import io.github.randomtools3377.checklist.core.SavingGoal
import io.github.randomtools3377.checklist.core.SavingProgress
import io.github.randomtools3377.checklist.core.WorkHours
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** In the database: pay that changes from a date, regular money in, bill reminders and spending paid from savings. */
class PayFromADateDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val weekdays = List(7) { if (it < 5) WorkHours(540, 1020) else null }
    private val old = PayPlan(20.0, PayUnit.HOUR, weekdays)
    private val new = PayPlan(25.0, PayUnit.HOUR, weekdays)
    private val rise = LocalDate(2026, 11, 16)

    @AfterTest
    fun close() = db.close()

    private suspend fun plans() = PayPlans.decode(dao.setting(PayPlan.SETTING))!!

    @Test
    fun aPayRiseKeepsEarlierDaysAndTheFirstPaySimplyApplies() = runTest {
        // The first pay is for every day, whatever date is given with it.
        repository.savePay(old, rise)
        assertEquals(PayPlans.of(old), plans())
        // As older versions stored it: one plan.
        assertEquals(old, PayPlan.decode(dao.setting(PayPlan.SETTING)))

        repository.savePay(new, rise)
        assertEquals(160.0, Pay.day(plans(), LocalDate(2026, 11, 13)).total, 0.0001)
        assertEquals(200.0, Pay.day(plans(), rise).total, 0.0001)
        // The month's spreadsheet has the month's pay with both.
        val sheet = repository.budgetSpreadsheet(LocalDate(2026, 11, 1), LocalDateTime(2026, 12, 1, 0, 0))
        assertTrue(sheet.contains("Pay,,3800.00"), sheet)
        // October stays what it was: 22 work days at 160.
        val october = repository.budgetSpreadsheet(LocalDate(2026, 10, 1), LocalDateTime(2026, 12, 1, 0, 0))
        assertTrue(october.contains("Pay,,3520.00"), october)

        // It survives a backup and comes back the same.
        val text = repository.exportBackup()
        assertEquals(Backup.FORMAT, Backup.decode(text).format)
        repository.savePay(old)
        assertEquals(PayPlans.of(old), plans())
        repository.restore(Backup.decode(text))
        assertEquals(200.0, Pay.day(plans(), rise).total, 0.0001)
        assertEquals(160.0, Pay.day(plans(), LocalDate(2026, 11, 13)).total, 0.0001)
    }

    @Test
    fun regularMoneyInIsCountedAsReceivedOnItsDay() = runTest {
        repository.startBudget()
        val today = LocalDate(2026, 11, 20)
        repository.saveBill(Bill(name = "Pension", icon = "💵", cents = 60_000, first = today, moneyIn = true, remindDays = 3), today)
        repository.saveBill(Bill(name = "Rent", icon = "🏠", cents = 30_000, first = today), today)
        repository.countBills(today)
        repository.countBills(today)
        val money = dao.allMoney().map { it.toCore() }
        assertEquals(2, money.size)
        val came = money.single { it.kind == MoneyKind.RECEIVED }
        assertEquals(60_000, came.cents)
        assertEquals("Pension", came.note)
        assertTrue(came.billId != null && came.categoryId == null)
        assertEquals(30_000, money.single { it.kind == MoneyKind.SPENT }.cents)
        val stored = dao.allBills().map { it.toCore() }.single { it.moneyIn }
        assertEquals(3, stored.remindDays)
        // And through a backup.
        val backup = Backup.decode(repository.exportBackup())
        repository.restore(backup)
        assertEquals(stored, dao.allBills().map { it.toCore() }.single { it.moneyIn })
    }

    @Test
    fun billRemindersComeWhileBudgetIsOnAndHideBehindTheLock() = runTest {
        val today = LocalDate(2026, 11, 20)
        repository.saveBill(Bill(name = "Rent", icon = "🏠", cents = 65_000, first = LocalDate(2026, 11, 25), remindDays = 1), today)
        val from = LocalDateTime(2026, 11, 24, 0, 0)
        val to = LocalDateTime(2026, 11, 25, 0, 0)
        // Budget is an extra: nothing while it is off.
        assertTrue(billTimes(dao, from, to).isEmpty())
        assertTrue(billReminders(dao, from, to).isEmpty())
        repository.setSetting(Feature.BUDGET.key, Feature.BUDGET.onValue)
        assertEquals(listOf(LocalDateTime(2026, 11, 24, 9, 0)), billTimes(dao, from, to).map { it.at })
        val shown = billReminders(dao, from, to).single()
        assertTrue(shown.title.startsWith("🏠 Rent is due tomorrow, "), shown.title)
        assertTrue(shown.title.endsWith("650"), shown.title)
        // When the user's day starts, once that is set.
        repository.setSetting(SETTING_DAY_START, (7 * 60 + 30).toString())
        assertEquals(LocalDateTime(2026, 11, 24, 7, 30), billTimes(dao, from, to).single().at)
        // Behind the privacy lock it names nothing.
        repository.setSetting(LOCK_BUDGET, "1")
        assertEquals("🔒 A bill is due", billReminders(dao, from, to).single().title)
        // A stopped bill has none.
        repository.removeBill(dao.allBills().single().id)
        assertTrue(billTimes(dao, from, to).isEmpty())
    }

    @Test
    fun spendingFromSavingsComesOutOfTheGoalWhenItHoldsEnough() = runTest {
        repository.startBudget()
        val food = dao.moneyCategories().first { it.name == "Food" }.id
        repository.saveSavingGoal(SavingGoal(name = "Holiday", icon = "🏖️", targetCents = 100_000))
        val goal = dao.allSavingGoals().single().id
        val day = LocalDate(2026, 11, 10)
        repository.moveSaving(goal, 30_000, out = false, day = LocalDate(2026, 10, 5), name = "Holiday")
        suspend fun saved() = SavingProgress.of(dao.allSavingGoals().map { it.toCore() }, dao.allMoney().map { it.toCore() }).single().savedCents
        suspend fun month() = Budget.month(LocalDate(2026, 11, 1), null, emptyMap(), dao.allMoney().map { it.toCore() }, dao.moneyCategories().map { it.toCore() }, LocalDateTime(2026, 12, 1, 0, 0))

        val spend = MoneyEntry(kind = MoneyKind.TAKEN_OUT, cents = 20_000, categoryId = food, note = "Flights", day = day, savingId = goal, spentFromSavings = true)
        repository.saveMoney(spend)
        val stored = dao.allMoney().map { it.toCore() }.single { it.note == "Flights" }
        assertTrue(stored.paidFromSavings)
        assertEquals(food, stored.categoryId)
        assertEquals(10_000, saved())
        assertEquals(0, month().spentCents)
        assertEquals(0, month().savedCents)

        // Changing it counts what it already took as still there: 250 fits in the 300 saved.
        repository.saveMoney(stored.copy(cents = 25_000))
        assertEquals(5_000, saved())
        assertTrue(dao.money(stored.id)!!.toCore().paidFromSavings)

        // More than the goal holds: it is plain spending instead, and the goal keeps its money.
        repository.saveMoney(spend.copy(cents = 9_000, note = "Hotel"))
        val hotel = dao.allMoney().map { it.toCore() }.single { it.note == "Hotel" }
        assertEquals(MoneyKind.SPENT, hotel.kind)
        assertFalse(hotel.spentFromSavings)
        assertEquals(null, hotel.savingId)
        assertEquals(5_000, saved())
        assertEquals(9_000, month().spentCents)

        // Removing the spending puts the money back in the goal.
        repository.removeMoney(stored.id)
        assertEquals(30_000, saved())
    }
}
