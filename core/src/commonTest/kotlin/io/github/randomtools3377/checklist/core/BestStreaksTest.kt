package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The best run of each streak, ever and within a stretch, read off the streaks' own day records. */
class BestStreaksTest {
    private val first = LocalDate(2026, 9, 1)
    private val settings = StreakSettings()

    private fun record(day: Int, due: Int, done: Int, misses: Int = 0, skips: Int = 0, overLimit: Int = 0) =
        DayRecord(first.plusDays(day), due, done, misses, skips, overLimit)

    /** A day with everything done, and a day with nothing done and a miss. */
    private fun good(day: Int) = record(day, due = 2, done = 2)
    private fun bad(day: Int) = record(day, due = 2, done = 0, misses = 1)

    /** By definition: the highest each streak ever stood at, counted as it stood at the end of every day. */
    private fun highestEver(records: List<DayRecord>, settings: StreakSettings): Streaks {
        val all = records.map { record -> StreakCounter.count(records.filter { it.day <= record.day }, record.day.plusDays(1), settings) }
        return Streaks(
            done = all.maxOfOrNull { it.done } ?: 0,
            noMisses = all.maxOfOrNull { it.noMisses } ?: 0,
            noSkips = all.maxOfOrNull { it.noSkips } ?: 0,
            withinLimit = all.maxOfOrNull { it.withinLimit } ?: 0,
            perfect = all.maxOfOrNull { it.perfect } ?: 0,
        )
    }

    @Test
    fun theBestRunIsTheLongestOneNotTheLatest() {
        // Five good days, a miss, then two good days: the best is 5 while the current one is 2.
        val records = (0..4).map { good(it) } + bad(5) + listOf(good(6), good(7))
        val best = BestStreaks.of(records, settings)
        assertEquals(5, best.done)
        assertEquals(5, best.perfect)
        assertEquals(5, best.noMisses)
        assertEquals(8, best.noSkips)
        assertEquals(8, best.withinLimit)
        val now = StreakCounter.count(records, first.plusDays(7), settings)
        assertEquals(2, now.done)
        assertEquals(2, now.noMisses)
    }

    @Test
    fun daysWithNothingDueNeitherCountNorBreak() {
        val records = listOf(good(0), good(1), record(2, 0, 0), record(3, 0, 0), good(4), bad(5), good(6))
        val best = BestStreaks.of(records, settings)
        assertEquals(3, best.done)
        assertEquals(3, best.perfect)
        assertEquals(3, best.noMisses)
        assertEquals(5, best.noSkips, "the two days with nothing due don't add to it")
    }

    @Test
    fun eachStreakHasItsOwnBestRun() {
        val records = listOf(
            good(0),
            good(1),
            good(2),
            record(3, 10, 9), // nine of ten: the main streak at 90% goes on, the perfect days end
            record(4, 2, 2, skips = 1), // a skip: the skip-free run ends
            good(5),
            record(6, 2, 0, misses = 1, overLimit = 1), // a miss over the limit ends the rest
            good(7),
            good(8),
        )
        val best = BestStreaks.of(records, settings)
        assertEquals(6, best.done)
        assertEquals(6, best.noMisses)
        assertEquals(4, best.noSkips)
        assertEquals(6, best.withinLimit)
        assertEquals(3, best.perfect)
        // The main streak follows the user's own target: at 100%, nine of ten ends it.
        assertEquals(3, BestStreaks.of(records, StreakSettings(StreakTarget.PERCENT, 100)).done)
    }

    @Test
    fun theBestIsNeverLessThanTheCurrentStreakWhileTodayIsStillGoing() {
        // Today has nothing done yet: it doesn't break the streak, and the best keeps the days before it.
        val today = first.plusDays(3)
        val records = listOf(good(0), good(1), good(2), record(3, 2, 0))
        val now = StreakCounter.count(records, today, settings)
        val best = BestStreaks.of(records, settings)
        assertEquals(3, now.done)
        assertEquals(3, best.done)
        assertEquals(3, best.perfect)
        // Once today qualifies it counts.
        assertEquals(4, BestStreaks.of(records.dropLast(1) + good(3), settings).done)
    }

