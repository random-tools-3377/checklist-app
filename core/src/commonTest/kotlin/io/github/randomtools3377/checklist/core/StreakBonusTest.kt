package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakBonusTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0))
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val monday = LocalDate(2026, 11, 9)

    @Test
    fun longerStreaksPayMore() = runTest {
        assertEquals(100, StreakBonus.percentFor(6))
        assertEquals(110, StreakBonus.percentFor(7))
        assertEquals(120, StreakBonus.percentFor(30))
        assertEquals(130, StreakBonus.percentFor(365))
        assertEquals(11, payFor(10, Difficulty.NORMAL, boosted = false, streakPercent = 110))
        assertEquals(24, payFor(10, Difficulty.EASY, boosted = true, streakPercent = 110)) // 10 × 1.5 × 1.5 × 1.1, rounded down once

        // Seven days done in a row before today: today's tick pays 10% more.
        val read = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 10, repeat = Repeat.everyDay(monday.minusDays(7))))
        (1..7).forEach { d ->
            val day = monday.minusDays(d)
            store.ticks += Tick(id = 1000L + d, goalId = read.id, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day)
        }
        assertEquals(110, book.streakPercent(monday))
        assertEquals(11, (book.tickHabit(read.id) as TickResult.Ticked).points)
        store.putSetting(StreakBonus.SETTING, "0")
        assertEquals(100, book.streakPercent(monday))
    }
}
