package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The weekly planning card opens the plan, and "Done planning" puts it away for the week; the review has a year. */
@RunWith(AndroidJUnit4::class)
class WeekPlanTest {
    class PlanningOn : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.db.dao().putSetting(SettingEntity(Feature.WEEKLY_PLANNING.key, Feature.WEEKLY_PLANNING.onValue))
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            // A flexible goal: three times this week, on any days.
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Swim", icon = "🏊", points = 5, repeat = Repeat.timesPer(Period.WEEKLY, nowLocal().date), target = 3, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(PlanningOn()).around(app)

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun planTheWeekThenSeeTheYear() {
        app.waitFor { shown("week-plan-card") }
        app.tap("week-plan")
        app.waitFor { shown("week-plan-screen") }
        // The week's flexible goal is listed with how many are left.
        app.waitFor { shown("week-plan-flexible:Swim") }
        app.onNodeWithTag("week-plan-flexible:Swim", useUnmergedTree = true).assertTextContains("3 of 3 left", substring = true)
        // The planner opens on top, and Back returns to planning and its "Done planning".
        app.tap("week-plan-open")
        app.waitFor { shown("plan") }
        app.tap("planner-back")
        app.waitFor { shown("week-plan-screen") }
        app.tap("week-plan-done")
        app.waitFor { shown("today-list") }
        app.waitFor { !shown("week-plan-card") }

        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { shown("review:Year") }
        app.tap("review:Year")
        app.waitFor { app.shownIn("review", "review-year") }
    }
}
