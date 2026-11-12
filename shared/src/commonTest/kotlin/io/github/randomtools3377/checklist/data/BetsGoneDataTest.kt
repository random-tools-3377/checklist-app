package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Bet
import io.github.randomtools3377.checklist.core.BetState
import io.github.randomtools3377.checklist.core.Bets
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bets are no longer in the app: on the real database, a bet still open gives its stake back
 * once when the app starts, and once after importing a backup made before bets were removed.
 */
class BetsGoneDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val parts = SharedParts(db, NoGifs(), null)
    private val monday = LocalDate(2026, 11, 9)

    @AfterTest
    fun close() = db.close()

    /** 100 points, then a bet of 25 placed before bets were removed, still open, and one won before. */
    private suspend fun anOpenBet() {
        dao.insertEntry(Entry(at = 0, day = monday, kind = EntryKind.TICK, amount = 100, label = "Start").toEntity())
        dao.insertEntry(Entry(at = 1, day = monday, kind = EntryKind.BET, amount = -25, goalId = 7, label = "Bet: Read 3 times this week").toEntity())
        dao.putSetting(SettingEntity(Bets.SETTING, Bets.encode(listOf(Bet(7, monday, 3, 25), Bet(8, monday.minusDays(7), 2, 10, BetState.WON)))))
    }

    private suspend fun stakesBack() = dao.allEntries().filter { it.label == Bets.STAKE_BACK }

    @Test
    fun anOpenBetGivesItsStakeBackWhenTheAppStarts() = runTest {
        anOpenBet()
        parts.closeDays()
        assertEquals(100, dao.balance())
        val back = stakesBack().single()
        assertEquals(EntryKind.BET.name, back.kind)
        assertEquals(25, back.amount)
        assertEquals(7L, back.goalId)
        assertEquals(listOf(BetState.CANCELLED, BetState.WON), Bets.decode(dao.setting(Bets.SETTING)).map { it.state })

        // The next start gives nothing back again.
        parts.closeDays()
        assertEquals(100, dao.balance())
        assertEquals(1, stakesBack().size)
        assertEquals(1, dao.allEntries().count { it.label.startsWith("Bet: ") }, "the past bet stays in the history")
    }

    @Test
    fun anOlderBackupWithAnOpenBetGivesItsStakeBackOnImport() = runTest {
        anOpenBet()
        val older = Backup.decode(parts.repository.exportBackup())

        parts.restore(older)
        assertEquals(100, dao.balance())
        assertEquals(1, stakesBack().size)

        // A backup made since holds the stake given back and the bet cancelled: nothing twice.
        val since = Backup.decode(parts.repository.exportBackup())
        parts.restore(since)
        assertEquals(100, dao.balance())
        assertEquals(1, stakesBack().size)
        assertEquals(BetState.CANCELLED, Bets.decode(dao.setting(Bets.SETTING)).first().state)

        // The older one imported again gives it back once more, as its history doesn't have it.
        parts.restore(older)
        assertEquals(100, dao.balance())
        assertEquals(1, stakesBack().size)
    }
}
