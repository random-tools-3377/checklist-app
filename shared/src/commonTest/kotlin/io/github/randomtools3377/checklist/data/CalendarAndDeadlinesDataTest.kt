package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.core.CalendarShow
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.LineKind
import io.github.randomtools3377.checklist.core.LineState
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The calendar widget's data and a project's deadline reminders, as every phone and the computer
 * find them: from the database as it stands.
 */
class CalendarAndDeadlinesDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class MemoryGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, MemoryGifs())
    private val book = Book(RoomStore(db))

    /** The user's day now (the day ends at midnight here), and noon on it. */
    private val today = Days.dayOf(nowLocal(), 0)
    private val noon = LocalDateTime(today, LocalTime(12, 0))

    @AfterTest
    fun close() = db.close()

    private suspend fun addHabit(name: String, at: LocalTime? = null, private: Boolean = false): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(today.minusDays(7)),
                target = 1, bonus = 0, minutes = null, at = at, finishBy = null, finishDay = null, private = private,
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    private suspend fun addProject(name: String, daysAhead: Int?, reminder: Reminder? = null, private: Boolean = false, steps: Int = 3): Long {
        repository.saveProject(
            ProjectDraft(
                id = 0, name = name, icon = "🎨", steps = (1..steps).map { StepDraft(0, "Step $it", 2) }, bonus = 5,
                deadline = daysAhead?.let { today.plusDays(it) }, finishBy = null, private = private, reminder = reminder,
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    private fun at(daysAhead: Int, hour: Int, minute: Int = 0) = LocalDateTime(today.plusDays(daysAhead), LocalTime(hour, minute))

    @Test
    fun theBoardListsTheNextDaysWithDeadlinesToDosAndEvents() = runTest {
        val walk = addHabit("Walk", at = LocalTime(9, 0))
        addHabit("Water")
        addProject("Paint the shed", daysAhead = 2)
        repository.saveTodo(TodoDraft(id = 0, name = "Post the letter", date = today.plusDays(1)))
        repository.saveTodo(TodoDraft(id = 0, name = "Buy milk"))
        val events = listOf(Busy(at(1, 10), at(1, 11), "Dentist"))

        val board = calendarBoard(dao, noon, events)
        assertEquals(today, board.today)
        assertEquals(listOf("Today", "Tomorrow"), board.days.take(2).map { it.header })
        assertEquals(7, board.days.size)
        assertEquals(listOf("Walk", "Water", "Buy milk"), board.days[0].lines.map { it.name })
        // 9 am has passed at noon.
        assertEquals(LineState.LATE, board.days[0].lines[0].state)
        assertEquals(listOf("Walk", "Dentist", "Water", "Post the letter"), board.days[1].lines.map { it.name })
        val deadline = board.days[2].lines.first()
        assertEquals(LineKind.DEADLINE, deadline.kind)
        assertEquals("Deadline · 3 steps left", deadline.detail)
        assertEquals(1, board.month.first { it.date == today.plusDays(2) }.deadlines)
        assertEquals(2 to 0, board.month.first { it.isToday }.let { it.due to it.done })
        assertFalse(board.addsTodo)

        // A tick shows as done, and a step done leaves one fewer.
        assertTrue(book.tickHabit(walk) is TickResult.Ticked)
        val shed = dao.activeGoals().single { it.name == "Paint the shed" }.id
        assertTrue(book.tickStep(dao.activeSteps(shed).first().id) is TickResult.Ticked)
        repository.setSetting(Feature.TODOS.key, Feature.TODOS.onValue)
        val after = calendarBoard(dao, noon, events)
        assertEquals(LineState.DONE, after.days[0].lines.first { it.name == "Walk" }.state)
        assertEquals(1, after.days[0].done)
        assertEquals("Deadline · 2 steps left", after.days[2].lines.first().detail)
        assertEquals(2 to 1, after.month.first { it.isToday }.let { it.due to it.done })
        assertTrue(after.addsTodo)
    }

    @Test
    fun theBoardLeavesOutWhatTheWidgetWasToldToHide() = runTest {
        addHabit("Water")
        addProject("Paint the shed", daysAhead = 1)
        repository.saveTodo(TodoDraft(id = 0, name = "Post the letter", date = today.plusDays(1)))
        repository.saveTodo(TodoDraft(id = 0, name = "Buy milk"))
        val events = listOf(Busy(at(1, 10), at(1, 11), "Dentist"))
        val tomorrow = today.plusDays(1)

        // To start: everything but the count under each day.
        val usual = calendarBoard(dao, noon, events, picked = tomorrow)
        assertFalse(usual.shows.counts)
        assertEquals(listOf("Paint the shed", "Dentist", "Water", "Post the letter"), usual.days[1].lines.map { it.name })
        assertEquals(usual.days[1].lines.map { it.name }, usual.picked?.lines?.map { it.name })
        assertEquals(listOf(LineKind.EVENT, LineKind.DEADLINE, LineKind.TODO), usual.labels[tomorrow]?.map { it.kind })

        repository.setSetting(CalendarShow.COUNTS.key, "1")
        repository.setSetting(CalendarShow.EVENTS.key, "0")
        repository.setSetting(CalendarShow.DAILY_GOALS.key, "0")
        val some = calendarBoard(dao, noon, events, picked = tomorrow)
        assertTrue(some.shows.counts)
        assertEquals(listOf("Buy milk"), some.days[0].lines.map { it.name })
        // Today's count is still the whole day's.
        assertEquals(2, some.days[0].due)
        assertEquals(listOf("Paint the shed", "Post the letter"), some.days[1].lines.map { it.name })
        assertEquals(listOf("Paint the shed", "Post the letter"), some.picked?.lines?.map { it.name })
        assertEquals(listOf(LineKind.DEADLINE, LineKind.TODO), some.labels[tomorrow]?.map { it.kind })

        repository.setSetting(CalendarShow.DEADLINES.key, "0")
        repository.setSetting(CalendarShow.TODOS.key, "0")
        val none = calendarBoard(dao, noon, events, picked = tomorrow)
        // Only today is left, with the to-do that has no date.
        assertEquals(listOf(today), none.days.map { it.date })
        assertEquals(listOf("Buy milk"), none.days[0].lines.map { it.name })
        assertTrue(none.picked?.lines.orEmpty().isEmpty())
        assertTrue(none.labels.isEmpty())
        // The choices travel in the backup file.
        assertTrue("\"${CalendarShow.TODOS.key}\"" in repository.exportBackup())
    }

    @Test
    fun aLockedPrivateGoalIsNotNamedOnTheBoard() = runTest {
        addHabit("Secret thing", private = true)
        addProject("Secret plan", daysAhead = 0, private = true)
        assertTrue(calendarBoard(dao, noon).days[0].lines.any { it.name == "Secret thing" })
        repository.setSetting(LOCK_PRIVATE, "1")
        val lines = calendarBoard(dao, noon).days[0].lines
        assertEquals(listOf("🔒 A private goal", "🔒 A private goal"), lines.map { it.title })
        assertTrue(lines.none { it.canTick })
    }

    @Test
    fun thePhonesCalendarChoiceIsReadFromSettings() = runTest {
        assertEquals(false to emptySet<Long>(), calendarChoice(dao))
        repository.setSetting(SETTING_CALENDAR, "1")
        repository.setSetting(SETTING_CALENDAR_IDS, "3,7")
        assertEquals(true to setOf(3L, 7L), calendarChoice(dao))
    }

    @Test
    fun aDeadlineRemindsTheDayBeforeAndOnTheDayAtTheDaysStart() = runTest {
        addProject("Paint the shed", daysAhead = 1)
        // Until "My day starts at" is set, and with no time of its own, there is none.
        assertTrue(deadlineTimes(dao, at(0, 0), at(2, 0)).isEmpty())
        repository.setSetting(SETTING_DAY_START, (7 * 60 + 30).toString())
        assertEquals(listOf(at(0, 7, 30), at(1, 7, 30)), deadlineTimes(dao, at(0, 0), at(2, 0)).map { it.at })

        val first = deadlineReminders(dao, at(0, 7, 30), at(0, 7, 31)).single()
        assertEquals("🎨 Paint the shed", first.title)
        assertEquals("Due tomorrow, 3 steps left", first.text)
        assertEquals("Due today, 3 steps left", deadlineReminders(dao, at(1, 7, 30), at(1, 7, 31)).single().text)
        // Not in the minutes around it.
        assertTrue(deadlineReminders(dao, at(0, 7, 31), at(0, 7, 32)).isEmpty())
        assertTrue(deadlineReminders(dao, at(0, 7, 29), at(0, 7, 30)).isEmpty())
    }

    @Test
    fun theProjectsEditorPicksATimeOrTurnsItOffAndItIsKept() = runTest {
        val timed = addProject("Timed", daysAhead = 1, reminder = Reminder(at = LocalTime(18, 0)))
        val off = addProject("Off", daysAhead = 1, reminder = Reminder(onTime = false))
        assertEquals(LocalTime(18, 0), repository.goal(timed)?.reminder?.at)
        assertEquals(false, repository.goal(off)?.reminder?.onTime)
        assertEquals(listOf(timed to at(0, 18), timed to at(1, 18)), deadlineTimes(dao, at(0, 0), at(2, 0)).map { it.goalId to it.at })

        // Saved again from the editor as it starts (on, at the day's start): nothing is kept.
        repository.saveProject(
            ProjectDraft(
                id = off, name = "Off", icon = "🎨", steps = dao.activeSteps(off).map { StepDraft(it.id, it.name, it.points) }, bonus = 5,
                deadline = today.plusDays(1), finishBy = null,
            ),
        )
        assertNull(repository.goal(off)?.reminder)
        repository.setSetting(SETTING_DAY_START, (8 * 60).toString())
        assertEquals(setOf(timed, off), deadlineTimes(dao, at(0, 0), at(2, 0)).map { it.goalId }.toSet())

        // The choice travels in the backup file with the goal.
        val file = repository.exportBackup()
        assertTrue("18:00" in file)
    }

    @Test
    fun aFinishedProjectDoesNotRemindAndALockedPrivateOneIsNotNamed() = runTest {
        repository.setSetting(SETTING_DAY_START, (8 * 60).toString())
        val shed = addProject("Paint the shed", daysAhead = 0, steps = 2)
        addProject("Secret plan", daysAhead = 0, private = true)
        repository.setSetting(LOCK_PRIVATE, "1")
        val due = deadlineReminders(dao, at(0, 8), at(0, 8, 1))
        assertEquals(listOf("🎨 Paint the shed" to "Due today, 2 steps left", "🔒 A private goal" to "Due today"), due.map { it.title to it.text })

        dao.activeSteps(shed).forEach { assertTrue(book.tickStep(it.id) is TickResult.Ticked) }
        assertEquals(listOf("🔒 A private goal"), deadlineReminders(dao, at(0, 8), at(0, 8, 1)).map { it.title })
    }
}
