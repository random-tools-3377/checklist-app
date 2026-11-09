package io.github.randomtools3377.checklist

import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.data.SETTING_GUIDE
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

/** Someone new gets the welcome card and the guide, whose examples can then be searched in Goals. */
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

    @Test
    fun theGuideAddsExamplesThatGoalsCanSearch() {
        app.waitFor { !gone("welcome-card") }
        app.tap("welcome-tour")
        app.waitFor { !gone("guide") }
        repeat(5) {
            app.waitFor { !gone("guide-next") }
            app.onNodeWithTag("guide-next").performClick()
        }
        app.waitFor { !gone("guide-examples") }
        app.tap("guide-examples")
        app.waitFor { "Drink water" in goalNames() }
        assertEquals(
            setOf("Drink water", "Read", "Exercise", "Morning", "Sort out a cupboard", "Tick this to-do"),
            goalNames(),
        )
        assertEquals(2, runBlocking { container.db.dao().allRewards() }.size)
        // The welcome card has been answered.
        app.waitFor { gone("welcome-card") }

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
    fun noThanksClosesTheWelcomeCard() {
        app.waitFor { !gone("welcome-card") }
        app.tap("welcome-close")
        app.waitFor { gone("welcome-card") }
        app.waitFor { runBlocking { container.db.dao().setting(SETTING_GUIDE) } == "done" }
        assertTrue(goalNames().isEmpty())
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
