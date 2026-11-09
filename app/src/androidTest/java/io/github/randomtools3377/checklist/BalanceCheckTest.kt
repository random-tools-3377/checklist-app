package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

/** A reward priced far below a day's earning is flagged, and its suggested price can be used. */
@RunWith(AndroidJUnit4::class)
class BalanceCheckTest {
    class EarnedTwentyToday : ExternalResource() {
        override fun before() = runBlocking {
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 20, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            container.book.tickHabit(container.db.dao().allGoals().single().id)
            container.repository.saveReward(RewardDraft(id = 0, name = "Sweet", icon = "🍬", price = 1))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EarnedTwentyToday()).around(app)

    @Test
    fun aTooCheapRewardGetsASuggestedPrice() {
        app.onNodeWithTag("nav:shop").performClick()
        app.waitFor { app.onAllNodesWithTag("balance-check").fetchSemanticsNodes().isNotEmpty() }
        app.tap("balance-check")
        app.waitFor { app.onAllNodesWithTag("use:check-reward:Sweet").fetchSemanticsNodes().isNotEmpty() }
        app.tap("use:check-reward:Sweet")
        app.waitFor { runBlocking { container.db.dao().allRewards() }.single().price == 20 }
    }
}
