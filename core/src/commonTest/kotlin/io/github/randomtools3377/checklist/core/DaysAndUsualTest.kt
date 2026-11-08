package io.github.randomtools3377.checklist.core

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class DaysTest {
    @Test
    fun theDayEndsAtTheChosenHour() {
        val lateNight = LocalDateTime(2026, 11, 10, 3, 59)
        assertEquals(LocalDate(2026, 11, 9), Days.dayOf(lateNight, dayEndHour = 4))
        assertEquals(LocalDate(2026, 11, 10), Days.dayOf(lateNight, dayEndHour = 0))
        assertEquals(LocalDate(2026, 11, 10), Days.dayOf(LocalDateTime(2026, 11, 10, 4, 0), dayEndHour = 4))
    }

    @Test
    fun weeksStartOnMondayAndMonthsOnTheFirst() {
        val sunday = LocalDate(2026, 11, 15)
        assertEquals(LocalDate(2026, 11, 9), Days.periodStart(sunday, Period.WEEKLY))
        assertEquals(sunday, Days.periodEnd(sunday, Period.WEEKLY))
        assertEquals(LocalDate(2026, 11, 1), Days.periodStart(sunday, Period.MONTHLY))
        assertEquals(LocalDate(2026, 11, 30), Days.periodEnd(sunday, Period.MONTHLY))
        assertEquals(sunday, Days.periodStart(sunday, Period.DAILY))
    }

    @Test
    fun theEarliestOpenDayCoversTheYearAndAWeekThatStartedLastYear() {
        assertEquals(LocalDate(2026, 1, 1), Days.earliestOpenDay(LocalDate(2026, 12, 1)))
        // Friday 1 January 2027: its week started on Monday 28 December 2026.
        assertEquals(LocalDate(2026, 12, 28), Days.earliestOpenDay(LocalDate(2027, 1, 1)))
    }

    @Test
    fun yearsRunFromJanuaryToDecember() {
        val day = LocalDate(2026, 6, 15)
        assertEquals(LocalDate(2026, 1, 1), Days.periodStart(day, Period.YEARLY))
        assertEquals(LocalDate(2026, 12, 31), Days.periodEnd(day, Period.YEARLY))
    }
}

class UsualTest {
    private val today = LocalDate(2026, 11, 20)

    @Test
    fun nothingEarnedMeansNoUsualYet() {
        assertNull(Usual.pointsPerDay(emptyMap(), firstDay = null, today = today))
        assertNull(Usual.describe(100, null))
    }

    @Test
    fun theUsualIsTheAverageOfFinishedDaysCountingEmptyDaysAsZero() {
        val firstDay = today.minusDays(4)
        val earned = mapOf(firstDay to 40, today.minusDays(2) to 40, today to 500)
        // Four finished days (16th to 19th): 80 points, so 20 a day. Today does not count yet.
        assertEquals(20.0, Usual.pointsPerDay(earned, firstDay, today)!!, 0.001)
    }

    @Test
    fun onTheFirstDayTodaySoFarIsTheUsual() {
        assertEquals(30.0, Usual.pointsPerDay(mapOf(today to 30), today, today)!!, 0.001)
    }

    @Test
    fun onlyTheLastFourWeeksCount() {
        val earned = mapOf(today.minusDays(100) to 10_000, today.minusDays(1) to 280)
        assertEquals(10.0, Usual.pointsPerDay(earned, today.minusDays(100), today)!!, 0.001)
    }

    @Test
    fun describesThePriceInDaysOfTheUsual() {
        assertEquals("≈ 4 days of your usual", Usual.describe(80, 20.0))
        assertEquals("≈ 1 day of your usual", Usual.describe(18, 20.0))
        assertEquals("less than a day of your usual", Usual.describe(10, 20.0))
    }
}
