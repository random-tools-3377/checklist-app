package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DifficultyTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0)) // Monday
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val lapses = Lapses(store, book)
    private val monday = LocalDate(2026, 11, 9)

    @Test
    fun levelsScalePointsAndFreeSkips() {
        assertEquals(10, Difficulty.VERY_EASY.pay(5))
        assertEquals(7, Difficulty.EASY.pay(5)) // 7.5, rounded down
        assertEquals(5, Difficulty.NORMAL.pay(5))
        assertEquals(1, Difficulty.VERY_HARD.pay(1)) // never down to nothing
        assertEquals(0, Difficulty.HARD.pay(0))
        assertEquals(3, Difficulty.VERY_EASY.allowed(1))
        assertEquals(0, Difficulty.HARD.allowed(1))
        assertEquals(0, Difficulty.VERY_HARD.allowed(5))
    }

    @Test
    fun aRampMovesALevelAtATimeOnMondays() {
        val ramp = Ramp(Difficulty.VERY_EASY, Difficulty.NORMAL, weeks = 6, start = monday.plusDays(2)) // a Wednesday: its week counts
        assertEquals(Difficulty.VERY_EASY, ramp.levelOn(monday))
        assertEquals(Difficulty.VERY_EASY, ramp.levelOn(monday.plusDays(20)))
        assertEquals(Difficulty.EASY, ramp.levelOn(monday.plusDays(21)))
        assertEquals(Difficulty.NORMAL, ramp.levelOn(monday.plusDays(42)))
        assertEquals(Difficulty.NORMAL, ramp.levelOn(monday.plusDays(400)))
        assertEquals(monday.plusDays(21), ramp.nextChange(monday))
        assertNull(ramp.nextChange(monday.plusDays(42)))
        assertEquals(ramp, Ramp.decode(Ramp.encode(ramp)))
        assertNull(Ramp.decode("EASY|NORMAL|0|2026-11-09"))
        // Going easier works the same way.
        assertEquals(Difficulty.HARD, Ramp(Difficulty.VERY_HARD, Difficulty.NORMAL, 2, monday).levelOn(monday.plusDays(7)))
    }

    @Test
    fun ticksPayAtTheDaysLevelAndFreeMissesFollowIt() = runTest {
        val read = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(monday.minusDays(7))))
        store.putSetting(DifficultySetting.LEVEL, Difficulty.EASY.name)
        assertEquals(7, (book.tickHabit(read.id) as TickResult.Ticked).points)
        assertEquals(7, book.balance())
        assertTrue(book.undoHabitTick(read.id))
        assertEquals(0, book.balance())

        // Hard: the one free miss a week is gone, so a miss costs half its own points.
        store.putSetting(DifficultySetting.LEVEL, Difficulty.HARD.name)
        lapses.closeDays()
        clock.set(LocalDateTime(2026, 11, 10, 10, 0))
        val missed = lapses.closeDays().single()
        assertTrue(missed.overLimit)
        assertEquals(-2, book.balance())

        // A ramp wins over the level set by hand.
        store.putSetting(DifficultySetting.RAMP, Ramp.encode(Ramp(Difficulty.VERY_EASY, Difficulty.NORMAL, 4, monday)))
        assertEquals(Difficulty.VERY_EASY, book.difficulty())
    }

    @Test
    fun suggestions() {
        assertEquals(13, Suggest.points(Effort.HARD, minutes = 30))
        assertEquals(1, Suggest.points(Effort.EASY, target = 8))
        assertEquals(2, Suggest.points(Effort.VERY_HARD, target = 8))
        assertEquals(280, Suggest.price(RewardSize.BIG, null, perDay = 20.0, weeklyBudget = null))
        assertEquals(35, Suggest.price(null, 5.0, perDay = 20.0, weeklyBudget = 20.0))
        assertNull(Suggest.price(null, 5.0, perDay = 20.0, weeklyBudget = null))
        assertNull(Suggest.price(RewardSize.SMALL, null, perDay = null, weeklyBudget = null))
        assertEquals(7, Suggest.nice(7.4))
        assertEquals(25, Suggest.nice(23.0))
        assertEquals(1250, Suggest.nice(1234.0))
        val goals = listOf(
            Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(monday)),
            Goal(id = 2, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 7, repeat = Repeat(RepeatKind.TIMES_PER, monday, per = Period.WEEKLY), target = 2),
        )
        assertEquals(7.0, Suggest.expectedPerDay(goals, monday)) // 5 a day, and 14 a week
    }
}
