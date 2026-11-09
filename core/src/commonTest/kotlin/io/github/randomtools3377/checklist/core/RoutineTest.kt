package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A routine: a habit with a checklist inside, done and paid when every item is ticked. */
class RoutineTest {
    private val day = LocalDate(2026, 11, 11)
    private val store = MemoryStore()
    private val book = Book(store, TestClock(LocalDateTime(2026, 11, 11, 8, 0)), { kotlinx.datetime.TimeZone.UTC })
    private val morning = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = "Morning", icon = "🌅", points = 6, repeat = Repeat.everyDay(day.minusDays(1))),
    )
    private val water = store.addStep(Step(goalId = morning.id, name = "Water", points = 0, position = 0))
    private val stretch = store.addStep(Step(goalId = morning.id, name = "Stretch", points = 0, position = 1))
    private val bed = store.addStep(Step(goalId = morning.id, name = "Make bed", points = 0, position = 2))

    private suspend fun today() = TodayList.build(day, store.goals, store.steps, store.ticks, store.balance(), checks = store.checks).dueToday.single()

    @Test
    fun itemsPayNothingUntilTheLastOneDoesTheRoutine() = runTest {
        assertEquals(TickResult.Ticked(0, 0, false), book.checkItem(water.id))
        assertEquals(TickResult.Ticked(0, 0, false), book.checkItem(stretch.id))
        assertEquals(0, book.balance())
        assertEquals(listOf(true, true, false), today().items.map { it.done })
        assertEquals(TickResult.Ticked(6, 0, true), book.checkItem(bed.id))
        assertEquals(6, book.balance())
        assertTrue(today().done)
        assertEquals(TickResult.AlreadyDone, book.checkItem(bed.id))
    }

    @Test
    fun takingBackAnItemUndoesTheRoutine() = runTest {
        book.finishRoutine(morning.id)
        assertEquals(6, book.balance())
        assertTrue(book.uncheckItem(stretch.id))
        assertEquals(0, book.balance())
        val row = today()
        assertEquals(false, row.done)
        assertEquals(listOf(true, false, true), row.items.map { it.done })
    }

    @Test
    fun doneTicksEveryItemLeft() = runTest {
        book.checkItem(water.id)
        assertEquals(TickResult.Ticked(6, 0, true), book.finishRoutine(morning.id))
        assertEquals(3, store.checks.size)
        assertTrue(book.undoRoutine(morning.id))
        assertEquals(0, book.balance())
        assertTrue(store.checks.isEmpty())
        assertTrue(today().items.none { it.done })
    }

    @Test
    fun theItemsStartFreshTheNextDay() = runTest {
        book.finishRoutine(morning.id)
        val tomorrow = TodayList.build(day.plusDays(1), store.goals, store.steps, store.ticks, 0, checks = store.checks).dueToday.single()
        assertTrue(tomorrow.items.none { it.done })
        assertEquals(false, tomorrow.done)
    }
}
