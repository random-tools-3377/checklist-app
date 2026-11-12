package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The review's mood summary, the journal's filters, the single late check-in, the reminder, and the highlight. */
class MoodJournalTest {
    private val monday = LocalDate(2026, 11, 9)

    @AfterTest
    fun putTheUsualNamesBack() {
        PointsName.current = PointsName.DEFAULT
    }

    @Test
    fun aWeekIsSummedUpNextToTheWeekBefore() {
        val moods = listOf(
            // The week before: 2 and 3.
            Mood(monday.minusDays(7), 2),
            Mood(monday.minusDays(3), 3, factors = listOf("Work")),
            // This week: 4, then 2 that ended as 5 (the later one counts), then 3.
            Mood(monday, 4, factors = listOf("Slept well", "Work")),
            Mood(monday.plusDays(1), 2, after = 5, factors = listOf("slept well")),
            Mood(monday.plusDays(3), 3, factors = listOf("Outdoors")),
            // Next week isn't part of it.
            Mood(monday.plusDays(7), 1),
        )
        val (from, to) = Review.range(monday.plusDays(2), Period.WEEKLY)
        val (beforeFrom, beforeTo) = Review.range(from.minusDays(1), Period.WEEKLY)
        val summary = MoodSummaries.of(moods, from, to, beforeFrom, beforeTo)

        assertEquals(3, summary.days)
        assertEquals(4.0, summary.average)
        assertEquals(2, summary.beforeDays)
        assertEquals(2.5, summary.before)
        assertEquals(1.5, summary.change)
        assertEquals(listOf("Slept well" to 2, "Outdoors" to 1, "Work" to 1), summary.top)
        assertEquals("Average mood 4.0 🙂 over 3 days", MoodSummaries.averageLine(summary))
        assertEquals("Higher than the week before (2.5).", MoodSummaries.compareLine(summary, MoodSummaries.stretch(Period.WEEKLY)))
        assertEquals("Noted most: Slept well (2 days) · Outdoors (1 day) · Work (1 day)", MoodSummaries.topLine(summary))
    }

    @Test
    fun theComparisonSaysSoWhenThereIsNothingBeforeOrNothingHere() {
        val only = MoodSummaries.of(listOf(Mood(monday, 3)), monday, monday.plusDays(6), monday.minusDays(7), monday.minusDays(1))
        assertEquals("Average mood 3.0 😐 over 1 day", MoodSummaries.averageLine(only))
        assertEquals("No moods from the month before to compare with.", MoodSummaries.compareLine(only, "month"))
        assertNull(MoodSummaries.topLine(only))

        val none = MoodSummaries.of(emptyList(), monday, monday.plusDays(6), monday.minusDays(7), monday.minusDays(1))
        assertNull(none.average)
        assertNull(MoodSummaries.averageLine(none))
        assertNull(MoodSummaries.compareLine(none, "week"))

        fun line(now: Int, before: Int) = MoodSummaries.compareLine(
            MoodSummaries.of(listOf(Mood(monday, now), Mood(monday.plusDays(1), 3), Mood(monday.minusDays(1), before), Mood(monday.minusDays(2), 3)), monday, monday.plusDays(6), monday.minusDays(7), monday.minusDays(1)),
            "year",
        )
        assertEquals("About the same as the year before (3.0).", line(3, 3))
        assertEquals("A little higher than the year before (3.0).", line(4, 3))
        assertEquals("A little lower than the year before (3.5).", line(3, 4))
        assertEquals("Lower than the year before (4.0).", line(1, 5))
    }

    @Test
    fun theYearsMonthlyMoodIsTheLaterOneWhenGiven() {
        val (from, to) = Review.range(LocalDate(2026, 6, 1), Period.YEARLY)
        val summary = Review.summarize(from = from, to = to, today = LocalDate(2026, 3, 1), goals = emptyList(), ticks = emptyList(), lapses = emptyList(), entries = emptyList(), gaps = emptyList())
        val moods = listOf(Mood(LocalDate(2026, 1, 5), 2, after = 4), Mood(LocalDate(2026, 1, 6), 2))
        assertEquals(3.0, YearReview.months(summary, moods, 2026, today = LocalDate(2026, 3, 1))[0].mood)
    }

