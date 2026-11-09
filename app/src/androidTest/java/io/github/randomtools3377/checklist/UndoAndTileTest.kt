package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.tile.NextGoal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A tick can be taken back from the Undo bar, and the tile finds the next goal to tick. */
@RunWith(AndroidJUnit4::class)
class UndoAndTileTest {
    class ReadEveryDay : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ReadEveryDay()).around(app)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ChecklistApplication).container
    private fun balance() = runBlocking { container.book.balance() }

    @Test
    fun undoTakesTheTickBack() {
        app.waitFor { app.shownIn("today-list", "tick:Read") }
        app.tap("tick:Read")
        app.waitFor { balance() == 5 }
        app.waitFor { app.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithText("Undo").performClick()
        app.waitFor { balance() == 0 }
    }

    @Test
    fun theTileFindsTheNextGoalAndNothingOnceItIsDone() {
        assertEquals("Read", runBlocking { NextGoal.find(context) }?.goal?.name)
        runBlocking { container.book.tickHabit(container.db.dao().allGoals().single().id) }
        assertNull(runBlocking { NextGoal.find(context) })
    }
}
