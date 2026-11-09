package io.github.randomtools3377.checklist

import android.app.UiAutomation
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.reminders.Focus
import io.github.randomtools3377.checklist.ui.READABLE_WIDTH
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Turned on its side, the tabs move to a rail down the side and the focus timer still fits. */
@RunWith(AndroidJUnit4::class)
class SidewaysTest {
    class AReadingGoal : ExternalResource() {
        override fun before() = runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val container = (context.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            Focus.stop(context)
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = 20, at = null, finishBy = null, finishDay = null,
                ),
            )
        }

        override fun after() {
            Focus.stop(InstrumentationRegistry.getInstrumentation().targetContext)
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AReadingGoal()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    /** Turns the whole phone, the way a hand would, rather than asking the app to. */
    private fun turn(rotation: Int) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(rotation)
    }

    @Test
    fun onItsSideTheTabsMoveToTheSideAndTheTimerFits() {
        try {
            // Upright on a phone: tabs along the bottom.
            app.waitFor { !gone("nav:today") }
            assertTrue(gone("nav-rail"))

            turn(UiAutomation.ROTATION_FREEZE_90)
            app.waitFor { !gone("nav-rail") && app.onAllNodesWithTag("nav:goals").fetchSemanticsNodes().size == 1 }

            // The tabs still work from the rail.
            app.tap("nav:goals")
            app.waitFor { gone("today-list") }
            app.tap("nav:today")
            app.waitFor { app.shownIn("today-list", "focus:Read") }

            // Today's list stays a readable width.
            val list = app.onNodeWithTag("today-list").fetchSemanticsNode()
            val width = with(list.layoutInfo.density) { list.size.width.toDp() }
            assertTrue("Today is $width wide", width <= READABLE_WIDTH + 1.dp)

            // The focus timer fits on the screen, Stop included.
            app.tap("focus:Read")
            app.waitFor { !gone("focus") }
            app.onNodeWithTag("focus-time").assertIsDisplayed()
            app.onNodeWithTag("focus-stop").assertIsDisplayed()
            app.onNodeWithTag("focus-done").assertIsDisplayed()
            app.tap("focus-stop")
            app.waitFor { gone("focus") }
        } finally {
            // Back upright for the tests after this one.
            turn(UiAutomation.ROTATION_FREEZE_0)
            turn(UiAutomation.ROTATION_UNFREEZE)
            runCatching { app.waitFor { gone("nav-rail") } }
        }
    }
}
