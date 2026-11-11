package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SkipResult
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.ui.usesTwentyFourHours
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Windows app's side: its own folder, the values it keeps there, and which reminders it shows
 * while open (found as the iPhone finds them).
 */
class DesktopPhoneTest {
    private val folder: File = Files.createTempDirectory("checklist-desktop-test").toFile()
    private val phone = DesktopPhone(appVersion = "0.0.0", folder = folder)
    private val dao = phone.parts.db.dao()

    @AfterTest
    fun close() {
        phone.parts.db.close()
        folder.deleteRecursively()
    }

    private suspend fun addRead(at: LocalTime?, private: Boolean = false): Long {
        phone.parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date.plus(DatePeriod(days = -7))),
                target = 1, bonus = 0, minutes = 10, at = at, finishBy = null, finishDay = null, private = private,
            ),
        )
        return dao.activeGoals().single().id
    }

    private fun at(day: LocalDate, hour: Int, minute: Int) = LocalDateTime(day, LocalTime(hour, minute))

    @Test
    fun aReminderIsShownInItsMinute() = runTest {
        addRead(LocalTime(9, 0))
        val tomorrow = nowLocal().date.plus(DatePeriod(days = 1))
        val due = DesktopReminders.due(phone.parts, at(tomorrow, 9, 0), at(tomorrow, 9, 1))
        assertEquals(listOf("📖 Read" to "Now"), due.map { it.title to it.text })
        assertTrue(DesktopReminders.due(phone.parts, at(tomorrow, 9, 1), at(tomorrow, 9, 2)).isEmpty())
        assertTrue(DesktopReminders.due(phone.parts, at(tomorrow, 8, 59), at(tomorrow, 9, 0)).isEmpty())
    }

    @Test
    fun aLockedPrivateGoalIsNotNamed() = runTest {
        addRead(LocalTime(9, 0), private = true)
        dao.putSetting(SettingEntity(LOCK_PRIVATE, "1"))
        val tomorrow = nowLocal().date.plus(DatePeriod(days = 1))
        val due = DesktopReminders.due(phone.parts, at(tomorrow, 9, 0), at(tomorrow, 9, 1)).single()
        assertEquals("🔒 A private goal", due.title)
        assertFalse(due.text.contains("Read"))
    }

    @Test
    fun aGoalDoneTodayIsNotReminded() = runTest {
        val id = addRead(LocalTime(23, 58))
        val today = nowLocal().date
        assertEquals(1, DesktopReminders.due(phone.parts, at(today, 23, 58), at(today, 23, 59)).size)
        assertTrue(phone.parts.book.tickHabit(id) is TickResult.Ticked)
        assertTrue(DesktopReminders.due(phone.parts, at(today, 23, 58), at(today, 23, 59)).isEmpty())
    }

    @Test
    fun anAmountIsRemindedUntilAllOfItIsEntered() = runTest {
        phone.parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Pages", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date.plus(DatePeriod(days = -7))),
                target = 1, bonus = 0, minutes = null, at = LocalTime(23, 58), finishBy = null, finishDay = null, amount = 20.0, unit = "pages",
            ),
        )
        val id = dao.activeGoals().single().id
        val today = nowLocal().date
        phone.parts.book.logAmount(id, 5.0)
        assertEquals(1, DesktopReminders.due(phone.parts, at(today, 23, 58), at(today, 23, 59)).size)
        phone.parts.book.logAmount(id, 15.0)
        assertTrue(DesktopReminders.due(phone.parts, at(today, 23, 58), at(today, 23, 59)).isEmpty())
    }

    @Test
    fun aTimesPerWeekGoalWithASkipForItsLastTimeIsNotReminded() = runTest {
        phone.parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Swim", icon = "🏊", points = 5, repeat = Repeat.timesPer(Period.WEEKLY, nowLocal().date.plus(DatePeriod(days = -7))),
                target = 3, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null, reminder = Reminder(at = LocalTime(23, 58)),
            ),
        )
        val id = dao.activeGoals().single().id
        val today = nowLocal().date
        phone.parts.book.tickHabit(id)
        phone.parts.book.tickHabit(id)
        assertEquals(1, DesktopReminders.due(phone.parts, at(today, 23, 58), at(today, 23, 59)).size)
        assertTrue(phone.parts.lapses.skip(id, reason = null, note = null) is SkipResult.Skipped)
        assertTrue(DesktopReminders.due(phone.parts, at(today, 23, 58), at(today, 23, 59)).isEmpty())
    }

    @Test
    fun localValuesAreKeptInTheFolder() {
        phone.setLocalValue("emoji", "recent", "📖,💧")
        phone.startFocus(goalId = 7, minutes = 25)
        phone.setBackupReminderOn(false)
        val again = DesktopPhone(appVersion = "0.0.0", folder = folder)
        assertEquals("📖,💧", again.localValue("emoji", "recent"))
        assertEquals(7L, again.focus()?.goalId)
        assertEquals(25 * 60_000L, again.focus()?.totalMillis)
        assertFalse(again.backupReminderOn())
        again.stopFocus()
        assertEquals(null, DesktopPhone(appVersion = "0.0.0", folder = folder).focus())
    }

    @Test
    fun aPickedGifIsCopiedInAndTheUsersFileStays() = runTest {
        val picked = File(folder, "picked.gif").apply { writeBytes("GIF89a".toByteArray() + ByteArray(10)) }
        val name = phone.addGif(picked.absolutePath).getOrThrow()
        assertTrue(picked.isFile)
        assertEquals(16, phone.parts.gifs.read(name)?.size)
        val notGif = File(folder, "photo.gif").apply { writeBytes(ByteArray(16)) }
        assertTrue(phone.addGif(notGif.absolutePath).isFailure)
    }

    @Test
    fun theExportFileIsWrittenAndReadBack() = runTest {
        val file = File(folder, "backup.json").absolutePath
        phone.writeText(file, "{\"goals\": \"📖\"}")
        assertEquals("{\"goals\": \"📖\"}", phone.readText(file))
    }

    @Test
    fun theClockFollowsTheRegion() {
        assertFalse(usesTwentyFourHours(Locale.US))
        assertTrue(usesTwentyFourHours(Locale.GERMANY))
    }
}
