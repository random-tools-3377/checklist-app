package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SavingFor
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

/** Starring a reward in the shop shows it on Today's balance card, with how far along it is. */
@RunWith(AndroidJUnit4::class)
class SavingForTest {
    /** 100 points earned today, a reward out of reach and one the balance covers. */
    class EarnedAHundred : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 100, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            c.book.tickHabit(c.db.dao().allGoals().single().id)
            c.repository.saveReward(RewardDraft(id = 0, name = "Bike", icon = "🚲", price = 300))
            c.repository.saveReward(RewardDraft(id = 0, name = "Coffee", icon = "☕", price = 20))
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(EarnedAHundred()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    private fun shown(tag: String): Boolean = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun says(tag: String, text: String): Boolean = try {
        app.onNodeWithTag(tag, useUnmergedTree = true).assertTextContains(text, substring = true)
        true
    } catch (e: AssertionError) {
        false
    }

    private fun starred(): Long? = runBlocking { SavingFor.starred(container.db.dao().setting(SavingFor.SETTING)) }

    private fun rewardId(name: String): Long = runBlocking { container.db.dao().allRewards().single { it.name == name }.id }

    private fun toToday() {
        app.onNodeWithTag("nav:today").performClick()
        app.waitFor { shown("today-list") }
        app.toTopOfToday()
    }

    @Test
    fun aStarredRewardShowsOnTodayAndTheStarMoves() {
        // Nothing is starred: Today's balance card shows the balance alone.
        app.waitFor { shown("today-list") }
        app.toTopOfToday()
        app.waitFor { shown("balance") }
        assertEquals(false, shown("saving-for"))

        // Star the bike in the shop. Under its bar the shop says how many more days it takes.
        app.onNodeWithTag("nav:shop").performClick()
        app.waitFor { shown("star:Bike") }
        app.waitFor { says("saving:Bike", "Saving for it: 100 of 300 points") }
        app.waitFor { says("saving-days:Bike", "about 2 more days") }
        app.tap("star:Bike")
        app.waitFor { starred() == rewardId("Bike") }

        // Today shows it: its name, how far along, and the same days as the shop.
        toToday()
        app.waitFor { shown("saving-for") }
        app.waitFor { says("saving-for-name", "Saving for 🚲 Bike") }
        app.waitFor { says("saving-for-line", "100 of 300 points") }
        app.waitFor { says("saving-for-line", "about 2 more days at your usual pace") }

        // Starring another reward moves the star. The balance covers this one.
        app.onNodeWithTag("nav:shop").performClick()
        app.waitFor { shown("star:Coffee") }
        app.tap("star:Coffee")
        app.waitFor { starred() == rewardId("Coffee") }
        toToday()
        app.waitFor { says("saving-for-name", "Saving for ☕ Coffee") }
        app.waitFor { says("saving-for-line", "You can buy it now") }

        // The button on the card opens the shop.
        app.tap("saving-for-shop")
        app.waitFor { shown("buy:Coffee") }

        // Tapping the star again takes it off, and Today's card goes back to the balance alone.
        app.tap("star:Coffee")
        app.waitFor { starred() == null }
        toToday()
        app.waitFor { shown("balance") }
        app.waitFor { !shown("saving-for") }
    }
}
