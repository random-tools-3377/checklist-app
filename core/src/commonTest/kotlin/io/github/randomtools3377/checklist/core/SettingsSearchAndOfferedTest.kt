package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSearchAndOfferedTest {
    private val day = LocalDate(2026, 11, 11)

    @Test
    fun searchFindsPluralsAndSimpleWordForms() {
        assertTrue(SettingsSearch.matches("quest", "Daily quests"))
        assertTrue(SettingsSearch.matches("quests", "A quest pays"))
        assertTrue(SettingsSearch.matches("reminders", "Reminder at your usual time"))
        assertTrue(SettingsSearch.matches("skipping", "Reasons for skips and misses"))
        assertTrue(SettingsSearch.matches("skipped", "skip"))
        assertTrue(SettingsSearch.matches("locked", "Privacy lock"))
        assertTrue(SettingsSearch.matches("categories", "A category for each goal"))
        assertTrue(SettingsSearch.matches("bosses", "Items and bosses"))
        assertTrue(SettingsSearch.matches("boss", "Items and bosses"))
        assertTrue(SettingsSearch.matches("add", "Goals added today"))
        assertTrue(SettingsSearch.matches("Habit Chains", "Habit chains: a goal can come after another"))
        assertTrue(SettingsSearch.matches("dark mode", "Theme: light, dark mode, black"))
        assertTrue(SettingsSearch.matches("notif", "Notifications"))
        assertTrue(SettingsSearch.matches("to-do", "To-dos on Today"))
        assertTrue(SettingsSearch.matches("  ", "anything"))
    }

    @Test
    fun searchWordsHaveToStartAWord() {
        // "AI" is the AI helper, not every word with "ai" in it.
        assertTrue(SettingsSearch.matches("ai", "AI helper"))
        assertFalse(SettingsSearch.matches("ai", "Habit chains, again and paid"))
        // Every word has to be found.
        assertFalse(SettingsSearch.matches("dark sound", "Theme: dark mode"))
        assertFalse(SettingsSearch.matches("privacy", "Features extras to-dos mood budget"))
    }

    @Test
    fun stemsAreSimple() {
        assertEquals("quest", SettingsSearch.stem("quests"))
        assertEquals("category", SettingsSearch.stem("categories"))
        assertEquals("box", SettingsSearch.stem("boxes"))
        assertEquals("skip", SettingsSearch.stem("skipping"))
        assertEquals("remind", SettingsSearch.stem("reminding"))
        assertEquals("miss", SettingsSearch.stem("missed"))
        assertEquals("miss", SettingsSearch.stem("misses"))
        assertEquals("bonus", SettingsSearch.stem("bonus"))
        assertEquals("privacy", SettingsSearch.stem("privacy"))
        // A plural of an "-ing" word is the same word as the "-ing" word itself.
        assertEquals(SettingsSearch.stem("morning"), SettingsSearch.stem("mornings"))
        assertEquals(SettingsSearch.stem("saving"), SettingsSearch.stem("savings"))
        assertTrue(SettingsSearch.matches("savings", "reaching a saving goal"))
        assertTrue(SettingsSearch.matches("settings", "Setting"))
    }

    @Test
    fun wordsAreRunsOfLettersAndDigits() {
        assertEquals(listOf("today", "s", "section"), SettingsSearch.words("Today's sections"))
        assertEquals(listOf("to", "do", "colour", "blind", "10"), SettingsSearch.words("To-do, colour-blind (10)"))
        assertEquals(listOf("café"), SettingsSearch.words("Café"))
        assertEquals(emptyList(), SettingsSearch.words(" · "))
    }

    @Test
    fun extrasSectionsShowOnlyOnceTurnedOnOrInUse() {
        val off = Offered.sections(Sections.DEFAULT, emptySet()).map { it.section }
        listOf(
            TodaySection.MOOD, TodaySection.CHARACTER, TodaySection.QUESTS, TodaySection.RACE, TodaySection.BOSS,
            TodaySection.MONEY, TodaySection.TODOS, TodaySection.WEEKLY_TARGET,
        ).forEach { assertFalse(it in off, "$it shows with its extra off") }
        listOf(TodaySection.BALANCE, TodaySection.STREAKS, TodaySection.PACE, TodaySection.DUE_TODAY, TodaySection.PROJECTS)
            .forEach { assertTrue(it in off, "$it always shows") }

        val on = setOf(Feature.MOOD, Feature.CHARACTER, Feature.QUESTS, Feature.RACE, Feature.BOSSES, Feature.BUDGET, Feature.TODOS)
        val all = Offered.sections(Sections.DEFAULT, on, Offered.InUse(weeklyTarget = true)).map { it.section }
        listOf(
            TodaySection.MOOD, TodaySection.CHARACTER, TodaySection.QUESTS, TodaySection.RACE, TodaySection.BOSS,
            TodaySection.MONEY, TodaySection.TODOS, TodaySection.WEEKLY_TARGET,
        ).forEach { assertTrue(it in all, "$it shows with its extra on") }

        // In use with the extra off: to-dos that exist, and a mood already given today.
        val inUse = Offered.sections(Sections.DEFAULT, emptySet(), Offered.InUse(todos = true, moodToday = true)).map { it.section }
        assertTrue(TodaySection.TODOS in inUse)
        assertTrue(TodaySection.MOOD in inUse)
    }

    @Test
    fun aSectionMovesPastTheHiddenOnes() {
        val listed = Offered.sections(Sections.DEFAULT, emptySet()).map { it.section }
        // Streaks goes up past the extras' hidden sections, to just before the one shown above it.
        val above = listed[listed.indexOf(TodaySection.STREAKS) - 1]
        val moved = Offered.move(Sections.DEFAULT, TodaySection.STREAKS, -1, listed)
        val shownOrder = moved.map { it.section }.filter { it in listed }
        assertEquals(shownOrder.indexOf(TodaySection.STREAKS) + 1, shownOrder.indexOf(above))
        assertTrue(moved.indexOf(moved.first { it.section == TodaySection.STREAKS }) < Sections.DEFAULT.indexOfFirst { it.section == TodaySection.WEEKLY_TARGET })
        // The hidden ones keep their order and stay in the list.
        assertEquals(TodaySection.entries.size, moved.size)
        assertEquals(
            Sections.DEFAULT.map { it.section }.filter { it !in listed },
            moved.map { it.section }.filter { it !in listed },
        )
        // The first one shown can't go further up, nor the last one down.
        assertEquals(Sections.DEFAULT, Offered.move(Sections.DEFAULT, listed.first(), -1, listed))
        assertEquals(Sections.DEFAULT, Offered.move(Sections.DEFAULT, listed.last(), 1, listed))
        // With everything shown, it is the same as moving one place.
        val everything = TodaySection.entries
        assertEquals(Sections.move(Sections.DEFAULT, Sections.DEFAULT.lastIndex, -1), Offered.move(Sections.DEFAULT, TodaySection.PROJECTS, -1, everything))
        assertEquals(Sections.move(Sections.DEFAULT, 2, 1), Offered.move(Sections.DEFAULT, Sections.DEFAULT[2].section, 1, everything))
    }

    @Test
    fun rulesForExtrasThatAreOffAreHiddenUnlessChanged() {
        val usual = Rules()
        val shown = Offered.rules(Rules.FIELDS, emptySet(), usual).map { it.key }
        assertTrue("penaltyPercent" in shown)
        assertTrue("normalPayPercent" in shown)
        assertFalse("questReward" in shown)
        assertFalse("bossReward" in shown)
        assertFalse("goodMoneyDay" in shown)
        assertFalse("moodMinDays" in shown)
        // On, or already changed: shown.
        assertTrue("questReward" in Offered.rules(Rules.FIELDS, setOf(Feature.QUESTS), usual).map { it.key })
        assertTrue("bossReward" in Offered.rules(Rules.FIELDS, emptySet(), usual.copy(bossReward = usual.bossReward + 1)).map { it.key })
        // Every number listed for an extra is one of the rules.
        val keys = Rules.FIELDS.map { it.key }.toSet()
        Offered.RULE_FEATURES.keys.forEach { assertTrue(it in keys, "$it is not a rule") }
    }

    private fun habit(id: Long, repeat: Repeat) = Goal(id = id, kind = GoalKind.HABIT, name = "Habit $id", icon = "✅", points = 1, repeat = repeat)

    @Test
    fun habitsWhoseLastDayPassedAndFinishedProjectsAreCompleted() {
        val going = habit(1, Repeat.everyDay(day.minusDays(30)))
        val endsToday = habit(2, Repeat.everyDay(day.minusDays(30)).copy(end = day))
        val ended = habit(3, Repeat.everyDay(day.minusDays(30)).copy(end = day.minusDays(2)))
        val once = habit(4, Repeat(RepeatKind.ONCE, day.minusDays(1)))
        val onceMovedOn = habit(5, Repeat(RepeatKind.ONCE, day.minusDays(1), moves = listOf(Move(day.minusDays(1), day.plusDays(1)))))
        // A week's goal ending on a Wednesday has until that Sunday.
        val wednesday = LocalDate(2026, 11, 11)
        val weekly = habit(6, Repeat.timesPer(Period.WEEKLY, day.minusDays(30)).copy(end = wednesday.minusDays(7)))
        val weeklyThisWeek = habit(7, Repeat.timesPer(Period.WEEKLY, day.minusDays(30)).copy(end = wednesday.minusDays(1)))
        val afterDone = habit(8, Repeat(RepeatKind.AFTER_DONE, day.minusDays(10)))
        val project = Goal(id = 9, kind = GoalKind.PROJECT, name = "Project", icon = "📦", points = 0, repeat = null)
        val openProject = Goal(id = 10, kind = GoalKind.PROJECT, name = "Open project", icon = "📦", points = 0, repeat = null)
        val todo = Goal(id = 11, kind = GoalKind.TODO, name = "To-do", icon = "📝", points = 1, repeat = null)

        assertNull(Completed.habitLastDay(going, day))
        assertNull(Completed.habitLastDay(endsToday, day))
        assertEquals(day.minusDays(2), Completed.habitLastDay(ended, day))
        assertEquals(day.minusDays(1), Completed.habitLastDay(once, day))
        assertNull(Completed.habitLastDay(onceMovedOn, day))
        assertEquals(LocalDate(2026, 11, 8), Completed.habitLastDay(weekly, day))
        assertNull(Completed.habitLastDay(weeklyThisWeek, day))
        assertNull(Completed.habitLastDay(afterDone, day))

        val goals = listOf(going, endsToday, ended, once, onceMovedOn, weekly, weeklyThisWeek, afterDone, project, openProject, todo)
        val (still, completed) = Completed.split(goals, mapOf(project.id to day.minusDays(5)), day)
        assertEquals(listOf(1L, 2L, 5L, 7L, 8L, 10L, 11L), still.map { it.id })
        // Newest first.
        assertEquals(listOf(4L, 3L, 6L, 9L), completed.map { it.goal.id })
    }

    @Test
    fun coloursHaveNamesForScreenReaders() {
        assertEquals("Ocean blue", ColourNames.of(0xFF2F80C0.toInt()))
        assertEquals("Vermillion", ColourNames.of(0xFFD55E00.toInt()))
        Colors.GOAL_COLORS.forEach { assertFalse(ColourNames.of(it).startsWith("#"), Colors.hex(it)) }
        assertEquals("Dark blue", ColourNames.of(0xFF102060.toInt()))
        assertEquals("Red", ColourNames.of(0xFFE02020.toInt()))
        assertEquals("Light pink", ColourNames.of(0xFFF8C0D8.toInt()))
        assertEquals("Grey", ColourNames.of(0xFF808080.toInt()))
    }

    @Test
    fun moodFacesSayWhichIsLowestAndHighest() {
        assertEquals("Mood 1 of 5, the lowest", Moods.spoken(1))
        assertEquals("Mood 3 of 5", Moods.spoken(3))
        assertEquals("Mood 5 of 5, the highest", Moods.spoken(5))
    }
}
