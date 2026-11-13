package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The app's own reading of a sentence about a habit, and how it puts the AI's reading right. */
class SentenceReaderTest {
    @Test
    fun readsDaysTimeAndAmount() {
        val read = SentenceReader.read("read 20 pages every weekday at 9pm")
        assertEquals(IdeaRepeat.DAYS_OF_WEEK, read.repeat)
        assertEquals(listOf(1, 2, 3, 4, 5), read.days)
        assertEquals(LocalTime(21, 0), read.time)
        assertEquals(20.0, read.amount)
        assertEquals("pages", read.unit)
        assertNull(read.minutes)
        assertEquals("Read", read.name)
    }

    @Test
    fun readsTimesAWeekAndLength() {
        val read = SentenceReader.read("Gym 3 times a week for an hour")
        assertEquals(IdeaRepeat.TIMES_PER_WEEK, read.repeat)
        assertEquals(3, read.timesPerWeek)
        assertEquals(60, read.minutes)
        assertNull(read.amount)
        assertNull(read.time)
        assertEquals("Gym", read.name)
        assertEquals(2, SentenceReader.read("swim twice a week").timesPerWeek)
        assertEquals(30, SentenceReader.read("stretch for 30 minutes").minutes)
        assertEquals(30, SentenceReader.read("walk half an hour daily").minutes)
        assertEquals(90, SentenceReader.read("study for 1.5 hours").minutes)
        assertEquals(45, SentenceReader.read("yoga 45 min on mondays").minutes)
    }

    @Test
    fun readsClockTimesInTheirForms() {
        assertEquals(LocalTime(7, 30), SentenceReader.read("run at 7:30 am").time)
        assertEquals(LocalTime(21, 15), SentenceReader.read("journal at 21:15").time)
        assertEquals(LocalTime(12, 0), SentenceReader.read("take vitamins at noon").time)
        assertEquals(LocalTime(19, 0), SentenceReader.read("call home at 7 in the evening").time)
        assertEquals(LocalTime(8, 0), SentenceReader.read("meditate at 8").time)
        assertEquals(LocalTime(0, 0), SentenceReader.read("lights out at 12am").time)
        assertNull(SentenceReader.read("drink water every day").time)
    }

    @Test
    fun readsNamedDaysAndEveryDay() {
        val read = SentenceReader.read("Water the plants on mondays and thursdays")
        assertEquals(listOf(1, 4), read.days)
        assertEquals("Water the plants", read.name)
        assertEquals(listOf(6, 7), SentenceReader.read("long walk at weekends").days)
        assertEquals(IdeaRepeat.EVERY_DAY, SentenceReader.read("drink water every day").repeat)
        assertNull(SentenceReader.read("drink water").repeat)
        val glasses = SentenceReader.read("drink 8 glasses of water daily")
        assertEquals(8.0, glasses.amount)
        assertEquals("glasses", glasses.unit)
        assertEquals("Drink water", glasses.name)
    }

    @Test
    fun aGoalWithNoAiKeepsTheNameAsTyped() {
        val idea = SentenceReader.idea("practise guitar 4 times a week for 20 minutes")!!
        assertEquals("Practise guitar", idea.name)
        assertNull(idea.icon)
        assertEquals(IdeaRepeat.TIMES_PER_WEEK, idea.repeat)
        assertEquals(4, idea.timesPerWeek)
        assertEquals(20, idea.minutes)
        assertNull(SentenceReader.idea("every day at 9pm"))
        assertEquals(IdeaRepeat.EVERY_DAY, SentenceReader.idea("floss")!!.repeat)
    }

    @Test
    fun theAppsReadingWinsOverTheAis() {
        // The AI slipped on the time and the amount, and made up a length.
        val answer = """{"name":"Read","emoji":"📖","repeat":"every_day","days":[],"times_per_week":null,"time":"09:00","minutes":20,"amount":2,"unit":"page"}"""
        val idea = GoalFromSentence.parse(answer, "read 20 pages every weekday at 9pm")!!
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
    fun numbersTheSentenceNeverHeldAreDropped() {
        val answer = """{"name":"Drink water","emoji":"💧","repeat":"times_per_week","days":[],"times_per_week":3,"time":"08:00","minutes":10,"amount":8,"unit":"glasses"}"""
        val idea = GoalFromSentence.parse(answer, "drink water")!!
        assertEquals(IdeaRepeat.EVERY_DAY, idea.repeat)
        assertNull(idea.time)
        assertNull(idea.minutes)
        assertNull(idea.amount)
        assertNull(idea.unit)
    }

    @Test
    fun whatWasUnderstoodIsSaidInPlainWords() {
        val idea = GoalFromSentence.parse("""{"name":"Read","emoji":"📖","repeat":"every_day","days":[],"times_per_week":null,"time":null,"minutes":null,"amount":null,"unit":null}""", "read 20 pages every weekday at 9pm")!!
        assertEquals("📖 Read · Mon to Fri · 9:00 pm · 20 pages", idea.describe())
        assertEquals(listOf("No length given"), idea.missing())
        val gym = SentenceReader.idea("Gym 3 times a week for an hour")!!
        assertEquals("Gym · 3 times a week · 60 min", gym.describe())
        assertEquals(listOf("No time given", "No amount given"), gym.missing())
        assertEquals("Mon, Wed and Fri".replace(" and", ","), daysText(listOf(1, 3, 5)))
        assertEquals("Sat and Sun", daysText(listOf(7, 6)))
    }

    @Test
    fun pointsAndAreaAreSuggestedByTheAppsOwnRules() {
        val gym = SentenceReader.idea("Gym 3 times a week for an hour")!!.suggestion()
        assertEquals(Effort.HARD, gym.effort)
        assertEquals(Suggest.points(Effort.HARD, 60), gym.points)
        assertEquals(SkillArea.FITNESS, gym.area)
        assertTrue("Hard for 60 minutes" in gym.reason, gym.reason)
        val plain = SentenceReader.idea("floss")!!.suggestion()
        assertEquals(Effort.MEDIUM, plain.effort)
        assertTrue("no length was given" in plain.reason)
    }
}
