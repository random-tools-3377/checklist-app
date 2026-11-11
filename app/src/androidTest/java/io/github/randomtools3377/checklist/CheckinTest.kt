package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Checkins
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * With check-ins on, the character asks how the user feels, answers a hard day gently with one
 * small goal to do from its card, and cheers a new level until it is seen.
 */
@RunWith(AndroidJUnit4::class)
class CheckinTest {
    class TwoGoals : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            for (feature in listOf(Feature.CHARACTER, Feature.CHECKINS, Feature.MOOD)) {
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

    /** A button's words, which sit on the text inside it: read as one, the way they are shown. */
    private fun label(tag: String): String = app.onAllNodesWithTag(tag).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun theCharacterChecksInAndHelpsGetGoing() {
        // It asks how the user feels, on its own card instead of the separate mood card.
        app.waitFor { app.shownIn("today-list", "level-card") }
        app.waitFor { shown("companion-mood:2") }
        assert(!shown("mood-checkin"))

        // A hard day: gentle, and one small thing, the quickest goal.
        app.tap("companion-mood:2")
        app.waitFor { runBlocking { dao.allMoods() }.singleOrNull()?.score == 2 }
        app.waitFor { shown("companion-do") && label("companion-do").contains("Water plants") }
        assert(shown("companion-face:CALM"))
        app.tap("companion-do")
        app.waitFor { runBlocking { dao.allTicks() }.size == 1 }

        // Done, it counts as a win, says so, and moves on rather than repeating itself.
        app.waitFor { shown("companion-wins") && text("companion-wins").contains("1 small win") }
        assert(shown("companion-face:HAPPY"))
        assert(!text("companion-line").contains("One small step is plenty"))

        // A new level: it jumps for joy until the cheer is seen.
        runBlocking { dao.putSetting(SettingEntity(Checkins.LEVEL_SEEN, "0")) }
        app.waitFor { shown("companion-cheered") }
        assert(text("companion-line").contains("evel"))
        assert(shown("companion-face:PROUD"))
        app.tap("companion-cheered")
        app.waitFor { !shown("companion-cheered") }
        app.waitFor { (runBlocking { dao.setting(Checkins.LEVEL_SEEN) }?.toIntOrNull() ?: 0) >= 1 }
    }
}
