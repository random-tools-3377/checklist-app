package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.BuyResult
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A reward bought once this week shows its higher price in the shop, and costs that. */
@RunWith(AndroidJUnit4::class)
class RepeatPriceTest {
    class BoughtOnce : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.db.dao().putSetting(io.github.randomtools3377.checklist.data.SettingEntity(io.github.randomtools3377.checklist.core.Feature.REPEAT_PRICE.key, io.github.randomtools3377.checklist.core.Feature.REPEAT_PRICE.onValue))
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 100, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            c.book.tickHabit(c.db.dao().allGoals().single().id)
            c.repository.saveReward(RewardDraft(id = 0, name = "Coffee", icon = "☕", price = 20))
            c.book.buy(c.db.dao().allRewards().single().id)
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(BoughtOnce()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    @Test
    fun theSecondCostsMore() {
        app.onNodeWithTag("nav:shop").performClick()
        app.waitFor { app.onAllNodesWithTag("repeat-price:Coffee", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val second = runBlocking { container.book.buy(container.db.dao().allRewards().single().id) } as BuyResult.Bought
        assertEquals(25, second.price)
    }
}
