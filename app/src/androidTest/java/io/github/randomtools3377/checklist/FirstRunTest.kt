package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DAY_START
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.ui.FirstRun
import io.github.randomtools3377.checklist.ui.SetupStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

/** The first-run setup for someone new: what to use, a first goal, and the day's start and end. */
@RunWith(AndroidJUnit4::class)
class FirstRunTest {
    /** Under the app's tests the setup only shows when a test starts it, as here. */
    class Started : ExternalResource() {
        override fun before() = runBlocking {
            container.db.clearAllTables()
            container.db.dao().putSetting(SettingEntity(FirstRun.SETTING, SetupStep.PARTS.name))
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Started()).around(app)

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun setting(name: String) = runBlocking { container.db.dao().setting(name) }
    private fun on(feature: Feature) = feature.isOn(setting(feature.key))
    private fun goalNames() = runBlocking { container.db.dao().allGoals() }.map { it.name }

    @Test
    fun aNewUserWalksThroughTheSetup() {
        // The welcome, and the main parts to use.
        app.waitFor { shown("setup-step:PARTS") }
        assertTrue(shown("setup-welcome"))
        app.tap("setup-part:TODOS")
        app.tap("setup-part:MOOD")
        app.tap("setup-next")
        app.waitFor { on(Feature.TODOS) && on(Feature.MOOD) }
        assertFalse(on(Feature.BUDGET))
        assertFalse(on(Feature.CHARACTER))

        // A first goal, with one tap.
        app.waitFor { shown("setup-step:GOAL") }
        assertTrue(shown("setup-skip"))
        app.tap("setup-goal:Read")
        app.waitFor { goalNames() == listOf("Read") }
        app.waitFor { shown("setup-next") }
        app.tap("setup-next")

        // When the day starts and ends.
        app.waitFor { shown("setup-step:DAY") }
        app.tap("setup-start:7")
        app.tap("setup-end:2")
        app.tap("setup-done")
        app.waitFor { setting(FirstRun.SETTING) == FirstRun.DONE }
        assertEquals((7 * 60).toString(), setting(SETTING_DAY_START))
        assertEquals("2", setting(SETTING_DAY_END_HOUR))

        // Then Today.
        app.waitFor { shown("today-list") }
        assertFalse(shown("setup"))
        assertEquals(listOf("Read"), goalNames())
    }

    @Test
    fun everyStepCanBeSkipped() {
        app.waitFor { shown("setup-step:PARTS") }
        app.tap("setup-part:BUDGET")
        app.tap("setup-skip")
        app.waitFor { shown("setup-step:GOAL") }
        app.tap("setup-skip")
        app.waitFor { shown("setup-step:DAY") }
        app.tap("setup-end:3")
        app.tap("setup-skip")
        app.waitFor { shown("today-list") }
        assertEquals(FirstRun.DONE, setting(FirstRun.SETTING))
        // Skipping changes nothing.
        assertTrue(goalNames().isEmpty())
        assertFalse(on(Feature.BUDGET))
        assertTrue(setting(SETTING_DAY_END_HOUR) == null)
    }
}
