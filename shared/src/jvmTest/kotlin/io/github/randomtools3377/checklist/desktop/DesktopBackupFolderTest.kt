package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.data.AUTO_BACKUPS_KEPT
import io.github.randomtools3377.checklist.data.AutoBackupResult
import io.github.randomtools3377.checklist.ui.runningWords
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Windows app's automatic backup folder, keeping it running with the window closed, and the
 * shortcut in the Startup folder that starts it at sign-in.
 */
class DesktopBackupFolderTest {
    private val app: File = Files.createTempDirectory("checklist-desktop-app").toFile()
    private val chosen: File = Files.createTempDirectory("checklist-desktop-backups").toFile()
    private val appData: File = Files.createTempDirectory("checklist-desktop-roaming").toFile()
    private val day = LocalDate(2026, 3, 10)

    /** The shortcuts made, in place of Windows' own script host. */
    private val made = mutableListOf<Triple<File, String, String>>()

    private fun shortcut(exe: String? = "C:\\Programs\\Checklist\\Checklist.exe", windows: Boolean = true, writes: Boolean = true) =
        StartupShortcut(appData = appData.path, exe = exe, windows = windows) { file, target, arguments ->
            made += Triple(file, target, arguments)
            if (writes) file.writeText("shortcut")
        }

    private val phone = DesktopPhone(appVersion = "0.0.0", folder = app, startup = shortcut())

    @AfterTest
    fun close() {
        listOf(app, chosen, appData).forEach { it.deleteRecursively() }
    }

    private fun names() = chosen.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun nothingIsSavedUntilAFolderIsChosen() = runTest {
        assertTrue(phone.canPickFolder)
        assertNull(phone.backupFolder())
        assertNull(phone.backupFolderName())
        var asked = false
        assertEquals(AutoBackupResult.NotDue, phone.autoBackupIfDue(day) { asked = true; "{}" })
        assertFalse(asked)
        assertTrue(phone.autoBackups().isEmpty())
    }

    @Test
    fun aDatedFileIsSavedOnceADay() = runTest {
        phone.setBackupFolder(chosen.absolutePath)
        assertEquals(chosen.absolutePath, phone.backupFolder())
        assertEquals(chosen.absolutePath, phone.backupFolderName())
        assertNull(phone.lastBackup())
        assertEquals(AutoBackupResult.Saved, phone.autoBackupIfDue(day) { "{\"day\": 1}" })
        assertEquals(listOf("checklist-auto-2026-03-10.json"), names())
        assertEquals("{\"day\": 1}", File(chosen, "checklist-auto-2026-03-10.json").readText())
        assertTrue(phone.lastBackup() != null)
        // Later the same day nothing is made again, and the export isn't even asked for.
        var asked = false
        assertEquals(AutoBackupResult.NotDue, phone.autoBackupIfDue(day) { asked = true; "{\"day\": 2}" })
        assertFalse(asked)
        assertEquals("{\"day\": 1}", File(chosen, "checklist-auto-2026-03-10.json").readText())
        // The next day has its own file.
        assertEquals(AutoBackupResult.Saved, phone.autoBackupIfDue(day.plus(DatePeriod(days = 1))) { "{}" })
        assertEquals(listOf("checklist-auto-2026-03-10.json", "checklist-auto-2026-03-11.json"), names())
    }

    @Test
    fun theFolderIsRememberedOnThisComputerOnly() = runTest {
        phone.setBackupFolder(chosen.absolutePath)
        assertEquals(AutoBackupResult.Saved, phone.autoBackupIfDue(day) { "{}" })
        // Opened again, the folder and the day are still known: they are in the app's own local values.
        val again = DesktopPhone(appVersion = "0.0.0", folder = app, startup = shortcut())
        assertEquals(chosen.absolutePath, again.backupFolder())
        assertEquals(AutoBackupResult.NotDue, again.autoBackupIfDue(day) { "{}" })
        assertTrue("backup.auto_folder" in File(app, "local.properties").readText())
        // Choosing a folder again saves there straight away, even on the same day.
        again.setBackupFolder(chosen.absolutePath)
        assertEquals(AutoBackupResult.Saved, again.autoBackupIfDue(day) { "{}" })
        // Turned off, nothing more is saved.
        again.setBackupFolder(null)
        assertNull(again.backupFolder())
        assertEquals(AutoBackupResult.NotDue, again.autoBackupIfDue(day.plus(DatePeriod(days = 1))) { "{}" })
    }

    @Test
    fun theLastSevenAreKeptAndExportsAreNeverDeleted() = runTest {
        phone.setBackupFolder(chosen.absolutePath)
        // The user's own files in the same folder: exports, old and new, and something else entirely.
        File(chosen, "checklist-backup-2020-01-01.json").writeText("{\"mine\": true}")
        File(chosen, "checklist-backup-2026-03-12.json").writeText("{\"mine\": true}")
        File(chosen, "notes.txt").writeText("keep")
        File(chosen, "checklist-auto-notes.json").writeText("keep")
        repeat(10) { n -> assertEquals(AutoBackupResult.Saved, phone.autoBackupIfDue(day.plus(DatePeriod(days = n))) { "{\"n\": $n}" }) }
        val own = names().filter { it.startsWith("checklist-auto-2") }
        assertEquals(AUTO_BACKUPS_KEPT, own.size)
        assertEquals(7, own.size)
        assertEquals("checklist-auto-2026-03-13.json", own.first())
        assertEquals("checklist-auto-2026-03-19.json", own.last())
        assertEquals(
            listOf("checklist-auto-notes.json", "checklist-backup-2020-01-01.json", "checklist-backup-2026-03-12.json", "notes.txt"),
            names() - own.toSet(),
        )
        assertEquals("{\"mine\": true}", File(chosen, "checklist-backup-2020-01-01.json").readText())
        // No half-written files are left beside them.
        assertTrue(names().none { it.endsWith(".part") })
    }

