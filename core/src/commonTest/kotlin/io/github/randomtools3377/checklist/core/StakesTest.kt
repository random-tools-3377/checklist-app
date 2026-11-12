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

    private fun book(store: MemoryStore) = Book(store, TestClock(LocalDateTime(2026, 11, 11, 10, 0))) { kotlinx.datetime.TimeZone.UTC }

    @Test
    fun anOpenBetGivesItsStakeBackOnce() = runTest {
        val store = MemoryStore()
        val book = book(store)
        val bets = Bets(store, book)
        store.addEntry(Entry(at = 0, day = monday, kind = EntryKind.TICK, amount = 100, label = "Start"))
        // Placed before bets were removed: one still open, one won and one lost the week before.
        store.addEntry(Entry(at = 1, day = monday, kind = EntryKind.BET, amount = -25, goalId = 3, label = "Bet: Read 4 times this week"))
        val before = listOf(
            Bet(3, monday, 4, 25),
            Bet(5, monday.minusDays(7), 2, 10, BetState.WON),
            Bet(6, monday.minusDays(7), 3, 50, BetState.LOST),
        )
        store.putSetting(Bets.SETTING, Bets.encode(before))

        assertEquals(25, bets.giveBackOpen())
        assertEquals(100, book.balance(), "the stake is back")
        val back = store.entries.last()
        assertEquals(EntryKind.BET, back.kind)
        assertEquals(25, back.amount)
        assertEquals(3L, back.goalId)
        assertEquals(Bets.STAKE_BACK, back.label)
        assertEquals(LocalDate(2026, 11, 11), back.day)
        assertEquals(
            listOf(BetState.CANCELLED, BetState.WON, BetState.LOST),
            Bets.decode(store.setting(Bets.SETTING)).map { it.state },
            "won and lost bets stay as they were",
        )

        // Next time the app starts, or after importing this phone's own export: nothing twice.
        assertEquals(0, bets.giveBackOpen())
        assertEquals(100, book.balance())
        assertEquals(3, store.entries.size, "the past bet entry stays as it was")
    }

    @Test
    fun everyOpenBetGivesItsStakeBackEvenForAGoalSinceRemoved() = runTest {
        val store = MemoryStore()
        val book = book(store)
        store.putSetting(Bets.SETTING, Bets.encode(listOf(Bet(1, monday, 2, 10), Bet(99, monday, 3, 50))))
        assertEquals(60, Bets(store, book).giveBackOpen())
        assertEquals(listOf(10, 50), store.entries.map { it.amount })
        assertEquals(listOf(1L, 99L), store.entries.map { it.goalId })
        assertTrue(Bets.decode(store.setting(Bets.SETTING)).all { it.state == BetState.CANCELLED })
    }

    @Test
    fun withNoBetsNothingChanges() = runTest {
        val store = MemoryStore()
        val bets = Bets(store, book(store))
        assertEquals(0, bets.giveBackOpen())
        assertNull(store.setting(Bets.SETTING))
        store.putSetting(Bets.SETTING, "not a bet")
        assertEquals(0, bets.giveBackOpen())
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun betsAreKeptAndReadBack() {
        val list = listOf(Bet(3, monday, 4, 25), Bet(5, monday, 2, 10, BetState.WON), Bet(6, monday, 1, 50, BetState.CANCELLED))
        assertEquals(list, Bets.decode(Bets.encode(list)))
        assertTrue(Bets.decode("nonsense\n1|x|2|3|OPEN\n1|2026-11-09|2|3|SOMETHING_NEW").isEmpty())
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
    fun theWidgetsStreaksUseShieldsAsTodayDoes() {
        val settings = StreakSettings(StreakTarget.PERCENT, 100)
        val records = (0 until 30).map { day(it, true) } + day(30, false) + (31 until 36).map { day(it, true) }
        val today = monday.plusDays(35)
        val plain = StreakCounter.count(records, today, settings)
        assertEquals(plain, Shields.streaks(records, null, today, settings))
        val shielded = Shields.streaks(records, monday, today, settings)
        assertEquals(35, shielded.done)
        assertEquals(plain.copy(done = 35), shielded) // only the main streak changes
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
