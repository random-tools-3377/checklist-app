package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Starts each test with no data, the way the app is after installing. */
class FreshDatabase : ExternalResource() {
    override fun before() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication
        app.container.db.clearAllTables()
    }
}

/** Adds a habit, ticks it, stocks the shop, buys a reward and uses it, on a real (emulated) phone. */
@RunWith(AndroidJUnit4::class)
class TickAndBuyTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun balanceIs(text: String): Boolean = try {
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals(text)
        true
    } catch (e: AssertionError) {
        false
    }

    private fun gone(tag: String): Boolean = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun tickAGoalThenBuyAReward() {
        // A habit that pays 10 points.
        app.onNodeWithTag("nav:goals").performClick()
        app.onNodeWithTag("add-goal").performClick()
        app.onNodeWithTag("new-habit").performClick()
        app.onNodeWithTag("field:name").performTextInput("Drink water")
        app.onNodeWithTag("field:points").performTextInput("10")
        app.onNodeWithTag("save").performClick()
        app.waitUntil(10_000) { !gone("goal:Drink water") }

        // Tick it on today's list: the balance goes from 0 to 10.
        app.onNodeWithTag("nav:today").performClick()
        app.waitUntil(10_000) { !gone("tick:Drink water") }
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals("0 points")
        app.onNodeWithTag("tick:Drink water").performClick()
        app.waitUntil(10_000) { balanceIs("10 points") }

        // A reward that costs 4.
        app.onNodeWithTag("nav:shop").performClick()
        app.onNodeWithTag("add-reward").performClick()
        app.onNodeWithTag("field:name").performTextInput("Coffee")
        app.onNodeWithTag("field:price").performTextInput("4")
        app.onNodeWithTag("save").performClick()
        app.waitUntil(10_000) { !gone("buy:Coffee") }

        // Buy it: the balance drops to 6.
        app.onNodeWithTag("buy:Coffee").performClick()
        app.onNodeWithTag("confirm-buy").performClick()
        app.waitUntil(10_000) { balanceIs("6 points") }

        // The buying celebration closes with a tap, or by itself.
        if (!gone("celebration")) {
            try {
                app.onNodeWithTag("celebration").performClick()
            } catch (e: AssertionError) {
                // It closed by itself in the meantime.
            }
        }
        app.waitUntil(10_000) { gone("celebration") }

        // It waits under Bought until it is used.
        app.onNodeWithTag("tab:bought").performClick()
        app.onNodeWithTag("bought:Coffee").assertIsDisplayed()
        app.onNodeWithTag("use:Coffee").performClick()
        app.onNodeWithTag("confirm-use").performClick()
        app.waitUntil(10_000) { gone("bought:Coffee") }
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals("6 points")
    }

    @Test
    fun aRewardAboveTheBalanceCannotBeBought() {
        app.onNodeWithTag("nav:shop").performClick()
        app.onNodeWithTag("add-reward").performClick()
        app.onNodeWithTag("field:name").performTextInput("Trip")
        app.onNodeWithTag("field:price").performTextInput("500")
        app.onNodeWithTag("save").performClick()
        app.waitUntil(10_000) { !gone("buy:Trip") }

        app.onNodeWithTag("buy:Trip").assertIsNotEnabled()
        app.onNodeWithTag("buy:Trip").performClick()
        app.waitForIdle()
        assertTrue("Buying should not start at 0 points", gone("confirm-buy"))
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals("0 points")
    }
}
