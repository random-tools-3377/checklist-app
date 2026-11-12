package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.MoodFactors
import io.github.randomtools3377.checklist.core.MoodInsights
import io.github.randomtools3377.checklist.core.MoodRaise
import io.github.randomtools3377.checklist.core.MoodRaises
import io.github.randomtools3377.checklist.core.MoodReminder
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The morning's mood and the one once everything is done, their notes and the things behind them,
 * in the database and the backup; the list of things; and a chain set from the moods.
 */
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
    fun notesAndTheThingsBehindAMoodAreSavedWithTheDay() = runTest {
        // Nothing to go with yet.
        assertFalse(repository.setMoodNote(day, "hello"))
        assertFalse(repository.toggleMoodFactor(day, "Work"))

        repository.setMood(day, 2, null)
        assertTrue(repository.toggleMoodFactor(day, "Slept badly"))
        assertTrue(repository.toggleMoodFactor(day, "Work"))
        assertTrue(repository.setMoodNote(day, "  tired after a late night  "))
        assertEquals(listOf("Slept badly", "Work"), mood().factors)
        assertEquals("tired after a late night", mood().note)

        // Once everything is done: its own note; the factors are the day's, either check-in can change them.
        repository.setMoodAfter(day, 4)
        repository.setMoodNote(day, "went for a walk", after = true)
        repository.toggleMoodFactor(day, "work")
        repository.addMoodFactor("Sunshine", day)
        val later = mood()
        assertEquals(4, later.after)
        assertEquals("went for a walk", later.afterNote)
        assertEquals("tired after a late night", later.note, "the morning's note stays")
        assertEquals(listOf("Slept badly", "Sunshine"), later.factors)
        // The user's own thing is on the list now, after the app's own.
        assertEquals(MoodFactors.BUILT_IN + "Sunshine", repository.moodFactorList())

        // Changing the morning's mood keeps all of it.
        repository.setMood(day, 3, null)
        assertEquals(listOf("Slept badly", "Sunshine"), mood().factors)
        assertEquals("went for a walk", mood().afterNote)

        // A blank note clears it.
        repository.setMoodNote(day, "   ", after = true)
        assertNull(mood().afterNote)

        // Taking a thing off the list leaves it on the day.
        repository.setMoodFactorList(MoodFactors.remove(repository.moodFactorList(), "Slept badly"))
        assertFalse(MoodFactors.has(repository.moodFactorList(), "Slept badly"))
        assertEquals(listOf("Slept badly", "Sunshine"), mood().factors)
        // Every one taken off stays taken off.
        repository.setMoodFactorList(emptyList())
        assertTrue(repository.moodFactorList().isEmpty())
    }

    @Test
    fun aDayCanBeChangedInTheJournal() = runTest {
        repository.setMood(day, 2, "first")
        val before = mood()
        assertTrue(repository.editMoodDay(before.copy(score = 3, after = 4, note = " ", afterNote = "later on", factors = listOf("Food", "food", "Health"))))
        val changed = mood()
        assertEquals(3, changed.score)
        assertEquals(4, changed.after)
        assertTrue((changed.afterAt ?: 0) > 0, "a new mood after gets its time")
        assertNull(changed.note)
        assertEquals("later on", changed.afterNote)
        assertEquals(listOf("Food", "Health"), changed.factors)
        assertEquals(before.at, changed.at, "the morning's mood keeps when it was given")
        assertFalse(repository.editMoodDay(before.copy(day = day.minusDays(3))), "no mood that day")
    }

    @Test
    fun aMissedDayCanBeAddedAndADayDeleted() = runTest {
        val missed = day.minusDays(3)
        // A day still to come can't be filled in.
        assertFalse(repository.addMoodDay(Mood(day.plusDays(1), 3), today = day))
        assertTrue(dao.allMoods().isEmpty())

        assertTrue(repository.addMoodDay(Mood(missed, 9, note = "  a slow day ", after = 4, afterNote = "better by night", factors = listOf("Work", "work")), today = day))
        val added = mood()
        assertEquals(missed, added.day)
        assertEquals(5, added.score, "out of range is brought back to 1 to 5")
        assertEquals(4, added.after)
        assertEquals("a slow day", added.note)
        assertEquals("better by night", added.afterNote)
        assertEquals(listOf("Work"), added.factors)
        assertTrue(added.at > 0 && (added.afterAt ?: 0) > 0)
        // A day that has a mood isn't added over; it is changed instead.
        assertFalse(repository.addMoodDay(Mood(missed, 1), today = day))
        assertEquals(5, mood().score)
        // A put-off later mood can be added to a day afterwards.
        assertTrue(repository.addMoodDay(Mood(day, 2), today = day))
        assertTrue(repository.editMoodDay(Mood(day, 2, after = 4)))
        assertEquals(4, dao.mood(day.toEpochDay())!!.toCore().after)

        // Deleting takes the whole day, and only that day.
        repository.deleteMoodDay(missed)
        assertEquals(listOf(day), dao.allMoods().map { it.toCore().day })
        // What Today remembered about a deleted today goes too, so it asks afresh.
        repository.setSetting(SETTING_MOOD_AFTER_CLOSED, day.toString())
        repository.setSetting(SETTING_MOOD_ONLY, day.toString())
        repository.deleteMoodDay(day)
        assertTrue(dao.allMoods().isEmpty())
        assertEquals("", dao.setting(SETTING_MOOD_AFTER_CLOSED))
        assertEquals("", dao.setting(SETTING_MOOD_ONLY))
    }

    @Test
    fun aFirstCheckInAfterEverythingIsDoneIsMarkedAsTheOnlyOne() = runTest {
        // The usual morning check-in isn't marked.
        repository.setMood(day.minusDays(1), 3, null)
        assertNull(dao.setting(SETTING_MOOD_ONLY))
        // The first check-in of the day, with everything already done: the day's only mood.
        repository.setMood(day, 4, null, afterAllDone = true)
        assertEquals(day.toString(), dao.setting(SETTING_MOOD_ONLY))
        assertFalse(MoodInsights.asksLater(dao.mood(day.toEpochDay())!!.toCore(), allDone = true, onlyOne = dao.setting(SETTING_MOOD_ONLY) == day.toString(), closed = false))
        // Changing the face later in the day doesn't mark a day that started in the morning.
        repository.setSetting(SETTING_MOOD_ONLY, "")
        repository.setMood(day, 5, null, afterAllDone = true)
        assertEquals("", dao.setting(SETTING_MOOD_ONLY))
    }

    @Test
    fun aRaiseFromTheMoodPageCanBeUndoneOnce() = runTest {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = "Run", icon = "⭐", points = 10, repeat = Repeat.everyDay(day), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null,
            ),
        )
        val run = dao.allGoals().single()
        val more = MoodInsights.payMore(run.points)
        assertEquals(15, more)
        assertTrue(repository.raiseGoalPoints(run.id, more))
        assertEquals(15, dao.goal(run.id)!!.points)
        val standing = MoodRaises.standing(MoodRaises.decode(dao.setting(MoodRaises.SETTING)), dao.allGoals().map { it.toCore() })
        assertEquals(listOf(MoodRaise(run.id, 10, 15)), standing)
        // Undo puts the old points back, once.
        assertTrue(repository.undoRaise(run.id))
        assertEquals(10, dao.goal(run.id)!!.points)
        assertFalse(repository.undoRaise(run.id))
        assertEquals(10, dao.goal(run.id)!!.points)

        // Points changed somewhere else after a raise: Undo leaves them alone.
        assertTrue(repository.raiseGoalPoints(run.id, 15))
        repository.setGoalPoints(run.id, 12)
        assertTrue(MoodRaises.standing(MoodRaises.decode(dao.setting(MoodRaises.SETTING)), dao.allGoals().map { it.toCore() }).isEmpty())
        assertFalse(repository.undoRaise(run.id))
        assertEquals(12, dao.goal(run.id)!!.points)
        assertFalse(repository.raiseGoalPoints(999, 5))
    }

    @Test
    fun theMoodReminderIsOffUntilSetAndStopsOnceTheMoodIsGiven() = runTest {
        fun at(d: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(d, LocalTime(hour, minute))
        repository.setSetting(Feature.MOOD.key, Feature.MOOD.onValue)
        // Off until a time is picked.
        assertTrue(moodReminders(dao, at(day, 0), at(day.plusDays(2), 0)).isEmpty())
        repository.setSetting(MoodReminder.SETTING, MoodReminder.encode(LocalTime(9, 0)))
        assertEquals(listOf(at(day, 9), at(day.plusDays(1), 9)), moodReminders(dao, at(day, 0), at(day.plusDays(2), 0)).map { it.at })
        // That day's mood is in: its reminder isn't sent; tomorrow's still is.
        repository.setMood(day, 3, null)
        assertEquals(listOf(at(day.plusDays(1), 9)), moodReminders(dao, at(day, 0), at(day.plusDays(2), 0)).map { it.at })
        assertTrue(moodReminders(dao, at(day, 9), at(day, 9, 1)).isEmpty())
        // With the mood check-in turned off, nothing comes.
        repository.setSetting(Feature.MOOD.key, "0")
        assertTrue(moodReminders(dao, at(day, 0), at(day.plusDays(2), 0)).isEmpty())
        // Turned off again.
        repository.setSetting(Feature.MOOD.key, Feature.MOOD.onValue)
        repository.setSetting(MoodReminder.SETTING, MoodReminder.encode(null))
        assertTrue(moodReminders(dao, at(day, 0), at(day.plusDays(2), 0)).isEmpty())
    }

    @Test
    fun bothMoodsGoInTheBackup() = runTest {
        repository.setMood(day, 2, null)
        repository.setMoodAfter(day, 5)
        repository.setMoodNote(day, "a good evening", after = true)
        repository.toggleMoodFactor(day, "Friends and family")
        repository.setMood(day.minusDays(1), 3, null)

        val backup = Backup.decode(repository.exportBackup())
        assertEquals(Backup.FORMAT, backup.format)
        val moods = backup.moods.sortedBy { it.day }
        assertEquals(listOf(3, 2), moods.map { it.score })
        assertEquals(listOf(null, 5), moods.map { it.after })
        assertEquals(listOf(null, "a good evening"), moods.map { it.afterNote })
        assertEquals(listOf(emptyList(), listOf("Friends and family")), moods.map { it.factors })

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
