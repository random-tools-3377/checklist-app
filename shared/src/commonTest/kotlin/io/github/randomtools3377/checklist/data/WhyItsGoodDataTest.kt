package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A goal's "Why it's good", kept in the database, in a copy and in the backup file. */
class WhyItsGoodDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())

    @AfterTest
    fun close() = db.close()

    private suspend fun habit(name: String, why: String?): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = "🧘", points = 5, repeat = Repeat.everyDay(nowLocal().date.minusDays(1)), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null, why = why,
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    @Test
    fun theEditorsSaveItTidiedAndEmptyIsNone() = runTest {
        val stretch = habit("Stretch", "  so my back\n stops aching ")
        assertEquals("so my back stops aching", repository.goal(stretch)?.why)
        assertNull(repository.goal(habit("Water", "   "))?.why)
        assertNull(repository.goal(habit("Read", null))?.why)
        // Changing it in the editor, and clearing it.
        val draft = HabitDraft(
            id = stretch, name = "Stretch", icon = "🧘", points = 5, repeat = Repeat.everyDay(nowLocal().date.minusDays(1)), target = 1, bonus = 0,
            minutes = null, at = null, finishBy = null, finishDay = null,
        )
        repository.saveHabit(draft.copy(why = "to feel loose"))
        assertEquals("to feel loose", repository.goal(stretch)?.why)
        repository.saveHabit(draft.copy(why = ""))
        assertNull(repository.goal(stretch)?.why)
        // A project has one too.
        repository.saveProject(
            ProjectDraft(id = 0, name = "Shelf", icon = "🔨", steps = listOf(StepDraft(0, "Buy wood", 3)), bonus = 5, deadline = null, finishBy = null, why = "so the books have a home"),
        )
        val shelf = dao.activeGoals().single { it.name == "Shelf" }.id
        assertEquals("so the books have a home", repository.goal(shelf)?.why)
        repository.saveProject(ProjectDraft(id = shelf, name = "Shelf", icon = "🔨", steps = emptyList(), bonus = 5, deadline = null, finishBy = null))
        assertNull(repository.goal(shelf)?.why)
    }

    @Test
    fun aCopyKeepsIt() = runTest {
        val id = habit("Stretch", "so my back stops aching")
        val copy = repository.duplicateGoal(id)!!
        assertEquals("so my back stops aching", repository.goal(copy)?.why)
    }

    @Test
    fun itGoesInTheBackupAndAnOlderBackupImportsWithoutOne() = runTest {
        val stretch = habit("Stretch", "so my back stops aching")
        habit("Water", null)
        val text = repository.exportBackup()
        assertTrue("\"why\": \"so my back stops aching\"" in text)
        repository.restore(Backup.decode(text))
        assertEquals("so my back stops aching", repository.goal(stretch)?.why)

        // A file from 0.71.0: format 29, and no "why" in its goals at all.
        val older = text
            .replace(Regex(",\\s*\"why\": (null|\"[^\"]*\")"), "")
            .replace("\"format\": ${Backup.FORMAT}", "\"format\": 29")
        assertFalse("\"why\"" in older)
        repository.restore(Backup.decode(older))
        val goals = dao.activeCoreGoals()
        assertEquals(setOf("Stretch", "Water"), goals.map { it.name }.toSet())
        assertTrue(goals.all { it.why == null })
    }
}
