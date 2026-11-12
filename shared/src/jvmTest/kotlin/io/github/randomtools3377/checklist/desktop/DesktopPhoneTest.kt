package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.EveningCheck
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SkipResult
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.nextInChain
import io.github.randomtools3377.checklist.core.SettingsSearch
import io.github.randomtools3377.checklist.ui.HelpTopic
import io.github.randomtools3377.checklist.ui.LookPlace
import io.github.randomtools3377.checklist.ui.deviceTitle
import io.github.randomtools3377.checklist.ui.lookParts
import io.github.randomtools3377.checklist.ui.canShowEmoji
import io.github.randomtools3377.checklist.ui.notificationWords
import io.github.randomtools3377.checklist.ui.notificationsNotHere
import io.github.randomtools3377.checklist.ui.shownOn
import io.github.randomtools3377.checklist.ui.stepsOn
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
    fun theEveningCheckShowsWhatIsStillOpenAtItsTime() = runTest {
        val id = addRead(at = null)
        val today = nowLocal().date
        // Off until a time is picked.
        assertEquals(null, DesktopReminders.evening(phone.parts, at(today, 21, 0), at(today, 21, 1)))
        phone.parts.repository.setSetting(EveningCheck.SETTING, EveningCheck.encode(LocalTime(21, 0)))
        val shown = DesktopReminders.evening(phone.parts, at(today, 21, 0), at(today, 21, 1))
        assertEquals("1 still open today" to "📖 Read", shown?.let { it.title to it.text })
        assertEquals(null, DesktopReminders.evening(phone.parts, at(today, 21, 1), at(today, 21, 2)))
        // A private goal isn't named while the lock covers private goals.
        phone.parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Journal", icon = "📓", points = 5, repeat = Repeat.everyDay(today.plus(DatePeriod(days = -7))),
                target = 1, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null, private = true,
            ),
        )
        dao.putSetting(SettingEntity(LOCK_PRIVATE, "1"))
        assertEquals("📖 Read\n🔒 A private goal", DesktopReminders.evening(phone.parts, at(today, 21, 0), at(today, 21, 1))?.text)
        // Everything done: nothing is shown.
        assertTrue(phone.parts.book.tickHabit(id) is TickResult.Ticked)
        assertTrue(phone.parts.book.tickHabit(dao.activeGoals().single { it.name == "Journal" }.id) is TickResult.Ticked)
        assertEquals(null, DesktopReminders.evening(phone.parts, at(today, 21, 0), at(today, 21, 1)))
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
        // The timer is handed over once when it ends, so its goal is ticked once.
        phone.startFocus(goalId = 7, minutes = 25)
        assertEquals(7L, phone.takeFocus()?.goalId)
        assertEquals(null, phone.takeFocus())
        assertEquals(null, phone.focus())
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

    @Test
    fun whatAComputerCantDoIsLeftOutOrSaidSo() {
        assertEquals("Morning plan, weekly summary and snooze", notificationsNotHere(phone))
        assertFalse(phone.canBuzz)
        assertFalse(phone.hasRecentsPreview)
        // No guides for widgets or the AI, and no backup folder steps.
        assertFalse(HelpTopic.WIDGETS.shownOn(phone))
        assertFalse(HelpTopic.AI.shownOn(phone))
        assertTrue(HelpTopic.BACKUP.shownOn(phone))
        val backup = HelpTopic.BACKUP.stepsOn(phone)
        assertEquals(HelpTopic.BACKUP.steps.size - 2, backup.size)
        assertTrue(backup.none { "folder" in it || "automatic" in it })
    }

    @Test
    fun aComputerIsCalledAComputerAndItsLockSaysSo() {
        assertEquals("computer", phone.deviceWord)
        assertEquals("Computer", phone.deviceTitle)
        assertFalse(phone.outOfDeviceBackups)
        assertFalse(phone.canAskForLock)
        // A Settings search finds only what Appearance shows on a computer: no buzz, icon or widgets.
        val parts = lookParts(phone, matchWallpaper = false)
        assertTrue(parts.none { it.title == "App icon" || it.title == "Widgets" })
        assertTrue(parts.none { SettingsSearch.matches("buzz", it.words) })
        assertTrue(parts.any { it.key == LookPlace.CELEBRATIONS && SettingsSearch.matches("sounds", it.words) })
        assertTrue(parts.any { it.key == LookPlace.THEME && SettingsSearch.matches("dark mode", it.words) })
        // The evening check is found with the notifications, here as on a phone.
        assertTrue(SettingsSearch.matches("evening check", notificationWords(phone)))
        assertFalse(SettingsSearch.matches("widget", notificationWords(phone)))
    }

    @Test
    fun anEmojiNoFontHasIsLeftOut() {
        assertFalse(canShowEmoji("\u200D"))
        // Skia's own library isn't in every test run; where it is, a character no font has is left out.
        if (runCatching { org.jetbrains.skia.FontMgr.default }.isFailure) return
        assertFalse(canShowEmoji("\uDBFF\uDFFD"))
    }

    @Test
    fun theNextGoalInAChainIsShownAfterATick() = runTest {
        val coffee = addRead(null)
        phone.parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Journal", icon = "📓", points = 5, repeat = Repeat.everyDay(nowLocal().date.plus(DatePeriod(days = -7))),
                target = 1, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null, after = coffee,
            ),
        )
        assertTrue(phone.parts.book.tickHabit(coffee) is TickResult.Ticked)
        assertEquals(listOf("📓 Journal"), nextInChain(dao, coffee).map { it.title })
    }
}
