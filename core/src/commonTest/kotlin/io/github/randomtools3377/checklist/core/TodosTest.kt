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

    @Test
    fun aToDosStepsStayTickedUntilTheLastDoesIt() = runTest {
        val move = store.addGoal(Todos.of("Move the desk", points = 6))
        val steps = listOf("Clear it", "Carry it", "Plug in").mapIndexed { i, name ->
            store.addStep(Step(goalId = move.id, name = name, points = 0, position = i, archived = false))
        }
        lapses.closeDays()
        assertEquals(TickResult.Ticked(0, 0, false), book.checkItem(steps[0].id))
        assertEquals(0, book.balance())

        // Steps carry over to the next day.
        at(10)
        lapses.closeDays()
        val row = TodayList.build(LocalDate(2026, 11, 10), store.goals, store.steps, store.ticks, 0).todos.single()
        assertEquals(listOf(true, false, false), row.items.map { it.done })
        book.checkItem(steps[1].id)
        val done = book.checkItem(steps[2].id) as TickResult.Ticked
        assertTrue(done.finished)
        assertEquals(6, book.balance())

        // Taking a step back takes the to-do back.
        assertTrue(book.uncheckItem(steps[2].id))
        assertEquals(0, book.balance())
        // Done ticks the rest; undo takes back the to-do and its steps.
        assertTrue(book.finishRoutine(move.id) is TickResult.Ticked)
        assertEquals(6, book.balance())
        assertTrue(book.undoRoutine(move.id))
        assertEquals(0, book.balance())
        assertTrue(store.stepTicks(move.id).isEmpty())
    }

    @Test
    fun theDoneListHoldsFinishedProjectsAndDoneToDosNewestFirst() = runTest {
        val call = store.addGoal(Todos.of("Call the dentist"))
        val open = store.addGoal(Todos.of("Fix the gate"))
        val shelf = store.addGoal(Goal(kind = GoalKind.PROJECT, name = "Shelf", icon = "🪚", points = 0, repeat = null))
        val step = store.addStep(Step(goalId = shelf.id, name = "Build it", points = 3, position = 0, archived = false))
        lapses.closeDays()
        book.tickStep(step.id)
        at(10)
        book.tickHabit(call.id)
        val done = DoneList.of(store.goals, store.steps, store.ticks)
        assertEquals(listOf(call.id, shelf.id), done.map { it.goal.id })
        assertEquals(LocalDate(2026, 11, 10), done.first().day)
        assertTrue(done.none { it.goal.id == open.id })
        // The widget shows to-dos without a date at the end of the agenda.
        val agenda = CalendarView.agenda(LocalDate(2026, 11, 10), store.goals, store.ticks)
        assertEquals(listOf(true, false), agenda.map { it.done })
    }
}
