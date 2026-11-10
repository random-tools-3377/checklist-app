package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiTest {
    @Test
    fun aGoodAnswerFillsInEverything() {
        val idea = GoalFromSentence.parse(
            """{"name":"Read","emoji":"📖","repeat":"days_of_week","days":["mon","tue","wed","thu","fri"],"times_per_week":null,"time":"21:00","minutes":null,"amount":20,"unit":"pages"}""",
        )!!
        assertEquals("Read", idea.name)
        assertEquals("📖", idea.icon)
        assertEquals(IdeaRepeat.DAYS_OF_WEEK, idea.repeat)
        assertEquals(listOf(1, 2, 3, 4, 5), idea.days)
        assertEquals(LocalTime(21, 0), idea.time)
        assertEquals(20.0, idea.amount)
        assertEquals("pages", idea.unit)
        assertNull(idea.minutes)
    }

    @Test
    fun timesAWeekKeepsItsNumber() {
        val idea = GoalFromSentence.parse(
            """{"name":"gym","emoji":"🏋️","repeat":"times_per_week","days":[],"times_per_week":3,"time":null,"minutes":60,"amount":null,"unit":null}""",
        )!!
        assertEquals("Gym", idea.name)
        assertEquals(IdeaRepeat.TIMES_PER_WEEK, idea.repeat)
        assertEquals(3, idea.timesPerWeek)
        assertEquals(60, idea.minutes)
    }

    @Test
    fun oddValuesAreLeftOutNotGuessed() {
        val idea = GoalFromSentence.parse(
            """{"name":" \"stretch.\" ","emoji":"stretch","repeat":"days_of_week","days":[],"times_per_week":9,"time":"25:99","minutes":-5,"amount":0,"unit":"reps"}""",
        )!!
        assertEquals("Stretch", idea.name)
        assertNull(idea.icon, "a word is not an emoji")
        assertEquals(IdeaRepeat.EVERY_DAY, idea.repeat, "days of the week with no days is every day")
        assertNull(idea.time)
        assertNull(idea.minutes)
        assertNull(idea.amount)
        assertNull(idea.unit, "no unit without an amount")
    }

    @Test
    fun everyDayOfTheWeekIsEveryDay() {
        val idea = GoalFromSentence.parse(
            """{"name":"Walk","emoji":"🚶","repeat":"days_of_week","days":["mon","tue","wed","thu","fri","sat","sun"],"times_per_week":null,"time":null,"minutes":null,"amount":null,"unit":null}""",
        )!!
        assertEquals(IdeaRepeat.EVERY_DAY, idea.repeat)
        assertTrue(idea.days.isEmpty())
    }

    @Test
    fun textAroundTheJsonIsIgnoredAndNoNameIsNoGoal() {
        val idea = GoalFromSentence.parse("<think></think>\nHere it is: {\"name\":\"Tidy desk\",\"emoji\":\"🧹\",\"repeat\":\"every_day\"} thanks")
        assertEquals("Tidy desk", idea?.name)
        assertNull(GoalFromSentence.parse("""{"name":"  ","emoji":"🙂","repeat":"every_day"}"""))
        assertNull(GoalFromSentence.parse("no json here"))
        assertNull(GoalFromSentence.parse("{not json"))
    }

    @Test
    fun thePromptQuotesTheSentenceSafely() {
        assertEquals("\"say 'hi' daily\" ->", GoalFromSentence.prompt("  say \"hi\" daily "))
        assertTrue(GoalFromSentence.prompt("x".repeat(500)).length < 210)
    }

    @Test
    fun theAiFileIsDescribedForPeople() {
        assertEquals("614 MB", AiFile.sizeText)
        assertEquals(64, AiFile.SHA256.length)
        assertTrue(AiFile.URL.startsWith("https://huggingface.co/") && AiFile.FILE in AiFile.URL)
    }
}
