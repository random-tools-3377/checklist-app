package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A skip is never counted as done and never benefits, but it doesn't directly punish either. */
class SkipCountingTest {
    private val start = LocalDate(2026, 1, 1)
    private val monday = LocalDate(2026, 11, 9)
    private val days = (0..6).map { monday.plusDays(it) }

    private fun habit(id: Long, name: String, repeat: Repeat = Repeat.everyDay(start), target: Int = 1) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = 10, repeat = repeat, target = target)

    private fun tick(goalId: Long, day: LocalDate, period: Period = Period.DAILY, periodStart: LocalDate = day) =
        Tick(goalId = goalId, stepId = null, at = 0, day = day, period = period, periodStart = periodStart)

    private fun skip(goalId: Long, day: LocalDate, period: Period = Period.DAILY, periodStart: LocalDate = day, overLimit: Boolean = false) =
        Lapse(goalId = goalId, kind = LapseKind.SKIP, day = day, period = period, periodStart = periodStart, at = 0, overLimit = overLimit, label = "x")

    private val run = habit(1, "Run")
    private val read = habit(2, "Read")
    private val walk = habit(3, "Walk")

    @Test
    fun aSkippedGoalDropsOutOfWhatTheDayCounts() {
        val ticks = listOf(tick(1, monday), tick(2, monday))
        val record = StreakCounter.records(monday, monday, listOf(run, read, walk), ticks, listOf(skip(3, monday))).single()
        // Neither due nor done: two were due, and both got done.
        assertEquals(listOf(2, 2, 1), listOf(record.due, record.done, record.skips))

        val streaks = StreakCounter.count(listOf(record), monday, StreakSettings(StreakTarget.PERCENT, 100))
        assertEquals(1, streaks.done) // the main streak's target is met by what was left
        assertEquals(0, streaks.noSkips) // it still breaks the Skip-free streak
        assertEquals(0, streaks.perfect) // and a day with a skip is never a perfect day
        assertFalse(record.perfect)
        assertFalse(PerfectDays.isPerfect(record))
    }

    @Test
    fun aSkipNeverMakesTheTargetEasierToCallDone() {
        // One done, one skipped, one left undone: half of what counts, not two thirds.
        val record = StreakCounter.records(monday, monday, listOf(run, read, walk), listOf(tick(1, monday)), listOf(skip(3, monday))).single()
        assertEquals(2 to 1, record.due to record.done)
        assertEquals(false, StreakCounter.mainDay(record, StreakSettings(StreakTarget.PERCENT, 60)))
        assertEquals(true, StreakCounter.mainDay(record, StreakSettings(StreakTarget.PERCENT, 50)))
    }

    @Test
    fun aDayWithEverythingSkippedNeitherCountsNorBreaksTheMainStreak() {
        val goals = listOf(run, read)
        val ticks = listOf(tick(1, days[0]), tick(2, days[0]), tick(1, days[2]), tick(2, days[2]))
        val lapses = listOf(skip(1, days[1]), skip(2, days[1]))
        val records = StreakCounter.records(days[0], days[2], goals, ticks, lapses)
        assertEquals(listOf(2 to 2, 0 to 0, 2 to 2), records.map { it.due to it.done })
        assertNull(StreakCounter.mainDay(records[1], StreakSettings()))

        val settings = StreakSettings()
        val streaks = StreakCounter.count(records, days[2], settings)
        assertEquals(2, streaks.done) // Monday and Wednesday: Tuesday is passed over
        assertEquals(1, streaks.noSkips) // only Wednesday
        assertEquals(1, streaks.perfect) // Tuesday was no perfect day
        assertEquals(3, streaks.noMisses) // nothing was missed
        // Everything that reads the records agrees: shields, the longest streak, days away.
        assertEquals(2, Shields.of(records, days[0], days[2], settings).main)
        assertEquals(2, Titles.longestMainStreak(records, settings))
        assertEquals(0, Checkins.daysAway(records, days[3]))
        assertEquals(0, Checkins.leftYesterday(records, days[2]))
    }

    @Test
    fun aSkippedDayAfterNothingDoneIsNotADayAway() {
        // Nothing done on Monday, everything skipped on Tuesday: one day with nothing done, not two.
        val records = StreakCounter.records(days[0], days[2], listOf(run), listOf(tick(1, days[2])), listOf(skip(1, days[1])))
        val earlier = DayRecord(monday.minusDays(1), 1, 1, 0, 0, 0)
        assertEquals(1, Checkins.daysAway(listOf(earlier) + records.take(2), days[2]))
    }

    @Test
    fun aTimesPerGoalReachedOnlyWithASkipDropsOutOnItsLastDay() {
        val gym = habit(4, "Gym", Repeat.timesPer(Period.WEEKLY, start), target = 3)
        val sunday = days[6]
        fun record(ticks: List<Tick>, lapses: List<Lapse>) = StreakCounter.records(sunday, sunday, listOf(gym), ticks, lapses).single()
        fun gymTick(day: LocalDate) = tick(4, day, Period.WEEKLY, monday)
        val skipped = skip(4, days[2], Period.WEEKLY, monday)

        // Two done and one skipped: nothing left of it, but it isn't done.
        assertEquals(0 to 0, record(listOf(gymTick(days[0]), gymTick(days[1])), listOf(skipped)).let { it.due to it.done })
        // Done three times after all, skip or no skip: done.
        assertEquals(1 to 1, record(listOf(gymTick(days[0]), gymTick(days[1]), gymTick(days[3])), listOf(skipped)).let { it.due to it.done })
        // One done, one skipped, one never done: due and not done.
        assertEquals(1 to 0, record(listOf(gymTick(days[0])), listOf(skipped)).let { it.due to it.done })
        // The skip counts on the day it was made, not on Sunday.
        assertEquals(0, record(listOf(gymTick(days[0]), gymTick(days[1])), listOf(skipped)).skips)
        assertEquals(1, StreakCounter.records(days[2], days[2], listOf(gym), emptyList(), listOf(skipped)).single().skips)
    }

    @Test
    fun aSkipEarnsNoPerfectDayBonusAndEndsACombo() = runTest {
        val store = MemoryStore()
        val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0))
        val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
        val lapses = Lapses(store, book, Random(1))
        store.putSetting(Feature.PERFECT_DAY.key, Feature.PERFECT_DAY.onValue)
        store.putSetting(Feature.COMBOS.key, Feature.COMBOS.onValue)
        val perfect = PerfectDays(store, book)
        val combos = Combos(store, book)
        val a = store.addGoal(habit(0, "Run").copy(allowance = 5))
        val b = store.addGoal(habit(0, "Read").copy(allowance = 5))
        assertTrue(perfect.pay().isEmpty())
        assertTrue(combos.pay().isEmpty())

        // Monday: both done. Tuesday: one done, one skipped. Wednesday: both skipped. Thursday: both done.
        book.tickHabit(a.id)
        book.tickHabit(b.id)
        clock.set(LocalDateTime(2026, 11, 10, 10, 0))
        book.tickHabit(a.id)
        lapses.skip(b.id, null, null)
        clock.set(LocalDateTime(2026, 11, 11, 10, 0))
        lapses.skip(a.id, null, null)
        lapses.skip(b.id, null, null)
        clock.set(LocalDateTime(2026, 11, 12, 10, 0))
        book.tickHabit(a.id)
        book.tickHabit(b.id)
        clock.set(LocalDateTime(2026, 11, 13, 10, 0))

        assertEquals(listOf(days[0], days[3]), perfect.pay().map { it.day })
        // The combo ends on the day with a skip, and starts again from 1.
        assertEquals(listOf("Combo ×1" to days[0], "Combo ×1" to days[3]), combos.pay().map { it.label to it.day })
    }

    @Test
    fun aSkipMadeEarlierInTheWeekDoesNotSpoilTheWeeksLastDay() = runTest {
        val store = MemoryStore()
        val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0))
        val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
        val lapses = Lapses(store, book, Random(1))
        store.putSetting(Feature.PERFECT_DAY.key, Feature.PERFECT_DAY.onValue)
        store.putSetting(Feature.COMBOS.key, Feature.COMBOS.onValue)
        val perfect = PerfectDays(store, book)
        val combos = Combos(store, book)
        val read = store.addGoal(habit(0, "Read").copy(allowance = 5))
        val gym = store.addGoal(habit(0, "Gym", Repeat.timesPer(Period.WEEKLY, start), target = 2).copy(allowance = 5))
        assertTrue(perfect.pay().isEmpty())
        assertTrue(combos.pay().isEmpty())

        // The app is opened every day, so each day is looked at on its own. "Read" is done every
        // day; "Gym", twice a week, is done on Monday and one of its times is skipped on Wednesday.
        val perfectDays = mutableListOf<LocalDate>()
        val comboDays = mutableListOf<Pair<String, LocalDate>>()
        book.tickHabit(read.id)
        book.tickHabit(gym.id)
        for (date in 10..16) {
            clock.set(LocalDateTime(2026, 11, date, 10, 0))
            perfectDays += perfect.pay().map { it.day }
            comboDays += combos.pay().map { it.label to it.day }
            if (date <= 15) book.tickHabit(read.id)
            if (date == 11) lapses.skip(gym.id, null, null)
        }

        // Wednesday, the day of the skip, is no perfect day. Sunday is: the skipped goal dropped
        // out of it, and the skip was not made that day.
        assertEquals(listOf(days[0], days[1], days[3], days[4], days[5], days[6]), perfectDays)
        assertEquals(
            listOf("Combo ×1" to days[0], "Combo ×2" to days[1], "Combo ×1" to days[3], "Combo ×2" to days[4], "Combo ×3" to days[5], "Combo ×4" to days[6]),
            comboDays,
        )
    }

    @Test
    fun theReviewCountsOnlyRealTicksAsDone() {
        // Run: done Monday to Wednesday, skipped Thursday, done Friday to Sunday. Read: done every day but Thursday.
        val ticks = days.filter { it != days[3] }.flatMap { listOf(tick(1, it), tick(2, it)) }
        val lapses = listOf(skip(1, days[3]))
        val summary = Review.summarize(monday, days[6], today = days[6], goals = listOf(run, read), ticks = ticks, lapses = lapses, entries = emptyList(), gaps = emptyList())

        val running = summary.goals.first { it.goal.id == 1L }
        // The skipped day stays in what was due, under Skipped only.
        assertEquals(listOf(7, 6, 1, 0), listOf(running.due, running.done, running.skipped, running.missed))
        assertEquals(6.0 / 7, running.consistency)
        // The skip neither adds to the run nor ends it.
        assertEquals(6, running.longestRun)
        assertEquals(6, running.currentRun)
        // Thursday: 0 of 2 really done, so it is the hardest day, not as good as the rest.
        assertEquals(2 to 0, summary.weekdays[4])
        assertEquals(4, summary.worstWeekday)
        assertEquals("Run", summary.mostSkipped?.goal?.name)

        val weekly = WeeklySummary.of(summary)
        assertEquals(listOf(14, 12, 1), listOf(weekly.due, weekly.done, weekly.skipped))
        assertEquals(85, weekly.percent)
        // The day records behind the calendar leave the skipped goal out.
        assertEquals(1 to 0, summary.days[3].let { it.due to it.done })
    }

    @Test
    fun aGoalOnlyEverSkippedIsNeverTheMostConsistent() {
        val lapses = days.map { skip(1, it) }
        val ticks = days.take(4).map { tick(2, it) }
        val summary = Review.summarize(monday, days[6], today = days[6], goals = listOf(run, read), ticks = ticks, lapses = lapses, entries = emptyList(), gaps = emptyList())
        val running = summary.goals.first { it.goal.id == 1L }
        assertEquals(listOf(7, 0, 7), listOf(running.due, running.done, running.skipped))
        assertEquals("Read", summary.mostConsistent?.goal?.name)
        assertEquals("Read", WeeklySummary.of(summary).best?.goal?.name)
        // Skipped all week, nothing done: no "best" to name.
        val onlySkips = Review.summarize(monday, days[6], today = days[6], goals = listOf(run), ticks = emptyList(), lapses = lapses, entries = emptyList(), gaps = emptyList())
        assertFalse(WeeklySummary.of(onlySkips).describe().contains("best"))
        assertTrue(WeeklySummary.of(onlySkips).describe().startsWith("0 of 7 done (0%)"))
    }

    @Test
    fun aTimesPerWeekGoalWithASkipIsSkippedNotDoneInTheReview() {
        val gym = habit(4, "Gym", Repeat.timesPer(Period.WEEKLY, start), target = 3)
        val ticks = listOf(tick(4, days[0], Period.WEEKLY, monday), tick(4, days[1], Period.WEEKLY, monday))
        val lapses = listOf(skip(4, days[2], Period.WEEKLY, monday))
        val summary = Review.summarize(monday, days[6], today = days[6], goals = listOf(gym), ticks = ticks, lapses = lapses, entries = emptyList(), gaps = emptyList())
        val week = summary.goals.single()
        assertEquals(listOf(1, 0, 1, 0), listOf(week.due, week.done, week.skipped, week.missed))
    }

    @Test
    fun aSkippedGoalHasNothingLeftButIsNotDone() {
        val window = Window(monday, monday, Period.DAILY)
        val skipped = HabitRow(run, 0, window, skips = 1, skipsToday = 1)
        assertFalse(skipped.done)
        assertTrue(skipped.settled)
        assertTrue(skipped.skipped)
        assertEquals(0, skipped.left)
        assertTrue(skipped.canTakeBackSkip)

        val done = HabitRow(run, 1, window)
        assertTrue(done.done && done.settled && !done.skipped)
        val open = HabitRow(run, 0, window)
        assertFalse(open.done || open.settled)
        // Skipped on an earlier day of its week: it can't be taken back any more.
        val week = Window(monday, days[6], Period.WEEKLY)
        val gym = habit(4, "Gym", Repeat.timesPer(Period.WEEKLY, start), target = 3)
        val partly = HabitRow(gym, 1, week, skips = 1)
        assertFalse(partly.done || partly.settled)
        assertEquals(1, partly.left)
        assertFalse(partly.canTakeBackSkip)
        assertTrue(HabitRow(gym, 2, week, skips = 1).settled)
        assertFalse(HabitRow(gym, 2, week, skips = 1).done)
    }

    @Test
    fun todaysListKnowsWhichSkipsWereMadeToday() {
        val gym = habit(4, "Gym", Repeat.timesPer(Period.WEEKLY, start), target = 3)
        val lapses = listOf(skip(1, days[1]), skip(4, days[0], Period.WEEKLY, monday), skip(4, days[1], Period.WEEKLY, monday))
        val tuesday = TodayList.build(days[1], listOf(run, read, gym), emptyList(), emptyList(), 0, lapses)
        assertEquals(listOf(1, 0), tuesday.dueToday.map { it.skipsToday })
        assertEquals(2 to 1, tuesday.thisWeek.single().let { it.skips to it.skipsToday })
        // The next day, Tuesday's skips can't be taken back.
        val wednesday = TodayList.build(days[2], listOf(run, read, gym), emptyList(), emptyList(), 0, lapses)
        assertEquals(listOf(0, 0), wednesday.dueToday.map { it.skipsToday })
        assertEquals(2 to 0, wednesday.thisWeek.single().let { it.skips to it.skipsToday })
    }

    @Test
    fun aSkippedGoalNeedsNoDoingButNothingTreatsItAsDone() {
        val window = Window(monday, monday, Period.DAILY)
        val rows = listOf(HabitRow(run, 1, window), HabitRow(read, 0, window, skips = 1), HabitRow(walk, 0, window))
        val now = LocalDateTime(2026, 11, 9, 10, 0)
        val settings = PaceSettings(dayEndHour = 0, defaultFinish = LocalTime(21, 0))

        // The pace and the order only plan what is left: the one not done and not skipped.
        assertEquals(1, Pace.today(now, monday, rows, settings)?.untimedLeft)
        val order = Order.recommend(now, monday, rows, settings, emptyMap(), Pace.today(now, monday, rows, settings))
        assertEquals(listOf("Walk", "Run", "Read"), order.map { it.row.goal.name })
        // Reminders and the morning plan leave it alone.
        assertFalse(Alerts.stillToDo(read, window, emptyList(), listOf(skip(2, monday))))
        assertTrue(Alerts.stillToDo(walk, window, emptyList(), listOf(skip(2, monday))))
        val view = TodayView(monday, 0, rows, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(1, MorningPlan.of(now, view, settings)?.goals)

        // The mood check-in once everything is done leaves it out, and needs something really done.
        assertFalse(MoodInsights.allDone(rows))
        assertTrue(MoodInsights.allDone(rows.take(2)))
        assertFalse(MoodInsights.allDone(listOf(rows[1])))
    }

    @Test
    fun aSkippedGoalNeverFinishesAQuest() {
        val window = Window(monday, monday, Period.DAILY)
        val timeOf: (Long) -> LocalTime = { LocalTime(9, 0) }
        val ticks = listOf(tick(1, monday))
        val rows = listOf(HabitRow(run, 1, window), HabitRow(read, 0, window, skips = 1))

        val everything = Quest(0, QuestKind.ALL_BY, "Finish everything due by 8 pm", 2, 10, by = LocalTime(20, 0))
        assertEquals(QuestProgress(everything, 1, false), Quests.progress(everything, rows, ticks, timeOf, 10))
        val named = Quest(1, QuestKind.GOAL, "Do Read", 1, 5, goalId = 2)
        assertFalse(Quests.progress(named, rows, ticks, timeOf, 10).complete)
        val area = Quest(2, QuestKind.AREA, "Do all 2 goals", 2, 5, area = SkillArea.of(run))
        val sameArea = listOf(HabitRow(run, 1, window), HabitRow(run.copy(id = 9), 0, window, skips = 1))
        assertFalse(Quests.progress(area, sameArea, ticks, timeOf, 10).complete)
        // Done after all, they do.
        assertTrue(Quests.progress(named, listOf(rows[0], HabitRow(read, 1, window)), ticks, timeOf, 10).complete)
    }

    @Test
    fun theCharacterDoesNotCheerAFullDayWhenSomethingWasSkipped() {
        fun facts(skipped: Int) = CompanionFacts(
            day = monday, now = LocalTime(10, 0), left = emptyList(), doneToday = 2, dueToday = 2, missedYesterday = 0,
            mainStreak = 0, mood = null, askMood = false, daysAway = 0, skippedToday = skipped,
        )
        val full = Checkins.say(facts(0))
        assertEquals(Expression.PROUD, full.expression)
        assertEquals(CharacterMove.DANCE, full.move)
        val withSkip = Checkins.say(facts(1))
        assertEquals(Expression.HAPPY, withSkip.expression)
        assertEquals(CharacterMove.NONE, withSkip.move)
        assertFalse(withSkip.line.contains("Everything done") || withSkip.line.contains("All done"))
    }

    @Test
    fun theCheckInOnceEverythingIsDoneDoesNotSayASkippedGoalWasDone() {
        assertEquals("You've done everything for today. How do you feel now?", MoodInsights.question(3))
        assertEquals("That's everything for today. How do you feel now?", MoodInsights.question(3, skipped = true))
        assertEquals("You started the day feeling low. That's everything for today — how do you feel now?", MoodInsights.question(2, skipped = true))
    }

    @Test
    fun badgesForPerfectDaysNeedADayWithoutASkip() {
        // Every day of the week done, but one goal skipped on Wednesday.
        val ticks = days.flatMap { day -> listOfNotNull(tick(1, day), tick(2, day).takeIf { day != days[2] }) }
        val lapses = listOf(skip(2, days[2]))
        val records = StreakCounter.records(monday, days[6], listOf(run, read), ticks, lapses)
        val earned = Badges.earned(days[6], records, StreakSettings(), listOf(run, read), emptyList(), ticks, emptyList(), 0).map { it.badge }
        assertTrue(Badge.PERFECT_DAY in earned)
        assertFalse(Badge.PERFECT_WEEK in earned)
        // The main streak carried on through the skip: seven days.
        assertTrue(Badge.STREAK_7 in earned)
        // Without the skip, done: a perfect week.
        val clean = StreakCounter.records(monday, days[6], listOf(run, read), ticks + tick(2, days[2]), emptyList())
        assertTrue(Badge.PERFECT_WEEK in Badges.earned(days[6], clean, StreakSettings(), listOf(run, read), emptyList(), ticks, emptyList(), 0).map { it.badge })
    }
}

