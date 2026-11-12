package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The evening check: what counts as still open, the words, and when it comes. */
class EveningCheckTest {
    private val start = LocalDate(2026, 1, 1)
    private val wednesday = LocalDate(2026, 11, 11)
    private val sunday = LocalDate(2026, 11, 15)

    private fun habit(id: Long, name: String, icon: String = "⭐", repeat: Repeat = Repeat.everyDay(start), target: Int = 1) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = icon, points = 5, repeat = repeat, target = target, position = id.toInt())

    private fun tick(goal: Goal, day: LocalDate, amount: Double? = null) = Tick(
        goalId = goal.id, stepId = null, at = 0, day = day, period = goal.period,
        periodStart = Days.periodStart(day, goal.period!!), amount = amount,
    )

    private fun lapse(goal: Goal, day: LocalDate, kind: LapseKind, reason: Reason? = null, note: String? = null) = Lapse(
        goalId = goal.id, kind = kind, day = day, period = goal.period, periodStart = Days.periodStart(day, goal.period!!),
        at = 0, overLimit = false, reason = reason, note = note, label = goal.name,
    )

    private fun list(
        day: LocalDate,
        goals: List<Goal>,
        ticks: List<Tick> = emptyList(),
        lapses: List<Lapse> = emptyList(),
        steps: List<Step> = emptyList(),
        hidePrivate: Boolean = false,
    ) = EveningCheck.of(TodayList.build(day, goals, steps, ticks, 0, lapses), lapses, hidePrivate)

    @Test
    fun goalsDueTodayAreOpenUntilDoneOrSkipped() {
        val run = habit(1, "Run", "🏃")
        val read = habit(2, "Read", "📖")
        val floss = habit(3, "Floss", "🦷")
        val mondays = habit(4, "Bins", "🗑", Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1)))
        val goals = listOf(run, read, floss, mondays)

        val all = list(wednesday, goals)!!
        assertEquals(wednesday, all.day)
        assertEquals(listOf("🏃 Run", "📖 Read", "🦷 Floss"), all.lines())
        assertEquals("3 still open today", all.title)

        // One done and one skipped: only the third is left. Yesterday's tick doesn't count for today.
        val left = list(
            wednesday, goals,
            ticks = listOf(tick(run, wednesday), tick(floss, wednesday.minusDays(1))),
            lapses = listOf(lapse(read, wednesday, LapseKind.SKIP)),
        )!!
        assertEquals(listOf("🦷 Floss"), left.lines())
        assertEquals("1 still open today", left.title)
    }

    @Test
    fun nothingIsSentOnADayWhenEverythingIsDone() {
        val run = habit(1, "Run")
        val read = habit(2, "Read")
        assertNull(list(wednesday, listOf(run, read), ticks = listOf(tick(run, wednesday)), lapses = listOf(lapse(read, wednesday, LapseKind.SKIP))))
        // Nothing due at all, or only things that can't be missed: nothing either.
        assertNull(list(wednesday, emptyList()))
        val someday = Goal(id = 3, kind = GoalKind.TODO, name = "Sort photos", icon = "🖼", points = 2, repeat = null)
        val noDeadline = Goal(id = 4, kind = GoalKind.PROJECT, name = "Shelf", icon = "🔨", points = 0, repeat = null)
        assertNull(list(wednesday, listOf(someday, noDeadline), steps = listOf(Step(id = 10, goalId = 4, name = "Buy", points = 5, position = 0))))
    }

    @Test
    fun aTimesPerGoalIsOpenOnlyOnTheLastDayOfItsWindowWhileStillShort() {
        val gym = habit(1, "Gym", "🏋", Repeat.timesPer(Period.WEEKLY, start), target = 3)
        val once = listOf(tick(gym, LocalDate(2026, 11, 10)))
        // Midweek there is still time, so it isn't on the list.
        assertNull(list(wednesday, listOf(gym), once))
        // Sunday is the week's last day: two more are needed.
        assertEquals(listOf("🏋 Gym · 2 more this week"), list(sunday, listOf(gym), once)!!.lines())
        // A skip covers one of its times.
        val skipped = listOf(lapse(gym, sunday, LapseKind.SKIP))
        assertEquals(listOf("🏋 Gym · 1 more this week"), list(sunday, listOf(gym), once, skipped)!!.lines())
        // Done, or the rest skipped: nothing left.
        assertNull(list(sunday, listOf(gym), once + tick(gym, sunday) + tick(gym, sunday)))
        assertNull(list(sunday, listOf(gym), once + tick(gym, sunday), skipped))

        // The same for a month and a year, on their last days.
        val call = habit(2, "Call home", "📞", Repeat.timesPer(Period.MONTHLY, start), target = 2)
        assertNull(list(LocalDate(2026, 11, 29), listOf(call)))
        assertEquals(listOf("📞 Call home · 2 more this month"), list(LocalDate(2026, 11, 30), listOf(call))!!.lines())
        val checkup = habit(3, "Check-up", "🩺", Repeat.timesPer(Period.YEARLY, start), target = 1)
        assertNull(list(LocalDate(2026, 12, 30), listOf(checkup)))
        assertEquals(listOf("🩺 Check-up · 1 more this year"), list(LocalDate(2026, 12, 31), listOf(checkup))!!.lines())
    }

    @Test
    fun aWeekOnlyPartlyInTheGoalsDatesIsNeverMissedSoItIsNotListed() {
        // Started on the Saturday: that first week can't be missed.
        val gym = habit(1, "Gym", "🏋", Repeat.timesPer(Period.WEEKLY, LocalDate(2026, 11, 14)), target = 3)
        assertNull(list(sunday, listOf(gym)))
        assertEquals(listOf("🏋 Gym · 3 more this week"), list(sunday.plusDays(7), listOf(gym))!!.lines())
    }

    @Test
    fun countsAndAmountsSayHowFarAlongTheyAre() {
        val water = habit(1, "Water", "💧", target = 8)
        val pages = habit(2, "Read", "📖").copy(amount = 20.0, unit = "pages")
        val ticks = List(3) { tick(water, wednesday) } + tick(pages, wednesday, amount = 12.0)
        val open = list(wednesday, listOf(water, pages), ticks)!!
        assertEquals(listOf("💧 Water · 3 of 8", "📖 Read · 12 of 20 pages"), open.lines())
        // The amount reached: done.
        val reached = list(wednesday, listOf(water, pages), ticks + tick(pages, wednesday, amount = 8.0))!!
        assertEquals(listOf("💧 Water · 3 of 8"), reached.lines())
    }

    @Test
    fun aDatedToDoAndAProjectDeadlineTodayAreOpen() {
        val todo = Goal(id = 1, kind = GoalKind.TODO, name = "Post letter", icon = "✉", points = 2, repeat = Repeat(RepeatKind.ONCE, wednesday))
        val shelf = Goal(id = 2, kind = GoalKind.PROJECT, name = "Shelf", icon = "🔨", points = 0, repeat = null, deadline = wednesday)
        val later = Goal(id = 3, kind = GoalKind.PROJECT, name = "Garden", icon = "🌱", points = 0, repeat = null, deadline = wednesday.plusDays(3))
        val steps = listOf(
            Step(id = 10, goalId = 2, name = "Buy", points = 5, position = 0),
            Step(id = 11, goalId = 2, name = "Build", points = 5, position = 1),
            Step(id = 12, goalId = 3, name = "Dig", points = 5, position = 0),
        )
        val bought = Tick(goalId = 2, stepId = 10, at = 0, day = wednesday, period = null, periodStart = wednesday)
        val open = list(wednesday, listOf(todo, shelf, later), listOf(bought), steps = steps)!!
        assertEquals(listOf("✉ Post letter", "🔨 Shelf · 1 step left, deadline today"), open.lines())
        // Only the to-do can be ticked from the notification; a project's steps are ticked in the app.
        assertEquals(listOf("Done", null), open.items.map { it.tick })

        val built = bought.copy(stepId = 11)
        assertEquals(listOf("✉ Post letter"), list(wednesday, listOf(todo, shelf, later), listOf(bought, built), steps = steps)!!.lines())
        // The day before its date the to-do isn't due yet.
        assertNull(list(wednesday.minusDays(1), listOf(todo)))
    }

    @Test
    fun aGoalToQuitThatSlippedTodayIsAlreadyAMiss() {
        val sugar = habit(1, "No sugar", "🍬").copy(quit = true)
        assertEquals("Avoided", list(wednesday, listOf(sugar))!!.firstToTick!!.tick)
        assertNull(list(wednesday, listOf(sugar), lapses = listOf(lapse(sugar, wednesday, LapseKind.MISS))))
        // A miss that turned out to be a forgotten tick is no miss; yesterday's doesn't count for today.
        val forgotten = lapse(sugar, wednesday, LapseKind.MISS).copy(forgotten = true)
        assertEquals(1, list(wednesday, listOf(sugar), lapses = listOf(forgotten, lapse(sugar, wednesday.minusDays(1), LapseKind.MISS)))!!.items.size)
    }

    @Test
    fun aSinceLastDoneGoalStaysOnTheListWhileItIsLate() {
        // Missed on Monday and late since: due again every day until it is done.
        val late = Repeat(RepeatKind.AFTER_DONE, wednesday, interval = 3, lateSince = wednesday.minusDays(2))
        val plants = habit(1, "Water plants", "🪴", late)
        val missed = lapse(plants, wednesday.minusDays(2), LapseKind.MISS)
        assertEquals(listOf("🪴 Water plants"), list(wednesday, listOf(plants), lapses = listOf(missed))!!.lines())
        // Done today, it has moved on to its next day, and nothing is left open.
        val moved = plants.copy(repeat = late.copy(start = wednesday.plusDays(3), lateSince = null))
        assertNull(list(wednesday, listOf(moved), ticks = listOf(tick(plants, wednesday)), lapses = listOf(missed)))
        // Before its day comes it isn't due.
        assertNull(list(wednesday.minusDays(1), listOf(plants.copy(repeat = late.copy(lateSince = null)))))
    }

    @Test
    fun aRoutineIsOpenUntilEveryItemIsTicked() {
        // A routine is done by its own tick, once every item is ticked; its items alone don't finish it.
        val morning = habit(1, "Morning", "🌅")
        val items = listOf(
            Step(id = 10, goalId = 1, name = "Water", points = 0, position = 0),
            Step(id = 11, goalId = 1, name = "Stretch", points = 0, position = 1),
        )
        assertEquals(listOf("🌅 Morning"), list(wednesday, listOf(morning), steps = items)!!.lines())
        assertNull(list(wednesday, listOf(morning), ticks = listOf(tick(morning, wednesday)), steps = items))
    }

    @Test
    fun aLockedPrivateGoalIsOnlyAPrivateGoal() {
        val run = habit(1, "Run", "🏃")
        val therapy = habit(2, "Therapy notes", "🧠").copy(private = true)
        val meds = habit(3, "Meds", "💊", target = 2).copy(private = true)
        val plan = Goal(id = 4, kind = GoalKind.PROJECT, name = "Move out", icon = "📦", points = 0, repeat = null, deadline = wednesday, private = true)
        val goals = listOf(therapy, run, meds, plan)

        // The lock doesn't cover private goals: they are named like any other.
        val named = list(wednesday, goals)!!
        assertEquals(listOf("🏃 Run", "🧠 Therapy notes", "💊 Meds · 0 of 2", "📦 Move out · deadline today"), named.lines())
        assertEquals("🏃 Run", named.firstToTick!!.name)

        val locked = list(wednesday, goals, hidePrivate = true)!!
        assertEquals("4 still open today", locked.title)
        assertEquals(listOf("🏃 Run", "🔒 3 private goals"), locked.lines())
        val said = locked.title + locked.lines().joinToString() + locked.items.joinToString { it.words }
        listOf("Therapy", "Meds", "Move out", "🧠", "💊", "📦", "of 2", "deadline").forEach { assertFalse(said.contains(it), it) }
        // A button never ticks a private goal that isn't named.
        assertEquals(run.id, locked.firstToTick!!.goalId)
        assertTrue(locked.items.filter { it.hidden }.all { it.tick == null && it.left == null })

        val one = list(wednesday, listOf(therapy), hidePrivate = true)!!
        assertEquals(listOf("🔒 A private goal"), one.lines())
        assertNull(one.firstToTick)
    }

    @Test
    fun reasonsAndNotesAreNeverPartOfIt() {
        val gym = habit(1, "Gym", "🏋", Repeat.timesPer(Period.WEEKLY, start), target = 2)
        val read = habit(2, "Read", "📖")
        val skip = lapse(gym, sunday, LapseKind.SKIP, Reason.ILL, note = "sore knee")
        val earlier = lapse(read, sunday.minusDays(1), LapseKind.MISS, Reason.FORGOT, note = "fell asleep")
        val open = list(sunday, listOf(gym, read), lapses = listOf(skip, earlier))!!
        assertEquals(listOf("📖 Read", "🏋 Gym · 1 more this week"), open.lines())
        val said = open.title + open.lines().joinToString()
        listOf("Ill", "sore knee", "Forgot", "fell asleep").forEach { assertFalse(said.contains(it), it) }
    }

    @Test
    fun aLongListEndsWithHowManyMore() {
        val goals = (1L..9L).map { habit(it, "Goal $it") }
        val open = list(wednesday, goals)!!
        assertEquals("9 still open today", open.title)
        assertEquals((1..6).map { "⭐ Goal $it" } + "and 3 more", open.lines())
        assertEquals((1..4).map { "⭐ Goal $it" } + "and 5 more", open.lines(4))
        // Six fit as they are.
        assertEquals(6, list(wednesday, goals.take(6))!!.lines().size)
        // The first one in the user's own order is the one a button ticks, with what its button says.
        assertEquals(1L, open.firstToTick!!.goalId)
        assertEquals("+1", list(wednesday, listOf(habit(1, "Water", target = 8)))!!.firstToTick!!.tick)
    }

    @Test
    fun itComesOnceADayAtItsTime() {
        val nine = LocalTime(21, 0)
        val times = EveningCheck.between(LocalDateTime(2026, 11, 11, 21, 0), LocalDateTime(2026, 11, 13, 21, 0), nine, dayEndHour = 0)
        assertEquals(listOf(wednesday, wednesday.plusDays(1)), times.map { it.day })
        assertEquals(listOf(LocalDateTime(2026, 11, 11, 21, 0), LocalDateTime(2026, 11, 12, 21, 0)), times.map { it.at })
        // In its own minute, and not the minute before or after.
        fun inMinute(hour: Int, minute: Int) =
            EveningCheck.between(LocalDateTime(2026, 11, 11, hour, minute), LocalDateTime(2026, 11, 11, hour, minute + 1), nine, 0)
        assertEquals(1, inMinute(21, 0).size)
        assertTrue(inMinute(20, 58).isEmpty())
        assertTrue(inMinute(21, 1).isEmpty())

        // With the day ending at 4 am, 1:30 am belongs to the day before: Thursday's 1:30 is Wednesday's check.
        val late = EveningCheck.between(LocalDateTime(2026, 11, 12, 0, 0), LocalDateTime(2026, 11, 12, 4, 0), LocalTime(1, 30), dayEndHour = 4)
        assertEquals(listOf(EveningTime(wednesday, LocalDateTime(2026, 11, 12, 1, 30))), late)
    }

    @Test
    fun itsTimeMustBeBeforeTheDayEnds() {
        // From noon until the day ends: at midnight here.
        assertTrue(EveningCheck.allowed(LocalTime(12, 0), 0))
        assertTrue(EveningCheck.allowed(EveningCheck.SUGGESTED, 0))
        assertTrue(EveningCheck.allowed(LocalTime(23, 59), 0))
        assertFalse(EveningCheck.allowed(LocalTime(0, 0), 0))
        assertFalse(EveningCheck.allowed(LocalTime(1, 30), 0))
        assertFalse(EveningCheck.allowed(LocalTime(11, 59), 0))
        // With the day ending at 4 am, a late night still counts, up to 3:59.
        assertTrue(EveningCheck.allowed(LocalTime(1, 30), 4))
        assertTrue(EveningCheck.allowed(LocalTime(3, 59), 4))
        assertFalse(EveningCheck.allowed(LocalTime(4, 0), 4))
        assertFalse(EveningCheck.allowed(LocalTime(9, 0), 4))
        // 9 pm is before the day ends whenever the day ends.
        assertTrue((0..Days.MAX_DAY_END_HOUR).all { EveningCheck.allowed(EveningCheck.SUGGESTED, it) })

        // A time that isn't before the day's end any more (the end was moved earlier) sends nothing.
        val from = LocalDateTime(2026, 11, 11, 0, 0)
        assertTrue(EveningCheck.between(from, LocalDateTime(2026, 11, 14, 0, 0), LocalTime(1, 30), dayEndHour = 0).isEmpty())
        assertEquals(3, EveningCheck.between(from, LocalDateTime(2026, 11, 14, 0, 0), LocalTime(1, 30), dayEndHour = 4).size)
    }

    @Test
    fun theTimeIsKeptAsMinutesAndOffWhenEmpty() {
        assertEquals("1260", EveningCheck.encode(LocalTime(21, 0)))
        assertEquals(LocalTime(21, 0), EveningCheck.decode("1260"))
        assertEquals(LocalTime(0, 5), EveningCheck.decode(EveningCheck.encode(LocalTime(0, 5))))
        assertEquals("", EveningCheck.encode(null))
        listOf(null, "", "soon", "-1", "1440").forEach { assertNull(EveningCheck.decode(it), "$it") }
    }
}