    @Test
    fun theJournalShowsOnlyOneChipsDaysAndSearchesTheNotes() {
        val days = listOf(
            Mood(monday, 4, note = "Long walk by the river", factors = listOf("Outdoors")),
            Mood(monday.plusDays(1), 2, afterNote = "Tired after work", factors = listOf("Work", "Slept badly")),
            Mood(monday.plusDays(2), 3),
        )
        assertEquals(listOf(monday.plusDays(2), monday.plusDays(1), monday), MoodJournal.filter(days).map { it.day })
        assertEquals(listOf(monday.plusDays(1)), MoodJournal.filter(days, factor = "work").map { it.day })
        assertEquals(listOf(monday), MoodJournal.filter(days, query = "WALK river").map { it.day })
        // Both notes are searched, and the things noted.
        assertEquals(listOf(monday.plusDays(1)), MoodJournal.filter(days, query = "tired").map { it.day })
        assertEquals(listOf(monday.plusDays(1)), MoodJournal.filter(days, query = "slept").map { it.day })
        assertTrue(MoodJournal.filter(days, factor = "Outdoors", query = "tired").isEmpty())
        // A stretch from the review.
        assertEquals(listOf(monday.plusDays(1), monday), MoodJournal.filter(days, from = monday, to = monday.plusDays(1)).map { it.day })
        assertEquals(listOf("Outdoors" to 1, "Slept badly" to 1, "Work" to 1), MoodSummaries.used(days))
    }

    @Test
    fun theFirstAnswerIsOnlyCalledMorningWhenItWas() {
        assertEquals("Morning", MoodJournal.firstLabel(monday, LocalDateTime(monday, LocalTime(8, 30))))
        assertEquals("First check-in", MoodJournal.firstLabel(monday, LocalDateTime(monday, LocalTime(18, 0))))
        // Filled in on a later day, or from before times were kept.
        assertEquals("First check-in", MoodJournal.firstLabel(monday, LocalDateTime(monday.plusDays(2), LocalTime(8, 0))))
        assertEquals("First check-in", MoodJournal.firstLabel(monday, null))
        assertTrue(MoodJournal.canAdd(monday, monday))
        assertTrue(MoodJournal.canAdd(monday.minusDays(40), monday))
        assertFalse(MoodJournal.canAdd(monday.plusDays(1), monday))
    }

    @Test
    fun aFirstCheckInAfterEverythingIsDoneIsTheDaysOnlyOne() {
        val mood = Mood(monday, 3)
        // The usual day: asked once everything is done.
        assertFalse(MoodInsights.asksLater(mood, allDone = false, onlyOne = false, closed = false))
        assertTrue(MoodInsights.asksLater(mood, allDone = true, onlyOne = false, closed = false))
        // The first check-in came after everything was done: not asked again.
        assertFalse(MoodInsights.asksLater(mood, allDone = true, onlyOne = true, closed = false))
        // A later mood added in the journal still shows its answer.
        assertTrue(MoodInsights.asksLater(mood.copy(after = 4), allDone = true, onlyOne = true, closed = false))
        assertFalse(MoodInsights.asksLater(mood.copy(after = 4), allDone = true, onlyOne = false, closed = true))
        assertFalse(MoodInsights.asksLater(null, allDone = true, onlyOne = false, closed = false))
        // A first check-in later in the day isn't called "this morning".
        assertEquals("Same as this morning.", MoodInsights.answer(3, 3))
        assertEquals("Same as earlier today.", MoodInsights.answer(3, 3, inMorning = false))
    }