/** Taking a skip back puts everything back as if it never happened. */
class TakeBackSkipTest {
    private val store = MemoryStore()

    // Wednesday 11 November 2026, 10:00.
    private val clock = TestClock(LocalDateTime(2026, 11, 11, 10, 0))
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val lapses = Lapses(store, book, Random(1))
    private val gaps = Gaps(store, book)
    private val start = LocalDate(2026, 1, 1)
    private val wednesday = LocalDate(2026, 11, 11)

    private fun habit(points: Int = 10, allowance: Int? = null, repeat: Repeat = Repeat.everyDay(start), target: Int = 1, name: String = "Run") =
        store.addGoal(Goal(kind = GoalKind.HABIT, name = name, icon = "🏃", points = points, repeat = repeat, target = target, allowance = allowance))

    private fun at(day: Int, hour: Int = 10) = clock.set(LocalDateTime(2026, 11, day, hour, 0))

    private fun record(day: LocalDate = wednesday) = StreakCounter.records(day, day, store.goals, store.ticks, store.lapses).single()

    @Test
    fun withinTheAllowanceTheSkipGoesAndTheAllowanceComesBack() = runTest {
        val run = habit(allowance = 1)
        val yesterday = DayRecord(wednesday.minusDays(1), 1, 1, 0, 0, 0)
        fun skipFree() = StreakCounter.count(listOf(yesterday, record()), wednesday, StreakSettings()).noSkips
        lapses.skip(run.id, null, null)
        assertEquals(1, record().skips)
        assertEquals(0, skipFree())
        assertEquals(SkipCheck.AlreadyDone, lapses.checkSkip(run.id))

        val back = lapses.takeBackSkip(run.id)

        assertEquals(0, back?.refunded)
        assertTrue(store.lapses.isEmpty())
        assertTrue(store.entries.isEmpty())
        // The allowance it used is free again, and the day is as it was: due, not done, no skip.
        assertEquals(SkipCheck.Allowed(used = 0, allowed = 1, overLimit = false, cost = 0), lapses.checkSkip(run.id))
        assertEquals(listOf(1, 0, 0), record().let { listOf(it.due, it.done, it.skips) })
        assertEquals(2, skipFree()) // the Skip-free streak is running again, today included
        // Nothing more to take back.
        assertNull(lapses.takeBackSkip(run.id))
    }

