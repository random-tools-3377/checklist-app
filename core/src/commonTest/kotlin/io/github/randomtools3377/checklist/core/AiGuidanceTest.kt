package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The week's suggestion, the kept and the plain note, waiting and failing, steps again, follow-ups and voice. */
class AiGuidanceTest {
    private val monday = LocalDate(2026, 11, 2)
    private fun goal(id: Long, name: String, minutes: Int? = null, points: Int = 5, private: Boolean = false, amount: Double? = null, unit: String? = null) =
        Goal(
            id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = points, repeat = Repeat.everyDay(monday), minutes = minutes,
            private = private, amount = amount, unit = unit, position = id.toInt(),
        )

    private fun week(vararg goals: GoalSummary, weekdays: Map<Int, Pair<Int, Int>> = emptyMap()) = ReviewSummary(
        from = monday, to = monday.plusDays(6), goals = goals.toList(), reasons = listOf("No time" to 2),
        earned = 60, spent = 0, lost = 5, days = emptyList(), gaps = emptyList(), weekdays = weekdays,
    )

    @Test
    fun theHardestGoalGetsASmallerVersion() {
        val advice = WeekAdvices.pick(week(GoalSummary(goal(1, "Water"), 7, 7, 0, 0, 7, 7, 7, 7), GoalSummary(goal(2, "Stretch", minutes = 15), 4, 1, 0, 3, 1, 5, 1, 0)))!!
        assertEquals(AdviceKind.MAKE_SMALLER, advice.kind)
        assertEquals("Stretch got done 1 of 4 times. A smaller version is easier to start: 10 minutes instead of 15.", advice.text)
        assertEquals("Make it smaller", advice.button)
        assertEquals(2L, advice.idea?.goalId)
        assertEquals(10, advice.idea?.goal?.minutes)
        val pages = WeekAdvices.pick(week(GoalSummary(goal(3, "Read", amount = 30.0, unit = "pages"), 4, 2, 0, 2, 2, 10, 1, 0)))!!
        assertEquals(20.0, pages.idea?.goal?.amount)
        assertTrue("20 pages instead of 30 pages" in pages.text, pages.text)
    }

    @Test
    fun withNothingToShrinkItCanPayALittleMore() {
        val floss = goal(4, "Floss")
        val weekOf = week(GoalSummary(floss, 6, 2, 0, 4, 2, 10, 1, 0))
        val more = WeekAdvices.pick(weekOf, listOf(GoalCheck(floss, 20, 8, GoalHint.PAY_MORE, 7)))!!
        assertEquals(AdviceKind.PAY_MORE, more.kind)
        assertEquals("Pay a little more", more.button)
        assertEquals(7, more.idea?.points)
        assertEquals(AdviceKind.OPEN, WeekAdvices.pick(weekOf)!!.kind)
    }

    @Test
    fun aGoodWeekGetsANextStepAndPrivateGoalsAreNeverPicked() {
        val good = WeekAdvices.pick(week(GoalSummary(goal(1, "Run", minutes = 20), 5, 5, 0, 0, 5, 50, 5, 5)))!!
        assertEquals(AdviceKind.STEP_UP, good.kind)
        assertEquals("Run went well. A next step, if you like: 25 min instead of 20 min.", good.text)
        assertEquals("Step it up", good.button)
        // A private goal that went badly isn't the subject of the suggestion.
        val private = WeekAdvices.pick(
            week(GoalSummary(goal(9, "Therapy homework", minutes = 30, private = true), 4, 0, 0, 4, 0, 0, 0, 0), weekdays = mapOf(1 to (2 to 2), 7 to (2 to 0))),
        )!!
        assertEquals(AdviceKind.HARD_DAY, private.kind)
        assertFalse("Therapy" in private.text)
        assertTrue("Sunday was the hardest day" in private.text, private.text)
        assertNull(WeekAdvices.pick(week()))
    }

