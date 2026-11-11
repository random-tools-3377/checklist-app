package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionTest {
    private val day = LocalDate(2026, 11, 11)
    private val water = Goal(id = 1, kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 5, repeat = null, minutes = 2)
    private val run = Goal(id = 2, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 20, repeat = null, minutes = 30)
    private val read = Goal(id = 3, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 10, repeat = null)

    private fun facts(
        left: List<Goal> = listOf(run, water),
        done: Int = 0,
        due: Int = 2,
        missed: Int = 0,
        streak: Int = 0,
        mood: Int? = null,
        ask: Boolean = false,
        away: Int = 0,
        level: Int? = null,
        now: LocalTime = LocalTime(10, 0),
    ) = CompanionFacts(day, now, left, done, due, missed, streak, mood, ask, away, level)

    @Test
    fun theEasiestGoalIsTheQuickest() {
        assertEquals(water, Checkins.easiest(listOf(run, water, read)))
        // No length counts as about 15 minutes, so a 2-minute goal still comes first.
        assertEquals(read, Checkins.easiest(listOf(run, read)))
        assertNull(Checkins.easiest(emptyList()))
    }

    @Test
    fun afterASlipItSuggestsOneSmallThing() {
        val say = Checkins.say(facts(missed = 3))
        assertEquals(Expression.WORRIED, say.expression)
        assertEquals(water, say.suggestion)
        assertTrue("Water plants" in say.line, say.line)
        // Once something is done today, the slip is behind us.
        assertNotEquals(Expression.WORRIED, Checkins.say(facts(missed = 3, done = 1, due = 3)).expression)
    }

    @Test
    fun itWelcomesTheUserBack() {
        val say = Checkins.say(facts(away = 4, missed = 2))
        assertEquals(Expression.HAPPY, say.expression)
        assertTrue(say.line.contains("back", ignoreCase = true), say.line)
        assertEquals(water, say.suggestion)
    }

    @Test
    fun itAsksHowTheUserFeelsAndAnswers() {
        val asking = Checkins.say(facts(ask = true))
        assertTrue(asking.askMood)
        assertNull(asking.suggestion)

        // A hard day: gentle, with one small thing if they'd like, never the late-night nudge.
        val hard = Checkins.say(facts(mood = 1, missed = 2, now = LocalTime(21, 0)))
        assertEquals(Expression.CALM, hard.expression)
        assertEquals(water, hard.suggestion)
        assertFalse(hard.askMood)

        val good = Checkins.say(facts(mood = 5))
        assertEquals(Expression.HAPPY, good.expression)
        assertTrue("2" in good.line, good.line)
    }

    @Test
    fun itCheersWhenEverythingIsDone() {
        val say = Checkins.say(facts(left = emptyList(), done = 2, due = 2, ask = true))
        assertEquals(Expression.PROUD, say.expression)
        assertTrue(say.jumps)
        assertFalse(say.askMood, "nothing left to ask about")
        assertTrue("hard day" in Checkins.say(facts(left = emptyList(), done = 2, due = 2, mood = 2)).line)
    }

    @Test
    fun itJumpsForJoyAtANewLevel() {
        val say = Checkins.say(facts(level = 7, missed = 2))
        assertTrue(say.levelUp)
        assertTrue(say.jumps)
        assertTrue("7" in say.line)
    }

    @Test
    fun lateInTheDayItLooksTired() {
        val say = Checkins.say(facts(now = LocalTime(19, 30), due = 4, done = 1, left = listOf(run, water, read)))
        assertEquals(Expression.TIRED, say.expression)
        assertEquals(water, say.suggestion)
    }

    @Test
    fun theWordsStayTheSameAllDay() {
        assertEquals(Checkins.say(facts(missed = 1)).line, Checkins.say(facts(missed = 1, now = LocalTime(11, 0))).line)
    }

    @Test
    fun daysAwayCountsDaysWithSomethingDueAndNothingDone() {
        fun record(daysBack: Int, due: Int, done: Int) = DayRecord(day.minusDays(daysBack), due, done, 0, 0, 0)
        val records = listOf(record(5, 2, 1), record(4, 2, 0), record(3, 0, 0), record(2, 2, 0), record(1, 3, 0), record(0, 2, 0))
        // Yesterday, 2 days back and 4 days back; the day with nothing due is passed over.
        assertEquals(3, Checkins.daysAway(records, day))
        // Never done anything: a new user, not one coming back.
        assertEquals(0, Checkins.daysAway(records.drop(1), day))
        assertEquals(0, Checkins.daysAway(listOf(record(1, 2, 1)), day))
        assertEquals(3, Checkins.leftYesterday(records, day))
        assertEquals(1, Checkins.leftYesterday(listOf(DayRecord(day.minusDays(1), 3, 1, 0, 1, 0)), day))
        assertEquals(0, Checkins.leftYesterday(emptyList(), day))
    }

    @Test
    fun eachFaceIsDrawnDifferently() {
        val outfit = Outfit(emptyList(), null)
        val faces = Expression.entries.map { Figure.svg(Figure.marks(Look(), outfit, it)) }
        assertEquals(faces.size, faces.toSet().size)
        assertEquals(Figure.marks(Look(), outfit), Figure.marks(Look(), outfit, Expression.CALM))
        // Without hair, the brows still show the feeling.
        val bald = Look(hair = HairStyle.NONE)
        assertNotEquals(Figure.marks(bald, outfit, Expression.WORRIED), Figure.marks(bald, outfit, Expression.CALM))
    }
}
