package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtMostTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0)) // Monday
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val lapses = Lapses(store, book)
    private val monday = LocalDate(2026, 11, 9)
    private val tuesday = LocalDate(2026, 11, 10)

    private fun on(day: Int, hour: Int = 10) = clock.set(LocalDateTime(2026, 11, day, hour, 0))

    private fun coffee(limit: Double = 2.0, allowance: Int? = null, unit: String? = null) = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = "Coffee", icon = "☕", points = 6, repeat = Repeat.everyDay(monday), limit = limit, allowance = allowance, unit = unit),
    )

    private fun view(day: LocalDate) = TodayList.build(day, store.goals, store.steps, store.ticks, 0, store.lapses)
    private fun records(from: LocalDate, to: LocalDate) = StreakCounter.records(from, to, store.goals, store.ticks, store.lapses) { null }

    @Test
    fun theLimitsAreKeptByGoalIdAndCleanedUp() {
        val text = AtMost.with(AtMost.with(null, 12, 2.0), 15, 1.5)
        assertEquals("12:2,15:1.5", text)
        assertEquals(mapOf(12L to 2.0, 15L to 1.5), AtMost.decode(text))
        assertEquals("15:1.5", AtMost.with(text, 12, null))
        assertEquals("12:2", AtMost.kept(text, setOf(12L, 99L)))
        // Nonsense is left out rather than breaking anything.
        assertEquals(mapOf(3L to 4.0), AtMost.decode("x:1,3:4,5:-2,7,8:abc,"))
        assertEquals(emptyMap(), AtMost.decode(null))
    }

    @Test
    fun itFitsGoalsDueOnDaysOnly() {
        assertTrue(AtMost.fits(Repeat.everyDay(monday)))
        assertFalse(AtMost.fits(Repeat(RepeatKind.TIMES_PER, monday, per = Period.WEEKLY)))
        assertFalse(AtMost.fits(Repeat(RepeatKind.AFTER_DONE, monday, interval = 2)))
        assertFalse(AtMost.fits(null))
        // Only a habit with a limit is one.
        assertTrue(Goal(kind = GoalKind.HABIT, name = "a", icon = "a", points = 1, repeat = Repeat.everyDay(monday), limit = 1.0).atMost)
        assertFalse(Goal(kind = GoalKind.HABIT, name = "a", icon = "a", points = 1, repeat = Repeat.everyDay(monday)).atMost)
    }

    @Test
    fun theCardSaysWhatIsUsedAndLeft() {
        val goal = Goal(kind = GoalKind.HABIT, name = "Coffee", icon = "☕", points = 1, repeat = Repeat.everyDay(monday), limit = 2.0)
        assertEquals("0 of 2 used, 2 left", AtMost.text(0.0, goal))
        assertEquals("1 of 2 used, 1 left", AtMost.text(1.0, goal))
        assertEquals("2 of 2 used, none left", AtMost.text(2.0, goal))
        assertEquals("3 of 2 used, 1 over", AtMost.text(3.0, goal))
        val screen = goal.copy(limit = 120.0, unit = "minutes")
        assertEquals("45 of 120 minutes used, 75 left", AtMost.text(45.0, screen))
        assertEquals("120 minutes", AtMost.limitText(screen))
        assertEquals("2", AtMost.limitText(goal))
    }

    @Test
    fun aDayUnderTheLimitIsDoneAndPaidWhenItEnds() = runTest {
        val goal = coffee()
        lapses.closeDays()
        assertIs<UseResult.Added>(lapses.use(goal.id))
        // Adding pays nothing, and is never progress toward done.
        assertEquals(0, book.balance())
        val row = view(monday).limits.single()
        assertEquals(1.0, row.progress)
        assertFalse(row.done)
        assertFalse(row.slipped)
        assertTrue(view(monday).dueToday.isEmpty())
        assertEquals(0, records(monday, monday).single().done)

        on(10)
        assertTrue(lapses.closeDays().isEmpty()) // no miss
        assertEquals(6, book.balance())
        val entry = store.entries.single()
        assertEquals(EntryKind.TICK, entry.kind)
        assertEquals(monday, entry.day)
        assertEquals("Coffee", entry.label)
        // Done on Monday, by a tick dated the day's last moment.
        val record = records(monday, monday).single()
        assertEquals(1, record.due)
        assertEquals(1, record.done)
        assertTrue(record.perfect)
        val paid = store.ticks.single { !it.isUse }
        assertEquals(monday, paid.day)
        assertEquals(book.endOf(monday), paid.at)
        // Not paid twice.
        assertTrue(lapses.closeDays().isEmpty())
        assertEquals(6, book.balance())
    }

    @Test
    fun exactlyAtTheLimitIsDoneAndADayWithNothingAddedToo() = runTest {
        val goal = coffee()
        lapses.closeDays()
        lapses.use(goal.id)
        val second = lapses.use(goal.id) as UseResult.Added
        assertEquals(2.0, second.used)
        assertFalse(second.over)
        assertNull(second.slip)
        on(11) // Monday at the limit, Tuesday with nothing added
        assertTrue(lapses.closeDays().isEmpty())
        assertEquals(12, book.balance())
        assertEquals(listOf(1, 1), records(monday, tuesday).map { it.done })
        assertTrue(store.lapses.isEmpty())
    }

    @Test
    fun goingOverIsAMissStraightAwayWithinTheAllowance() = runTest {
        val goal = coffee()
        lapses.closeDays()
        lapses.use(goal.id)
        lapses.use(goal.id)
        val third = lapses.use(goal.id) as UseResult.Added
        assertTrue(third.over)
        val miss = assertNotNull(third.slip)
        assertEquals(LapseKind.MISS, miss.kind)
        assertEquals(monday, miss.day)
        assertFalse(miss.overLimit) // the week's one free miss
        assertNull(third.penaltyId)
        assertEquals(0, book.balance())
        assertTrue(Lapses.isSlip(miss, quit = true, dayEndHour = 0, zone = kotlinx.datetime.TimeZone.UTC))
        val row = view(monday).limits.single()
        assertTrue(row.slipped)
        assertTrue(row.overLimit)
        // More can still be added; the day is missed once.
        assertNull((lapses.use(goal.id) as UseResult.Added).slip)
        assertEquals(1, store.lapses.size)
        // It can't be confirmed, and isn't a forgotten tick.
        assertEquals(TickResult.AlreadyDone, book.tickHabit(goal.id))
        assertFalse(lapses.forgotToTick(miss.id))
        on(10)
        assertTrue(lapses.closeDays().isEmpty()) // not missed twice, and not paid
        assertEquals(0, book.balance())
        val record = records(monday, monday).single()
        assertEquals(1, record.due)
        assertEquals(0, record.done)
        assertEquals(1, record.misses)
    }

    @Test
    fun overTheAllowanceGoingOverCostsPointsAndOwesAPenaltyTask() = runTest {
        val goal = coffee(limit = 1.0, allowance = 0)
        lapses.closeDays()
        lapses.use(goal.id)
        val over = lapses.use(goal.id) as UseResult.Added
        val miss = assertNotNull(over.slip)
        assertTrue(miss.overLimit)
        assertEquals(3, miss.cost) // half of 6
        assertEquals(-3, book.balance())
        assertNotNull(over.penaltyId)
        assertEquals(miss.id, lapses.openPenalty()?.lapseId)
    }

    @Test
    fun takingBackWhatWentOverTakesTheMissBackToo() = runTest {
        val goal = coffee(limit = 1.0, allowance = 0)
        lapses.closeDays()
        lapses.use(goal.id)
        lapses.use(goal.id, 5.0) // a slip of the finger
        assertEquals(-3, book.balance())
        assertTrue(lapses.undoUse(goal.id))
        assertEquals(1.0, view(monday).limits.single().progress)
        assertTrue(store.lapses.isEmpty())
        assertEquals(0, book.balance())
        assertTrue(store.entries.isEmpty())
        assertNull(lapses.openPenalty())
        // Back under: the day can be confirmed, and ends done.
        assertIs<TickResult.Ticked>(book.tickHabit(goal.id))
        assertEquals(6, book.balance())
        // Undo of each thing added, until there is none.
        assertTrue(lapses.undoUse(goal.id))
        assertFalse(lapses.undoUse(goal.id))
        assertEquals(0.0, view(monday).limits.single().progress)
    }

    @Test
    fun undoOfOneOfSeveralOverKeepsTheMissWhileStillOver() = runTest {
        val goal = coffee(limit = 1.0)
        lapses.closeDays()
        repeat(3) { lapses.use(goal.id) }
        assertTrue(lapses.undoUse(goal.id)) // 2 of 1: still over
        assertEquals(1, store.lapses.size)
        assertTrue(lapses.undoUse(goal.id)) // 1 of 1
        assertTrue(store.lapses.isEmpty())
    }

    @Test
    fun confirmingEarlyPaysStraightAwayAndCanBeTakenBack() = runTest {
        val goal = coffee()
        lapses.closeDays()
        lapses.use(goal.id)
        val confirmed = book.tickHabit(goal.id) as TickResult.Ticked // "That's all for today"
        assertEquals(6, confirmed.points)
        assertTrue(confirmed.finished)
        assertEquals(6, book.balance())
        assertTrue(view(monday).limits.single().done)
        assertEquals(TickResult.AlreadyDone, book.tickHabit(goal.id))
        // Taken back, the points go too; confirmed again and left, the day's end pays nothing more.
        assertTrue(book.undoHabitTick(goal.id))
        assertEquals(0, book.balance())
        assertFalse(view(monday).limits.single().done)
        assertEquals(1.0, view(monday).limits.single().progress) // what was added stays
        book.tickHabit(goal.id)
        on(10)
        lapses.closeDays()
        assertEquals(6, book.balance())
        assertEquals(1, store.ticks.count { !it.isUse })
    }

    @Test
    fun goingOverAfterConfirmingTakesThePointsBack() = runTest {
        val goal = coffee()
        lapses.closeDays()
        book.tickHabit(goal.id)
        // One more, still within the limit: it stays confirmed.
        lapses.use(goal.id)
        lapses.use(goal.id)
        assertEquals(6, book.balance())
        assertTrue(view(monday).limits.single().done)
        val over = lapses.use(goal.id) as UseResult.Added
        assertEquals(6, over.tookBack)
        assertNotNull(over.slip)
        assertEquals(0, book.balance())
        assertFalse(view(monday).limits.single().done)
    }

    @Test
    fun aSkipWorksAsForAnyGoal() = runTest {
        val goal = coffee()
        lapses.closeDays()
        assertIs<SkipResult.Skipped>(lapses.skip(goal.id, null, null))
        // Skipped, going over is no miss, and the day is neither done nor paid.
        repeat(3) { lapses.use(goal.id) }
        assertEquals(1, store.lapses.size)
        assertEquals(LapseKind.SKIP, store.lapses.single().kind)
        on(10)
        assertTrue(lapses.closeDays().isEmpty())
        assertEquals(0, book.balance())
        val record = records(monday, monday).single()
        assertEquals(0, record.due) // dropped out of the day
        assertEquals(1, record.skips)
    }

    @Test
    fun anAmountInTheUsersUnitAddsUp() = runTest {
        val goal = coffee(limit = 120.0, unit = "minutes")
        lapses.closeDays()
        assertEquals(45.0, (lapses.use(goal.id, 45.0) as UseResult.Added).used)
        assertEquals(120.0, (lapses.use(goal.id, 75.0) as UseResult.Added).used)
        assertEquals("120 of 120 minutes used, none left", AtMost.text(view(monday).limits.single().progress, store.goals.single()))
        assertNotNull((lapses.use(goal.id, 0.5) as UseResult.Added).slip)
        assertEquals(UseResult.NotFound, lapses.use(goal.id, 0.0))
    }

    @Test
    fun onlyAnAtMostGoalDueTodayTakesUses() = runTest {
        val plain = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat.everyDay(monday)))
        val later = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Wine", icon = "🍷", points = 2, repeat = Repeat.everyDay(tuesday), limit = 1.0))
        assertEquals(UseResult.NotFound, lapses.use(plain.id))
        assertEquals(UseResult.NotDue, lapses.use(later.id))
        assertEquals(UseResult.NotFound, lapses.use(999))
        assertFalse(lapses.undoUse(plain.id))
        assertTrue(store.ticks.isEmpty())
    }

    @Test
    fun streaksTheReviewAndTheGoalPageCountItByItsDays() = runTest {
        val goal = coffee(limit = 1.0)
        lapses.closeDays()
        lapses.use(goal.id) // Monday: at the limit
        on(10)
        lapses.closeDays()
        lapses.use(goal.id)
        lapses.use(goal.id) // Tuesday: over
        on(11)
        lapses.closeDays() // Wednesday: nothing yet
        val wednesday = LocalDate(2026, 11, 11)
        val days = records(monday, wednesday)
        assertEquals(listOf(1, 0, 0), days.map { it.done })
        assertEquals(listOf(0, 1, 0), days.map { it.misses })
        val streaks = StreakCounter.count(days, wednesday, StreakSettings())
        assertEquals(0, streaks.done)
        assertEquals(1, streaks.noMisses) // today so far; Tuesday broke it

        val summary = Review.summarize(monday, wednesday, wednesday, store.goals, store.ticks, store.lapses, store.entries, emptyList()) { null }
        val line = summary.goals.single()
        assertEquals(3, line.due)
        assertEquals(1, line.done)
        assertEquals(1, line.missed)
        assertEquals(1, line.ticks) // the done day, not the three coffees
        assertEquals(6, line.points)

        val page = GoalPage.of(store.goals.single(), wednesday, emptyList(), store.ticks, store.lapses, store.entries) { null }
        assertEquals(GoalDayMark.DONE, page.marks[monday])
        assertEquals(GoalDayMark.MISSED, page.marks[tuesday])
        assertNull(page.marks[wednesday])
        assertEquals(1, page.ticks)
        assertEquals(listOf(TimeState.DONE, TimeState.MISSED, TimeState.OPEN), page.times.map { it.state })
    }

    @Test
    fun whatIsAddedIsNeverTakenAsATick() = runTest {
        val goal = coffee(limit = 5.0)
        lapses.closeDays()
        on(9, 7)
        repeat(3) { lapses.use(goal.id) }
        val uses = store.ticks
        assertTrue(uses.all { it.isUse })
        // Not an item for the boss, not a badge, not an early tick, not a quest's tick.
        assertTrue(Bosses.inventory(uses, store.goals, 0, emptyMap()).isEmpty())
        val badges = Badges.earned(monday, emptyList(), StreakSettings(), store.goals, emptyList(), uses, emptyList(), 0)
        assertTrue(badges.none { it.badge == Badge.FIRST_TICK })
        val early = Quest(0, QuestKind.EARLY, "Tick 2 goals before noon", 2, 5, by = kotlinx.datetime.LocalTime(12, 0))
        assertEquals(0, Quests.progress(early, emptyList(), uses, { kotlinx.datetime.LocalTime(7, 0) }, 0).done)
        // Nor a reminder, nor a place on the agenda, nor a goal ticked for any other counter.
        assertTrue(Alerts.between(store.goals.map { it.copy(reminder = Reminder(at = kotlinx.datetime.LocalTime(9, 0))) }, LocalDateTime(2026, 11, 9, 0, 0), LocalDateTime(2026, 11, 10, 0, 0), 0).isEmpty())
        assertFalse(Alerts.stillToDo(goal, Window(monday, monday, Period.DAILY), uses, emptyList()))
        assertTrue(CalendarView.agenda(monday, store.goals, uses).isEmpty())
        assertEquals(0, goal.timesDone(uses.filter { it.countsToward(goal, monday) }))
        assertEquals(uses.size, AtMost.used(goal, uses, monday).toInt())
        assertTrue(AtMost.withoutUses(uses).isEmpty())
    }

    @Test
    fun aDayOnlyTheAppPaidIsStillPartOfAGap() = runTest {
        coffee()
        store.addGoal(Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat.everyDay(monday)))
        val gaps = Gaps(store, book)
        lapses.closeDays()
        // Away from Monday to Wednesday: the limit was kept, the walk missed, nothing ticked.
        on(12)
        lapses.closeDays()
        assertEquals(3, store.ticks.count { !it.isUse })
        val gap = gaps.find().single()
        assertEquals(monday, gap.from)
        assertEquals(LocalDate(2026, 11, 11), gap.to)
    }

    @Test
    fun quietDaysWithOnlyAnAtMostGoalAreNotAGap() = runTest {
        coffee()
        lapses.closeDays()
        // Monday to Wednesday at or under the limit with nothing added: paid, and nothing was left undone.
        on(12)
        lapses.closeDays()
        assertEquals(3, store.ticks.count { !it.isUse })
        assertTrue(Gaps(store, book).find().isEmpty())
    }

    @Test
    fun theEveningCheckSaysItIsStillUnderButNeverComesForItAlone() = runTest {
        val goal = coffee()
        lapses.closeDays()
        lapses.use(goal.id)
        assertNull(EveningCheck.of(view(monday), store.lapses))
        store.addGoal(Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat.everyDay(monday)))
        val list = assertNotNull(EveningCheck.of(view(monday), store.lapses))
        assertEquals("1 still open today", list.title)
        assertEquals(listOf("🚶 Walk", "☕ Coffee · still under your limit (1 of 2 used, 1 left)"), list.lines())
        // Over, it isn't listed any more.
        lapses.use(goal.id)
        lapses.use(goal.id)
        assertEquals(listOf("🚶 Walk"), EveningCheck.of(view(monday), store.lapses)!!.lines())
    }
}
