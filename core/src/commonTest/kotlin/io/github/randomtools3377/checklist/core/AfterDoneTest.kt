package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AfterDoneTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0)) // Monday
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val lapses = Lapses(store, book)
    private val monday = LocalDate(2026, 11, 9)

    private fun on(day: Int) = clock.set(LocalDateTime(2026, 11, day, 10, 0))
    private fun repeatOf(id: Long) = store.goals.first { it.id == id }.repeat!!

    @Test
    fun theNextOneCountsFromWhenItIsDone() = runTest {
        val plants = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, monday, interval = 5)))
        lapses.closeDays()
        assertEquals(3, (book.tickHabit(plants.id) as TickResult.Ticked).points)
        assertEquals(monday.plusDays(5), repeatOf(plants.id).start)
        // Still shows today, done.
        assertTrue(TodayList.build(monday, store.goals, emptyList(), store.ticks, 0).dueToday.single().done)
        assertTrue(TodayList.build(monday.plusDays(1), store.goals, emptyList(), store.ticks, 0).dueToday.isEmpty())

        // Undone: due today again.
        assertTrue(book.undoHabitTick(plants.id))
        assertEquals(monday, repeatOf(plants.id).start)
        assertEquals(0, book.balance())
        assertEquals("5 days after it's done, next on 9 Nov 2026", repeatOf(plants.id).describe())
    }

    @Test
    fun aNextDayAlreadyGoneMovesToTodayWithoutAMiss() = runTest {
        // Its "Next due" was left on a day before the days looked at, so it would never come round.
        val plants = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, LocalDate(2026, 10, 1), interval = 3)))
        lapses.closeDays()
        on(10)
        assertTrue(lapses.closeDays().isEmpty())
        assertEquals(LocalDate(2026, 11, 10), repeatOf(plants.id).start)
        assertNull(repeatOf(plants.id).lateSince)
        assertTrue(TodayList.build(LocalDate(2026, 11, 10), store.goals, emptyList(), store.ticks, 0).dueToday.isNotEmpty())
    }

    @Test
    fun doneOnTheDayItWasSwitchedToThisTheNextCountsFromThatDay() = runTest {
        // Ticked on Monday, then switched to "5 days after it's done" with Monday as its day.
        val plants = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, monday, interval = 5)))
        lapses.closeDays()
        store.addTick(Tick(goalId = plants.id, stepId = null, at = 0, day = monday, period = Period.DAILY, periodStart = monday))
        assertTrue(TodayList.build(monday, store.goals, emptyList(), store.ticks, 0).dueToday.single().done)
        on(10)
        assertTrue(lapses.closeDays().isEmpty())
        assertEquals(monday.plusDays(5), repeatOf(plants.id).start)
        assertNull(repeatOf(plants.id).lateSince)
    }

    @Test
    fun notDoneOnItsDayIsOneMissThenItWaitsOnToday() = runTest {
        val plants = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, monday, interval = 5)))
        lapses.closeDays()
        on(12) // Thursday: Monday, Tuesday and Wednesday have ended
        val missed = lapses.closeDays()
        assertEquals(listOf(monday), missed.map { it.day }) // only the day it was due
        assertEquals(LocalDate(2026, 11, 12), repeatOf(plants.id).start)
        assertEquals(monday, repeatOf(plants.id).lateSince)
        assertTrue(TodayList.build(LocalDate(2026, 11, 12), store.goals, emptyList(), store.ticks, 0).dueToday.isNotEmpty())

        // Done on Thursday: the next one is 5 days from Thursday, and it isn't late any more.
        book.tickHabit(plants.id)
        assertEquals(LocalDate(2026, 11, 17), repeatOf(plants.id).start)
        assertNull(repeatOf(plants.id).lateSince)
        // Undone again: still late, so the end of Thursday is no new miss.
        book.undoHabitTick(plants.id)
        assertEquals(monday, repeatOf(plants.id).lateSince)
        on(13)
        assertTrue(lapses.closeDays().isEmpty())
    }
}
