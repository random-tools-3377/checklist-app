package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * With check-ins on, the character reacts to a tick with a line about what was just done, and
 * says why it suggests a goal.
 */
@RunWith(AndroidJUnit4::class)
class CharacterReactsTest {
    class TwoGoals : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            for (feature in listOf(Feature.CHARACTER, Feature.CHECKINS)) {
                c.db.dao().putSetting(SettingEntity(feature.key, feature.onValue))
            }
            for ((name, minutes) in listOf("Run" to 30, "Water plants" to 2)) {
                c.repository.saveHabit(
                    HabitDraft(
                        id = 0, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                        minutes = minutes, at = null, finishBy = null, finishDay = null,
                    ),
                )
            }
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoGoals()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun itSaysALineAboutWhatWasJustTicked() {
        // In the last two hours of the day it is sleepy and suggests nothing, so there is no button to tick from.
        assumeTrue("Not in the last two hours before midnight", nowLocal().hour < 22)
        app.waitFor { app.shownIn("today-list", "level-card") && shown("companion-do") }
        // The quickest goal, with the reason under its button.
        app.waitFor { text("companion-reason").contains("quickest one left") }

        app.tap("companion-do")
        app.waitFor { runBlocking { dao.allTicks() }.size == 1 }
        // For a few seconds: what was done, how far along today is, and what the streak needs.
        app.waitFor(5_000) { app.shownIn("today-list", "level-card") && text("companion-line").contains("Water plants") && text("companion-line").contains("1 of 2") }
        assert(shown("companion-face:HAPPY"))
        // Then it goes back to what holds for the day.
        app.waitFor(15_000) { app.shownIn("today-list", "level-card") && shown("companion-line") && !text("companion-line").contains("1 of 2") }
        app.waitFor { shown("companion-do") }
    }
}
