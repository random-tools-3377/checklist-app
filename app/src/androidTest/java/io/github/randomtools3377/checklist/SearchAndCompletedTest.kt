package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The Settings search finds extras and the look and opens them, and says when nothing is found;
 * a habit whose last day has passed moves under Completed; an extra's section and settings stay
 * out of the way until it is on.
 */
@RunWith(AndroidJUnit4::class)
class SearchAndCompletedTest {
    class TwoHabits : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            val today = nowLocal().date
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(today), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            // Its last day was yesterday.
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Old walk", icon = "🚶", points = 3, repeat = Repeat.everyDay(today.minusDays(10)).copy(end = today.minusDays(1)),
                    target = 1, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoHabits()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun search(words: String) {
        app.onNodeWithTag("settings-search").performTextClearance()
        app.onNodeWithTag("settings-search").performTextInput(words)
    }

    @Test
    fun theSearchFindsExtrasAndOpensThemOrSaysNothingFound() {
        app.waitFor { app.shownIn("today-list", "tick:Read") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("settings-search") }

        // A plural finds the extra, and its chip opens the Features page there.
        search("quests")
        app.waitFor { shown("search-hit:feature:QUESTS") }
        app.tap("search-hit:feature:QUESTS")
        app.waitFor { shown("switch:feature:QUESTS") }
        app.onNodeWithContentDescription("Back").performClick()

        // The look, and the Privacy section for the mood journal.
        app.waitFor { shown("settings-search") }
        search("dark mode")
        app.waitFor { shown("search-hit:look:Theme") }
        search("journal")
        app.waitFor { shown("search-hit:feature:MOOD") && shown("lock:reasons") && shown("privacy-moods") }

        // Nothing found: Help and tips is a tap away.
        search("zzzz")
        app.waitFor { shown("settings-nothing-found") }
        app.tap("settings-nothing-help")
        app.waitFor { !shown("settings-search") }
    }

    @Test
    fun aHabitWhoseLastDayPassedIsUnderCompleted() {
        app.waitFor { app.shownIn("today-list", "tick:Read") }
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { shown("goal:Read") }
        assert(!shown("goal:Old walk")) { "A habit whose last day has passed is out of the main list" }
        app.waitFor { shown("completed-goals") }
        assert(!shown("completed:Old walk")) { "Completed starts closed" }
        app.tap("completed-goals")
        app.waitFor { shown("completed:Old walk") }
        // Extend opens it, to move its end date on.
        app.tap("extend:Old walk")
        app.waitFor { shown("field:name") && shown("field:end") }
    }

    @Test
    fun anExtrasSectionAndSettingsShowOnceItIsOn() {
        app.waitFor { app.shownIn("today-list", "tick:Read") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("settings-search") }
        // The treat budget is for suggested prices.
        assert(!shown("field:treat-budget"))
        app.tap("setting:appearance")
        app.waitFor { shown("section:STREAKS") }
        assert(!shown("section:QUESTS")) { "Daily quests are off" }
        assert(!shown("section:MOOD")) { "The mood check-in is off" }
        runBlocking { container.repository.setSetting(Feature.QUESTS.key, Feature.QUESTS.onValue) }
        app.waitFor { shown("section:QUESTS") }
        runBlocking { container.repository.setSetting(Feature.SUGGESTIONS.key, Feature.SUGGESTIONS.onValue) }
        app.onNodeWithContentDescription("Back").performClick()
        app.waitFor { shown("field:treat-budget") }
    }
}
