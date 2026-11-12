package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SkipCheck
import io.github.randomtools3377.checklist.core.SkipResult
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Taking a skip back, on the app's real database: the skip goes, and what it cost comes back. */
class TakeBackSkipDataTest {
    private val folder: File = Files.createTempDirectory("checklist-skip-test").toFile()
    private val phone = DesktopPhone(appVersion = "0.0.0", folder = folder)
    private val parts = phone.parts
    private val dao = parts.db.dao()

    @AfterTest
    fun close() {
        parts.db.close()
        folder.deleteRecursively()
    }

    private fun at(day: LocalDate, hour: Int, minute: Int) = LocalDateTime(day, LocalTime(hour, minute))

    private suspend fun addRun(allowance: Int): Long {
        parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Run", icon = "🏃", points = 10, repeat = Repeat.everyDay(nowLocal().date.plus(DatePeriod(days = -7))),
                target = 1, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null,
                reminder = Reminder(at = LocalTime(23, 58)), allowance = allowance,
            ),
        )
        return dao.activeGoals().single().id
    }

    @Test
    fun aSkipTakenBackLeavesNoSkipAndIsRemindedAgain() = runTest {
        val id = addRun(allowance = 1)
        val today = nowLocal().date
        assertEquals(1, DesktopReminders.due(parts, at(today, 23, 58), at(today, 23, 59)).size)

        assertTrue(parts.lapses.skip(id, reason = null, note = null) is SkipResult.Skipped)
        assertTrue(DesktopReminders.due(parts, at(today, 23, 58), at(today, 23, 59)).isEmpty())
        assertEquals(1, dao.allLapses().size)

        assertEquals(0, parts.lapses.takeBackSkip(id)?.refunded)

        assertTrue(dao.allLapses().isEmpty())
        assertTrue(dao.allEntries().isEmpty())
        assertEquals(1, DesktopReminders.due(parts, at(today, 23, 58), at(today, 23, 59)).size)
        assertEquals(SkipCheck.Allowed(used = 0, allowed = 1, overLimit = false, cost = 0), parts.lapses.checkSkip(id))
        assertNull(parts.lapses.takeBackSkip(id))
    }

    @Test
    fun overTheLimitThePointsComeBackAndThePenaltyTaskIsDropped() = runTest {
        val id = addRun(allowance = 0)
        val skipped = parts.lapses.skip(id, Reason.NO_TIME, "busy") as SkipResult.Skipped
        assertEquals(-5, dao.balance())
        assertEquals(1, dao.openPenalties().size)

        assertEquals(5, parts.lapses.takeBackSkip(id)?.refunded)

        assertEquals(0, dao.balance())
        assertTrue(dao.allLapses().isEmpty())
        assertTrue(dao.openPenalties().isEmpty())
        assertNotNull(dao.penalty(skipped.penaltyId!!)?.cancelledAt)
        // The points history keeps both lines: the penalty, and the same points coming back.
        assertEquals(listOf(EntryKind.PENALTY.name to -5, EntryKind.PENALTY.name to 5), dao.allEntries().sortedBy { it.id }.map { it.kind to it.amount })
    }

    @Test
    fun tickingASkippedGoalTakesTheSkipBack() = runTest {
        val id = addRun(allowance = 0)
        parts.lapses.skip(id, Reason.NO_TIME, null)
        assertTrue(parts.book.tickHabit(id) is TickResult.Ticked)
        assertTrue(dao.allLapses().isEmpty())
        assertTrue(dao.openPenalties().isEmpty())
        assertEquals(10, dao.balance())
    }

    @Test
    fun aTimesPerWeekGoalGetsOneSkipBack() = runTest {
        parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Swim", icon = "🏊", points = 5, repeat = Repeat.timesPer(Period.WEEKLY, nowLocal().date.plus(DatePeriod(days = -7))),
                target = 3, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null, allowance = 5,
            ),
        )
        val id = dao.activeGoals().single().id
        parts.lapses.skip(id, null, null)
        parts.lapses.skip(id, null, null)
        assertEquals(2, dao.allLapses().size)
        assertNotNull(parts.lapses.takeBackSkip(id))
        assertEquals(1, dao.allLapses().size)
    }

    @Test
    fun aLaterSkipIsWithinTheAllowanceOnceTheFreeOneIsTakenBack() = runTest {
        parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Swim", icon = "🏊", points = 10, repeat = Repeat.timesPer(Period.WEEKLY, nowLocal().date.plus(DatePeriod(days = -7))),
                target = 4, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null, allowance = 1,
            ),
        )
        val id = dao.activeGoals().single().id
        val free = parts.lapses.skip(id, null, null) as SkipResult.Skipped
        assertTrue(parts.lapses.skip(id, Reason.NO_TIME, null) is SkipResult.Skipped) // over the limit
        assertEquals(-5, dao.balance())
        assertEquals(1, dao.openPenalties().size)

        // The free skip goes: the one left would have been the free one, so it costs nothing after all.
        assertNotNull(parts.lapses.takeBackSkipById(free.lapse.id))

        assertEquals(listOf(false to 0), dao.allLapses().map { it.overLimit to it.cost })
        assertEquals(0, dao.balance())
        assertTrue(dao.openPenalties().isEmpty())
    }
}
