package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeaturesTest {
    @Test
    fun extrasStartOffAndTurnOn() {
        Feature.entries.forEach { feature ->
            assertTrue(!feature.isOn(null), "${feature.name} starts off")
            assertTrue(!feature.isOn("0"))
            assertTrue(feature.isOn(feature.onValue), "${feature.name} turns on")
        }
        assertEquals(Feature.entries.size, Feature.entries.map { it.key }.toSet().size) // each has its own setting
        assertEquals(Interest.PERCENT, Feature.INTEREST.key)
        assertEquals(PerfectDays.BONUS, Feature.PERFECT_DAY.key)
        assertEquals(Boost.SETTING, Feature.BOOST.key)
        assertEquals(StreakBonus.SETTING, Feature.STREAK_BONUS.key)
        assertEquals(RepeatPrice.SETTING, Feature.REPEAT_PRICE.key)
    }
}
