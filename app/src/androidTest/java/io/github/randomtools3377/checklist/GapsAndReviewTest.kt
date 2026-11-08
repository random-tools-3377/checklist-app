package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
