package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NudgesTest {
    private val day = LocalDate(2026, 11, 11)
    private val from = LocalDateTime(2026, 11, 11, 4, 0)
    private val to = LocalDateTime(2026, 11, 12, 4, 0)
    private val plan = NudgePlan(on = true, lateAfter = 30, every = 30, most = 12)

    private fun goal(
        at: LocalTime? = null,
        finishBy: LocalTime? = null,
        times: List<LocalTime> = emptyList(),
        target: Int = 1,
        reminder: Reminder? = null,
        kind: GoalKind = GoalKind.HABIT,
        repeat: Repeat? = Repeat.everyDay(day.minusDays(3)),
        id: Long = 1,
        points: Int = 5,
    ) = Goal(
        id = id, kind = kind, name = "Reading", icon = "📖", points = points, repeat = repeat,
        at = at, finishBy = finishBy, reminder = reminder, times = times, target = target,
    )

    private fun alerts(g: Goal, plan: NudgePlan? = this.plan, dayEndHour: Int = 4) =
        Alerts.between(listOf(g), from, to, dayEndHour = dayEndHour, nudges = plan)

    private fun nudges(g: Goal, plan: NudgePlan? = this.plan, dayEndHour: Int = 4) = alerts(g, plan, dayEndHour).filter { it.kind == AlertKind.NUDGE }

    // When they come

    @Test
    fun aGoalWithAClockTimeIsNudgedFromHalfAnHourLateEveryHalfHour() {
        val all = nudges(goal(at = LocalTime(18, 0)))
        assertEquals(12, all.size)
        assertEquals(LocalDateTime(2026, 11, 11, 18, 30), all.first().at)
        assertEquals(LocalDateTime(2026, 11, 12, 0, 0), all.last().at)
        assertEquals((1..12).toList(), all.map { it.nth })
        assertEquals(listOf(30, 60, 90), all.take(3).map { it.late })
        assertTrue(all.all { it.day == day && it.number == 0 })
    }

    @Test
    fun theyStopWhenTheDayEnds() {
        // The day ends at 4 am: 1 am belongs to the night after, and the last nudge comes before 4.
        val all = nudges(goal(at = LocalTime(1, 0)))
        assertEquals(listOf(LocalTime(1, 30), LocalTime(2, 0), LocalTime(2, 30), LocalTime(3, 0), LocalTime(3, 30)), all.map { it.at.time })
        assertTrue(all.all { it.at.date == day.plusDays(1) && it.day == day })
    }

    @Test
    fun noMoreThanTheMostADay() {
        assertEquals(12, nudges(goal(at = LocalTime(7, 0))).size)
        assertEquals(LocalTime(13, 0), nudges(goal(at = LocalTime(7, 0))).last().at.time)
        assertEquals(3, nudges(goal(at = LocalTime(7, 0)), plan.copy(most = 3)).size)
    }

    @Test
    fun theNumbersCanBeChanged() {
        val all = nudges(goal(at = LocalTime(10, 0)), plan.copy(lateAfter = 10, every = 45, most = 3))
        assertEquals(listOf(LocalTime(10, 10), LocalTime(10, 55), LocalTime(11, 40)), all.map { it.at.time })
        assertEquals(listOf(10, 55, 100), all.map { it.late })
        val rules = Rules(nudgeLateMinutes = 15, nudgeEveryMinutes = 20, nudgeMost = 4)
        assertEquals(NudgePlan(true, 15, 20, 4), Nudges.plan(null, null, rules))
        assertEquals(listOf("nudgeLateMinutes", "nudgeEveryMinutes", "nudgeMost"), Rules.FIELDS.filter { it.group == Rules.NUDGES }.map { it.key })
    }

    @Test
    fun eachTimeOfAGoalDoneSeveralTimesIsNudgedUntilTheNext() {
        val all = nudges(goal(times = listOf(LocalTime(9, 0), LocalTime(11, 0)), target = 2))
        val first = all.filter { it.number == 1 }
        assertEquals(listOf(LocalTime(9, 30), LocalTime(10, 0), LocalTime(10, 30)), first.map { it.at.time })
        val second = all.filter { it.number == 2 }
        assertEquals(12, second.size)
        assertEquals(LocalTime(11, 30), second.first().at.time)
        assertEquals(1, second.first().nth)
    }

    @Test
    fun withoutAClockTimeItsOwnFinishByTimeCounts() {
        val all = nudges(goal(finishBy = LocalTime(20, 0)))
        assertEquals(LocalTime(20, 30), all.first().at.time)
        // The default finish time in Settings is nobody's own.
        assertTrue(Alerts.between(listOf(goal()), from, to, dayEndHour = 4, defaultFinish = LocalTime(20, 0), nudges = plan).none { it.kind == AlertKind.NUDGE })
        // A clock time comes before a finish-by time.
        assertEquals(LocalTime(8, 30), nudges(goal(at = LocalTime(8, 0), finishBy = LocalTime(20, 0))).first().at.time)
    }

    @Test
    fun aTimeGivenForOneDayOnTheTimelineIsFollowed() {
        val g = goal(at = LocalTime(8, 0)).let { it.copy(repeat = Retimes.set(it.repeat!!, day, 15 * 60, day)) }
        assertEquals(LocalTime(15, 30), nudges(g).first().at.time)
    }

    @Test
    fun noneForTheKindsThatAreNotNudged() {
        val at = LocalTime(9, 0)
        assertTrue(nudges(goal(at = at).copy(archived = true)).isEmpty())
        assertTrue(nudges(goal(at = at).copy(limit = 2.0)).isEmpty(), "at most")
        assertTrue(nudges(goal(at = at).copy(quit = true)).isEmpty(), "to quit")
        assertTrue(nudges(goal(at = at, repeat = Repeat.timesPer(Period.WEEKLY, day.minusDays(3)), target = 3)).isEmpty(), "times per week")
        assertTrue(nudges(goal(finishBy = at, kind = GoalKind.PROJECT, repeat = null)).isEmpty(), "project")
        assertTrue(nudges(goal(at = at, kind = GoalKind.TODO, repeat = Repeat(RepeatKind.ONCE, day))).isEmpty(), "to-do")
        // And none on a day it isn't due, or with nothing to be late for.
        assertTrue(nudges(goal(at = at, repeat = Repeat(RepeatKind.WEEKDAYS, day.minusDays(30), weekdays = setOf(day.plusDays(2).dayOfWeek.ordinal + 1)))).isEmpty())
        assertTrue(nudges(goal()).isEmpty())
        assertFalse(Nudges.hasTime(goal()))
        assertTrue(Nudges.hasTime(goal(finishBy = at)) && Nudges.rightKind(goal(finishBy = at)))
    }

    @Test
    fun theSwitchesTurnThemOff() {
        val g = goal(at = LocalTime(9, 0))
        assertTrue(nudges(g, plan.copy(on = false)).isEmpty(), "Settings")
        assertTrue(nudges(g, NudgePlan.OFF).isEmpty())
        assertTrue(nudges(g, null).isEmpty(), "not asked for")
        assertTrue(nudges(g.copy(reminder = Reminder(nudge = false))).isEmpty(), "the goal's own switch")
        assertEquals(12, nudges(g.copy(reminder = Reminder())).size)
        assertFalse(Nudges.isOn("0"))
        assertTrue(Nudges.isOn(null) && Nudges.isOn("1"))
    }

    @Test
    fun stoppedForTodayMeansNoneThatDayOnly() {
        val g = goal(at = LocalTime(9, 0))
        val stopped = plan.copy(stoppedDay = day, stopped = setOf(1L))
        assertTrue(nudges(g, stopped).isEmpty())
        assertEquals(12, nudges(g.copy(id = 2), stopped).size, "another goal")
        assertEquals(12, nudges(g, plan.copy(stoppedDay = day.minusDays(1), stopped = setOf(1L))).size, "yesterday's stop is over")
    }

    @Test
    fun theStopSettingHoldsOneDay() {
        val first = Nudges.stop(null, 7, day)
        assertEquals("2026-11-11|7", first)
        val second = Nudges.stop(first, 3, day)
        assertEquals("2026-11-11|3,7", second)
        assertEquals(second, Nudges.stop(second, 3, day))
        assertEquals(day to setOf(3L, 7L), Nudges.stopped(second))
        // A new day starts a new list: the old day's goals are dropped.
        assertEquals("2026-11-12|9", Nudges.stop(second, 9, day.plusDays(1)))
        assertEquals(null, Nudges.stopped("nonsense"))
        assertEquals(null, Nudges.stopped(null))
        val held = Nudges.plan("1", second, Rules())
        assertTrue(held.stoppedOn(3, day) && !held.stoppedOn(3, day.plusDays(1)) && !held.stoppedOn(4, day))
    }

    // With "keep reminding"

    @Test
    fun keepRemindingStopsOnceTheGoalIsLate() {
        val g = goal(at = LocalTime(18, 0), reminder = Reminder(againEvery = 30))
        // Without nudges: the reminder and its repeats.
        assertTrue(alerts(g, null).any { it.kind == AlertKind.AGAIN })
        // With them: the reminder, then only nudges.
        val with = alerts(g)
        assertEquals(AlertKind.ON_TIME, with.first().kind)
        assertTrue(with.none { it.kind == AlertKind.AGAIN })
        assertEquals(12, with.count { it.kind == AlertKind.NUDGE })
        // No two at the same moment.
        assertEquals(with.size, with.map { it.at }.distinct().size)
    }

    @Test
    fun keepRemindingCarriesOnUntilTheFinishByTime() {
        val g = goal(finishBy = LocalTime(19, 0), reminder = Reminder(at = LocalTime(18, 0), againEvery = 30))
        assertEquals(
            listOf(
                LocalTime(18, 0) to AlertKind.REMINDER, LocalTime(18, 30) to AlertKind.AGAIN,
                LocalTime(19, 30) to AlertKind.NUDGE, LocalTime(20, 0) to AlertKind.NUDGE,
            ),
            alerts(g).take(4).map { it.at.time to it.kind },
        )
    }

    @Test
    fun keepRemindingStaysForAGoalThatIsNotNudged() {
        val g = goal(at = LocalTime(18, 0), reminder = Reminder(againEvery = 30, nudge = false))
        assertTrue(alerts(g).any { it.kind == AlertKind.AGAIN })
        assertTrue(alerts(g, plan.copy(on = false)).any { it.kind == AlertKind.AGAIN })
        // Stopped for today asks for quiet: no nudges, and no repeats back in their place.
        val quiet = alerts(goal(at = LocalTime(18, 0), reminder = Reminder(againEvery = 30)), plan.copy(stoppedDay = day, stopped = setOf(1L)))
        assertEquals(listOf(AlertKind.ON_TIME), quiet.map { it.kind })
    }

    @Test
    fun aPhoneThatKeepsOnlySoManyKeepsFirstRemindersBeforeNudges() {
        val goals = (1L..3L).map { goal(at = LocalTime(9, 0), id = it) }
        val all = Alerts.between(goals, from, LocalDateTime(2026, 11, 14, 4, 0), dayEndHour = 4, nudges = plan)
        val firsts = all.filter { it.kind == AlertKind.ON_TIME }
        assertEquals(9, firsts.size)
        val kept = Alerts.keep(all, 12)
        assertEquals(12, kept.size)
        assertTrue(kept.containsAll(firsts))
        assertEquals(3, kept.count { it.kind == AlertKind.NUDGE })
        assertEquals(kept.sortedBy { it.at }, kept)
        assertEquals(firsts.take(5), Alerts.keep(all, 5))
    }

    @Test
    fun aNudgeIsStillToDoUntilItsGoalIsDoneOrSkipped() {
        val g = goal(at = LocalTime(9, 0))
        val window = g.repeat!!.window(day)!!
        assertTrue(Alerts.stillToDo(g, window, emptyList(), emptyList()))
        val tick = Tick(id = 1, goalId = 1, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day)
        assertFalse(Alerts.stillToDo(g, window, listOf(tick), emptyList()))
    }

    // The card on Today

    @Test
    fun todayOffersToStopOnlyWhileLateWithNudgesToCome() {
        val g = goal(at = LocalTime(18, 0))
        fun can(hour: Int, minute: Int, plan: NudgePlan = this.plan, count: Int = 0, goal: Goal = g) =
            Nudges.canStop(goal, day, LocalDateTime(2026, 11, 11, hour, minute), count, 4, plan)
        assertFalse(can(17, 59), "not late yet")
        assertTrue(can(18, 0), "late, the first nudge is coming")
        assertTrue(can(21, 0))
        assertFalse(Nudges.canStop(g, day, LocalDateTime(2026, 11, 12, 0, 0), 0, 4, plan), "the last one has come")
        assertFalse(can(21, 0, plan.copy(on = false)))
        assertFalse(can(21, 0, plan.copy(stoppedDay = day, stopped = setOf(1L))))
        assertFalse(can(21, 0, goal = g.copy(reminder = Reminder(nudge = false))))
        // One of several a day: only while the time that is late isn't done.
        val twice = goal(times = listOf(LocalTime(9, 0), LocalTime(20, 0)), target = 2)
        assertTrue(can(9, 15, goal = twice))
        assertFalse(can(9, 15, goal = twice, count = 1), "the first is done and the second isn't late")
        assertTrue(can(20, 5, goal = twice, count = 1))
    }

    // Tones

    @Test
    fun theTonesSettingListsTheKindsThatAreOn() {
        assertEquals(NudgeTone.entries, Nudges.tones(null))
        assertEquals(listOf(NudgeTone.GAIN, NudgeTone.PLAYFUL), Nudges.tones("PLAYFUL,GAIN"))
        assertEquals(listOf(NudgeTone.ENCOURAGING), Nudges.tones(""), "all off is Encouraging only")
        assertEquals(listOf(NudgeTone.ENCOURAGING), Nudges.tones("nothing"))
        assertEquals("ENCOURAGING,SMALLER", Nudges.encodeTones(setOf(NudgeTone.SMALLER, NudgeTone.ENCOURAGING)))
        assertEquals("", Nudges.encodeTones(emptySet()))
    }

    @Test
    fun theToneGoesRoundTheOnesThatAreOn() {
        val all = (1..8).map { Nudges.tone(1, day, it, NudgeTone.entries) }
        assertEquals(NudgeTone.entries.toSet(), all.take(4).toSet())
        assertEquals(all.take(4), all.drop(4))
        val two = (1..6).map { Nudges.tone(1, day, it, listOf(NudgeTone.GAIN, NudgeTone.PLAYFUL)) }
        assertEquals(setOf(NudgeTone.GAIN, NudgeTone.PLAYFUL), two.toSet())
        assertTrue(two.zipWithNext().all { (a, b) -> a != b })
        assertTrue((1..5).all { Nudges.tone(1, day, it, emptyList()) == NudgeTone.ENCOURAGING })
    }

    @Test
    fun theFirstToneDiffersByDayAndGoal() {
        val byDay = (0..3).map { Nudges.tone(1, day.plusDays(it), 1, NudgeTone.entries) }
        assertEquals(4, byDay.toSet().size)
        val byGoal = (1L..4L).map { Nudges.tone(it, day, 1, NudgeTone.entries) }
        assertEquals(4, byGoal.toSet().size)
    }

    // Words

    private val facts = NudgeFacts()

    private fun texts(g: Goal, facts: NudgeFacts = this.facts, tones: List<NudgeTone> = NudgeTone.entries, locked: Boolean = false, day: LocalDate = this.day) =
        (1..12).map { Nudges.words(g, day, it, it * 30, facts, tones, locked) }

    @Test
    fun theTitleSaysHowLate() {
        val g = goal(at = LocalTime(9, 0))
        assertEquals("📖 Reading · 40 min late", Nudges.words(g, day, 1, 40, facts).title)
        assertEquals("📖 Reading · 1 h 10 min late", Nudges.words(g, day, 2, 70, facts).title)
        assertEquals("📖 Reading · 2 h late", Nudges.words(g, day, 3, 120, facts).title)
        assertEquals("📖 Reading · due now", Nudges.words(g, day, 1, 0, facts).title)
    }

    @Test
    fun noTwoNudgesInARowReadTheSame() {
        val goals = listOf(
            goal(at = LocalTime(9, 0)),
            goal(at = LocalTime(9, 0), points = 0),
            goal(at = LocalTime(9, 0)).copy(minutes = 3),
            goal(at = LocalTime(9, 0)).copy(minutes = 45, amount = 20.0, unit = "pages"),
            goal(times = listOf(LocalTime(9, 0), LocalTime(12, 0), LocalTime(18, 0)), target = 3),
            goal(at = LocalTime(9, 0)).copy(why = "so I sleep better"),
        )
        val toneSets = listOf(NudgeTone.entries) + NudgeTone.entries.map { listOf(it) } +
            listOf(listOf(NudgeTone.ENCOURAGING, NudgeTone.GAIN), listOf(NudgeTone.GAIN, NudgeTone.SMALLER, NudgeTone.PLAYFUL), emptyList())
        for (g in goals) for (tones in toneSets) for (offset in 0..6) for (locked in listOf(false, true)) {
            val f = NudgeFacts(streak = 12, count = 1)
            val said = texts(g, f, tones, locked, day.plusDays(offset)).map { it.text }
            assertTrue(said.zipWithNext().all { (a, b) -> a != b }, "$tones on day +$offset: $said")
        }
    }

    @Test
    fun eachToneHasAtLeastFiveDifferentTexts() {
        val g = goal(at = LocalTime(9, 0))
        for (tone in NudgeTone.entries) {
            val said = (1..40).map { Nudges.words(g, day, it, 30, facts, listOf(tone)) }
            assertTrue(said.all { it.tone == tone })
            assertTrue(said.map { it.text }.toSet().size >= 5, "$tone: ${said.map { it.text }.toSet()}")
        }
        assertTrue((1..40).map { Nudges.words(g, day, it, 30, facts, locked = true).text }.toSet().size >= 5)
        // Why it's good, with a reason of the goal's own: worded differently too, with and without a streak.
        val why = g.copy(why = "so I sleep better")
        for (f in listOf(NudgeFacts(), NudgeFacts(streak = 12))) {
            assertTrue((1..40).map { Nudges.words(why, day, it, 30, f, listOf(NudgeTone.GAIN)).text }.toSet().size >= 5)
        }
    }

    @Test
    fun whyItsGoodLeadsWithTheGoalsOwnReasonThenTheStreak() {
        val g = goal(at = LocalTime(9, 0)).copy(why = "so I sleep better")
        val why = listOf(NudgeTone.GAIN)
        assertEquals("Why it's good", NudgeTone.GAIN.label)
        // The reason alone: no streak under 2 days.
        val plain = texts(g, NudgeFacts(streak = 1), why).map { it.text }
        assertTrue(plain.all { "so I sleep better." in it }, "$plain")
        assertTrue(plain.none { "streak" in it || "1" in it }, "$plain")
        // A streak of 2 or more comes after the reason, with its real length.
        val streak = texts(g, NudgeFacts(streak = 12), why).map { it.text }
        assertTrue(streak.all { "12-day streak" in it && it.indexOf("so I sleep better") < it.indexOf("streak") }, "$streak")
        // The user's own words are kept as they are, with no second full stop after their own.
        val own = texts(g.copy(why = "  To feel\n calm!  "), NudgeFacts(), why).map { it.text }
        assertTrue(own.all { it.endsWith("To feel calm!") }, "$own")
        // No number that wasn't given.
        val digits = Regex("\\d+")
        assertTrue(streak.all { text -> digits.findAll(text).all { it.value == "12" } }, "$streak")
    }

    @Test
    fun whyItsGoodWithNoReasonUsesTheStreak() {
        val g = goal(at = LocalTime(9, 0))
        val said = texts(g, NudgeFacts(streak = 12), listOf(NudgeTone.GAIN)).map { it.text }
        assertTrue(said.all { "Reading" in it && "12-day streak" in it }, "$said")
        assertTrue(said.toSet().size >= 5, "$said")
    }

    @Test
    fun aGoalWithNeitherAReasonNorAStreakGetsAnEncouragingTextInstead() {
        val g = goal(at = LocalTime(9, 0))
        val said = texts(g, NudgeFacts(streak = 1), listOf(NudgeTone.GAIN)).map { it.text }
        val encouraging = (1..14).map { Nudges.words(g, day, it, 30, facts, listOf(NudgeTone.ENCOURAGING)).text }.toSet()
        assertTrue(said.all { it in encouraging }, "$said")
        assertTrue(said.none { "point" in it || "streak" in it })
        // A reason that is only spaces is none.
        assertEquals(said, texts(g.copy(why = "   "), NudgeFacts(streak = 1), listOf(NudgeTone.GAIN)).map { it.text })
    }

    @Test
    fun noNudgeSaysWhatTheGoalPays() {
        val goals = listOf(
            goal(at = LocalTime(9, 0), points = 25),
            goal(at = LocalTime(9, 0), points = 25).copy(why = "so I sleep better"),
            goal(at = LocalTime(9, 0), points = 25).copy(minutes = 3),
            goal(times = listOf(LocalTime(9, 0), LocalTime(12, 0), LocalTime(18, 0)), target = 3, points = 25),
        )
        for (g in goals) for (offset in 0..6) {
            for (words in texts(g, NudgeFacts(streak = 9, count = 1), day = day.plusDays(offset))) {
                val low = words.text.lowercase()
                assertFalse("point" in low || "coin" in low || "+" in low || "25" in low || "reward" in low || "closer" in low, words.text)
            }
        }
    }

    @Test
    fun makeItSmallerOffersTheNextSmallPiece() {
        val smaller = listOf(NudgeTone.SMALLER)
        val plain = texts(goal(at = LocalTime(9, 0)), tones = smaller).map { it.text }
        assertTrue("Just the first 5 minutes of Reading counts as a start." in plain)
        assertTrue("Just start; you can stop after 2 minutes." in plain)
        val quick = texts(goal(at = LocalTime(9, 0)).copy(minutes = 3), tones = smaller).map { it.text }
        assertTrue(quick.any { "about 3 min" in it } && quick.none { "5 minutes" in it }, "$quick")
        val pages = texts(goal(at = LocalTime(9, 0)).copy(amount = 20.0, unit = "pages"), tones = smaller).map { it.text }
        assertTrue("Just 2 pages of Reading is a start." in pages, "$pages")
        val counted = texts(goal(times = listOf(LocalTime(9, 0), LocalTime(12, 0), LocalTime(18, 0)), target = 3), facts.copy(count = 1), smaller).map { it.text }
        assertTrue("Just the next one: Reading, 2 of 3." in counted, "$counted")
        assertEquals(2.0, Nudges.smallPiece(20.0))
        assertEquals(1.0, Nudges.smallPiece(5.0))
        assertEquals(0.5, Nudges.smallPiece(1.0))
        assertEquals(0.25, Nudges.smallPiece(0.5))
    }

    @Test
    fun aLockedPrivateGoalIsNeverNamed() {
        val g = goal(at = LocalTime(9, 0), points = 7).copy(private = true, name = "Therapy homework", icon = "🧠", minutes = 20, why = "so my head is clearer")
        val f = NudgeFacts(streak = 12)
        for (words in texts(g, f, locked = true)) {
            assertEquals("🔒 A private goal", words.title)
            val all = words.title + " " + words.text
            assertFalse("Therapy" in all || "🧠" in all || "head" in all || "7" in all || "12" in all || "streak" in all, all)
        }
        assertEquals("Still to do", Alerts.lockedWords(Alert(1, day, from, AlertKind.NUDGE)))
    }

    @Test
    fun nothingBlamesAndPlayfulHasAtMostOneEmoji() {
        val goals = listOf(goal(at = LocalTime(9, 0)), goal(at = LocalTime(9, 0)).copy(minutes = 30, amount = 3.0, unit = "km"))
        val bad = listOf("fail", "miss", "penalt", "reason", "mood", "should have", "lazy", "again?", "forgot", "behind", "shame", "guilt", "disappoint")
        val said = goals.flatMap { g ->
            (0..6).flatMap { offset -> texts(g, NudgeFacts(streak = 9), day = day.plusDays(offset)) } +
                (0..6).flatMap { offset -> texts(g, locked = true, day = day.plusDays(offset)) }
        }
        for (words in said) {
            val low = words.text.lowercase()
            assertTrue(bad.none { it in low }, words.text)
        }
        val emoji = Regex("[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]|[\\u2600-\\u27BF]")
        val playful = (1..14).map { Nudges.words(goal(at = LocalTime(9, 0)).copy(icon = ""), day, it, 30, facts, listOf(NudgeTone.PLAYFUL)).text }
        assertTrue(playful.all { emoji.findAll(it).count() <= 1 }, "$playful")
    }

    @Test
    fun theSameNudgeAlwaysReadsTheSame() {
        val g = goal(at = LocalTime(9, 0))
        assertEquals(Nudges.words(g, day, 3, 90, facts), Nudges.words(g, day, 3, 90, facts))
        assertNotEquals(Nudges.words(g, day, 3, 90, facts).text, Nudges.words(g, day, 4, 120, facts).text)
    }

    // Storage

    @Test
    fun theGoalsSwitchIsKeptWithItsReminderAndOldOnesStillRead() {
        // Saved before late nudges: no "nudge" in the text. It reads as on.
        val old = RepeatText.decodeReminder("""{"onTime":true,"minutesBefore":10,"againEvery":30}""")
        assertEquals(Reminder(onTime = true, minutesBefore = 10, againEvery = 30, nudge = true), old)
        val off = Reminder(nudge = false)
        assertEquals(off, RepeatText.decodeReminder(RepeatText.encodeReminder(off)))
        assertTrue(RepeatText.decodeReminder(RepeatText.encodeReminder(Reminder())).nudge)
    }

    @Test
    fun theNumbersShowInPointsAndRulesWhileNudgesAreOn() {
        val usual = Rules()
        fun shown(nudges: Boolean, rules: Rules = usual) = Offered.rules(Rules.FIELDS, emptySet(), rules, usual, nudges).map { it.key }
        assertTrue("nudgeEveryMinutes" in shown(true) && "nudgeLateMinutes" in shown(true) && "nudgeMost" in shown(true))
        assertFalse("nudgeEveryMinutes" in shown(false) || "nudgeMost" in shown(false))
        assertTrue("nudgeMost" in shown(false, usual.copy(nudgeMost = 6)), "changed ones stay")
        assertTrue("penaltyPercent" in shown(false))
        assertEquals(5, Rules(nudgeEveryMinutes = 1).sane().nudgeEveryMinutes)
        assertEquals(48, Rules(nudgeMost = 500).sane().nudgeMost)
    }
}
