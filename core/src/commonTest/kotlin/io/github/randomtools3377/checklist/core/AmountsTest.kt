package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountsTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0))
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val start = LocalDate(2026, 1, 1)

    private fun reading(minutes: Int? = null) = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(start), amount = 20.0, unit = "pages", minutes = minutes),
    )

    @Test
    fun amountsAddUpAndPayWhenTheGoalIsReached() = runTest {
        val read = reading()
        val first = book.logAmount(read.id, 12.0, seconds = 20 * 60) as TickResult.Ticked
        assertEquals(0, first.points)
        assertFalse(first.finished)
        assertEquals(0, book.balance())

        val second = book.logAmount(read.id, 8.0) as TickResult.Ticked
        assertTrue(second.finished)
        assertEquals(4, second.points)
        assertEquals(4, book.balance())
        assertEquals(TickResult.AlreadyDone, book.logAmount(read.id, 1.0))

        val row = TodayList.build(LocalDate(2026, 11, 9), store.goals, store.steps, store.ticks, 4).dueToday.single()
        assertTrue(row.done)
        assertEquals(20.0, row.progress)
        assertEquals(0.0, row.amountLeft)
    }

    @Test
    fun undoTakesBackTheLatestEntryAndItsPoints() = runTest {
        val read = reading()
        book.logAmount(read.id, 12.0)
        book.logAmount(read.id, 8.0)
        assertTrue(book.undoHabitTick(read.id))
        assertEquals(0, book.balance())
        val row = TodayList.build(LocalDate(2026, 11, 9), store.goals, store.steps, store.ticks, 0).dueToday.single()
        assertFalse(row.done)
        assertEquals(12.0, row.progress)
        assertEquals(8.0, row.amountLeft)
        assertEquals("12 of 20 pages", Amounts.progressText(row.progress, read))
    }

    @Test
    fun doneFromAWidgetOrANotificationEntersWhatIsLeft() = runTest {
        val read = reading()
        book.logAmount(read.id, 5.5)
        val result = book.tickHabit(read.id) as TickResult.Ticked
        assertTrue(result.finished)
        assertEquals(14.5, store.ticks.last().amount)
        assertEquals(4, book.balance())
    }

    @Test
    fun aDayWithTooLittleIsNotDone() {
        val read = reading()
        val day = LocalDate(2026, 11, 9)
        val tick = Tick(goalId = read.id, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day, amount = 15.0)
        val records = StreakCounter.records(day, day, listOf(read), listOf(tick), emptyList())
        assertEquals(1, records.single().due)
        assertEquals(0, records.single().done)
        val enough = records.let { StreakCounter.records(day, day, listOf(read), listOf(tick, tick.copy(amount = 5.0)), emptyList()) }
        assertEquals(1, enough.single().done)
    }

    @Test
    fun theUsualSpeedIsLearnedAndATypedTimeWins() {
        val day = LocalDate(2026, 11, 9)
        fun timed(at: Long, amount: Double, minutes: Int) =
            Tick(goalId = 1, stepId = null, at = at, day = day, period = Period.DAILY, periodStart = day, amount = amount, seconds = minutes * 60)
        val ticks = listOf(timed(1, 10.0, 20), timed(2, 10.0, 16), Tick(goalId = 1, stepId = null, at = 3, day = day, period = Period.DAILY, periodStart = day, amount = 5.0))
        val rate = Amounts.minutesPerUnit(ticks)!!
        assertEquals(1.8, rate, 0.0001) // 36 minutes for 20 pages; the untimed entry doesn't count
        val read = Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(start), amount = 20.0, unit = "pages")
        assertEquals(15, Amounts.minutesFor(read, 8.0, rate)) // 8 × 1.8 = 14.4, rounded up
        assertEquals(12, Amounts.minutesFor(read.copy(minutes = 30), 8.0, rate)) // 30 min for 20 pages, typed
        assertNull(Amounts.minutesFor(read, 8.0, null))
        assertNull(Amounts.minutesPerUnit(emptyList()))
    }

    @Test
    fun amountsAreReadAndWrittenPlainly() {
        assertEquals(1.5, Amounts.parse("1,5"))
        assertEquals(20.0, Amounts.parse(" 20 "))
        assertNull(Amounts.parse("0"))
        assertNull(Amounts.parse("abc"))
        assertEquals("12", Amounts.format(12.0))
        assertEquals("1.5", Amounts.format(1.5))
        assertEquals("0.25", Amounts.format(0.25))
        assertEquals("0 of 1 mile", Amounts.progressText(0.0, Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = null, amount = 1.0, unit = "mile")))
    }
}
