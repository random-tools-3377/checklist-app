package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgainAndBackupTest {
    private val day = LocalDate(2026, 11, 11)
    private val wholeDay = LocalDateTime(2026, 11, 11, 0, 0) to LocalDateTime(2026, 11, 12, 0, 0)

    private fun goal(reminder: Reminder, at: LocalTime? = null, finishBy: LocalTime? = null, times: List<LocalTime> = emptyList(), target: Int = 1) = Goal(
        id = 1, kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 1, repeat = Repeat.everyDay(day.minusDays(3)),
        at = at, finishBy = finishBy, reminder = reminder, times = times, target = target,
    )

    private fun alerts(g: Goal, defaultFinish: LocalTime? = null) =
        Alerts.between(listOf(g), wholeDay.first, wholeDay.second, dayEndHour = 0, defaultFinish = defaultFinish)

    @Test
    fun keepRemindingRepeatsUntilTheFinishTime() {
        val g = goal(Reminder(at = LocalTime(18, 0), againEvery = 30), finishBy = LocalTime(19, 30))
        assertEquals(
            listOf(LocalTime(18, 0) to AlertKind.REMINDER, LocalTime(18, 30) to AlertKind.AGAIN, LocalTime(19, 0) to AlertKind.AGAIN, LocalTime(19, 30) to AlertKind.AGAIN),
            alerts(g).map { it.at.time to it.kind },
        )
    }

    @Test
    fun withoutAFinishTimeItStopsAfterAFewHours() {
        val g = goal(Reminder(at = LocalTime(9, 0), againEvery = 30))
        val again = alerts(g).filter { it.kind == AlertKind.AGAIN }
        assertEquals(Alerts.MAX_AGAIN, again.size)
        assertEquals(LocalTime(15, 0), again.last().at.time)
    }

    @Test
    fun theDefaultFinishTimeCountsToo() {
        val g = goal(Reminder(onTime = true, againEvery = 60), at = LocalTime(20, 0))
        assertEquals(listOf(LocalTime(20, 0), LocalTime(21, 0)), alerts(g, defaultFinish = LocalTime(21, 0)).map { it.at.time })
    }

    @Test
    fun eachTimeOfOneDoneSeveralTimesKeepsRemindingUntilTheNext() {
        val g = goal(Reminder(onTime = true, againEvery = 60), times = listOf(LocalTime(9, 0), LocalTime(11, 0)), target = 2, finishBy = LocalTime(12, 0))
        assertEquals(
            listOf(LocalTime(9, 0) to 1, LocalTime(10, 0) to 1, LocalTime(11, 0) to 2, LocalTime(12, 0) to 2),
            alerts(g).map { it.at.time to it.number },
        )
    }

    @Test
    fun theEarlierAlertIsntRepeated() {
        val g = goal(Reminder(onTime = false, minutesBefore = 10, againEvery = 30), at = LocalTime(18, 0), finishBy = LocalTime(19, 0))
        assertEquals(listOf(AlertKind.BEFORE), alerts(g).map { it.kind })
    }

    @Test
    fun theBackupReminderComesWhenTheLastBackupIsAWeekOld() {
        val day = 86_400_000L
        val now = 100 * day
        assertFalse(BackupReminder.due(lastBackup = now - 6 * day, since = 0, now = now, hasData = true))
        assertTrue(BackupReminder.due(lastBackup = now - 7 * day, since = 0, now = now, hasData = true))
        assertFalse(BackupReminder.due(lastBackup = null, since = now - day, now = now, hasData = true))
        assertTrue(BackupReminder.due(lastBackup = null, since = now - 8 * day, now = now, hasData = true))
        assertFalse(BackupReminder.due(lastBackup = null, since = 0, now = now, hasData = false))
        assertEquals(9, BackupReminder.daysSince(now - 9 * day - 5, now))
    }
}
