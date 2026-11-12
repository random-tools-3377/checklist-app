package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pay that changes from a date, regular money in, bill reminders, and spending paid from savings. */
class PayPlansAndMoneyInTest {
    private val nineToFive = WorkHours(9 * 60, 17 * 60)
    private val weekdays = List(7) { if (it < 5) nineToFive else null }
    private val first = LocalDate(2026, 11, 1)
    private val rise = LocalDate(2026, 11, 16) // a Monday
    private val old = PayPlan(20.0, PayUnit.HOUR, weekdays)
    private val new = PayPlan(25.0, PayUnit.HOUR, weekdays)
    private val endOfMonth = LocalDateTime(2026, 12, 1, 0, 0)

    @Test
    fun aPayRiseMidMonthLeavesEarlierDaysAsTheyWere() {
        val plans = PayPlans.of(old).changed(new, rise)
        // Before the rise: 8 hours at 20. From it on: 8 hours at 25.
        assertEquals(160.0, Pay.day(plans, LocalDate(2026, 11, 13)).total, 0.0001)
        assertEquals(200.0, Pay.day(plans, rise).total, 0.0001)
        // November 2026: 10 work days before the 16th and 11 from it on.
        val month = Pay.month(plans, first, emptyMap(), endOfMonth)
        assertEquals(10 * 160.0 + 11 * 200.0, month.total, 0.0001)
        // October, a past month, is just as it was with the old pay.
        val october = LocalDate(2026, 10, 1)
        assertEquals(Pay.month(old, october, emptyMap(), endOfMonth).total, Pay.month(plans, october, emptyMap(), endOfMonth).total, 0.0001)
        // The Budget tab's month and the budget game's share use the same days.
        assertEquals(month.total, Budget.month(first, plans, emptyMap(), emptyList(), emptyList(), endOfMonth).pay!!.total, 0.0001)
        assertEquals(month.total / 30, MoneyGame.share(plans, first, emptyMap(), emptyList())!!, 0.0001)
        assertEquals(new, plans.latest)
    }

    @Test
    fun aNewTaxShareOrWeekAlsoAppliesFromItsDate() {
        val fourDays = old.copy(week = List(7) { if (it < 4) nineToFive else null }, takeHome = 80)
        val plans = PayPlans.of(old).changed(fourDays, rise)
        val friday = LocalDate(2026, 11, 13)
        assertEquals(160.0, Pay.day(plans, friday).total, 0.0001)
        assertEquals(0.0, Pay.day(plans, friday.plusDays(7)).total, 0.0001)
        assertEquals(128.0, Pay.day(plans, rise).total, 0.0001)
        // A monthly pay is spread over each plan's own usual minutes of the month.
        val monthly = PayPlans.of(PayPlan(2100.0, PayUnit.MONTH, weekdays)).changed(PayPlan(4200.0, PayUnit.MONTH, weekdays), rise)
        assertEquals(100.0, Pay.day(monthly, friday).total, 0.0001)
        assertEquals(200.0, Pay.day(monthly, rise).total, 0.0001)
    }

    @Test
    fun changingAgainReplacesLaterPlansAndFromTheStartReplacesAll() {
        val plans = PayPlans.of(old).changed(new, rise)
        // A change from an earlier day takes the place of the one after it.
        val third = PayPlan(30.0, PayUnit.HOUR, weekdays)
        val earlier = plans.changed(third, LocalDate(2026, 11, 2))
        assertEquals(listOf(null, LocalDate(2026, 11, 2)), earlier.plans.map { it.from })
        assertEquals(240.0, Pay.day(earlier, rise).total, 0.0001)
        // The same day again: only the newest stays for that day.
        assertEquals(2, plans.changed(third, rise).plans.size)
        // From the start: one plan for every day.
        assertEquals(PayPlans.of(third), plans.changed(third, null))
        // Saving the pay it already is adds nothing.
        assertEquals(plans, plans.changed(new, LocalDate(2026, 11, 20)))
        // The earliest plan covers the days before it, too.
        val dated = PayPlans(listOf(DatedPlan(rise, new)))
        assertEquals(new, dated.on(first))
        assertEquals(listOf(null, rise), dated.changed(third, rise).plans.map { it.from })
        assertEquals(new, dated.changed(third, rise).on(first))
    }

