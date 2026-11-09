package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BusyTest {
    private val monday = LocalDate(2026, 11, 9)
    private fun at(h: Int, m: Int = 0) = LocalDateTime(2026, 11, 9, h, m)

    @Test
    fun overlappingEventsCountOnceAndAllDayOnesNotAtAll() {
        val busy = listOf(
            Busy(at(10), at(11), "Meeting"),
            Busy(at(10, 30), at(12), "Call"),
            Busy(at(15), at(16), "Dentist"),
            Busy(at(0), LocalDateTime(2026, 11, 10, 0, 0), "Birthday", allDay = true),
        )
        assertEquals(120 + 30, BusyTime.minutes(busy, at(9), at(15, 30)))
        assertEquals(0, BusyTime.minutes(busy, at(12), at(15)))
        assertEquals(listOf("Birthday", "Meeting", "Call", "Dentist"), BusyTime.on(busy, monday, 4).map { it.title })
    }

    @Test
    fun pacingLeavesOutBusyTime() {
        val goals = (1..2).map { Goal(id = it.toLong(), kind = GoalKind.HABIT, name = "G$it", icon = "⭐", points = 1, repeat = Repeat.everyDay(monday)) }
        val rows = TodayList.build(monday, goals, emptyList(), emptyList(), 0).dueToday
        val settings = PaceSettings(dayEndHour = 4, defaultFinish = LocalTime(13, 0))
        val free = Pace.today(at(9), monday, rows, settings)!!
        assertEquals(120, free.minutesEach) // 4 hours for 2
        val busy = Pace.today(at(9), monday, rows, settings, listOf(Busy(at(10), at(12), "Meeting")))!!
        assertEquals(60, busy.minutesEach) // 2 hours left for 2
        assertEquals(120, busy.busyMinutes)
    }
}
