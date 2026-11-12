package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.CelebrationSounds
import io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_GLOW
import io.github.randomtools3377.checklist.data.SETTING_CELEBRATE_GLOW_AWAY
import io.github.randomtools3377.checklist.data.SETTING_REMINDER_LIGHT
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The user's own celebration sounds, the glow round the edges, and reminders that light up the screen. */
@RunWith(AndroidJUnit4::class)
class SoundsAndEdgeGlowTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun setting(name: String): String? = runBlocking { container.db.dao().setting(name) }
    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    private fun openAppearance() {
        app.waitFor { !gone("settings") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("celebrate:glow") }
    }

    @Test
    fun theGlowIsOnAndTheOffScreenOneIsOffUntilSwitched() {
        openAppearance()
        assertNull(setting(SETTING_CELEBRATE_GLOW))
        app.tap("celebrate:glow")
        app.waitFor { setting(SETTING_CELEBRATE_GLOW) == "0" }
        app.tap("celebrate:glow")
        app.waitFor { setting(SETTING_CELEBRATE_GLOW) == "1" }

        // A phone can also light its own edges for a tick made away from the app.
        assertTrue(gone("not-here:Also light the edges when the app isn't on screen"))
        app.tap("celebrate:glow-away")
        app.waitFor { setting(SETTING_CELEBRATE_GLOW_AWAY) == "1" }
    }

    @Test
    fun aPickedSoundShowsAndTheAppsSoundCanBePutBack() {
        openAppearance()
        CelebrationSounds.Kind.entries.forEach { kind ->
            val tag = "sound:${kind.name.lowercase()}"
            assertEquals("The app's sound", textOf("$tag:now"))
            assertTrue(!gone("$tag:pick"))
            assertTrue(gone("$tag:reset"))
        }

        // A sound picked for ticks, as the file dialog would leave it: copied in, and named in its setting.
        val name = "own-00000000000000aa.sound"
        container.celebrationFiles.write(name, ByteArray(32))
        runBlocking { container.repository.setCelebrationSound(CelebrationSounds.Kind.TICK, name) }
        app.waitFor { textOf("sound:tick:now") == "Your own sound" }
        assertEquals("The app's sound", textOf("sound:project:now"))
        assertNotNull(container.celebrationFiles.read(name))

        app.tap("sound:tick:reset")
        app.waitFor { setting(CelebrationSounds.Kind.TICK.setting) == "" }
        app.waitFor { textOf("sound:tick:now") == "The app's sound" }
        // Its copy goes with it.
        app.waitFor { container.celebrationFiles.read(name) == null }
    }

    @Test
    fun remindersCanLightUpTheScreen() {
        app.waitFor { !gone("settings") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:reminder-light") }
        assertTrue(gone("reminder-light:settings"))
        app.tap("setting:reminder-light")
        app.waitFor { setting(SETTING_REMINDER_LIGHT) == "1" }
        // Its row then offers the phone's own notification settings, which decide what it looks like.
        app.waitFor { !gone("reminder-light:settings") }
        app.tap("setting:reminder-light")
        app.waitFor { setting(SETTING_REMINDER_LIGHT) == "0" }
    }

    @Test
    fun aGoalsEditorOffersItsOwnSound() {
        app.waitFor { !gone("nav:goals") }
        app.onNodeWithTag("nav:goals").performClick()
        app.onNodeWithTag("add-goal").performClick()
        app.onNodeWithTag("new-habit").performClick()
        app.waitFor { !gone("sound:goal") }
        assertEquals("The app's sound", textOf("sound:goal:now"))
        assertTrue(!gone("sound:goal:pick"))
    }
}
