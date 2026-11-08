package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DescribeTest {
    private val start = LocalDate(2026, 11, 2)

    @Test
    fun repeatsInPlainWords() {
        assertEquals("Every day", Repeat.everyDay(start).describe())
        assertEquals("Every 3 days", Repeat(RepeatKind.EVERY_N_DAYS, start, interval = 3).describe())
        assertEquals("Mon, Wed, Fri", Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(5, 1, 3)).describe())
        assertEquals("Weekdays", Repeat(RepeatKind.WEEKDAYS, start, weekdays = (1..5).toSet()).describe())
        assertEquals("Every 2 weeks: Tue", Repeat(RepeatKind.WEEKDAYS, start, interval = 2, weekdays = setOf(2)).describe())
        assertEquals("3 times a week", Repeat.timesPer(Period.WEEKLY, start).describe(target = 3))
        assertEquals("Once a month", Repeat.timesPer(Period.MONTHLY, start).describe(target = 1))
        assertEquals(
            "The 1st, 15th and last day of every month",
            Repeat(RepeatKind.MONTH_DATES, start, monthDays = setOf(Repeat.LAST_DAY, 15, 1)).describe(),
        )
        assertEquals(
            "The last Fri of every 2 months",
            Repeat(RepeatKind.MONTH_WEEKDAY, start, interval = 2, weekdays = setOf(5), weekOfMonth = Repeat.LAST_WEEK).describe(),
        )
        assertEquals("Every year on 25 Dec", Repeat(RepeatKind.YEARLY, start, month = 12, day = 25).describe())
        assertEquals("Once, on 2 Nov 2026", Repeat(RepeatKind.ONCE, start).describe())
        assertEquals("Every day, until 30 Nov 2026", Repeat(RepeatKind.EVERY_N_DAYS, start, end = LocalDate(2026, 11, 30)).describe())
    }

    @Test
    fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "31st"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 31).map { ordinal(it) })
    }
}
