package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.EveningCheck
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SkipResult
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The evening check as every phone and the computer find it: from the database as it stands. */
class EveningChecksTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class MemoryGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, MemoryGifs())
    private val book = Book(RoomStore(db))
    private val lapses = Lapses(RoomStore(db), book)

    /** The user's day now (the day ends at midnight here), and 9 pm on it. */
    private val today = Days.dayOf(nowLocal(), 0)
    private val nine = LocalDateTime(today, LocalTime(21, 0))

    @AfterTest
    fun close() = db.close()

    private suspend fun addHabit(name: String, icon: String, private: Boolean = false): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = icon, points = 5, repeat = Repeat.everyDay(today.minusDays(7)),
                target = 1, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null, private = private,
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    private suspend fun turnOn(time: LocalTime = LocalTime(21, 0)) = repository.setSetting(EveningCheck.SETTING, EveningCheck.encode(time))

    private fun minute(at: LocalDateTime) = LocalDateTime(at.date, LocalTime(at.hour, at.minute + 1))

    @Test
    fun itIsOffUntilATimeIsPicked() = runTest {
        addHabit("Run", "🏃")
        assertTrue(eveningTimes(dao, nine, minute(nine)).isEmpty())
        assertTrue(eveningChecks(dao, nine, minute(nine)).isEmpty())
        turnOn()
        assertEquals(listOf(nine), eveningTimes(dao, nine, minute(nine)).map { it.at })
        // Turned off again: the setting is emptied.
        repository.setSetting(EveningCheck.SETTING, EveningCheck.encode(null))
        assertTrue(eveningChecks(dao, nine, minute(nine)).isEmpty())
    }

    @Test
    fun atItsTimeItListsWhatIsStillOpenToday() = runTest {
        val run = addHabit("Run", "🏃")
        val read = addHabit("Read", "📖")
        val floss = addHabit("Floss", "🦷")
        turnOn()
        val due = eveningChecks(dao, nine, minute(nine)).single()
        assertEquals(nine, due.at)
        assertEquals(today, due.list.day)
        assertEquals("3 still open today", due.list.title)
        assertEquals(listOf("🏃 Run", "📖 Read", "🦷 Floss"), due.list.lines().drop(1))
        // What is at stake comes first: at 90%, all three are needed for today to count.
        assertEquals("3 more and today counts for your streak", due.list.stake)
        assertEquals(due.list.stake, due.list.lines().first())
        assertEquals(run, due.list.firstToTick?.goalId)
        // Not in the minutes around it.
        assertTrue(eveningChecks(dao, LocalDateTime(today, LocalTime(20, 59)), nine).isEmpty())
        assertTrue(eveningChecks(dao, minute(nine), minute(minute(nine))).isEmpty())

        // One done and one skipped: only the third is left.
        assertTrue(book.tickHabit(run) is TickResult.Ticked)
        assertTrue(lapses.skip(read, reason = null, note = null) is SkipResult.Skipped)
        assertEquals(listOf("1 more and today counts for your streak", "🦷 Floss"), eveningChecks(dao, nine, minute(nine)).single().list.lines())
        assertEquals("1 still open today", eveningList(dao, today)?.title)

        // Everything done: nothing is sent.
        assertTrue(book.tickHabit(floss) is TickResult.Ticked)
        assertTrue(eveningChecks(dao, nine, minute(nine)).isEmpty())
        assertNull(eveningList(dao, today))
    }

    @Test
    fun aLockedPrivateGoalIsNotNamed() = runTest {
        addHabit("Run", "🏃")
        addHabit("Journal", "📓", private = true)
        turnOn()
        assertEquals(listOf("2 more and today counts for your streak", "🏃 Run", "📓 Journal"), eveningChecks(dao, nine, minute(nine)).single().list.lines())
        dao.putSetting(SettingEntity(LOCK_PRIVATE, "1"))
        val locked = eveningChecks(dao, nine, minute(nine)).single().list
        assertEquals("2 still open today", locked.title)
        assertEquals(listOf("2 more and today counts for your streak", "🏃 Run", "🔒 A private goal"), locked.lines())
    }

    @Test
    fun theDaysAheadEachGetTheirOwnList() = runTest {
        // As the iPhone asks when it hands its reminders over: the next three days from now.
        val run = addHabit("Run", "🏃")
        turnOn()
        val from = LocalDateTime(today, LocalTime(12, 0))
        val ahead = eveningChecks(dao, from, LocalDateTime(today.plusDays(3), LocalTime(12, 0)))
        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2)), ahead.map { it.list.day })
        assertEquals(listOf(21, 21, 21), ahead.map { it.at.hour })
        // Done today: today's goes, the days after still have theirs.
        assertTrue(book.tickHabit(run) is TickResult.Ticked)
        val after = eveningChecks(dao, from, LocalDateTime(today.plusDays(3), LocalTime(12, 0)))
        assertEquals(listOf(today.plusDays(1), today.plusDays(2)), after.map { it.list.day })
    }

    @Test
    fun aTimeAfterTheDayEndsSendsNothing() = runTest {
        addHabit("Run", "🏃")
        // 1:30 am is before the day ends while the day ends at 4 am.
        repository.setDayEndHour(4)
        turnOn(LocalTime(1, 30))
        val night = LocalDateTime(today.plusDays(1), LocalTime(1, 30))
        assertEquals(1, eveningTimes(dao, night, minute(night)).size)
        // The day's end moved to midnight: 1:30 am is the next day already.
        repository.setDayEndHour(0)
        assertTrue(eveningTimes(dao, night, minute(night)).isEmpty())
        assertTrue(eveningChecks(dao, night, minute(night)).isEmpty())
    }
}
