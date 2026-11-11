package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.TickResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Next up" in a habit chain, as the iPhone and the computer show it after a tick. */
class NextInChainTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class MemoryGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, MemoryGifs())
    private val book = Book(RoomStore(db))

    @AfterTest
    fun close() = db.close()

    private suspend fun addHabit(name: String, icon: String, after: Long? = null, private: Boolean = false): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = icon, points = 5, repeat = Repeat.everyDay(nowLocal().date.plus(DatePeriod(days = -7))),
                target = 1, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null, private = private, after = after,
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    @Test
    fun theGoalAfterOneJustDoneIsNextUp() = runTest {
        val coffee = addHabit("Coffee", "☕")
        val journal = addHabit("Journal", "📓", after = coffee)
        assertTrue(book.tickHabit(coffee) is TickResult.Ticked)
        assertEquals(listOf(NextUp(journal, "📓 Journal")), nextInChain(dao, coffee))
        // Nothing comes after the journal, and once it is done it isn't next up either.
        assertTrue(nextInChain(dao, journal).isEmpty())
        assertTrue(book.tickHabit(journal) is TickResult.Ticked)
        assertTrue(nextInChain(dao, coffee).isEmpty())
    }

    @Test
    fun aLockedPrivateGoalIsNotNamed() = runTest {
        val coffee = addHabit("Coffee", "☕")
        addHabit("Journal", "📓", after = coffee, private = true)
        assertEquals(listOf("📓 Journal"), nextInChain(dao, coffee).map { it.title })
        dao.putSetting(SettingEntity(LOCK_PRIVATE, "1"))
        assertEquals(listOf("🔒 A private goal"), nextInChain(dao, coffee).map { it.title })
    }
}
