package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuitAndChallengeTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0)) // Monday
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val lapses = Lapses(store, book)
    private val monday = LocalDate(2026, 11, 9)

    private fun on(day: Int) = clock.set(LocalDateTime(2026, 11, day, 10, 0))

    @Test
    fun aSlipIsAMissStraightAwayAndNotTwice() = runTest {
        val sugar = store.addGoal(Goal(kind = GoalKind.HABIT, name = "No sugar", icon = "🍬", points = 4, repeat = Repeat.everyDay(monday), quit = true))
        lapses.closeDays()
        book.tickHabit(sugar.id)
        on(10)
        lapses.closeDays()
        book.tickHabit(sugar.id)
        assertEquals(2, Quit.daysWithout(sugar, store.ticks, LocalDate(2026, 11, 10)))

        // "I slipped" takes back today's tick and records the miss now.
        val miss = lapses.slip(sugar.id)!!
        assertEquals(LapseKind.MISS, miss.kind)
        assertEquals(4, book.balance())
        assertNull(lapses.slip(sugar.id)) // once
        assertEquals(1, Quit.daysWithout(sugar, store.ticks, LocalDate(2026, 11, 10)))
        on(11)
        assertTrue(lapses.closeDays().isEmpty()) // no second miss for that day
        assertEquals(0, Quit.daysWithout(sugar, store.ticks, LocalDate(2026, 11, 11)))
    }

    @Test
    fun aChallengePaysOnceAndAMissStartsItAgain() = runTest {
        val walk = store.addGoal(
            Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat.everyDay(monday), challenge = Challenge(days = 3, bonus = 30, from = monday)),
        )
        lapses.closeDays()
        book.tickHabit(walk.id)
        on(10)
        lapses.closeDays()
        // Tuesday missed: it starts again from Wednesday.
        on(11)
        assertEquals(1, lapses.closeDays().size)
        val progress = { day: LocalDate -> Challenges.progress(store.goals.first { it.id == walk.id }, store.ticks, store.lapses, store.entries, day)!! }
        assertEquals(LocalDate(2026, 11, 11), progress(LocalDate(2026, 11, 11)).startedOn)
        assertEquals(0, progress(LocalDate(2026, 11, 11)).done)
        book.tickHabit(walk.id)
        on(12); lapses.closeDays(); book.tickHabit(walk.id)
        on(13); lapses.closeDays()
        val third = book.tickHabit(walk.id) as TickResult.Ticked
        assertEquals(30, third.bonus) // finished: 3 in a row
        assertTrue(progress(LocalDate(2026, 11, 13)).finished)
        // Undone, the bonus goes too; done again, it is paid again once.
        book.undoHabitTick(walk.id)
        assertEquals(0, store.entries.count { it.kind == EntryKind.CHALLENGE })
        assertEquals(30, (book.tickHabit(walk.id) as TickResult.Ticked).bonus)
        on(14); lapses.closeDays()
        assertEquals(0, (book.tickHabit(walk.id) as TickResult.Ticked).bonus)
    }
}
