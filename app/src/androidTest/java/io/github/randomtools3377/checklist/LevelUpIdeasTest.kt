package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Ideas
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SkillArea
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toCore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The character page offers ideas to level up each skill: a new activity opens the goal editor
 * filled in, "More ideas" brings others, "Not for me" hides one, and a goal steps up.
 */
@RunWith(AndroidJUnit4::class)
class LevelUpIdeasTest {
    class ARunner : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.db.dao().putSetting(SettingEntity(Feature.CHARACTER.key, Feature.CHARACTER.onValue))
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Run", icon = "🏃", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = 30, at = null, finishBy = null, finishDay = null, skill = SkillArea.FITNESS,
                ),
            )
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ARunner()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun goals() = runBlocking { dao.activeGoals() }.map { it.toCore() }

    @Test
    fun ideasLevelUpASkill() {
        val run = goals().single()
        app.waitFor { app.shownIn("today-list", "level-card") }
        app.tap("level-card")
        app.waitFor { shown("level-up-next") }
        app.tap("skill-ideas:FITNESS")

        // A new activity: the editor opens filled in, with its skill, and saving adds it.
        val walk = "idea:fitness:walk10minutes"
        app.waitFor { shown(walk) }
        assert(shown("step-up:${run.id}"))
        app.tap("$walk:use")
        app.waitFor { shown("field:name") }
        app.tap("save")
        app.waitFor { goals().any { it.name == "Walk 10 minutes" && it.minutes == 10 && it.skill == SkillArea.FITNESS && it.points > 0 } }
        app.waitFor { shown("ideas") && !shown(walk) }

        // More ideas bring others; "Not for me" hides one.
        val first = Ideas.library(SkillArea.FITNESS, 1, goals(), emptySet()).first().key
        app.waitFor { shown("idea:$first") }
        app.tap("ideas-more")
        app.waitFor { !shown("idea:$first") }
        val now = Ideas.page(Ideas.library(SkillArea.FITNESS, 1, goals(), emptySet()), 1).first().key
        app.waitFor { shown("idea:$now") }
        app.tap("idea:$now:hide")
        app.waitFor { now in Ideas.decodeHidden(runBlocking { dao.setting(Ideas.HIDDEN) }) }

        // Stepping up a goal: the editor opens on it with more time, from now on.
        app.tap("step-up:${run.id}:use")
        app.waitFor { shown("field:minutes") }
        app.tap("save")
        app.waitFor { goals().first { it.id == run.id }.minutes == 40 }
    }
}
