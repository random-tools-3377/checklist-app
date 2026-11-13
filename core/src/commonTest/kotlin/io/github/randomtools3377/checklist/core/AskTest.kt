package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ask: matching the question, building the facts, what stays locked, and the answer without the AI. */
class AskTest {
    private val day = LocalDate(2026, 11, 4)
    private val monday = LocalDate(2026, 11, 2)
    private fun goal(id: Long, name: String, minutes: Int? = null, points: Int = 5, private: Boolean = false) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = points, repeat = Repeat.everyDay(monday), minutes = minutes, private = private, position = id.toInt())

    private val read = goal(1, "Read")
    private val stretch = goal(2, "Stretch")
    private val run = goal(3, "Run", minutes = 20, points = 10)
    private val window = Window(day, day, Period.DAILY)

    private val guides = listOf(
        AskGuide(
            "SKIPS_AND_MISSES", "Skips and misses", "A skip is said ahead of time; a miss is a goal left undone when its day ends.",
            listOf("Each goal allows 1 skip or miss a week.", "Over the limit, a skip or miss costs half the points the goal pays.", "Tap \"Skip\" on a goal's card on Today."),
        ),
        AskGuide("BACKUP", "Backup", "Save everything to a file, and bring it back.", listOf("Tap \"Export\" in Settings to save a backup file.", "Tap \"Import\" to bring one back.")),
        AskGuide("STREAKS", "Streaks", "Streaks count days in a row.", listOf("Set what a day needs under \"Main streak\" in Settings.")),
        AskGuide("QUESTS", "Daily quests", "Three small quests each day, from your own goals.", listOf("Each quest pays 5 points."), setOf(Feature.QUESTS)),
        AskGuide("IDEAS", "Ideas to level up", "Ideas for each skill.", listOf("Tap \"Add\" on an idea.")),
        AskGuide("CHARACTER", "Character and skills", "Your points level up a character.", listOf("Each goal belongs to one skill.")),
    )

    private val week = ReviewSummary(
        from = monday, to = monday.plusDays(6),
        goals = listOf(
            GoalSummary(read, 3, 3, 0, 0, 3, 15, 3, 3),
            GoalSummary(stretch, 3, 1, 0, 2, 1, 5, 1, 0),
            GoalSummary(run, 3, 2, 0, 1, 2, 20, 2, 0),
        ),
        reasons = emptyList(), earned = 40, spent = 0, lost = 2, days = emptyList(), gaps = emptyList(), weekdays = emptyMap(),
    )

    private fun state(
        locks: AskLocks = AskLocks(),
        features: Set<Feature> = setOf(Feature.CHARACTER, Feature.MOOD, Feature.BUDGET),
        hiddenPrivate: Int = 0,
        readDone: Boolean = true,
    ) = AskState(
        today = TodayView(
            day, 120,
            dueToday = listOf(HabitRow(read, if (readDone) 1 else 0, window), HabitRow(stretch, 0, window), HabitRow(run, 0, window)),
            thisWeek = emptyList(), thisMonth = emptyList(), thisYear = emptyList(), projects = emptyList(),
        ),
        nextId = 2,
        hiddenPrivate = hiddenPrivate,
        streaks = Streaks(6, 6, 3, 6, 2),
        bestStreak = 14,
        week = week.takeIf { !locks.review },
        lastWeek = null,
        target = WeekProgress(monday, 40, 100),
        goals = listOf(read, stretch, run),
        checks = if (locks.review) emptyList() else listOf(GoalCheck(read, 24, 23, GoalHint.PAY_LESS, 3), GoalCheck(stretch, 20, 6, GoalHint.PAY_MORE, 7)),
        rewards = if (locks.shop) emptyList() else listOf(Reward(id = 1, name = "Film night", icon = "🎬", price = 300)),
        balance = 120,
        perDay = 30.0,
        starredId = 1L.takeIf { !locks.shop },
        character = CharacterSheet(120, Levels.of(120, Levels.CHARACTER_STEP), mapOf(SkillArea.FITNESS to 70L, SkillArea.MIND to 50L)),
        moodLifts = if (locks.moods) emptyList() else listOf("Run"),
        moods = if (locks.moods) emptyList() else listOf(Mood(day.minusDays(1), 2), Mood(day, 4)),
        money = if (locks.budget) null else listOf(AskFigure("Spent", "$40.00"), AskFigure("Left", "$310.00")),
        missedMost = stretch.takeIf { !locks.review },
        features = features,
        locks = locks,
        guides = guides,
    )

    @Test
    fun whatsNextAndWhatsLeftComeFromTodaysList() {
        val next = Asker.answer("What's next?", state())
        assertEquals(AskKind.NEXT, next.kind)
        assertTrue(next.text.startsWith("Next is Stretch"), next.text)
        assertTrue("1 more is left" in next.text, next.text)
        assertTrue(AskFigure("Done today", "1 of 3") in next.figures)
        assertTrue(next.actions.contains(AskAction.Open("Open Today", "today")))

        val left = Asker.answer("what is left today", state())
        assertEquals(AskKind.LEFT, left.kind)
        assertTrue("2 of 3 are left today: Stretch and Run." in left.text, left.text)
        assertEquals(left.text, left.facts)
    }

    @Test
    fun dataQuestionsAreRecognised() {
        fun kind(q: String) = Asker.answer(q, state()).kind
        assertEquals(AskKind.TODAY, kind("How am I doing?"))
        assertEquals(AskKind.WEEK, kind("how am i doing this week"))
        assertEquals(AskKind.GOAL, kind("How is Stretch going?"))
        assertEquals(AskKind.GOAL, kind("how often do I get the reading done"))
        assertEquals(AskKind.REWARD, kind("How close is Film night?"))
        assertEquals(AskKind.REWARD, kind("can i buy something yet"))
        assertEquals(AskKind.STREAK, kind("What's my streak?"))
        assertEquals(AskKind.LEVEL, kind("what level am I"))
        assertEquals(AskKind.MONEY, kind("How is my budget this month?"))
        assertEquals(AskKind.MOOD, kind("how has my mood been lately"))
        assertEquals(AskKind.MOOD, kind("What goes with a better mood?"))
        assertEquals(AskKind.BETTER, kind("How do I get better at Fitness?"))
        assertEquals(AskKind.BETTER, kind("How do I keep my streak?"))
        assertEquals(AskKind.BETTER, kind("any tips to earn more points"))
        assertEquals(AskKind.HOW_TO, kind("What does a skip cost?"))
        assertEquals(AskKind.HOW_TO, kind("How do I back up?"))
        assertEquals(AskKind.UNKNOWN, kind("purple monkey dishwasher"))
    }

    @Test
    fun aGoalsAnswerUsesItsOwnNumbersAndOpensIt() {
        val answer = Asker.answer("How is Stretch going?", state())
        assertTrue("done 1 of 3 this week" in answer.text, answer.text)
        assertTrue("6 of 20 times (30%)" in answer.text, answer.text)
        assertTrue("could also pay 7 instead of 5" in answer.text, answer.text)
        assertTrue(AskFigure("Last 4 weeks", "6 of 20") in answer.figures)
        assertEquals(listOf(AskAction.Open("Open Stretch", "goal/2"), AskAction.Open("Edit Stretch", "habit/2")), answer.actions)
        // A goal that goes with a better mood says so without claiming a cause.
        val run = Asker.answer("how is run going", state())
        assertTrue("goes with a better mood" in run.text && "doesn't say what causes what" in run.text, run.text)
    }

    @Test
    fun aRewardSaysHowFarAndHowLong() {
        val answer = Asker.answer("How close is Film night?", state())
        assertTrue("Film night costs 300: 180 to go, about 6 more days at your usual pace." in answer.text, answer.text)
        assertTrue(AskFigure("To go", "180") in answer.figures)
    }

    @Test
    fun streakLevelMoneyAndMoodAnswerFromTheAppsNumbers() {
        val streak = Asker.answer("what's my streak", state())
        assertTrue("Your main streak is 6 days. Your best is 14 days." in streak.text, streak.text)
        assertTrue("One more day makes it 7 in a row." in streak.text, streak.text)
        assertEquals(listOf("STREAKS"), streak.guides.map { it.key })

        val level = Asker.answer("what level am i", state())
        assertTrue("Your character is level 2" in level.text, level.text)
        assertTrue("Fitness 3" in level.text, level.text)

        val money = Asker.answer("how is my budget", state())
        assertEquals("This month so far: spent $40.00 and left $310.00.", money.text)
        assertEquals(2, money.figures.size)

        val mood = Asker.answer("how is my mood", state())
        assertTrue("3.0 out of 5" in mood.text, mood.text)
        assertTrue("Run goes with a better mood for you. These go together; it doesn't say what causes what." in mood.text, mood.text)
        assertFalse(Regex("\\b(because|causes your|makes you|lifts|helped)\\b").containsMatchIn(mood.text), mood.text)
    }

    @Test
    fun lockedPartsAreLeftOutAndSaidSoWithUnlock() {
        val locked = state(locks = AskLocks(review = true, shop = true, budget = true, moods = true))
        for (q in listOf("how am i doing this week", "How is Stretch going?", "How is my budget this month?", "how has my mood been")) {
            val answer = Asker.answer(q, locked)
            assertTrue(answer.locked, q)
            assertTrue("behind the privacy lock" in answer.text, answer.text)
            assertEquals(listOf<AskAction>(AskAction.Unlock), answer.actions, q)
            assertEquals("", answer.facts, "nothing locked may reach the AI: $q")
            assertNull(Asker.prompt(q, answer))
        }
        val reward = Asker.answer("How close is Film night?", locked)
        assertTrue(reward.locked)
        assertFalse("Film night" in reward.text, reward.text)
        // The summary still gives today, and says the week is locked.
        val today = Asker.answer("How am I doing?", locked)
        assertTrue("Today: 1 of 3 done" in today.text, today.text)
        assertFalse("This week so far" in today.facts, today.facts)
        assertTrue(AskAction.Unlock in today.actions)
        assertTrue(Asker.suggestions(locked).none { "Stretch" in it || "Film" in it || "budget" in it || "mood" in it })
    }

    @Test
    fun aLockedPrivateGoalIsNeverNamed() {
        // The state arrives without the private goal; the answer only says some are left out.
        val hidden = state(hiddenPrivate = 1)
        val answer = Asker.answer("What's left today?", hidden)
        assertTrue("Private goals are locked, so they're left out." in answer.text, answer.text)
        assertTrue(AskAction.Unlock in answer.actions)
        val journal = Asker.answer("How is Journal going?", hidden)
        assertFalse(journal.kind == AskKind.GOAL)
        assertFalse("Journal" in journal.text + journal.facts)
    }

    @Test
    fun extrasThatAreOffOfferTheirSwitch() {
        val off = state(features = emptySet())
        val money = Asker.answer("how is my budget", off)
        assertEquals(listOf<AskAction>(AskAction.Open("Turn on \"Budget\"", "features/BUDGET")), money.actions)
        assertEquals(AskKind.LEVEL, Asker.answer("what level am i", off).kind)
        assertTrue("is off" in Asker.answer("what level am i", off).text)
        val quests = Asker.answer("What are daily quests?", off)
        assertEquals(AskKind.HOW_TO, quests.kind)
        assertEquals("QUESTS", quests.guides.first().key)
        assertTrue(AskAction.Open("Turn on \"Daily quests\"", "features/QUESTS") in quests.actions)
        assertTrue("\"Daily quests\" is off now" in quests.text, quests.text)
    }

    @Test
    fun howToQuestionsAnswerFromTheBestGuides() {
        val skip = Asker.answer("What does a skip cost?", state())
        assertEquals("SKIPS_AND_MISSES", skip.guides.first().key)
        assertTrue(skip.text.startsWith("A skip is said ahead of time"), skip.text)
        assertTrue("costs half the points" in skip.text, skip.text)
        assertTrue("Skips and misses:" in skip.facts && "- Over the limit" in skip.facts, skip.facts)
        val backup = Asker.answer("how do I back up my data", state())
        assertEquals(listOf("BACKUP"), backup.guides.map { it.key })
        assertTrue(AskAction.Open("Open Settings", "settings") in backup.actions)
        assertTrue(Asker.findGuides("zebra crossing", guides).isEmpty())
    }

    @Test
    fun aFollowUpCarriesOnFromTheAnswerBefore() {
        val first = Asker.answer("How do I back up?", state())
        val turn = AskTurn("How do I back up?", first, first.text)
        assertTrue(Asker.isFollowUp("and how do I bring that back?"))
        val next = Asker.answer("and how do I bring that back?", state(), turn)
        assertEquals(AskKind.HOW_TO, next.kind)
        assertEquals("BACKUP", next.guides.first().key)
        assertTrue("Import" in next.text, next.text)
        // A new subject isn't dragged back to the old one.
        assertEquals(AskKind.NEXT, Asker.answer("what's next", state(), turn).kind)
    }

    @Test
    fun guidanceForAnAreaUsesTheUsersOwnGoals() {
        val answer = Asker.answer("How do I get better at Fitness?", state())
        assertEquals(AskKind.BETTER, answer.kind)
        assertTrue("Fitness is level 3" in answer.text, answer.text)
        assertTrue("Your goals in it: Stretch and Run." in answer.text, answer.text)
        assertTrue("A next step for Run: 25 min instead of 20 min." in answer.text, answer.text)
        assertTrue("New ideas:" in answer.text, answer.text)
        assertEquals(AskAction.Open("Ideas for Fitness", "ideas/FITNESS"), answer.actions.first())
        assertEquals(listOf("IDEAS", "CHARACTER"), answer.guides.map { it.key })
    }

    @Test
    fun guidanceForStreaksAndPointsUsesTheRecord() {
        val streaks = Asker.answer("how do i keep my streak going", state())
        assertTrue("Stretch is the goal missed most lately" in streaks.text, streaks.text)
        assertTrue(AskAction.Open("Open Stretch", "goal/2") in streaks.actions)
        val points = Asker.answer("How can I earn more points?", state())
        assertEquals(AskKind.BETTER, points.kind)
        assertTrue("Stretch got done 6 of 20 times lately; it could pay 7 instead of 5" in points.text, points.text)
        assertTrue("Read is done nearly every time (23 of 24)" in points.text, points.text)
        assertTrue("Film night is 180 away" in points.text, points.text)
        val focus = Asker.answer("how can i focus better", state())
        assertTrue("Run (20 min)" in focus.text, focus.text)
    }

    @Test
    fun theSummaryHasTodayTheWeekTheStreakAndOneNextStep() {
        val doing = HowDoing.of(state())
        assertEquals("Today: 1 of 3 done, 2 left.", doing.lines.first())
        assertTrue(doing.lines.any { it.startsWith("This week so far: 6 of 9 done (66%), 40 points earned, 2 lost.") }, doing.lines.toString())
        assertTrue("Your main streak is 6 days (best 14 days)." in doing.lines)
        assertTrue("Going best: Read, 23 of 24." in doing.lines)
        assertTrue("Could use a hand: Stretch, 6 of 20." in doing.lines)
        assertEquals("Next step: Stretch is next on today's list.", doing.lines.last())
        assertEquals(AskAction.Open("Open Today", "today"), doing.next?.action)
    }

    @Test
    fun onADoneDayTheNextStepIsTheAppsSuggestionWithItsButton() {
        val done = state().let { s ->
            s.copy(today = s.today!!.copy(dueToday = s.today!!.dueToday.map { it.copy(count = 1) }), checks = emptyList())
        }
        val next = HowDoing.nextStep(done)!!
        // Stretch was done 1 of 3 this week and has nothing to make smaller: a look at it.
        assertTrue(next.text.startsWith("Stretch got done 1 of 3 times."), next.text)
        val action = assertIs<AskAction.Fill>(next.action)
        assertEquals(2L, action.idea.goalId)
        assertTrue(NearWins.of(done).any { "7 in a row" in it })
    }

    @Test
    fun theAiIsSentOnlyTheFactsAndTheTurnsBefore() {
        val answer = Asker.answer("What's left today?", state())
        val prompt = Asker.prompt("What's left today?", answer)!!
        assertTrue(prompt.startsWith("Notes:\n2 of 3 are left today"), prompt)
        assertTrue(prompt.endsWith("Question: What's left today?\nAnswer:"), prompt)
        val before = AskTurn("How am I doing?", Asker.today(state()), "You're doing well today.")
        val withEarlier = Asker.prompt("and what's left?", answer, listOf(before))!!
        assertTrue("Earlier in this chat:\nThey asked: How am I doing?\nYou said: You're doing well today." in withEarlier, withEarlier)
        // Long facts are trimmed to what the small model can take.
        val long = answer.copy(facts = (1..200).joinToString("\n") { "Line $it of the guide, with some words in it." })
        assertTrue(Asker.prompt("q", long)!!.length < Asker.MAX_FACTS + 100)
        assertNull(Asker.prompt("anything", Asker.answer("purple monkey dishwasher", state())))
        assertTrue("ONLY the notes" in Asker.SYSTEM && "say you don't know" in Asker.SYSTEM)
    }

    @Test
    fun aSlipOnANumberFallsBackToTheAppsOwnAnswer() {
        val facts = "2 of 3 are left today: Stretch and Run."
        assertEquals("You have 2 of 3 left today: Stretch and Run. Nice going!", Asker.clean("<think>hmm</think>You have **2 of 3** left today: Stretch and Run. Nice going!", facts))
        assertNull(Asker.clean("You have 4 left today.", facts))
        assertNull(Asker.clean("Ok.", facts))
        // Mood notes say what goes together; an answer that makes it a cause is dropped.
        val mood = "Run goes with a better mood for you. These go together; it doesn't say what causes what."
        assertNull(Asker.clean("Running improves your mood, so keep at it!", mood))
        assertNull(Asker.clean("You feel better because you run.", mood))
        assertEquals("Run goes with a better mood for you.", Asker.clean("Run goes with a better mood for you.", mood))
        // At most 5 sentences.
        val many = Asker.clean("One thing. Two things. Three things. Four things. Five things. Six things.", "")
        assertEquals("One thing. Two things. Three things. Four things. Five things.", many)
    }

    @Test
    fun suggestedQuestionsFollowTheUsersState() {
        val suggestions = Asker.suggestions(state())
        assertEquals("What's left today?", suggestions.first())
        assertTrue("How is Stretch going?" in suggestions, suggestions.toString())
        assertTrue("How do I get better at Health?" in suggestions, suggestions.toString())
        assertTrue("How close is Film night?" in suggestions, suggestions.toString())
        assertTrue(suggestions.size <= 6)
        // Every suggestion gets a real answer.
        suggestions.forEach { assertFalse(Asker.answer(it, state()).kind == AskKind.UNKNOWN, it) }
        assertNotNull(Asker.suggestions(AskState()).firstOrNull())
    }

    @Test
    fun anEmptyAppStillAnswers() {
        val empty = AskState(guides = guides)
        assertEquals(AskKind.NEXT, Asker.answer("what's next", empty).kind)
        assertEquals(AskKind.TODAY, Asker.answer("how am i doing", empty).kind)
        assertEquals(AskKind.HOW_TO, Asker.answer("what does a skip cost", empty).kind)
        assertEquals(AskKind.BETTER, Asker.answer("how do i get better at mind", empty).kind)
    }
}
