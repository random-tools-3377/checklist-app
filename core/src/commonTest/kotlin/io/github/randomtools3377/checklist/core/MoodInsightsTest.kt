package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mood and your goals: which habits go with a better mood, which with a worse one, and ways in. */
class MoodInsightsTest {
    private val today = LocalDate(2026, 11, 11)
    private val start = today.minusDays(200)

    @AfterTest
    fun putTheUsualNamesBack() {
        PointsName.current = PointsName.DEFAULT
    }

    private fun habit(id: Long, name: String, points: Int = 10, position: Int = id.toInt(), after: Long? = null) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = points, repeat = Repeat.everyDay(start), position = position, after = after)

    private fun ago(days: Int) = today.minusDays(days)

    private fun tick(goal: Goal, day: LocalDate, period: Period = Period.DAILY, periodStart: LocalDate = day) =
        Tick(goalId = goal.id, stepId = null, at = 0, day = day, period = period, periodStart = periodStart)

    /** Moods on the days 1, 2, 3, … days ago, in that order. */
    private fun moods(vararg scores: Int) = scores.mapIndexed { i, score -> Mood(ago(i + 1), score) }

    @Test
    fun aGoalDoneOnBetterDaysLiftsTheMood() {
        val run = habit(1, "Run")
        // Done 1 to 5 days ago, which felt 4; not done 6 to 10 days ago, which felt 2.
        val found = MoodInsights.find(listOf(run), (1..5).map { tick(run, ago(it)) }, moods(4, 4, 4, 4, 4, 2, 2, 2, 2, 2), today, Rules())

        val lift = found.lifts.single()
        assertEquals(run.id, lift.goal.id)
        assertEquals(5, lift.doneDays)
        assertEquals(5, lift.notDoneDays)
        assertEquals(4.0, lift.moodDone)
        assertEquals(2.0, lift.moodNotDone)
        assertEquals(2.0, lift.difference)
        assertTrue(found.weighs.isEmpty())
        assertEquals(1, found.compared)
        assertEquals("On days you do it, your mood is about 2.0 higher.", MoodInsights.describe(lift))
        assertEquals("From 5 days done and 5 days not done.", MoodInsights.basis(lift))
        assertEquals("Goals that often go with a better mood: ⭐ Run.", MoodInsights.liftsLine(found))
    }

    @Test
    fun aGoalDoneOnWorseDaysWeighsAndGetsKindWaysIn() {
        val run = habit(1, "Run")
        val walk = habit(2, "Walk")
        val desk = habit(3, "Tidy desk", points = 5)
        val ticks = (1..5).map { tick(run, ago(it)) } +
            listOf(1, 2, 3, 6, 7, 8).map { tick(walk, ago(it)) } +
            (6..10).map { tick(desk, ago(it)) }
        val found = MoodInsights.find(listOf(run, walk, desk), ticks, moods(4, 4, 4, 4, 4, 2, 2, 2, 2, 2), today, Rules())

        // Walk is done as often on good days as on hard ones, so it is neither.
        assertEquals(listOf("Run"), found.lifts.map { it.goal.name })
        assertEquals(listOf("Tidy desk"), found.weighs.map { it.goal.name })
        val weigh = found.weighs.single()
        assertEquals(-2.0, weigh.difference)
        assertEquals("On days you do it, your mood is about 2.0 lower.", MoodInsights.describe(weigh))

        val ways = MoodInsights.ways(weigh, found, listOf(run, walk, desk))
        assertEquals(listOf(WayKind.AFTER, WayKind.SMALLER, WayKind.PAY_MORE), ways.map { it.kind })
        assertEquals(run.id, ways[0].after?.id)
        assertTrue(ways[0].canChain)
        assertTrue("Run" in ways[0].text)
        assertEquals(8, ways[2].points, "half as much again, rounded")
        assertEquals("Pay more for it: 8 points instead of 5, so it feels worth it.", ways[2].text)

        // Already right after Run: the next goal that lifts, if any, or the other ways.
        val chained = desk.copy(after = run.id)
        val again = MoodInsights.find(listOf(run, chained), ticks, moods(4, 4, 4, 4, 4, 2, 2, 2, 2, 2), today, Rules())
        assertEquals(listOf(WayKind.SMALLER, WayKind.PAY_MORE, WayKind.EARLY), MoodInsights.ways(again.weighs.single(), again, listOf(run, chained)).map { it.kind })

        // A chain that would loop back can't be set, but the words still stand.
        val runAfterDesk = run.copy(after = desk.id)
        val looped = MoodInsights.find(listOf(runAfterDesk, desk), ticks, moods(4, 4, 4, 4, 4, 2, 2, 2, 2, 2), today, Rules())
        val way = MoodInsights.ways(looped.weighs.single(), looped, listOf(runAfterDesk, desk)).first()
        assertEquals(WayKind.AFTER, way.kind)
        assertFalse(way.canChain)
    }

    @Test
    fun liftsAndWeighsAreBiggestFirst() {
        val big = habit(1, "Big")
        val small = habit(2, "Small")
        val heavy = habit(3, "Heavy")
        val light = habit(4, "Light")
        // Moods 5, 5, 5, 3, 3, 3 over the last six days.
        val ticks = listOf(1, 2, 3).map { tick(big, ago(it)) } + // 5 against 3: 2.0
            listOf(1, 2, 4).map { tick(small, ago(it)) } + // 4.33 against 3.67: 0.67
            listOf(4, 5, 6).map { tick(heavy, ago(it)) } + // 3 against 5: -2.0
            listOf(3, 5, 6).map { tick(light, ago(it)) } // 3.67 against 4.33: -0.67
        val found = MoodInsights.find(listOf(light, heavy, small, big), ticks, moods(5, 5, 5, 3, 3, 3), today, Rules())
        assertEquals(listOf("Big", "Small"), found.lifts.map { it.goal.name })
        assertEquals(listOf("Heavy", "Light"), found.weighs.map { it.goal.name })
        assertEquals("0.7", MoodInsights.tenths(found.lifts[1].difference!!))
    }

    @Test
    fun itNeedsThreeDaysEachWayAndHalfAPointOfDifference() {
        val run = habit(1, "Run")
        // Only two days done: not compared yet.
        val two = MoodInsights.find(listOf(run), listOf(1, 2).map { tick(run, ago(it)) }, moods(5, 5, 1, 1, 1, 1), today, Rules())
        assertTrue(two.lifts.isEmpty())
        assertNull(two.lifts.firstOrNull())
        assertEquals(0, two.compared)
        assertFalse(two.enough)
        // With the rule changed to two days, it is.
        val changed = MoodInsights.find(listOf(run), listOf(1, 2).map { tick(run, ago(it)) }, moods(5, 5, 1, 1, 1, 1), today, Rules(moodMinDays = 2))
        assertEquals(listOf("Run"), changed.lifts.map { it.goal.name })

        // Exactly half a point counts.
        val half = MoodInsights.find(listOf(run), (1..3).map { tick(run, ago(it)) }, moods(4, 4, 4, 3, 4, 4, 3), today, Rules())
        assertEquals(0.5, half.lifts.single().difference)
        // So does half a point from averages that don't come out exact: 4.33 against 3.83.
        val inexact = MoodInsights.find(listOf(run), (1..3).map { tick(run, ago(it)) }, moods(4, 4, 5, 4, 4, 3, 4, 4, 4), today, Rules())
        assertEquals(1, inexact.lifts.size)
        assertEquals("0.5", MoodInsights.tenths(inexact.lifts.single().difference!!))
        // 0.4 doesn't: compared, but nothing stands out.
        val less = MoodInsights.find(listOf(run), (1..5).map { tick(run, ago(it)) }, moods(4, 4, 4, 4, 4, 4, 4, 3, 4, 3), today, Rules())
        assertTrue(less.lifts.isEmpty() && less.weighs.isEmpty())
        assertEquals(1, less.compared)
        assertTrue(less.enough)
        // And with the notable difference raised to a whole point, half a point isn't enough.
        assertTrue(MoodInsights.find(listOf(run), (1..3).map { tick(run, ago(it)) }, moods(4, 4, 4, 3, 4, 4, 3), today, Rules(moodNotableTenths = 10)).lifts.isEmpty())
    }

    @Test
    fun theMoodOnceEverythingIsDoneCountsOverTheMorningAndADropWeighs() {
        val run = habit(1, "Run")
        // Mornings of 2 that end at 4 on days it was done; plain 3s on days it wasn't.
        val lifted = (1..3).map { Mood(ago(it), 2, after = 4) } + (4..6).map { Mood(ago(it), 3) }
        val found = MoodInsights.find(listOf(run), (1..3).map { tick(run, ago(it)) }, lifted, today, Rules())
        val lift = found.lifts.single()
        assertEquals(4.0, lift.moodDone)
        assertEquals(1.0, lift.difference)
        assertEquals(3, lift.riseDays)
        assertEquals(2.0, lift.rise)

        // Good mornings that end lower on days it was done weigh, though the day's mood is the same.
        val late = habit(2, "Late shift")
        val dropped = (1..3).map { Mood(ago(it), 5, after = 4) } + (4..6).map { Mood(ago(it), 4) }
        val drop = MoodInsights.find(listOf(late), (1..3).map { tick(late, ago(it)) }, dropped, today, Rules())
        assertTrue(drop.lifts.isEmpty())
        val weigh = drop.weighs.single()
        assertEquals(0.0, weigh.difference)
        assertEquals(-1.0, weigh.rise)
        assertEquals(
            "On days you do it, your mood is about the same, and it drops about 1.0 from the morning to the end of those days.",
            MoodInsights.describe(weigh),
        )
    }

    @Test
    fun onlyTheNinetyDaysBeforeTodayCountAndOnlyActiveHabits() {
        val run = habit(1, "Run")
        val gone = habit(2, "Gone").copy(archived = true)
        val project = Goal(id = 3, kind = GoalKind.PROJECT, name = "Move", icon = "📦", points = 0, repeat = null)
        val ticks = (1..3).map { tick(run, ago(it)) } + (1..3).map { tick(gone, ago(it)) } + tick(run, today) + tick(run, ago(95))
        val moods = moods(5, 5, 5, 1, 1, 1) + Mood(today, 5) + Mood(ago(95), 5) + Mood(ago(96), 1)
        val found = MoodInsights.find(listOf(run, gone, project), ticks, moods, today, Rules())
        val lift = found.lifts.single()
        assertEquals("Run", lift.goal.name)
        assertEquals(3, lift.doneDays)
        assertEquals(3, lift.notDoneDays)
        assertEquals(1, found.compared)
    }

    @Test
    fun withNoMoodsThereIsNothingToShow() {
        val run = habit(1, "Run")
        val found = MoodInsights.find(listOf(run), (1..10).map { tick(run, ago(it)) }, emptyList(), today, Rules())
        assertEquals(MoodFindings.NONE, found)
        assertFalse(found.enough)
        assertNull(MoodInsights.liftsLine(found))
        assertTrue("couple of weeks" in MoodInsights.NOT_ENOUGH)
    }

    @Test
    fun aGoalCountedOverAWeekIsDoneOnItsDaysAndNotDoneWhileStillToDo() {
        val gym = Goal(id = 1, kind = GoalKind.HABIT, name = "Gym", icon = "🏋️", points = 10, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 2)
        val lastWeek = LocalDate(2026, 11, 2)
        val weekBefore = LocalDate(2026, 10, 26)
        val ticks = listOf(
            tick(gym, lastWeek, Period.WEEKLY, lastWeek),
            tick(gym, lastWeek.plusDays(1), Period.WEEKLY, lastWeek),
            tick(gym, weekBefore, Period.WEEKLY, weekBefore),
        )
        val ticked = ticks.map { it.day }
        // A mood every day from 26 October to yesterday: 5 on the days it was done, 3 on the rest.
        val moods = (1..16).map { Mood(ago(it), if (ago(it) in ticked) 5 else 3) }
        val lift = MoodInsights.find(listOf(gym), ticks, moods, today, Rules()).lifts.single()
        // Done: the three days it was ticked. Not done: the rest of the week before, and this
        // week so far. Last week, once it was finished, counts neither way.
        assertEquals(3, lift.doneDays)
        assertEquals(8, lift.notDoneDays)
        assertEquals(2.0, lift.difference)
    }

    @Test
    fun everythingDoneMeansAllOfTodayLeavingSkipsOut() {
        val window = Window(today, today, Period.DAILY)
        val run = habit(1, "Run")
        val read = habit(2, "Read")
        val water = habit(3, "Water").copy(target = 2)
        assertFalse(MoodInsights.allDone(emptyList()), "nothing due")
        assertTrue(MoodInsights.allDone(listOf(HabitRow(run, 1, window), HabitRow(water, 2, window))))
        assertFalse(MoodInsights.allDone(listOf(HabitRow(run, 1, window), HabitRow(water, 1, window))), "one of two glasses")
        assertTrue(MoodInsights.allDone(listOf(HabitRow(run, 1, window), HabitRow(read, 0, window, skips = 1))), "the skipped one is left out")
        assertFalse(MoodInsights.allDone(listOf(HabitRow(read, 0, window, skips = 1))), "only skipped: nothing was done")
    }

    @Test
    fun theCheckInSaysTheRightThing() {
        assertEquals("You started the day feeling low. You've done everything — how do you feel now?", MoodInsights.question(2))
        assertEquals("You started the day feeling low. You've done everything — how do you feel now?", MoodInsights.question(1))
        assertTrue(MoodInsights.question(3).endsWith("How do you feel now?"))

        // Only how it moved: never why.
        assertEquals("Up from 🙁 to 🙂.", MoodInsights.answer(2, 4))
        assertEquals("Same as this morning.", MoodInsights.answer(2, 2))
        assertEquals("Same as this morning.", MoodInsights.answer(5, 5))
        assertEquals("Down from 🙂 to 🙁 — that's all right.", MoodInsights.answer(4, 2))
        assertEquals("Feeling 😐 now.", MoodInsights.answer(null, 3))
        for (morning in 1..5) for (after in 1..5) {
            val said = MoodInsights.answer(morning, after).lowercase()
            for (word in listOf("helped", "because", "done", "lift")) assertFalse(word in said, "\"$said\" says $word")
        }
        assertEquals("What played a part?", MoodInsights.PART_QUESTION)
        assertEquals("What's behind it?", MoodInsights.BEHIND_QUESTION)
    }

    @Test
    fun theWordsSayWhatGoesTogetherNeverWhatCauses() {
        val run = habit(1, "Run")
        val desk = habit(2, "Desk")
        val found = MoodFindings(listOf(GoalMood(run, 5, 5, 4.0, 2.0)), listOf(GoalMood(desk, 5, 5, 2.0, 4.0)), 2)
        val words = listOf(
            MoodInsights.liftsLine(found)!!, MoodInsights.NOT_ENOUGH, MoodInsights.NOTHING_STANDS_OUT, MoodInsights.CAVEAT,
            MoodInsights.describe(found.lifts[0]), MoodInsights.describe(found.weighs[0]),
        ) + MoodInsights.ways(found.weighs[0], found, listOf(run, desk)).map { it.text }
        for (text in words) {
            for (word in listOf("helped", "lifts you", "lift you", "makes a difference", "because")) assertFalse(word in text, "\"$text\" says $word")
        }
        assertEquals("These go together; it doesn't say what causes what.", MoodInsights.CAVEAT)
    }

    @Test
    fun aLowMorningStartsWithAGoalThatLifts() {
        val window = Window(today, today, Period.DAILY)
        val run = habit(1, "Run")
        val read = habit(2, "Read")
        val found = MoodFindings(lifts = listOf(GoalMood(run, 5, 5, 4.0, 2.0), GoalMood(read, 5, 5, 3.5, 2.5)), weighs = emptyList(), compared = 2)
        assertEquals(run, MoodInsights.startWith(found, listOf(HabitRow(read, 0, window), HabitRow(run, 0, window))))
        // Run already done: Read next. Both done: nothing.
        assertEquals(read, MoodInsights.startWith(found, listOf(HabitRow(read, 0, window), HabitRow(run, 1, window))))
        assertNull(MoodInsights.startWith(found, listOf(HabitRow(read, 1, window), HabitRow(run, 1, window))))
        assertNull(MoodInsights.startWith(MoodFindings.NONE, listOf(HabitRow(read, 0, window))))
        assertEquals("Start with ⭐ Run — it usually lifts you.", MoodInsights.startWithLine(run))
    }

    @Test
    fun payingMoreIsHalfAsMuchAgainAndAtLeastOne() {
        assertEquals(listOf(1, 2, 5, 8, 15, 3), listOf(0, 1, 3, 5, 10, 2).map { MoodInsights.payMore(it) })
        PointsName.current = PointsName("coin", "coins", "🪙")
        val desk = habit(1, "Desk", points = 4)
        val way = MoodInsights.ways(GoalMood(desk, 3, 3, 2.0, 4.0), MoodFindings.NONE, listOf(desk)).first { it.kind == WayKind.PAY_MORE }
        assertEquals("Pay more for it: 6 coins 🪙 instead of 4, so it feels worth it.", way.text)
    }

    @Test
    fun theSecondMoodNotesAndFactorsGoThroughTheBackup() {
        val day = LocalDate(2026, 11, 10)
        val moods = listOf(
            Mood(day, 2, "rough start", at = 1_000, after = 4, afterAt = 2_000, afterNote = "a walk with a friend", factors = listOf("Slept badly", "Friends and family")),
            Mood(day.minusDays(1), 3, at = 500),
        )
        val backup = Backup(exportedAt = 0, goals = emptyList(), steps = emptyList(), ticks = emptyList(), entries = emptyList(), rewards = emptyList(), purchases = emptyList(), settings = emptyMap(), moods = moods)
        val back = Backup.decode(Backup.encode(backup))
        assertEquals(27, back.format)
        assertEquals("a walk with a friend", back.moods[0].afterNote)
        assertEquals(listOf("Slept badly", "Friends and family"), back.moods[0].factors)
        assertEquals(moods, back.moods)
        assertEquals(4, back.moods[0].last)
        assertEquals(3, back.moods[1].last)

        // A file from before has no second mood, notes after it or factors.
        val older = Backup.encode(backup.copy(moods = listOf(Mood(day, 3, at = 7))))
            .replace(Regex(",\\s*\"(after|afterAt|afterNote)\": null|,\\s*\"factors\": \\[\\s*]"), "")
        assertTrue("\"after" !in older && "\"factors\"" !in older)
        assertEquals(listOf(Mood(day, 3, at = 7)), Backup.decode(older).moods)
    }

    @Test
    fun thingsNotedOnBetterAndLowerDaysAreFound() {
        // Moods over the last eight days: "Slept well" on the good ones, "News" on the low ones,
        // "Work" on both alike, and "Coffee" noted only twice.
        val moods = listOf(
            Mood(ago(1), 4, factors = listOf("Slept well", "Work", "Coffee")),
            Mood(ago(2), 5, factors = listOf("Slept well")),
            Mood(ago(3), 4, factors = listOf("slept WELL", "Work")),
            Mood(ago(4), 2, factors = listOf("News", "Work", "Coffee")),
            Mood(ago(5), 2, factors = listOf("News")),
            Mood(ago(6), 3, factors = listOf("News", "Work")),
            Mood(ago(7), 2, after = 4, factors = listOf("Slept well")),
            Mood(ago(8), 3),
        )
        val found = MoodInsights.find(emptyList(), emptyList(), moods, today, Rules())
        assertTrue(found.lifts.isEmpty() && found.weighs.isEmpty())
        assertTrue(found.enough)

        // Slept well: 4, 5, 4 and 4 (the mood once everything was done counts) against 2, 2, 3, 3.
        val slept = found.betterFactors.single()
        assertEquals("Slept well", slept.factor, "one name, whatever its capitals")
        assertEquals(4, slept.notedDays)
        assertEquals(4, slept.otherDays)
        assertEquals(4.25, slept.moodNoted)
        assertEquals(2.5, slept.moodOther)
        assertEquals("Slept well: your mood is about 1.8 higher on days you note it.", MoodInsights.describe(slept))
        assertEquals("From 4 days noted and 4 days not.", MoodInsights.basis(slept))

        // News: 2, 2, 3 against 4, 5, 4, 4, 3.
        val news = found.lowerFactors.single()
        assertEquals("News", news.factor)
        assertEquals("News: your mood is about 1.7 lower on days you note it.", MoodInsights.describe(news))

        // Work goes with neither; Coffee isn't compared, with only two days.
        assertEquals(3, found.factorsCompared, "Slept well, Work and News")
        assertTrue(found.betterFactors.none { it.factor == "Coffee" } && found.lowerFactors.none { it.factor == "Coffee" })

        // The same rules as the goals: a whole point needed, or two days enough.
        assertTrue(MoodInsights.find(emptyList(), emptyList(), moods, today, Rules(moodNotableTenths = 20)).betterFactors.isEmpty())
        val two = MoodInsights.find(emptyList(), emptyList(), moods, today, Rules(moodMinDays = 2))
        assertEquals(4, two.factorsCompared)
        // Only the 90 days before today count.
        val old = moods.map { it.copy(day = it.day.minusDays(100)) }
        assertEquals(MoodFindings.NONE, MoodInsights.find(emptyList(), emptyList(), old, today, Rules()))
    }

    @Test
    fun theListOfThingsStartsWithTheAppsOwnAndCanChange() {
        assertEquals(MoodFactors.BUILT_IN, MoodFactors.list(null))
        assertEquals(13, MoodFactors.BUILT_IN.size)
        // Every one taken off: an empty list, not the app's own again.
        assertEquals(emptyList(), MoodFactors.list(MoodFactors.encodeList(emptyList())))
        val mine = MoodFactors.add(MoodFactors.remove(MoodFactors.BUILT_IN, "news"), "  Coffee \n time ")
        assertFalse(MoodFactors.has(mine, "News"))
        assertEquals("Coffee time", mine.last())
        assertEquals(mine, MoodFactors.list(MoodFactors.encodeList(mine)))
        assertEquals(mine, MoodFactors.add(mine, "coffee TIME"), "already there")
        assertTrue(MoodFactors.has(MoodFactors.withBuiltIns(mine), "News"))
        assertEquals(MoodFactors.LENGTH, MoodFactors.tidy("x".repeat(80)).length)

        // A day keeps a thing taken off the list, and it is still offered for that day.
        assertEquals(mine + "News", MoodFactors.offered(mine, listOf("News")))
        assertEquals(mine, MoodFactors.offered(mine, listOf("coffee time")), "already on the list, as the list spells it")

        // Picking and unpicking.
        val picked = MoodFactors.toggle(emptyList(), "Work")
        assertEquals(listOf("Work"), picked)
        assertEquals(listOf("Work", "Food"), MoodFactors.toggle(picked, "Food"))
        assertEquals(emptyList(), MoodFactors.toggle(picked, "work"))
        assertEquals(picked, MoodFactors.toggle(picked, "   "))
        assertEquals(listOf("A", "B"), MoodFactors.decode(MoodFactors.encode(listOf("A", "", " B ", "a"))))
        assertNull(MoodFactors.encode(emptyList()))
    }
}
