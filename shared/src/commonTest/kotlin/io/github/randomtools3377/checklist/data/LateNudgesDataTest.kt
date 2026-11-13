package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Alerts
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

    /** Tomorrow, so nothing depends on the time the test runs at. */
    private val day get() = nowLocal().date.plusDays(1)

    @AfterTest
    fun close() = db.close()

    private suspend fun habit(name: String = "Reading", private: Boolean = false, reminder: Reminder? = null, minutes: Int? = null, why: String? = null): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date.minusDays(1)), target = 1, bonus = 0,
                minutes = minutes, at = LocalTime(9, 0), finishBy = null, finishDay = null, reminder = reminder, private = private, why = why,
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
    fun whyItsGoodUsesTheGoalsOwnReasonAndNeverWhatItPays() = runTest {
        val id = habit(minutes = 20, why = "  so I read\n more  ")
        val goal = dao.activeCoreGoals().single { it.id == id }
        // Saved tidied, in the user's own words.
        assertEquals("so I read more", goal.why)
        // The switch for this kind keeps its stored name, so nobody's settings change.
        repository.setSetting(Nudges.TONES, "GAIN")
        assertEquals(listOf(NudgeTone.GAIN), Nudges.tones(dao.setting(Nudges.TONES)))
        repository.setSetting(SETTING_POINTS_NAME, PointsName.encode(PointsName("coin", "coins", "")))
        // A starred reward out of reach is never named either.
        repository.saveReward(RewardDraft(id = 0, name = "Cinema", icon = "🎬", price = 50))
        repository.setSetting(SavingFor.SETTING, dao.activeRewards().single().id.toString())
        // A nudge for today: the writer's own day.
        val later = nudges().first()
        val alert = later.copy(day = nowLocal().date)
        val today = nudgeWriter(dao).words(goal, alert)
        assertEquals("📖 Reading · 30 min late", today.title)
        assertEquals(NudgeTone.GAIN, today.tone)
        assertTrue("so I read more." in today.text, today.text)
        assertFalse("coin" in today.text || "5" in today.text || "Cinema" in today.text || "closer" in today.text, today.text)
        // A nudge written ahead for a later day (the iPhone's) names only the goal and its reason.
        val ahead = nudgeWriter(dao).words(goal, later)
        assertEquals("📖 Reading · 30 min late", ahead.title)
        assertTrue("so I read more." in ahead.text, ahead.text)
        assertFalse("streak" in ahead.text || "coin" in ahead.text || "Cinema" in ahead.text, ahead.text)

        // No reason and no streak: an encouraging one instead, still without what it pays.
        val plainId = habit("Stretch")
        val plain = dao.activeCoreGoals().single { it.id == plainId }
        val words = nudgeWriter(dao).words(plain, alert.copy(goalId = plainId))
        assertEquals(NudgeTone.GAIN, words.tone)
        assertTrue("Stretch" in words.text, words.text)
        assertFalse("coin" in words.text || "5" in words.text || "streak" in words.text, words.text)
    }

    @Test
    fun aLockedPrivateGoalsNudgeNamesNothing() = runTest {
        val id = habit("Secret plan", private = true, why = "so the chores get done")
        val goal = dao.activeCoreGoals().single { it.id == id }
        val alert = nudges().first()
        assertEquals("📖 Secret plan · 30 min late", nudgeWriter(dao).words(goal, alert).title)
        repository.setSetting(LOCK_PRIVATE, "1")
        val writer = nudgeWriter(dao)
        assertTrue(writer.hidden(goal))
        // Every kind of nudge, Why it's good included: nothing named, its reason neither.
        for (nth in 1..8) {
            val words = writer.words(goal, alert.copy(nth = nth))
            assertEquals("🔒 A private goal", words.title)
            assertFalse("Secret" in words.text || "5" in words.text || "chores" in words.text, words.text)
        }
    }
}
