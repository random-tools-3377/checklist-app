package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The spreadsheets beside the points history: the goal record, the mood journal and a month of the budget. */
class RecordsTest {
    private val start = LocalDate(2026, 11, 1)
    private val today = LocalDate(2026, 11, 12)
    private val read = Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(start), position = 0)

    private var nextId = 1L
    private fun tick(day: LocalDate, goalId: Long = 1, stepId: Long? = null, note: String? = null) =
        Tick(id = nextId++, goalId = goalId, stepId = stepId, at = nextId, day = day, period = if (stepId == null) Period.DAILY else null, periodStart = day, note = note)
    private fun lapse(kind: LapseKind, day: LocalDate, goalId: Long = 1, reason: Reason? = null, note: String? = null, reasonText: String? = null, period: Period? = Period.DAILY) =
        Lapse(id = nextId++, goalId = goalId, kind = kind, day = day, period = period, periodStart = day, at = nextId, overLimit = false, reason = reason, note = note, reasonText = reasonText, label = "x")

    private fun lines(csv: String) = csv.trimEnd().split("\r\n")

    @Test
    fun theGoalRecordHasEachGoalEachDayWithTheReason() {
        val from = today.minusDays(4)
        val shed = Goal(id = 2, kind = GoalKind.PROJECT, name = "Paint the shed", icon = "🎨", points = 0, repeat = null, position = 1)
        val gone = Goal(id = 3, kind = GoalKind.HABIT, name = "Removed", icon = "🗑️", points = 5, repeat = Repeat.everyDay(start), archived = true, position = 2)
        val steps = listOf(Step(id = 21, goalId = 2, name = "Sand, then prime", points = 5, position = 0))
        val ticks = listOf(
            tick(today.minusDays(4), note = "chapter 3"), tick(today.minusDays(1)),
            tick(today.minusDays(3), goalId = 2, stepId = 21), tick(today.minusDays(4), goalId = 3),
        )
        val lapses = listOf(
            lapse(LapseKind.MISS, today.minusDays(3), reason = Reason.NO_TIME, note = "late home"),
            lapse(LapseKind.SKIP, today.minusDays(2)),
            lapse(LapseKind.MISS, today.minusDays(2), goalId = 2, reason = Reason.OTHER, reasonText = "=rain", period = null),
        )
        val csv = GoalRecord.csv(listOf(read, shed, gone), steps, ticks, lapses, today) { from }
        assertEquals(
            listOf(
                "Date,Goal,Result,Reason,Note,Step",
                "2026-11-08,Read,Done,,chapter 3,",
                "2026-11-09,Read,Missed,No time,late home,",
                // A cell with a comma is quoted.
                "2026-11-09,Paint the shed,Step done,,,\"Sand, then prime\"",
                // A skip needs no reason; words that start like a formula get a ' in front.
                "2026-11-10,Read,Skipped,,,",
                "2026-11-10,Paint the shed,Missed,'=rain,,",
                "2026-11-11,Read,Done,,,",
                // Today isn't over: it shows once it is done.
            ),
            lines(csv),
        )
        assertTrue(csv.endsWith("\r\n"))
        val done = GoalRecord.csv(listOf(read), steps, ticks + tick(today), lapses, today) { from }
        assertEquals("2026-11-12,Read,Done,,,", lines(done).last())
    }

    @Test
    fun aDayGoneByWithNothingRecordedIsNotDone() {
        val csv = GoalRecord.csv(listOf(read), emptyList(), listOf(tick(today.minusDays(1))), emptyList(), today) { today.minusDays(2) }
        assertEquals(listOf(GoalRecord.HEADER, "2026-11-10,Read,Not done,,,", "2026-11-11,Read,Done,,,"), lines(csv))
    }

    @Test
    fun aToDoWithoutADateIsDoneOnTheDayItWasTicked() {
        val milk = Goal(id = 4, kind = GoalKind.TODO, name = "Buy milk", icon = "🥛", points = 2, repeat = null)
        val ticked = Tick(id = 1, goalId = 4, stepId = null, at = 1, day = today.minusDays(1), period = null, periodStart = today.minusDays(1), note = "+2 for oat")
        val csv = GoalRecord.csv(listOf(milk), emptyList(), listOf(ticked), emptyList(), today)
        assertEquals(listOf(GoalRecord.HEADER, "2026-11-11,Buy milk,Done,,'+2 for oat,"), lines(csv))
    }

    @Test
    fun aWeekStillGoingListsItsTicksAndAWeekEndedIsDoneOrNot() {
        val monday = LocalDate(2026, 11, 9)
        val swim = Goal(id = 5, kind = GoalKind.HABIT, name = "Swim", icon = "🏊", points = 5, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 2)
        fun swam(day: LocalDate, week: LocalDate) = Tick(id = nextId++, goalId = 5, stepId = null, at = nextId, day = day, period = Period.WEEKLY, periodStart = week)
        val ticks = listOf(swam(monday.minusDays(6), monday.minusDays(7)), swam(monday.minusDays(3), monday.minusDays(7)), swam(monday.plusDays(1), monday))
        val csv = GoalRecord.csv(listOf(swim), emptyList(), ticks, emptyList(), today) { monday.minusDays(7) }
        assertEquals(listOf(GoalRecord.HEADER, "2026-11-08,Swim,Done,,,", "2026-11-10,Swim,Ticked,,,"), lines(csv))
    }

    @Test
    fun theMoodJournalHasADayOnEachLine() {
        val moods = listOf(
            Mood(day = today, score = 2, note = "slept \"badly\"", after = 4, afterNote = "-better after a walk", factors = listOf("Slept badly", "Outdoors")),
            Mood(day = today.minusDays(1), score = 5),
        )
        assertEquals(
            listOf(
                MoodSheet.HEADER,
                "2026-11-11,5,,,,",
                "2026-11-12,2,4,Slept badly; Outdoors,\"slept \"\"badly\"\"\",'-better after a walk",
            ),
            lines(MoodSheet.csv(moods)),
        )
        assertEquals(listOf(MoodSheet.HEADER), lines(MoodSheet.csv(emptyList())))
    }

    @Test
    fun aMonthOfTheBudgetHasSpendingIncomeBillsAndSavings() {
        val first = LocalDate(2026, 11, 1)
        val categories = listOf(MoneyCategory(id = 1, name = "Food", icon = "🍎"), MoneyCategory(id = 2, name = "Bills", icon = "🧾"))
        val entries = listOf(
            MoneyEntry(id = 1, kind = MoneyKind.SPENT, cents = 1250, categoryId = 1, note = "lunch, with a friend", day = LocalDate(2026, 11, 3), at = 2),
            MoneyEntry(id = 2, kind = MoneyKind.SPENT, cents = 4000, categoryId = 2, note = "Internet", day = LocalDate(2026, 11, 3), at = 1, billId = 9),
            MoneyEntry(id = 3, kind = MoneyKind.RECEIVED, cents = 2000, note = "@gift", day = LocalDate(2026, 11, 5)),
            MoneyEntry(id = 4, kind = MoneyKind.SAVED, cents = 5005, note = "Holiday", day = LocalDate(2026, 11, 6), savingId = 1),
            MoneyEntry(id = 5, kind = MoneyKind.TAKEN_OUT, cents = 500, note = "Holiday", day = LocalDate(2026, 11, 7), savingId = 1),
            MoneyEntry(id = 6, kind = MoneyKind.SPENT, cents = 999, categoryId = 1, day = LocalDate(2026, 10, 31)), // last month
            MoneyEntry(id = 7, kind = MoneyKind.SPENT, cents = 999, categoryId = 1, day = LocalDate(2026, 12, 1)), // next month
        )
        val now = LocalDateTime(2026, 11, 12, 15, 0)
        val csv = BudgetSheet.csv(first, entries, categories, MonthPay(earned = 812.345, total = 2000.0), now)
        assertEquals(
            listOf(
                "Date,What,Category,Amount,Note",
                "2026-11-03,Bill,Bills,-40.00,Internet",
                "2026-11-03,Spent,Food,-12.50,\"lunch, with a friend\"",
                "2026-11-05,Money in,,20.00,'@gift",
                "2026-11-06,Put into savings,,-50.05,Holiday",
                "2026-11-07,Taken out of savings,,5.00,Holiday",
                "2026-11-12,Pay so far,,812.35,",
            ),
            lines(csv),
        )
        // A month gone by: its whole pay, on its last day. Without pay set, no pay line.
        val past = BudgetSheet.csv(first, entries, categories, MonthPay(earned = 2000.0, total = 2000.0), LocalDateTime(2026, 12, 3, 9, 0))
        assertEquals("2026-11-30,Pay,,2000.00,", lines(past).last())
        assertEquals(6, lines(BudgetSheet.csv(first, entries, categories, null, now)).size)
        assertEquals("-0.05", BudgetSheet.amount(-5))
        assertEquals("0.00", BudgetSheet.amount(0))
    }
}