    @Test
    fun theNotesPromptCarriesMoreFactsAndTheAppsSuggestion() {
        val stretch = goal(2, "Stretch", minutes = 15)
        val thisWeek = week(GoalSummary(goal(1, "Water"), 7, 7, 0, 0, 7, 7, 7, 7), GoalSummary(stretch, 4, 1, 0, 3, 1, 5, 1, 0))
        val more = WeekMore(
            lastWeek = week(GoalSummary(goal(1, "Water"), 7, 5, 0, 2, 5, 5, 3, 0)),
            streak = 6, target = WeekProgress(monday, 60, 50), badges = listOf("Perfect day"), advice = WeekAdvices.pick(thisWeek),
        )
        val prompt = CoachNote.prompt(thisWeek, hideReasons = false, more = more)
        assertTrue("The week before: done 5 of 7 (71%), points earned 60." in prompt, prompt)
        assertTrue("Streak now: 6 days in a row." in prompt)
        assertTrue("Weekly points target: 60 of 50, reached." in prompt)
        assertTrue("New badges: Perfect day." in prompt)
        assertTrue(prompt.endsWith("Suggestion for next week: Stretch got done 1 of 4 times. A smaller version is easier to start: 10 minutes instead of 15."), prompt)
        assertTrue("give no other advice" in CoachNote.SYSTEM)
        assertFalse("Reasons" in CoachNote.prompt(thisWeek, hideReasons = true, more = more))
    }

    @Test
    fun thePlainNoteIsWrittenByTheApp() {
        val thisWeek = week(
            GoalSummary(goal(1, "Water"), 7, 7, 0, 0, 7, 7, 7, 6),
            GoalSummary(goal(2, "Stretch"), 4, 1, 0, 3, 1, 5, 1, 0),
            GoalSummary(goal(3, "Diary", private = true), 4, 0, 0, 4, 0, 0, 0, 0),
            weekdays = mapOf(2 to (3 to 3), 5 to (3 to 1)),
        )
        assertEquals("8 of 15 done. Best day: Tuesday. Water is on a run of 6. Hardest: Stretch, 1 of 4.", CoachNote.plain(thisWeek))
        assertNull(CoachNote.plain(week()))
    }

    @Test
    fun aNoteIsKeptForItsWeekAndHidesWithLockedReasons() {
        var notes = CoachNotes.put(emptyList(), KeptNote(monday, "A good week.\nKeep going.", reasons = true))
        notes = CoachNotes.put(notes, KeptNote(monday.plusDays(7), "Another | week.", reasons = false))
        notes = CoachNotes.put(notes, KeptNote(monday, "A better note.", reasons = true))
        val back = CoachNotes.decode(CoachNotes.encode(notes))
        assertEquals(2, back.size)
        assertEquals("A better note.", CoachNotes.shown(back, monday, hideReasons = false)?.text)
        assertNull(CoachNotes.shown(back, monday, hideReasons = true))
        assertEquals("Another | week.", CoachNotes.shown(back, monday.plusDays(7), hideReasons = true)?.text)
        // A note naming a goal that is private and locked now stays hidden too.
        assertNull(CoachNotes.shown(back, monday.plusDays(7), hideReasons = false, hiddenNames = listOf("Another")))
        assertEquals("Another | week.", CoachNotes.shown(back, monday.plusDays(7), hideReasons = false, hiddenNames = listOf("Diary", ""))?.text)
        // Only the latest weeks are kept.
        val many = (0 until 12).fold(emptyList<KeptNote>()) { all, i -> CoachNotes.put(all, KeptNote(monday.plusDays(7 * i), "Note $i.", false)) }
        assertEquals(CoachNotes.KEEP, CoachNotes.decode(CoachNotes.encode(many)).size)
        assertTrue(CoachNotes.decode(null).isEmpty() && CoachNotes.decode("rubbish").isEmpty())
    }

    @Test
    fun failuresAreSaidInPlainWordsNeverTheEnginesOwn() {
        val raw = listOf(
            "java.lang.IllegalStateException: The AI file isn't added yet.", "Failed to allocate 512MB: OutOfMemoryError", AiTrouble.TIMED_OUT,
            "SecurityException: Only the checklist app may ask Checklist AI.", "Unknown authority io.example", "litert::Status 13 INTERNAL kernel failed", null,
        )
        val said = raw.map { AiTrouble.plain(it) }
        assertEquals(6, said.toSet().size)
        said.forEach { text ->
            assertFalse(Regex("Exception|litert|INTERNAL|java\\.|authority").containsMatchIn(text), text)
            assertTrue(text.endsWith("."), text)
        }
        assertTrue("stopped waiting" in AiTrouble.plain(AiTrouble.TIMED_OUT))
        assertTrue("12 seconds" in AiTrouble.waiting(12))
        assertTrue("stop waiting" in AiTrouble.waiting(75) && "1 min 15 s" in AiTrouble.waiting(75))
    }

