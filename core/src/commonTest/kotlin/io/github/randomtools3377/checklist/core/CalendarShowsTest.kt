package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the calendar widget shows and leaves out, by the switches under "Calendar widget shows". */
class CalendarShowsTest {
    private val start = LocalDate(2026, 1, 1)
    private val wednesday = LocalDate(2026, 11, 11)
    private val friday = LocalDate(2026, 11, 13)
    private val noon = LocalDateTime(wednesday, LocalTime(12, 0))

    private fun habit(id: Long, name: String, repeat: Repeat = Repeat.everyDay(start)) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = 1, repeat = repeat, position = id.toInt())

    private val water = habit(1, "Water")
    private val gym = habit(2, "Gym", Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(5)))
    private val swim = habit(3, "Swim", Repeat.timesPer(Period.WEEKLY, start))
    private val shed = Goal(id = 4, kind = GoalKind.PROJECT, name = "Paint the shed", icon = "🎨", points = 0, repeat = null, deadline = friday)
    private val letter = Goal(id = 5, kind = GoalKind.TODO, name = "Post the letter", icon = "✅", points = 2, repeat = Repeat(RepeatKind.ONCE, friday), position = 9)
    private val milk = Goal(id = 6, kind = GoalKind.TODO, name = "Buy milk", icon = "✅", points = 2, repeat = null)
    private val goals = listOf(water, gym, swim, shed, letter, milk)
    private val events = listOf(Busy(LocalDateTime(friday, LocalTime(10, 0)), LocalDateTime(friday, LocalTime(11, 0)), "Dentist"))
    private val steps = listOf(Step(id = 40, goalId = 4, name = "Sand", points = 1, position = 1))

    private fun days(shows: CalendarShows) = shows.days(CalendarView.days(wednesday, noon, 7, goals, steps, emptyList(), events = events), wednesday)
    private fun names(shows: CalendarShows, day: LocalDate) = days(shows).firstOrNull { it.date == day }?.lines?.map { it.name }
    private fun only(vararg off: CalendarShow) = CalendarShows(CalendarShows().on - off.toSet())

    @Test
    fun toStartEverythingShowsButTheCountUnderEachDay() {
        val shows = CalendarShows()
        assertFalse(shows.counts)
        assertTrue(shows.events && shows.deadlines && shows.todos && shows.dailyGoals)
        assertEquals(2, shows.labelLines)
        assertEquals(1, only().copy(on = CalendarShow.entries.toSet()).labelLines)
        assertEquals(listOf("Water", "Swim", "Buy milk"), names(shows, wednesday))
        assertEquals(listOf("Paint the shed", "Dentist", "Water", "Gym", "Post the letter"), names(shows, friday))
    }

    @Test
    fun settingsTurnEachOneOnOrOff() {
        assertEquals(CalendarShows(), CalendarShows.of(emptyMap()))
        val chosen = CalendarShows.of(mapOf(CalendarShow.COUNTS.key to "1", CalendarShow.EVENTS.key to "0", CalendarShow.TODOS.key to "x"))
        assertTrue(chosen.counts)
        assertFalse(chosen.events)
        assertTrue(chosen.todos)
        assertEquals("0", CalendarShow.DAILY_GOALS.valueOf(false))
        assertEquals(CalendarShow.entries.size, CalendarShow.entries.map { it.key }.toSet().size)
    }

    @Test
    fun eachSwitchLeavesItsOwnKindOutOfTheAgenda() {
        assertEquals(listOf("Paint the shed", "Water", "Gym", "Post the letter"), names(only(CalendarShow.EVENTS), friday))
        assertEquals(listOf("Dentist", "Water", "Gym", "Post the letter"), names(only(CalendarShow.DEADLINES), friday))
        assertEquals(listOf("Paint the shed", "Dentist", "Water", "Gym"), names(only(CalendarShow.TODOS), friday))
        assertEquals(listOf("Paint the shed", "Dentist", "Gym", "Post the letter"), names(only(CalendarShow.DAILY_GOALS), friday))
        assertEquals(
            listOf("Gym"),
            names(only(CalendarShow.EVENTS, CalendarShow.DEADLINES, CalendarShow.TODOS, CalendarShow.DAILY_GOALS), friday),
        )
    }

    @Test
    fun aToDoWithoutADateAndAGoalForSomeDaysAlwaysStay() {
        val bare = only(CalendarShow.TODOS, CalendarShow.DAILY_GOALS)
        // Today keeps the goal counted per week and the to-do that waits on today's list.
        assertEquals(listOf("Swim", "Buy milk"), names(bare, wednesday))
        val swimLine = days(bare)[0].lines.first { it.name == "Swim" }
        assertTrue(swimLine.canTick)
        // With daily goals on, today's goals keep their tick buttons.
        assertTrue(days(CalendarShows())[0].lines.first { it.name == "Water" }.canTick)
    }

    @Test
    fun aDayWithNothingLeftIsDroppedButTodayStays() {
        val none = CalendarShows(emptySet())
        val left = shows(none)
        assertEquals(listOf(wednesday, friday), left.map { it.date })
        // Only Gym on Friday; Thursday had only the daily goal.
        assertEquals(listOf("Gym"), left[1].lines.map { it.name })
        val onlyDaily = CalendarShows(emptySet()).days(CalendarView.days(wednesday, noon, 3, listOf(water), emptyList(), emptyList()), wednesday)
        assertEquals(listOf(wednesday), onlyDaily.map { it.date })
        assertTrue(onlyDaily[0].lines.isEmpty())
        // What the day counts as due stays the whole day's.
        assertEquals(1, onlyDaily[0].due)
        // It says so, rather than that nothing is due.
        assertEquals("What's on this day is hidden", onlyDaily[0].emptyNote)
        assertEquals("Nothing due today", CalendarView.days(wednesday, noon, 1, emptyList(), emptyList(), emptyList())[0].emptyNote)
    }

    private fun shows(shows: CalendarShows) = days(shows)

    @Test
    fun everyDayMeansEverySingleDay() {
        assertTrue(Repeat.everyDay(start).isEveryDay)
        assertTrue(Repeat(RepeatKind.WEEKDAYS, start, weekdays = (1..7).toSet()).isEveryDay)
        assertFalse(Repeat(RepeatKind.EVERY_N_DAYS, start, interval = 2).isEveryDay)
        assertFalse(Repeat(RepeatKind.WEEKDAYS, start, weekdays = (1..5).toSet()).isEveryDay)
        assertFalse(Repeat(RepeatKind.WEEKDAYS, start, weekdays = (1..7).toSet(), interval = 2).isEveryDay)
        assertFalse(Repeat.timesPer(Period.WEEKLY, start).isEveryDay)
    }

    @Test
    fun theMonthsLabelsFollowTheSwitches() {
        val all = CalendarView.labels(wednesday, friday.plusDays(7), goals, events)
        assertEquals(
            listOf(DayLabel(LineKind.EVENT, "Dentist"), DayLabel(LineKind.DEADLINE, "Paint the shed"), DayLabel(LineKind.TODO, "Post the letter")),
            all[friday],
        )
        // Goals are never written in the month.
        assertEquals(setOf(friday), all.keys)
        assertEquals(all, CalendarShows().labels(all))
        assertEquals(listOf(LineKind.DEADLINE, LineKind.TODO), only(CalendarShow.EVENTS).labels(all)[friday]?.map { it.kind })
        assertEquals(listOf(LineKind.EVENT, LineKind.TODO), only(CalendarShow.DEADLINES).labels(all)[friday]?.map { it.kind })
        assertEquals(listOf(LineKind.EVENT, LineKind.DEADLINE), only(CalendarShow.TODOS).labels(all)[friday]?.map { it.kind })
        assertEquals(all, only(CalendarShow.DAILY_GOALS).labels(all))
        // A day with nothing left has no entry.
        assertTrue(only(CalendarShow.EVENTS, CalendarShow.DEADLINES, CalendarShow.TODOS).labels(all).isEmpty())
    }

    @Test
    fun aCellWritesTwoThingsBeforeCountingTheRest() {
        val on = listOf(DayLabel(LineKind.EVENT, "Dentist"), DayLabel(LineKind.DEADLINE, "Paint the shed"), DayLabel(LineKind.TODO, "Post the letter"))
        assertEquals(emptyList(), CalendarView.cellLabels(emptyList(), 2, marks = false))
        assertEquals(listOf("Dentist"), CalendarView.cellLabels(on.take(1), 2, marks = false).map { it.name })
        // Two fit on two lines, each cut to the cell; a deadline keeps its flag.
        assertEquals(listOf("Dentist", "🚩Paint t"), CalendarView.cellLabels(on.take(2), 2, marks = false).map { it.name })
        // A third is counted on the last line.
        assertEquals(listOf("Dentist", "🚩Pain +1"), CalendarView.cellLabels(on, 2, marks = false).map { it.name })
        // With the count shown there is one line, as before.
        assertEquals(listOf("Denti +2"), CalendarView.cellLabels(on, 1, marks = false).map { it.name })
        assertEquals(listOf(LineKind.EVENT, LineKind.DEADLINE), CalendarView.cellLabels(on, 2, marks = false).map { it.kind })
        assertTrue(CalendarView.cellLabels(on, 2, marks = true).all { it.name.length <= CalendarView.LABEL_CHARS + 1 })
    }

    @Test
    fun colourBlindMarksTellTheKindsApart() {
        val on = listOf(DayLabel(LineKind.EVENT, "Dentist"), DayLabel(LineKind.TODO, "Post the letter"), DayLabel(LineKind.DEADLINE, "Shed"))
        assertEquals(listOf("▪Dentist", "○Post th", "🚩Shed"), CalendarView.cellLabels(on, 3, marks = true).map { it.name })
        assertEquals(3, on.map { it.mark(true) }.toSet().size)
        // An emoji is never cut in half.
        val cut = CalendarView.cellLabels(listOf(DayLabel(LineKind.TODO, "Party 🎉🎉🎉")), 1, marks = false).single().name
        assertFalse(cut.last().isHighSurrogate())
    }
}
