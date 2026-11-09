package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_WEEKLY_TARGET
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The weekly points target fills up on Today, and ticking earns badges, shown once and kept. */
@RunWith(AndroidJUnit4::class)
class BadgesAndTargetTest {
    class ARunAndATarget : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Run", icon = "🏃", points = 30, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            container.repository.setSetting(SETTING_WEEKLY_TARGET, "20")
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ARunAndATarget()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") }

    @Test
    fun theTargetFillsUpAndTheFirstTickEarnsBadges() {
        app.waitFor { textOf("weekly-target-text").contains("0 of 20") }
        app.waitFor { app.shownIn("today-list", "tick:Run") }
        app.tap("tick:Run")
        app.waitFor {
            app.toTopOfToday()
            textOf("weekly-target-text").contains("Weekly target reached: 30 of 20")
        }
        // First tick, a perfect day and the target: three at once.
        app.waitFor(20_000) {
            app.toTopOfToday()
            !gone("new-badges")
        }
        app.tap("see-badges")
        app.waitFor { app.shownIn("badges", "badge:FIRST_TICK") }
        app.waitFor { app.shownIn("badges", "badge:PERFECT_DAY") }
        app.waitFor { app.shownIn("badges", "badge:WEEKLY_TARGET") }
        app.waitFor { app.shownIn("badges", "badge-to-go:STREAK_7") }
        app.onNodeWithContentDescription("Back").performClick()

        // Seen now, so Today's card has gone; taking the tick back (Done again) doesn't take them away.
        app.waitFor { app.shownIn("today-list", "tick:Run") }
        app.tap("tick:Run")
        app.waitFor {
            app.toTopOfToday()
            textOf("weekly-target-text").contains("0 of 20")
        }
        app.waitFor { gone("new-badges") }
        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { textOf("review:badges").contains("3 of") }
    }

    @Test
    fun theTargetIsSetInSettings() {
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:weekly-target") }
        app.tap("setting:weekly-target")
        app.onNodeWithTag("setting:weekly-target").performTextClearance()
        app.onNodeWithTag("setting:weekly-target").performTextInput("200")
        app.tap("save-weekly-target")
        app.onNodeWithContentDescription("Back").performClick()
        app.waitFor { textOf("weekly-target-text").contains("0 of 200") }
    }
}
