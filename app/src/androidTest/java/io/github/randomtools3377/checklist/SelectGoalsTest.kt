package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Choosing several goals gives them one category together; removing several can be undone. */
@RunWith(AndroidJUnit4::class)
class SelectGoalsTest {
    class TwoGoals : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            listOf("Run" to "🏃", "Stretch" to "🧘").forEach { (name, icon) ->
                c.repository.saveHabit(
                    HabitDraft(
                        id = 0, name = name, icon = icon, points = 2, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                        minutes = null, at = null, finishBy = null, finishDay = null,
                    ),
                )
            }
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoGoals()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun categories() = runBlocking { container.db.dao().allGoals() }.map { it.category }

    @Test
    fun aCategoryForTwoAtOnce() {
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { shown("select") }
        app.tap("select")
        app.waitFor { shown("selection-bar") }
        app.tap("goal:Run")
        app.tap("goal:Stretch")
        app.tap("bulk:category")
        app.waitFor { shown("bulk-category") }
        app.onNodeWithTag("bulk-category").performTextInput("Health")
        app.onNodeWithTag("bulk-category-ok").performClick()
        app.waitFor { categories().all { it == "Health" } }
    }
}
