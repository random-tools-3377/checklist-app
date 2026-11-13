package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.AskAction
import io.github.randomtools3377.checklist.core.AskKind
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
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.Streaks
import io.github.randomtools3377.checklist.core.TodayView
import io.github.randomtools3377.checklist.core.Window
import io.github.randomtools3377.checklist.core.AskFigure
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What Ask may know: the real guides it searches, and what the privacy lock keeps out of every answer. */
class AskStateTest {
    private val android = GuideDevice()
    private val iphone = GuideDevice(buttons = false, plan = false, folder = false, widgets = false, ai = false, missing = "Morning plan, weekly summary and snooze")
    private val guides = askGuides(android)
    private val day = LocalDate(2026, 11, 4)
    private val monday = LocalDate(2026, 11, 2)

    private fun goal(id: Long, name: String, private: Boolean = false) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(monday), private = private)

    private val read = goal(1, "Read")
    private val diary = goal(2, "Secret diary", private = true)
    private val window = Window(day, day, Period.DAILY)

    private fun week(vararg goals: Goal) = ReviewSummary(
        from = monday, to = monday.plusDays(6), goals = goals.map { GoalSummary(it, 3, 1, 0, 2, 1, 5, 1, 0) },
        reasons = emptyList(), earned = 30, spent = 0, lost = 0, days = emptyList(), gaps = emptyList(), weekdays = emptyMap(),
    )

    /** The app as it stands with the lock closed: Today, the week and the balance check already come without private goals. */
    private fun built(locks: Locks, unlocked: Boolean, features: Set<Feature> = setOf(Feature.MOOD, Feature.BUDGET, Feature.CHARACTER)) = run {
        val hide = locks.privateGoals && !unlocked
        val shownGoals = if (hide) listOf(read) else listOf(read, diary)
        askState(
            today = TodayState(
                TodayView(day, 80, shownGoals.map { HabitRow(it, 0, window) }, emptyList(), emptyList(), emptyList(), emptyList()),
                LocalDateTime(2026, 11, 4, 10, 0), null, emptyList(), true, emptyMap(), false, Streaks(3, 3, 3, 3, 0), StreakSettings(),
                hiddenPrivate = if (hide) 1 else 0,
            ),
            // The week arrives unfiltered here on purpose: the builder must not rely on it.
            week = week(read, diary),
            lastWeek = week(read, diary),
            target = null,
            best = Streaks(9, 9, 9, 9, 2),
            goals = listOf(read, diary),
            check = BalanceCheckView(20.0, Difficulty.NORMAL, emptyList(), listOf(GoalCheck(read, 10, 9, null, null), GoalCheck(diary, 10, 2, GoalHint.PAY_MORE, 7))),
            shop = ShopView(80, listOf(RewardRow(Reward(id = 1, name = "Cinema", icon = "🎬", price = 200), null)), emptyList(), 20.0),
            starred = 1,
            character = CharacterSheet(0, Levels.of(0, Levels.CHARACTER_STEP), emptyMap()),
            findings = MoodFindings(listOf(GoalMood(diary, 5, 5, 4.0, 2.0), GoalMood(read, 5, 5, 4.0, 3.0)), emptyList(), 2),
            moods = listOf(Mood(day, 4, note = "a private note"), Mood(day.minusDays(30), 1)),
            money = listOf(AskFigure("Spent", "$12.00")),
            features = features,
            locks = locks,
            unlocked = unlocked,
            guides = guides,
        )
    }

    private val everything = Locks(history = true, shop = true, privateGoals = true, reasons = true, gaps = true, review = true, budget = true)

    @Test
    fun withTheLockClosedNothingLockedIsInTheState() {
        val state = built(everything, unlocked = false)
        assertEquals(listOf("Read"), state.goals.map { it.name })
        assertEquals(1, state.hiddenPrivate)
        assertNull(state.week)
        assertNull(state.lastWeek)
        assertTrue(state.checks.isEmpty() && state.rewards.isEmpty() && state.moods.isEmpty() && state.moodLifts.isEmpty())
        assertNull(state.money)
        assertNull(state.starredId)
        assertNull(state.missedMost)
        assertEquals(80, state.balance)
        // Nothing an answer says, or would send to the AI, names what is locked.
        for (q in Asker.suggestions(state) + listOf("How is Secret diary going?", "how close is cinema", "how is my mood", "how is my budget", "how am i doing this week", "how do i get better at mind")) {
            val answer = Asker.answer(q, state)
            val all = answer.text + answer.facts + answer.figures.joinToString { it.label + it.value } + answer.actions.joinToString { it.label }
            assertFalse(Regex("diary|Cinema|private note|12\\.00", RegexOption.IGNORE_CASE).containsMatchIn(all), "$q -> $all")
        }
        assertTrue(Asker.answer("how is my mood", state).actions.contains(AskAction.Unlock))
    }

    @Test
    fun onlyThePrivateLockStillKeepsPrivateGoalsOutOfTheWeekAndTheMood() {
        val state = built(Locks(privateGoals = true), unlocked = false)
        assertEquals(listOf("Read"), state.week!!.goals.map { it.goal.name })
        assertEquals(listOf("Read"), state.checks.map { it.goal.name })
        assertEquals(listOf("Read"), state.moodLifts)
        assertEquals("Read", state.missedMost?.name)
        assertEquals(listOf("Cinema"), state.rewards.map { it.name })
        // Only the last week of moods is used.
        assertEquals(1, state.moods.size)
    }

    @Test
    fun unlockedOrWithNoLockEverythingIsThere() {
        for (state in listOf(built(everything, unlocked = true), built(Locks(), unlocked = false))) {
            assertEquals(2, state.goals.size)
            assertEquals(0, state.hiddenPrivate)
            assertEquals(2, state.week!!.goals.size)
            assertEquals(listOf(AskFigure("Spent", "$12.00")), state.money)
            assertEquals(AskKind.GOAL, Asker.answer("How is Secret diary going?", state).kind)
        }
        // With the extras off, their parts are left out whatever the lock.
        val off = built(Locks(), unlocked = false, features = emptySet())
        assertNull(off.money)
        assertNull(off.character)
        assertTrue(off.moods.isEmpty())
    }

    @Test
    fun questionsAboutTheAppFindTheRightGuide() {
        val state = built(Locks(), unlocked = false, features = Feature.entries.toSet())
        fun first(q: String) = Asker.answer(q, state).let { answer -> assertEquals(AskKind.HOW_TO, answer.kind, q); answer.guides.first().key }
        assertEquals("SKIPS_AND_MISSES", first("What does a skip cost?"))
        assertEquals("BACKUP", first("How do I back up?"))
        assertEquals("PRIVACY", first("how do I lock my reasons and notes"))
        assertEquals("REMINDERS", first("how do I snooze a reminder"))
        assertEquals("BOSSES", first("what is a boss"))
        assertEquals("WIDGETS", first("how do i add a widget to my home screen"))
        assertEquals("SHIELDS", first("what are streak shields"))
        assertEquals("ASK", first("what can I ask a question about"))
        assertEquals("PENALTY", first("what is a penalty task"))
        assertEquals("TODO_EDITOR", first("how do I add steps to a to-do"))
        // The answer is the guide's own words, with the user's numbers already in them.
        val skip = Asker.answer("What does a skip cost?", state)
        assertFalse("{" in skip.text + skip.facts, skip.facts)
        assertTrue(skip.facts.length <= 2200, "the guides' text is kept short for the small AI")
        assertTrue(Asker.prompt("What does a skip cost?", skip)!!.length < 1800)
    }

    @Test
    fun everyGuideCanBeFoundByItsOwnTitleAndOpensARealPlace() {
        for (guide in guides) {
            val found = Asker.findGuides("what is ${guide.title}", guides)
            assertTrue(guide in found.take(3), "${guide.title} -> ${found.map { it.key }}")
        }
        // Where a guide has a screen to open, it is one of the app's routes.
        val routes = setOf(
            "today", "goals", "shop", "review", "budget", "settings", "features", "rules", "appearance", "balance-check", "history", "character",
            "badges", "mood-goals", "mood-journal", "planner", "plan-week", "done", "removed", "budget-pay", "budget-categories", "budget-bills", "budget-report",
        )
        HelpTopic.entries.mapNotNull { Asker.guideRoute(it.name) }.forEach { assertTrue(it in routes, it) }
    }

    @Test
    fun theGuidesForAskAreThereOnEveryDeviceWithoutTheAiWhereThereIsNone() {
        assertTrue(HelpTopic.ASK.shownOn(iphone) && HelpTopic.HOW_DOING.shownOn(iphone))
        assertTrue(HelpTopic.ASK.stepsOn(iphone).none { "AI" in it })
        assertTrue(HelpTopic.HOW_DOING.stepsOn(iphone).none { "AI" in it })
        assertTrue(HelpTopic.ASK.stepsOn(android).any { "AI helper" in it })
        assertTrue(askGuides(iphone).none { it.key == "AI" || it.key == "WIDGETS" })
        assertEquals(HelpTopic.ASK, helpTopicFor("ask"))
        assertTrue(HelpTopic.AI.steps.any { "Stop waiting" in it } && HelpTopic.AI.steps.any { "what's next" in it })
    }
}