    @Test
    fun theSettingReadsBothASinglePlanAndDatedOnes() {
        // What older versions and backups hold: one plan.
        val single = PayPlan.encode(old)
        assertEquals(PayPlans.of(old), PayPlans.decode(single))
        assertEquals(old, PayPlan.decode(single))
        // One plan is still written the older way.
        assertEquals(single, PayPlans.encode(PayPlans.of(old)))
        val plans = PayPlans.of(old).changed(new, rise)
        val text = PayPlans.encode(plans)
        assertEquals(plans, PayPlans.decode(text))
        // The pay as it is now, for everything that asks for one plan.
        assertEquals(new, PayPlan.decode(text))
        assertNull(PayPlans.decode(null))
        assertNull(PayPlans.decode("not json"))
        assertNull(PayPlans.decode("""{"plans":[]}"""))
        // Out of order in the file: read earliest first.
        val shuffled = PayPlans.decode(PayPlans.encode(PayPlans(plans.plans.reversed())))!!
        assertEquals(listOf(null, rise), shuffled.plans.map { it.from })
    }

    @Test
    fun regularMoneyInIsPartOfTheDaysShareBeforeAndAfterItComes() {
        val pension = Bill(id = 4, name = "Pension", icon = "💵", cents = 60_000, first = LocalDate(2026, 11, 20), moneyIn = true)
        val rent = Bill(id = 5, name = "Rent", icon = "🏠", cents = 30_000, first = LocalDate(2026, 11, 25))
        // Still to come: (600 − 300) over 30 days, with no pay at all.
        assertEquals(10.0, MoneyGame.share(null, first, emptyMap(), emptyList(), listOf(pension, rent))!!, 0.0001)
        // Once it has come in, counted as money received with the bill's id, the share is the same.
        val came = MoneyEntry(kind = MoneyKind.RECEIVED, cents = 60_000, day = LocalDate(2026, 11, 20), billId = 4)
        val counted = pension.copy(countedTo = LocalDate(2026, 11, 20))
        assertEquals(10.0, MoneyGame.share(null, first, emptyMap(), listOf(came), listOf(counted, rent))!!, 0.0001)
        // It is money in for the month, never spending.
        val month = Budget.month(first, null, emptyMap(), listOf(came), emptyList(), endOfMonth)
        assertEquals(60_000, month.receivedCents)
        assertEquals(0, month.spentCents)
        assertEquals(0, MoneyGame.spent(LocalDate(2026, 11, 20), listOf(came)))
    }

    @Test
    fun aBillRemindsTheChosenDaysBeforeItIsDue() {
        val rent = Bill(id = 5, name = "Rent", icon = "🏠", cents = 65_000, first = LocalDate(2026, 11, 25), remindDays = 1)
        val quiet = rent.copy(id = 6, name = "Phone", remindDays = null)
        val stopped = rent.copy(id = 7, archived = true)
        val from = LocalDateTime(2026, 11, 1, 0, 0)
        val to = LocalDateTime(2027, 1, 1, 0, 0)
        val alerts = Alerts.bills(listOf(rent, quiet, stopped), from, to)
        assertEquals(listOf(LocalDateTime(2026, 11, 24, 9, 0), LocalDateTime(2026, 12, 24, 9, 0)), alerts.map { it.at })
        assertTrue(alerts.all { it.kind == AlertKind.BILL && it.goalId == 5L })
        // At the time the user's day starts, once that is set; and on the day itself with 0.
        val onTheDay = Alerts.bills(listOf(rent.copy(remindDays = 0)), from, to, LocalTime(7, 30))
        assertEquals(LocalDateTime(2026, 11, 25, 7, 30), onTheDay.first().at)
        // A week before a bill due early in the month reaches back into the month before.
        val early = rent.copy(first = LocalDate(2026, 12, 3), remindDays = 7)
        assertEquals(listOf(LocalDateTime(2026, 11, 26, 9, 0)), Alerts.bills(listOf(early), from, LocalDateTime(2026, 12, 1, 0, 0)).map { it.at })
        // Only the minute it comes in.
        val nine = LocalDateTime(2026, 11, 24, 9, 0)
        assertEquals(1, Alerts.bills(listOf(rent), nine, LocalDateTime(2026, 11, 24, 9, 1)).size)
        assertTrue(Alerts.bills(listOf(rent), LocalDateTime(2026, 11, 24, 9, 1), LocalDateTime(2026, 11, 24, 9, 2)).isEmpty())
    }

