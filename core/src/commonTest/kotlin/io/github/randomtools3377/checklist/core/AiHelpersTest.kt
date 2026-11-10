package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiHelpersTest {
    @Test
    fun stepsAreTidiedAndRepeatsDropped() {
        val steps = ProjectSteps.parse(
            """{"steps":["1. pick a date.","Write the guest list","write the guest list","- Book the place","  ","Send the invitations","Order the cake","Buy decorations","Clean up","Say thanks","One too many"]}""",
        )
        assertEquals(
            listOf("Pick a date", "Write the guest list", "Book the place", "Send the invitations", "Order the cake", "Buy decorations", "Clean up", "Say thanks"),
            steps,
        )
    }

    @Test
    fun noStepsFromNonsense() {
        assertTrue(ProjectSteps.parse("no json").isEmpty())
        assertTrue(ProjectSteps.parse("""{"steps":"not a list"}""").isEmpty())
        assertTrue(ProjectSteps.parse("""{"steps":[1,2,"123"]}""").isEmpty())
    }

    private val day = LocalDate(2026, 11, 2)
    private fun goal(id: Long, name: String, private: Boolean = false) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(day), private = private)

    private val week = ReviewSummary(
        from = day, to = day.plusDays(6),
        goals = listOf(
            GoalSummary(goal(1, "Read"), 7, 6, 0, 1, 6, 30, 4, 4),
            GoalSummary(goal(2, "Gym"), 3, 1, 0, 2, 1, 10, 1, 0),
            GoalSummary(goal(3, "Journal", private = true), 7, 7, 0, 0, 7, 7, 7, 7),
        ),
        reasons = listOf("No time" to 2, "Forgot" to 1),
        earned = 47, spent = 0, lost = 5, days = emptyList(), gaps = emptyList(),
        weekdays = mapOf(1 to (3 to 3), 6 to (3 to 1)),
    )

    @Test
    fun theCoachGetsOnlyTheWeeksFacts() {
        val prompt = CoachNote.prompt(week, hideReasons = false)
        assertTrue("Done 14 of 17 goals due (82%)." in prompt, prompt)
        assertTrue("- Gym: done 1 of 3, missed 2." in prompt, prompt)
        assertTrue("- Read: done 6 of 7, missed 1, on a run of 4." in prompt, prompt)
        assertTrue("A private goal" in prompt && "Journal" !in prompt, "private goals aren't named")
        assertTrue("Reasons for misses: No time (2), Forgot (1)." in prompt, prompt)
        assertTrue("Best day: Monday; hardest day: Saturday." in prompt, prompt)
        assertFalse("No time" in CoachNote.prompt(week, hideReasons = true), "locked reasons stay out")
    }

    @Test
    fun theNoteIsPlainSentences() {
        val note = CoachNote.clean("<think>hmm</think>**Great week!** You read on 6 of 7 days.\n- Gym slipped twice for lack of time. Try a short session on Thursday. Keep going. And one more.")
        assertEquals("Great week! You read on 6 of 7 days. Gym slipped twice for lack of time. Try a short session on Thursday.", note)
        assertNull(CoachNote.clean("   ok  "))
    }
}
