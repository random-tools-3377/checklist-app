package io.github.randomtools3377.checklist

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.ColourBlind
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The colour-blind friendly look: turned on in Appearance, the review calendar marks each day as well as colouring it. */
@RunWith(AndroidJUnit4::class)
class ColourBlindTest {
    class AGoalDoneToday : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            container.book.tickHabit(container.db.dao().activeGoals().single().id)
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AGoalDoneToday()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    private fun setting(name: String): String? = runBlocking { dao.setting(name) }
    private fun shows(text: String) = app.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun back() {
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.waitForIdle()
    }

    private fun monthReview() {
        app.waitFor { !gone("nav:review") }
        app.tap("nav:review")
        app.waitFor { !gone("review:Month") }
        app.tap("review:Month")
    }

    @Test
    fun theReviewCalendarMarksEachDayInTheColourBlindLook() {
        // Usually the calendar tells days apart by colour alone.
        monthReview()
        app.waitFor { app.shownIn("review", "review-calendar-key") }
        app.waitFor { shows("red: none") }
        app.waitFor { !shows("✓ everything done") }

        // Turned on in Appearance.
        app.tap("nav:today")
        app.waitFor { !gone("settings") }
        app.tap("settings")
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("switch:colour-blind") }
        app.tap("switch:colour-blind")
        app.waitFor { setting(ColourBlind.SETTING) == "1" }
        back()
        back()

        // Now today, with everything done, has a ✓ as well as its colour.
        monthReview()
        app.waitFor { app.shownIn("review", "review-calendar-key") }
        app.waitFor { shows("✓ everything done") }
        app.waitFor { app.onAllNodes(hasText("✓")).fetchSemanticsNodes().isNotEmpty() }
    }
}
