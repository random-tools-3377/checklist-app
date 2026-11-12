package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.BackupSummary
import io.github.randomtools3377.checklist.core.Bill
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.Check
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.FocusSession
import io.github.randomtools3377.checklist.core.Gap
import io.github.randomtools3377.checklist.core.GapKind
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.ImportCheck
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.Penalty
import io.github.randomtools3377.checklist.core.PenaltyTask
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Purchase
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.SavingGoal
import io.github.randomtools3377.checklist.core.Step
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.Todos
import io.github.randomtools3377.checklist.core.WorkDay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Backup safety in the database: "Erase everything" leaves nothing, an import keeps this device's
 * key for protected files, the "before import" copies are never cleaned up with the daily ones,
 * and the import question counts what goes.
 */
class BackupSafetyDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class Files : GifStore {
        val kept = mutableMapOf<String, ByteArray>()
        override fun read(name: String): ByteArray? = kept[name]
        override fun write(name: String, bytes: ByteArray) {
            kept[name] = bytes
        }
        override fun keepOnly(names: Set<String>) {
            kept.keys.retainAll(names)
        }
    }

    private class Photo : PhotoStore {
        var bytes: ByteArray? = byteArrayOf(1, 2, 3)
        override fun read(): ByteArray? = bytes
        override fun write(bytes: ByteArray) {
            this.bytes = bytes
        }
        override fun delete() {
            bytes = null
        }
    }

    private val files = Files()
    private val photo = Photo()
    private val repository = Repository(db, files, photo)
    private val day = LocalDate(2026, 11, 12)

    @AfterTest
    fun close() = db.close()

    /** One of everything the app keeps. */
    private fun full() = Backup(
        exportedAt = 5,
        goals = listOf(
            Goal(id = 1, kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 5, repeat = Repeat.everyDay(day)),
            Goal(id = 2, kind = GoalKind.PROJECT, name = "Shed", icon = "📦", points = 0, repeat = null),
            Todos.of("Buy milk").copy(id = 3),
        ),
        steps = listOf(Step(id = 1, goalId = 2, name = "Clear out", points = 3, position = 0)),
        ticks = listOf(Tick(id = 1, goalId = 1, stepId = null, at = 10, day = day, period = Period.DAILY, periodStart = day)),
        entries = listOf(Entry(id = 1, at = 10, day = day, kind = EntryKind.TICK, amount = 5, goalId = 1, tickId = 1, label = "Water")),
        rewards = listOf(Reward(id = 1, name = "Coffee", icon = "☕", price = 4)),
        purchases = listOf(Purchase(id = 1, rewardId = 1, name = "Coffee", icon = "☕", price = 4, at = 11)),
        settings = mapOf("day_end_hour" to "4", Budget_PAY to "x"),
        lapses = listOf(
            Lapse(id = 1, goalId = 1, kind = LapseKind.MISS, day = day, period = Period.DAILY, periodStart = day, at = 12, overLimit = true, label = "Water"),
        ),
        penalties = listOf(Penalty(id = 1, lapseId = 1, createdAt = 12)),
        penaltyTasks = listOf(PenaltyTask(id = 1, name = "Ten push-ups")),
        gaps = listOf(Gap(id = 1, from = day, to = day, kind = GapKind.NOTHING_TICKED, createdAt = 13)),
        checks = listOf(Check(id = 1, goalId = 2, stepId = 1, periodStart = day, at = 14)),
        moods = listOf(Mood(day, 4, note = "A good day")),
        money = listOf(MoneyEntry(id = 1, kind = MoneyKind.SPENT, cents = 250, day = day)),
        moneyCategories = Budget.STARTER,
        workDays = listOf(WorkDay(day)),
        bills = listOf(Bill(id = 1, name = "Rent", icon = "🏠", cents = 1000, first = day)),
        savingGoals = listOf(SavingGoal(id = 1, name = "Bike", icon = "🚲", targetCents = 50_000)),
        focusSessions = listOf(FocusSession(id = 1, goalId = 1, day = day, startedAt = 15, minutes = 25)),
    )

    @Test
    fun eraseLeavesNothing() = runTest {
        repository.restore(full())
        repository.setSetting(SETTING_BACKUP_KEY, "1000:AAAA:AAAA")
        files.write("own.gif", byteArrayOf(1))
        assertFalse(repository.summary().isEmpty)
        assertEquals(1, dao.allFocusSessions().size)

        repository.eraseEverything()

        assertTrue(dao.allGoals().isEmpty())
        assertTrue(dao.allSteps().isEmpty())
        assertTrue(dao.allTicks().isEmpty())
        assertTrue(dao.allEntries().isEmpty())
        assertTrue(dao.allRewards().isEmpty())
        assertTrue(dao.allPurchases().isEmpty())
        assertTrue(dao.allSettings().isEmpty())
        assertTrue(dao.allLapses().isEmpty())
        assertTrue(dao.allPenalties().isEmpty())
        assertTrue(dao.allPenaltyTasks().isEmpty())
        assertTrue(dao.allGaps().isEmpty())
        assertTrue(dao.allChecks().isEmpty())
        assertTrue(dao.allMoods().isEmpty())
        assertTrue(dao.allMoney().isEmpty())
        assertTrue(dao.moneyCategories().isEmpty())
        assertTrue(dao.allWorkDays().isEmpty())
        assertTrue(dao.allBills().isEmpty())
        assertTrue(dao.allSavingGoals().isEmpty())
        assertTrue(dao.allFocusSessions().isEmpty())
        // The picked GIFs, sounds and photo go too, and what is left is what a new install has.
        assertTrue(files.kept.isEmpty())
        assertNull(photo.bytes)
        assertTrue(repository.summary().isEmpty)
        val after = Backup.decode(repository.exportBackup())
        assertTrue(after.goals.isEmpty() && after.settings.isEmpty() && after.files.isEmpty() && after.background == null)
    }

    @Test
    fun anImportKeepsThisDevicesKeyAndAnExportLeavesItOut() = runTest {
        repository.setSetting(SETTING_BACKUP_KEY, "1000:AAAA:AAAA")
        // Neither this device's key nor one written into a file travels.
        assertFalse(SETTING_BACKUP_KEY in Backup.decode(repository.exportBackup()).settings)
        repository.restore(full().copy(settings = mapOf("day_end_hour" to "4", SETTING_BACKUP_KEY to "someone else's")))
        assertEquals("1000:AAAA:AAAA", dao.setting(SETTING_BACKUP_KEY))
        assertEquals("4", dao.setting("day_end_hour"))
    }

    @Test
    fun aProtectedExportImportsBackWithEverything() = runTest {
        repository.restore(full())
        val plain = repository.exportBackup()
        val file = BackupCrypto.seal(plain, BackupCrypto.newKey("correct horse battery", iterations = 1000))
        repository.eraseEverything()

        repository.restore(Backup.decode(BackupCrypto.open(file, "correct horse battery")))

        val summary = repository.summary()
        assertEquals(BackupSummary.of(Backup.decode(plain)), summary)
        assertEquals("1 habit, 1 project, 1 to-do, 1 reward and 1 tick", summary.counts())
        assertTrue(summary.budget && summary.moods && summary.focus)
        assertEquals("A good day", dao.allMoods().single().note)
    }

    @Test
    fun theImportQuestionWarnsAboutWhatOnlyThisDeviceHas() = runTest {
        repository.restore(full())
        val device = repository.summary()
        val old = BackupSummary.of(full().copy(moods = emptyList(), focusSessions = emptyList()))
        assertEquals(listOf("the mood journal", "focus time"), ImportCheck.missing(old, device))
        assertNotNull(ImportCheck.warning(old, device, "phone"))
        assertNull(ImportCheck.warning(BackupSummary.of(full()), device, "phone"))
    }

    @Test
    fun beforeImportCopiesHaveTheirOwnNameAndTheirOwnCount() {
        val name = AutoBackupFiles.beforeImportName(LocalDateTime(2026, 11, 12, 9, 5, 7))
        assertEquals("checklist-before-import-2026-11-12-090507.json", name)
        assertTrue(AutoBackupFiles.isBeforeImport(name))
        assertTrue(AutoBackupFiles.isListed(name))
        assertFalse(AutoBackupFiles.isOwn(name))
        assertEquals(LocalDate(2026, 11, 12), AutoBackupFiles.dayOf(name))
        assertEquals(kotlinx.datetime.LocalTime(9, 5), AutoBackupFiles.timeOf(name))
        assertNull(AutoBackupFiles.timeOf("checklist-auto-2026-11-12.json"))

        // The daily clean-up never deletes one, however many newer automatic backups there are.
        val autos = (1..20).map { "checklist-auto-2026-12-${it.toString().padStart(2, '0')}.json" }
        val copies = listOf(
            "checklist-before-import-2026-11-12-090507.json",
            "checklist-before-import-2026-11-12-101500.json",
            "checklist-before-import-2026-10-01-080000.json",
            "checklist-before-import-2026-11-13-070000.json",
        )
        assertTrue(AutoBackupFiles.toDelete(autos + copies) { it }.none(AutoBackupFiles::isBeforeImport))
        // Its own count keeps the newest three, and only touches its own kind.
        assertEquals(listOf("checklist-before-import-2026-10-01-080000.json"), AutoBackupFiles.beforeImportsToDelete(autos + copies) { it })
        assertTrue(AutoBackupFiles.beforeImportsToDelete(autos + copies.take(3)) { it }.isEmpty())
        // Restore lists the newest first, with a copy made on a day above that day's automatic backup.
        val listed = AutoBackupFiles.newestFirst(listOf("checklist-auto-2026-11-12.json") + copies.take(2)) { it }
        assertEquals(
            listOf("checklist-before-import-2026-11-12-101500.json", "checklist-before-import-2026-11-12-090507.json", "checklist-auto-2026-11-12.json"),
            listed,
        )
    }

    private companion object {
        const val Budget_PAY = io.github.randomtools3377.checklist.core.PayPlan.SETTING
    }
}
