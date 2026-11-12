package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Rules
import io.github.randomtools3377.checklist.ui.SETTING_TIPS_ON
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The ⓘ guide on Today, every guide in Help, and a number changed on the Points and rules page. */
@RunWith(AndroidJUnit4::class)
class RulesAndHelpTest {
    class Fresh : ExternalResource() {
        override fun before() = runBlocking {
            (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.clearAllTables()
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Fresh()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aGuideOpensAndARuleChanges() {
        // Today's guide.
        app.waitFor { shown("help") }
        app.onNodeWithTag("help").performClick()
        app.waitFor { shown("help:TODAY") }
        app.onNodeWithTag("help-ok").performClick()
        app.waitFor { !shown("help:TODAY") }

        // The penalty for going over the limit, from half to all of a goal's points.
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("setting:rules") }
        app.tap("setting:rules")
        app.waitFor { app.shownIn("rules-list", "rule-field:penaltyPercent") }
        app.onNodeWithTag("rule-field:penaltyPercent").performTextReplacement("100")
        app.waitFor { Rules.decode(runBlocking { container.db.dao().setting(Rules.SETTING) }).penaltyPercent == 100 }
        app.waitFor { shown("rule-reset:penaltyPercent") }
        app.tap("rule-reset:penaltyPercent")
        app.waitFor { Rules.decode(runBlocking { container.db.dao().setting(Rules.SETTING) }).penaltyPercent == 50 }
    }

    @Test
    fun helpListsEveryGuideInGroupsWithSearch() {
        app.waitFor { shown("settings") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("setting:help") }
        app.tap("setting:help")
        app.waitFor { shown("help-group:BASICS") }
        // Tips the first time start off, so guides don't pop up by themselves.
        assertFalse(runBlocking { container.db.dao().setting(SETTING_TIPS_ON) } == "1")

        // The search looks in the guides' text.
        app.onNodeWithTag("help-search").performTextInput("snooze")
        app.waitFor { app.shownIn("help-list", "help-topic:REMINDERS") }
        app.waitFor { !shown("help-topic:TODAY") }
        app.tap("help-topic:REMINDERS")
        // "See also" opens a guide the search had left out.
        app.waitFor { app.shownIn("help-list", "see-also:PRIVACY") }
        app.tap("see-also:PRIVACY")
        app.waitFor { app.shownIn("help-list", "help-topic:PRIVACY") }
        app.waitFor { app.shownIn("help-list", "see-also:MOOD_JOURNAL") }

        // Only extras that are on: the quests guide goes while quests are off.
        app.onNodeWithTag("help-list").performScrollToIndex(0)
        app.tap("help-only-on")
        app.waitFor { !app.shownIn("help-list", "help-topic:QUESTS") }
        assertTrue(app.shownIn("help-list", "help-topic:TODAY"))
    }
}
