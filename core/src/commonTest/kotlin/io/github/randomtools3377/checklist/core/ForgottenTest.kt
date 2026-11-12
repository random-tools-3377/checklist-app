package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "I forgot to tick it", wherever it is said: inside a gap, miss by miss; and what the day gets
 * back once it is perfect after all, paid once.
 */
class ForgottenTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0)) // Monday
    private val book = Book(store, clock) { TimeZone.UTC }
    private val lapses = Lapses(store, book)
    private val gaps = Gaps(store, book)
    private val perfect = PerfectDays(store, book)
    private val combos = Combos(store, book)
    private val start = LocalDate(2026, 1, 1)
    private val monday = LocalDate(2026, 11, 9)
    private val tuesday = LocalDate(2026, 11, 10)
    private val wednesday = LocalDate(2026, 11, 11)

    private fun at(day: Int) = clock.set(LocalDateTime(2026, 11, day, 10, 0))

    private fun habit(name: String, points: Int, allowance: Int = 0) = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = name, icon = "⭐", points = points, repeat = Repeat.everyDay(start), allowance = allowance),
    )

    /** What the app does when it opens, in the same order (SharedParts.closeDays in the shared code). */
    private suspend fun open(): List<Lapse> {
        val found = lapses.closeDays()
        gaps.find()
        perfect.pay()
        combos.pay()
        return found
    }

    private fun on(feature: Feature) = store.settings.put(feature.key, feature.onValue)

    /** The perfect-day and combo lines in the points history for [day], oldest first. */
    private fun dayEnds(day: LocalDate) = store.entries.filter { it.kind == EntryKind.PERFECT_DAY && it.day == day }.map { it.label to it.amount }

    @Test
    fun inAGapEachMissCanBeSaidForgottenAndTheRestStaysCovered() = runTest {
        val run = habit("Run", 10)
        val read = habit("Read", 4)
        open()
        book.tickHabit(run.id)
        book.tickHabit(read.id) // Monday: 14
        at(12) // Thursday: nothing was ticked on Tuesday or Wednesday
        open()

        val gap = store.gapList.single()
        // The check-in lists each goal missed in the stretch, by day.
        assertEquals(
            listOf(tuesday to "Run", tuesday to "Read", wednesday to "Run", wednesday to "Read"),
            gaps.misses(gap).map { it.day to it.label },
        )
        assertEquals(14 - 5 - 2 - 5 - 2, book.balance())

        val tuesdayRun = gaps.misses(gap).first()
        assertTrue(lapses.forgotToTick(tuesdayRun.id))

        // Ticked for that day, and what that miss cost is back.
        assertTrue(store.ticks.any { it.goalId == run.id && it.day == tuesday })
        assertEquals(10 + 5, book.balance())
        // The gap's one reason and one penalty task still cover the rest.
        assertEquals(listOf(tuesday to "Read", wednesday to "Run", wednesday to "Read"), gaps.misses(gap).map { it.day to it.label })
        assertEquals(listOf(gap.id), store.openPenalties().map { it.gapId })
        gaps.explain(gap.id, Reason.ILL, "flu")
        assertTrue(gaps.misses(gap).all { it.reason == Reason.ILL && it.note == "flu" })
        assertEquals(Reason.FORGOT, store.lapse(tuesdayRun.id)!!.reason)
        assertEquals(tuesday to wednesday, store.gapList.single().let { it.from to it.to })
    }

    @Test
    fun whenEveryMissInAGapWasAForgottenTickTheGapGoesAwayWithNoPenaltyTask() = runTest {
        val run = habit("Run", 10)
        open()
        book.tickHabit(run.id) // Monday
        at(12)
        open()
        val gap = store.gapList.single()
        val (first, second) = gaps.misses(gap)
        assertEquals(10 - 5 - 5, book.balance())

        assertTrue(lapses.forgotToTick(first.id))
        assertEquals(listOf(gap.id), store.gapList.map { it.id }, "one miss still stands, so the gap does too")
        assertEquals(listOf(gap.id), store.openPenalties().map { it.gapId })
        assertTrue(lapses.forgotToTick(second.id))

        assertTrue(store.gapList.isEmpty())
        assertTrue(store.openPenalties().isEmpty(), "no penalty task")
        assertTrue(store.lapses.all { it.forgotten && it.gapId == null })
        assertEquals(setOf(monday, tuesday, wednesday), store.ticks.map { it.day }.toSet())
        assertEquals(10 + 10 + 10, book.balance()) // three days ticked, both penalties given back
        // It isn't flagged again, and nothing is found twice.
        assertTrue(gaps.find().isEmpty())
        assertTrue(open().isEmpty())
        assertTrue(store.gapList.isEmpty())
        assertEquals(30, book.balance())
    }

    @Test
    fun aGapThatWentAwayIsNotFlaggedAgainForADayLeftInIt() = runTest {
        store.putSetting(Gaps.SETTING_DAYS, "1") // one day with nothing ticked is a gap here
        val run = habit("Run", 10, allowance = 5)
        open()
        book.tickHabit(run.id) // Monday
        at(10)
        open()
        lapses.skip(run.id, null, null) // Tuesday is skipped: nothing ticked that day
        at(12) // and Wednesday is missed
        open()
        val gap = store.gapList.single()
        assertEquals(tuesday to wednesday, gap.from to gap.to)

        assertTrue(lapses.forgotToTick(gaps.misses(gap).single().id))

        assertTrue(store.gapList.isEmpty())
        assertEquals(listOf(tuesday to wednesday), Gaps.cleared(store.setting(Gaps.CLEARED)))
        assertTrue(gaps.find().isEmpty(), "the skipped Tuesday isn't a new gap of its own")
        assertTrue(store.openPenalties().isEmpty())
    }

    @Test
    fun aGapFromABigPointLossGoesAwayTheSameWay() = runTest {
        store.putSetting(Gaps.SETTING_DAYS, "30") // only the point loss can trigger it here
        store.putSetting(Gaps.SETTING_POINTS, "40")
        val run = habit("Run", 100)
        val read = habit("Read", 4, allowance = 5)
        open()
        book.tickHabit(run.id)
        book.tickHabit(read.id)
        at(10)
        open()
        book.tickHabit(read.id) // Tuesday: Run is missed, half of 100 is lost
        at(11)
        open()
        val gap = store.gapList.single()
        assertEquals(GapKind.POINT_LOSS, gap.kind)
        val miss = gaps.misses(gap).single()
        assertEquals(50, miss.cost)

        assertTrue(lapses.forgotToTick(miss.id))

        assertTrue(store.gapList.isEmpty())
        assertTrue(store.openPenalties().isEmpty())
        assertEquals(104 + 4 + 100, book.balance())
        assertTrue(gaps.find().isEmpty())
    }

    @Test
    fun aGapAlreadyAnsweredGoesAwayTooOnceEveryMissWasAForgottenTick() = runTest {
        val run = habit("Run", 10)
        open()
        book.tickHabit(run.id)
        at(12)
        open()
        val gap = store.gapList.single()
        val misses = gaps.misses(gap)
        gaps.explain(gap.id, Reason.NO_TIME, null)
        val penalty = lapses.openPenalty()!!
        lapses.makeUp(penalty.id, run.id)
        assertTrue(lapses.finishPenalty(penalty.id))

        // Corrected later, from the review.
        misses.forEach { assertTrue(lapses.forgotToTick(it.id)) }

        assertTrue(store.gapList.isEmpty())
        assertNotNull(store.penalty(penalty.id)!!.doneAt, "a penalty task already done stays done")
        assertTrue(gaps.find().isEmpty())
    }

    @Test
    fun aForgottenTickBringsBackTheDaysPerfectDayBonusOnce() = runTest {
        on(Feature.PERFECT_DAY)
        val run = habit("Run", 10, allowance = 5)
        val read = habit("Read", 4, allowance = 5)
        open()
        book.tickHabit(run.id)
        book.tickHabit(read.id) // Monday: everything done
        at(10)
        open()
        book.tickHabit(run.id) // Tuesday: Read is left
        at(11)
        val miss = open().single()
        assertEquals(listOf(PerfectDays.LABEL to 10), dayEnds(monday), "what the day's end pays, under the same name")
        assertTrue(dayEnds(tuesday).isEmpty())

        assertTrue(lapses.forgotToTick(miss.id))

        // Tuesday is perfect after all: its bonus is paid now, for that day.
        assertEquals(listOf(PerfectDays.LABEL to 10), dayEnds(tuesday))
        assertEquals(14 + 10 + 10 + 4 + 10, book.balance())
        // Not twice: not when the app looks again, and not by saying it again.
        open()
        assertFalse(lapses.forgotToTick(miss.id))
        assertEquals(1, dayEnds(tuesday).size)
        assertEquals(1, dayEnds(monday).size)
        assertEquals(48, book.balance())
    }

    @Test
    fun theDayGetsItBackOnlyOnceEverythingDueOnItIsDone() = runTest {
        on(Feature.PERFECT_DAY)
        on(Feature.COMBOS)
        val run = habit("Run", 10, allowance = 5)
        val read = habit("Read", 4, allowance = 5)
        open()
        book.tickHabit(run.id)
        book.tickHabit(read.id) // Monday
        at(10)
        open() // Monday: the bonus, and a combo of 1
        book.tickHabit(read.id) // Tuesday: only Read, so it is no gap
        at(11)
        open()
        // A third goal was due on Tuesday too, missed, and has been removed since.
        val walk = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat.everyDay(start), allowance = 5, archived = true))
        store.addLapse(Lapse(goalId = walk.id, kind = LapseKind.MISS, day = tuesday, period = Period.DAILY, periodStart = tuesday, at = book.millis(), overLimit = false, label = "Walk"))
        val runMiss = store.lapses.single { it.goalId == run.id }
        val walkMiss = store.lapses.single { it.goalId == walk.id }

        assertTrue(lapses.forgotToTick(runMiss.id))
        assertTrue(dayEnds(tuesday).isEmpty(), "a miss still stands on Tuesday")

        assertTrue(lapses.forgotToTick(walkMiss.id))
        assertEquals(listOf(PerfectDays.LABEL to 10, "Combo ×2" to 4), dayEnds(tuesday))
        assertEquals("2", store.settings[Combos.COUNT])

        // Wednesday carries on from there.
        book.tickHabit(run.id)
        book.tickHabit(read.id)
        at(12)
        open()
        assertEquals(listOf(PerfectDays.LABEL to 10, "Combo ×3" to 6), dayEnds(wednesday))
    }

    @Test
    fun aForgottenTickCarriesTheComboOnAsIfTheDayHadBeenPerfectAllAlong() = runTest {
        on(Feature.COMBOS)
        val read = habit("Read", 5, allowance = 5)
        // Twelve perfect days, Monday 9 to Friday 20 November.
        for (day in 9..20) {
            at(day)
            open()
            book.tickHabit(read.id)
        }
        at(21) // Saturday is left undone
        open()
        assertEquals("12", store.settings[Combos.COUNT])
        at(22)
        val miss = open().single()
        assertEquals("0", store.settings[Combos.COUNT], "the combo ended on the miss")
        book.tickHabit(read.id) // Sunday
        at(23)
        open()
        val saturday = LocalDate(2026, 11, 21)
        val sunday = LocalDate(2026, 11, 22)
        assertEquals(listOf("Combo ×1" to 2), dayEnds(sunday), "and started again")
        assertEquals(1, DayBack.comboOf(store.entries.last { it.day == sunday }), "read from the name the day's end gives it")

        assertTrue(lapses.forgotToTick(miss.id))

        // Saturday takes its place after the ×12, and Sunday is topped up to what ×14 pays.
        assertEquals(listOf("Combo ×13" to Combos.pay(13)), dayEnds(saturday))
        assertEquals(listOf("Combo ×1" to 2, "Combo ×14" to Combos.pay(14) - 2), dayEnds(sunday))
        assertEquals("14", store.settings[Combos.COUNT])
        book.tickHabit(read.id) // Monday 23
        at(24)
        open()
        assertEquals(listOf("Combo ×15" to Combos.pay(15)), dayEnds(LocalDate(2026, 11, 23)))
        // Every day was paid what it would have been, no more.
        val paid = { store.entries.filter { DayBack.comboOf(it) != null }.sumOf { it.amount } }
        assertEquals((1..15).sumOf { Combos.pay(it) }, paid())
        assertFalse(lapses.forgotToTick(miss.id))
        open()
        assertEquals((1..15).sumOf { Combos.pay(it) }, paid())
    }

    @Test
    fun daysForgottenInAnyOrderEndUpPaidTheSame() = runTest {
        on(Feature.COMBOS)
        on(Feature.PERFECT_DAY)
        store.putSetting(Gaps.SETTING_DAYS, "30") // no gap here: each miss is asked about on its own
        val read = habit("Read", 5, allowance = 5)
        for (day in 9..10) {
            at(day)
            open()
            book.tickHabit(read.id)
        }
        at(14) // Saturday: Wednesday, Thursday and Friday were missed
        val misses = open()
        assertEquals(3, misses.size)

        // The latest first.
        misses.reversed().forEach { assertTrue(lapses.forgotToTick(it.id)) }

        val days = (9..13).map { LocalDate(2026, 11, it) }
        assertEquals(List(5) { 10 }, days.map { day -> store.entries.filter { it.day == day && it.label == PerfectDays.LABEL }.sumOf { it.amount } })
        assertEquals((1..5).map { Combos.pay(it) }, days.map { day -> store.entries.filter { it.day == day && DayBack.comboOf(it) != null }.sumOf { it.amount } })
        assertEquals((1..5).toList(), days.map { day -> store.entries.filter { it.day == day }.mapNotNull { DayBack.comboOf(it) }.max() })
        assertEquals("5", store.settings[Combos.COUNT])
    }

    @Test
    fun pastTheMostAComboPaysItsLineJustSaysTheNewNumber() = runTest {
        store.putSetting(Rules.SETTING, Rules.encode(Rules(comboMostDays = 2)))
        try {
            on(Feature.COMBOS)
            val read = habit("Read", 5, allowance = 5)
            // Monday and Tuesday done, Wednesday missed, Thursday to Saturday done.
            for (day in 9..14) {
                at(day)
                open()
                if (day != 11) book.tickHabit(read.id)
            }
            at(15)
            open()
            val miss = store.lapses.single()
            assertEquals(listOf(2, 4, 0, 2, 4, 4), (9..14).map { day -> dayEnds(LocalDate(2026, 11, day)).sumOf { it.second } })

            assertTrue(lapses.forgotToTick(miss.id))

            // Wednesday pays its place; Thursday is topped up; Friday and Saturday already had the most.
            assertEquals(listOf(2, 4, 4, 4, 4, 4), (9..14).map { day -> dayEnds(LocalDate(2026, 11, day)).sumOf { it.second } })
            assertEquals(listOf("Combo ×5" to 4), dayEnds(LocalDate(2026, 11, 13)))
            assertEquals(listOf("Combo ×6" to 4), dayEnds(LocalDate(2026, 11, 14)))
            assertEquals("6", store.settings[Combos.COUNT])
        } finally {
            Rules.current = Rules()
        }
    }

    /** Removes a goal, as the Goals list does: it is no longer counted as due, and its misses stay. */
    private fun remove(goal: Goal) {
        store.goals[store.goals.indexOfFirst { it.id == goal.id }] = store.goals.first { it.id == goal.id }.copy(archived = true)
    }

    @Test
    fun aComboDoesNotCarryOnOverADayARemovedGoalsMissLeftUndone() = runTest {
        on(Feature.COMBOS)
        store.putSetting(Gaps.SETTING_DAYS, "30") // no gap here
        // Read is due on Monday, Wednesday and Friday; Walk on Tuesday, Thursday and Saturday.
        val read = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat(RepeatKind.EVERY_N_DAYS, monday, interval = 2), allowance = 5))
        val walk = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat(RepeatKind.EVERY_N_DAYS, tuesday, interval = 2), allowance = 5))
        open()
        book.tickHabit(read.id) // Monday: a combo of 1
        at(10)
        open() // Tuesday: Walk is left undone, the only thing due
        at(11)
        open() // Wednesday: Read is left undone
        assertEquals(listOf("Combo ×1" to 2), dayEnds(monday))
        remove(walk) // nothing counts as due on Tuesday any more, but its miss still stands
        at(12)
        open()

        assertTrue(lapses.forgotToTick(store.lapses.single { it.goalId == read.id }.id))

        assertEquals(listOf("Combo ×1" to 2), dayEnds(wednesday), "Tuesday's miss ended the combo, so Wednesday starts a new one")
        assertEquals("1", store.settings[Combos.COUNT])
    }

    @Test
    fun aLaterDayARemovedGoalsMissLeftUndoneIsNotToppedUpPast() = runTest {
        on(Feature.COMBOS)
        store.putSetting(Gaps.SETTING_DAYS, "30")
        val read = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat(RepeatKind.EVERY_N_DAYS, monday, interval = 2), allowance = 5))
        val walk = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat(RepeatKind.EVERY_N_DAYS, tuesday, interval = 2), allowance = 5))
        open() // Monday: Read is left undone
        at(10)
        open() // Tuesday: Walk is left undone
        at(11)
        open()
        book.tickHabit(read.id) // Wednesday: a combo of 1
        at(12)
        open()
        assertEquals(listOf("Combo ×1" to 2), dayEnds(wednesday))
        remove(walk)

        assertTrue(lapses.forgotToTick(store.lapses.single { it.goalId == read.id && it.day == monday }.id))

        assertEquals(listOf("Combo ×1" to 2), dayEnds(monday))
        assertEquals(listOf("Combo ×1" to 2), dayEnds(wednesday), "Tuesday still ended the combo, so Wednesday stays as it was")
        assertEquals("1", store.settings[Combos.COUNT])
    }

    @Test
    fun aDayFromBeforeTheBonusAndCombosWereOnGetsNothingBack() = runTest {
        val read = habit("Read", 5, allowance = 5)
        open()
        book.tickHabit(read.id) // Monday
        at(10)
        open() // Tuesday is left undone, with both still off
        at(11)
        val miss = open().single()
        on(Feature.PERFECT_DAY)
        on(Feature.COMBOS)
        open()

        assertTrue(lapses.forgotToTick(miss.id))

        assertTrue(store.entries.none { it.kind == EntryKind.PERFECT_DAY }, "they were off on Tuesday")
        // From today on they count as usual.
        book.tickHabit(read.id)
        at(12)
        open()
        assertEquals(listOf(PerfectDays.LABEL to 10, "Combo ×1" to 2), dayEnds(wednesday))
    }

    @Test
    fun turningTheBonusOffAndOnWithinADayStillBringsAnEarlierDayBack() = runTest {
        on(Feature.PERFECT_DAY)
        on(Feature.COMBOS)
        val read = habit("Read", 5, allowance = 5)
        open()
        book.tickHabit(read.id) // Monday
        at(10)
        open() // Tuesday is left undone
        at(11)
        val miss = open().single()
        // Off, the app opened again, and on again, all on Wednesday.
        store.putSetting(Feature.PERFECT_DAY.key, "0")
        store.putSetting(Feature.COMBOS.key, "")
        open()
        on(Feature.PERFECT_DAY)
        on(Feature.COMBOS)

        assertTrue(lapses.forgotToTick(miss.id))

        assertEquals(listOf(PerfectDays.LABEL to 10, "Combo ×2" to 4), dayEnds(tuesday))
    }

    @Test
    fun undoingASlipTodayPutsTheTickBackAndLeavesTheBonusToTheDaysEnd() = runTest {
        on(Feature.PERFECT_DAY)
        val sugar = store.addGoal(Goal(kind = GoalKind.HABIT, name = "No sugar", icon = "🍬", points = 4, repeat = Repeat.everyDay(start), allowance = 0, quit = true))
        open()
        book.tickHabit(sugar.id) // Monday avoided
        at(10)
        open()
        book.tickHabit(sugar.id)
        val slip = lapses.slip(sugar.id)!!
        assertTrue(Lapses.isSlip(slip, quit = true, dayEndHour = 0, zone = TimeZone.UTC))
        assertEquals(4 + 10 - 2, book.balance())
        assertNotNull(lapses.openPenalty())

        assertTrue(lapses.forgotToTick(slip.id)) // "Undo, I didn't slip"

        assertEquals(4 + 10 + 4, book.balance())
        assertNull(lapses.openPenalty())
        assertEquals(TickResult.AlreadyDone, book.tickHabit(sugar.id))
        // Today isn't over: its bonus comes when it ends, once.
        assertTrue(dayEnds(tuesday).isEmpty())
        at(11)
        assertTrue(open().isEmpty())
        assertEquals(listOf(PerfectDays.LABEL to 10), dayEnds(tuesday))
    }

    @Test
    fun aSlipIsAMissOfAGoalToQuitRecordedOnItsOwnDay() = runTest {
        val sugar = store.addGoal(Goal(kind = GoalKind.HABIT, name = "No sugar", icon = "🍬", points = 4, repeat = Repeat.everyDay(start), allowance = 5, quit = true))
        open()
        at(10)
        val found = open().single() // Monday wasn't ticked as avoided: found once it had ended
        val slip = lapses.slip(sugar.id)!!

        assertTrue(Lapses.isSlip(slip, quit = true, dayEndHour = 0, zone = TimeZone.UTC))
        assertFalse(Lapses.isSlip(found, quit = true, dayEndHour = 0, zone = TimeZone.UTC), "an ordinary miss")
        assertFalse(Lapses.isSlip(slip, quit = false, dayEndHour = 0, zone = TimeZone.UTC))
        // With the day ending at 4am, a slip at 1am still belongs to the day before.
        clock.set(LocalDateTime(2026, 11, 12, 1, 0))
        store.dayEnd = 4
        val late = lapses.slip(sugar.id)!!
        assertEquals(wednesday, late.day)
        assertTrue(Lapses.isSlip(late, quit = true, dayEndHour = 4, zone = TimeZone.UTC))
    }

    @Test
    fun aMissOpenedLaterCanBeSaidForgottenForTwoWeeks() {
        val today = LocalDate(2026, 11, 30)
        fun lapse(daysAgo: Int, kind: LapseKind = LapseKind.MISS, period: Period? = Period.DAILY, forgotten: Boolean = false) = Lapse(
            goalId = 1, kind = kind, day = today.minusDays(daysAgo), period = period, periodStart = today.minusDays(daysAgo), at = 0,
            overLimit = false, forgotten = forgotten, label = "Run",
        )
        assertTrue(Lapses.canForgetLater(lapse(1), today))
        assertTrue(Lapses.canForgetLater(lapse(Lapses.FORGET_DAYS), today))
        assertFalse(Lapses.canForgetLater(lapse(Lapses.FORGET_DAYS + 1), today), "too long ago")
        assertFalse(Lapses.canForgetLater(lapse(1, period = null), today), "a project's missed deadline")
        assertFalse(Lapses.canForgetLater(lapse(1, kind = LapseKind.SKIP), today), "a skip")
        assertFalse(Lapses.canForgetLater(lapse(1, forgotten = true), today), "already said")
    }

    @Test
    fun aSinceLastDoneGoalCorrectedLaterStaysWhereItHasMovedOnTo() = runTest {
        val plants = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, monday, interval = 5), allowance = 5))
        fun repeat() = store.goals.first { it.id == plants.id }.repeat!!
        open()
        at(10)
        val miss = open().single() // Monday's
        book.tickHabit(plants.id) // done on Tuesday: the next one is on Sunday
        assertEquals(LocalDate(2026, 11, 15), repeat().start)
        at(12)
        open()

        assertTrue(lapses.forgotToTick(miss.id)) // Monday was done after all

        assertTrue(store.ticks.any { it.goalId == plants.id && it.day == monday })
        assertEquals(LocalDate(2026, 11, 15), repeat().start, "it has moved on since, and stays there")
        assertNull(repeat().lateSince)
    }

    @Test
    fun aSinceLastDoneGoalStillLateStartsTheNextFromTheDayItWasMissed() = runTest {
        val plants = store.addGoal(Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, monday, interval = 5), allowance = 5))
        fun repeat() = store.goals.first { it.id == plants.id }.repeat!!
        open()
        at(10)
        val miss = open().single()
        assertEquals(monday, repeat().lateSince)

        assertTrue(lapses.forgotToTick(miss.id))

        assertEquals(LocalDate(2026, 11, 14), repeat().start)
        assertNull(repeat().lateSince)
    }
}
