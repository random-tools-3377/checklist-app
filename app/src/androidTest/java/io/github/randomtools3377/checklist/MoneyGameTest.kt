package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.PayUnit
import io.github.randomtools3377.checklist.core.WorkHours
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The budget game shows today's share and what's spent against it. */
@RunWith(AndroidJUnit4::class)
class MoneyGameTest {
    class GameOn : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.setSetting(Feature.BUDGET.key, Feature.BUDGET.onValue)
            container.repository.setSetting(Feature.BUDGET_GAME.key, Feature.BUDGET_GAME.onValue)
            container.repository.savePay(PayPlan(3000.0, PayUnit.MONTH, List(7) { WorkHours(9 * 60, 17 * 60) }))
            container.repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 1_234, day = nowLocal().date))
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GameOn()).around(app)

    @Test
    fun todaysShareIsShown() {
        app.waitFor { app.onAllNodesWithTag("nav:budget").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("nav:budget").performClick()
        app.waitFor { app.shownIn("budget-list", "money-game") }
        app.waitFor { app.onAllNodesWithTag("money-game-today", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("money-game-today", useUnmergedTree = true).assertTextContains("12.34", substring = true)
    }
}
