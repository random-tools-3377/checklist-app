package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionTest {
    private val day = LocalDate(2026, 11, 11)
    private val water = Goal(id = 1, kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 5, repeat = null, minutes = 2)
    private val run = Goal(id = 2, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 20, repeat = null, minutes = 30)
    private val read = Goal(id = 3, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 10, repeat = null)

    private fun facts(
        left: List<Goal> = listOf(run, water),
        done: Int = 0,
        due: Int = 2,
        missed: Int = 0,
        streak: Int = 0,
        mood: Int? = null,
        ask: Boolean = false,
        away: Int = 0,
        level: Int? = null,
        now: LocalTime = LocalTime(10, 0),
    ) = CompanionFacts(day, now, left, done, due, missed, streak, mood, ask, away, level)

    @Test
    fun theEasiestGoalIsTheQuickest() {
        assertEquals(water, Checkins.easiest(listOf(run, water, read)))
        // No length counts as about 15 minutes, so a 2-minute goal still comes first.
        assertEquals(read, Checkins.easiest(listOf(run, read)))
        assertNull(Checkins.easiest(emptyList()))
    }

    @Test
    fun afterASlipItSuggestsOneSmallThing() {
        val say = Checkins.say(facts(missed = 3))
        assertEquals(Expression.WORRIED, say.expression)
        assertEquals(water, say.suggestion)
        assertTrue("Water plants" in say.line, say.line)
        // Once something is done today, the slip is behind us.
        assertNotEquals(Expression.WORRIED, Checkins.say(facts(missed = 3, done = 1, due = 3)).expression)
    }

    @Test
    fun itWelcomesTheUserBack() {
        val say = Checkins.say(facts(away = 4, missed = 2))
        assertEquals(Expression.HAPPY, say.expression)
        assertTrue(say.line.contains("back", ignoreCase = true), say.line)
        assertEquals(water, say.suggestion)
    }

    @Test
    fun itAsksHowTheUserFeelsAndAnswers() {
        val asking = Checkins.say(facts(ask = true))
        assertTrue(asking.askMood)
        assertNull(asking.suggestion)

        // A hard day: gentle, with one small thing if they'd like, never the late-night nudge.
        val hard = Checkins.say(facts(mood = 1, missed = 2, now = LocalTime(21, 0)))
        assertEquals(Expression.CALM, hard.expression)
        assertEquals(water, hard.suggestion)
        assertFalse(hard.askMood)

        val good = Checkins.say(facts(mood = 5))
        assertEquals(Expression.HAPPY, good.expression)
        assertTrue("2" in good.line, good.line)
    }

    @Test
    fun onAHardDayEachThingDoneIsAWin() {
        val first = Checkins.say(facts(mood = 2))
        assertEquals(0, first.wins)
        assertEquals(CharacterMove.NONE, first.move)
        val one = Checkins.say(facts(mood = 2, done = 1, due = 3, left = listOf(run, water)))
        assertEquals(1, one.wins)
        assertEquals(Expression.HAPPY, one.expression)
        assertEquals(CharacterMove.HOP, one.move, "it hops for the win")
        assertNotEquals(first.line, one.line, "not the same nudge again")
        assertTrue("stop" in one.line || "Rest" in one.line, one.line)
        assertEquals(water, one.suggestion)
        val two = Checkins.say(facts(mood = 2, done = 2, due = 4, left = listOf(run, water)))
        assertTrue("two" in two.line.lowercase(), two.line)
        val four = Checkins.say(facts(mood = 1, done = 4, due = 6, left = listOf(run, water)))
        assertEquals(Expression.PROUD, four.expression)
        assertTrue("4" in four.line, four.line)
        // Nothing small left to offer: still a win, with no half-finished sentence.
        val none = Checkins.say(facts(mood = 2, done = 1, due = 2, left = emptyList()))
        assertTrue(none.line.endsWith("."), none.line)
        assertTrue("{next}" !in none.line)
        // On an ordinary day, there's no tally of wins.
        assertEquals(0, Checkins.say(facts(mood = 4, done = 1, due = 3)).wins)
    }

    @Test
    fun itCheersWhenEverythingIsDone() {
        val say = Checkins.say(facts(left = emptyList(), done = 2, due = 2, ask = true))
        assertEquals(Expression.PROUD, say.expression)
        assertEquals(CharacterMove.DANCE, say.move)
        assertFalse(say.askMood, "nothing left to ask about")
        assertTrue("hard day" in Checkins.say(facts(left = emptyList(), done = 2, due = 2, mood = 2)).line)
    }

    @Test
    fun itJumpsForJoyAtANewLevel() {
        val say = Checkins.say(facts(level = 7, missed = 2))
        assertTrue(say.levelUp)
        assertEquals(CharacterMove.HOP, say.move)
        assertTrue("7" in say.line)
    }

    @Test
    fun lateInTheDayItLooksTired() {
        val say = Checkins.say(facts(now = LocalTime(19, 30), due = 4, done = 1, left = listOf(run, water, read)))
        assertEquals(Expression.TIRED, say.expression)
        assertEquals(water, say.suggestion)
    }

    @Test
    fun theWordsStayTheSameAllDay() {
        assertEquals(Checkins.say(facts(missed = 1)).line, Checkins.say(facts(missed = 1, now = LocalTime(11, 0))).line)
    }

    @Test
    fun daysAwayCountsDaysWithSomethingDueAndNothingDone() {
        fun record(daysBack: Int, due: Int, done: Int) = DayRecord(day.minusDays(daysBack), due, done, 0, 0, 0)
        val records = listOf(record(5, 2, 1), record(4, 2, 0), record(3, 0, 0), record(2, 2, 0), record(1, 3, 0), record(0, 2, 0))
        // Yesterday, 2 days back and 4 days back; the day with nothing due is passed over.
        assertEquals(3, Checkins.daysAway(records, day))
        // Never done anything: a new user, not one coming back.
        assertEquals(0, Checkins.daysAway(records.drop(1), day))
        assertEquals(0, Checkins.daysAway(listOf(record(1, 2, 1)), day))
        assertEquals(3, Checkins.leftYesterday(records, day))
        // Three were due: one done, one skipped (it dropped out of what was due), one left undone.
        assertEquals(1, Checkins.leftYesterday(listOf(DayRecord(day.minusDays(1), 2, 1, 0, 1, 0)), day))
        assertEquals(0, Checkins.leftYesterday(emptyList(), day))
    }

    @Test
    fun eachFaceIsDrawnDifferently() {
        val outfit = Outfit(emptyList(), null)
        val faces = Expression.entries.map { Figure.svg(Figure.marks(Look(), outfit, it)) }
        assertEquals(faces.size, faces.toSet().size)
        assertEquals(Figure.marks(Look(), outfit), Figure.marks(Look(), outfit, Expression.CALM))
        // Without hair, the brows still show the feeling.
        val bald = Look(hair = HairStyle.NONE)
        assertNotEquals(Figure.marks(bald, outfit, Expression.WORRIED), Figure.marks(bald, outfit, Expression.CALM))
    }

    // 0.70.0: the character reacts to what happens, knows the user's own day, and moves.

    private fun sheet(vararg xp: Pair<SkillArea, Long>) = CharacterSheet(xp.sumOf { it.second }, Levels.of(xp.sumOf { it.second }, Levels.CHARACTER_STEP), xp.toMap())

    @Test
    fun aTickGetsALineAboutWhatWasJustDone() {
        val base = facts(done = 1, due = 3)
        val say = Checkins.say(base.copy(justNow = JustNow(JustNow.Kind.TICK, "Read")))
        assertTrue("Read" in say.line && "1 of 3" in say.line, say.line)
        assertEquals(CharacterMove.NOD, say.move)
        assertEquals(Expression.HAPPY, say.expression)
        // The suggestion under it stays where it was, so the card doesn't jump about.
        assertEquals(Checkins.say(base).suggestion, say.suggestion)

        // A late one done after all: relieved.
        val late = Checkins.say(base.copy(justNow = JustNow(JustNow.Kind.TICK, "Read", late = true)))
        assertEquals(Expression.RELIEVED, late.expression)
        assertTrue("Phew" in late.line && "Read" in late.line, late.line)

        // A locked private goal is never named.
        val private = Checkins.say(base.copy(justNow = JustNow(JustNow.Kind.TICK, null)))
        assertFalse("null" in private.line, private.line)
        assertTrue("1 of 3" in private.line, private.line)

        // A skip gets a neutral line, and no cheer.
        val skip = Checkins.say(base.copy(justNow = JustNow(JustNow.Kind.SKIP, "Run")))
        assertEquals(Expression.CALM, skip.expression)
        assertEquals(CharacterMove.NONE, skip.move)
        assertTrue("skipped" in skip.line.lowercase(), skip.line)

        // The tick that finishes the day goes straight to the cheer, and a hard day keeps its own words.
        val last = Checkins.say(facts(left = emptyList(), done = 2, due = 2).copy(justNow = JustNow(JustNow.Kind.TICK, "Read")))
        assertEquals(CharacterMove.DANCE, last.move)
        val hard = facts(mood = 2, done = 1, due = 3)
        assertEquals(Checkins.say(hard), Checkins.say(hard.copy(justNow = JustNow(JustNow.Kind.TICK, "Read"))))
    }

    @Test
    fun itKnowsThePaceAndWhetherTodayCounts() {
        val rule = StreakSettings(StreakTarget.COUNT, 2)
        assertEquals(2, Checkins.streakNeed(3, 0, rule))
        assertEquals(0, Checkins.streakNeed(3, 2, rule))
        assertEquals(1, Checkins.streakNeed(1, 0, rule), "only one is due, so one is all it needs")
        assertEquals(9, Checkins.streakNeed(10, 0, StreakSettings()), "90% of 10")
        assertNull(Checkins.streakNeed(0, 0, rule))

        val just = JustNow(JustNow.Kind.TICK, "Read")
        val one = Checkins.say(facts(done = 1, due = 3, streak = 4).copy(justNow = just, streakRule = rule))
        assertTrue("One more keeps the streak" in one.line, one.line)
        // With no streak running there is none to keep: one more would start one.
        val fresh = Checkins.say(facts(done = 1, due = 3, streak = 0).copy(justNow = just, streakRule = rule))
        assertTrue("One more and today counts for a streak" in fresh.line, fresh.line)
        // A long streak is never given a day number that could be a day out: its length is said as it is.
        (0..6).forEach { shift ->
            val line = Checkins.steady(facts(done = 0, due = 3, streak = 9).let { it.copy(day = it.day.plusDays(shift)) }).line
            assertTrue("Day 10" !in line, line)
        }
        val two = Checkins.say(facts(done = 2, due = 3, streak = 4, left = listOf(run)).copy(justNow = just, streakRule = rule))
        assertTrue("counts for your streak" in two.line, two.line)
        // Once it counts, it isn't said again on the next tick.
        val three = Checkins.say(facts(done = 3, due = 4, streak = 4, left = listOf(run)).copy(justNow = just, streakRule = rule))
        assertFalse("streak" in three.line, three.line)

        val ahead = Checkins.say(facts(done = 1, due = 3).copy(justNow = just, shortBy = 0))
        assertTrue("no rush" in ahead.line, ahead.line)
        val tight = Checkins.say(facts(done = 1, due = 3).copy(justNow = just, shortBy = 40))
        assertTrue("one at a time" in tight.line, tight.line)
    }

    @Test
    fun itNoticesSkillsGearAndTheGame() {
        val seen = mapOf(SkillArea.MIND to 4, SkillArea.FITNESS to 1)
        // Mind reaches level 5 (200 experience at a step of 20) and earns its glasses.
        val up = Checkins.skillUp(sheet(SkillArea.MIND to 200L, SkillArea.FITNESS to 20L), seen)!!
        assertEquals(CompanionEvent.SkillUp(SkillArea.MIND, 5, Gear.GLASSES), up, "gear comes before a plain level")
        assertNull(Checkins.skillUp(sheet(SkillArea.MIND to 199L), seen))
        assertNull(Checkins.skillUp(sheet(SkillArea.MIND to 400L), null), "the first look starts from here, with no cheer")
        assertEquals(seen + SkillArea.entries.filter { it !in seen }.associateWith { 1 }, Checkins.decodeSkills(Checkins.encodeSkills(seen)))
        assertNull(Checkins.decodeSkills(""))

        val say = Checkins.say(facts().copy(skillUp = up))
        assertTrue("Mind" in say.line && "5" in say.line && "glasses" in say.line, say.line)
        assertTrue((0..1).any { Checkins.say(facts().copy(day = day.plusDays(it), skillUp = up)).line == "Mind is level 5: new glasses!" })
        assertTrue(say.levelUp, "cheered until seen, like a level")
        assertEquals(Expression.CHEERING, say.expression)
        assertEquals(Expression.HAPPY, Checkins.say(facts().copy(skillUp = CompanionEvent.SkillUp(SkillArea.HOME, 3))).expression)
        // The character's own level comes first.
        assertTrue("7" in Checkins.say(facts(level = 7).copy(skillUp = up)).line)

        // How near the next piece is.
        val near = Checkins.gearNear(sheet(SkillArea.MIND to 170L, SkillArea.HOME to 10L))!!
        assertEquals(GearNear(Gear.GLASSES, 30), near)
        assertEquals(0, Outfit.left(sheet(SkillArea.MIND to 500L), Gear.GLASSES))

        val lucky = Checkins.say(facts(done = 1, due = 3).copy(event = CompanionEvent.Lucky(12, double = false)))
        assertEquals(Expression.SURPRISED, lucky.expression)
        assertTrue("12" in lucky.line, lucky.line)
        val quest = Checkins.say(facts(done = 1, due = 3).copy(event = CompanionEvent.QuestDone(2)))
        assertTrue("uest" in quest.line && "2" in quest.line, quest.line)
        val hit = Checkins.say(facts().copy(event = CompanionEvent.BossHit(40, beaten = false)))
        assertEquals(Expression.DETERMINED, hit.expression)
        assertTrue("40" in hit.line, hit.line)
        assertEquals(Expression.CHEERING, Checkins.say(facts().copy(event = CompanionEvent.BossHit(40, beaten = true))).expression)
        val shield = Checkins.say(facts(streak = 31).copy(shieldSaved = true))
        assertEquals(Expression.RELIEVED, shield.expression)
        assertTrue("shield" in shield.line && "31" in shield.line, shield.line)
    }

    @Test
    fun itKnowsTheTimeOfDayByTheUsersOwnDay() {
        // A day that ends at 4 am: 7:30 pm isn't late yet, 10 pm is (6 hours before), 2:30 am is sleepy.
        fun at(hour: Int, minute: Int = 0, done: Int = 1) =
            Checkins.say(facts(now = LocalTime(hour, minute), due = 4, done = done, left = listOf(run, water, read)).copy(dayEndHour = 4))
        assertNotEquals(Expression.TIRED, at(19, 30).expression)
        assertEquals(Expression.TIRED, at(22).expression)
        val sleepy = at(2, 30)
        assertEquals(Expression.SLEEPY, sleepy.expression)
        assertNull(sleepy.suggestion, "gentle: the rest can wait")
        assertTrue("1" in sleepy.line || "late" in sleepy.line.lowercase(), sleepy.line)
        // The hours can be changed in Points and rules.
        val early = Checkins.say(facts(now = LocalTime(15, 0), due = 4, done = 1, left = listOf(run, water, read)).copy(lateHours = 10))
        assertEquals(Expression.TIRED, early.expression)
        // Sleepy, but one more would keep a streak going: it says so, and leaves it to the user.
        val keep = Checkins.say(
            facts(now = LocalTime(23, 0), due = 4, done = 1, streak = 12, left = listOf(run, water)).copy(streakRule = StreakSettings(StreakTarget.COUNT, 2)),
        )
        assertEquals(Expression.SLEEPY, keep.expression)
        assertEquals(water, keep.suggestion)
        assertTrue("streak" in keep.line, keep.line)

        // Good morning with a stretch, for two hours from "My day starts at".
        val morning = Checkins.say(facts(now = LocalTime(7, 30)).copy(dayStart = LocalTime(7, 0)))
        assertEquals(CharacterMove.STRETCH, morning.move)
        assertTrue("orning" in morning.line, morning.line)
        assertEquals(CharacterMove.NONE, Checkins.say(facts(now = LocalTime(9, 30)).copy(dayStart = LocalTime(7, 0))).move)
        assertEquals(CharacterMove.NONE, Checkins.say(facts(now = LocalTime(7, 30))).move, "not until the day's start is set")
    }

    @Test
    fun yesterdaysLineNeverComesStraightBack() {
        val shapes = listOf(
            facts(), facts(missed = 2), facts(away = 3), facts(done = 1, due = 3), facts(left = emptyList(), done = 2, due = 2),
            facts(ask = true), facts(mood = 5), facts(mood = 1), facts(streak = 9), facts(level = 4), facts(left = emptyList(), due = 0),
            facts(now = LocalTime(20, 0), due = 4, done = 1), facts(now = LocalTime(23, 0), due = 4, done = 1),
            facts(done = 1, due = 3).copy(justNow = JustNow(JustNow.Kind.TICK, "Read")),
        )
        for (shape in shapes) {
            for (offset in 0..13) {
                val today = Checkins.say(shape.copy(day = day.plusDays(offset))).line
                val yesterday = Checkins.say(shape.copy(day = day.plusDays(offset - 1))).line
                assertNotEquals(yesterday, today, "day $offset: $today")
            }
        }
    }

    @Test
    fun linesUseRealNumbersTheAppKnows() {
        val base = facts(done = 1, due = 3)
        fun lines(f: CompanionFacts) = (0..6).map { Checkins.say(f.copy(day = day.plusDays(it))).line }
        assertTrue(lines(base.copy(level = 4, toNextLevel = 35)).all { "35 to level 5." in it })
        assertTrue(lines(base.copy(savingName = "Cinema", savingLeft = 40)).all { "40 more for Cinema." in it })
        assertTrue(lines(base.copy(savingName = "Cinema", savingLeft = 0)).all { "enough for Cinema" in it })
        assertTrue(lines(base.copy(weekEarned = 60, weekTarget = 100)).all { "40 to this week's target." in it })
        assertTrue(lines(base.copy(raceAhead = 15)).all { "15 ahead of last week" in it })
        assertTrue(lines(base.copy(raceAhead = -15)).none { "last week" in it }, "being behind isn't rubbed in")
        assertTrue(lines(base.copy(bestDayThisWeek = true)).all { "Best day this week" in it })
        assertTrue(lines(base.copy(gearNear = GearNear(Gear.GLASSES, 30))).all { "Mind is 30 away from its glasses." in it })
        // With several things known they take turns, one at a time.
        val many = lines(base.copy(level = 4, toNextLevel = 35, raceAhead = 15, bestDayThisWeek = true))
        assertTrue(many.toSet().size >= 3, many.toString())
        assertTrue(many.none { "35 to level" in it && "ahead of last week" in it })
        // With nothing known, nothing is made up.
        assertTrue(lines(base).none { "level" in it || "target" in it })
    }

    @Test
    fun theSuggestionSaysWhy() {
        val quick = Checkins.say(facts())
        assertEquals(water, quick.suggestion)
        assertEquals("it's the quickest one left", quick.reason)
        // 10:00 now, and it is usually done at 10:20.
        val usual = Checkins.say(facts().copy(hints = mapOf(water.id to SuggestHint(usualMinute = 10 * 60 + 20))))
        assertEquals("it's the quickest one left and you usually do it around now", usual.reason)
        assertEquals("it's the quickest one left", Checkins.say(facts().copy(hints = mapOf(water.id to SuggestHint(usualMinute = 15 * 60)))).reason)
        // Just before midnight, a goal usually done just after it is still "around now".
        assertEquals(
            "you usually do it around now",
            Checkins.reason(run, facts(now = LocalTime(23, 50)).copy(hints = mapOf(run.id to SuggestHint(usualMinute = 10)))),
        )
        assertEquals("it's the only one left", Checkins.say(facts(left = listOf(run), done = 1, due = 2)).reason)
        assertEquals("it's a small one to start with", Checkins.say(facts(left = listOf(read, run.copy(minutes = null, points = 30)))).reason)
        assertTrue("its time has come round" in Checkins.reason(water, facts().copy(hints = mapOf(water.id to SuggestHint(late = true))))!!)
        // Nothing suggested, nothing to explain.
        assertNull(Checkins.say(facts(left = emptyList(), done = 2, due = 2)).reason)
    }

    @Test
    fun theDayAfterARoughOneItSaysBackOnTrack() {
        val back = Checkins.say(facts(left = emptyList(), done = 2, due = 2, missed = 2))
        assertTrue("ack on track" in back.line, back.line)
        assertEquals(CharacterMove.DANCE, back.move)
        assertTrue("ack on track" in Checkins.say(facts(left = emptyList(), done = 2, due = 2, away = 3)).line)
        // No reminder of what went wrong.
        assertFalse(listOf("miss", "slip", "yesterday").any { it in back.line.lowercase() }, back.line)
        assertFalse("ack on track" in Checkins.say(facts(left = emptyList(), done = 2, due = 2)).line)
    }

    @Test
    fun itHasAFaceAndALineForOtherScreens() {
        for (scene in Scene.entries) {
            val say = Checkins.at(scene, day)
            assertTrue(say.line.isNotBlank())
            assertNotEquals(say.line, Checkins.at(scene, day.plusDays(1)).line, scene.name)
        }
        assertEquals(Expression.DETERMINED, Checkins.at(Scene.FOCUS, day).expression)
        assertEquals(CharacterMove.DANCE, Checkins.at(Scene.CELEBRATION, day).move)
    }

    @Test
    fun theDrawingComesInPartsThatMove() {
        val outfit = Outfit(listOf(Gear.BOTTLE, Gear.BOOK), null)
        val parts = Figure.parts(Look(hair = HairStyle.LONG), outfit)
        assertEquals(Figure.marks(Look(hair = HairStyle.LONG), outfit), parts.all)
        assertTrue(listOf(parts.ground, parts.hairBack, parts.legs, parts.body, parts.leftArm, parts.rightArm, parts.head).all { it.isNotEmpty() })
        // Each arm carries its sleeve, what it holds and its hand.
        assertEquals(4, parts.leftArm.size)
        assertEquals(4, parts.rightArm.size)
        // A blink shuts the eyes, and nothing else changes.
        val blink = Figure.parts(Look(hair = HairStyle.LONG), outfit, blink = true)
        assertNotEquals(parts.head, blink.head)
        assertEquals(parts.copy(head = emptyList()), blink.copy(head = emptyList()))
        // Standing still nothing is moved; a cheer has both arms up.
        assertTrue(Figure.layers(parts, Pose()).all { it.motions.isEmpty() })
        val cheer = Figure.layers(parts, Figure.rest(Expression.CHEERING))
        assertEquals(2, cheer.count { layer -> layer.motions.any { it is Motion.Turn } })
        assertEquals(Pose(), Figure.rest(Expression.CALM))
        val svg = Figure.svg(parts, Figure.rest(Expression.CHEERING))
        assertTrue(svg.startsWith("<svg") && svg.endsWith("</svg>") && "rotate(150.0 34.5 80.0)" in svg && "rotate(-150.0 85.5 80.0)" in svg, svg.take(300))
    }

    @Test
    fun everyMoveStartsAndEndsAtRest() {
        for (move in CharacterMove.entries) {
            val length = CharacterMoves.duration(move)
            assertEquals(Pose(), CharacterMoves.pose(move, -1))
            assertEquals(Pose(), CharacterMoves.pose(move, length))
            if (move == CharacterMove.NONE) continue
            assertTrue(length in 500..3000, "$move is short")
            val start = CharacterMoves.pose(move, 0)
            assertTrue(kotlin.math.abs(start.lift) < 0.01f && start.leftArm < 0.01f && start.rightArm < 0.01f && kotlin.math.abs(start.lean) < 0.01f, "$move $start")
            assertTrue((1 until length step 20).any { CharacterMoves.pose(move, it) != Pose() }, "$move moves")
            // Nothing flies off: arms between down and straight up, feet never under the ground.
            for (ms in 0 until length step 10) {
                val pose = CharacterMoves.pose(move, ms)
                assertTrue(pose.leftArm in 0f..180f && pose.rightArm in 0f..180f && pose.lift >= 0f && pose.lift <= 24f, "$move at $ms: $pose")
            }
        }
        assertTrue((0..CharacterMoves.duration(CharacterMove.WAVE) step 10).all { CharacterMoves.pose(CharacterMove.WAVE, it).leftArm == 0f }, "one arm waves")
        assertTrue((0..CharacterMoves.duration(CharacterMove.NOD) step 10).any { CharacterMoves.pose(CharacterMove.NOD, it).headDy > 2f })
    }

    @Test
    fun itBreathesAndBlinksWhenIdle() {
        val poses = (0L until 3 * CharacterMoves.BLINK_EVERY_MS step 20).map { CharacterMoves.idle(it) }
        assertTrue(poses.any { it.blink } && poses.any { !it.blink })
        assertTrue(poses.count { it.blink } < poses.size / 10, "mostly its eyes are open")
        assertTrue(poses.all { it.rise in 0f..1f })
        assertTrue(poses.maxOf { it.rise } > 0.8f && poses.minOf { it.rise } < 0.1f)
        // A move plays over the breath, and a cheer keeps its arms up while it nods.
        val both = CharacterMoves.pose(Figure.rest(Expression.CHEERING), CharacterMove.NOD, 180, idleMs = 900)
        assertTrue(both.headDy > 0f && both.rise > 0f)
        assertEquals(150f, both.leftArm)
        // With animations off the screen asks for the rest pose alone.
        assertEquals(Pose(), CharacterMoves.pose(Figure.rest(Expression.CALM)))
    }
}