    @Test
    fun overTheLimitThePenaltyPointsComeBackAndTheOpenPenaltyTaskIsDropped() = runTest {
        val run = habit(points = 11, allowance = 0)
        val skipped = lapses.skip(run.id, Reason.NO_TIME, "busy") as SkipResult.Skipped
        assertEquals(-5, book.balance())
        assertNotNull(lapses.openPenalty())
        assertEquals(skipped.lapse.id, lapses.skipBehind(lapses.openPenalty()!!)?.id)

        val back = lapses.takeBackSkip(run.id)

        assertEquals(5, back?.refunded)
        assertEquals(0, book.balance())
        assertTrue(store.lapses.isEmpty())
        // Given back with an entry in the points history, beside the penalty itself.
        assertEquals(listOf(-5, 5), store.entries.map { it.amount })
        val given = store.entries.last()
        assertTrue(given.isSkipTakenBack)
        assertEquals(listOf(EntryKind.PENALTY, wednesday, run.id), listOf(given.kind, given.day, given.goalId))
        // The penalty task wasn't done yet: it is dropped.
        assertNull(lapses.openPenalty())
        assertNotNull(store.penalty(skipped.penaltyId!!)?.cancelledAt)
        // Nothing still points at the skip that is gone.
        assertTrue(store.entries.all { it.lapseId == null })
        assertNull(store.penalty(skipped.penaltyId!!)?.lapseId)
        // The day is back within the limit.
        assertEquals(listOf(0, 0), record().let { listOf(it.skips, it.overLimit) })
        // Skipping again goes over the limit again, as the first time.
        assertEquals(SkipCheck.Allowed(used = 0, allowed = 0, overLimit = true, cost = 5), lapses.checkSkip(run.id))
    }

