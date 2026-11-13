package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "less motion" rule, where a dragged row lands, and what a swipe on a card does. */
class LessMotionTest {
    @Test
    fun lessMotionStartsOnTheDevicesSettingUntilTheSwitchIsMoved() {
        assertTrue(LessMotion.calm(null, device = true))
        assertFalse(LessMotion.calm(null, device = false))
        assertFalse(LessMotion.calm("", device = false))
        // Once the user has chosen, the device's own setting no longer decides.
        assertTrue(LessMotion.calm("1", device = false))
        assertFalse(LessMotion.calm("0", device = true))
        assertNull(LessMotion.choice(null))
        assertEquals(true, LessMotion.choice(LessMotion.encode(true)))
        assertEquals(false, LessMotion.choice(LessMotion.encode(false)))
    }

    @Test
    fun aDraggedRowTakesThePlaceItsMiddleIsOver() {
        val rows = listOf(100, 100, 100, 100)
        assertEquals(1, DragOrder.target(1, 0f, rows))
        // Not yet past the middle of the next row.
        assertEquals(1, DragOrder.target(1, 40f, rows))
        assertEquals(2, DragOrder.target(1, 60f, rows))
        assertEquals(3, DragOrder.target(1, 160f, rows))
        // Dragged far past the end, or the start, it stops at the last or first place.
        assertEquals(3, DragOrder.target(1, 900f, rows))
        assertEquals(0, DragOrder.target(1, -60f, rows))
        assertEquals(1, DragOrder.target(1, -40f, rows))
        assertEquals(0, DragOrder.target(3, -900f, rows))
        // A row that isn't there stays where it was.
        assertEquals(7, DragOrder.target(7, 50f, rows))
    }

    @Test
    fun rowsOfDifferentHeightsAreCountedByTheirOwnHeight() {
        // A short row over a tall one: it has to pass the tall row's middle.
        val rows = listOf(40, 200, 40)
        assertEquals(0, DragOrder.target(0, 90f, rows))
        assertEquals(1, DragOrder.target(0, 110f, rows))
        assertEquals(2, DragOrder.target(0, 230f, rows))
        assertEquals(1, DragOrder.target(2, -110f, rows))
    }

    @Test
    fun theRowsBetweenStepAsideByTheDraggedRowsHeight() {
        // From 1 down to 3: rows 2 and 3 move up; from 3 up to 1: rows 1 and 2 move down.
        assertEquals(listOf(0, 0, -80, -80, 0), (0..4).map { DragOrder.shift(it, from = 1, target = 3, draggedHeight = 80) })
        assertEquals(listOf(0, 80, 80, 0, 0), (0..4).map { DragOrder.shift(it, from = 3, target = 1, draggedHeight = 80) })
        assertEquals(listOf(0, 0, 0), (0..2).map { DragOrder.shift(it, from = 1, target = 1, draggedHeight = 80) })
    }

    @Test
    fun aDropMovesOneRowAndKeepsTheRest() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "d", "b"), DragOrder.moved(list, 1, 3))
        assertEquals(listOf("d", "a", "b", "c"), DragOrder.moved(list, 3, 0))
        assertEquals(list, DragOrder.moved(list, 2, 2))
        assertEquals(list, DragOrder.moved(list, 2, 9))
        // The same place the existing arrows' move reaches, one step at a time.
        val steps = list.toMutableList()
        Reorder.move(steps, 1, 2)
        assertEquals(DragOrder.moved(list, 1, 3), steps)
    }

    private val day = LocalDate(2026, 11, 9)
    private fun habit(target: Int = 1, amount: Double? = null, limit: Double? = null) =
        Goal(id = 1, kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 2, target = target, amount = amount, limit = limit, repeat = Repeat.everyDay(day), createdAt = 1)
    private fun row(goal: Goal, count: Int = 0, skips: Int = 0) = HabitRow(goal, count, goal.repeat!!.window(day)!!, skips = skips)

    @Test
    fun aSwipeOnlyDoesWhatTheCardsButtonDoes() {
        assertEquals(SwipeTick.Does.TICK, SwipeTick.does(row(habit())))
        // "+1" on a counted goal, until it is done.
        assertEquals(SwipeTick.Does.TICK, SwipeTick.does(row(habit(target = 3), count = 2)))
        assertEquals(SwipeTick.Does.NOTHING, SwipeTick.does(row(habit(target = 3), count = 3)))
        // Done: the button would take the tick back, and a swipe never does that.
        assertEquals(SwipeTick.Does.NOTHING, SwipeTick.does(row(habit(), count = 1)))
        // An amount opens the same box as "Add".
        assertEquals(SwipeTick.Does.AMOUNT, SwipeTick.does(row(habit(amount = 20.0))))
        // A skipped goal can still be ticked, as its button can; nothing is skipped by a swipe.
        assertEquals(SwipeTick.Does.TICK, SwipeTick.does(row(habit(), skips = 1)))
        // "At most" goals count what was used, which is no tick.
        assertEquals(SwipeTick.Does.NOTHING, SwipeTick.does(row(habit(limit = 2.0))))
        val todo = Goal(id = 2, kind = GoalKind.TODO, name = "Buy milk", icon = "🥛", points = 2, repeat = null, createdAt = 1)
        assertEquals(SwipeTick.Does.TICK, SwipeTick.does(TodoRow(todo, done = false)))
        assertEquals(SwipeTick.Does.NOTHING, SwipeTick.does(TodoRow(todo, done = true)))
    }

    @Test
    fun aSwipeCountsOnlyPastAThirdOfTheCard() {
        assertFalse(SwipeTick.crossed(0f, 300))
        assertFalse(SwipeTick.crossed(100f, 300))
        assertTrue(SwipeTick.crossed(105f, 300))
        assertTrue(SwipeTick.crossed(300f, 300))
        // Before the card is measured nothing counts.
        assertFalse(SwipeTick.crossed(50f, 0))
    }

    @Test
    fun swipingIsOnUntilTurnedOffAndSaysWhatItWillDo() {
        assertTrue(SwipeTick.on(null))
        assertTrue(SwipeTick.on("1"))
        assertFalse(SwipeTick.on("0"))
        assertEquals("✓ Done", SwipeTick.label(SwipeTick.Does.TICK, counted = false, quit = false))
        assertEquals("+1", SwipeTick.label(SwipeTick.Does.TICK, counted = true, quit = false))
        assertEquals("✓ Avoided", SwipeTick.label(SwipeTick.Does.TICK, counted = false, quit = true))
        assertEquals("Add an amount", SwipeTick.label(SwipeTick.Does.AMOUNT, counted = false, quit = false))
        assertEquals("", SwipeTick.label(SwipeTick.Does.NOTHING, counted = false, quit = false))
    }
}
