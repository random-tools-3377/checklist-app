package io.github.randomtools3377.checklist

import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.PayPlans
import io.github.randomtools3377.checklist.core.PayUnit
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SavingGoal
import io.github.randomtools3377.checklist.core.WorkHours
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.ui.OPEN_SPENT
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private val spentContext get() = InstrumentationRegistry.getInstrumentation().targetContext
private val spentContainer get() = (spentContext.applicationContext as ChecklistApplication).container

/** Budget on, paid around the clock, one habit, and a saving goal with 50 in it. */
private fun budgetWithPayAndSavings() = runBlocking {
    val container = spentContainer
    container.db.clearAllTables()
    container.repository.setSetting(Feature.BUDGET.key, Feature.BUDGET.onValue)
    container.repository.startBudget()
    container.repository.savePay(PayPlan(20.0, PayUnit.HOUR, List(7) { WorkHours(0, 0) }))
    container.repository.saveHabit(
        HabitDraft(
            id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
            minutes = null, at = null, finishBy = null, finishDay = null,
        ),
    )
    container.repository.saveSavingGoal(SavingGoal(name = "Holiday", icon = "🏖️", targetCents = 100_000))
    val goal = container.db.dao().allSavingGoals().single()
    container.repository.moveSaving(goal.id, 5_000, out = false, day = nowLocal().date, name = goal.name)
}

/**
 * Quick "Spent" from Today's pay card, spending paid from a saving goal, a pay change that asks
 * from when it applies, and regular money in.
 */
@RunWith(AndroidJUnit4::class)
class QuickSpentAndPayTest {
    class Setup : ExternalResource() {
        override fun before() = budgetWithPayAndSavings()
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Setup()).around(app)

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun money() = runBlocking { spentContainer.db.dao().allMoney().map { it.toCore() } }

    @Test
    fun spentOnTodayOpensTheFormAndCanBePaidFromSavings() {
        app.waitFor { app.shownIn("today-list", "today-spent") }
        app.tap("today-spent")
        app.waitFor { shown("field:amount") }
        app.onNodeWithTag("field:amount").performTextInput("20")
        app.tap("money-category:Food")
        // From savings: the goal that holds enough is picked.
        app.waitFor { shown("from-savings") }
        app.tap("from-savings")
        app.waitFor { shown("from-saving:Holiday") }
        app.onNodeWithTag("save").performClick()
        app.waitFor { money().any { it.spentFromSavings } }
        val paid = money().single { it.spentFromSavings }
        assertEquals(MoneyKind.TAKEN_OUT, paid.kind)
        assertEquals(2_000L, paid.cents)
        // It is no part of the month's spending.
        assertTrue(money().none { it.kind == MoneyKind.SPENT })
    }

    @Test
    fun aPayChangeAsksFromWhenAndKeepsTheOldPayBefore() {
        app.waitFor { shown("nav:budget") }
        app.onNodeWithTag("nav:budget").performClick()
        app.waitFor { app.shownIn("budget-list", "budget-pay") }
        app.tap("budget-pay")
        app.waitFor { shown("field:pay") }
        // The pay as it is shows first.
        app.waitFor { app.onAllNodes(androidx.compose.ui.test.hasText("20")).fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("field:pay").performTextReplacement("25")
        app.onNodeWithTag("save").performClick()
        // Today is offered first; the 1st of this month and from the start are there too.
        app.waitFor { shown("pay-from:TODAY") }
        assertTrue(shown("pay-from:MONTH") && shown("pay-from:START"))
        app.onNodeWithTag("pay-from-save").performClick()
        app.waitFor { runBlocking { PayPlans.decode(spentContainer.db.dao().setting(PayPlan.SETTING)) }?.plans?.size == 2 }
        val plans = runBlocking { PayPlans.decode(spentContainer.db.dao().setting(PayPlan.SETTING)) }!!
        val today = nowLocal().date
        assertEquals(25.0, plans.on(today).amount, 0.0001)
        assertEquals(20.0, plans.on(today.minusDays(1)).amount, 0.0001)
    }

    @Test
    fun regularMoneyInIsCountedAndListedAsMoneyIn() {
        app.waitFor { shown("nav:budget") }
        app.onNodeWithTag("nav:budget").performClick()
        app.waitFor { app.shownIn("budget-list", "open-bills") }
        app.tap("open-bills")
        app.waitFor { shown("add-bill") }
        app.onNodeWithTag("add-bill").performClick()
        app.waitFor { shown("bill-money-in") }
        app.tap("bill-money-in")
        app.onNodeWithTag("field:name").performTextInput("Pension")
        app.onNodeWithTag("field:amount").performTextInput("600")
        app.tap("remind:1")
        app.onNodeWithTag("save").performClick()
        app.waitFor { shown("bill:Pension") }
        app.waitFor { shown("money-in-total") }
        // Due today: counted as money received, by itself.
        app.waitFor { money().any { it.kind == MoneyKind.RECEIVED && it.note == "Pension" && it.cents == 60_000L } }
        val bill = runBlocking { spentContainer.db.dao().allBills() }.single().toCore()
        assertTrue(bill.moneyIn)
        assertEquals(1, bill.remindDays)
    }
}

/** The "Spent" shortcut on the app's icon, which the budget widget's "+ Spent" uses too. */
@RunWith(AndroidJUnit4::class)
class SpentShortcutTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun setUp() = budgetWithPayAndSavings()

    @Test
    fun spentOpensTheSpendingFormStraightAway() {
        val intent = Intent(spentContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, OPEN_SPENT)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitFor { compose.onAllNodesWithTag("field:amount").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("field:amount").performTextInput("4.50")
            compose.onNodeWithTag("money-category:Food").performClick()
            compose.onNodeWithTag("save").performClick()
            compose.waitFor { runBlocking { spentContainer.db.dao().allMoney() }.any { it.kind == "SPENT" && it.cents == 450L } }
        }
    }

    @Test
    fun withBudgetOffItSaysSoInsteadOfOpeningTheForm() {
        runBlocking { spentContainer.repository.setSetting(Feature.BUDGET.key, "0") }
        val intent = Intent(spentContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, OPEN_SPENT)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitFor { compose.onAllNodesWithTag("spent-open-features").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.onAllNodesWithTag("field:amount").fetchSemanticsNodes().isEmpty())
        }
    }
}
