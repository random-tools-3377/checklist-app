package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toCore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A goal with an amount: entered as you go, paid when reached, and timed with Start and Stop. */
@RunWith(AndroidJUnit4::class)
class AmountsTest {
    class TwentyPages : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null, amount = 20.0, unit = "pages",
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwentyPages()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun amountsAddUpAndPayWhenReached() {
        app.waitFor { app.shownIn("today-list", "amount:Read") }
        app.waitFor { textOf("amount:Read").startsWith("0 of 20 pages") }
        app.tap("add-amount:Read")
        app.waitFor { !gone("field:amount") }
        app.onNodeWithTag("field:amount").performTextInput("12")
        app.onNodeWithTag("save-amount").performClick()
        app.waitFor { textOf("amount:Read").startsWith("12 of 20 pages") }
        assertEquals(0, runBlocking { container.book.balance() })

        app.waitFor { app.shownIn("today-list", "add-amount:Read") }
        app.tap("add-amount:Read")
        app.waitFor { !gone("amount-all") }
        app.onNodeWithTag("amount-all").performClick()
        app.waitFor { runBlocking { container.book.balance() } == 4 }
        app.waitFor { textOf("amount:Read").startsWith("20 of 20 pages") }
    }

    @Test
    fun theUsualAmountIsOneTapNextTime() {
        app.waitFor { app.shownIn("today-list", "add-amount:Read") }
        app.tap("add-amount:Read")
        app.waitFor { !gone("field:amount") }
        // Nothing entered before: no usual amounts yet.
        assertTrue(gone("usual-amounts"))
        app.onNodeWithTag("field:amount").performTextInput("5")
        app.onNodeWithTag("save-amount").performClick()
        app.waitFor { textOf("amount:Read").startsWith("5 of 20 pages") }

        // Next time, 5 pages is a button.
        app.waitFor { app.shownIn("today-list", "add-amount:Read") }
        app.tap("add-amount:Read")
        app.waitFor { !gone("usual-amount:5") }
        app.onNodeWithTag("usual-amount:5").performClick()
        app.waitFor { textOf("amount:Read").startsWith("10 of 20 pages") }
    }

    @Test
    fun aTimingIsStoppedAndSavedWithItsAmount() {
        app.waitFor { app.shownIn("today-list", "start:Read") }
        app.tap("start:Read")
        app.waitFor { app.shownIn("today-list", "stop:Read") }
        Thread.sleep(1500)
        app.tap("stop:Read")
        app.waitFor { !gone("field:amount") }
        app.onNodeWithTag("field:amount").performTextInput("5")
        app.onNodeWithTag("save-amount").performClick()
        app.waitFor { textOf("amount:Read").startsWith("5 of 20 pages") }
        val tick = runBlocking { container.db.dao().allTicks().single().toCore() }
        assertEquals(5.0, tick.amount)
        assertTrue((tick.seconds ?: 0) >= 1)
        // The timing has ended, so it can start again.
        app.waitFor { app.shownIn("today-list", "start:Read") }
    }

    @Test
    fun aGoalGetsItsAmountInTheEditor() {
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal:Read") }
        app.editGoal("Read")
        app.waitFor { !gone("field:unit") }
        app.onNodeWithTag("field:unit").performTextClearance()
        app.onNodeWithTag("field:unit").performTextInput("pages read")
        app.tap("save")
        app.waitFor { runBlocking { container.db.dao().allGoals().first { it.name == "Read" }.unit } == "pages read" }
        assertEquals(20.0, runBlocking { container.db.dao().allGoals().first { it.name == "Read" }.amount })
    }
}
