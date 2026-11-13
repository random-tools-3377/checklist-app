package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** What is at stake, as notifications say it: reminders, the morning plan, the evening check and the note after a tick. */
class AtStakeTest {
    private val day = LocalDate(2026, 11, 11)
    private val percent90 = StreakSettings(StreakTarget.PERCENT, 90)
    private val count2 = StreakSettings(StreakTarget.COUNT, 2)

    private fun goal(id: Long = 1, target: Int = 1, repeat: Repeat = Repeat.everyDay(LocalDate(2026, 1, 1))) =
        Goal(id = id, kind = GoalKind.HABIT, name = "G$id", icon = "⭐", points = 5, repeat = repeat, target = target)

    private fun row(goal: Goal = goal(), count: Int = 0, skips: Int = 0) = HabitRow(goal, count, goal.repeat!!.window(day)!!, skips)

    private fun record(due: Int, done: Int, skips: Int = 0) = DayRecord(day, due, done, misses = 0, skips = skips, overLimit = 0)

    @Test
    fun aReminderSaysWhenItsGoalMakesTodayCountForTheStreak() {
        // 2 of 3 done at 90%: the last one makes the day count.
        val s = DayStanding(record(3, 2), percent90, streak = 12, left = 1)
        assertEquals("This one makes today count for your streak: day 13", AtStake.reminder(row(), s))
        // No streak running: the same words without a day number.
        assertEquals("This one makes today count for your streak", AtStake.reminder(row(), s.copy(streak = 0)))
        // A target of 2 goals with 1 done and 3 still open: any one of them makes today count.
        assertEquals("This one makes today count for your streak: day 4", AtStake.reminder(row(), DayStanding(record(4, 1), count2, streak = 3, left = 3)))
        // Two more needed: this one alone doesn't, so nothing is promised.
        assertNull(AtStake.reminder(row(), DayStanding(record(4, 0), count2, streak = 3, left = 4)))
    }

    @Test
    fun aReminderSaysWhenItIsTheLastOneForToday() {
        // Today already counts (2 of 3 with a target of 2 goals); one goal is left.
        val s = DayStanding(record(3, 2), count2, streak = 5, left = 1)
        assertEquals("Last one for today", AtStake.reminder(row(), s))
        assertNull(AtStake.reminder(row(), s.copy(left = 2)))
        // The second of three glasses isn't the last one; the third is.
        val glasses = goal(target = 3)
        assertNull(AtStake.reminder(row(glasses, count = 1), s))
        assertEquals("Last one for today", AtStake.reminder(row(glasses, count = 2), s))
        // Done or skipped: nothing to say.
        assertNull(AtStake.reminder(row(count = 1), s))
        assertNull(AtStake.reminder(row(skips = 1), s))
    }

    @Test
    fun aGoalCountedOverAWeekGetsNoPhrase() {
        val weekly = goal(repeat = Repeat.timesPer(Period.WEEKLY, LocalDate(2026, 1, 1)), target = 3)
        val s = DayStanding(record(1, 0), percent90, streak = 5, left = 1)
        assertNull(AtStake.reminder(row(weekly, count = 2), s))
    }

    @Test
    fun noStreakIsMentionedWhileTheReviewIsLocked() {
        val s = DayStanding(record(3, 2), percent90, streak = 12, left = 1, showStreak = false)
        // The count of what is left is not a streak.
        assertEquals("Last one for today", AtStake.reminder(row(), s))
        assertNull(AtStake.morning(s))
        assertNull(AtStake.evening(s))
        assertEquals("That's everything for today", AtStake.afterTick(finished = true, countsToday = true, s = DayStanding(record(3, 3), percent90, 12, left = 0, showStreak = false)))
    }

    @Test
    fun theMorningPlanSaysWhichDayOfTheStreakTodayIs() {
        assertEquals("Day 13 of your streak", AtStake.morning(DayStanding(record(3, 0), percent90, streak = 12, left = 3)))
        // No streak running, or nothing due today: nothing is said, and a broken streak is never mentioned.
        assertNull(AtStake.morning(DayStanding(record(3, 0), percent90, streak = 0, left = 3)))
        assertNull(AtStake.morning(DayStanding(record(0, 0), percent90, streak = 12, left = 0)))
        assertNull(AtStake.morning(DayStanding(null, percent90, streak = 12)))
    }

    @Test
    fun theEveningCheckSaysTheClosestWinAsTodayDoes() {
        assertEquals("1 more keeps your 12-day streak", AtStake.evening(DayStanding(record(4, 1), count2, streak = 12, left = 3)))
        assertEquals("2 more and today counts for your streak", AtStake.evening(DayStanding(record(4, 0), count2, streak = 0, left = 4)))
        // Today counts already: the perfect day is the next win.
        assertEquals("2 more for a perfect day", AtStake.evening(DayStanding(record(4, 2), count2, streak = 12, left = 2)))
        // It is what Today's own line says.
        assertEquals(NearWins.line(record(4, 1), count2, 12), AtStake.evening(DayStanding(record(4, 1), count2, streak = 12, left = 3)))
        // When every open goal is needed, the streak is what is said, not the perfect day.
        assertEquals("3 more keeps your 12-day streak", AtStake.evening(DayStanding(record(3, 0), percent90, streak = 12, left = 3)))
        assertNull(AtStake.evening(DayStanding(record(0, 0), percent90, streak = 12, left = 0)))
        // A day with a skip is never a perfect day, so that isn't offered.
        assertNull(AtStake.evening(DayStanding(record(3, 2, skips = 1), count2, streak = 12, left = 1)))
    }

