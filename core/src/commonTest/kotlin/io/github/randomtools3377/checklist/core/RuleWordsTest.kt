package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleWordsTest {
    @Test
    fun aPercentAsAMultiplier() {
        assertEquals("2", RuleWords.times(200))
        assertEquals("1.5", RuleWords.times(150))
        assertEquals("1.75", RuleWords.times(175))
        assertEquals("1.05", RuleWords.times(105))
        assertEquals("0.5", RuleWords.times(50))
        assertEquals("3", RuleWords.times(300))
    }

    @Test
    fun aWeakItemSaysTheNumberThatIsSet() {
        assertEquals("hits 2 times as hard", RuleWords.weakHit(Rules()))
        assertEquals("hits 3 times as hard", RuleWords.weakHit(Rules(bossWeakPercent = 300)))
    }

    @Test
    fun theBalanceCheckSaysTheSharesThatAreSet() {
        assertEquals(
            "All in balance: over the last 4 weeks, every goal was done more than half the time, and none 95% of the time or more.",
            RuleWords.inBalance(Rules()),
        )
        val changed = RuleWords.inBalance(Rules(hardSharePercent = 40, easySharePercent = 90))
        assertTrue("more than 40% of the time" in changed, changed)
        assertTrue("none 90% of the time or more" in changed, changed)
    }

    @Test
    fun aFeaturesLineSaysTheUsualNumbersUntilTheyAreChanged() {
        // With the usual rules, each line reads as it always did.
        for (feature in Feature.entries) assertEquals(feature.about, feature.aboutNow(Rules()), feature.name)
    }

    @Test
    fun aFeaturesLineFollowsPointsAndRules() {
        val rules = Rules(
            boostExtraPercent = 80, repeatPriceRisePercent = 40, shieldEveryDays = 14, shieldsMost = 3,
            streakBonus1Days = 5, streakBonus1Percent = 15, streakBonus2Days = 20, streakBonus2Percent = 25, streakBonus3Days = 60, streakBonus3Percent = 35,
        )
        assertEquals("Each week, the goal missed most lately pays 80% more.", Feature.BOOST.aboutNow(rules))
        assertEquals("A reward costs 40% more for each time it was already bought that week.", Feature.REPEAT_PRICE.aboutNow(rules))
        assertEquals("Goals pay 15% more from a 5-day main streak, 25% from 20 and 35% from 60.", Feature.STREAK_BONUS.aboutNow(rules))
        assertTrue(Feature.SHIELDS.aboutNow(rules).startsWith("Every 14 days of main streak earns a shield (hold 3 at most)."))
        assertFalse("30 days" in Feature.SHIELDS.aboutNow(rules))
    }

    @Test
    fun aNumberOutOfRangeIsToldItsRange() {
        val penalty = Rules.FIELDS.first { it.key == "penaltyPercent" }
        assertEquals("From 0 to 1000.", Rules.rangeText(penalty))
        val skips = Rules.FIELDS.first { it.key == "hardExtraSkips" }
        assertEquals("From -10 to 10.", Rules.rangeText(skips))
    }
}

class ReorderTest {
    @Test
    fun movesOneUpOrDown() {
        val steps = mutableListOf("a", "b", "c")
        assertTrue(Reorder.move(steps, 2, -1))
        assertEquals(listOf("a", "c", "b"), steps)
        assertTrue(Reorder.move(steps, 0, 1))
        assertEquals(listOf("c", "a", "b"), steps)
    }

    @Test
    fun staysPutAtEitherEnd() {
        val steps = mutableListOf("a", "b")
        assertFalse(Reorder.move(steps, 0, -1))
        assertFalse(Reorder.move(steps, 1, 1))
        assertFalse(Reorder.move(steps, 5, -1))
        assertFalse(Reorder.move(steps, 0, 0))
        assertEquals(listOf("a", "b"), steps)
    }
}
