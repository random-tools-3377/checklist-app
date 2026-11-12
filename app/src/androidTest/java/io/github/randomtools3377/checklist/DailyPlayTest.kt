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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Daily quests and the race against last week, on Today. */
@RunWith(AndroidJUnit4::class)
class DailyPlayTest {
    class TwoGoalsOneTicked : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            listOf(Feature.QUESTS, Feature.RACE).forEach { c.db.dao().putSetting(SettingEntity(it.key, it.onValue)) }
            listOf("Read" to "📖", "Gym" to "🏋️").forEach { (name, icon) ->
                c.repository.saveHabit(
                    HabitDraft(
                        id = 0, name = name, icon = icon, points = 10, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                        minutes = null, at = null, finishBy = null, finishDay = null,
                    ),
                )
            }
            c.book.tickHabit(c.db.dao().activeGoals().first { it.name == "Read" }.id)
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoGoalsOneTicked()).around(app)

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun todayShowsTheQuestsAndTheRace() {
        app.waitFor { app.shownIn("today-list", "quests") }
        app.waitFor { app.onAllNodesWithTag("quest:2", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        app.waitFor { app.shownIn("today-list", "race") }
        // 10 points this week, none last week.
        app.waitFor { text("race-standing").contains("ahead of last week") }

        // Each game card has a small ⓘ that opens its guide.
        app.waitFor { app.shownIn("today-list", "quests") }
        app.tap("guide:QUESTS")
        app.waitFor { app.onAllNodesWithTag("help:QUESTS", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        app.tap("help-ok")
        app.waitFor { app.onAllNodesWithTag("help:QUESTS", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
}
