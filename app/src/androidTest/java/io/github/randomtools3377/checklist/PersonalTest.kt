package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_SOUND
import io.github.randomtools3377.checklist.data.SETTING_CONFETTI
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_GIF
import io.github.randomtools3377.checklist.data.SETTING_FONT
import io.github.randomtools3377.checklist.data.SETTING_POINTS_NAME
import io.github.randomtools3377.checklist.data.SETTING_WIDGET_STYLE
import io.github.randomtools3377.checklist.widget.StatsWidget
import io.github.randomtools3377.checklist.widget.WidgetStyle
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Points under another name, a bundled font, and the celebration settings. */
@RunWith(AndroidJUnit4::class)
class PersonalTest {
    class CoinsAndAGoal : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            container.repository.setSetting(SETTING_POINTS_NAME, "coin|coins|🪙")
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(CoinsAndAGoal()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()
    // The whole tree: the celebration GIF sits inside the overlay that closes with a tap.
    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun setting(name: String): String? = runBlocking { dao.setting(name) }
    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun pointsGoByTheirNewName() {
        app.waitFor { textOf("balance") == "0 coins 🪙" }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("points-name:stars") }
        app.tap("points-name:stars")
        app.waitFor { setting(SETTING_POINTS_NAME) == "star|stars|⭐" }
        app.tap("font:Rounded")
        app.waitFor { setting(SETTING_FONT) == "ROUNDED" }
        app.onNodeWithContentDescription("Back").performClick()
        app.onNodeWithContentDescription("Back").performClick()
        app.waitFor { textOf("balance") == "0 stars ⭐" }
    }

    @Test
    fun theAppIconAndWidgetStyleAreChosen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("app-icon:SUNSET") }
        app.tap("app-icon:SUNSET")
        app.waitFor { AppIcons.current(context) == AppIcon.SUNSET }
        app.tap("widget-style:See-through")
        app.waitFor { setting(SETTING_WIDGET_STYLE) == "SEE_THROUGH" }
        app.waitFor { runBlocking { StatsWidget.load(context) }.look.style == WidgetStyle.SEE_THROUGH }
        // Back to the usual icon, for the other tests.
        app.tap("app-icon:GARDEN")
        app.waitFor { AppIcons.current(context) == AppIcon.GARDEN }
    }

    @Test
    fun everyGoalGetsTheChosenGif() {
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("celebrate:sound") }
        app.tap("celebrate:sound")
        app.waitFor { setting(SETTING_CELEBRATE_SOUND) == "1" }
        app.tap("confetti:None")
        app.waitFor { setting(SETTING_CONFETTI) == "NONE" }
        app.tap("default-gif:star")
        app.waitFor { setting(SETTING_DEFAULT_GIF) == "star" }
        app.onNodeWithContentDescription("Back").performClick()
        app.onNodeWithContentDescription("Back").performClick()

        app.waitFor { app.shownIn("today-list", "tick:Read") }
        app.tap("tick:Read")
        app.waitFor { !gone("celebration-gif") }
    }
}
