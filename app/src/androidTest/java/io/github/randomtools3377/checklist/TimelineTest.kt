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
import io.github.randomtools3377.checklist.data.toCore
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A goal on the timeline gets a time for today only, and back to its usual one. */
@RunWith(AndroidJUnit4::class)
class TimelineTest {
    class RunAtSeven : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Run", icon = "🏃", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = LocalTime(7, 0), finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RunAtSeven()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun retimes() = runBlocking { container.db.dao().allGoals().single().toCore().repeat!!.retimes }
    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aTimeForTodayOnly() {
        app.waitFor { shown("open-timeline") }
        app.tap("open-timeline")
        app.waitFor { shown("timeline-goal:Run") }
        app.tap("timeline-goal:Run")
        app.waitFor { shown("retime-ok") }
        app.onNodeWithTag("retime-ok").performClick()
        app.waitFor { retimes().singleOrNull()?.minute == 7 * 60 }
        app.tap("timeline-goal:Run")
        app.waitFor { shown("retime-usual") }
        app.onNodeWithTag("retime-usual").performClick()
        app.waitFor { retimes().isEmpty() }
    }
}
