package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathsTest {
    private val monday = LocalDate(2026, 11, 9)
    private val read = Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 10, repeat = null)

    @Test
    fun aPathsSkillGetsALittleMoreExperience() {
        val entries = listOf(Entry(at = 1, day = monday, kind = EntryKind.TICK, amount = 100, goalId = 1, label = "Read"))
        assertEquals(100, Experience.of(entries, listOf(read)).skills[SkillArea.MIND])
        val scholar = Experience.of(entries, listOf(read), Path.SCHOLAR)
        assertEquals(110, scholar.skills[SkillArea.MIND])
        assertEquals(100, scholar.xp, "the character's own experience is unchanged")
        assertEquals(100, Experience.of(entries, listOf(read), Path.ATHLETE).skills[SkillArea.MIND])
        assertEquals(Path.MAKER, Paths.decode("MAKER"))
        assertNull(Paths.decode("WIZARD"))
    }

    private fun tick(minuteOfDay: Int) = Tick(goalId = 1, stepId = null, at = minuteOfDay.toLong(), day = monday, period = Period.DAILY, periodStart = monday)
    private val timeOf: (Long) -> LocalTime = { LocalTime((it / 60).toInt(), (it % 60).toInt()) }
    private val sheet = CharacterSheet(0, Levels.of(0, Levels.CHARACTER_STEP), emptyMap())
    private val streak = StreakSettings(StreakTarget.PERCENT, 100)

    @Test
    fun titlesComeFromTheWayYouPlay() {
        val early = List(20) { tick(7 * 60) }
        assertEquals(setOf(Title.EARLY_BIRD), Titles.earned(early, emptyList(), emptyList(), sheet, streak, timeOf))
        val late = List(20) { tick(22 * 60 + 30) }
        assertTrue(Title.NIGHT_OWL in Titles.earned(late, emptyList(), emptyList(), sheet, streak, timeOf))

        val weekends = (0 until 8).map { DayRecord(LocalDate(2026, 11, 14).plusDays(7 * it), 2, 2, 0, 0, 0) }
        assertTrue(Title.WEEKEND_WARRIOR in Titles.earned(emptyList(), emptyList(), weekends, sheet, streak, timeOf))

        val wins = List(4) { Entry(at = 1, day = monday, kind = EntryKind.BONUS, amount = 25, label = "Boss beaten: The Fog") }
        assertTrue(Title.BOSS_SLAYER in Titles.earned(emptyList(), wins, emptyList(), sheet, streak, timeOf))

        val allFive = SkillArea.entries.associateWith { Levels.needed(5, Levels.SKILL_STEP) }
        val rounded = CharacterSheet(0, Levels.of(0, Levels.CHARACTER_STEP), allFive)
        assertTrue(Title.ALL_ROUNDER in Titles.earned(emptyList(), emptyList(), emptyList(), rounded, streak, timeOf))
    }

    @Test
    fun theLongestStreakSkipsEmptyDays() {
        val days = (0 until 60).map { n ->
            when {
                n == 20 -> DayRecord(monday.plusDays(n), 0, 0, 0, 0, 0) // nothing due
                n == 55 -> DayRecord(monday.plusDays(n), 1, 0, 1, 0, 0) // missed
                else -> DayRecord(monday.plusDays(n), 1, 1, 0, 0, 0)
            }
        }
        assertEquals(54, Titles.longestMainStreak(days, streak))
        assertTrue(Title.UNSTOPPABLE in Titles.earned(emptyList(), emptyList(), days, sheet, streak, timeOf))
    }

    @Test
    fun theTitleShownIsThePickedOneOrThePaths() {
        val earned = setOf(Title.EARLY_BIRD)
        assertEquals("🐦 Early bird", Titles.shown("EARLY_BIRD", Path.SCHOLAR, earned))
        assertEquals("🦉 Scholar", Titles.shown(null, Path.SCHOLAR, earned))
        assertEquals("🦉 Scholar", Titles.shown("NIGHT_OWL", Path.SCHOLAR, earned), "not earned: the path's")
        assertNull(Titles.shown("", Path.SCHOLAR, earned), "none picked")
        assertNull(Titles.shown(null, null, earned))
    }
}
