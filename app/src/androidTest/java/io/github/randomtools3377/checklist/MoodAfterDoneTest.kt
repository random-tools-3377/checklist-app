package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_MOOD_AFTER_CLOSED
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.data.toEntity
import io.github.randomtools3377.checklist.data.toEpochDay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * With the mood check-in on, a low morning suggests the goal that usually lifts the user, and
 * once everything due today is done, Today asks how they feel now and answers with how the mood
 * moved, the goals that lift them, and "Mood and your goals".
 */
@RunWith(AndroidJUnit4::class)
class MoodAfterDoneTest {
    /** Two goals due today, and six days before today: on days Walk was done the mood was 4, on the others 2. */
    class TwoGoalsAndAWeekOfMoods : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.setSetting(Feature.MOOD.key, Feature.MOOD.onValue)
            val today = c.book.today()
            for (name in listOf("Read", "Walk")) {
                c.repository.saveHabit(
                    HabitDraft(
                        id = 0, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(today.minusDays(10)), target = 1, bonus = 0,
                        minutes = null, at = null, finishBy = null, finishDay = null,
                    ),
                )
            }
            val walk = c.db.dao().allGoals().first { it.name == "Walk" }
            val read = c.db.dao().allGoals().first { it.name == "Read" }
            val dayMillis = 24L * 60 * 60 * 1000
            for (ago in 1..6) {
                val day = today.minusDays(ago)
                val at = nowMillis() - ago * dayMillis
                val walked = ago <= 3
                c.db.dao().putMood(Mood(day, if (walked) 4 else 2, at = at).toEntity())
                val goals = if (walked) listOf(walk.id, read.id) else listOf(read.id)
                c.db.dao().insertTicks(goals.map { Tick(goalId = it, stepId = null, at = at, day = day, period = Period.DAILY, periodStart = day).toEntity() })
            }
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoGoalsAndAWeekOfMoods()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private val dao get() = container.db.dao()
    private val today get() = runBlocking { container.book.today() }

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    private fun todaysMood() = runBlocking { dao.allMoods() }.firstOrNull { it.day == today.toEpochDay() }

    private fun ticksToday() = runBlocking { dao.allTicks() }.count { it.day == today.toEpochDay() }

    private fun tickBoth() {
        for (name in listOf("Read", "Walk")) {
            app.waitFor { app.shownIn("today-list", "tick:$name") }
            app.tapIn("today-list", "tick:$name")
        }
        app.waitFor { ticksToday() == 2 }
        app.toTopOfToday()
    }

    @Test
    fun aLowMorningThenEverythingDoneAsksAgainAndShowsWhatLifts() {
        // A low morning: the usual card, then a goal that usually lifts, to start with.
        app.waitFor { shown("mood:2") }
        assert(!shown("mood-after"))
        app.tap("mood:2")
        app.waitFor { todaysMood()?.score == 2 }
        app.waitFor { shown("mood-start-with") && text("mood-start-with").contains("Walk") }

        // Everything done: how does it feel now?
        tickBoth()
        app.waitFor { shown("mood-after") }
        assert(text("mood-after-question").contains("You started the day feeling low"))
        assert(!shown("mood-start-with"))
        app.tap("mood-after:4")
        app.waitFor { todaysMood()?.afterScore == 4 }
        assert(todaysMood()?.score == 2) { "the morning's mood stays" }

        // The answer: how it moved, the goals that lift, and the page about them.
        app.waitFor { shown("mood-after-answer") }
        assert(text("mood-after-line").contains("Up from"))
        app.waitFor { text("mood-after-lifts").contains("Walk") }
        assert(text("mood-today").contains("→"))
        app.tap("mood-goals-open")
        app.waitFor { shown("mood-goals") }
        app.waitFor { shown("mood-lift:Walk") }

        // Asked once a day.
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.waitFor { shown("mood-after-answer") }
        assert(!shown("mood-after"))
    }

    @Test
    fun notNowPutsItOffUntilTomorrow() {
        app.waitFor { shown("mood:3") }
        app.tap("mood:3")
        app.waitFor { todaysMood()?.score == 3 }
        // Not a low morning: no suggestion.
        assert(!shown("mood-start-with"))

        tickBoth()
        app.waitFor { shown("mood-after") }
        assert(!text("mood-after-question").contains("feeling low"))
        app.tap("mood-after-later")
        app.waitFor { !shown("mood-after") }
        app.waitFor { runBlocking { dao.setting(SETTING_MOOD_AFTER_CLOSED) } == today.toString() }
        assert(todaysMood()?.afterScore == null)
        assert(!shown("mood-after-answer"))
    }
}
