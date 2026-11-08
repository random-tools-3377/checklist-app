package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Going over the limit with a skip: a reason, half the points, and a penalty task right away. */
@RunWith(AndroidJUnit4::class)
class SkipOverTheLimitTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun aSkipOverTheLimitCostsPointsAndAPenaltyTaskThatMustBeDone() {
        // A habit paying 10, with no skips or misses allowed.
        app.onNodeWithTag("nav:goals").performClick()
        app.onNodeWithTag("add-goal").performClick()
        app.onNodeWithTag("new-habit").performClick()
        app.onNodeWithTag("field:name").performTextInput("Run")
        app.onNodeWithTag("field:points").performTextInput("10")
        app.onNodeWithTag("field:allowance").performTextClearance()
        app.onNodeWithTag("field:allowance").performTextInput("0")
        app.onNodeWithTag("save").performClick()
        app.waitFor { !gone("goal:Run") }

        // Skip it: over the limit, it needs a reason.
        app.onNodeWithTag("nav:today").performClick()
        app.waitFor { !gone("skip:Run") }
        app.onNodeWithTag("skip:Run").performClick()
        app.waitFor { !gone("skip-reason:NO_TIME") }
        app.onNodeWithTag("skip-reason:NO_TIME").performClick()
        app.onNodeWithTag("confirm-skip").performClick()

        // The penalty task fills the screen. No penalty tasks yet, so write one and draw it.
        app.waitFor { !gone("penalty") }
        app.onNodeWithTag("new-penalty-task").performTextInput("20 push-ups")
        app.onNodeWithTag("add-penalty-task").performClick()
        app.waitFor { !gone("draw-task") }
        app.onNodeWithTag("draw-task").performClick()
        app.waitFor { !gone("penalty-task") }
        app.onNodeWithTag("penalty-task").assertTextEquals("20 push-ups")
        app.onNodeWithTag("penalty-done").performClick()

        // Back on Today: skipped, and half of 10 taken off.
        app.waitFor { !gone("skipped:Run") }
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals("-5 points")
    }
}

/** Misses are found when the app opens, and each needs a reason before anything else. */
@RunWith(AndroidJUnit4::class)
class MissesOnOpeningTest {
    /** Before the app starts: a daily habit, and the last check for misses three days ago. */
    class ThreeDaysAway : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            val today = nowLocal().date
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(today.minusDays(10)), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null, allowance = 0,
                ),
            )
            container.db.dao().putSetting(SettingEntity(Lapses.CHECKED_THROUGH, today.minusDays(3).toString()))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ThreeDaysAway()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun twoMissesNeedReasonsAndEachOverTheLimitOwesAPenalty() {
        // Two days missed, with no allowance: both are over the limit.
        app.waitFor { app.onAllNodesWithTag("miss:Read").fetchSemanticsNodes().size == 2 }
        app.onAllNodesWithTag("reason:Read:ILL")[0].performClick()
        app.onAllNodesWithTag("reason:Read:NO_TIME")[1].performClick()
        app.onNodeWithTag("misses-done").performClick()

        // Then a penalty task for each: make up the missed goal, twice.
        repeat(2) {
            app.waitFor { !gone("makeup:Read") }
            app.onNodeWithTag("makeup:Read").performClick()
            app.waitFor { !gone("penalty-done") }
            app.onNodeWithTag("penalty-done").performClick()
            app.waitForIdle()
        }

        // Then today's list. Half of 4 came off for each miss.
        app.waitFor { !gone("today-list") }
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals("-4 points")
    }
}
