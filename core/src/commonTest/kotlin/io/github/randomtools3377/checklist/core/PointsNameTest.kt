package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PointsNameTest {
    @Test
    fun pointsCanBeCalledSomethingElse() {
        val coins = PointsName.PRESETS[1]
        assertEquals("126 coins 🪙", coins.amount(126))
        assertEquals("1 coin 🪙", coins.amount(1))
        assertEquals("-1 coin 🪙", coins.amount(-1))
        assertEquals("Coins", coins.title)
        assertEquals("5 XP", PointsName.PRESETS[3].amount(5))
        assertEquals("2 points", PointsName.DEFAULT.amount(2))
    }

    @Test
    fun ownWordsAreTidiedAndStored() {
        assertEquals(PointsName("bean", "beans", "🫘"), PointsName.of("  bean ", "beans", " 🫘 "))
        assertEquals(PointsName("gold", "gold", ""), PointsName.of("", "gold", ""))
        assertEquals(PointsName.DEFAULT.copy(many = "point"), PointsName.of("", "", ""))
        val coins = PointsName.PRESETS[1]
        assertEquals(coins, PointsName.decode(PointsName.encode(coins)))
        assertEquals(PointsName.DEFAULT, PointsName.decode(null))
        assertEquals(PointsName.DEFAULT, PointsName.decode("broken"))
    }

    @Test
    fun theWeeklySummaryUsesTheName() {
        val before = PointsName.current
        try {
            PointsName.current = PointsName.PRESETS[1]
            val summary = WeeklySummary(due = 0, done = 0, missed = 0, skipped = 0, earned = 27, lost = 0, best = null)
            assertEquals("Nothing was due · +27 coins 🪙", summary.describe())
        } finally {
            PointsName.current = before
        }
    }
}
