package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.BackupSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the "Replace everything?" question and the erase question say. */
class BackupWordsTest {
    private val file = BackupSummary(habits = 3, projects = 1, todos = 2, rewards = 4, ticks = 120, balance = 55, budget = true, moods = true, focus = false)
    private val device = BackupSummary(habits = 1, projects = 0, todos = 0, rewards = 1, ticks = 7, balance = 9, budget = false, moods = false, focus = false)
    private val nothing = BackupSummary(0, 0, 0, 0, 0, 0, budget = false, moods = false, focus = false)

    @Test
    fun theImportQuestionSaysWhatTheFileHasAndEverythingThatGoes() {
        val text = BackupWords.importText("12 Nov 2026", file, device, "55 points", "phone", copyFolder = null)
        assertTrue("It has 3 habits, 1 project, 2 to-dos, 4 rewards and 120 ticks, with a balance of 55 points." in text, text)
        assertTrue("the budget (spending, bills, savings and pay), the mood journal and focus time" in text, text)
        assertTrue(text.endsWith("What is here now (1 habit, 0 projects, 0 to-dos, 1 reward and 7 ticks) will be gone. Tap \"Export first\" to keep a copy."), text)
    }

    @Test
    fun withAFolderItSaysWhereTheCopyGoes() {
        val text = BackupWords.importText("12 Nov 2026", file, device, "55 points", "phone", copyFolder = "Backups")
        assertTrue("is saved in Backups first, as a \"Before import\" copy. \"Restore an automatic backup\" brings it back." in text, text)
        // Nothing here yet: nothing to copy, and nothing to lose.
        val empty = BackupWords.importText("12 Nov 2026", file, nothing, "55 points", "computer", copyFolder = "Backups")
        assertTrue(empty.endsWith("There is nothing on this computer yet, so nothing is lost."), empty)
    }

    @Test
    fun theEraseQuestionNamesEverythingThatGoes() {
        assertEquals(
            "This removes your goals, rewards, points, history, the budget, moods, focus time and settings, and the app starts fresh.",
            BackupWords.ERASE,
        )
        assertEquals("Backup files you already saved stay where they are.", BackupWords.ERASE_KEEPS)
    }

    @Test
    fun backupsToRestoreAreListedByDayAndACopyBeforeAnImportSaysSo() {
        assertEquals("11 Nov 2026", BackupWords.listed("checklist-auto-2026-11-11.json"))
        assertTrue(BackupWords.listed("checklist-before-import-2026-11-12-090507.json").startsWith("Before import · 12 Nov 2026, "))
        assertEquals("notes.json", BackupWords.listed("notes.json"))
    }
}
