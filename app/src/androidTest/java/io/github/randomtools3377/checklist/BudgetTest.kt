package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.PayUnit
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.WorkHours
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The Budget tab: today's pay filling up, spending typed in, and a reward with a money value
 * counted as spent when bought and taken off again when returned.
 */
@RunWith(AndroidJUnit4::class)
class BudgetTest {
    class PaidAndAReward : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.setSetting(Feature.BUDGET.key, Feature.BUDGET.onValue)
            // Paid by the hour, around the clock every day, so today always has pay coming in.
            container.repository.savePay(PayPlan(20.0, PayUnit.HOUR, List(7) { WorkHours(0, 0) }))
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            container.book.tickHabit(container.db.dao().activeGoals().single().id)
            container.repository.saveReward(RewardDraft(id = 0, name = "Film", icon = "🎬", price = 3, value = 9.99))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(PaidAndAReward()).around(app)

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun closeCelebration() {
        if (shown("celebration")) {
            try {
                app.onNodeWithTag("celebration").performClick()
            } catch (e: AssertionError) {
                // It closed by itself in the meantime.
            }
        }
        app.waitFor { !shown("celebration") }
    }

    @Test
    fun spendingAndRewardsAddUpInTheBudget() {
        app.waitFor { shown("nav:budget") }
        app.onNodeWithTag("nav:budget").performClick()

        // Today's pay is filling up, and the starting categories are there.
        app.waitFor { shown("today-pay") }
        app.waitFor { app.shownIn("budget-list", "category:Food") }

        // Spending typed in.
        app.onNodeWithTag("add-money").performClick()
        app.onNodeWithTag("field:amount").performTextInput("12.50")
        app.waitFor { shown("work-time") } // 12.50 at 20 an hour: 37½ minutes of work
        app.onNodeWithTag("work-time").assertTextContains("38 min", substring = true)
        app.onNodeWithTag("money-category:Food").performClick()
        app.onNodeWithTag("save").performClick()
        app.waitFor { app.shownIn("budget-list", "money:Food") }
        app.waitFor { app.shownIn("budget-list", "sum:spent") }
        app.onNodeWithTag("sum:spent", useUnmergedTree = true).assertTextContains("12.50", substring = true)

        // A reward with a money value, bought in the shop, is spent under Rewards.
        app.onNodeWithTag("nav:shop").performClick()
        app.waitFor { shown("buy:Film") }
        app.onNodeWithTag("work:Film", useUnmergedTree = true).assertTextContains("of work", substring = true)
        app.onNodeWithTag("buy:Film").performClick()
        app.onNodeWithTag("confirm-buy").performClick()
        closeCelebration()
        app.onNodeWithTag("nav:budget").performClick()
        app.waitFor { app.shownIn("budget-list", "money:Film") }
        app.waitFor { app.shownIn("budget-list", "sum:spent") }
        app.onNodeWithTag("sum:spent", useUnmergedTree = true).assertTextContains("22.49", substring = true)

        // Returned, it comes off again.
        app.onNodeWithTag("nav:shop").performClick()
        app.waitFor { shown("tab:bought") }
        app.onNodeWithTag("tab:bought").performClick()
        app.waitFor { shown("return:Film") }
        app.onNodeWithTag("return:Film").performClick()
        app.onNodeWithTag("confirm-return").performClick()
        app.onNodeWithTag("nav:budget").performClick()
        app.waitFor { app.shownIn("budget-list", "money:Food") }
        app.waitFor { !app.shownIn("budget-list", "money:Film") }
    }
}
