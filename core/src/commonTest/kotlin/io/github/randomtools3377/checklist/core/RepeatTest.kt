package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepeatTest {
    private val start = LocalDate(2026, 11, 2) // a Monday

    private fun dueDays(repeat: Repeat, from: LocalDate, days: Int): List<Int> =
        (0 until days).map { from.plusDays(it) }.filter { repeat.isDueOn(it) }.map { it.dayOfMonth }

    @Test
    fun everyDayAndEveryFewDays() {
        assertEquals(listOf(2, 3, 4, 5, 6), dueDays(Repeat.everyDay(start), start, 5))
        val everyThird = Repeat(RepeatKind.EVERY_N_DAYS, start, interval = 3)
        assertEquals(listOf(2, 5, 8, 11), dueDays(everyThird, start, 12))
    }

    @Test
    fun nothingIsDueBeforeTheStartOrAfterTheEnd() {
        val repeat = Repeat(RepeatKind.EVERY_N_DAYS, start, end = LocalDate(2026, 11, 4))
        assertFalse(repeat.isDueOn(LocalDate(2026, 11, 1)))
        assertEquals(listOf(2, 3, 4), dueDays(repeat, LocalDate(2026, 11, 1), 10))
    }

    @Test
    fun chosenDaysOfTheWeek() {
        val monWedFri = Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1, 3, 5))
        assertEquals(listOf(2, 4, 6, 9, 11, 13), dueDays(monWedFri, start, 14))
    }

    @Test
    fun everyOtherWeekCountsFromTheStartWeek() {
        val tuesdays = Repeat(RepeatKind.WEEKDAYS, start, interval = 2, weekdays = setOf(2))
        assertEquals(listOf(3, 17), dueDays(tuesdays, start, 28))
    }

    @Test
    fun datesOfTheMonthIncludingTheLastDay() {
        val repeat = Repeat(RepeatKind.MONTH_DATES, LocalDate(2026, 1, 1), monthDays = setOf(1, 15, Repeat.LAST_DAY))
        assertEquals(listOf(1, 15, 28), dueDays(repeat, LocalDate(2026, 2, 1), 28))
    }

    @Test
    fun aDateTheMonthDoesNotHaveFallsOnItsLastDay() {
        val the31st = Repeat(RepeatKind.MONTH_DATES, LocalDate(2026, 1, 1), monthDays = setOf(31))
        assertEquals(listOf(30), dueDays(the31st, LocalDate(2026, 11, 1), 30))
    }

    @Test
    fun everyFewMonths() {
        val quarterly = Repeat(RepeatKind.MONTH_DATES, LocalDate(2026, 1, 1), interval = 3, monthDays = setOf(10))
        assertTrue(quarterly.isDueOn(LocalDate(2026, 4, 10)))
        assertFalse(quarterly.isDueOn(LocalDate(2026, 5, 10)))
        assertTrue(quarterly.isDueOn(LocalDate(2026, 7, 10)))
    }

    @Test
    fun firstMondayAndLastFridayOfTheMonth() {
        val firstMonday = Repeat(RepeatKind.MONTH_WEEKDAY, LocalDate(2026, 1, 1), weekdays = setOf(1), weekOfMonth = 1)
        assertEquals(listOf(7), dueDays(firstMonday, LocalDate(2026, 12, 1), 31))
        val lastFriday = Repeat(RepeatKind.MONTH_WEEKDAY, LocalDate(2026, 1, 1), weekdays = setOf(5), weekOfMonth = Repeat.LAST_WEEK)
        assertEquals(listOf(25), dueDays(lastFriday, LocalDate(2026, 12, 1), 31))
    }

    @Test
    fun yearlyAndOnce() {
        val birthday = Repeat(RepeatKind.YEARLY, LocalDate(2026, 1, 1), month = 2, day = 29)
        assertTrue(birthday.isDueOn(LocalDate(2027, 2, 28))) // no 29th in 2027
        assertTrue(birthday.isDueOn(LocalDate(2028, 2, 29)))
        assertFalse(birthday.isDueOn(LocalDate(2028, 2, 28)))

        val once = Repeat(RepeatKind.ONCE, LocalDate(2026, 11, 20))
        assertEquals(listOf(20), dueDays(once, LocalDate(2026, 11, 1), 30))
    }

    @Test
    fun timesPerHasAWindowForTheWholePeriod() {
        val twiceAMonth = Repeat.timesPer(Period.MONTHLY, start)
        assertEquals(Window(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30), Period.MONTHLY), twiceAMonth.window(LocalDate(2026, 11, 20)))
        assertNull(Repeat.timesPer(Period.WEEKLY, LocalDate(2026, 12, 1)).window(LocalDate(2026, 11, 20)))
    }

    @Test
    fun nextDueLooksAhead() {
        val firstMonday = Repeat(RepeatKind.MONTH_WEEKDAY, LocalDate(2026, 1, 1), weekdays = setOf(1), weekOfMonth = 1)
        assertEquals(LocalDate(2026, 12, 7), firstMonday.nextDue(LocalDate(2026, 11, 3)))
        assertNull(Repeat(RepeatKind.ONCE, LocalDate(2026, 11, 1)).nextDue(LocalDate(2026, 11, 2)))
    }
}
