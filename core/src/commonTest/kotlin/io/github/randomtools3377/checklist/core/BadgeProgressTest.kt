package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BadgeProgressTest {
    private val start = LocalDate(2026, 1, 1)
    private val monday = LocalDate(2026, 11, 2)
    private val run = Goal(id = 1, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 10, repeat = Repeat.everyDay(start))
    private val settings = StreakSettings()

    private var nextId = 1L
    private fun tick(day: LocalDate, goalId: Long = 1, stepId: Long? = null) =
        Tick(id = nextId++, goalId = goalId, stepId = stepId, at = nextId, day = day, period = if (stepId == null) Period.DAILY else null, periodStart = day)
    private fun entry(kind: EntryKind, amount: Int, day: LocalDate, label: String = "x") =
        Entry(at = nextId++, day = day, kind = kind, amount = amount, goalId = 1, label = label)
    private fun day(day: LocalDate, due: Int, done: Int, misses: Int = 0, skips: Int = 0) = DayRecord(day, due, done, misses, skips, 0)

    @Test
    fun ticksStreakAndPointsSayHowFarAlongTheyAre() {
        val today = monday.plusDays(4)
        val ticks = (0..4).map { tick(monday.plusDays(it)) }
        val entries = (0..4).map { entry(EntryKind.TICK, 250, monday.plusDays(it)) } + entry(EntryKind.PURCHASE, -400, today)
        val records = StreakCounter.records(monday, today, listOf(run), ticks, emptyList()) { null }
        val progress = Badges.progress(today, records, settings, listOf(run), emptyList(), ticks, entries, weeklyTarget = 0)

        assertEquals("5 of 100 ticks", progress[Badge.TICKS_100]?.text)
        assertEquals("5 of 5,000 ticks", progress[Badge.TICKS_5000]?.text)
        assertEquals("Streak 5 of 7 days", progress[Badge.STREAK_7]?.text)
        assertEquals("Streak 5 of 30 days", progress[Badge.STREAK_30]?.text)
        // Spending doesn't lower points earned; what is counted never passes what is needed.
        assertEquals("1,000 of 1,000 points earned", progress[Badge.POINTS_1000]?.text)
        assertEquals("1,250 of 10,000 points earned", progress[Badge.POINTS_10000]?.text)
        assertEquals(0.125f, progress[Badge.POINTS_10000]?.share)
        // One thing happening, no target set, extras off: nothing to count.
        assertNull(progress[Badge.FIRST_TICK])
        assertNull(progress[Badge.PERFECT_DAY])
        assertNull(progress[Badge.BOUNCED_BACK])
        assertNull(progress[Badge.WEEKLY_TARGET])
        assertNull(progress[Badge.LEVEL_10])
        assertNull(progress[Badge.MOOD_30])
        assertNull(progress[Badge.MONEY_STREAK_7])
    }

    @Test
    fun theClosestBadgeStillToEarnIsNextUp() {
        val today = monday.plusDays(4)
        val ticks = (0..4).map { tick(monday.plusDays(it)) }
        val entries = (0..4).map { entry(EntryKind.TICK, 12, monday.plusDays(it)) }
        val records = StreakCounter.records(monday, today, listOf(run), ticks, emptyList()) { null }
        val progress = Badges.progress(today, records, settings, listOf(run), emptyList(), ticks, entries, weeklyTarget = 0)
        val shown = Badge.shown(emptySet())
        // The streak is 5 of 7, ahead of 60 of 100 points and 5 of 100 ticks.
        assertEquals(Badge.STREAK_7, Badges.nextUp(shown, setOf(Badge.FIRST_TICK), progress))
        // Once it is earned, the next closest takes its place.
        assertEquals(Badge.POINTS_100, Badges.nextUp(shown, setOf(Badge.FIRST_TICK, Badge.STREAK_7), progress))
        // Nothing started yet: nothing is next up.
        val none = Badges.progress(today, emptyList(), settings, listOf(run), emptyList(), emptyList(), emptyList(), weeklyTarget = 0)
        assertNull(Badges.nextUp(shown, emptySet(), none))
        assertEquals("0 of 100 ticks", none[Badge.TICKS_100]?.text)
    }

    @Test
    fun theWeeklyTargetCountsThisWeekAndWeeksInARow() {
        // On target in the two weeks before this one; this week has 30 of 50 so far.
        val entries = listOf(
            entry(EntryKind.TICK, 60, monday.minusDays(28)), // an earlier week, with a gap after it
            entry(EntryKind.TICK, 60, monday.minusDays(14)),
            entry(EntryKind.TICK, 60, monday.minusDays(7)),
            entry(EntryKind.TICK, 30, monday),
        )
        val progress = Badges.progress(monday.plusDays(1), emptyList(), settings, emptyList(), emptyList(), emptyList(), entries, weeklyTarget = 50)
        assertEquals("30 of 50 points this week", progress[Badge.WEEKLY_TARGET]?.text)
        assertEquals("2 of 4 weeks in a row", progress[Badge.WEEKLY_TARGET_4]?.text)
        assertEquals("2 of 12 weeks in a row", progress[Badge.WEEKLY_TARGET_12]?.text)
        // A week missed in between: the run starts again.
        val later = Badges.progress(monday.plusDays(15), emptyList(), settings, emptyList(), emptyList(), emptyList(), entries, weeklyTarget = 50)
        assertEquals("0 of 4 weeks in a row", later[Badge.WEEKLY_TARGET_4]?.text)
    }

    @Test
    fun theProjectNearestDoneAndTheExtrasCountToo() {
        val project = Goal(id = 2, kind = GoalKind.PROJECT, name = "Shed", icon = "🛠", points = 5, repeat = null)
        val other = Goal(id = 3, kind = GoalKind.PROJECT, name = "Garden", icon = "🌱", points = 5, repeat = null)
        val steps = (1L..4L).map { Step(id = it, goalId = 2, name = "Step $it", points = 1, position = it.toInt()) } +
            (5L..9L).map { Step(id = it, goalId = 3, name = "Step $it", points = 1, position = it.toInt()) }
        val ticks = listOf(tick(monday, 2, 1), tick(monday, 2, 2), tick(monday, 2, 3), tick(monday, 3, 5))
        val entries = listOf(
            entry(EntryKind.TICK, 500, monday), // level 5 needs 500 at 50 a step
            entry(EntryKind.BONUS, 3, monday.minusDays(1), MoneyGame.LABEL_WITHIN),
            entry(EntryKind.BONUS, 5, monday, MoneyGame.LABEL_NO_SPEND),
        )
        val moods = (0..11).map { Mood(day = monday.minusDays(it), score = 3) }
        val saving = SavingGoal(id = 7, name = "Bike", icon = "🚲", targetCents = 20_000)
        val money = listOf(MoneyEntry(kind = MoneyKind.SAVED, cents = 8_000, day = monday, savingId = 7))
        val on = setOf(Feature.CHARACTER, Feature.BUDGET, Feature.BUDGET_GAME, Feature.MOOD)
        val progress = Badges.progress(
            monday.plusDays(1), emptyList(), settings, listOf(project, other), steps, ticks, entries, weeklyTarget = 0,
            on = on, moods = moods, money = money, savingGoals = listOf(saving),
        )
        assertEquals("3 of 4 steps of your nearest project", progress[Badge.FIRST_PROJECT]?.text)
        assertEquals("Level 5 of 10", progress[Badge.LEVEL_10]?.text)
        assertEquals("40% of the way with Bike", progress[Badge.SAVED_UP]?.text)
        assertEquals("Money streak 2 of 7 days", progress[Badge.MONEY_STREAK_7]?.text)
        assertEquals("12 of 30 check-ins", progress[Badge.MOOD_30]?.text)
        assertTrue(progress.values.all { it.have <= it.of && it.share in 0f..1f })
    }

    // Comebacks

    @Test
    fun aComebackIsADayOnTargetRightAfterOneThatFellShort() {
        val records = listOf(
            day(monday, due = 2, done = 2),
            day(monday.plusDays(1), due = 2, done = 0, misses = 2), // fell short
            day(monday.plusDays(2), due = 2, done = 2), // a comeback
            day(monday.plusDays(3), due = 2, done = 2), // just a good day
            day(monday.plusDays(4), due = 2, done = 1, misses = 1), // fell short
            day(monday.plusDays(5), due = 0, done = 0), // nothing due: passed over
            day(monday.plusDays(6), due = 1, done = 0, skips = 0).copy(due = 0, skips = 1), // everything skipped: passed over
            day(monday.plusDays(7), due = 2, done = 2), // a comeback
        )
        val today = monday.plusDays(7)
        assertEquals(listOf(monday.plusDays(2), today), Comebacks.days(records, today, settings, since = monday))
        // Only from the day tracking began: the first hard day is before it, so only the second counts.
        assertEquals(listOf(today), Comebacks.days(records, today, settings, since = monday.plusDays(2)))
        assertEquals(emptyList(), Comebacks.days(records, today, settings, since = null))
    }

    @Test
    fun aComebackFollowsTheMainStreaksTargetAndTodayStillGoingIsLeftAlone() {
        val records = listOf(
            day(monday, due = 4, done = 1, misses = 3),
            day(monday.plusDays(1), due = 4, done = 3, misses = 1), // 75%: on target at 75, short at 90
            day(monday.plusDays(2), due = 4, done = 0), // today, still going
        )
        val today = monday.plusDays(2)
        assertEquals(listOf(monday.plusDays(1)), Comebacks.days(records, today, StreakSettings(StreakTarget.PERCENT, 75), monday))
        assertEquals(emptyList(), Comebacks.days(records, today, settings, monday))
        // Today reaching the target after two short days is one comeback, not two.
        val done = records.dropLast(1) + day(today, due = 4, done = 4)
        assertEquals(listOf(today), Comebacks.days(done, today, settings, monday))
    }

    @Test
    fun theBouncedBackBadgeIsEarnedOnTheFirstComeback() {
        val records = listOf(
            day(monday, due = 1, done = 0, misses = 1),
            day(monday.plusDays(1), due = 1, done = 1),
        )
        fun earned(since: LocalDate?) = Badges.earned(
            monday.plusDays(1), records, settings, listOf(run), emptyList(), emptyList(), emptyList(), 0, trackingSince = since,
        ).associate { it.badge to it.on }
        assertEquals(monday.plusDays(1), earned(monday)[Badge.BOUNCED_BACK])
        assertNull(earned(null)[Badge.BOUNCED_BACK])
        assertEquals(monday, Comebacks.since(monday.toString()))
        assertNull(Comebacks.since("soon"))
        assertNull(Comebacks.since(null))
    }
}
