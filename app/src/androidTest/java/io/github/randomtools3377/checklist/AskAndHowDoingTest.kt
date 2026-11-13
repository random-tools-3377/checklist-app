package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

/**
 * "Ask a question" in Help and "How am I doing?" on Today, with no AI at all, as on this test
 * phone: the app answers from its own numbers and guides, with its figures, links and buttons.
 */
@RunWith(AndroidJUnit4::class)
class AskAndHowDoingTest {
    class TwoGoals : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            for (name in listOf("Read", "Stretch")) {
                container.repository.saveHabit(
                    HabitDraft(
                        id = 0, name = name, icon = "⭐", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                        minutes = null, at = null, finishBy = null, finishDay = null,
                    ),
                )
            }
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoGoals()).around(app)

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun askAnswersFromTheAppsNumbersAndGuides() {
        // Without the AI helper there is no Ask button on Today: it is in Help.
        app.waitFor { shown("settings") }
        assert(!shown("ask-open")) { "The Ask button on Today comes with the AI helper" }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("setting:help") }
        app.tap("setting:help")
        app.waitFor { shown("help-ask") }
        app.tap("help-ask")
        app.waitFor { shown("ask-box") }
        app.waitFor { shown("ask-suggestion:0") }

        // A question about the day: the app's own answer, with its figures.
        app.onNodeWithTag("ask-box").performTextInput("What's left today?")
        app.tap("ask-send")
        app.waitFor { text("ask-answer:0:text").contains("2 of 2 are left today") }
        app.waitFor { shown("ask-answer:0:figures") }

        // A question about the app: the guide's words, and the guide it came from.
        app.onNodeWithTag("ask-box").performTextInput("What does a skip cost?")
        app.tap("ask-send")
        app.waitFor { app.shownIn("ask", "ask-answer:1:guide:SKIPS_AND_MISSES") }
        app.tap("ask-answer:1:guide:SKIPS_AND_MISSES")
        app.waitFor { shown("help:SKIPS_AND_MISSES") }
        app.onNodeWithTag("help-ok").performClick()
        app.waitFor { !shown("help:SKIPS_AND_MISSES") }

        // "Done" and a goal typed here asks before ticking, like by voice.
        app.onNodeWithTag("ask-box").performTextInput("done reading")
        app.tap("ask-send")
        app.waitFor { shown("voice-tick") }
        app.onNodeWithTag("voice-tick").performClick()
        app.waitFor { !shown("voice-tick") }
        app.onNodeWithTag("ask-box").performTextInput("what is left")
        app.tap("ask-send")
        app.waitFor { app.shownIn("ask", "ask-answer:2:text") }
        app.waitFor { text("ask-answer:2:text").contains("1 of 2 is left today: Stretch.") }
    }

    @Test
    fun howAmIDoingSumsUpTheDayWithANextStep() {
        app.waitFor { app.shownIn("today-list", "how-doing-open") }
        app.tap("how-doing-open")
        app.waitFor { shown("how-doing-sheet") }
        app.waitFor { text("how-doing-line").contains("Today: 0 of 2 done, 2 left.") }
        app.waitFor { text("how-doing-line").contains("is next on today's list.") }
        // No AI here, so there is nothing to put into words.
        assert(!shown("how-doing-say")) { "Say it in words comes with the AI helper" }
        app.onNodeWithTag("how-doing-close").performClick()
        app.waitFor { !shown("how-doing-sheet") }
    }
}
