package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_TREAT_BUDGET
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * A reward priced in money: its price in points works itself out from the weekly treat budget
 * and what the goals pay, and moves when the goals change.
 */
@RunWith(AndroidJUnit4::class)
class MoneyPriceTest {
    class ReadAndABudget : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.setSetting(Feature.SUGGESTIONS.key, Feature.SUGGESTIONS.onValue)
            container.repository.saveHabit(read("Read"))
            // A full week of reading pays 35, and earns 7 of money in rewards: 1 of money is 5 points.
            container.repository.setSetting(SETTING_TREAT_BUDGET, "7.0")
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ReadAndABudget()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun priceOf(name: String) = runBlocking { container.db.dao().activeRewards().firstOrNull { it.name == name }?.price }

    @Test
    fun aRewardPricedInMoneyWorksOutItsPriceAndKeepsInStep() {
        app.waitFor { shown("nav:shop") }
        app.onNodeWithTag("nav:shop").performClick()
        app.onNodeWithTag("add-reward").performClick()
        app.onNodeWithTag("field:name").performTextInput("Film")
        app.onNodeWithTag("field:money").performTextInput("12")

        // Typing what it costs makes the price follow it: 12 × 5 = 60, with no price to type.
        app.waitFor { shown("auto-price") }
        app.onNodeWithTag("field:follows-money").assertIsOn()
        app.onNodeWithTag("auto-price").assertTextContains("60 points", substring = true)
        app.waitFor { !shown("field:price") }
        app.onNodeWithTag("save").performClick()
        app.waitFor { shown("buy:Film") }
        app.waitFor { priceOf("Film") == 60 }
        app.onNodeWithTag("price:Film", useUnmergedTree = true).assertTextContains("60 points", substring = true)

        // A second goal that pays as much again: a point is worth half as much, so the price doubles.
        runBlocking { container.repository.saveHabit(read("Run")) }
        app.waitFor { priceOf("Film") == 120 }
        app.waitFor {
            try {
                app.onNodeWithTag("price:Film", useUnmergedTree = true).assertTextContains("120 points", substring = true)
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    companion object {
        fun read(name: String) = HabitDraft(
            id = 0, name = name, icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
            minutes = null, at = null, finishBy = null, finishDay = null,
        )
    }
}
