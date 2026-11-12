package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Two days in a row with nothing ticked: one check-in and one penalty task cover them, and the review shows the loss. */
@RunWith(AndroidJUnit4::class)
class GapCheckInTest {
    /** Before the app starts: a daily habit, tracked from three days ago, untouched since. */
    class TwoQuietDays : ExternalResource() {
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
            // Added ten days ago, so the quiet days count for it.
            val added = today.minusDays(10).atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds()
            container.db.dao().observeGoals().first().forEach { container.db.dao().updateGoal(it.copy(createdAt = added)) }
            container.db.dao().putSetting(SettingEntity(Lapses.TRACKING_SINCE, today.minusDays(3).toString()))
            container.db.dao().putSetting(SettingEntity(Lapses.CHECKED_THROUGH, today.minusDays(3).toString()))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoQuietDays()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun aGapIsOneCheckInAndOnePenaltyTask() {
        // The check-in comes first, instead of a reason for each miss.
        app.waitFor { !gone("gap") }
        // The reasons are in the check-in's list, which only draws what is on the screen: scroll to them first.
        app.waitFor { app.shownIn("gap-list", "gap-reason:ILL") }
        app.tap("gap-reason:ILL")
        app.tap("gap-done")

        // One penalty task for the whole stretch.
        app.waitFor { !gone("makeup:Read") }
        app.tap("makeup:Read")
        app.waitFor { !gone("penalty-done") }
        app.tap("penalty-done")

        // Then today's list. Points still came off for both misses: half of 4, twice.
        app.waitFor { !gone("today-list") }
        app.toTopOfToday()
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals("-4 points")

        // The review, on yesterday: 2 points lost.
        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { !gone("review:Day") }
        app.tap("review:Day")
        app.waitFor { !gone("review:back") }
        app.tap("review:back")
        app.waitFor {
            try {
                app.onNodeWithTag("review:lost").assertTextEquals("−2")
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }
}

/** The gap check-in lists each miss with its own "I forgot to tick it"; said for every one, the gap goes away. */
@RunWith(AndroidJUnit4::class)
class GapForgottenTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GapCheckInTest.TwoQuietDays()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun balance() = runBlocking { container.book.balance() }
    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun everyMissSaidForgottenMakesTheGapGoAwayWithNoPenaltyTask() {
        val today = nowLocal().date
        val first = "gap-forgot:Read:${today.minusDays(2)}"
        val second = "gap-forgot:Read:${today.minusDays(1)}"
        // The check-in lists each goal missed in the stretch, by day, with its own button.
        app.waitFor { !gone("gap") }
        app.waitFor { app.shownIn("gap-list", first) }
        app.waitFor { app.shownIn("gap-list", second) }
        app.waitFor { balance() == -4 }

        // The first: ticked for that day, and what that miss cost is back. The gap stays for the other.
        app.tapIn("gap-list", first)
        app.waitFor { balance() == 2 }
        app.waitFor { gone(first) }
        assertTrue(!gone("gap"))

        // The second: every miss was a forgotten tick, so there is no gap and no penalty task.
        app.tapIn("gap-list", second)
        app.waitFor { !gone("today-list") }
        assertTrue(gone("gap"))
        assertTrue(gone("penalty"))
        app.waitFor { balance() == 8 }
        app.toTopOfToday()
        app.onNodeWithTag("balance", useUnmergedTree = true).assertTextEquals("8 points")
        assertTrue(runBlocking { container.db.dao().gaps() }.isEmpty())
    }
}

/** A past miss is put right later, from the review's day view and from its penalty's line in the points history. */
@RunWith(AndroidJUnit4::class)
class PastMissTest {
    /** Before the app starts: a daily habit with no misses allowed, last looked at two days ago, so yesterday is a miss. */
    class MissedYesterday : ExternalResource() {
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
            val added = today.minusDays(10).atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds()
            container.db.dao().observeGoals().first().forEach { container.db.dao().updateGoal(it.copy(createdAt = added)) }
            container.db.dao().putSetting(SettingEntity(Lapses.CHECKED_THROUGH, today.minusDays(2).toString()))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(MissedYesterday()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun balance() = runBlocking { container.book.balance() }
    private fun miss() = runBlocking { container.db.dao().allLapses() }.single()
    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    private val yesterday get() = nowLocal().date.minusDays(1)

    /** Opening the app: yesterday's miss gets "No time", and its penalty task is done. */
    private fun answerTheMiss() {
        app.waitFor { !gone("misses-list") }
        app.tapIn("misses-list", "reason:Read:$yesterday:NO_TIME")
        app.tap("misses-done")
        app.waitFor { !gone("makeup:Read") }
        app.tap("makeup:Read")
        app.waitFor { !gone("penalty-done") }
        app.tap("penalty-done")
        app.waitFor { !gone("today-list") }
        app.waitFor { balance() == -2 }
    }

    @Test
    fun fromTheReviewsDayViewItsReasonIsChangedAndThenItWasAForgottenTick() {
        answerTheMiss()
        val row = "review-miss:Read:$yesterday"
        // The review, on yesterday: the day view lists what was missed.
        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { !gone("review:Day") }
        app.tap("review:Day")
        app.waitFor { !gone("review:back") }
        app.tap("review:back")
        app.waitFor { app.shownIn("review", row) }

        // Opened: its reason is changed.
        app.tapIn("review", row)
        app.waitFor { !gone("past-reason:ILL") }
        app.tap("past-reason:ILL")
        app.tap("past-save")
        app.waitFor { miss().reason == "ILL" }

        // Opened again: "I forgot to tick it" ticks that day and gives back what the miss cost.
        app.waitFor { gone("past-miss") }
        app.tapIn("review", row)
        app.waitFor { !gone("past-forgot") }
        app.tap("past-forgot")
        app.waitFor { miss().forgotten }
        app.waitFor { balance() == -2 + 2 + 4 }
        // No longer a miss: it leaves the list.
        app.waitFor { gone("review-missed") }
    }

    @Test
    fun fromItsPenaltysLineInThePointsHistoryItsReasonIsChanged() {
        answerTheMiss()
        app.toTopOfToday()
        app.onNodeWithText("Tap for your points history").performSemanticsAction(SemanticsActions.OnClick)
        app.waitFor { !gone("history") }
        // The penalty's line opens the miss it was for.
        app.tapIn("history", "entry:Read")
        app.waitFor { !gone("past-reason:CHOSE_NOT") }
        app.tap("past-reason:CHOSE_NOT")
        app.tap("past-save")
        app.waitFor { miss().reason == "CHOSE_NOT" }
        assertTrue(!miss().forgotten)
    }
}