    @Test
    fun pointsGivenBackAreNeitherEarnedNorLost() = runTest {
        val run = habit(points = 40, allowance = 0)
        lapses.skip(run.id, Reason.CHOSE_NOT, null)
        lapses.takeBackSkip(run.id)
        lapses.skip(run.id, Reason.CHOSE_NOT, null)
        lapses.takeBackSkip(run.id)

        val entries = store.entries.toList()
        assertEquals(0, book.balance())
        val summary = Review.summarize(wednesday, wednesday, wednesday, store.goals, store.ticks, store.lapses, entries, emptyList())
        assertEquals(listOf(0, 0), listOf(summary.earned, summary.lost))
        assertTrue(summary.lostByDay.isEmpty())
        assertEquals(0, WeeklyTarget.progress(entries, wednesday, 100).earned)
        assertEquals(0, Race.of(entries, wednesday, book.millis()).thisWeek)
        // The points history and the spreadsheet say what each line was.
        assertEquals(listOf("Penalty", "Skip taken back", "Penalty", "Skip taken back"), entries.map { Spreadsheet.kindText(it) })
        assertTrue(Spreadsheet.csv(entries, emptyMap()) { LocalDateTime(2026, 11, 11, 10, 0) }.contains("Skip taken back,Run,20"))
    }

