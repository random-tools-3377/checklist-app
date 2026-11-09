package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A routine: a habit with a checklist, done and paid when every item is ticked. */
@RunWith(AndroidJUnit4::class)
class RoutineTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun balanceIs(text: String): Boolean = try {
        app.toTopOfToday()
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals(text)
        true
    } catch (e: AssertionError) {
        false
    }

    @Test
    fun theLastItemFinishesTheRoutine() {
        app.onNodeWithTag("nav:goals").performClick()
        app.onNodeWithTag("add-goal").performClick()
        app.onNodeWithTag("new-habit").performClick()
        app.onNodeWithTag("field:name").performTextInput("Morning")
        app.onNodeWithTag("field:points").performTextInput("6")
        app.tap("add-item")
        app.onNodeWithTag("field:item-0").performTextInput("Water")
        app.tap("add-item")
        app.onNodeWithTag("field:item-1").performTextInput("Stretch")
        app.onNodeWithTag("save").performClick()
        app.waitFor { !gone("goal:Morning") }

        app.onNodeWithTag("nav:today").performClick()
        app.waitFor { app.shownIn("today-list", "item:Water") }
        app.tap("item:Water")
        app.waitFor { app.shownIn("today-list", "count:Morning") && countIs("1 of 2") }
        app.waitFor { balanceIs("0 points") }

        app.waitFor { app.shownIn("today-list", "item:Stretch") }
        app.tap("item:Stretch")
        app.waitFor { balanceIs("6 points") }
    }

    private fun countIs(text: String): Boolean = try {
        app.onNodeWithTag("count:Morning").assertTextEquals(text)
        true
    } catch (e: AssertionError) {
        false
    }
}
