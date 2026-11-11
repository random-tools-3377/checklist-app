package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IdeasTest {
    private val day = LocalDate(2026, 11, 11)
    private fun habit(id: Long, name: String, skill: SkillArea, repeat: Repeat = Repeat.everyDay(day), minutes: Int? = null, amount: Double? = null, unit: String? = null, target: Int = 1) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = 5, repeat = repeat, minutes = minutes, amount = amount, unit = unit, target = target, skill = skill)

    @Test
    fun everyAreaHasIdeasForEachStage() {
        for (area in SkillArea.entries) for (stage in 1..3) {
            assertEquals(8, Ideas.LIBRARY.count { it.area == area && it.stage == stage }, "$area stage $stage")
        }
        assertEquals(Ideas.LIBRARY.size, Ideas.LIBRARY.map { it.key }.toSet().size, "each idea has its own key")
    }

    @Test
    fun ideasSuitTheSkillsLevel() {
        assertEquals(1, Ideas.library(SkillArea.FITNESS, 1, emptyList(), emptySet()).first().stage)
        val building = Ideas.library(SkillArea.FITNESS, 7, emptyList(), emptySet())
        assertEquals(listOf(2, 3, 1), building.map { it.stage }.distinct())
        val further = Ideas.library(SkillArea.FITNESS, 12, emptyList(), emptySet())
        assertEquals(listOf(3, 2, 1), further.map { it.stage }.distinct())
    }

    @Test
    fun ideasLeaveOutWhatYouHaveAndWhatYouHid() {
        val all = Ideas.library(SkillArea.HOME, 1, emptyList(), emptySet())
        val bed = all.first { it.goal.name == "Make the bed" }
        val have = listOf(habit(1, "Make the bed", SkillArea.HOME))
        assertTrue(Ideas.library(SkillArea.HOME, 1, have, emptySet()).none { it.key == bed.key })
        val hidden = Ideas.decodeHidden(Ideas.encodeHidden(setOf(bed.key, "fitness:stretch")))
        assertTrue(Ideas.library(SkillArea.HOME, 1, emptyList(), hidden).none { it.key == bed.key })
        assertEquals(all.size - 1, Ideas.library(SkillArea.HOME, 1, emptyList(), hidden).size)
    }

    @Test
    fun thereAreAlwaysMoreIdeas() {
        val all = Ideas.library(SkillArea.MIND, 3, emptyList(), emptySet())
        val seen = (0 until 20).flatMap { Ideas.page(all, it) }
        assertTrue(seen.toSet().size == all.size, "every idea comes round")
        assertEquals(Ideas.PAGE, Ideas.page(all, 1000).size, "and it goes round again")
        assertEquals(2, Ideas.page(all.take(2), 5).size)
        assertTrue(Ideas.page(emptyList(), 0).isEmpty())
    }

    @Test
    fun yourOwnGoalsStepUp() {
        val goals = listOf(
            habit(1, "Run", SkillArea.FITNESS, minutes = 30),
            habit(2, "Push-ups", SkillArea.FITNESS, amount = 10.0, unit = "push-ups"),
            habit(3, "Swim", SkillArea.FITNESS, repeat = Repeat(RepeatKind.TIMES_PER, day, per = Period.WEEKLY), target = 2),
            habit(4, "Stretch", SkillArea.FITNESS),
            habit(5, "Read", SkillArea.MIND, minutes = 20),
        )
        val steps = Ideas.stepUps(goals, SkillArea.FITNESS).associateBy { it.goalId }
        assertEquals(setOf(1L, 2L, 3L), steps.keys, "nothing to step up on a plain daily goal, nor other areas")
        assertEquals(40, steps.getValue(1).goal.minutes)
        assertEquals("30 min", steps.getValue(1).from)
        assertEquals(12.0, steps.getValue(2).goal.amount)
        assertEquals(3, steps.getValue(3).goal.timesPerWeek)
        // Stepped up again, it keeps growing.
        assertEquals(50, Ideas.longer(40))
        assertEquals(75, Ideas.longer(60))
        assertEquals(10000.0, Ideas.moreOf(8000.0))
        assertEquals(6.0, Ideas.moreOf(5.0))
        assertEquals(400.0, Ideas.moreOf(300.0))
        assertTrue(Ideas.moreOf(0.5) > 0.5)
    }

    @Test
    fun theWeakestAreaIsSuggested() {
        val sheet = CharacterSheet(100, Levels.of(100, Levels.CHARACTER_STEP), SkillArea.entries.associateWith { 50L } + (SkillArea.CREATIVE to 10L))
        assertEquals(SkillArea.CREATIVE, Ideas.weakest(sheet))
        assertEquals(SkillArea.FITNESS, Ideas.weakest(CharacterSheet(0, Levels.of(0, Levels.CHARACTER_STEP), emptyMap())))
    }

    @Test
    fun theAiIsToldWhatYouDoAndWhatWasShown() {
        val goals = listOf(habit(1, "Run", SkillArea.FITNESS), habit(2, "Secret thing", SkillArea.FITNESS).copy(private = true))
        val prompt = AreaIdeasFromAi.prompt(SkillArea.FITNESS, 6, goals, listOf("Swim"))
        assertTrue("Run" in prompt)
        assertTrue("Secret" !in prompt, "private goals aren't named")
        assertTrue("not these: Swim" in prompt)
        assertTrue("building up" in prompt)
    }

    @Test
    fun theAisIdeasAreChecked() {
        val answer = """{"ideas":[{"name":"swim laps.","emoji":"🏊","times_per_week":3,"minutes":30},
            {"name":"Swim laps","emoji":"🏊","times_per_week":2,"minutes":20},
            {"name":"Jump rope","emoji":"rope","times_per_week":9,"minutes":5000},
            {"name":"Tai chi","emoji":"🧘","times_per_week":7,"minutes":15}]}"""
        val ideas = AreaIdeasFromAi.parse(answer, SkillArea.FITNESS, shown = listOf("Tai chi"))
        assertEquals(listOf("Swim laps", "Jump rope"), ideas.map { it.goal.name })
        assertEquals(IdeaRepeat.TIMES_PER_WEEK, ideas[0].goal.repeat)
        assertEquals(Effort.MEDIUM, ideas[0].effort)
        val rope = ideas[1].goal
        assertEquals(IdeaRepeat.EVERY_DAY, rope.repeat, "7 or more a week is every day")
        assertEquals(null, rope.icon)
        assertEquals(null, rope.minutes)
        assertTrue(AreaIdeasFromAi.parse("no json here", SkillArea.FITNESS).isEmpty())
        assertNotNull(Ideas.LIBRARY.first().detail)
        assertEquals("Every day · 10 min · Easy", Ideas.LIBRARY.first().detail)
    }
}
