package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toCore
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The planner shows what is due, and a day's goal moves to another day of its week. */
@RunWith(AndroidJUnit4::class)
class PlanTest {
    class SwimToday : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Swim", icon = "🏊", points = 5, repeat = Repeat(RepeatKind.ONCE, nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SwimToday()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private val today = nowLocal().date
    private val tomorrow = today.plusDays(1)

    @Before
    fun aWeekdayWithTomorrowInTheSameWeek() {
        // On a Sunday there is no later day left in the week to move to.
        assumeTrue(today.dayOfWeek != DayOfWeek.SUNDAY)
    }

    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun moves() = runBlocking { container.db.dao().allGoals().first { it.name == "Swim" }.toCore().repeat!!.moves }

    @Test
    fun aGoalMovesToTomorrowFromThePlanner() {
        app.onNodeWithTag("nav:plan").performClick()
        app.waitFor { app.shownIn("plan", "move:Swim") }
        app.tap("move:Swim")
        app.waitFor { !gone("move-to:${tomorrow.dayOfWeek.isoDayNumber}") }
        app.onNodeWithTag("move-to:${tomorrow.dayOfWeek.isoDayNumber}").performClick()
        app.waitFor { moves().singleOrNull()?.let { it.from == today && it.to == tomorrow } == true }

        // Tomorrow's page lists it; today's list doesn't.
        app.waitFor { gone("plan-goal:Swim") }
        app.onNodeWithTag("plan-day:1").performClick()
        app.waitFor { !gone("plan-goal:Swim") }
        app.onNodeWithTag("nav:today").performClick()
        app.waitFor { !gone("today-list") }
        app.waitFor { !app.shownIn("today-list", "tick:Swim") }
    }

    @Test
    fun aGoalMovesFromToday() {
        app.waitFor { app.shownIn("today-list", "move:Swim") }
        app.tap("move:Swim")
        app.waitFor { !gone("move-to:${tomorrow.dayOfWeek.isoDayNumber}") }
        app.onNodeWithTag("move-to:${tomorrow.dayOfWeek.isoDayNumber}").performClick()
        app.waitFor { moves().isNotEmpty() }
        app.waitFor { !app.shownIn("today-list", "tick:Swim") }
    }
}
