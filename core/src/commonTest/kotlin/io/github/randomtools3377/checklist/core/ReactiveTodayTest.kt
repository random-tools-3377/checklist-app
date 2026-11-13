package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Today reacting to how the day goes: what a tick changed, what is within reach, the morning card, the recap. */
class ReactiveTodayTest {
    private val day = LocalDate(2026, 11, 11)
    private val ninety = StreakSettings()

    private fun goal(id: Long, name: String = "G$id", points: Int = 5, minutes: Int? = null, repeat: Repeat = Repeat.everyDay(day)) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = points, repeat = repeat, minutes = minutes)

    private fun row(id: Long, done: Boolean = false, skipped: Boolean = false, name: String = "G$id", points: Int = 5, minutes: Int? = null) =
        HabitRow(goal(id, name, points, minutes), if (done) 1 else 0, Window(day, day, Period.DAILY), skips = if (skipped) 1 else 0)

    private fun record(due: Int, done: Int, skips: Int = 0, misses: Int = 0, on: LocalDate = day) = DayRecord(on, due, done, misses, skips, 0)

    // What a tick changed

    @Test
    fun aTickSaysHowManyAreLeft() {
        val m = TickMoment(finished = true, paid = 5, onTodaysList = true, leftBefore = 3, countsToday = true, record = record(5, 2))
        assertEquals("2 left today", TickNews.phrase(m, "Water"))
        assertEquals("Water · +5 · 2 left today", TickNews.line("Water", 5, null, TickNews.phrase(m, "Water")))
    }

    @Test
    fun aTickOfOneOfSeveralTimesKeepsTheCount() {
        // 3 of 8 glasses: the goal isn't finished, so as many goals are left as before.
        val m = TickMoment(finished = false, paid = 1, onTodaysList = true, leftBefore = 3, countsToday = true, record = record(5, 2))
        assertEquals("3 left today", TickNews.phrase(m, "Water"))
    }

    @Test
    fun theTickThatReachesTheStreakTargetSaysTheDay() {
        // 10 due, 8 done: the 9th reaches 90%.
        val m = TickMoment(finished = true, paid = 5, onTodaysList = true, leftBefore = 2, countsToday = true, record = record(10, 8), streak = ninety, streakBefore = 12)
        assertEquals("Today now counts for your streak: day 13", TickNews.phrase(m, "Water"))
        // The 10th doesn't say it again: it is the last one, and the day is perfect so far.
        val last = m.copy(leftBefore = 1, record = record(10, 9))
        assertEquals("That was the last one: a perfect day so far", TickNews.phrase(last, "Water"))
    }

    @Test
    fun theLastOneOnADayWithASkipIsNotCalledPerfect() {
        val m = TickMoment(finished = true, paid = 5, onTodaysList = true, leftBefore = 1, countsToday = true, record = record(1, 1, skips = 1).copy(done = 0), streak = StreakSettings(StreakTarget.COUNT, 5), streakBefore = 0)
        // A count target of 5 with 1 due is reached by 1: the streak line comes first.
        assertEquals("Today now counts for your streak: day 1", TickNews.phrase(m, null))
        val counted = m.copy(record = record(2, 1, skips = 1), streak = StreakSettings(StreakTarget.COUNT, 1))
        assertEquals("That was the last one", TickNews.phrase(counted, null))
    }

    @Test
    fun aNewBestRunAndAMilestoneAreSaid() {
        val base = TickMoment(finished = true, paid = 5, onTodaysList = true, leftBefore = 4, countsToday = true, record = record(20, 2))
        assertEquals("New best: 9 in a row", TickNews.phrase(base.copy(run = 9, bestRunBefore = 8), "Stretching"))
        // Not a best: the run only equals it. The 50th time is a milestone.
        assertEquals("That's 50 times for Stretching", TickNews.phrase(base.copy(run = 8, bestRunBefore = 8, timesDone = 50), "Stretching"))
        // A goal that mustn't be named isn't.
        assertEquals("That's 50 times", TickNews.phrase(base.copy(timesDone = 50), null))
        // A run of 2 is no news yet.
        assertEquals("3 left today", TickNews.phrase(base.copy(run = 2, bestRunBefore = 1, timesDone = 2), "Stretching"))
    }

    @Test
    fun aQuestFinishedByTheTickIsSaidWithWhatItPays() {
        val quest = Quest(0, QuestKind.GOAL, "Do ⭐ G1", 1, 5, goalId = 1)
        val early = Quest(1, QuestKind.EARLY, "Tick 2 goals before noon", 2, 5, by = LocalTime(12, 0))
        val base = TickMoment(
            finished = true, paid = 5, onTodaysList = true, leftBefore = 4, countsToday = true, record = record(20, 2), goalId = 1,
            quests = listOf(QuestProgress(quest, 0, false), QuestProgress(early, 1, false)), time = LocalTime(9, 0),
        )
        assertEquals("2 quests done +10", TickNews.phrase(base, "G1"))
        // After noon the early quest isn't finished by it.
        assertEquals("Quest done +5", TickNews.phrase(base.copy(time = LocalTime(13, 0)), "G1"))
        // Another goal, after noon: no quest.
        assertEquals("3 left today", TickNews.phrase(base.copy(goalId = 2, time = LocalTime(13, 0)), "G2"))
        // A quest already complete isn't said again.
        assertEquals("3 left today", TickNews.phrase(base.copy(quests = listOf(QuestProgress(quest, 1, true))), "G1"))
        // The points quest counts what the tick paid.
        val points = Quest(2, QuestKind.POINTS, "Earn 20 points from goals", 20, 5)
        assertEquals("Quest done +5", TickNews.phrase(base.copy(goalId = 2, quests = listOf(QuestProgress(points, 15, false))), "G2"))
    }

    @Test
    fun theWeeklyTargetAndTheRaceAreSaidWhenTheTickCrossesThem() {
        val base = TickMoment(finished = true, paid = 10, onTodaysList = true, leftBefore = 4, countsToday = true, record = record(20, 2))
        assertEquals("Weekly target reached", TickNews.phrase(base.copy(week = WeekProgress(day, 95, 100)), "G"))
        assertEquals("3 left today", TickNews.phrase(base.copy(week = WeekProgress(day, 80, 100)), "G"))
        assertEquals("3 left today", TickNews.phrase(base.copy(week = WeekProgress(day, 120, 100)), "G"))
        assertEquals("You're ahead of last week now", TickNews.phrase(base.copy(race = RaceStanding(30, 35)), "G"))
        assertEquals("3 left today", TickNews.phrase(base.copy(race = RaceStanding(30, 45)), "G"))
        assertEquals("3 left today", TickNews.phrase(base.copy(race = RaceStanding(50, 45)), "G"))
    }

    @Test
    fun tickingASkippedGoalPutsItBackInTheDay() {
        // 2 due with 1 done, and a third skipped. Ticking the skipped one makes it 2 of 3: under
        // 90%, so the streak isn't claimed, and as many are left as before.
        val m = TickMoment(
            finished = true, paid = 5, onTodaysList = true, leftBefore = 1, countsToday = true,
            record = record(2, 1, skips = 1), streak = ninety, streakBefore = 4, skippedBefore = true,
        )
        assertEquals("1 left today", TickNews.phrase(m, "Water"))
        // Undone, the count isn't guessed at.
        assertEquals("Water taken back · −5", TickNews.undone("Water", 5, m))
    }

    @Test
    fun aTickOffTodaysListWithNoNewsAddsNothing() {
        val m = TickMoment(finished = false, paid = 5)
        assertNull(TickNews.phrase(m, "Run"))
        assertEquals("Run · +5", TickNews.line("Run", 5, null, null))
        assertEquals("Run ticked", TickNews.line("Run", 0, null, null))
    }

    @Test
    fun undoSaysWhatWentBack() {
        assertEquals("Water taken back · −5", TickNews.undone("Water", 5))
        assertEquals("Water taken back", TickNews.undone("Water", 0))
        val m = TickMoment(finished = true, paid = 5, onTodaysList = true, leftBefore = 1)
        assertEquals("Water taken back · −5 · 1 left today", TickNews.undone("Water", 5, m))
    }

    // What is within reach

    @Test
    fun whatTheStreakTargetStillNeeds() {
        assertEquals(2, NearWins.neededForStreak(record(10, 7), ninety))
        assertEquals(0, NearWins.neededForStreak(record(10, 9), ninety))
        assertEquals(0, NearWins.neededForStreak(record(0, 0), ninety))
        assertEquals(3, NearWins.neededForStreak(record(2, 0).copy(due = 5), StreakSettings(StreakTarget.COUNT, 3)))
        // A count target above what is due needs only what is due.
        assertEquals(2, NearWins.neededForStreak(record(2, 0), StreakSettings(StreakTarget.COUNT, 3)))
    }

    @Test
    fun theClosestWinIsNamedAsAnOffer() {
        Rules.current = Rules()
        assertEquals("1 more for a perfect day (+10)", NearWins.line(record(5, 4), ninety, streak = 12, perfectBonus = 10))
        // With combos on, tonight's combo is part of what a perfect day pays: 10 + combo of 4 (8).
        assertEquals("1 more for a perfect day (+18)", NearWins.line(record(5, 4), ninety, streak = 12, perfectBonus = 10, combo = 3))
        // 20 due, 16 done: 2 more reach 90%, 4 more a perfect day.
        assertEquals("2 more keeps your 12-day streak", NearWins.line(record(20, 16), ninety, streak = 12, perfectBonus = 10))
        assertEquals("2 more and today counts for your streak", NearWins.line(record(20, 16), ninety, streak = 0))
        // A day with a skip is never a perfect day, so that isn't offered.
        assertEquals("1 more keeps your 3-day streak", NearWins.line(record(5, 4, skips = 1), ninety, streak = 3, perfectBonus = 10))
        // Everything done: nothing more to offer from the day.
        assertNull(NearWins.line(record(5, 5), ninety, streak = 3, perfectBonus = 10))
        assertNull(NearWins.line(null, ninety, streak = 3))
    }

    @Test
    fun aBadgeOrTheWeeklyTargetWithinReachIsNamed() {
        assertEquals("3 ticks from the 100 ticks badge", NearWins.line(record(5, 5), ninety, 0, ticks = 97))
        assertEquals("1 tick from the 100 ticks badge", NearWins.line(record(5, 5), ninety, 0, ticks = 99))
        assertNull(NearWins.line(record(5, 5), ninety, 0, ticks = 90))
        // Past 100, the next is 1,000.
        assertEquals("2 ticks from the 1,000 ticks badge", NearWins.line(record(5, 5), ninety, 0, ticks = 998, earnedBadges = setOf(Badge.TICKS_100)))
        // The nearer win comes first.
        assertEquals("1 more for a perfect day", NearWins.line(record(5, 4), ninety, 0, ticks = 97))
        PointsName.current = PointsName.DEFAULT
        assertEquals("15 more points reaches your weekly target", NearWins.line(record(5, 5), ninety, 0, week = WeekProgress(day, 85, 100)))
        assertNull(NearWins.line(record(5, 5), ninety, 0, week = WeekProgress(day, 50, 100)))
        assertNull(NearWins.line(record(5, 5), ninety, 0, week = WeekProgress(day, 100, 100)))
    }

    @Test
    fun theStreakCardSaysWhatTodayIs() {
        assertEquals(StreakToday("Today counts: day 13", best = 21), NearWins.streakToday(record(5, 5), ninety, streak = 13, best = 21, brokeYesterday = false))
        assertEquals(StreakToday("1 more and today counts: day 13", best = null), NearWins.streakToday(record(5, 4), ninety, streak = 12, best = 12, brokeYesterday = false))
        assertEquals("A new run starts today", NearWins.streakToday(record(5, 0), ninety, streak = 0, best = 9, brokeYesterday = true).line)
        // Nothing due today: nothing to say about it.
        assertNull(NearWins.streakToday(record(0, 0), ninety, streak = 4, best = 4, brokeYesterday = false).line)
        // Far from the target, it doesn't count down.
        assertNull(NearWins.streakToday(record(20, 2), ninety, streak = 4, best = 4, brokeYesterday = false).line)
    }

    // The morning card

    @Test
    fun whatWasPaidOvernightIsKeptForTheDay() {
        val yesterday = day.minusDays(1)
        val entries = listOf(
            Entry(at = 1, day = yesterday, kind = EntryKind.PERFECT_DAY, amount = 10, label = PerfectDays.LABEL),
            Entry(at = 1, day = yesterday.minusDays(1), kind = EntryKind.PERFECT_DAY, amount = 10, label = PerfectDays.LABEL),
            Entry(at = 1, day = yesterday.minusDays(1), kind = EntryKind.PERFECT_DAY, amount = 4, label = "Combo ×2"),
            Entry(at = 1, day = yesterday, kind = EntryKind.PERFECT_DAY, amount = 6, label = "Combo ×3"),
            Entry(at = 1, day = day, kind = EntryKind.INTEREST, amount = 3, label = "2% on 150"),
        )
        val paid = Overnight.paid(day, entries)
        assertEquals(OvernightPaid(day, perfect = 10, comboDays = 3, comboPoints = 6, interest = 3), paid)
        assertEquals(paid, Overnight.decode(Overnight.encode(paid)))
        // A second look the same day with nothing new keeps it; the next day starts again.
        assertEquals(paid, Overnight.paid(day, emptyList(), paid))
        assertTrue(Overnight.paid(day.plusDays(1), emptyList(), paid).isEmpty)
        assertNull(Overnight.decode("nonsense"))
        assertNull(Overnight.decode(null))
    }

    @Test
    fun theMorningCardSaysWhatYesterdayCameTo() {
        val yesterday = record(5, 5, on = day.minusDays(1))
        val paid = OvernightPaid(day, perfect = 10, comboDays = 3, comboPoints = 6, interest = 3)
        assertEquals(
            "Yesterday: 5 of 5 done · perfect day +10 · combo of 3 +6 · interest +3 · streak now 13",
            Overnight.line(day, yesterday, paid, ninety, streak = 13),
        )
        // A thin day: the count, and a fresh start. Nothing about what went wrong.
        assertEquals("Yesterday: 2 of 5. Today is a fresh start.", Overnight.line(day, record(5, 2, misses = 3, on = day.minusDays(1)), null, ninety, streak = 0))
        // What was kept for another day isn't shown.
        assertEquals("Yesterday: 5 of 5 done · streak now 13", Overnight.line(day, yesterday, paid.copy(day = day.minusDays(1)), ninety, streak = 13))
        // Nothing due and nothing paid: no card. Only interest: a short one.
        assertNull(Overnight.line(day, record(0, 0, on = day.minusDays(1)), null, ninety, streak = 4))
        assertEquals("Overnight: interest +3", Overnight.line(day, null, OvernightPaid(day, interest = 3), ninety, streak = 4))
    }

    // The combo, the race, the recap

    @Test
    fun theComboLineSaysWhatTonightIsWorth() {
        Rules.current = Rules()
        assertEquals("Combo ×3 · a perfect day today pays +8", Combos.line(3, record(5, 2)))
        assertEquals("A perfect day today starts a new combo (+2)", Combos.line(0, record(5, 2)))
        assertEquals("Combo ×4 tonight: +8 when the day ends", Combos.line(3, record(5, 5)))
        // Capped at the most days a combo pays for.
        assertEquals("Combo ×14 · a perfect day today pays +20", Combos.line(14, record(5, 2)))
        // A day with a skip is never perfect, and a day with nothing due says nothing.
        assertNull(Combos.line(3, record(5, 2, skips = 1)))
        assertNull(Combos.line(3, record(0, 0)))
        // The numbers are the user's own.
        Rules.current = Rules(comboPerDay = 5)
        assertEquals("A perfect day today starts a new combo (+5)", Combos.line(0, record(5, 2)))
        Rules.current = Rules()
    }

    @Test
    fun theRaceSaysWhatWouldTakeTheLead() {
        PointsName.current = PointsName.DEFAULT
        val rows = listOf(row(1, name = "Reading", points = 5), row(2, name = "Walk", points = 4), row(3, name = "Tidy", points = 2), row(4, done = true, name = "Done", points = 50), row(5, skipped = true, name = "Skipped", points = 50))
        val pays: (HabitRow) -> Int = { it.goal.points }
        // 8 behind: 9 takes the lead; the two biggest still open are enough.
        val catchUp = Race.catchUp(RaceStanding(20, 28), rows, pays)!!
        assertEquals(listOf("Reading", "Walk"), catchUp.goals.map { it.name })
        assertEquals("⭐ Reading and ⭐ Walk would put you ahead", catchUp.text())
        assertEquals("⭐ Reading would put you ahead", Race.catchUp(RaceStanding(20, 22), rows, pays)!!.text())
        // Level: one point more leads.
        assertEquals(1, Race.catchUp(RaceStanding(20, 20), rows, pays)!!.points)
        // More than today's goals can earn: only the points.
        assertEquals("31 more points today takes the lead", Race.catchUp(RaceStanding(20, 50), rows, pays)!!.text())
        assertNull(Race.catchUp(RaceStanding(30, 20), rows, pays))
        assertEquals("🏁 beat last week by 10", Race.result(RaceStanding(30, 20)))
        assertEquals("🏁 8 behind last week", Race.result(RaceStanding(20, 28)))
        assertEquals("🏁 level with last week", Race.result(RaceStanding(20, 20)))
    }

    @Test
    fun theWeeklySummarySaysHowTheRaceEnded() {
        PointsName.current = PointsName.DEFAULT
        val summary = WeeklySummary(due = 10, done = 9, missed = 1, skipped = 0, earned = 90, lost = 0, best = null)
        assertEquals("9 of 10 done (90%) · +90 points · 🏁 beat last week by 10 · 1 missed", summary.describe(race = RaceStanding(90, 80)))
        assertEquals("9 of 10 done (90%) · +90 points · 1 missed", summary.describe())
    }

    @Test
    fun aDoneDayGetsARecap() {
        Rules.current = Rules()
        PointsName.current = PointsName.DEFAULT
        val rows = listOf(row(1, done = true), row(2, done = true))
        val entries = listOf(
            Entry(at = 1, day = day, kind = EntryKind.TICK, amount = 30, label = "a"),
            Entry(at = 1, day = day, kind = EntryKind.BONUS, amount = 12, label = "b"),
            Entry(at = 1, day = day, kind = EntryKind.PURCHASE, amount = -40, label = "c"),
            Entry(at = 1, day = day.minusDays(1), kind = EntryKind.TICK, amount = 20, label = "a"),
            Entry(at = 1, day = day.minusDays(2), kind = EntryKind.TICK, amount = 41, label = "a"),
            Entry(at = 1, day = day.minusDays(3), kind = EntryKind.TICK, amount = 10, label = "a"),
            // Last month's best doesn't count for "this month".
            Entry(at = 1, day = LocalDate(2026, 10, 30), kind = EntryKind.TICK, amount = 500, label = "a"),
        )
        val streaks = Streaks(done = 13, noMisses = 3, noSkips = 3, withinLimit = 3, perfect = 4)
        val recap = DayRecaps.of(day, rows, record(2, 2), entries, ninety, streaks, perfectBonus = 10, combo = 3)!!
        assertEquals("2 done · +42 points", recap.headline())
        assertEquals("Tonight: perfect day +10 · combo of 4 +8", recap.tonight())
        assertEquals("Your streak with today: 13 days", recap.streakLine())
        assertEquals("Most points in a day this month", recap.highlight)
        // Locked: nothing about other days.
        assertNull(DayRecaps.of(day, rows, record(2, 2), entries, ninety, streaks, highlights = false)!!.highlight)
        // Not the most: the run of perfect days instead.
        val fewer = entries.filter { it.kind != EntryKind.BONUS }
        assertEquals("4 perfect days in a row", DayRecaps.of(day, rows, record(2, 2), fewer, ninety, streaks)!!.highlight)
    }

    @Test
    fun aSkippedGoalIsLeftOutOfTheRecapAndTheDayIsNotPerfect() {
        val rows = listOf(row(1, done = true), row(2, skipped = true))
        val recap = DayRecaps.of(day, rows, record(1, 1, skips = 1), emptyList(), ninety, Streaks(1, 1, 0, 1, 0), perfectBonus = 10, combo = 3)!!
        assertEquals(1, recap.done)
        assertFalse(recap.perfect)
        assertNull(recap.tonight())
        assertEquals("1 done", recap.headline())
        // Something left, or everything skipped: no recap.
        assertNull(DayRecaps.of(day, listOf(row(1, done = true), row(2)), record(2, 1), emptyList(), ninety, Streaks(0, 0, 0, 0, 0)))
        assertNull(DayRecaps.of(day, listOf(row(2, skipped = true)), record(0, 0, skips = 1), emptyList(), ninety, Streaks(0, 0, 0, 0, 0)))
        assertNull(DayRecaps.of(day, emptyList(), null, emptyList(), ninety, Streaks(0, 0, 0, 0, 0)))
    }

    @Test
    fun tomorrowIsPreviewedByItsFirstFew() {
        fun view(rows: List<HabitRow>) = TodayView(day.plusDays(1), 0, rows, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("⭐ A, ⭐ B", Upcoming.of(view(listOf(row(1, name = "A"), row(2, name = "B"))))!!.text())
        assertEquals("⭐ A, ⭐ B, ⭐ C and 2 more", Upcoming.of(view((1L..5L).map { row(it, name = ('A' + (it - 1).toInt()).toString()) }))!!.text())
        assertNull(Upcoming.of(view(emptyList())))
        assertNull(Upcoming.of(null))
    }

    // The pace card

    private val tenPm = PaceSettings(dayEndHour = 0, defaultFinish = LocalTime(22, 0), dayStart = LocalTime(8, 0))
    private fun at(hour: Int, minute: Int = 0) = LocalDateTime(2026, 11, 11, hour, minute)

    @Test
    fun timeToSpareIsWhatIsFreeBeforeTheNextFinish() {
        // 2 pm to 10 pm is 480 minutes; an hour is set aside.
        val pace = Pace.today(at(14), day, listOf(row(1, minutes = 60)), tenPm)!!
        assertEquals(420, pace.spareMinutes)
        // Short of time: nothing to spare.
        assertEquals(0, Pace.today(at(14), day, listOf(row(1, minutes = 600)), tenPm)!!.spareMinutes)
    }

    @Test
    fun thePaceIsAheadWhenMoreIsDoneThanTheDayHasGone() {
        // 8 am to 10 pm; at 10 am a seventh of the day has gone, and half the goals are done.
        val rows = listOf(row(1, done = true), row(2, done = true), row(3), row(4))
        val pace = Pace.today(at(10), day, rows, tenPm)!!
        assertEquals(PaceState.AHEAD, Pace.state(pace, at(10), day, rows, tenPm))
        // By 6 pm most of the day has gone: no longer ahead.
        assertEquals(PaceState.STEADY, Pace.state(Pace.today(at(18), day, rows, tenPm)!!, at(18), day, rows, tenPm))
        // Nothing done yet is never ahead, and neither is a day with something late.
        val none = listOf(row(1), row(2))
        assertEquals(PaceState.STEADY, Pace.state(Pace.today(at(9), day, none, tenPm)!!, at(9), day, none, tenPm))
        assertEquals(PaceState.STEADY, Pace.state(pace, at(10), day, rows, tenPm, late = true))
    }

    @Test
    fun thePaceIsTightOrShortWhenTimeRunsOut() {
        // Two untimed goals with 10 minutes left: 5 minutes each.
        val rows = listOf(row(1), row(2))
        val tight = Pace.today(at(21, 50), day, rows, tenPm)!!
        assertEquals(PaceState.TIGHT, Pace.state(tight, at(21, 50), day, rows, tenPm))
        // A 60-minute goal with 65 minutes left: it fits, with 5 to spare.
        val long = listOf(row(1, minutes = 60))
        assertEquals(PaceState.TIGHT, Pace.state(Pace.today(at(20, 55), day, long, tenPm)!!, at(20, 55), day, long, tenPm))
        // Past the finish time, or with something late, nothing is said to "still fit".
        val past = Pace.today(at(22, 30), day, rows, tenPm)!!
        assertEquals(PaceState.STEADY, Pace.state(past, at(22, 30), day, rows, tenPm))
        assertEquals(PaceState.STEADY, Pace.state(tight, at(21, 50), day, rows, tenPm, late = true))
        val short = Pace.today(at(21, 30), day, long, tenPm)!!
        assertEquals(PaceState.SHORT, Pace.state(short, at(21, 30), day, long, tenPm))
    }

    @Test
    fun aFullDayIsOfferedAMoveOrTheShortestNeverASkip() {
        // An every-day goal can't move (it is due every day); one due only today can.
        val onlyToday = Repeat.everyDay(day).copy(kind = RepeatKind.WEEKDAYS, weekdays = setOf(day.dayOfWeek.ordinal + 1))
        val movable = HabitRow(goal(1, "Gym", minutes = 60, repeat = onlyToday), 0, Window(day, day, Period.DAILY))
        val rows = listOf(movable, row(2, name = "Stretch", minutes = 5), row(3, done = true, name = "Done", minutes = 1), row(4, name = "Read"))
        val help = Pace.help(rows, day)
        assertNotNull(help.move)
        assertEquals("Gym", help.move?.name)
        assertEquals("Stretch", help.shortest?.name)
        // With nothing that can move, only the shortest.
        assertNull(Pace.help(rows.drop(1), day).move)
        // With one goal left there is no "start with".
        assertNull(Pace.help(listOf(row(2, name = "Stretch", minutes = 5)), day).shortest)
    }
}
