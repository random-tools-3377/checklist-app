package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_FIRST_TAB
import io.github.randomtools3377.checklist.data.SETTING_HIDDEN_TABS
import io.github.randomtools3377.checklist.data.SETTING_TODAY_SECTIONS
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private fun freshGoal() = runBlocking {
    val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    container.db.clearAllTables()
    container.repository.saveHabit(
        HabitDraft(
            id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
            minutes = null, at = null, finishBy = null, finishDay = null,
        ),
    )
    container
}

/** Today's sections are shown, hidden and moved in Appearance; tabs can be hidden and still opened from Settings. */
@RunWith(AndroidJUnit4::class)
class SectionsAndTabsTest {
    class AGoal : ExternalResource() {
        override fun before() {
            freshGoal()
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AGoal()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()
    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    private fun setting(name: String): String? = runBlocking { dao.setting(name) }

    @Test
    fun aSectionIsHiddenAndATabTooButItStillOpens() {
        app.waitFor { app.shownIn("today-list", "streaks") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("section:STREAKS") }
        app.tap("section:STREAKS")
        app.waitFor { setting(SETTING_TODAY_SECTIONS)?.contains("-STREAKS") == true }
        app.tap("section-up:PROJECTS")
        app.waitFor { setting(SETTING_TODAY_SECTIONS)?.contains("PROJECTS,THIS_YEAR") == true }
        app.tap("tab:SHOP")
        app.waitFor { setting(SETTING_HIDDEN_TABS) == "SHOP" }
        app.onNodeWithContentDescription("Back").performClick()

        // Settings offers the hidden tab.
        app.waitFor { !gone("open-tab:SHOP") }
        app.onNodeWithContentDescription("Back").performClick()
        app.waitFor { !gone("today-list") }
        app.waitFor { !app.shownIn("today-list", "streaks") }
        app.waitFor { app.shownIn("today-list", "tick:Read") }
        app.waitFor { gone("nav:shop") }

        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("open-tab:SHOP") }
        app.tap("open-tab:SHOP")
        app.waitFor { !gone("add-reward") }
    }
}

/** The app opens on the tab chosen in Appearance. */
@RunWith(AndroidJUnit4::class)
class FirstTabTest {
    class OpensOnReview : ExternalResource() {
        override fun before() = runBlocking {
            freshGoal().repository.setSetting(SETTING_FIRST_TAB, "REVIEW")
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(OpensOnReview()).around(app)

    @Test
    fun theAppOpensOnTheReview() {
        app.waitFor { app.onAllNodesWithTag("review").fetchSemanticsNodes().isNotEmpty() }
    }
}
