package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SkillArea
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The character: a level on Today from what goals paid, each skill's level, and a goal's skill picked in its editor. */
@RunWith(AndroidJUnit4::class)
class CharacterTest {
    class ARunTicked : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.db.dao().putSetting(SettingEntity(Feature.CHARACTER.key, Feature.CHARACTER.onValue))
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Morning run", icon = "🏃", points = 60, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            c.book.tickHabit(c.db.dao().activeGoals().single().id)
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ARunTicked()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun aTickLevelsUpTheCharacterAndItsSkill() {
        // 60 points paid: level 2 (50 needed), and Fitness, guessed from "run", is level 3 (60 of 20 + 40).
        app.waitFor { app.shownIn("today-list", "level-card") }
        app.tap("level-card")
        app.waitFor { shown("character") }
        app.waitFor { text("character-level").contains("Level 2") }
        app.waitFor { text("skill-level:FITNESS").contains("Level 3") }
        app.waitFor { text("skill-level:MIND").contains("Level 1") }

        // The character is drawn, and its style is kept.
        app.waitFor { shown("character-figure") }
        app.tap("hair:CURLY")
        app.tap("skin:4")
        app.waitFor { runBlocking { dao.setting(io.github.randomtools3377.checklist.core.Looks.SETTING) }?.startsWith("4,CURLY,") == true }

        // A tap on the character gets a line: how far to the next level (150 in all), then to the next gear (Fitness's trainers, at 200).
        app.tap("character-figure")
        app.waitFor { text("character-says").contains("90 more experience") && text("character-says").contains("level 3") }
        app.tap("character-figure")
        app.waitFor { text("character-says").contains("Fitness needs 140 more experience for the trainers") }

        // The goal's skill, changed in its editor, is saved.
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.waitFor { shown("nav:goals") }
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { shown("goal:Morning run") }
        app.editGoal("Morning run")
        app.waitFor { shown("skill:HEALTH") }
        app.tap("skill:HEALTH")
        app.tap("save")
        app.waitFor { runBlocking { dao.activeGoals().single().skill } == SkillArea.HEALTH.name }
    }
}