    @Test
    fun theMoodReminderComesOnceADayAndNotOnceTheMoodIsGiven() {
        fun at(day: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime(day, LocalTime(hour, minute))
        val nine = LocalTime(9, 0)
        val three = MoodReminder.between(at(monday, 0), at(monday.plusDays(3), 0), nine, dayEndHour = 0)
        assertEquals(listOf(monday, monday.plusDays(1), monday.plusDays(2)), three.map { it.day })
        assertEquals(at(monday, 9), three.first().at)
        // The minute it is due in, and not the one after.
        assertEquals(1, MoodReminder.between(at(monday, 9), at(monday, 9, 1), nine, 0).size)
        assertTrue(MoodReminder.between(at(monday, 9, 1), at(monday, 9, 2), nine, 0).isEmpty())
        // Not for a day whose mood is in.
        assertEquals(
            listOf(monday.plusDays(1), monday.plusDays(2)),
            MoodReminder.between(at(monday, 0), at(monday.plusDays(3), 0), nine, 0, given = setOf(monday)).map { it.day },
        )
        // With the day ending at 4 am, 1 am belongs to the day before.
        val late = MoodReminder.between(at(monday.plusDays(1), 0), at(monday.plusDays(1), 2), LocalTime(1, 0), dayEndHour = 4)
        assertEquals(monday, late.single().day)
        assertTrue(MoodReminder.between(at(monday.plusDays(1), 0), at(monday.plusDays(1), 2), LocalTime(1, 0), 4, given = setOf(monday)).isEmpty())
        // It only asks; nothing of the journal is in its words.
        assertEquals("How are you feeling?", MoodReminder.TITLE)
        assertEquals(nine, MoodReminder.decode(MoodReminder.encode(nine)))
        assertNull(MoodReminder.decode(MoodReminder.encode(null)))
    }

    @Test
    fun aRewardToLookForwardToIsOneInReachOrTheClosest() {
        val coffee = Reward(id = 1, name = "Coffee out", icon = "☕", price = 20)
        val film = Reward(id = 2, name = "Film night", icon = "🎬", price = 60, position = 1)
        val gone = Reward(id = 3, name = "Old", icon = "🗑", price = 1, archived = true)
        assertEquals(coffee, MoodInsights.lookForward(listOf(film, coffee, gone), balance = 100))
        assertEquals(coffee, MoodInsights.lookForward(listOf(film, coffee, gone), balance = 5))
        assertEquals(film, MoodInsights.lookForward(listOf(film), balance = -10))
        assertNull(MoodInsights.lookForward(listOf(gone), balance = 100))
        assertEquals(
            "🎁 Something to look forward to: ☕ Coffee out. You can already get it, whenever you like.",
            MoodInsights.lookForwardLine(coffee, 20),
        )
        PointsName.current = PointsName(one = "coin", many = "coins")
        assertEquals("🎁 Something to look forward to: ☕ Coffee out, 15 coins away. No rush.", MoodInsights.lookForwardLine(coffee, 5))
        assertEquals("Now pays 15 coins", MoodInsights.nowPays(15))
    }

    @Test
    fun aRaiseIsRememberedUntilThePointsChangeElsewhere() {
        val run = Goal(id = 7, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 10, repeat = null)
        val raises = MoodRaises.raised(emptyList(), run, 15)
        assertEquals(listOf(MoodRaise(7, 10, 15)), MoodRaises.decode(MoodRaises.encode(raises)))
        // While it still pays 15, the raise stands; edited to something else, it is forgotten.
        assertEquals(raises, MoodRaises.standing(raises, listOf(run.copy(points = 15))))
        assertTrue(MoodRaises.standing(raises, listOf(run.copy(points = 12))).isEmpty())
        assertTrue(MoodRaises.standing(raises, listOf(run.copy(points = 15, archived = true))).isEmpty())
        assertTrue(MoodRaises.without(raises, 7).isEmpty())
        assertTrue(MoodRaises.decode("x,1:2,::,").isEmpty())
        assertTrue(MoodRaises.decode(null).isEmpty())
    }

    @Test
    fun aFindingOnlyFromDaysSomethingWasNotedIsMarked() {
        val today = LocalDate(2026, 11, 11)
        fun ago(days: Int) = today.minusDays(days)
        // "Slept well" on three good days. "Work" on three low days, and three more low days with nothing noted at all.
        val moods = (1..3).map { Mood(ago(it), 5, factors = listOf("Slept well")) } +
            (4..6).map { Mood(ago(it), 2, factors = listOf("Work")) } +
            (7..9).map { Mood(ago(it), 2) }
        val found = MoodInsights.find(emptyList(), emptyList(), moods, today, Rules())
        assertEquals(9, found.moodDays)
        assertEquals(6, found.notedDays)
        val slept = found.betterFactors.single { it.factor == "Slept well" }
        // Its other days include the three with nothing noted, so it isn't only from noted days.
        assertEquals(6, slept.otherDays)
        assertEquals(3, slept.otherNoted)
        assertFalse(slept.onlyNotedDays)
        assertEquals(
            "You noted something on 6 of 9 days with a mood. ★ marks what is compared only among those days.",
            MoodInsights.notedDaysLine(found),
        )

        // Without the empty days, everything it is set against had something noted.
        val noted = MoodInsights.find(emptyList(), emptyList(), moods.take(6), today, Rules())
        assertTrue(noted.betterFactors.single { it.factor == "Slept well" }.onlyNotedDays)
        // The comparison itself is as it was.
        assertEquals(3.0, noted.betterFactors.single().difference)
        assertEquals(3.0, slept.difference)
    }

    @Test
    fun theCharactersCardNamesTheGoalWithoutSayingItCausesAnything() {
        val walk = Goal(id = 1, kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 5, repeat = null)
        assertEquals("Days you do 🚶 Walk tend to go with a better mood.", MoodInsights.alsoLine(walk))
    }
}
