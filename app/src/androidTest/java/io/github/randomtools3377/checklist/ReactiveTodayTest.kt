package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Today reacting to how the day goes: the closest win near the top, a tick that says what it
 * changed, Undo that says what went back, the thin bar on the pace card, and the recap once
 * everything is done.
 */
@RunWith(AndroidJUnit4::class)
class ReactiveTodayTest {
    class TwoGoals : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            listOf(Feature.PERFECT_DAY, Feature.COMBOS).forEach { c.db.dao().putSetting(SettingEntity(it.key, it.onValue)) }
            listOf("Read" to "📖", "Gym" to "🏋️").forEach { (name, icon) ->
                c.repository.saveHabit(
                    HabitDraft(
                        id = 0, name = name, icon = icon, points = 10, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                        minutes = null, at = null, finishBy = null, finishDay = null,
                    ),
                )
            }
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoGoals()).around(app)

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    private fun shows(words: String): Boolean = app.onAllNodesWithText(words).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aTickSaysWhatItChangedAndADoneDayGetsARecap() {
        app.waitFor { app.shownIn("today-list", "tick:Read") }
        // The closest win, and what a perfect day today is worth.
        app.toTopOfToday()
        app.waitFor { text("near-win").contains("2 more for a perfect day") }
        app.waitFor { app.shownIn("today-list", "combo-line") }
        app.waitFor { text("combo-line").contains("A perfect day today starts a new combo") }

        // The tick says how many are left, and the bar on the pace card fills.
        app.tapIn("today-list", "tick:Read")
        app.waitFor { shows("Read · +10 · 1 left today") }
        app.waitFor { app.shownIn("today-list", "pace-done") }
        app.waitFor { text("pace-done").contains("1 of 2 done") }

        // Undo says what went back.
        app.waitFor { shows("Undo") }
        app.onNodeWithText("Undo").performClick()
        app.waitFor { shows("Read taken back · −10 · 2 left today") }
        app.waitFor { app.shownIn("today-list", "pace-done") }
        app.waitFor { text("pace-done").contains("0 of 2 done") }

        // Both done: the recap, with what tonight adds.
        app.tapIn("today-list", "tick:Read")
        app.waitFor { shows("Read · +10 · 1 left today") }
        app.tapIn("today-list", "tick:Gym")
        app.waitFor { app.shownIn("today-list", "day-recap") }
        app.waitFor { text("day-recap-headline").contains("2 done") }
        app.waitFor { text("day-recap-tonight").contains("perfect day +10") }
    }
}

/** A day off: there are goals, only none of them today, so Today shows what's next. */
@RunWith(AndroidJUnit4::class)
class DayOffTest {
    class AGoalFromTomorrow : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date.plusDays(1)), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AGoalFromTomorrow()).around(app)

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun aDayOffShowsWhatIsNext() {
        app.waitFor { app.shownIn("today-list", "today-empty") }
        app.waitFor { text("today-empty").contains("Nothing due today. Enjoy it.") }
        app.waitFor { text("today-empty-more").contains("Tomorrow: 📖 Read") }
        // No "Add a goal" here: there are goals already.
        assertTrue(app.onAllNodesWithTag("today-add-goal").fetchSemanticsNodes().isEmpty())
    }
}