    @Test
    fun theNoteAfterATickSaysWhatIsLeft() {
        val after = DayStanding(record(4, 2), count2, streak = 12, left = 2)
        // The tick that reached the target: the streak first, then what is left.
        assertEquals("Today now counts for your streak: day 13 · 2 left today", AtStake.afterTick(finished = true, countsToday = true, s = after))
        // One later: the streak was already reached, so only what is left.
        assertEquals("1 left today", AtStake.afterTick(true, true, DayStanding(record(4, 3), count2, 12, left = 1)))
        assertEquals("That's everything for today", AtStake.afterTick(true, true, DayStanding(record(4, 4), count2, 12, left = 0)))
        // A tick that didn't finish its goal changes nothing for the streak.
        assertEquals("2 left today", AtStake.afterTick(finished = false, countsToday = true, s = after))
        // A goal counted over the week: today's list is as it was, and "everything" isn't claimed for it.
        assertEquals("2 left today", AtStake.afterTick(finished = true, countsToday = false, s = after))
        assertNull(AtStake.afterTick(finished = true, countsToday = false, s = DayStanding(record(0, 0), count2, 12, left = 0)))
    }

    @Test
    fun theAnswerNamesTheGoalAndWhatItPaid() {
        val after = DayStanding(record(3, 1), percent90, streak = 0, left = 2)
        val done = AtStake.answer("💧 Water", paid = 5, finished = true, countsToday = true, progress = null, hidden = false, stakes = after)
        assertEquals("Done: 💧 Water, +5", done.title)
        assertEquals("2 left today", done.text)
        assertEquals("Done: 💧 Water, +5 · 2 left today", done.line)
        // One of several a day: how far along it is, then what is left.
        val glass = AtStake.answer("💧 Water", paid = 2, finished = false, countsToday = true, progress = "3 of 8", hidden = false, stakes = after)
        assertEquals("3 of 8 · 2 left today", glass.text)
        // Nothing paid: no "+0".
        assertEquals("Done: 💧 Water", AtStake.answer("💧 Water", 0, true, true, null, false, after).title)
        // With its switch off (only the edges are lit): just the first part, as before.
        assertEquals(TickAnswer("Done: 💧 Water, +5", null), AtStake.answer("💧 Water", 5, true, true, null, false, stakes = null))
    }

    @Test
    fun aLockedPrivateGoalSaysNothingMore() {
        val after = DayStanding(record(3, 3), percent90, streak = 12, left = 0)
        val answer = AtStake.answer("🔒 A private goal", paid = 5, finished = true, countsToday = true, progress = "1 of 2", hidden = true, stakes = after)
        assertEquals(TickAnswer("Done: 🔒 A private goal, +5", null), answer)
    }

    @Test
    fun noPhraseEverMentionsWhatWentWrong() {
        val banned = Regex("miss|penalt|skip|mood|late|behind|fail|lost", RegexOption.IGNORE_CASE)
        val standings = listOf(
            DayStanding(record(3, 2), percent90, 12, 1), DayStanding(record(4, 1), count2, 0, 3), DayStanding(record(4, 4), count2, 3, 0),
            DayStanding(record(3, 1, skips = 1), count2, 2, 1),
        )
        for (s in standings) {
            listOfNotNull(AtStake.reminder(row(), s), AtStake.morning(s), AtStake.evening(s), AtStake.afterTick(true, true, s)).forEach {
                assertFalse(banned.containsMatchIn(it), it)
            }
        }
    }

    @Test
    fun theWeeklySummaryComparesOnlyWhenItIsUpAndOnlySaysARaceThatWasWon() {
        PointsName.current = PointsName.DEFAULT
        val week = WeeklySummary(due = 20, done = 17, missed = 0, skipped = 0, earned = 90, lost = 0, best = null)
        val worse = week.copy(done = 14)
        assertEquals("17 of 20 done (85%, up from 70%) · +90 points", week.describe(before = worse))
        // Down, or the same: nothing about last week.
        assertEquals("14 of 20 done (70%) · +90 points", worse.describe(before = week))
        assertEquals("17 of 20 done (85%) · +90 points", week.describe(before = week))
        // Nothing was due last week, or this week: nothing to compare.
        assertEquals("17 of 20 done (85%) · +90 points", week.describe(before = week.copy(due = 0, done = 0)))
        assertEquals("Nothing was due · +90 points", week.copy(due = 0, done = 0).describe(before = worse))
        // The race: said when won, and nothing when it wasn't.
        assertEquals("17 of 20 done (85%) · +90 points · 🏁 beat last week by 10", week.describe(race = RaceStanding(90, 80)))
        assertEquals("17 of 20 done (85%) · +90 points", week.describe(race = RaceStanding(90, 95)))
        assertEquals("17 of 20 done (85%) · +90 points", week.describe(race = RaceStanding(90, 90)))
    }

    @Test
    fun theEveningListSaysWhatIsAtStakeFirstAndKeepsToItsLines() {
        val items = (1..8).map { EveningItem(it.toLong(), "G$it", tick = "Done") }
        val list = EveningList(day, items, stake = "1 more keeps your 12-day streak")
        assertEquals(listOf("1 more keeps your 12-day streak", "G1", "G2", "G3", "and 5 more"), list.lines(4))
        assertEquals(listOf("G1", "G2", "G3", "G4", "and 4 more"), list.copy(stake = null).lines(4))
        assertEquals("8 still open today", list.title)
    }
}
