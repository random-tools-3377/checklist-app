package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.CelebrationSounds
import io.github.randomtools3377.checklist.core.CelebrationSounds.Kind
import io.github.randomtools3377.checklist.core.Repeat
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The user's own celebration sounds: which file is used, tidying up, and the backup file. */
class CelebrationSoundsDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class MemoryFiles : GifStore {
        val files = mutableMapOf<String, ByteArray>()
        override fun read(name: String): ByteArray? = files[name]
        override fun write(name: String, bytes: ByteArray) {
            files[name] = bytes
        }
        override fun keepOnly(names: Set<String>) {
            files.keys.retainAll(names)
        }
    }

    private val files = MemoryFiles()
    private val repository = Repository(db, files)

    private val chime = "own-0000000000000001.sound"
    private val fanfare = "own-0000000000000002.sound"
    private val purr = "own-0000000000000003.sound"

    @AfterTest
    fun close() = db.close()

    private fun draft(id: Long = 0, sound: String? = null) = HabitDraft(
        id = id, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(LocalDate(2026, 11, 1)), target = 1, bonus = 0,
        minutes = null, at = null, finishBy = null, finishDay = null, sound = sound,
    )

    private suspend fun onlyGoal(): Long = dao.activeGoals().single().id

    @Test
    fun aPickedSoundIsKeptInItsSettingAndTheOldFileGoes() = runTest {
        files.write(chime, byteArrayOf(1))
        repository.setCelebrationSound(Kind.TICK, chime)
        assertEquals(chime, dao.setting(Kind.TICK.setting))
        assertNull(dao.setting(Kind.PROJECT.setting))

        // Another one takes its place, and the first file is deleted.
        files.write(fanfare, byteArrayOf(2))
        repository.setCelebrationSound(Kind.TICK, fanfare)
        assertEquals(setOf(fanfare), files.files.keys)

        // "Use the app's sound" puts the usual one back.
        repository.setCelebrationSound(Kind.TICK, null)
        assertEquals("", dao.setting(Kind.TICK.setting))
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun aNameThatIsNotASoundFileIsNeverSaved() = runTest {
        repository.setCelebrationSound(Kind.REWARD, "../../somewhere.sound")
        assertEquals("", dao.setting(Kind.REWARD.setting))
    }

    @Test
    fun aGoalKeepsItsOwnSoundUntilItIsTakenOff() = runTest {
        files.write(purr, byteArrayOf(3))
        repository.saveHabit(draft(sound = purr))
        val id = onlyGoal()
        assertEquals(purr, repository.goalSound(id))
        assertEquals(setOf(purr), files.files.keys)

        // Saving it again with the same sound keeps it; a removed goal keeps it too, to come back with.
        repository.saveHabit(draft(id = id, sound = purr))
        repository.removeGoal(id)
        repository.saveHabit(draft().copy(name = "Walk"))
        assertEquals(purr, repository.goalSound(id))
        assertEquals(setOf(purr), files.files.keys)

        repository.saveHabit(draft(id = id, sound = null))
        assertNull(repository.goalSound(id))
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun aCopyOfAGoalCelebratesWithTheSameSound() = runTest {
        files.write(purr, byteArrayOf(3))
        repository.saveHabit(draft(sound = purr))
        val id = onlyGoal()
        val copy = repository.duplicateGoal(id, LocalDate(2026, 11, 11))!!
        assertEquals(purr, repository.goalSound(copy))
        // Taking it off one leaves the other's.
        repository.saveHabit(draft(id = id, sound = null))
        assertEquals(setOf(purr), files.files.keys)
    }

    @Test
    fun pickedSoundsGoInTheBackupFileAndComeBack() = runTest {
        files.write(chime, byteArrayOf(1, 2, 3))
        repository.setCelebrationSound(Kind.TICK, chime)
        files.write(purr, byteArrayOf(9, 8, 7, 6))
        repository.saveHabit(draft(sound = purr))
        repository.setSetting(SETTING_CELEBRATE_GLOW, "0")
        val id = onlyGoal()

        val text = repository.exportBackup()
        val backup = Backup.decode(text)
        assertEquals(Backup.FORMAT, backup.format)
        assertEquals(setOf(chime, purr), backup.files.keys)

        // Everything goes, then the file brings it all back.
        repository.setCelebrationSound(Kind.TICK, null)
        repository.saveHabit(draft(id = id, sound = null))
        repository.setSetting(SETTING_CELEBRATE_GLOW, "1")
        assertTrue(files.files.isEmpty())

        repository.restore(backup)
        assertEquals(chime, dao.setting(Kind.TICK.setting))
        assertEquals(purr, repository.goalSound(id))
        assertEquals("0", dao.setting(SETTING_CELEBRATE_GLOW))
        assertContentEquals(byteArrayOf(1, 2, 3), files.read(chime))
        assertContentEquals(byteArrayOf(9, 8, 7, 6), files.read(purr))
    }

    @Test
    fun aBackupCannotBringInFilesNothingUses() = runTest {
        repository.saveHabit(draft())
        val backup = Backup.decode(repository.exportBackup())
        val crafted = backup.copy(
            files = mapOf(fanfare to "AQID", "../evil.sound" to "AQID"),
            settings = backup.settings + (Kind.PROJECT.setting to "../evil.sound") + (CelebrationSounds.GOALS_SETTING to "999:$fanfare"),
        )
        repository.restore(crafted)
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun aBackupFromBeforeSoundsStillRestores() = runTest {
        files.write(chime, byteArrayOf(1))
        repository.setCelebrationSound(Kind.TICK, chime)
        repository.saveHabit(draft())
        val backup = Backup.decode(repository.exportBackup())
        val older = backup.copy(format = 27, files = emptyMap(), settings = backup.settings - Kind.TICK.setting)
        repository.restore(older)
        assertNull(dao.setting(Kind.TICK.setting))
        assertTrue(files.files.isEmpty())
        assertEquals("Read", dao.activeGoals().single().name)
    }
}