    @Test
    fun aPenaltyTaskAlreadyDoneStaysDone() = runTest {
        val run = habit(points = 10, allowance = 0)
        store.penaltyTaskList += PenaltyTask(id = 1, name = "20 push-ups")
        val skipped = lapses.skip(run.id, Reason.ILL, null) as SkipResult.Skipped
        lapses.drawTask(skipped.penaltyId!!)
        assertTrue(lapses.finishPenalty(skipped.penaltyId!!))

        assertEquals(5, lapses.takeBackSkip(run.id)?.refunded)

        val penalty = store.penalty(skipped.penaltyId!!)!!
        assertNotNull(penalty.doneAt)
        assertNull(penalty.cancelledAt)
        assertEquals(0, book.balance())
        assertTrue(store.lapses.isEmpty())
    }

    @Test
    fun forATimesPerGoalItTakesBackOneSkipOfItsTimes() = runTest {
        val gym = habit(allowance = 5, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 3)
        at(10) // Tuesday
        lapses.skip(gym.id, null, null)
        at(11) // Wednesday
        val second = lapses.skip(gym.id, null, null) as SkipResult.Skipped
        assertEquals(2, store.lapses.size)

        // One skip of today's comes back; Tuesday's has ended and stays.
        assertEquals(second.lapse.id, lapses.takeBackSkip(gym.id)?.skip?.id)
        assertEquals(listOf(LocalDate(2026, 11, 10)), store.lapses.map { it.day })
        assertNull(lapses.takeBackSkip(gym.id))
        assertEquals(1, store.lapses.size)
    }

