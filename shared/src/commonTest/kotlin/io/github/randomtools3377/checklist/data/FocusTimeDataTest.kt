package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.FocusTime
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.reminders.FocusState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Focus time in the database and the backup: what a timer that ended leaves behind. */
class FocusTimeDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val day = LocalDate(2026, 11, 11)
    private val minute = 60_000L

    /** [hour]:[minute] on [day], on this machine's clock. */
    private fun at(hour: Int, minute: Int = 0, on: LocalDate = day): Long =
        LocalDateTime(on.year, on.monthNumber, on.dayOfMonth, hour, minute).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

    @AfterTest
    fun close() = db.close()

    private suspend fun goal(name: String): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = "📖", points = 4, repeat = Repeat.everyDay(day), target = 1, bonus = 0,
                minutes = 25, at = null, finishBy = null, finishDay = null,
            ),
        )
        return dao.allGoals().first { it.name == name }.id
    }

    private suspend fun sessions() = dao.allFocusSessions().map { it.toCore() }

    @Test
    fun aTimerThatRanOutKeepsItsWholeLengthOnTheDayItEnded() = runTest {
        val read = goal("Read")
        val start = at(10)
        val state = FocusState(read, endsAt = start + 25 * minute, pausedLeft = null, totalMillis = 25 * minute, startedAt = start)
        // Found two hours after it ran out, as when the app was closed: still 25 minutes.
        val kept = repository.recordFocus(state, now = start + 145 * minute)
        assertEquals(25, kept?.minutes)
        val row = sessions().single()
        assertEquals(read, row.goalId)
        assertEquals(day, row.day)
        assertEquals(start, row.startedAt)
        assertEquals(25, row.minutes)
        assertTrue(row.id > 0)
    }

    @Test
    fun pausesAreLeftOut() = runTest {
        val read = goal("Read")
        val start = at(10)
        // Ten minutes in it was paused for an hour, then carried on to the end: the phone moves
        // the end on by the pause, so the session spans 85 minutes of the clock and keeps 25.
        val resumed = FocusState(read, endsAt = start + 85 * minute, pausedLeft = null, totalMillis = 25 * minute, startedAt = start)
        assertEquals(25, repository.recordFocus(resumed, now = start + 85 * minute)?.minutes)
        // Stopped while paused with 15 minutes left, long after: 10 minutes were focused.
        val paused = FocusState(read, endsAt = start + 25 * minute, pausedLeft = 15 * minute, totalMillis = 25 * minute, startedAt = start)
        assertEquals(10, repository.recordFocus(paused, now = start + 300 * minute)?.minutes)
        assertEquals(listOf(25, 10), sessions().map { it.minutes })
    }

    @Test
    fun doneNowKeepsOnlyTheTimeSoFar() = runTest {
        val read = goal("Read")
        val start = at(10)
        val state = FocusState(read, endsAt = start + 25 * minute, pausedLeft = null, totalMillis = 25 * minute, startedAt = start)
        assertEquals(7, repository.recordFocus(state, now = start + 7 * minute)?.minutes)
        // Stopped at once: nothing to keep.
        assertNull(repository.recordFocus(state, now = start + 10_000))
        assertEquals(listOf(7), sessions().map { it.minutes })
    }

    @Test
    fun aBreakIsNeverKept() = runTest {
        goal("Read")
        val start = at(10)
        val rest = FocusState(FocusState.BREAK_GOAL, endsAt = start + 5 * minute, pausedLeft = null, totalMillis = 5 * minute, startedAt = start)
        assertTrue(rest.isBreak)
        assertNull(repository.recordFocus(rest, now = start + 5 * minute))
        assertNull(repository.recordFocus(rest, now = start + 2 * minute))
        assertTrue(sessions().isEmpty())
    }

    @Test
    fun aLateSessionBelongsToTheUsersDay() = runTest {
        val read = goal("Read")
        repository.setSetting(SETTING_DAY_END_HOUR, "4")
        // It ends at 1 am, before the day ends at 4: still the day before.
        val start = at(0, 35, on = LocalDate(2026, 11, 12))
        val state = FocusState(read, endsAt = start + 25 * minute, pausedLeft = null, totalMillis = 25 * minute, startedAt = start)
        repository.recordFocus(state, now = start + 25 * minute)
        assertEquals(day, sessions().single().day)
    }

    @Test
    fun aTimerFromBeforeStartsWereKeptGetsOneWorkedOut() = runTest {
        val read = goal("Read")
        val end = at(11)
        val state = FocusState(read, endsAt = end, pausedLeft = null, totalMillis = 25 * minute)
        repository.recordFocus(state, now = end)
        assertEquals(end - 25 * minute, sessions().single().startedAt)
    }

    @Test
    fun eachGoalsMinutesAndTheReviewsTotals() = runTest {
        val read = goal("Read")
        val write = goal("Write")
        fun ran(goalId: Long, on: LocalDate, minutes: Int): FocusState {
            val start = at(9, on = on)
            return FocusState(goalId, endsAt = start + minutes * minute, pausedLeft = null, totalMillis = minutes * minute, startedAt = start)
        }
        val monday = LocalDate(2026, 11, 9)
        for ((state, on) in listOf(ran(read, monday, 20) to monday, ran(read, day, 25) to day, ran(write, day, 50) to day, ran(read, monday.minusDays1(), 15) to monday.minusDays1())) {
            repository.recordFocus(state, now = at(12, on = on))
        }
        assertEquals(45, repository.focusMinutes(read, monday, monday.plusDays6()))
        assertEquals(60, repository.focusMinutes(read, monday.minusDays1(), monday.plusDays6()))
        assertEquals(45, repository.observeFocusMinutes(read, monday, monday.plusDays6()).first())
        assertEquals(0, repository.focusMinutes(write, monday, monday))

        val rows = dao.observeFocusSessions(monday.toEpochDay(), monday.plusDays6().toEpochDay()).first().map { it.toCore() }
        val totals = FocusTime.totals(rows, dao.allGoals().map { it.toCore() }, monday, monday.plusDays6())
        assertEquals(95, totals.minutes)
        assertEquals(listOf("Write" to 50, "Read" to 45), totals.goals.map { it.goal.name to it.minutes })
    }

    private fun LocalDate.minusDays1() = LocalDate.fromEpochDays(toEpochDays() - 1)
    private fun LocalDate.plusDays6() = LocalDate.fromEpochDays(toEpochDays() + 6)

    @Test
    fun focusTimeAndTheBreakSettingGoInTheBackup() = runTest {
        val read = goal("Read")
        val start = at(10)
        repository.recordFocus(FocusState(read, start + 25 * minute, null, 25 * minute, start), now = start + 25 * minute)
        repository.recordFocus(FocusState(read, start + 90 * minute, null, 25 * minute, start + 65 * minute), now = start + 75 * minute)
        repository.setSetting(FocusTime.BREAK_SETTING, "10")
        assertEquals(10, repository.focusBreakMinutes())

        val backup = Backup.decode(repository.exportBackup())
        assertEquals(Backup.FORMAT, backup.format)
        assertEquals(listOf(25, 10), backup.focusSessions.map { it.minutes })
        val before = sessions()

        // Importing wipes what was there, then puts the file's sessions back as they were.
        repository.recordFocus(FocusState(read, start + 200 * minute, null, 5 * minute, start + 195 * minute), now = start + 200 * minute)
        assertEquals(3, sessions().size)
        repository.restore(backup)
        assertEquals(before, sessions())
        assertEquals(10, repository.focusBreakMinutes())

        // A file from before focus time was kept brings none, and clears what was there.
        repository.restore(backup.copy(format = 27, focusSessions = emptyList()))
        assertTrue(sessions().isEmpty())
    }
}
