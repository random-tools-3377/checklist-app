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
}