    @Test
    fun onceTheDayHasEndedASkipCannotBeTakenBack() = runTest {
        val run = habit(points = 10, allowance = 0)
        val skipped = lapses.skip(run.id, Reason.ILL, null) as SkipResult.Skipped
        at(12) // Thursday

        assertNull(lapses.takeBackSkip(run.id))
        assertNull(lapses.takeBackSkipById(skipped.lapse.id))
        assertNull(lapses.skipBehind(store.penalty(skipped.penaltyId!!)!!))
        assertEquals(1, store.lapses.size)
        assertEquals(-5, book.balance())
        assertNotNull(lapses.openPenalty())
    }

    @Test
    fun theDayEndsWhenTheUserSaysSo() = runTest {
        store.dayEnd = 4
        val run = habit(allowance = 1)
        at(11, hour = 23)
        lapses.skip(run.id, null, null)
        // 2 am still belongs to Wednesday: it can be taken back. After 4 am it can't.
        at(12, hour = 5)
        assertNull(lapses.takeBackSkip(run.id))
        at(12, hour = 2)
        assertNotNull(lapses.takeBackSkip(run.id))
    }

    @Test
    fun undoTakesBackThatVerySkip() = runTest {
        val run = habit(allowance = 1)
        val read = habit(allowance = 1, name = "Read")
        val first = lapses.skip(run.id, null, null) as SkipResult.Skipped
        lapses.skip(read.id, null, null)

        assertEquals(run.id, lapses.takeBackSkipById(first.lapse.id)?.skip?.goalId)
        assertEquals(listOf(read.id), store.lapses.map { it.goalId })
        // A miss is no skip: it can't be taken back this way.
        val miss = Lapse(goalId = run.id, kind = LapseKind.MISS, day = wednesday, period = Period.DAILY, periodStart = wednesday, at = 0, overLimit = false, label = "Run")
        val missId = store.addLapse(miss)
        assertNull(lapses.takeBackSkipById(missId))
    }

