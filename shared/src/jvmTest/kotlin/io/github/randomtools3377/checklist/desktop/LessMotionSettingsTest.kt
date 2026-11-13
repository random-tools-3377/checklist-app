package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.BuzzKind
import io.github.randomtools3377.checklist.core.LessMotion
import io.github.randomtools3377.checklist.core.SettingsSearch
import io.github.randomtools3377.checklist.core.SwipeTick
import io.github.randomtools3377.checklist.data.PHONE_ONLY_SETTINGS
import io.github.randomtools3377.checklist.ui.Appearance
import io.github.randomtools3377.checklist.ui.HelpTopic
import io.github.randomtools3377.checklist.ui.LookPlace
import io.github.randomtools3377.checklist.ui.lookParts
import io.github.randomtools3377.checklist.ui.stepsOn
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Less motion" and "Swipe a card to tick it": where a Settings search finds them, and how they are kept. */
class LessMotionSettingsTest {
    private val folder: File = Files.createTempDirectory("checklist-motion-test").toFile()
    private val phone = DesktopPhone(appVersion = "0.0.0", folder = folder)

    @AfterTest
    fun tidy() {
        folder.deleteRecursively()
    }

    private fun found(query: String) = lookParts(phone, matchWallpaper = false).filter { SettingsSearch.matches(query, "appearance look ${it.title} ${it.words}") }

    @Test
    fun aSettingsSearchFindsLessMotionAndSwiping() {
        listOf("less motion", "motion", "animation", "animations", "reduce motion").forEach { query ->
            assertTrue(found(query).any { it.title == "Less motion" && it.key == LookPlace.MOTION }, query)
        }
        listOf("swipe", "swiping", "slide").forEach { query ->
            assertTrue(found(query).any { it.title == "Swipe a card to tick it" && it.key == LookPlace.TODAY }, query)
        }
    }

    @Test
    fun lessMotionStaysOnThisDeviceAndSwipingGoesInTheBackup() {
        assertTrue(LessMotion.SETTING in PHONE_ONLY_SETTINGS)
        assertFalse(SwipeTick.SETTING in PHONE_ONLY_SETTINGS)
    }

    @Test
    fun untilTheSwitchIsMovedTheDeviceDecidesAndSwipingIsOn() {
        val fresh = Appearance()
        assertNull(fresh.lessMotion)
        assertTrue(fresh.swipeTick)
        // A computer doesn't say, so the app moves until "Less motion" is turned on.
        assertFalse(LessMotion.calm(null, phone.reducedMotion()))
    }

    @Test
    fun aComputerHasNoBuzzOfItsOwnAndItsGuideLeavesBuzzOut() {
        BuzzKind.entries.forEach { assertFalse(phone.buzz(it)) }
        assertEquals(4, BuzzKind.entries.size)
        assertFalse(HelpTopic.APPEARANCE.stepsOn(phone).any { "Buzz" in it })
        assertTrue(HelpTopic.APPEARANCE.steps.any { "Buzz" in it })
        assertTrue(HelpTopic.APPEARANCE.stepsOn(phone).any { "Less motion" in it })
    }
}
