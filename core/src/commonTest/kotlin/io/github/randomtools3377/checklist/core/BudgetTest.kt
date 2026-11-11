package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetTest {
    private val monday = LocalDate(2026, 11, 9)
    private val nineToFive = WorkHours(9 * 60, 17 * 60)
    private val weekdays = List(7) { if (it < 5) nineToFive else null }

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(day.year, day.month, day.dayOfMonth, hour, minute)

    @Test
    fun hourlyPayFillsUpThroughTheDay() {
        val plan = PayPlan(20.0, PayUnit.HOUR, List(7) { if (it < 5) nineToFive.copy(breakMinutes = 30) else null })
        val day = Pay.day(plan, monday)
        assertEquals(150.0, day.total, 0.0001) // 7½ paid hours
        assertEquals(0.0, day.earned(at(monday, 8, 59)), 0.0001)
        assertEquals(75.0, day.earned(at(monday, 13)), 0.0001) // half way from 9 to 5
        assertEquals(240, day.minutesLeft(at(monday, 13)))
        assertEquals(150.0, day.earned(at(monday, 18)), 0.0001)
        assertEquals(0, day.minutesLeft(at(monday, 18)))
        // Saturday is a day off.
        assertEquals(0.0, Pay.day(plan, monday.plusDays(5)).total, 0.0001)
        // Tax: 80% is kept.
        assertEquals(120.0, Pay.day(plan.copy(takeHome = 80), monday).total, 0.0001)
    }

    @Test
    fun aMonthlyPayIsSpreadOverTheMonthsWorkMinutes() {
        val plan = PayPlan(3000.0, PayUnit.MONTH, weekdays)
        // November 2026 has 21 weekdays: a usual month pays exactly the monthly amount.
        val month = Pay.month(plan, monday, emptyMap(), at(LocalDate(2026, 12, 1), 0))
        assertEquals(3000.0, month.total, 0.001)
        assertEquals(3000.0, month.earned, 0.001)
        // Half way through the 9th, 5 weekdays and a half are earned.
        assertEquals(3000.0 / 21 * 5.5, Pay.month(plan, monday, emptyMap(), at(monday, 13)).earned, 0.001)

        // A paid day off pays as usual, all of it from the start of the day; an unpaid one pays nothing.
        val paidOff = mapOf(monday to WorkDay(monday, paid = true))
        assertEquals(3000.0, Pay.month(plan, monday, paidOff, at(monday, 0, 1)).total, 0.001)
        assertEquals(1.0, Pay.day(plan, monday, paidOff[monday]).done(at(monday, 0, 1)))
        val unpaid = mapOf(monday to WorkDay(monday))
        assertEquals(3000.0 - 3000.0 / 21, Pay.month(plan, monday, unpaid, at(monday, 0)).total, 0.001)

        // Working half a day pays for half a day; working a Saturday pays extra.
        val half = WorkDay(monday, WorkHours(9 * 60, 13 * 60))
        assertEquals(3000.0 / 21 / 2, Pay.day(plan, monday, half).total, 0.001)
        val saturday = monday.plusDays(5)
        assertEquals(3000.0 / 21, Pay.day(plan, saturday, WorkDay(saturday, nineToFive)).total, 0.001)
    }

    @Test
    fun dailyWeeklyAndYearlyPay() {
        val daily = PayPlan(120.0, PayUnit.DAY, weekdays)
        assertEquals(120.0, Pay.day(daily, monday).total, 0.0001)
        assertEquals(120.0, Pay.day(daily, monday, WorkDay(monday, WorkHours(10 * 60, 12 * 60))).total, 0.0001)
        assertEquals(15.0, Pay.hourly(daily)!!, 0.0001)

        val weekly = PayPlan(800.0, PayUnit.WEEK, weekdays)
        assertEquals(160.0, Pay.day(weekly, monday).total, 0.0001)
        assertEquals(20.0, Pay.hourly(weekly)!!, 0.0001)

        val yearly = PayPlan(52_000.0, PayUnit.YEAR, weekdays, takeHome = 75)
        assertEquals(18.75, Pay.hourly(yearly)!!, 0.0001) // 25 an hour, 75% kept
        assertEquals(30, Pay.workMinutes(937, 18.75)) // 9.37 is half an hour
        assertEquals(1, Pay.workMinutes(1, 18.75))

        assertNull(Pay.hourly(PayPlan(3000.0, PayUnit.MONTH)))
        assertFalse(PayPlan(3000.0, PayUnit.MONTH).complete)
        assertTrue(weekly.complete)
    }

    @Test
    fun aNightShiftEndsTheNextDay() {
        val night = WorkHours(22 * 60, 6 * 60)
        assertEquals(8 * 60, night.span)
        val plan = PayPlan(10.0, PayUnit.HOUR, List(7) { night })
        val day = Pay.day(plan, monday)
        assertEquals(80.0, day.total, 0.0001)
        assertEquals(40.0, day.earned(at(monday.plusDays(1), 2)), 0.0001)
        assertEquals(0.0, day.earned(at(monday, 21)), 0.0001)
    }

    @Test
    fun afterMidnightTheNightShiftStillGoingIsShown() {
        val night = WorkHours(22 * 60, 6 * 60)
        val plan = PayPlan(10.0, PayUnit.HOUR, List(7) { if (it < 5) night else null })
        val tuesday = monday.plusDays(1)
        // Tuesday 2 am: Monday's shift, half done.
        val shift = Pay.current(plan, at(tuesday, 2), emptyMap())
        assertEquals(monday, shift.day)
        assertEquals(0.5, shift.done(at(tuesday, 2)), 0.0001)
        // Once it's over, Tuesday's own, which starts at 10 pm.
        assertEquals(tuesday, Pay.current(plan, at(tuesday, 6), emptyMap()).day)
        assertEquals(tuesday, Pay.current(plan, at(tuesday, 23), emptyMap()).day)
        // Saturday 2 am: Friday's shift, though Saturday is a day off.
        val saturday = monday.plusDays(5)
        assertEquals(monday.plusDays(4), Pay.current(plan, at(saturday, 2), emptyMap()).day)
        // A day shift is over by midnight; a changed day off yesterday has nothing still going.
        val days = PayPlan(10.0, PayUnit.HOUR, weekdays)
        assertEquals(tuesday, Pay.current(days, at(tuesday, 2), emptyMap()).day)
        assertEquals(tuesday, Pay.current(plan, at(tuesday, 2), mapOf(monday to WorkDay(monday))).day)
        // Yesterday's change counts: a later shift that runs to 8 am.
        val late = mapOf(monday to WorkDay(monday, WorkHours(23 * 60, 8 * 60)))
        assertEquals(monday, Pay.current(plan, at(tuesday, 7), late).day)
    }

    @Test
    fun newBillsGoUnderBillsEvenOnceItIsRenamed() {
        val categories = Budget.STARTER.mapIndexed { i, it -> it.copy(id = i + 1L) }
        assertEquals("Bills", Budget.billsCategory(categories)?.name)
        val renamed = categories.map { if (it.name == "Bills") it.copy(name = "Utilities") else it }
        assertEquals("Utilities", Budget.billsCategory(renamed)?.name)
        assertNull(Budget.billsCategory(renamed.map { if (it.name == "Utilities") it.copy(archived = true) else it }))
    }

    @Test
    fun thePayPlanIsStoredAsText() {
        val plan = PayPlan(18.5, PayUnit.HOUR, weekdays, takeHome = 85)
        assertEquals(plan, PayPlan.decode(PayPlan.encode(plan)))
        assertNull(PayPlan.decode(null))
        assertNull(PayPlan.decode("not json"))
        assertNull(PayPlan.decode("""{"amount":1.0,"unit":"HOUR","week":[]}"""))
    }

    @Test
    fun aMonthAddsUpByCategory() {
        val food = MoneyCategory(id = 1, name = "Food", icon = "🍎", limitCents = 20_000, position = 0)
        val fun_ = MoneyCategory(id = 2, name = "Fun", icon = "🎉", position = 1)
        val old = MoneyCategory(id = 3, name = "Old", icon = "📦", position = 2, archived = true)
        val entries = listOf(
            MoneyEntry(kind = MoneyKind.SPENT, cents = 15_000, categoryId = 1, day = monday),
            MoneyEntry(kind = MoneyKind.SPENT, cents = 7_550, categoryId = 1, day = monday.plusDays(1)),
            MoneyEntry(kind = MoneyKind.SPENT, cents = 2_000, categoryId = 2, day = monday),
            MoneyEntry(kind = MoneyKind.RECEIVED, cents = 5_000, day = monday),
            MoneyEntry(kind = MoneyKind.SPENT, cents = 99_999, categoryId = 2, day = LocalDate(2026, 10, 31)),
        )
        val plan = PayPlan(3000.0, PayUnit.MONTH, weekdays)
        val month = Budget.month(Budget.monthOf(monday), plan, emptyMap(), entries, listOf(old, fun_, food), at(LocalDate(2026, 12, 1), 0))
        assertEquals(LocalDate(2026, 11, 1), month.first)
        assertEquals(24_550, month.spentCents)
        assertEquals(5_000, month.receivedCents)
        assertEquals(listOf("Food", "Fun"), month.categories.map { it.category.name })
        assertEquals(22_550, month.categories[0].cents)
        assertTrue(month.categories[0].over)
        assertFalse(month.categories[1].over)
        assertEquals(3000.0 + 50.0 - 245.5, month.leftAtEnd, 0.001)
        assertEquals(3000.0 + 50.0 - 245.5, month.leftNow, 0.001)
        assertEquals(1234, Budget.cents(12.34)) // to the nearest cent, though 12.34 isn't exact as a double
        assertEquals(1250, Budget.cents(12.5))
        assertEquals(1, Budget.STARTER.count { it.role == CategoryRole.REWARDS })
    }
}
