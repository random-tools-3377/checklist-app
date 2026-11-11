package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StakesTest {
    private val monday = LocalDate(2026, 11, 9)

    @Test
    fun aBetWonPaysTwiceTheStake() = runTest {
        val store = MemoryStore()
        val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0))
        val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
        val bets = Bets(store, book)
        val read = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(monday)))
        store.addEntry(Entry(at = 0, day = monday, kind = EntryKind.TICK, amount = 30, label = "Start"))

        assertEquals(BetRefusal.TOO_POOR, bets.place(read.id, 2, 50))
        assertNull(bets.place(read.id, 2, 25))
        assertEquals(5, book.balance(), "the stake is taken")
        assertEquals(BetRefusal.ALREADY, bets.place(read.id, 3, 10))

        book.tickHabit(read.id) // 5 points
        assertTrue(bets.settle().isEmpty())
        clock.set(LocalDateTime(2026, 11, 10, 10, 0))
        book.tickHabit(read.id)
        val won = bets.settle()
        assertEquals(listOf(BetState.WON), won.map { it.state })
        assertEquals(5 + 5 + 5 + 50, book.balance())
        assertTrue(bets.settle().isEmpty(), "paid once")

        // Undoing the tick that won it takes the win back.
        book.undoHabitTick(read.id)
        bets.takeBackWins()
        assertEquals(5 + 5, book.balance())
        assertEquals(BetState.OPEN, Bets.decode(store.setting(Bets.SETTING)).single().state)
    }

    @Test
    fun aWeekEndingShortLosesTheStake() = runTest {
        val store = MemoryStore()
        val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0))
        val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
        val bets = Bets(store, book)
        val gym = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Gym", icon = "🏋️", points = 10, repeat = Repeat.everyDay(monday)))
        store.addEntry(Entry(at = 0, day = monday, kind = EntryKind.TICK, amount = 100, label = "Start"))
        assertNull(bets.place(gym.id, 5, 25))
        book.tickHabit(gym.id)
        clock.set(LocalDateTime(2026, 11, 15, 22, 0))
        assertTrue(bets.settle().isEmpty(), "still Sunday")
        clock.set(LocalDateTime(2026, 11, 16, 9, 0))
        assertEquals(listOf(BetState.LOST), bets.settle().map { it.state })
        assertEquals(100 - 25 + 10, book.balance())
        assertEquals(BetState.LOST, Bets.decode(store.setting(Bets.SETTING)).single().state)
    }

    @Test
    fun betsAreKeptAndReadBack() {
        val list = listOf(Bet(3, monday, 4, 25), Bet(5, monday, 2, 10, BetState.WON))
        assertEquals(list, Bets.decode(Bets.encode(list)))
        assertTrue(Bets.decode("nonsense\n1|x|2|3|OPEN").isEmpty())
    }

    private fun day(n: Int, ok: Boolean) = DayRecord(monday.plusDays(n), due = 1, done = if (ok) 1 else 0, misses = if (ok) 0 else 1, skips = 0, overLimit = 0)

    @Test
    fun aShieldEarnedAtThirtyDaysSavesTheStreakOnce() {
        val settings = StreakSettings(StreakTarget.PERCENT, 100)
        // 30 good days, a bad one, 5 good, another bad one.
        val records = (0 until 30).map { day(it, true) } + day(30, false) + (31 until 36).map { day(it, true) } + day(36, false) + day(37, true)
        val today = monday.plusDays(37)
        val shielded = Shields.of(records, monday, today, settings)
        assertEquals(listOf(monday.plusDays(30)), shielded.saved)
        assertEquals(1, shielded.main, "the second bad day had no shield left")
        assertEquals(0, shielded.held)
        // Without shields, the streak is 1 as well, but it was broken at day 30 too.
        assertEquals(1, StreakCounter.count(records, today, settings).done)
        val upToDay35 = Shields.of(records, monday, monday.plusDays(35), settings)
        assertEquals(35, upToDay35.main, "the shield kept the 30 going")
        assertEquals(5, StreakCounter.count(records, monday.plusDays(35), settings).done)
    }

    @Test
    fun shieldsDontChangeThePastBeforeTheyStarted() {
        val settings = StreakSettings(StreakTarget.PERCENT, 100)
        val records = (0 until 10).map { day(it, true) } + day(10, false) + (11 until 14).map { day(it, true) }
        val shielded = Shields.of(records, monday.plusDays(12), monday.plusDays(13), settings)
        assertEquals(3, shielded.main)
        assertTrue(shielded.saved.isEmpty())
    }
}
