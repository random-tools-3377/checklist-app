package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.AtMost
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Challenge
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.UseResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "At most" goals in the database: where the limit is kept, and that it travels and is tidied. */
class AtMostDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class MemoryGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, MemoryGifs())
    private val store = RoomStore(db)
    private val book = Book(store)
    private val lapses = Lapses(store, book)
    private val start = LocalDate(2026, 1, 1)

    @AfterTest
    fun close() {
        db.close()
        AtMostLimits.current = emptyMap()
    }

    private fun draft(id: Long = 0, name: String = "Coffee", limit: Double? = 2.0, repeat: Repeat = Repeat.everyDay(start), unit: String? = null) = HabitDraft(
        id = id, name = name, icon = "☕", points = 6, repeat = repeat, target = 3, bonus = 4,
        minutes = 10, at = LocalTime(9, 0), finishBy = null, finishDay = null, reminder = Reminder(at = LocalTime(8, 0)),
        quit = true, challenge = Challenge(30, 50, start), limit = limit, unit = unit,
    )

    private suspend fun goalId(name: String = "Coffee") = dao.activeGoals().single { it.name == name }.id

    @Test
    fun theLimitIsKeptInASettingAndTheGoalIsNothingElse() = runTest {
        repository.saveHabit(draft(unit = "cups"))
        val id = goalId()
        assertEquals("$id:2", dao.setting(AtMost.SETTING))
        val goal = repository.goal(id)!!
        assertTrue(goal.atMost)
        assertEquals(2.0, goal.limit)
        assertEquals("cups", goal.unit)
        // Not counted several times, not a goal to quit or a challenge, no amount, time or reminder.
        assertEquals(1, goal.target)
        assertEquals(0, goal.bonus)
        assertFalse(goal.quit)
        assertNull(goal.challenge)
        assertNull(goal.amount)
        assertNull(goal.at)
        assertNull(goal.minutes)
        assertEquals(Reminder(onTime = false), goal.reminder)
        // The goals table has no limit: without the setting it is a plain habit.
        assertNull(dao.goal(id)!!.toCore(emptyMap()).limit)
        // A new limit replaces the old one.
        repository.saveHabit(draft(id = id, limit = 3.5))
        assertEquals("$id:3.5", dao.setting(AtMost.SETTING))
        assertEquals(3.5, store.goal(id)!!.limit)
    }

    @Test
    fun aGoalCountedOverAWeekOrWithAnAmountIsNotOne() = runTest {
        repository.saveHabit(draft(name = "Takeaways", repeat = Repeat(RepeatKind.TIMES_PER, start, per = Period.WEEKLY)))
        assertEquals("", dao.setting(AtMost.SETTING).orEmpty())
        assertFalse(repository.goal(goalId("Takeaways"))!!.atMost)
        // One with an amount stays as it is: what was entered toward it keeps its meaning.
        repository.saveHabit(draft(name = "Read", limit = null).copy(target = 1, amount = 20.0, unit = "pages", quit = false, challenge = null))
        val read = goalId("Read")
        repository.saveHabit(draft(id = read, name = "Read", limit = 5.0))
        assertFalse(repository.goal(read)!!.atMost)
    }

    @Test
    fun addingAndGoingOverWorkOnTheDatabase() = runTest {
        repository.saveHabit(draft(limit = 1.0))
        val id = goalId()
        lapses.closeDays()
        assertIs<UseResult.Added>(lapses.use(id))
        // What was added is a tick with no period: not a tick toward done.
        val use = dao.allTicks().single()
        assertNull(use.period)
        assertEquals(1.0, use.amount)
        val view = DayOrders.load(dao).view
        assertTrue(view.dueToday.isEmpty())
        assertEquals(1.0, view.limits.single().progress)
        assertFalse(view.limits.single().done)
        // "That's all for today" pays; going over takes it back and is a miss.
        assertIs<TickResult.Ticked>(book.tickHabit(id))
        assertEquals(6, dao.balance())
        val over = lapses.use(id) as UseResult.Added
        assertTrue(over.over)
        assertEquals(0, dao.balance())
        assertEquals(1, dao.allLapses().size)
        assertTrue(DayOrders.load(dao).view.limits.single().slipped)
        // Taken back, the miss goes too.
        assertTrue(lapses.undoUse(id))
        assertTrue(dao.allLapses().isEmpty())
    }

    @Test
    fun theLimitTravelsInTheBackupAndIsTidied() = runTest {
        repository.saveHabit(draft())
        val id = goalId()
        lapses.closeDays()
        lapses.use(id)
        val backup = Backup.decode(repository.exportBackup())
        assertEquals("$id:2", backup.settings[AtMost.SETTING])

        // Changed, then restored: the limit and what was added are back.
        repository.saveHabit(draft(id = id, limit = 9.0))
        lapses.undoUse(id)
        repository.restore(backup)
        assertEquals(2.0, repository.goal(id)!!.limit)
        assertEquals(1.0, DayOrders.load(dao).view.limits.single().progress)

        // A limit for a goal that isn't in the backup doesn't come in.
        repository.restore(backup.copy(settings = backup.settings + (AtMost.SETTING to "$id:2,999:4")))
        assertEquals("$id:2", dao.setting(AtMost.SETTING))

        // A backup from before "at most" goals: none.
        repository.restore(backup.copy(settings = backup.settings - AtMost.SETTING))
        assertFalse(repository.goal(id)!!.atMost)
        assertTrue(AtMostLimits.current.isEmpty())
    }

    @Test
    fun aCopyIsOneTooAndARemovedExampleTakesItsLimitAlong() = runTest {
        repository.saveHabit(draft())
        val id = goalId()
        val copy = repository.duplicateGoal(id)!!
        assertEquals(2.0, repository.goal(copy)!!.limit)
        assertEquals("$id:2,$copy:2", dao.setting(AtMost.SETTING))
        // Removed from the lists it keeps its limit, so bringing it back brings an "at most" goal back.
        repository.removeGoal(copy)
        assertEquals(2.0, repository.goal(copy)!!.limit)
        // Deleted for good (an example taken back before it was used), its limit goes.
        repository.removeExamples(AddedExamples(goals = listOf(copy), rewards = emptyList()))
        assertEquals("$id:2", dao.setting(AtMost.SETTING))
    }
}
