package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillsTest {
    @Test
    fun theAreaIsGuessedFromTheNameAndCategory() {
        assertEquals(SkillArea.FITNESS, SkillArea.guess("Morning run", null))
        assertEquals(SkillArea.HEALTH, SkillArea.guess("Drink water", null))
        assertEquals(SkillArea.MIND, SkillArea.guess("Read 20 pages", null))
        assertEquals(SkillArea.HOME, SkillArea.guess("Water the plants", null))
        assertEquals(SkillArea.WORK, SkillArea.guess("Clear the inbox", null))
        assertEquals(SkillArea.CREATIVE, SkillArea.guess("Practise guitar", null))
        // The category counts double, and naming an area picks it.
        assertEquals(SkillArea.FITNESS, SkillArea.guess("Morning routine", "Sport"))
        assertEquals(SkillArea.CREATIVE, SkillArea.guess("Anything", "creative"))
        // Nothing fits: Health.
        assertEquals(SkillArea.HEALTH, SkillArea.guess("Xyz", null))
    }

    @Test
    fun aChosenAreaWins() {
        val goal = Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = null, skill = SkillArea.WORK)
        assertEquals(SkillArea.WORK, SkillArea.of(goal))
        assertEquals(SkillArea.MIND, SkillArea.of(goal.copy(skill = null)))
    }

    @Test
    fun eachLevelTakesALittleMore() {
        assertEquals(0, Levels.needed(1, 50))
        assertEquals(50, Levels.needed(2, 50))
        assertEquals(150, Levels.needed(3, 50))
        assertEquals(2250, Levels.needed(10, 50))
        assertEquals(Level(1, 0, 50), Levels.of(0, 50))
        assertEquals(Level(2, 0, 100), Levels.of(50, 50))
        assertEquals(Level(3, 50, 150), Levels.of(200, 50))
        assertEquals(100L, Levels.of(200, 50).toNext)
        assertNull(Levels.skillTier(4))
        assertEquals(Tier.BRONZE, Levels.skillTier(5))
        assertEquals(Tier.SILVER, Levels.skillTier(12))
        assertEquals(Tier.GOLD, Levels.skillTier(20))
    }

    @Test
    fun experienceIsWhatGoalsPaidAndIsNeverTakenAway() {
        val day = LocalDate(2026, 11, 2)
        val run = Goal(id = 1, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 10, repeat = null)
        val read = Goal(id = 2, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = null, archived = true)
        val entries = listOf(
            Entry(at = 1, day = day, kind = EntryKind.TICK, amount = 10, goalId = 1, label = "Run"),
            Entry(at = 2, day = day, kind = EntryKind.TICK, amount = 10, goalId = 1, label = "Run"),
            Entry(at = 3, day = day, kind = EntryKind.TICK, amount = 5, goalId = 2, label = "Read"),
            Entry(at = 4, day = day, kind = EntryKind.BONUS, amount = 3, goalId = 2, label = "Read"),
            Entry(at = 5, day = day, kind = EntryKind.PURCHASE, amount = -30, label = "Coffee"),
            Entry(at = 6, day = day, kind = EntryKind.PENALTY, amount = -5, goalId = 1, label = "Run"),
            Entry(at = 7, day = day, kind = EntryKind.INTEREST, amount = 2, label = "Interest"),
        )
        val sheet = Experience.of(entries, listOf(run, read))
        assertEquals(28, sheet.xp)
        assertEquals(20, sheet.skills[SkillArea.FITNESS])
        assertEquals(8, sheet.skills[SkillArea.MIND], "a removed goal's points still count")
        assertEquals(1, sheet.skillLevel(SkillArea.MIND).level)
        assertEquals(2, sheet.skillLevel(SkillArea.FITNESS).level)
        assertEquals(1, sheet.level.level)
    }

    @Test
    fun levelBadgesShowOnlyWithTheCharacter() {
        assertTrue(Badge.shown(character = true).contains(Badge.LEVEL_10))
        assertTrue(!Badge.shown(character = false).contains(Badge.LEVEL_10))
        assertEquals(Tier.GOLD, Badge.TICKS_5000.tier)
    }
}
