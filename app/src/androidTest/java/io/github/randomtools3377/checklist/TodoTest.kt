package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Todos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A to-do is added from the box on Today, pays its points when done, and takes them back when undone. */
@RunWith(AndroidJUnit4::class)
class TodoTest {
    class Empty : ExternalResource() {
        override fun before() = runBlocking {
            (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.clearAllTables()
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Empty()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun balance() = runBlocking { container.book.balance() }

    @Test
    fun aToDoIsAddedOnTodayAndTicked() {
        app.waitFor { app.shownIn("today-list", "todo-field") }
        app.onNodeWithTag("todo-field").performTextInput("Call the dentist")
        app.tap("todo-add")
        app.waitFor { runBlocking { container.db.dao().allGoals() }.any { it.name == "Call the dentist" } }
        val goal = runBlocking { container.db.dao().allGoals() }.single()
        assertEquals(GoalKind.TODO.name, goal.kind)
        assertEquals(null, goal.repeat) // no date: never missed

        app.waitFor { app.shownIn("today-list", "tick:Call the dentist") }
        app.tap("tick:Call the dentist")
        app.waitFor { balance() == Todos.POINTS }

        // Tapping Done again takes it back.
        app.tap("tick:Call the dentist")
        app.waitFor { balance() == 0 }
    }

    @Test
    fun aToDosStepsAreTickedOnTodayAndItLandsInTheDoneList() {
        runBlocking {
            container.repository.saveTodo(
                io.github.randomtools3377.checklist.data.TodoDraft(
                    id = 0, name = "Move the desk", points = 6,
                    steps = listOf(
                        io.github.randomtools3377.checklist.data.StepDraft(0, "Clear it", 0),
                        io.github.randomtools3377.checklist.data.StepDraft(0, "Carry it", 0),
                    ),
                ),
            )
        }
        app.waitFor { app.shownIn("today-list", "item:Clear it") }
        app.onNodeWithTag("item:Clear it", useUnmergedTree = true).performClick()
        app.waitFor { runBlocking { container.db.dao().allTicks() }.count { it.stepId != null } == 1 }
        assertEquals(0, balance())
        app.waitFor { app.shownIn("today-list", "item:Carry it") }
        app.onNodeWithTag("item:Carry it", useUnmergedTree = true).performClick()
        app.waitFor { balance() == 6 }

        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { app.onAllNodesWithTag("done-list").fetchSemanticsNodes().isNotEmpty() }
        app.tap("done-list")
        app.waitFor { app.onAllNodesWithTag("done:Move the desk", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
