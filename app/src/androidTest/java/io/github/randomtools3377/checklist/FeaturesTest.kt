package io.github.randomtools3377.checklist

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

/** Extras start off and stay out of the way; the Features page turns one on, and Settings can be searched. */
@RunWith(AndroidJUnit4::class)
class FeaturesTest {
    class OneGoal : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(OneGoal()).around(app)

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun extrasStartOffAndTurnOn() {
        app.waitFor { app.shownIn("today-list", "tick:Read") }
        // Off: no to-do box, no mood card, no timeline button.
        assert(!app.shownIn("today-list", "todo-field"))
        assert(!shown("mood-checkin"))
        assert(!shown("open-timeline"))

        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("settings-search") }
        app.onNodeWithTag("settings-search").performTextInput("privacy")
        app.waitFor { !shown("setting:features") }
        app.waitFor { shown("lock:history") }

        app.onNodeWithContentDescription("Back").performClick()
        app.waitFor { shown("settings") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("setting:features") }
        app.tap("setting:features")
        app.waitFor { shown("switch:feature:TODOS") }
        app.tap("switch:feature:TODOS")
        app.onNodeWithContentDescription("Back").performClick()
        app.onNodeWithContentDescription("Back").performClick()
        app.waitFor { app.shownIn("today-list", "todo-field") }
    }
}