    @Test
    fun tickingASkippedGoalTheSameDayTakesTheSkipBackByItself() = runTest {
        val run = habit(points = 10, allowance = 0)
        val skipped = lapses.skip(run.id, Reason.NO_TIME, null) as SkipResult.Skipped
        assertEquals(-5, book.balance())

        val result = book.tickHabit(run.id) as TickResult.Ticked

        assertTrue(result.finished)
        assertTrue(store.lapses.isEmpty())
        assertEquals(10, book.balance()) // the penalty came back, and the tick paid
        assertNotNull(store.penalty(skipped.penaltyId!!)?.cancelledAt)
        assertEquals(listOf(1, 1, 0, 0), record().let { listOf(it.due, it.done, it.skips, it.overLimit) })
        assertTrue(record().perfect)
        // Taking the tick back doesn't bring the skip back: the day is simply not done yet.
        assertTrue(book.undoHabitTick(run.id))
        assertTrue(store.lapses.isEmpty())
        assertEquals(0, book.balance())
    }

    @Test
    fun aCountedGoalKeepsItsSkipUntilItIsDoneAfterAll() = runTest {
        val water = habit(points = 1, allowance = 1, target = 3, name = "Water")
        book.tickHabit(water.id)
        lapses.skip(water.id, null, null)
        book.tickHabit(water.id) // 2 of 3: the skip still covers the rest
        assertEquals(1, store.lapses.size)
        book.tickHabit(water.id) // 3 of 3: done after all
        assertTrue(store.lapses.isEmpty())
    }

    @Test
    fun aRoutineOrAnAmountDoneAfterAllTakesItsSkipBackToo() = runTest {
        val read = store.addGoal(
            Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(start), amount = 20.0, unit = "pages", allowance = 1),
        )
        lapses.skip(read.id, null, null)
        book.logAmount(read.id, 8.0)
        assertEquals(1, store.lapses.size)
        book.logAmount(read.id, 12.0)
        assertTrue(store.lapses.isEmpty())