    @Test
    fun aBillReminderSaysWhatIsDueAndOnlyABillIsDueBehindTheLock() {
        val rent = Bill(id = 5, name = "Rent", icon = "🏠", cents = 65_000, first = LocalDate(2026, 11, 25), remindDays = 1)
        val pension = Bill(id = 4, name = "Pension", icon = "💵", cents = 60_000, first = LocalDate(2026, 11, 25), moneyIn = true, remindDays = 3)
        val from = LocalDateTime(2026, 11, 20, 0, 0)
        val to = LocalDateTime(2026, 11, 26, 0, 0)
        val money = { cents: Long -> (cents / 100).toString() }
        val shown = Alerts.billReminders(listOf(rent, pension), from, to, amount = money)
        assertEquals(listOf("💵 Pension comes in in 3 days, 600", "🏠 Rent is due tomorrow, 650"), shown.map { it.title })
        assertEquals("Rent is due today, 650", Bills.reminderWords(rent, 0, "650"))
        // Their ids are apart from the goals', and from each other.
        assertEquals(listOf(Alerts.BILL_IDS + 4, Alerts.BILL_IDS + 5), shown.map { it.goalId })
        val locked = Alerts.billReminders(listOf(rent, pension), from, to, hidden = true, amount = money)
        assertEquals(listOf("🔒 Money is coming in", "🔒 A bill is due"), locked.map { it.title })
        assertTrue(locked.none { "Rent" in it.title + it.text || "650" in it.title + it.text || "Pension" in it.title + it.text })
        assertEquals("The day before", Bills.remindLabel(1))
        assertEquals("On the day", Bills.remindLabel(0))
    }

    @Test
    fun spendingPaidFromSavingsLeavesTheMonthAlone() {
        val food = MoneyCategory(id = 1, name = "Food", icon = "🍎", limitCents = 5_000)
        val goal = SavingGoal(id = 3, name = "Holiday", icon = "🏖️", targetCents = 100_000)
        val day = LocalDate(2026, 11, 10)
        val put = MoneyEntry(id = 1, kind = MoneyKind.SAVED, cents = 30_000, day = LocalDate(2026, 10, 5), savingId = 3)
        val paid = MoneyEntry(id = 2, kind = MoneyKind.TAKEN_OUT, cents = 20_000, categoryId = 1, note = "Flights", day = day, savingId = 3, spentFromSavings = true)
        val plain = MoneyEntry(id = 3, kind = MoneyKind.TAKEN_OUT, cents = 1_000, day = day, savingId = 3)
        assertTrue(paid.paidFromSavings)
        assertFalse(plain.paidFromSavings)
        val entries = listOf(put, paid, plain)
        // The money left the goal.
        assertEquals(9_000, SavingProgress.of(listOf(goal), entries).single().savedCents)
        // It is no spending, and unlike money simply taken out it isn't back in the pocket either.
        val month = Budget.month(first, null, emptyMap(), entries, listOf(food), endOfMonth)
        assertEquals(0, month.spentCents)
        assertEquals(-1_000, month.savedCents)
        assertEquals(0, month.categories.single().cents)
        // The money game and the limits don't see it.
        assertEquals(0, MoneyGame.spent(day, entries))
        assertEquals(true, MoneyGame.withinLimits(first, listOf(food), entries))
        // In the month's spreadsheet it is a line at zero that says what it was.
        val sheet = BudgetSheet.csv(first, entries, listOf(food), null, endOfMonth)
        assertTrue(sheet.contains("2026-11-10,Spent from savings,Food,0.00,200.00 from savings: Flights"), sheet)
    }

    @Test
    fun theBackupKeepsTheNewFieldsAndOlderFilesStillOpen() {
        val backup = Backup(
            exportedAt = 1, goals = emptyList(), steps = emptyList(), ticks = emptyList(), entries = emptyList(),
            rewards = emptyList(), purchases = emptyList(),
            settings = mapOf(PayPlan.SETTING to PayPlans.encode(PayPlans.of(old).changed(new, rise))),
            money = listOf(MoneyEntry(id = 2, kind = MoneyKind.TAKEN_OUT, cents = 500, categoryId = 1, day = rise, savingId = 3, spentFromSavings = true)),
            bills = listOf(Bill(id = 4, name = "Pension", icon = "💵", cents = 60_000, first = rise, moneyIn = true, remindDays = 3)),
        )
        val text = Backup.encode(backup)
        assertEquals(29, Backup.FORMAT)
        assertEquals(backup, Backup.decode(text))
        // A file from before: no such fields, one plan in the setting.
        val older = text.replace("\"format\": 29", "\"format\": 28")
            .replace(Regex(",\\s*\"spentFromSavings\": true"), "")
            .replace(Regex(",\\s*\"moneyIn\": true"), "")
            .replace(Regex(",\\s*\"remindDays\": 3"), "")
        val read = Backup.decode(older)
        assertFalse(read.money.single().spentFromSavings)
        assertFalse(read.bills.single().moneyIn)
        assertNull(read.bills.single().remindDays)
        val single = Backup.decode(Backup.encode(backup.copy(settings = mapOf(PayPlan.SETTING to PayPlan.encode(old)))))
        assertEquals(PayPlans.of(old), PayPlans.decode(single.settings[PayPlan.SETTING]))
    }
}
