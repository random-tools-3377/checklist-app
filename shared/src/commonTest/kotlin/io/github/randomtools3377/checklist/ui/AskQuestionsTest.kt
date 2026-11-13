package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.AskAction
import io.github.randomtools3377.checklist.core.AskFigure
import io.github.randomtools3377.checklist.core.AskKind
import io.github.randomtools3377.checklist.core.AskTurn
import io.github.randomtools3377.checklist.core.Asker
import io.github.randomtools3377.checklist.core.CharacterSheet
import io.github.randomtools3377.checklist.core.Difficulty
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalCheck
import io.github.randomtools3377.checklist.core.GoalHint
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.GoalMood
import io.github.randomtools3377.checklist.core.GoalSummary
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.Levels
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.MoodFindings
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.ReviewSummary
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.SkillArea
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.Streaks
import io.github.randomtools3377.checklist.core.TodayView
import io.github.randomtools3377.checklist.core.WeekProgress
import io.github.randomtools3377.checklist.core.Window
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The questions people really type, against the app's real guides: each has to land on a useful answer. */
class AskQuestionsTest {
    private val day = LocalDate(2026, 11, 4)
    private val monday = LocalDate(2026, 11, 2)
    private fun goal(id: Long, name: String, minutes: Int? = null) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(monday), minutes = minutes, position = id.toInt())

    private val read = goal(1, "Read")
    private val run = goal(2, "Morning run", 30)
    private val water = goal(3, "Drink water")
    private val work = goal(4, "Work")
    private val window = Window(day, day, Period.DAILY)

    private fun week() = ReviewSummary(
        from = monday, to = monday.plusDays(6),
        goals = listOf(GoalSummary(read, 3, 3, 0, 0, 3, 15, 3, 3), GoalSummary(run, 3, 1, 0, 2, 1, 5, 1, 0), GoalSummary(water, 3, 2, 0, 1, 2, 10, 2, 0), GoalSummary(work, 3, 2, 1, 0, 2, 10, 2, 0)),
        reasons = emptyList(), earned = 40, spent = 0, lost = 5, days = emptyList(), gaps = emptyList(), weekdays = emptyMap(),
    )

    private fun state(locks: Locks = Locks()) = askState(
        today = TodayState(
            TodayView(day, 80, listOf(HabitRow(read, 1, window), HabitRow(run, 0, window), HabitRow(water, 0, window), HabitRow(work, 0, window)), emptyList(), emptyList(), emptyList(), emptyList()),
            LocalDateTime(2026, 11, 4, 10, 0), null, emptyList(), true, emptyMap(), false, Streaks(5, 3, 3, 3, 0), StreakSettings(), hiddenPrivate = 0,
        ),
        week = week(), lastWeek = week(), target = WeekProgress(monday, 40, 100), best = Streaks(9, 9, 9, 9, 2), goals = listOf(read, run, water, work),
        check = BalanceCheckView(20.0, Difficulty.NORMAL, emptyList(), listOf(GoalCheck(read, 24, 23, GoalHint.PAY_LESS, 3), GoalCheck(run, 20, 6, GoalHint.PAY_MORE, 7))),
        shop = ShopView(80, listOf(RewardRow(Reward(id = 1, name = "Cinema trip", icon = "🎬", price = 200), null), RewardRow(Reward(id = 2, name = "Coffee", icon = "☕", price = 30), null)), emptyList(), 20.0),
        starred = 1,
        character = CharacterSheet(120, Levels.of(120, Levels.CHARACTER_STEP), mapOf(SkillArea.FITNESS to 70L, SkillArea.MIND to 50L)),
        findings = MoodFindings(listOf(GoalMood(run, 5, 5, 4.0, 3.0)), emptyList(), 2),
        moods = listOf(Mood(day, 4), Mood(day.minusDays(1), 2)),
        money = listOf(AskFigure("Spent", "$12.00"), AskFigure("Left", "$300.00")),
        features = Feature.entries.toSet(), locks = locks, unlocked = false,
        guides = askGuides(GuideDevice(), Feature.entries),
    )

    private fun ask(q: String) = Asker.answer(q, state())

    @Test
    fun questionsAboutTheUsersOwnDayAndNumbers() {
        for (q in listOf("what's left today", "What's left?", "what do I still have to do", "is there anything left today", "what have I not done yet today")) {
            val a = ask(q)
            assertEquals(AskKind.LEFT, a.kind, q)
            assertTrue("3 of 4 are left today: Morning run, Drink water and Work." in a.text, a.text)
        }
        for (q in listOf("what's next", "what should I do now", "what do I start with")) assertEquals(AskKind.NEXT, ask(q).kind, q)
        assertEquals(AskKind.TODAY, ask("how am I doing").kind)
        assertEquals(AskKind.WEEK, ask("how am I doing this week").kind)
        assertTrue("Last week ended at" in ask("how was last week").text)
        assertEquals(AskKind.STREAK, ask("what's my streak").kind)
        assertEquals(AskKind.LEVEL, ask("what level am I").kind)
        // "Next" in a question about levels isn't today's list.
        assertEquals(AskKind.LEVEL, ask("what's the next level").kind)
        assertEquals(AskKind.MONEY, ask("how much have I spent this month").kind)
        assertEquals(AskKind.MOOD, ask("how is my mood").kind)
        for (q in listOf("how many points do I have", "what's my balance")) assertTrue("Your balance is 80 points." in ask(q).text, q)
        for (q in listOf("how close is the cinema trip", "how long until I can buy the cinema trip")) assertTrue("Cinema trip costs 200: 120 to go" in ask(q).text, q)
        assertTrue("Coffee costs 30, so you can buy it now." in ask("can I afford coffee").text)
        val target = ask("what is the weekly target")
        assertTrue("This week's target: 40 of 100, 60 to go." in target.text, target.text)
    }

    @Test
    fun questionsAboutOneGoal() {
        for (q in listOf("how is Read going", "what does Read pay", "tips for reading")) {
            val a = ask(q)
            assertEquals(AskKind.GOAL, a.kind, q)
            assertTrue(a.text.startsWith("Read: done 3 of 3 this week"), a.text)
        }
        for (q in listOf("how is my morning run going", "I'm struggling with my morning run", "how is the run going")) {
            val a = ask(q)
            assertEquals(AskKind.GOAL, a.kind, q)
            assertTrue("Morning run got done 6 of 20 times" in a.text && "A smaller version" in a.text, a.text)
        }
        // A goal whose name is a small word can still be asked about.
        assertTrue(ask("how is Work going").text.startsWith("Work: done 2 of 3 this week"))
        // Sharing a word with a goal's name doesn't make a question about the app one about the goal.
        assertEquals("REMINDERS", ask("what is the morning plan").guides.first().key)
        assertEquals(AskKind.HOW_TO, ask("how does the focus timer work").kind)
    }

    @Test
    fun whyPointsWereLostSaysWhatCameOffAndWhatTakesPointsOff() {
        for (q in listOf("why did I lose points", "why did my points go down", "why is my balance lower")) {
            val a = ask(q)
            assertTrue("This week 5 came off as penalties." in a.text, "$q -> ${a.text}")
            assertTrue("Missed this week: Morning run (2) and Drink water (1)." in a.text, a.text)
            assertTrue("costs 50% of the goal's points" in a.text, a.text)
            assertEquals(listOf("SKIPS_AND_MISSES", "HISTORY"), a.guides.map { it.key })
            assertEquals(AskAction.Open("Open the points history", "history"), a.actions.first())
        }
        // With the review locked the numbers stay out, and the explanation still comes.
        val locked = Asker.answer("why did I lose points", state(Locks(review = true)))
        assertFalse("came off as penalties" in locked.text || "Morning run" in locked.text + locked.facts, locked.text)
        assertTrue("Points come off when" in locked.text && AskAction.Unlock in locked.actions)
    }

    @Test
    fun guidanceQuestions() {
        val fitness = ask("how do I get better at fitness")
        assertEquals(AskKind.BETTER, fitness.kind)
        assertTrue("Your goals in it: Morning run." in fitness.text, fitness.text)
        assertEquals(AskKind.BETTER, ask("how can I improve my health").kind)
        assertTrue("it could pay 7 instead of 5" in ask("how do I earn more points").text)
        val track = ask("how do I get back on track")
        assertEquals(AskKind.BETTER, track.kind)
        assertTrue("Could use a hand: Morning run" in track.text, track.text)
    }

    /** The guide a question about the app lands on, and words its answer has to hold. */
    private fun guide(q: String, key: String, vararg holds: String) {
        val a = ask(q)
        assertEquals(AskKind.HOW_TO, a.kind, q)
        assertEquals(key, a.guides.first().key, "$q -> ${a.guides.map { it.key }}")
        for (words in holds) assertTrue(words in a.text, "$q should say '$words': ${a.text}")
        assertFalse("{" in a.text + a.facts, a.text)
    }

    @Test
    fun questionsAboutHowTheAppWorks() {
        guide("what does a skip cost", "SKIPS_AND_MISSES", "costs 50% of the goal's points")
        guide("how much does a miss cost", "SKIPS_AND_MISSES", "costs 50% of the goal's points")
        guide("what happens if I miss a goal", "SKIPS_AND_MISSES", "is a miss, and asks for a reason")
        guide("how do I skip a goal", "SKIPS_AND_MISSES", "Tap \"Skip\" on Today")
        guide("how do I take back a skip", "SKIPS_AND_MISSES", "Take back the skip")
        guide("I forgot to tick something yesterday", "SKIPS_AND_MISSES", "I did it, I forgot to tick it")
        guide("how do I add a reminder", "REMINDERS", "under Reminders, give it a set time")
        guide("how do I set a reminder for a goal", "REMINDERS", "under Reminders, give it a set time")
        guide("how do I snooze", "REMINDERS", "Tap \"Snooze\"")
        guide("what is a combo", "COMBOS", "Perfect days in a row build a combo")
        guide("what are daily quests", "QUESTS")
        guide("what is the boss", "BOSSES")
        guide("what is a penalty task", "PENALTY")
        guide("what is a gap", "GAPS", "2 or more days in a row")
        guide("how do I back up", "BACKUP", "Export everything")
        guide("how do I back up my data", "BACKUP", "Export everything")
        guide("how do I import a backup", "BACKUP", "Import a backup")
        guide("how do I export to a spreadsheet", "BACKUP", "as a spreadsheet")
        guide("does it sync to the cloud", "BACKUP", "Your data stays on this phone")
        guide("how do streaks work", "STREAKS", "Streaks count the days in a row")
        guide("what happens to my streak if I skip", "STREAKS", "doesn't break the main streak")
        guide("how do I change my day end", "SETTINGS", "My day ends at")
        guide("how do I change when my day ends", "SETTINGS", "My day ends at")
        guide("what time does my day start", "SETTINGS", "My day starts at")
        guide("how do I set a weekly target", "SETTINGS", "Under \"Weekly target\"")
        guide("how do I add a goal", "GOALS", "Tap \"New goal\"")
        guide("how do I make a new habit", "GOALS", "Tap \"New goal\"")
        guide("how do I delete a goal", "REMOVED_GOALS", "To remove a goal, open it and tap")
        guide("how do I mark a goal as done", "TODAY", "Tap \"Done\" to tick a goal")
        guide("how do I undo a tick", "TODAY", "Tap it again to take it back")
        guide("how do I move a goal to another day", "PLAN", "Tap \"Move\"")
        guide("how do I change how many points a goal pays", "HABIT_EDITOR", "the points each tick pays")
        guide("how do I add a reward", "SHOP", "Tap \"New reward\"")
        guide("how do I buy a reward", "SHOP", "Tap \"Buy\"")
        guide("how do I return a reward", "SHOP", "\"Return\" for a full refund")
        guide("how do I lock the app", "PRIVACY", "In Settings → Privacy")
        guide("how do I hide my private goals", "PRIVACY", "Turn on \"Private\"")
        guide("how do I change the colours", "APPEARANCE")
        guide("how do I turn on dark mode", "APPEARANCE", "\"Dark\"")
        guide("how do I rename points", "APPEARANCE", "Rename points")
        guide("how do I add a widget", "WIDGETS", "widget list")
        guide("how do I use the focus timer", "FOCUS", "Focus for")
        guide("what does difficulty do", "DIFFICULTY")
        guide("how does the budget work", "BUDGET")
        guide("what are badges", "BADGES")
        guide("what is the morning plan", "REMINDERS", "\"Morning plan\" comes when your day starts")
        guide("how do I add a to-do", "TODO_EDITOR")
        // An extra with no guide of its own is answered from its line on the Features page.
        guide("how do I start a challenge", "FEATURE_CHALLENGES", "30 days in a row")
        guide("what is interest", "FEATURE_INTEREST", "grow a little at the end of each week")
    }

    @Test
    fun buttonsSayWhereTheyGoAndNewThingsOpenTheirEditor() {
        assertEquals(listOf(AskAction.Open("New habit", "habit/0"), AskAction.Open("Open Goals", "goals")), ask("how do I add a goal").actions)
        assertEquals(AskAction.Open("New project", "project/0"), ask("how do I add a project").actions.first())
        assertEquals(AskAction.Open("New reward", "reward/0"), ask("how do I add a reward").actions.first())
        // A guide about a setting opens Settings, and says so.
        assertEquals(listOf(AskAction.Open("Open Settings", "settings")), ask("how do I back up").actions)
        assertEquals(listOf(AskAction.Open("Open Features", "features/CHALLENGES")), ask("how do I start a challenge").actions)
        // With the shop locked, no button makes a reward.
        assertTrue(Asker.answer("how do I add a reward", state(Locks(shop = true))).actions.none { it is AskAction.Open && it.route == "reward/0" })
    }

    @Test
    fun smallTalkAndTheUnknownAreKind() {
        assertTrue(ask("hello").text.startsWith("Hello! Ask me"))
        assertTrue(ask("what can you do").text.startsWith("Hello! Ask me"))
        assertTrue(ask("thanks").text.startsWith("You're welcome"))
        val unknown = ask("can I use it on my bicycle")
        assertEquals(AskKind.UNKNOWN, unknown.kind)
        assertEquals("", unknown.facts)
    }

    @Test
    fun aFollowUpLeansOnTheQuestionBeforeOnlyWhenItHasLittleOfItsOwn() {
        val first = ask("how do I back up")
        val turn = AskTurn("how do I back up", first, first.text)
        assertEquals("BACKUP", Asker.answer("and how do I bring it back?", state(), turn).guides.first().key)
        // A new question that happens to say "it" is answered for itself.
        assertEquals("APPEARANCE", Asker.answer("is there a dark mode for it", state(), turn).guides.first().key)
    }

    @Test
    fun moodsLockWithReasonsOrWithTheReviewAndTheBestRunWithTheReview() {
        for (locks in listOf(Locks(reasons = true), Locks(review = true))) {
            val s = state(locks)
            assertTrue(s.moods.isEmpty() && s.moodLifts.isEmpty(), locks.toString())
            assertTrue(Asker.answer("how is my mood", s).locked)
        }
        val review = state(Locks(review = true))
        assertEquals(null, review.bestStreak)
        assertEquals(null, review.perDay)
        assertEquals(null, state(Locks(shop = true)).perDay)
        assertFalse("best" in Asker.answer("what's my streak", review).text.lowercase())
    }
}
