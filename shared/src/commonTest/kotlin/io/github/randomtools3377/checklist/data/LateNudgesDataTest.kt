package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Alerts
import io.github.randomtools3377.checklist.core.Book
import io.github.randomtools3377.checklist.core.NudgePlan
import io.github.randomtools3377.checklist.core.NudgeTone
import io.github.randomtools3377.checklist.core.Nudges
import io.github.randomtools3377.checklist.core.PointsName
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Rules
import io.github.randomtools3377.checklist.core.SavingFor
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Late nudges read from the database: the switches, "Stop for today", and the words with real numbers. */
class LateNudgesDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val book = Book(RoomStore(db))

    /** Tomorrow, so nothing depends on the time the test runs at. */
    private val day get() = nowLocal().date.plusDays(1)

    @AfterTest
    fun close() = db.close()

    private suspend fun habit(name: String = "Reading", private: Boolean = false, reminder: Reminder? = null, minutes: Int? = null): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date.minusDays(1)), target = 1, bonus = 0,
                minutes = minutes, at = LocalTime(9, 0), finishBy = null, finishDay = null, reminder = reminder, private = private,
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    private suspend fun nudges(): List<Alert> = Alerts.between(
        dao.activeCoreGoals(), LocalDateTime(day, LocalTime(8, 0)), LocalDateTime(day, LocalTime(12, 0)), dayEndHour = 4, nudges = nudgePlan(dao),
    ).filter { it.kind == AlertKind.NUDGE }

    @Test
    fun nudgesAreOnToStartAndTheSettingsSwitchTurnsThemAllOff() = runTest {
        habit()
        assertEquals(NudgePlan(on = true, lateAfter = 30, every = 30, most = 12), nudgePlan(dao))
        assertEquals(listOf(LocalTime(9, 30), LocalTime(10, 0)), nudges().take(2).map { it.at.time })
        repository.setSetting(Nudges.SETTING, "0")
        assertFalse(nudgePlan(dao).on)
        assertTrue(nudges().isEmpty())
        repository.setSetting(Nudges.SETTING, "1")
        assertEquals(5, nudges().size)
    }

    @Test
    fun theNumbersComeFromPointsAndRules() = runTest {
        habit()
        repository.setSetting(Rules.SETTING, Rules.encode(Rules(nudgeLateMinutes = 10, nudgeEveryMinutes = 60, nudgeMost = 2)))
        assertEquals(listOf(LocalTime(9, 10), LocalTime(10, 10)), nudges().map { it.at.time })
    }

    @Test
    fun theGoalsOwnSwitchIsSavedWithIt() = runTest {
        val id = habit(reminder = Reminder(nudge = false))
        assertEquals(false, dao.activeCoreGoals().single { it.id == id }.reminder?.nudge)
        assertTrue(nudges().isEmpty())
        // A goal saved without a word about reminders is nudged.
        habit("Stretch")
        assertEquals(setOf(dao.activeGoals().single { it.name == "Stretch" }.id), nudges().map { it.goalId }.toSet())
    }

    @Test
    fun stopForTodayIsKeptForThatDayAndThatGoalOnly() = runTest {
        val reading = habit()
        val stretch = habit("Stretch")
        stopNudges(dao, reading, day)
        assertEquals(setOf(stretch), nudges().map { it.goalId }.toSet())
        assertTrue(nudgePlan(dao).stoppedOn(reading, day))
        stopNudges(dao, stretch, day)
        assertTrue(nudges().isEmpty())
        assertEquals("$day|$reading,$stretch", dao.setting(Nudges.STOPPED))
        // The next day's stop replaces the list: the day before is over.
        stopNudges(dao, stretch, day.plusDays(1))
        assertEquals("${day.plusDays(1)}|$stretch", dao.setting(Nudges.STOPPED))
        assertEquals(setOf(reading, stretch), nudges().map { it.goalId }.toSet())
    }

    @Test
    fun theSettingsGoInTheBackup() = runTest {
        repository.setSetting(Nudges.SETTING, "0")
        repository.setSetting(Nudges.TONES, "PLAYFUL")
        assertFalse(Nudges.SETTING in PHONE_ONLY_SETTINGS || Nudges.TONES in PHONE_ONLY_SETTINGS || Nudges.STOPPED in PHONE_ONLY_SETTINGS)
    }

    @Test
    fun theWordsUseWhatIsReallyThere() = runTest {
        val id = habit(minutes = 20)
        val goal = dao.activeCoreGoals().single { it.id == id }
        repository.setSetting(Nudges.TONES, Nudges.encodeTones(setOf(NudgeTone.GAIN)))
        repository.setSetting(SETTING_POINTS_NAME, PointsName.encode(PointsName("coin", "coins", "")))
        // A nudge for today: the writer's own day.
        val later = nudges().first()
        val alert = later.copy(day = nowLocal().date)
        // No streak yet and nothing starred: the coins alone.
        val plain = nudgeWriter(dao, book).words(goal, alert)
        assertEquals("📖 Reading · 30 min late", plain.title)
        assertEquals(NudgeTone.GAIN, plain.tone)
        assertTrue("5 coins" in plain.text, plain.text)
        assertFalse("streak" in plain.text || "closer" in plain.text, plain.text)

        // A starred reward out of reach is named with what the tick brings.
        repository.saveReward(RewardDraft(id = 0, name = "Cinema", icon = "🎬", price = 50))
        repository.setSetting(SavingFor.SETTING, dao.activeRewards().single().id.toString())
        val closer = nudgeWriter(dao, book).words(goal, alert)
        assertTrue("That's 5 coins closer to 🎬 Cinema." in closer.text, closer.text)
        // A nudge written ahead for a later day can't know that day's balance: the coins alone.
        val ahead = nudgeWriter(dao, book).words(goal, later)
        assertTrue("5 coins" in ahead.text, ahead.text)
        assertFalse("Cinema" in ahead.text, ahead.text)

        // With the shop behind the lock, no reward is named.
        repository.setSetting(LOCK_SHOP, "1")
        assertFalse("Cinema" in nudgeWriter(dao, book).words(goal, alert).text)
    }

    @Test
    fun aLockedPrivateGoalsNudgeNamesNothing() = runTest {
        val id = habit("Secret plan", private = true)
        val goal = dao.activeCoreGoals().single { it.id == id }
        val alert = nudges().first()
        assertEquals("📖 Secret plan · 30 min late", nudgeWriter(dao, book).words(goal, alert).title)
        repository.setSetting(LOCK_PRIVATE, "1")
        val writer = nudgeWriter(dao, book)
        assertTrue(writer.hidden(goal))
        val words = writer.words(goal, alert)
        assertEquals("🔒 A private goal", words.title)
        assertFalse("Secret" in words.text || "5" in words.text, words.text)
    }
}
