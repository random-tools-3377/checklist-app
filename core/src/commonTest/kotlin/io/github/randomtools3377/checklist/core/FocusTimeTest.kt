package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Focus time kept: the minutes really focused, the review's totals, and the break after a session. */
class FocusTimeTest {
    private val monday = LocalDate(2026, 11, 9)
    private val minute = 60_000L

    private fun goal(id: Long, name: String, private: Boolean = false) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "📖", points = 5, repeat = Repeat.everyDay(LocalDate(2026, 11, 2)), private = private)

    private fun session(goalId: Long, day: LocalDate, minutes: Int) = FocusSession(goalId = goalId, day = day, startedAt = 1, minutes = minutes)

    @Test
    fun aSessionThatRunsOutCountsItsWholeLength() {
        val kept = FocusTime.session(goalId = 3, isBreak = false, totalMillis = 25 * minute, leftMillis = 0, startedAt = 100, day = monday)
        assertEquals(FocusSession(goalId = 3, day = monday, startedAt = 100, minutes = 25), kept)
    }

    @Test
    fun pausesAreNotCounted() {
        // The countdown stands still while paused, so however long the pause was, the time
        // focused is the timer's length less what is left on it.
        assertEquals(10 * minute, FocusTime.focusedMillis(totalMillis = 25 * minute, leftMillis = 15 * minute))
        val kept = FocusTime.session(3, false, totalMillis = 25 * minute, leftMillis = 15 * minute, startedAt = 100, day = monday)
        assertEquals(10, kept?.minutes)
    }

    @Test
    fun doneNowCountsOnlyWhatWasFocused() {
        assertEquals(7, FocusTime.session(3, false, 20 * minute, leftMillis = 13 * minute, startedAt = 1, day = monday)?.minutes)
        // To the nearest minute; under half a minute is nothing to keep.
        assertEquals(1, FocusTime.minutes(30_000))
        assertEquals(1, FocusTime.minutes(89_000))
        assertEquals(2, FocusTime.minutes(90_000))
        assertNull(FocusTime.session(3, false, 20 * minute, leftMillis = 20 * minute - 20_000, startedAt = 1, day = monday))
        // Odd numbers from a damaged timer never give time below nothing or above its length.
        assertEquals(0, FocusTime.focusedMillis(totalMillis = 5 * minute, leftMillis = 9 * minute))
        assertEquals(5 * minute, FocusTime.focusedMillis(totalMillis = 5 * minute, leftMillis = -minute))
    }

    @Test
    fun aBreakIsNeverFocusTime() {
        assertNull(FocusTime.session(goalId = -2, isBreak = true, totalMillis = 5 * minute, leftMillis = 0, startedAt = 1, day = monday))
        assertNull(FocusTime.session(goalId = 3, isBreak = true, totalMillis = 5 * minute, leftMillis = 0, startedAt = 1, day = monday))
        assertNull(FocusTime.session(goalId = -2, isBreak = false, totalMillis = 5 * minute, leftMillis = 0, startedAt = 1, day = monday))
    }

    @Test
    fun theBreakIsOffUntilALengthIsPicked() {
        assertEquals(0, FocusTime.breakMinutes(null))
        assertEquals(0, FocusTime.breakMinutes("0"))
        assertEquals(5, FocusTime.breakMinutes("5"))
        assertEquals(10, FocusTime.breakMinutes("10"))
        assertEquals(15, FocusTime.breakMinutes("15"))
        assertEquals(0, FocusTime.breakMinutes("7"))
        assertEquals(0, FocusTime.breakMinutes("soon"))
        assertEquals(listOf(0, 5, 10, 15), FocusTime.BREAK_CHOICES)
    }

    @Test
    fun theReviewAddsUpEachGoalAndTheTotal() {
        val read = goal(1, "Read")
        val write = goal(2, "Write")
        val sessions = listOf(
            session(1, monday, 20),
            session(1, monday.plusDays(2), 25),
            session(2, monday.plusDays(1), 50),
            session(2, monday.plusDays(7), 30), // next week
            session(1, monday.minusDays(1), 15), // last week
        )
        val week = FocusTime.totals(sessions, listOf(read, write), monday, monday.plusDays(6))
        assertTrue(week.any)
        assertEquals(95, week.minutes)
        assertEquals(3, week.sessions)
        assertEquals(listOf("Write" to 50, "Read" to 45), week.goals.map { it.goal.name to it.minutes })
        assertEquals(listOf(1, 2), week.goals.map { it.sessions })

        assertEquals(45, FocusTime.goalMinutes(sessions, 1, monday, monday.plusDays(6)))
        assertEquals(60, FocusTime.goalMinutes(sessions, 1, monday.minusDays(1), monday.plusDays(6)))
        assertEquals(0, FocusTime.goalMinutes(sessions, 9, monday, monday.plusDays(6)))
    }

    @Test
    fun nothingFocusedIsNothingToShow() {
        val none = FocusTime.totals(emptyList(), listOf(goal(1, "Read")), monday, monday.plusDays(6))
        assertFalse(none.any)
        assertEquals(0, none.minutes)
        assertTrue(none.goals.isEmpty())
    }

    @Test
    fun privateGoalsAreLeftOutWhileLocked() {
        val goals = listOf(goal(1, "Read"), goal(2, "Diary", private = true))
        val sessions = listOf(session(1, monday, 20), session(2, monday, 30))
        val open = FocusTime.totals(sessions, goals, monday, monday.plusDays(6))
        assertEquals(50, open.minutes)
        val locked = FocusTime.totals(sessions, goals, monday, monday.plusDays(6), hidePrivate = true)
        assertEquals(20, locked.minutes)
        assertEquals(listOf("Read"), locked.goals.map { it.goal.name })
    }

    @Test
    fun timeOnAGoalThatIsGoneStillCountsInTheTotal() {
        val totals = FocusTime.totals(listOf(session(1, monday, 20), session(8, monday, 10)), listOf(goal(1, "Read")), monday, monday.plusDays(6))
        assertEquals(30, totals.minutes)
        assertEquals(listOf("Read"), totals.goals.map { it.goal.name })
    }

    @Test
    fun focusTimeGoesInTheBackupAndOlderFilesStillOpen() {
        val backup = Backup(
            exportedAt = 1, goals = listOf(goal(1, "Read")), steps = emptyList(), ticks = emptyList(), entries = emptyList(),
            rewards = emptyList(), purchases = emptyList(), settings = mapOf(FocusTime.BREAK_SETTING to "10"),
            focusSessions = listOf(session(1, monday, 20)),
        )
        val text = Backup.encode(backup)
        assertEquals(28, Backup.FORMAT)
        assertEquals(backup, Backup.decode(text))
        // A file from before focus time was kept has none, and opens as before.
        val older = Backup.decode(Backup.encode(backup.copy(format = 27)).replace(Regex(",\\s*\"focusSessions\": \\[[^\\]]*\\]"), ""))
        assertEquals(27, older.format)
        assertTrue("focusSessions" !in Backup.encode(backup.copy(format = 27)).replace(Regex(",\\s*\"focusSessions\": \\[[^\\]]*\\]"), ""))
        assertTrue(older.focusSessions.isEmpty())
        assertEquals("10", older.settings[FocusTime.BREAK_SETTING])
    }
}
