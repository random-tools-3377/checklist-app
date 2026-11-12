package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.NudgeTone
import io.github.randomtools3377.checklist.core.Nudges
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SettingsSearch
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.stopNudges
import io.github.randomtools3377.checklist.ui.notificationWords
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Late nudges on the computer: shown in their minute like other reminders, in the same words. */
class DesktopNudgeTest {
    private val folder: File = Files.createTempDirectory("checklist-desktop-nudge-test").toFile()
    private val phone = DesktopPhone(appVersion = "0.0.0", folder = folder)
    private val dao = phone.parts.db.dao()

    @AfterTest
    fun close() {
        phone.parts.db.close()
        folder.deleteRecursively()
    }

    @Test
    fun aLateGoalIsNudgedInsteadOfRemindedAgainUntilStoppedForTheDay() = runTest {
        // Tomorrow, so nothing depends on the time the test runs at.
        val day = nowLocal().date.plus(DatePeriod(days = 1))
        phone.parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Reading", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date.minus(DatePeriod(days = 1))), target = 1, bonus = 0,
                minutes = null, at = LocalTime(9, 0), finishBy = null, finishDay = null, reminder = Reminder(againEvery = 30),
            ),
        )
        phone.parts.repository.setSetting(Nudges.TONES, Nudges.encodeTones(setOf(NudgeTone.ENCOURAGING)))
        fun minute(hour: Int, minute: Int) = LocalDateTime(day, LocalTime(hour, minute)) to LocalDateTime(day, LocalTime(hour, minute + 1))

        val (at, after) = minute(9, 0)
        assertEquals("📖 Reading" to "Now", DesktopReminders.due(phone.parts, at, after).single().let { it.title to it.text })

        // Half an hour late: one nudge, not a "Still to do" as well.
        val (late, lateEnd) = minute(9, 30)
        val first = DesktopReminders.due(phone.parts, late, lateEnd).single()
        assertEquals("📖 Reading · 30 min late", first.title)
        assertTrue("Reading" in first.text)
        val (later, laterEnd) = minute(10, 0)
        val second = DesktopReminders.due(phone.parts, later, laterEnd).single()
        assertEquals("📖 Reading · 1 h late", second.title)
        assertTrue(second.text != first.text, "two nudges in a row read differently")

        // "No more nudges today": the rest of that day's are gone, the day after's are not.
        stopNudges(dao, dao.activeGoals().single().id, day)
        assertTrue(DesktopReminders.due(phone.parts, later, laterEnd).isEmpty())
        val next = day.plus(DatePeriod(days = 1))
        assertEquals(1, DesktopReminders.due(phone.parts, LocalDateTime(next, LocalTime(9, 30)), LocalDateTime(next, LocalTime(9, 31))).size)
    }

    @Test
    fun aSettingsSearchFindsTheNudges() {
        for (typed in listOf("nudge", "late nudges", "nudging", "playful", "encouraging")) {
            assertTrue(SettingsSearch.matches(typed, notificationWords(phone)), typed)
        }
    }
}
