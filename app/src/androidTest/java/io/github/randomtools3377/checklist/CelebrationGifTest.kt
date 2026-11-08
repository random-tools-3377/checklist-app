package io.github.randomtools3377.checklist

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A goal with its own GIF shows it full screen when it is finished. */
@RunWith(AndroidJUnit4::class)
class CelebrationGifTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun finishingAGoalPlaysItsGif() {
        app.onNodeWithTag("nav:goals").performClick()
        app.onNodeWithTag("add-goal").performClick()
        app.onNodeWithTag("new-habit").performClick()
        app.onNodeWithTag("field:name").performTextInput("Stretch")
        app.onNodeWithTag("field:points").performTextInput("3")
        app.tap("celebration:star")
        app.waitFor { !gone("celebration-preview") }
        app.onNodeWithTag("save").performClick()
        app.waitFor { !gone("goal:Stretch") }

        app.onNodeWithTag("nav:today").performClick()
        app.waitFor { app.shownIn("today-list", "tick:Stretch") }
        app.tap("tick:Stretch")
        // The GIF itself, loaded and playing, not just the space for it.
        app.waitFor {
            app.onAllNodes(hasTestTag("celebration-gif") and hasContentDescription("Celebration GIF"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // A tap closes it, or it closes by itself.
        if (!gone("celebration")) {
            try {
                app.onNodeWithTag("celebration").performClick()
            } catch (e: AssertionError) {
                // Closed by itself in the meantime.
            }
        }
        app.waitFor { gone("celebration") }
    }
}
