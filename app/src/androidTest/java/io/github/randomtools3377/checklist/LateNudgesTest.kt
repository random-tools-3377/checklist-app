package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.randomtools3377.checklist.core.Nudges
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Rules
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.activeCoreGoals
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Late nudges in the app: "No more nudges today" on a late goal's card, and the switch in its editor. */
@RunWith(AndroidJUnit4::class)
class LateNudgesTest {
    /** "Stretch" has a set time, this very minute, so it is late straight away; "Wander" has no time. */
    class OneLateGoal : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            val now = nowLocal()
            // A nudge every 5 minutes from the very minute, so one is still to come at any time
            // of day but the last minutes before midnight.
            c.repository.setSetting(Rules.SETTING, Rules.encode(Rules(nudgeLateMinutes = 0, nudgeEveryMinutes = 5)))
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Stretch", icon = "🧘", points = 5, repeat = Repeat.everyDay(now.date), target = 1, bonus = 0,
                    minutes = null, at = LocalTime(now.hour, now.minute), finishBy = null, finishDay = null,
                ),
            )
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Wander", icon = "🚶", points = 5, repeat = Repeat.everyDay(now.date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    /** Saving a goal with a set time would otherwise stop to ask for notifications. */
    @get:Rule
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(OneLateGoal()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun goal(name: String) = runBlocking { container.db.dao().activeCoreGoals().single { it.name == name } }

    @Test
    fun aLateGoalsCardStopsItsNudgesForTheDay() {
        app.waitFor { app.shownIn("today-list", "stop-nudges:Stretch") }
        // A goal with nothing to be late for has nothing to stop.
        assertTrue(gone("stop-nudges:Wander"))
        app.tap("stop-nudges:Stretch")
        app.waitFor { gone("stop-nudges:Stretch") }
        val today = runBlocking { container.book.today() }
        assertEquals(today to setOf(goal("Stretch").id), Nudges.stopped(runBlocking { container.db.dao().setting(Nudges.STOPPED) }))
    }

    @Test
    fun theEditorHasTheSwitchForAGoalWithATime() {
        app.onNodeWithTag("nav:goals").performClick()
        app.editGoal("Stretch")
        app.waitFor { !gone("field:nudge") }
        app.tap("field:nudge")
        app.tap("save")
        app.waitFor { !gone("goal:Stretch") && gone("field:nudge") }
        app.waitFor { goal("Stretch").reminder?.nudge == false }

        // Without a set time or its own finish-by time there is nothing to nudge about.
        app.editGoal("Wander")
        app.waitFor { !gone("field:name") }
        assertTrue(gone("field:nudge"))
    }
}
