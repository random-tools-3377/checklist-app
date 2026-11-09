package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
