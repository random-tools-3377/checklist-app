package io.github.randomtools3377.checklist

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.StepDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.widget.CalendarWidget
import io.github.randomtools3377.checklist.widget.DayPlans
import io.github.randomtools3377.checklist.widget.TimelineData
import io.github.randomtools3377.checklist.widget.TodayWidget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The today widget's timeline: a tick made from the home screen shows on it straight away (it
 * reads again while awake), a goal done twice a day counts up with +1, and a routine's button
 * ticks its next item. The calendar widget follows ticks the same way.
 */
@RunWith(AndroidJUnit4::class)
class TimelineWidgetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val container get() = (context as ChecklistApplication).container
    private var brush = 0L
    private var morning = 0L

    @Before
    fun twoGoals() = runBlocking {
        container.db.clearAllTables()
        val today = nowLocal().date
        container.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Brush teeth", icon = "🪥", points = 2, repeat = Repeat.everyDay(today), target = 2, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null,
            ),
        )
        container.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Morning", icon = "🌅", points = 5, repeat = Repeat.everyDay(today), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null,
                items = listOf(StepDraft(0, "Water", 0), StepDraft(0, "Stretch", 0)),
            ),
        )
        val goals = container.db.dao().activeGoals()
        brush = goals.first { it.name == "Brush teeth" }.id
        morning = goals.first { it.name == "Morning" }.id
    }

    /** Waits for the widget's data to pass [check], reading it as it changes. */
    private suspend fun <T> Channel<T>.until(check: (T) -> Boolean): T = withTimeout(10_000) {
        var latest = receive()
        while (!check(latest)) latest = receive()
        latest
    }

    @Test
    fun ticksShowOnTheWidgetStraightAway() = runBlocking {
        val updates = Channel<TimelineData>(Channel.UNLIMITED)
        val watching = launch { DayPlans.following(context) { TodayWidget.load(context) }.collect { updates.send(it) } }
        try {
            val first = updates.until { data -> data.rows.any { it.goalId == brush } }
            assertEquals(2, first.total)
            val row = first.rows.first { it.goalId == brush }
            assertEquals("+1", row.button)
            assertEquals("0 of 2", row.detail)

            container.book.tickHabit(brush)
            updates.until { data -> data.rows.firstOrNull { it.goalId == brush }?.detail == "1 of 2" }
            container.book.tickHabit(brush)
            val done = updates.until { data -> data.done.any { it.goalId == brush } }
            assertEquals(1, done.rows.size)

            // A routine's button ticks its next item.
            val routine = done.rows.single()
            assertEquals("✓ Water", routine.button)
            container.book.checkItem(routine.stepId!!)
            val next = updates.until { data -> data.rows.firstOrNull()?.button == "✓ Stretch" }
            assertEquals("1 of 2 · next: Stretch", next.rows.first().detail)
            container.book.checkItem(next.rows.first().stepId!!)
            val allDone = updates.until { data -> data.rows.isEmpty() }
            assertEquals(2, allDone.done.size)
            assertTrue("tomorrow is shown once today is done", allDone.tomorrow.any { "Brush teeth" in it })

            // Tapping a done routine starts it again.
            container.book.undoRoutine(morning)
            updates.until { data -> data.rows.any { it.goalId == morning } }
        } finally {
            watching.cancel()
        }
    }

    @Test
    fun theCalendarWidgetFollowsTicksToo() = runBlocking {
        val updates = Channel<Int>(Channel.UNLIMITED)
        val watching = launch {
            DayPlans.following(context) { CalendarWidget.load(context) }.collect { data -> updates.send(data.agenda.first { it.goal.id == brush }.count) }
        }
        try {
            updates.until { it == 0 }
            container.book.tickHabit(brush)
            updates.until { it == 1 }
        } finally {
            watching.cancel()
        }
    }
}
