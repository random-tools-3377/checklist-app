package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.LessMotion
import io.github.randomtools3377.checklist.core.SwipeTick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * "Less motion" and "Swipe a card to tick it" in Appearance. Less motion starts on the phone's
 * own setting and is only kept once its switch is moved; here it is turned on, never off, so
 * nothing starts moving under the tests.
 */
@RunWith(AndroidJUnit4::class)
class LessMotionAndSwipeTest {
    class NothingYet : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(NothingYet()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    private fun setting(name: String): String? = runBlocking { dao.setting(name) }

    @Test
    fun theSwitchesAreInAppearanceAndAreKept() {
        app.waitFor { !gone("settings") }
        app.tap("settings")
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("switch:less-motion") }

        // Until its switch is moved, nothing is kept: the phone's own setting decides.
        assertNull(setting(LessMotion.SETTING))
        // Swiping is on until turned off.
        app.tap("switch:swipe-tick")
        app.waitFor { setting(SwipeTick.SETTING) == "0" }
        app.tap("switch:swipe-tick")
        app.waitFor { setting(SwipeTick.SETTING) == "1" }
    }
}