    @Test
    fun askingForStepsAgainNamesWhatWasShownAndTheHint() {
        assertEquals("\"Plan a party\" ->", ProjectSteps.prompt("Plan a party"))
        val again = ProjectSteps.prompt("Plan a party", existing = listOf("Pick a date", " "), shown = listOf("Book the place", "Order the cake"), hint = "it's for 20 people, outdoors")
        assertEquals(
            "About it: it's for 20 people, outdoors.\nSteps already there: Pick a date.\nAlready suggested, so not these: Book the place; Order the cake.\n\"Plan a party\" ->",
            again,
        )
        val steps = ProjectSteps.parse("""{"steps":["Pick a date","Send invitations","book the place","Buy food"]}""", not = listOf("Pick a date", "Book the place"))
        assertEquals(listOf("Send invitations", "Buy food"), steps)
    }

    @Test
    fun anIdeaSaysWhatItPaysAndHowLongToTheNextLevel() {
        val idea = AreaIdea("ai:fitness:swim", SkillArea.FITNESS, IdeaKind.AI, GoalIdea("Swim", "🏊", IdeaRepeat.TIMES_PER_WEEK, timesPerWeek = 2, minutes = 30), Effort.MEDIUM)
        val perTick = Suggest.points(Effort.MEDIUM, 30)
        val level = Levels.of(70, Levels.SKILL_STEP)
        val weeks = (level.toNext + perTick * 2 - 1) / (perTick * 2)
        assertEquals("about ${perTick * 2} points a week · ${if (weeks == 1L) "about a week" else "roughly $weeks weeks"} to Fitness level ${level.level + 1}", idea.why(level))
        assertEquals("about ${perTick * 2} points a week", idea.why(null))
    }

    @Test
    fun oneFollowUpIsOfferedAfterSavingADescribedGoal() {
        val brush = goal(1, "Brush teeth")
        val read = goal(5, "Read").copy(at = LocalTime(21, 0), reminder = Reminder())
        val before = assertIs<FollowUp.RemindBefore>(FollowUp.pick(read, listOf(brush, read), emptySet()))
        assertEquals("Also remind you 10 minutes before 9:00 pm?", before.question)
        val silent = assertIs<FollowUp.RemindAt>(FollowUp.pick(read.copy(reminder = Reminder(onTime = false)), listOf(brush, read), emptySet()))
        assertEquals("Add a reminder at 9:00 pm?", silent.question)
        val floss = goal(6, "Floss")
        // A chain is only offered while habit chains are on.
        assertNull(FollowUp.pick(floss, listOf(brush, floss), emptySet()))
        val after = assertIs<FollowUp.After>(FollowUp.pick(floss, listOf(brush, floss), setOf(Feature.CHAINS)))
        assertEquals("Do it right after Brush teeth?", after.question)
        assertEquals(1L, after.afterId)
        assertNull(FollowUp.pick(read.copy(reminder = Reminder(minutesBefore = 10)), listOf(brush, read), emptySet()))
    }

    @Test
    fun voiceAndTypingUnderstandQuestionsAndToDos() {
        val goals = listOf(1L to "Read", 2L to "Morning run")
        val todos = listOf(7L to "Call the bank")
        assertEquals(VoiceCommand.Question("what's next"), VoiceCommand.parse("what's next", goals, todos))
        assertIs<VoiceCommand.Question>(VoiceCommand.parse("How am I doing?", goals))
        assertIs<VoiceCommand.Question>(VoiceCommand.parse("what's left", goals))
        assertEquals(VoiceCommand.Tick(7, "Call the bank", todo = true), VoiceCommand.parse("done calling the bank", goals, todos))
        assertEquals(VoiceCommand.Tick(1, "Read"), VoiceCommand.parse("done reading", goals, todos))
        assertEquals(VoiceCommand.Add("Buy milk"), VoiceCommand.parse("add buy milk", goals, todos))
        // No goal recognised: the open ones are offered, the likeliest first.
        val missed = assertIs<VoiceCommand.NotUnderstood>(VoiceCommand.parse("done morni jog", goals, todos))
        assertEquals(listOf(2L, 1L, 7L), missed.closest.map { it.first })
        assertTrue(assertIs<VoiceCommand.NotUnderstood>(VoiceCommand.parse("banana", goals, todos)).closest.isEmpty())
    }
}
