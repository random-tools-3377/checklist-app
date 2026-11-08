package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupTest {
    private val day = LocalDate(2026, 11, 11)

    private val backup = Backup(
        exportedAt = 1_800_000_000_000,
        goals = listOf(
            Goal(
                id = 1, kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 1,
                repeat = Repeat(RepeatKind.WEEKDAYS, day, interval = 2, weekdays = setOf(1, 3)),
                target = 8, bonus = 5, minutes = 2, at = LocalTime(9, 30), finishBy = LocalTime(21, 0),
                position = 3, createdAt = 42, celebration = Celebration.own("own-1a2b.gif"),
            ),
            Goal(id = 2, kind = GoalKind.PROJECT, name = "Shelf", icon = "🔨", points = 0, repeat = null, deadline = day),
        ),
        steps = listOf(Step(id = 10, goalId = 2, name = "Buy wood", points = 5, position = 0)),
        ticks = listOf(Tick(id = 100, goalId = 1, stepId = null, at = 5, day = day, period = Period.DAILY, periodStart = day)),
        entries = listOf(Entry(id = 200, at = 5, day = day, kind = EntryKind.TICK, amount = 1, goalId = 1, tickId = 100, label = "Water")),
        rewards = listOf(Reward(id = 300, name = "Coffee", icon = "☕", price = 4)),
        purchases = listOf(Purchase(id = 400, rewardId = 300, name = "Coffee", icon = "☕", price = 4, at = 9, returnedAt = 10)),
        settings = mapOf("day_end_hour" to "4"),
        lapses = listOf(
            Lapse(
                id = 500, goalId = 1, kind = LapseKind.MISS, day = day, period = Period.DAILY, periodStart = day, at = 7,
                overLimit = true, cost = 1, reason = Reason.ILL, note = "flu", label = "Water",
            ),
        ),
        penalties = listOf(Penalty(id = 600, lapseId = 500, createdAt = 7, choice = PenaltyChoice.TASK, taskId = 700, label = "Push-ups", doneAt = 8)),
        penaltyTasks = listOf(PenaltyTask(id = 700, name = "Push-ups")),
        gaps = listOf(Gap(id = 800, from = day, to = day.plusDays(2), kind = GapKind.NOTHING_TICKED, createdAt = 9, reason = Reason.ILL, note = "flu")),
        files = mapOf("own-1a2b.gif" to "R0lGODlhAQABAAAAACw="),
    )

    @Test
    fun aBackupReadsBackExactly() {
        assertEquals(backup, Backup.decode(Backup.encode(backup)))
    }

    @Test
    fun theFileSaysWhatItIs() {
        val text = Backup.encode(backup)
        assertTrue(text.contains("\"app\": \"checklist\""))
        assertTrue(text.contains("\"format\": 4"))
    }

    @Test
    fun otherFilesAreRefusedInPlainWords() {
        val notJson = assertFailsWith<BackupError> { Backup.decode("hello") }
        assertEquals("This file isn't a Checklist backup, or it is damaged.", notJson.message)
        assertFailsWith<BackupError> { Backup.decode(Backup.encode(backup).replace("\"checklist\"", "\"other\"")) }
    }

    @Test
    fun aBackupFromANewerVersionIsRefused() {
        val newer = Backup.encode(backup).replace("\"format\": 4", "\"format\": 99")
        val error = assertFailsWith<BackupError> { Backup.decode(newer) }
        assertEquals("This backup was made by a newer version of the app. Update the app first.", error.message)
    }

    @Test
    fun aFormat1BackupStillReads() {
        val old = Backup.encode(backup.copy(lapses = emptyList(), penalties = emptyList(), penaltyTasks = emptyList(), gaps = emptyList()))
            .replace("\"format\": 4", "\"format\": 1")
            .replace(Regex(",\\s*\"lapses\": \\[\\],\\s*\"penalties\": \\[\\],\\s*\"penaltyTasks\": \\[\\]"), "")
        val read = Backup.decode(old)
        assertEquals(1, read.format)
        assertTrue(read.lapses.isEmpty())
    }

    @Test
    fun aRepeatSurvivesBeingStoredAsText() {
        val repeat = Repeat(RepeatKind.MONTH_WEEKDAY, day, end = day.plusDays(90), interval = 2, weekdays = setOf(5), weekOfMonth = Repeat.LAST_WEEK)
        assertEquals(repeat, RepeatText.decode(RepeatText.encode(repeat)))
    }
}
