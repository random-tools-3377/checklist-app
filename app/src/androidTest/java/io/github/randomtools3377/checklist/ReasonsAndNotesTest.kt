package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** A note on a tick, and the user's own reason offered when skipping over the limit. */
@RunWith(AndroidJUnit4::class)
class ReasonsAndNotesTest {
    class AWalk : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Walk", icon = "🚶", points = 3, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null, allowance = 0,
                ),
            )
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Swim", icon = "🏊", points = 6, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null, allowance = 0,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AWalk()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun aTickCanHaveANote() {
        app.waitFor { app.shownIn("today-list", "tick:Walk") }
        app.tap("tick:Walk")
        app.waitFor { app.shownIn("today-list", "note:Walk") }
        app.tap("note:Walk")
        app.waitFor { !gone("edit-tick-note") }
        app.onNodeWithTag("edit-tick-note").performTextInput("ran 5 km")
        app.tap("edit-tick-save")
        app.waitFor {
            app.shownIn("today-list", "note:Walk") && try {
                app.onNodeWithTag("note:Walk").assertTextEquals("“ran 5 km”")
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    @Test
    fun anOwnReasonIsOfferedWhenSkippingOverTheLimit() {
        app.onNodeWithTag("settings").performClick()
        app.waitFor { app.onAllNodesWithTag("setting:new-reason").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("setting:new-reason").performTextInput("Raining")
        app.tap("add-reason")
        app.waitFor { !gone("remove-reason:Raining") }
        app.onNodeWithContentDescription("Back").performClick()

        app.waitFor { app.shownIn("today-list", "skip:Swim") }
        app.tap("skip:Swim")
        app.waitFor { !gone("skip-reason:own:Raining") }
        app.tap("skip-reason:own:Raining")
        app.tap("confirm-skip")
        app.waitFor { !gone("penalty") }
    }
}
