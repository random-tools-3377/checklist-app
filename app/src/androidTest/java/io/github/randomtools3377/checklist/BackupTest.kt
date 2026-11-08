package io.github.randomtools3377.checklist

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.BuyResult
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.data.AppDatabase
import io.github.randomtools3377.checklist.data.CelebrationFiles
import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.Repository
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.RoomStore
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Export everything, wipe it, import it back: everything is as it was. Also refunds and reordering. */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val folder = java.io.File(context.cacheDir, "backup-test").apply { deleteRecursively() }
    private val gifs = CelebrationFiles(folder)
    private val repository = Repository(db, gifs)
    private val book = Book(RoomStore(db))

    @After
    fun close() = db.close()

    private suspend fun habit(name: String, repeat: Repeat) = repository.saveHabit(
        HabitDraft(
            id = 0, name = name, icon = "⭐", points = 10, repeat = repeat, target = 1, bonus = 0,
            minutes = 30, at = LocalTime(18, 0), finishBy = null, finishDay = null,
        ),
    )

    @Test
    fun exportThenImportRestoresEverything() = runTest {
        val today = nowLocal().date
        habit("Water", Repeat.everyDay(today))
        habit("Gym", Repeat(RepeatKind.TIMES_PER, today, per = Period.WEEKLY))
        repository.saveReward(RewardDraft(0, "Coffee", "☕", 4))
        val water = db.dao().activeGoals().first { it.name == "Water" }.id
        book.tickHabit(water)
        val bought = book.buy(db.dao().activeRewards().single().id) as BuyResult.Bought

        val text = repository.exportBackup()
        val before = book.balance()

        // Wipe everything by importing an empty backup, then import the real one.
        repository.restore(
            Backup(
                exportedAt = 0, goals = emptyList(), steps = emptyList(), ticks = emptyList(), entries = emptyList(),
                rewards = emptyList(), purchases = emptyList(), settings = emptyMap(),
            ),
        )
        assertEquals(0, book.balance())
        assertTrue(db.dao().activeGoals().isEmpty())

        repository.restore(Backup.decode(text))

        assertEquals(before, book.balance())
        assertEquals(listOf("Water", "Gym"), db.dao().activeGoals().map { it.name })
        assertEquals(LocalTime(18, 0), repository.goal(water)!!.at)
        assertEquals(30, repository.goal(water)!!.minutes)
        assertTrue(book.returnPurchase(bought.purchaseId))
        assertEquals(10, book.balance())
    }

    @Test
    fun aPickedGifTravelsWithTheBackup() = runTest {
        val gif = "GIF89a".toByteArray() + ByteArray(64) { it.toByte() }
        val name = gifs.add(gif.inputStream()).getOrThrow()
        repository.saveHabit(
            HabitDraft(
                id = 0, name = "Run", icon = "🏃", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null, celebration = Celebration.own(name),
            ),
        )
        val text = repository.exportBackup()

        repository.restore(
            Backup(
                exportedAt = 0, goals = emptyList(), steps = emptyList(), ticks = emptyList(), entries = emptyList(),
                rewards = emptyList(), purchases = emptyList(), settings = emptyMap(),
            ),
        )
        assertEquals(null, gifs.read(name))

        repository.restore(Backup.decode(text))
        assertTrue(gif.contentEquals(gifs.read(name)))
        assertEquals(Celebration.own(name), db.dao().activeGoals().single().celebration)
    }

    @Test
    fun onlyGifsAreKept() {
        assertTrue(gifs.add("not a gif".byteInputStream()).isFailure)
    }

    @Test
    fun goalsCanBeReordered() = runTest {
        val today = nowLocal().date
        habit("A", Repeat.everyDay(today))
        habit("B", Repeat.everyDay(today))
        habit("C", Repeat.everyDay(today))
        val c = db.dao().activeGoals().first { it.name == "C" }.id

        repository.moveGoal(c, -1)
        assertEquals(listOf("A", "C", "B"), db.dao().activeGoals().map { it.name })
        repository.moveGoal(c, -1)
        assertEquals(listOf("C", "A", "B"), db.dao().activeGoals().map { it.name })
        repository.moveGoal(c, -1) // already first: nothing changes
        assertEquals(listOf("C", "A", "B"), db.dao().activeGoals().map { it.name })
    }
}
