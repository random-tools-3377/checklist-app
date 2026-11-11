package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The morning's mood and the one once everything is done, in the database and the backup; and a chain set from them. */
class MoodDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val day = LocalDate(2026, 11, 11)

    @AfterTest
    fun close() = db.close()

    private suspend fun mood() = dao.allMoods().single().toCore()

    @Test
    fun theMorningsMoodAndTheOneAfterAreBothKept() = runTest {
        // Nothing to go beside: the second mood needs the morning's.
        assertFalse(repository.setMoodAfter(day, 4))
        assertTrue(dao.allMoods().isEmpty())

        repository.setMood(day, 2, "slow start")
        assertNull(mood().after)
        assertTrue(repository.setMoodAfter(day, 4))
        val both = mood()
        assertEquals(2, both.score)
        assertEquals(4, both.after)
        assertEquals("slow start", both.note)
        assertTrue((both.afterAt ?: 0) > 0)
        assertEquals(4, both.last)

        // Changing the morning's mood, or its note, keeps the second one.
        repository.setMood(day, 1, null)
        assertEquals(1, mood().score)
        assertEquals(4, mood().after)
        assertEquals("slow start", mood().note)
        repository.setMood(day, 1, "new note")
        assertEquals(4, mood().after)
        assertEquals("new note", mood().note)

        // Out of range is brought back to 1 to 5.
        repository.setMoodAfter(day, 9)
        assertEquals(5, mood().after)
    }

    @Test
    fun bothMoodsGoInTheBackup() = runTest {
        repository.setMood(day, 2, null)
        repository.setMoodAfter(day, 5)
        repository.setMood(day.minusDays(1), 3, null)

        val backup = Backup.decode(repository.exportBackup())
        assertEquals(Backup.FORMAT, backup.format)
        val moods = backup.moods.sortedBy { it.day }
        assertEquals(listOf(3, 2), moods.map { it.score })
        assertEquals(listOf(null, 5), moods.map { it.after })

        dao.deleteMoods()
        repository.restore(backup)
        val back = dao.allMoods().map { it.toCore() }.sortedBy { it.day }
        assertEquals(moods, back)
    }

    @Test
    fun aGoalCanBePutRightAfterOneThatLiftsButNotInALoop() = runTest {
        fun draft(name: String) = HabitDraft(
            id = 0, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(day), target = 1, bonus = 0,
            minutes = null, at = null, finishBy = null, finishDay = null,
        )
        repository.saveHabit(draft("Run"))
        repository.saveHabit(draft("Tidy desk"))
        val run = dao.allGoals().first { it.name == "Run" }
        val desk = dao.allGoals().first { it.name == "Tidy desk" }

        assertTrue(repository.setGoalAfter(desk.id, run.id))
        assertEquals(run.id, dao.goal(desk.id)!!.afterGoal)
        // Run can't now come after the desk: that would go round in a circle.
        assertFalse(repository.setGoalAfter(run.id, desk.id))
        assertNull(dao.goal(run.id)!!.afterGoal)
        assertFalse(repository.setGoalAfter(desk.id, desk.id))
    }
}