    @Test
    fun withinAStretchOnlyItsOwnDaysCount() {
        // Good from day 0 to day 9, a miss on day 10, good again to day 20.
        val records = (0..20).map { if (it == 10) bad(it) else good(it) }
        assertEquals(10, BestStreaks.of(records, settings).done)
        // The week of days 7 to 13: three good days, the miss, three good days.
        val week = records.filter { it.day >= first.plusDays(7) && it.day <= first.plusDays(13) }
        assertEquals(3, BestStreaks.of(week, settings).done)
        assertEquals(3, BestStreaks.of(week, settings).noMisses)
        assertEquals(7, BestStreaks.of(week, settings).noSkips)
        // A stretch with every day kept is as long as its days, whatever came before it.
        val after = records.filter { it.day >= first.plusDays(14) }
        assertEquals(7, BestStreaks.of(after, settings).done)
    }

    @Test
    fun nothingToGoOnIsNoStreak() {
        assertEquals(Streaks(0, 0, 0, 0, 0), BestStreaks.of(emptyList(), settings))
        assertEquals(Streaks(0, 0, 0, 0, 0), BestStreaks.of(listOf(record(0, 0, 0), record(1, 0, 0)), settings))
        assertEquals(0, BestStreaks.of(listOf(bad(0)), settings).done)
    }

    @Test
    fun theOrderTheRecordsComeInDoesNotMatter() {
        val records = (0..4).map { good(it) } + bad(5) + listOf(good(6), good(7))
        assertEquals(BestStreaks.of(records, settings), BestStreaks.of(records.shuffled(Random(3)), settings))
    }

    @Test
    fun itAgreesWithTheStreaksThemselvesOnAnyHistory() {
        // Whatever the days hold, the best run is the highest the streak itself ever stood at.
        val random = Random(64)
        val targets = listOf(StreakSettings(), StreakSettings(StreakTarget.PERCENT, 50), StreakSettings(StreakTarget.COUNT, 2))
        repeat(60) { round ->
            val records = (0 until 45).map { day ->
                val due = random.nextInt(0, 4)
                val done = if (due == 0) 0 else random.nextInt(0, due + 1)
                val misses = if (random.nextInt(5) == 0) 1 else 0
                val skips = if (random.nextInt(6) == 0) 1 else 0
                val overLimit = if ((misses + skips) > 0 && random.nextInt(3) == 0) 1 else 0
                record(day, due, done, misses, skips, overLimit)
            }
            val target = targets[round % targets.size]
            assertEquals(highestEver(records, target), BestStreaks.of(records, target), "round $round")
        }
    }

    @Test
    fun itReadsTheSameRecordsAsTheStreaks() {
        // From goals, ticks and skips, through the streaks' own records.
        val start = LocalDate(2026, 1, 1)
        val read = Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 1, repeat = Repeat.everyDay(start))
        val days = (0..9).map { first.plusDays(it) }
        // Ticked every day but the fifth.
        val ticks = days.filterIndexed { i, _ -> i != 4 }.map { Tick(goalId = 1, stepId = null, at = 0, day = it, period = Period.DAILY, periodStart = it) }
        val miss = Lapse(goalId = 1, kind = LapseKind.MISS, day = days[4], period = Period.DAILY, periodStart = days[4], at = 0, overLimit = false, label = "Read")
        val records = StreakCounter.records(days.first(), days.last(), listOf(read), ticks, listOf(miss)) { null }
        val best = BestStreaks.of(records, settings)
        assertEquals(5, best.done)
        assertEquals(5, best.noMisses)
        assertEquals(10, best.noSkips)
        assertTrue(best.done >= StreakCounter.count(records, days.last(), settings).done)
    }
}
