package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A bill counted as spent on its day by itself, and a saving goal filling up. */
@RunWith(AndroidJUnit4::class)
class BillsAndSavingsTest {
    class BudgetOn : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.setSetting(Feature.BUDGET.key, Feature.BUDGET.onValue)
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(BudgetOn()).around(app)

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aBillIsCountedAndASavingGoalFillsUp() {
        app.waitFor { shown("nav:budget") }
        app.onNodeWithTag("nav:budget").performClick()

        // A weekly bill from today counts as spent today.
        app.waitFor { app.shownIn("budget-list", "open-bills") }
        app.tap("open-bills")
        app.waitFor { shown("add-bill") }
        app.onNodeWithTag("add-bill").performClick()
        app.onNodeWithTag("field:name").performTextInput("Paper")
        app.onNodeWithTag("field:amount").performTextInput("3")
        app.tap("every:WEEKS")
        app.onNodeWithTag("save").performClick()
        app.waitFor { shown("bill:Paper") }
        app.onNodeWithContentDescription("Back").performClick()
        app.waitFor { app.shownIn("budget-list", "money:Paper") }
        app.waitFor { app.shownIn("budget-list", "coming:Paper") }

        // Saving for a bike: 100 of 300.
        app.waitFor { app.shownIn("budget-list", "add-saving") }
        app.tap("add-saving")
        app.waitFor { shown("field:saving-name") }
        app.onNodeWithTag("field:saving-name").performTextInput("Bike")
        app.onNodeWithTag("field:saving-target").performTextInput("300")
        app.onNodeWithTag("save-saving").performClick()
        app.waitFor { app.shownIn("budget-list", "put-in:Bike") }
        app.tap("put-in:Bike")
        app.waitFor { shown("field:saving-amount") }
        app.onNodeWithTag("field:saving-amount").performTextInput("100")
        app.onNodeWithTag("confirm-saving").performClick()
        app.waitFor { app.shownIn("budget-list", "saving:Bike") }
        app.waitFor {
            try {
                app.onNodeWithTag("saving-amounts:Bike", useUnmergedTree = true).assertTextContains("100.00 of", substring = true)
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // The month's report shows where it went.
        app.waitFor { app.shownIn("budget-list", "open-report") }
        app.tap("open-report")
        app.waitFor { shown("report-category:Bills") }
    }
}