    @Test
    fun theRestoreListIsNewestFirstWithExportsAndWithoutEmptyFiles() = runTest {
        phone.setBackupFolder(chosen.absolutePath)
        repeat(3) { n -> phone.autoBackupIfDue(day.plus(DatePeriod(days = n))) { "{}" } }
        File(chosen, "checklist-backup-2026-03-11.json").writeText("{}")
        File(chosen, "checklist-auto-2026-03-20.json").writeText("")
        File(chosen, "notes.txt").writeText("not a backup")
        File(chosen, "checklist-auto-2026-03-01.json").mkdir()
        val list = phone.autoBackups()
        assertEquals(
            listOf(
                "checklist-auto-2026-03-12.json",
                "checklist-backup-2026-03-11.json",
                "checklist-auto-2026-03-11.json",
                "checklist-auto-2026-03-10.json",
            ),
            list.map { it.first },
        )
        // Each one is the file's full path, which the app reads to restore it.
        assertEquals(File(chosen, "checklist-auto-2026-03-12.json").absolutePath, list.first().second)
        assertEquals("{}", phone.readText(list.first().second))
    }

    @Test
    fun aFolderThatIsGoneIsSaidSoAndABackupThatCantBeMadeLeavesNothing() = runTest {
        phone.setBackupFolder(File(chosen, "gone").absolutePath)
        var asked = false
        assertEquals(AutoBackupResult.FolderGone, phone.autoBackupIfDue(day) { asked = true; "{}" })
        assertFalse(asked)
        assertTrue(phone.autoBackups().isEmpty())
        phone.setBackupFolder(chosen.absolutePath)
        assertEquals(AutoBackupResult.Failed, phone.autoBackupIfDue(day) { error("no export") })
        assertTrue(names().isEmpty())
        // It tries again the next time, the same day.
        assertEquals(AutoBackupResult.Saved, phone.autoBackupIfDue(day) { "{}" })
    }

    @Test
    fun keepingRunningIsOnUntilTurnedOff() {
        assertTrue(phone.keepsRunning())
        phone.setKeepsRunning(false)
        assertFalse(phone.keepsRunning())
        assertFalse(DesktopPhone(appVersion = "0.0.0", folder = app, startup = shortcut()).keepsRunning())
        phone.setKeepsRunning(true)
        assertTrue(phone.keepsRunning())
        assertEquals("Reminders come while Checklist is running, also with the window closed.", runningWords(true))
        assertEquals("Reminders come only while the window is open.", runningWords(false))
        // With no icon in the notification area (as in a test), closing the window quits as before.
        assertFalse(phone.hideWindow())
        assertTrue(phone.windowShown.value)
    }

    @Test
    fun openingTheAppAgainAsksTheRunningCopyForItsWindow() {
        // Nothing is running to take the request up, so it is taken back and the caller says so.
        assertFalse(phone.askToShow(waitMillis = 150))
        assertFalse(File(app, "show.request").exists())
        assertFalse(phone.takeShowRequest())
        // A running copy takes it up, once.
        val running = Thread {
            repeat(100) {
                if (phone.takeShowRequest()) return@Thread
                Thread.sleep(20)
            }
        }
        running.start()
        assertTrue(phone.askToShow(waitMillis = 5_000))
        running.join()
        assertFalse(phone.takeShowRequest())
    }

    @Test
    fun theStartupShortcutGoesInTheUsersOwnStartupFolder() {
        val expected = File(appData, "Microsoft/Windows/Start Menu/Programs/Startup/Checklist.lnk")
        assertEquals(expected, shortcut().file)
        assertEquals(File(appData, "Microsoft/Windows/Start Menu/Programs/Startup"), StartupShortcut.startupFolder(appData.path))
        // Off to start with; turning it on makes the shortcut, to the installed app, starting hidden.
        assertTrue(phone.canStartAtSignIn)
        assertFalse(phone.startsAtSignIn())
        assertTrue(phone.setStartsAtSignIn(true))
        assertTrue(phone.startsAtSignIn())
        assertTrue(expected.isFile)
        assertEquals(listOf(Triple(expected, "C:\\Programs\\Checklist\\Checklist.exe", "--hidden")), made)
        // Turning it off takes the shortcut away, and nothing else.
        File(expected.parentFile, "Other.lnk").writeText("someone else's")
        assertTrue(phone.setStartsAtSignIn(false))
        assertFalse(phone.startsAtSignIn())
        assertFalse(expected.exists())
        assertTrue(File(expected.parentFile, "Other.lnk").isFile)
        // Off already is fine too.
        assertTrue(phone.setStartsAtSignIn(false))
    }

    @Test
    fun theStartupSwitchIsOnlyOfferedOnWindowsWithTheAppInstalled() {
        // Run from the code, not installed: there is no app to point a shortcut at.
        val notInstalled = shortcut(exe = null)
        assertFalse(notInstalled.available)
        assertFalse(notInstalled.set(true))
        assertTrue(made.isEmpty())
        // Not Windows: no Startup folder.
        val elsewhere = shortcut(windows = false)
        assertNull(elsewhere.file)
        assertFalse(elsewhere.available)
        assertFalse(elsewhere.set(true))
        assertFalse(elsewhere.isOn())
        assertFalse(StartupShortcut(appData = null, exe = "Checklist.exe", windows = true) { _, _, _ -> }.available)
        // If Windows couldn't write the shortcut, it says it isn't on.
        val failing = shortcut(writes = false)
        assertTrue(failing.available)
        assertFalse(failing.set(true))
        assertFalse(failing.isOn())
    }
}
