package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.reminders.Focus
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The focus timer ticks its goal; a goal can be copied, removed and brought back. */
@RunWith(AndroidJUnit4::class)
class FocusAndCopyTest {
    class AReadingGoal : ExternalResource() {
        override fun before() = runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val container = (context.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            Focus.stop(context)
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = 20, at = null, finishBy = null, finishDay = null,
                ),
            )
        }

        override fun after() {
            Focus.stop(InstrumentationRegistry.getInstrumentation().targetContext)
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AReadingGoal()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun theFocusTimerTicksTheGoal() {
        app.waitFor { app.shownIn("today-list", "focus:Read") }
        app.tap("focus:Read")
        app.waitFor { !gone("focus") }
        app.onNodeWithTag("focus-time").assertTextEquals("20:00")
        app.tap("focus-done")
        app.waitFor { gone("focus") }
        app.waitFor {
            try {
                app.toTopOfToday()
                app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals("4 points")
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun aGoalCanBeCopiedRemovedAndBroughtBack() {
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal:Read") }
        app.tap("goal:Read")
        app.waitFor { !gone("duplicate") }
        app.tap("duplicate")
        // The copy opens in the editor; going back shows both.
        app.waitFor { !gone("field:name") }
        app.onNodeWithTag("save").performClick()
        app.waitFor { !gone("goal:Read (copy)") }

        app.tap("goal:Read (copy)")
        app.waitFor { !gone("remove") }
        app.tap("remove")
        app.waitFor { !gone("confirm-remove") }
        app.tap("confirm-remove")
        app.waitFor { gone("goal:Read (copy)") && !gone("removed-goals") }
        app.tap("removed-goals")
        app.waitFor { !gone("restore:Read (copy)") }
        app.tap("restore:Read (copy)")
        app.waitFor { gone("restore:Read (copy)") }
    }
}
