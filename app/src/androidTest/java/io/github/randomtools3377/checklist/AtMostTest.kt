package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** An "at most" goal: each one is added on Today, and going over the limit is a miss straight away. */
@RunWith(AndroidJUnit4::class)
class AtMostTest {
    class TwoCoffees : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.setSetting(Feature.AT_MOST.key, Feature.AT_MOST.onValue)
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Coffee", icon = "☕", points = 6, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null, limit = 2.0,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoCoffees()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun balance() = runBlocking { container.book.balance() }
    private fun lapses() = runBlocking { container.db.dao().allLapses() }
    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun addingConfirmingAndGoingOver() {
        app.waitFor { app.shownIn("today-list", "use:Coffee") }
        app.waitFor { textOf("used:Coffee") == "0 of 2 used, 2 left" }
        // It isn't one of the day's goals to tick.
        assertTrue(gone("tick:Coffee"))

        app.tap("use:Coffee")
        app.waitFor { textOf("used:Coffee") == "1 of 2 used, 1 left" }
        assertEquals(0, balance())

        // "That's all for today" pays now, and can be taken back.
        app.tap("confirm:Coffee")
        app.waitFor { balance() == 6 }
        app.waitFor { app.shownIn("today-list", "unconfirm:Coffee") }
        app.tap("unconfirm:Coffee")
        app.waitFor { balance() == 0 }
        app.waitFor { app.shownIn("today-list", "confirm:Coffee") }

        // At the limit is still fine; one more is over, and a miss straight away.
        app.tap("use:Coffee")
        app.waitFor { textOf("used:Coffee") == "2 of 2 used, none left" }
        assertTrue(lapses().isEmpty())
        app.tap("use:Coffee")
        app.waitFor { lapses().size == 1 }
        app.waitFor { app.onAllNodesWithTag("misses-list").fetchSemanticsNodes().isNotEmpty() }
        app.waitFor { app.onAllNodesWithText("Went over on", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Nothing to say was forgotten; one added by mistake can be taken back from here.
        assertTrue(gone("forgot:Coffee:${nowLocal().date}"))
        app.tap("miss-undo-use:Coffee")
        app.waitFor { lapses().isEmpty() }
        app.waitFor { app.onAllNodesWithTag("misses-list").fetchSemanticsNodes().isEmpty() }
        app.waitFor { app.shownIn("today-list", "used:Coffee") }
        app.waitFor { textOf("used:Coffee") == "2 of 2 used, none left" }
        // What was added is kept as it is: two, and no tick toward done.
        val ticks = runBlocking { container.db.dao().allTicks() }
        assertEquals(2, ticks.size)
        assertTrue(ticks.all { it.period == null })
    }

    @Test
    fun theEditorMakesOne() {
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("add-goal") }
        app.onNodeWithTag("add-goal").performClick()
        app.waitFor { !gone("new-habit") }
        app.onNodeWithTag("new-habit").performClick()
        app.waitFor { !gone("field:name") }
        app.onNodeWithTag("field:name").performTextInput("Screen time")
        app.onNodeWithTag("field:points").performTextInput("5")
        app.tap("field:at-most")
        app.waitFor { !gone("field:limit") }
        app.onNodeWithTag("field:limit").performTextInput("120")
        app.onNodeWithTag("field:limit-unit").performTextInput("minutes")
        app.tap("save")
        app.waitFor { !gone("goal:Screen time") }

        app.onNodeWithTag("nav:today").performClick()
        app.waitFor { app.shownIn("today-list", "use-amount:Screen time") }
        app.waitFor { textOf("used:Screen time") == "0 of 120 minutes used, 120 left" }
        app.tap("use-amount:Screen time")
        app.waitFor { !gone("field:use") }
        app.onNodeWithTag("field:use").performTextInput("45")
        app.onNodeWithTag("save-use").performClick()
        app.waitFor { textOf("used:Screen time") == "45 of 120 minutes used, 75 left" }
    }
}
