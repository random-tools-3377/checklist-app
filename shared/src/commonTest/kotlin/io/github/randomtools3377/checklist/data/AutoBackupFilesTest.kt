package io.github.randomtools3377.checklist.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The automatic backup deletes only its own old files, never an export saved in the same folder. */
class AutoBackupFilesTest {
    @Test
    fun itsOwnFilesHaveTheirOwnName() {
        val name = AutoBackupFiles.name(LocalDate(2026, 11, 11))
        assertEquals("checklist-auto-2026-11-11.json", name)
        assertTrue(AutoBackupFiles.isOwn(name))
        assertTrue(AutoBackupFiles.isOwn("checklist-auto-2026-11-11 (1).json"))
        assertFalse(AutoBackupFiles.isOwn("checklist-backup-2026-11-11.json"))
    }

    @Test
    fun exportsInTheFolderAreNeverDeleted() {
        val own = (1..10).map { "checklist-auto-2026-11-${it.toString().padStart(2, '0')}.json" }
        val exports = listOf("checklist-backup-2026-11-01.json", "checklist-backup-2026-10-01 (1).json", "notes.txt")
        val deleted = AutoBackupFiles.toDelete(exports + own) { it }
        assertEquals(listOf("checklist-auto-2026-11-03.json", "checklist-auto-2026-11-02.json", "checklist-auto-2026-11-01.json"), deleted)
    }

    @Test
    fun theRestoreListIsNewestDayFirstWhateverTheName() {
        val names = listOf("checklist-backup-2026-11-09.json", "checklist-auto-2026-11-11.json", "checklist-auto-2026-11-10.json", "photo.jpg")
        val listed = AutoBackupFiles.newestFirst(names.filter(AutoBackupFiles::isListed)) { it }
        assertEquals(listOf("checklist-auto-2026-11-11.json", "checklist-auto-2026-11-10.json", "checklist-backup-2026-11-09.json"), listed)
    }
}
