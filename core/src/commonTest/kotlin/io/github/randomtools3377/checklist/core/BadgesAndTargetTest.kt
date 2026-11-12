package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BadgesAndTargetTest {
    private val start = LocalDate(2026, 1, 1)
    private val monday = LocalDate(2026, 11, 2)
    private val run = Goal(id = 1, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 10, repeat = Repeat.everyDay(start))

    private var nextId = 1L
    private fun tick(day: LocalDate, goalId: Long = 1, stepId: Long? = null) =
        Tick(id = nextId++, goalId = goalId, stepId = stepId, at = nextId, day = day, period = if (stepId == null) Period.DAILY else null, periodStart = day)
    private fun entry(kind: EntryKind, amount: Int, day: LocalDate) = Entry(at = nextId++, day = day, kind = kind, amount = amount, goalId = 1, label = "x")

    private fun records(from: LocalDate, to: LocalDate, ticks: List<Tick>, goals: List<Goal> = listOf(run)) =
        StreakCounter.records(from, to, goals, ticks, emptyList())

    @Test
    fun theWeeksTargetCountsPointsEarnedSinceMonday() {
        val entries = listOf(
            entry(EntryKind.TICK, 30, monday.minusDays(1)), // last week
            entry(EntryKind.TICK, 20, monday),
            entry(EntryKind.BONUS, 15, monday.plusDays(2)),
            entry(EntryKind.PURCHASE, -40, monday.plusDays(2)), // spending doesn't count against it
            entry(EntryKind.PENALTY, -5, monday.plusDays(2)),
        )
        val progress = WeeklyTarget.progress(entries, monday.plusDays(3), target = 50)
        assertEquals(35, progress.earned)
        assertEquals(15, progress.left)
        assertFalse(progress.reached)
        assertEquals("This week: 35 of 50 · 15 to go", progress.describe())
        assertTrue(WeeklyTarget.progress(entries, monday.plusDays(3), target = 35).reached)
        assertFalse(WeeklyTarget.progress(entries, monday, target = 0).reached) // no target set
    }

    @Test
    fun aWeekOfTicksEarnsItsBadgesOnTheRightDays() {
        val sunday = monday.plusDays(6)
        val ticks = (0..6).map { tick(monday.plusDays(it)) }
        val entries = (0..6).map { entry(EntryKind.TICK, 20, monday.plusDays(it)) }
        val badges = Badges.earned(
            today = sunday, records = records(monday, sunday, ticks), streak = StreakSettings(),
            goals = listOf(run), steps = emptyList(), ticks = ticks, entries = entries, weeklyTarget = 100,
        ).associate { it.badge to it.on }

        assertEquals(monday, badges[Badge.FIRST_TICK])
        assertEquals(monday, badges[Badge.PERFECT_DAY])
        assertEquals(sunday, badges[Badge.PERFECT_WEEK])
        assertEquals(sunday, badges[Badge.STREAK_7])
        assertEquals(monday.plusDays(4), badges[Badge.POINTS_100]) // the 5th day's 20 makes 100
        assertEquals(monday.plusDays(4), badges[Badge.WEEKLY_TARGET])
        assertNull(badges[Badge.STREAK_30])
        assertNull(badges[Badge.TICKS_100])
        assertNull(badges[Badge.WEEKLY_TARGET_4])
        assertNull(badges[Badge.FIRST_REWARD])
    }

    @Test
    fun aMissedDayRestartsTheStreakButTodayCannotBreakIt() {
        val days = (0..9).map { monday.plusDays(it) }
        // Done every day but the 3rd; today (the 10th) isn't done yet.
        val ticks = days.filterIndexed { i, _ -> i != 2 && i != 9 }.map { tick(it) }
        val badges = Badges.earned(
            today = days.last(), records = records(days.first(), days.last(), ticks), streak = StreakSettings(),
            goals = listOf(run), steps = emptyList(), ticks = ticks, entries = emptyList(), weeklyTarget = 0,
        ).map { it.badge }
        assertFalse(Badge.STREAK_7 in badges) // 2, then 6 in a row, with today still open
        assertFalse(Badge.PERFECT_WEEK in badges)
        assertFalse(Badge.WEEKLY_TARGET in badges) // no target set

        val oneMore = ticks + tick(days.last())
        val later = Badges.earned(
            today = days.last(), records = records(days.first(), days.last(), oneMore), streak = StreakSettings(),
            goals = listOf(run), steps = emptyList(), ticks = oneMore, entries = emptyList(), weeklyTarget = 0,
        ).associate { it.badge to it.on }
        assertEquals(days.last(), later[Badge.STREAK_7])
    }

    @Test
    fun aFinishedProjectARewardAndFourWeeksOnTarget() {
        val project = Goal(id = 2, kind = GoalKind.PROJECT, name = "Shed", icon = "🛖", points = 0, repeat = null)
        val steps = listOf(Step(id = 10, goalId = 2, name = "Plan", points = 5, position = 0), Step(id = 11, goalId = 2, name = "Build", points = 5, position = 1))
        val ticks = listOf(tick(monday, goalId = 2, stepId = 10), tick(monday.plusDays(3), goalId = 2, stepId = 11))
        val entries = (0..3).map { entry(EntryKind.TICK, 60, monday.plusDays(7 * it)) } +
            listOf(Entry(at = 0, day = monday.plusDays(1), kind = EntryKind.PURCHASE, amount = -30, rewardId = 1, label = "Film"))
        val today = monday.plusDays(25)
        val badges = Badges.earned(
            today = today, records = emptyList(), streak = StreakSettings(),
            goals = listOf(project), steps = steps, ticks = ticks, entries = entries, weeklyTarget = 50,
        ).associate { it.badge to it.on }
        assertEquals(monday.plusDays(3), badges[Badge.FIRST_PROJECT])
        assertEquals(monday.plusDays(1), badges[Badge.FIRST_REWARD])
        assertEquals(monday, badges[Badge.WEEKLY_TARGET])
        assertEquals(monday.plusDays(21), badges[Badge.WEEKLY_TARGET_4])
        assertEquals(monday.plusDays(7), badges[Badge.POINTS_100]) // the second 60 passes 100; spending doesn't count
    }

    @Test
    fun badgesOnceEarnedAreKeptAndStoredAsText() {
        val kept = mapOf(Badge.FIRST_TICK to monday)
        val merged = Badges.merge(kept, listOf(EarnedBadge(Badge.FIRST_TICK, monday.plusDays(5)), EarnedBadge(Badge.PERFECT_DAY, monday.plusDays(1))))
        assertEquals(monday, merged[Badge.FIRST_TICK]) // keeps its first day
        assertEquals(monday.plusDays(1), merged[Badge.PERFECT_DAY])
        val text = Badges.encode(merged)
        assertEquals("FIRST_TICK=2026-11-02,PERFECT_DAY=2026-11-03", text)
        assertEquals(merged, Badges.decode(text))
        assertTrue(Badges.decode("NOT_A_BADGE=2026-01-01,FIRST_TICK=junk,").isEmpty())
        assertTrue(Badges.decode(null).isEmpty())
    }

    private fun moneyDay(day: LocalDate, label: String = MoneyGame.LABEL_WITHIN) = Entry(at = nextId++, day = day, kind = EntryKind.BONUS, amount = 3, label = label)
    private fun earnedWith(on: Set<Feature>, today: LocalDate, entries: List<Entry> = emptyList(), moods: List<Mood> = emptyList(), money: List<MoneyEntry> = emptyList(), saving: List<SavingGoal> = emptyList()) =
        Badges.earned(today, emptyList(), StreakSettings(), emptyList(), emptyList(), emptyList(), entries, 0, on = on, moods = moods, money = money, savingGoals = saving)
            .associate { it.badge to it.on }

    @Test
    fun theFirstSavingGoalReachedEarnsItsBadgeOnTheDayItWasReached() {
        val holiday = SavingGoal(id = 1, name = "Holiday", icon = "🏖️", targetCents = 10_000)
        val bike = SavingGoal(id = 2, name = "Bike", icon = "🚲", targetCents = 50_000, archived = true)
        val money = listOf(
            MoneyEntry(id = 1, kind = MoneyKind.SAVED, cents = 6_000, day = monday, savingId = 1),
            MoneyEntry(id = 2, kind = MoneyKind.TAKEN_OUT, cents = 2_000, day = monday.plusDays(1), savingId = 1),
            MoneyEntry(id = 3, kind = MoneyKind.SAVED, cents = 5_000, day = monday.plusDays(2), savingId = 1), // 9,000: not yet
            MoneyEntry(id = 4, kind = MoneyKind.SAVED, cents = 1_000, day = monday.plusDays(4), savingId = 1),
            MoneyEntry(id = 5, kind = MoneyKind.SPENT, cents = 90_000, day = monday), // plain spending isn't saving
        )
        val budget = setOf(Feature.BUDGET)
        assertEquals(monday.plusDays(4), earnedWith(budget, monday.plusDays(9), money = money, saving = listOf(holiday, bike))[Badge.SAVED_UP])
        // Not reached by the day before, and not looked for while Budget is off.
        assertNull(earnedWith(budget, monday.plusDays(3), money = money, saving = listOf(holiday, bike))[Badge.SAVED_UP])
        assertNull(earnedWith(emptySet(), monday.plusDays(9), money = money, saving = listOf(holiday, bike))[Badge.SAVED_UP])
        // A goal removed since was still reached.
        val bikeMoney = listOf(MoneyEntry(id = 6, kind = MoneyKind.SAVED, cents = 50_000, day = monday, savingId = 2))
        assertEquals(monday, earnedWith(budget, monday, money = bikeMoney, saving = listOf(bike))[Badge.SAVED_UP])
    }

    @Test
    fun moneyStreaksAndAMonthWithinEveryLimitComeFromWhatTheBudgetGamePaid() {
        val game = setOf(Feature.BUDGET, Feature.BUDGET_GAME)
        // Six good days, a day over its share (nothing paid), then thirty in a row.
        val entries = (0..5).map { moneyDay(monday.plusDays(it)) } +
            (7..36).map { moneyDay(monday.plusDays(it), if (it % 2 == 0) MoneyGame.LABEL_NO_SPEND else MoneyGame.LABEL_WITHIN) } +
            moneyDay(monday.plusDays(28), MoneyGame.LABEL_MONTH) +
            Entry(at = 0, day = monday.plusDays(6), kind = EntryKind.BONUS, amount = 5, label = "Quest: tick 2 goals")
        val all = earnedWith(game, monday.plusDays(40), entries)
        assertEquals(monday.plusDays(13), all[Badge.MONEY_STREAK_7])
        assertEquals(monday.plusDays(36), all[Badge.MONEY_STREAK_30])
        assertEquals(monday.plusDays(28), all[Badge.MONEY_MONTH])
        // Up to the sixth day: no streak of 7 yet.
        assertNull(earnedWith(game, monday.plusDays(12), entries)[Badge.MONEY_STREAK_7])
        // With Budget on but its game off, none of the game's badges.
        val budgetOnly = earnedWith(setOf(Feature.BUDGET), monday.plusDays(40), entries)
        assertNull(budgetOnly[Badge.MONEY_STREAK_7])
        assertNull(budgetOnly[Badge.MONEY_MONTH])
    }

    @Test
    fun thirtyDaysOfMoodCheckInsEarnABadgeOnTheThirtiethDay() {
        // Every other day: 30 days in all, not in a row.
        val moods = (0..29).map { Mood(day = monday.plusDays(it * 2), score = 3) }
        assertEquals(monday.plusDays(58), earnedWith(setOf(Feature.MOOD), monday.plusDays(70), moods = moods)[Badge.MOOD_30])
        assertNull(earnedWith(setOf(Feature.MOOD), monday.plusDays(57), moods = moods)[Badge.MOOD_30])
        assertNull(earnedWith(emptySet(), monday.plusDays(70), moods = moods)[Badge.MOOD_30])
    }

    @Test
    fun theBudgetsAndTheMoodCheckInsBadgesShowOnlyWhileTheirExtrasAreOn() {
        val plain = Badge.shown(emptySet())
        assertTrue(listOf(Badge.SAVED_UP, Badge.MONEY_STREAK_7, Badge.MONEY_STREAK_30, Badge.MONEY_MONTH, Badge.MOOD_30, Badge.LEVEL_10).none { it in plain })
        assertEquals(plain, Badge.shown(character = false))
        assertEquals(plain + Badge.SAVED_UP, Badge.shown(setOf(Feature.BUDGET)))
        // The budget game alone, without Budget, shows none of them.
        assertEquals(plain, Badge.shown(setOf(Feature.BUDGET_GAME)))
        assertEquals(
            plain + listOf(Badge.SAVED_UP, Badge.MONEY_STREAK_7, Badge.MONEY_STREAK_30, Badge.MONEY_MONTH),
            Badge.shown(setOf(Feature.BUDGET, Feature.BUDGET_GAME)),
        )
        assertEquals(plain + Badge.MOOD_30, Badge.shown(setOf(Feature.MOOD)))
        // A new badge kept in the settings reads back.
        val kept = mapOf(Badge.SAVED_UP to monday, Badge.MOOD_30 to monday.plusDays(1))
        assertEquals(kept, Badges.decode(Badges.encode(kept)))
    }
}
