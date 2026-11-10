package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.data.SettingEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The AI helper with no Checklist AI installed, as on this test phone: the Features page and a
 * new habit both say where to get it, and nothing else changes.
 */
@RunWith(AndroidJUnit4::class)
class AiHelperTest {
    class AiHelperOn : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.db.dao().putSetting(SettingEntity(Feature.AI.key, Feature.AI.onValue))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AiHelperOn()).around(app)

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun withoutChecklistAiItSaysWhereToGetIt() {
        app.waitFor { shown("settings") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("setting:features") }
        app.tap("setting:features")
        app.waitFor { shown("switch:feature:AI") }
        app.waitFor { text("ai-status").contains("isn't installed") }
        app.waitFor { shown("ai-get") }

        // A new habit offers "Describe it", saying the same, and the usual fields are all there.
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.waitFor { shown("nav:goals") }
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { shown("add-goal") }
        app.onNodeWithTag("add-goal").performClick()
        app.waitFor { shown("new-habit") }
        app.onNodeWithTag("new-habit").performClick()
        app.waitFor { shown("ai-describe") }
        app.waitFor { shown("field:name") }
        assert(!shown("ai-fill")) { "Fill in shows only when Checklist AI is ready" }
    }
}
