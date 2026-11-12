package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Goals in categories: Today shows one category at a time, and the review sums each one up, with charts. */
@RunWith(AndroidJUnit4::class)
class CategoriesAndChartsTest {
    class GoalsInCategories : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            listOf("Run" to "Health", "Dishes" to "Home", "Water" to " health ", "Read" to null).forEach { (name, category) ->
                container.repository.saveHabit(
                    HabitDraft(
                        id = 0, name = name, icon = "⭐", points = 3, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                        minutes = null, at = null, finishBy = null, finishDay = null, category = category,
                    ),
                )
            }
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GoalsInCategories()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun categoryOf(name: String): String? = runBlocking {
        val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
        container.db.dao().allGoals().first { it.name == name }.category
    }

    @Test
    fun todayShowsOneCategoryAtATime() {
        // "health" joined "Health" rather than starting a second one.
        assertEquals("Health", categoryOf("Water"))
        // The chips sit below the cards at the top of Today, so scroll to them first.
        app.waitFor { app.shownIn("today-list", "today-category:Home") }
        app.waitFor { app.shownIn("today-list", "tick:Run") }

        app.waitFor { app.shownIn("today-list", "today-category:Home") }
        app.tap("today-category:Home")
        app.waitFor { app.shownIn("today-list", "tick:Dishes") && !app.shownIn("today-list", "tick:Run") }
        app.waitFor { !app.shownIn("today-list", "tick:Read") }

        app.waitFor { app.shownIn("today-list", "today-category:Health") }
        app.tap("today-category:Health")
        app.waitFor { app.shownIn("today-list", "tick:Water") && !app.shownIn("today-list", "tick:Dishes") }

        app.waitFor { app.shownIn("today-list", "today-category:All") }
        app.tap("today-category:All")
        app.waitFor { app.shownIn("today-list", "tick:Read") && app.shownIn("today-list", "tick:Dishes") }
    }

    @Test
    fun theEditorOffersTheCategoriesInUse() {
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal:Read") }
        app.editGoal("Read")
        app.waitFor { !gone("category:Home") }
        app.tap("category:Home")
        app.tap("save")
        app.waitFor { categoryOf("Read") == "Home" }
    }

    @Test
    fun theReviewHasChartsAndEachCategory() {
        app.waitFor { app.shownIn("today-list", "tick:Run") }
        app.tap("tick:Run")
        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { !gone("review:Week") }
        app.tap("review:Week")
        app.waitFor { app.shownIn("review", "chart:done") }
        app.waitFor { app.shownIn("review", "chart:points") }
        app.waitFor { app.shownIn("review", "review:category:Health") }
        app.waitFor { app.shownIn("review", "review:category:Home") }
        app.waitFor { app.shownIn("review", "review:category:No category") }
    }
}
