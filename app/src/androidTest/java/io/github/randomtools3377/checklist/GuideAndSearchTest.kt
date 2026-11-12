package io.github.randomtools3377.checklist

import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_GUIDE
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
private val container get() = (context.applicationContext as ChecklistApplication).container

/** The tour from Settings, whose examples can then be searched in Goals, and Undo after adding them. */
@RunWith(AndroidJUnit4::class)
class GuideAndSearchTest {
    class Empty : ExternalResource() {
        override fun before() = runBlocking { container.db.clearAllTables() }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Empty()).around(app)

    private fun goalNames() = runBlocking { container.db.dao().allGoals() }.map { it.name }.toSet()
    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    /** Opens "How the app works" from Settings and goes to its last page. */
    private fun openTourToTheEnd() {
        app.waitFor { !gone("settings") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:guide") }
        app.tap("setting:guide")
        app.waitFor { !gone("guide") }
        // Seven pages, the last but one about the extras in Settings → Features.
        repeat(6) {
            app.waitFor { !gone("guide-next") }
            app.onNodeWithTag("guide-next").performClick()
        }
        app.waitFor { !gone("guide-examples") }
    }

    @Test
    fun theGuideAddsExamplesThatGoalsCanSearch() {
        // No welcome card or tip pops up on Today by itself.
        app.waitFor { !gone("today-list") }
        assertTrue(gone("welcome-card"))
        openTourToTheEnd()
        app.onNodeWithTag("guide-scratch").assertTextContains("Start from scratch")
        // With no goals yet, the examples come straight away.
        app.tap("guide-examples")
        app.waitFor { "Drink water" in goalNames() }
        assertEquals(
            setOf("Drink water", "Read", "Exercise", "Morning", "Sort out a cupboard", "Tick this to-do"),
            goalNames(),
        )
        assertEquals(2, runBlocking { container.db.dao().allRewards() }.size)
        app.waitFor { runBlocking { container.db.dao().setting(SETTING_GUIDE) } == "done" }

        // Search in the Goals list.
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal-search") }
        app.onNodeWithTag("goal-search").performTextInput("water")
        app.waitFor { gone("goal:Read") }
        assertTrue(!gone("goal:Drink water"))
        app.onNodeWithTag("goal-search").performTextInput("zzz")
        app.waitFor { !gone("no-match") }
    }

    @Test
    fun withGoalsTheTourAsksFirstAndUndoTakesTheExamplesBack() {
        runBlocking {
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Stretch", icon = "🧘", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
        openTourToTheEnd()
        // "Start from scratch" is "Done" for someone with goals.
        app.onNodeWithTag("guide-scratch").assertTextContains("Done")
        app.tap("guide-examples")
        app.waitFor { !gone("guide-examples-ok") }
        assertTrue(app.onAllNodesWithText("This adds 5 example goals, a to-do and 2 rewards to your lists.", substring = true).fetchSemanticsNodes().isNotEmpty())
        app.onNodeWithTag("guide-examples-ok").performClick()
        app.waitFor { "Drink water" in goalNames() }
        // Undo takes them all away again, and leaves the user's own goal.
        app.waitFor { app.onAllNodesWithText("Undo").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithText("Undo").performClick()
        app.waitFor { goalNames() == setOf("Stretch") }
        assertEquals(0, runBlocking { container.db.dao().allRewards() }.size)
    }
}

/** The shortcuts on the app's icon, and the screens they open. */
@RunWith(AndroidJUnit4::class)
class ShortcutsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun empty() = runBlocking { container.db.clearAllTables() }

    @Test
    fun theIconHasFourShortcuts() {
        val shortcuts = context.getSystemService(ShortcutManager::class.java).manifestShortcuts
        assertEquals(listOf("new_todo", "new_goal", "plan", "shop"), shortcuts.sortedBy { it.rank }.map { it.id })
        assertEquals("new-todo", shortcuts.first { it.id == "new_todo" }.intent?.getStringExtra(MainActivity.EXTRA_OPEN))
    }

    @Test
    fun newToDoOpensTheToDoEditor() {
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "new-todo")
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitFor { compose.onAllNodesWithTag("field:name").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("field:name").performTextInput("Post the letter")
            compose.onNodeWithTag("save").performClick()
            compose.waitFor { runBlocking { container.db.dao().allGoals() }.any { it.name == "Post the letter" && it.kind == "TODO" } }
        }
    }
}
