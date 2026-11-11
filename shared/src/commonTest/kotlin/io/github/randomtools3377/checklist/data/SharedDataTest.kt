package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.TickResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared database code, run with SQLite that comes with the app, as on the iPhone: a goal is
 * saved, ticked and paid for, transactions hold together, and the backup file goes out and back.
 */
class SharedDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class MemoryGifs : GifStore {
        val files = mutableMapOf<String, ByteArray>()
        override fun read(name: String) = files[name]
        override fun write(name: String, bytes: ByteArray) { files[name] = bytes }
        override fun keepOnly(names: Set<String>) { files.keys.retainAll(names) }
    }

    private val repository = Repository(db, MemoryGifs())
    private val book = Book(RoomStore(db))

    @AfterTest
    fun close() = db.close()

    private suspend fun addRead(): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 2, bonus = 3,
                minutes = 10, at = null, finishBy = null, finishDay = null,
            ),
        )
        return dao.activeGoals().single().id
    }

    @Test
    fun aGoalIsTickedAndPaid() = runTest {
        val id = addRead()
        assertEquals(5, (book.tickHabit(id) as TickResult.Ticked).points)
        val second = book.tickHabit(id) as TickResult.Ticked
        assertTrue(second.finished)
        assertEquals(3, second.bonus)
        assertEquals(13, dao.balance())
        assertEquals(TickResult.AlreadyDone, book.tickHabit(id))
        assertTrue(book.undoHabitTick(id))
        assertEquals(5, dao.observeBalance().first())
    }

    @Test
    fun aFailedTransactionSavesNothing() = runTest {
        val id = addRead()
        val failed = runCatching {
            db.withTransaction {
                book.tickHabit(id)
                error("stopped half way")
            }
        }
        assertTrue(failed.isFailure)
        assertEquals(0, dao.allTicks().size)
        assertEquals(0, dao.balance())
    }

    @Test
    fun theBackupFileGoesOutAndBack() = runTest {
        val id = addRead()
        book.tickHabit(id)
        val text = repository.exportBackup()
        dao.deleteTicks()
        dao.deleteEntries()
        repository.restore(Backup.decode(text))
        assertEquals(1, dao.allTicks().size)
        assertEquals(5, dao.balance())
        assertEquals("Read", dao.activeGoals().single().name)
    }

    @Test
    fun whatBelongsToOnePhoneStaysOutOfTheBackup() = runTest {
        val id = addRead()
        repository.setSetting(SETTING_STOPWATCH, "$id|1000")
        repository.setSetting(SETTING_CALENDAR_IDS, "3,4")
        repository.setSetting(SETTING_WEEKLY, "0")
        val backup = Backup.decode(repository.exportBackup())
        assertEquals(null, backup.settings[SETTING_STOPWATCH])
        assertEquals(null, backup.settings[SETTING_CALENDAR_IDS])
        assertEquals("0", backup.settings[SETTING_WEEKLY])

        // An older file that has them: this phone keeps its own calendars, and no timing comes back.
        val older = backup.copy(settings = backup.settings + mapOf(SETTING_STOPWATCH to "$id|5", SETTING_CALENDAR_IDS to "9"))
        repository.restore(older)
        assertEquals(null, dao.setting(SETTING_STOPWATCH))
        assertEquals("3,4", dao.setting(SETTING_CALENDAR_IDS))
        assertEquals("0", dao.setting(SETTING_WEEKLY))
    }
}
