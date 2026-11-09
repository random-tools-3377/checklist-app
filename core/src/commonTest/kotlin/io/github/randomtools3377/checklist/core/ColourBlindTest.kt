package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertTrue

class ColourBlindTest {
    private val brightnesses = listOf(Brightness.LIGHT, Brightness.DARK, Brightness.BLACK)

    @Test
    fun goodAndBadStayApartForEveryKindOfColourBlindness() {
        for (brightness in brightnesses) {
            val good = ColourBlind.good(brightness)
            val bad = ColourBlind.bad(brightness)
            for (vision in ColourVision.entries) {
                val apart = ColourBlind.difference(ColourBlind.simulate(good, vision), ColourBlind.simulate(bad, vision))
                assertTrue(apart >= 50, "good and bad only $apart apart for $vision in $brightness")
            }
        }
    }

    @Test
    fun theUsualGreenAndRedRunTogetherWhichIsWhyTheModeExists() {
        val forest = Colors.PRESETS.first { it.first == "Forest" }.second
        val usual = Schemes.of(forest, Brightness.LIGHT)
        val usualApart = ColourBlind.difference(ColourBlind.simulate(usual.primary, ColourVision.DEUTAN), ColourBlind.simulate(usual.error, ColourVision.DEUTAN))
        val modeApart = ColourBlind.difference(
            ColourBlind.simulate(ColourBlind.good(Brightness.LIGHT), ColourVision.DEUTAN),
            ColourBlind.simulate(ColourBlind.bad(Brightness.LIGHT), ColourVision.DEUTAN),
        )
        assertTrue(modeApart > usualApart * 3, "usual $usualApart, colour-blind $modeApart")
    }

    @Test
    fun everythingStaysReadableWhateverTheAppsColour() {
        for (brightness in brightnesses) {
            for ((name, color) in Colors.PRESETS) {
                val scheme = ColourBlind.scheme(Schemes.of(color, brightness), brightness)
                fun readable(text: Int, on: Int, what: String) {
                    val contrast = Colors.contrast(text, on)
                    assertTrue(contrast >= 4.5, "$what: contrast $contrast with $name in $brightness")
                }
                readable(scheme.error, scheme.background, "bad on the background")
                readable(scheme.onError, scheme.error, "text on bad")
                readable(scheme.onErrorContainer, scheme.errorContainer, "text on a soft bad")
                readable(ColourBlind.good(brightness), scheme.background, "good on the background")
                readable(ColourBlind.onGood(brightness), ColourBlind.good(brightness), "text on good")
                readable(scheme.onSurface, ColourBlind.goodContainer(brightness), "text on a soft good")
            }
        }
    }

    @Test
    fun goalColoursStayApartForEveryKindOfColourBlindness() {
        val colours = ColourBlind.GOAL_COLORS
        var closest = Double.MAX_VALUE
        for (vision in ColourVision.entries) {
            for (i in colours.indices) for (j in i + 1 until colours.size) {
                val apart = ColourBlind.difference(ColourBlind.simulate(colours[i], vision), ColourBlind.simulate(colours[j], vision))
                if (apart < closest) closest = apart
            }
        }
        assertTrue(closest >= 15, "two goal colours only $closest apart")
    }

    @Test
    fun simulatingKeepsGreysGrey() {
        for (vision in ColourVision.entries) {
            val grey = ColourBlind.simulate(0xFF808080.toInt(), vision)
            assertTrue(ColourBlind.difference(grey, 0xFF808080.toInt()) < 2, "$vision changes grey")
        }
    }
}