        val morning = habit(allowance = 1, name = "Morning")
        val bed = store.addStep(Step(goalId = morning.id, name = "Make bed", points = 0, position = 0))
        val stretch = store.addStep(Step(goalId = morning.id, name = "Stretch", points = 0, position = 1))
        lapses.skip(morning.id, null, null)
        book.checkItem(bed.id)
        assertEquals(1, store.lapses.size)
        book.checkItem(stretch.id)
        assertTrue(store.lapses.isEmpty())
    }

    @Test
    fun aTimesPerGoalLosesTodaysSkipOnlyWhenATickMakesItSpare() = runTest {
        val gym = habit(allowance = 5, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 3)
        at(9) // Monday
        book.tickHabit(gym.id)
        at(10) // Tuesday: skipped one, then did one. One done on Monday, one skipped, one done: the skip still covers a time.
        lapses.skip(gym.id, null, null)
        book.tickHabit(gym.id)
        assertEquals(1, store.lapses.size)
        // Then did the third as well, the same day: three done, so the skip covers nothing and goes.
        book.tickHabit(gym.id)
        assertTrue(store.lapses.isEmpty())
    }

    @Test
    fun aSkipFromADayThatHasEndedStaysWhenTheGoalIsDoneLater() = runTest {
        val gym = habit(allowance = 5, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 2)
        at(9) // Monday
        lapses.skip(gym.id, null, null)
        at(10) // Tuesday
        book.tickHabit(gym.id)
        book.tickHabit(gym.id)
        // Done twice after all: done, and Monday's skip is still Monday's skip.
        assertEquals(listOf(LocalDate(2026, 11, 9)), store.lapses.map { it.day })
        val sunday = LocalDate(2026, 11, 15)
        assertEquals(1 to 1, record(sunday).let { it.due to it.done })
    }

    /** A dated to-do for today. To-dos share one free skip or miss a week between them. */
    private fun todo(name: String) =
        store.addGoal(Goal(kind = GoalKind.TODO, name = name, icon = "📝", points = 4, repeat = Repeat(RepeatKind.ONCE, wednesday)))

    @Test
    fun aLaterSkipThatOnlyWentOverBecauseOfItIsWithinTheAllowanceAgain() = runTest {
        val call = todo("Call the bank")
        val post = todo("Post the letter")
        lapses.skip(call.id, null, null) // the free one
        val second = lapses.skip(post.id, Reason.NO_TIME, null) as SkipResult.Skipped // over the limit: 2 points and a penalty task
        assertEquals(-2, book.balance())
        assertEquals(1, record().overLimit)

        // Without the first skip, the second would have been the free one.
        assertEquals(0, lapses.takeBackSkip(call.id)?.refunded)

        val left = store.lapses.single()
        assertEquals(listOf(post.id, false, 0), listOf(left.goalId, left.overLimit, left.cost))
        assertEquals(0, book.balance())
        assertEquals(listOf(-2, 2), store.entries.map { it.amount })
        assertTrue(store.entries.last().isSkipTakenBack)
        assertNull(lapses.openPenalty())
        assertNotNull(store.penalty(second.penaltyId!!)?.cancelledAt)
        assertEquals(0, record().overLimit) // the day is back within the limit
        // The allowance is used by the one skip that is left, so another would go over again.
        assertEquals(SkipCheck.Allowed(used = 1, allowed = 1, overLimit = true, cost = 2), lapses.checkSkip(call.id))
        // Taking that one back as well gives nothing back twice.
        assertEquals(0, lapses.takeBackSkip(post.id)?.refunded)
        assertEquals(0, book.balance())
        assertTrue(store.lapses.isEmpty())
    }

    @Test
    fun aLaterSkipThatWouldHaveGoneOverAnywayStaysOverTheLimit() = runTest {
        val gym = habit(points = 10, allowance = 1, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 4)
        val first = lapses.skip(gym.id, null, null) as SkipResult.Skipped // the free one
        lapses.skip(gym.id, Reason.NO_TIME, null) // over the limit
        lapses.skip(gym.id, Reason.NO_TIME, null) // over the limit
        assertEquals(-10, book.balance())

        // "Undo" on the first: the second takes the free place, the third is over the limit as before.
        assertNotNull(lapses.takeBackSkipById(first.lapse.id))

        assertEquals(listOf(false, true), store.lapses.map { it.overLimit })
        assertEquals(listOf(0, 5), store.lapses.map { it.cost })
        assertEquals(-5, book.balance())
        assertEquals(1, store.openPenalties().size)
    }

    @Test
    fun aGoalDoneAfterAllFreesItsAllowanceForALaterSkipToo() = runTest {
        val call = todo("Call the bank")
        val post = todo("Post the letter")
        lapses.skip(call.id, null, null)
        lapses.skip(post.id, Reason.NO_TIME, null)

        book.tickHabit(call.id) // done after all: its skip is taken back by itself

        assertEquals(listOf(post.id to false), store.lapses.map { it.goalId to it.overLimit })
        assertEquals(4, book.balance()) // what the to-do pays; the other one's penalty came back
        assertNull(lapses.openPenalty())
    }

    @Test
    fun aSkipTakenBackIsNoPointLossForAGap() = runTest {
        store.putSetting(Gaps.SETTING_DAYS, "30") // only the point loss can trigger it here
        val run = habit(points = 200, allowance = 0) // 100 off for a skip over the limit
        lapses.closeDays()
        lapses.skip(run.id, Reason.NO_TIME, null)
        lapses.takeBackSkip(run.id)
        book.tickHabit(run.id)
        at(12)
        lapses.closeDays()
        assertTrue(gaps.find().isEmpty())

        // Left as it was, the same skip is a big point loss.
        book.tickHabit(run.id)
        at(13)
        lapses.skip(run.id, Reason.NO_TIME, null)
        at(14)
        lapses.closeDays()
        assertEquals(GapKind.POINT_LOSS, gaps.find().single().kind)
    }

    @Test
    fun aSkippedGoalTakenBackIsMissedLikeAnyOtherIfItIsLeftUndone() = runTest {
        val run = habit(allowance = 1)
        lapses.closeDays()
        lapses.skip(run.id, null, null)
        lapses.takeBackSkip(run.id)
        at(12)
        assertEquals(listOf(LapseKind.MISS), lapses.closeDays().map { it.kind })
    }
}
