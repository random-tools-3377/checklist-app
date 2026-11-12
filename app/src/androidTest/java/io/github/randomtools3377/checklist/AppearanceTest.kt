package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_COLOR
import io.github.randomtools3377.checklist.data.SETTING_RECENT_COLORS
import io.github.randomtools3377.checklist.data.SETTING_THEME
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Settings → Appearance: the theme, a ready-made colour, any colour from the picker, and a goal's own colour. */
@RunWith(AndroidJUnit4::class)
class AppearanceTest {
    class AGoal : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AGoal()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    private fun setting(name: String): String? = runBlocking { dao.setting(name) }

    @Test
    fun theThemeAndColourAreChosenInAppearance() {
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("theme:BLACK") }
        app.tap("theme:BLACK")
        app.waitFor { setting(SETTING_THEME) == "BLACK" }
        app.tap("colour:Ocean")
        app.waitFor { setting(SETTING_COLOR) == "#2F80C0" }

        // Any colour, typed as a hex code.
        app.tap("colour:Any colour…")
        app.waitFor { !gone("field:hex") }
        app.onNodeWithTag("field:hex").performTextClearance()
        app.onNodeWithTag("field:hex").performTextInput("#7A3FB0")
        app.tap("use-colour")
        app.waitFor { setting(SETTING_COLOR) == "#7A3FB0" }
        app.waitFor { setting(SETTING_RECENT_COLORS)?.contains("#7A3FB0") == true }
        app.waitFor { !gone("colour:Yours") }
        app.tap("theme:PHONE")
        app.waitFor { setting(SETTING_THEME) == "PHONE" }
    }

    @Test
    fun aGoalGetsItsOwnColour() {
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal:Read") }
        app.editGoal("Read")
        app.waitFor { !gone("goal-colour:#D9822B") }
        app.tap("goal-colour:#D9822B")
        app.tap("save")
        app.waitFor { runBlocking { dao.allGoals().first { it.name == "Read" }.color } == 0xFFD9822B.toInt() }

        // Any colour, from the picker.
        app.editGoal("Read")
        app.waitFor { !gone("goal-colour:any") }
        app.tap("goal-colour:any")
        app.waitFor { !gone("field:hex") }
        app.onNodeWithTag("field:hex").performTextClearance()
        app.onNodeWithTag("field:hex").performTextInput("#123456")
        app.onNodeWithTag("use-goal-colour").performClick()
        app.tap("save")
        app.waitFor { runBlocking { dao.allGoals().first { it.name == "Read" }.color } == 0xFF123456.toInt() }
    }
}
