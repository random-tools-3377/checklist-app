package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodosTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0)) // Monday
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val lapses = Lapses(store, book)
    private val monday = LocalDate(2026, 11, 9)

    private fun at(day: Int) = clock.set(LocalDateTime(2026, 11, day, 10, 0))

    @Test
    fun anUndatedToDoWaitsUntilDoneAndThenTakesThatDay() = runTest {
        val call = store.addGoal(Todos.of("Call the dentist"))
        lapses.closeDays() // starts tracking
        assertEquals(listOf(TodoRow(call, false)), TodayList.build(monday, store.goals, emptyList(), store.ticks, 0).todos)

        at(10)
        assertTrue(lapses.closeDays().isEmpty()) // never missed
        val result = book.tickHabit(call.id) as TickResult.Ticked
        assertEquals(Todos.POINTS, result.points)
        assertEquals(TickResult.AlreadyDone, book.tickHabit(call.id))
        assertEquals(true, TodayList.build(monday.plusDays(1), store.goals, emptyList(), store.ticks, 0).todos.single().done)

        // Undone and done again the same day.
        assertTrue(book.undoHabitTick(call.id))
        assertEquals(0, book.balance())
        book.tickHabit(call.id)

        at(11)
        lapses.closeDays()
        val dated = store.goals.first { it.id == call.id }
        assertEquals(LocalDate(2026, 11, 10), Todos.dateOf(dated))
        val tuesday = TodayList.build(LocalDate(2026, 11, 10), store.goals, emptyList(), store.ticks, 0)
        assertTrue(tuesday.dueToday.single().done)
        assertTrue(tuesday.todos.isEmpty())
    }

    @Test
    fun aDatedToDoLeftUndoneIsAMissAndToDosShareOneAllowance() = runTest {
        val bins = store.addGoal(Todos.of("Put the bins out", date = monday))
        val form = store.addGoal(Todos.of("Send the form", date = monday))
        lapses.closeDays()
        assertEquals("Put the bins out", TodayList.build(monday, store.goals, emptyList(), store.ticks, 0).dueToday.first().goal.name)

        at(10)
        val missed = lapses.closeDays()
        assertEquals(setOf(bins.id, form.id), missed.map { it.goalId }.toSet())
        // One miss a week is allowed between all to-dos, so the second goes over the limit.
        assertEquals(1, missed.count { it.overLimit })
        assertEquals(-1, book.balance()) // half of 2 points, rounded down
    }

    @Test
    fun toDosTravelInTheBackup() {
        val todos = listOf(Todos.of("Call the dentist").copy(id = 1), Todos.of("Send the form", points = 5, date = monday).copy(id = 2))
        val back = Backup.decode(Backup.encode(Backup(exportedAt = 0, goals = todos, steps = emptyList(), ticks = emptyList(), entries = emptyList(), rewards = emptyList(), purchases = emptyList(), settings = emptyMap())))
        assertEquals(todos, back.goals)
        assertEquals(null, Todos.dateOf(back.goals[0]))
        assertEquals(monday, Todos.dateOf(back.goals[1]))
    }
}
