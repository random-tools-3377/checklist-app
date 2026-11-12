package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the "Replace everything?" question counts, and when it warns. */
class BackupSummaryTest {
    private val day = LocalDate(2026, 11, 12)

    private fun backup(
        goals: List<Goal> = emptyList(),
        settings: Map<String, String> = emptyMap(),
        moods: List<Mood> = emptyList(),
        money: List<MoneyEntry> = emptyList(),
        bills: List<Bill> = emptyList(),
        focus: List<FocusSession> = emptyList(),
    ) = Backup(
        exportedAt = 0, goals = goals, steps = emptyList(), ticks = emptyList(), entries = emptyList(), rewards = emptyList(),
        purchases = emptyList(), settings = settings, moods = moods, money = money, bills = bills, focusSessions = focus,
    )

    private fun habit(name: String, archived: Boolean = false) =
        Goal(kind = GoalKind.HABIT, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(day), archived = archived)

    private fun project(name: String) = Goal(kind = GoalKind.PROJECT, name = name, icon = "📦", points = 0, repeat = null)

    @Test
    fun habitsProjectsAndToDosAreCountedApart() {
        // A to-do with a date has a repeat, and one without has none: neither is a habit or a project.
        val goals = listOf(
            habit("Water"), habit("Read"), habit("Old habit", archived = true), project("Shed"),
            Todos.of("Buy milk"), Todos.of("Post letter", date = day), Todos.of("Call back", date = day),
        )
        val summary = BackupSummary.of(backup(goals = goals))
        assertEquals(2, summary.habits)
        assertEquals(1, summary.projects)
        assertEquals(3, summary.todos)
        assertEquals("2 habits, 1 project, 3 to-dos, 0 rewards and 0 ticks", summary.counts())
    }

    @Test
    fun theBudgetMoodsAndFocusTimeAreNoticed() {
        assertTrue(BackupSummary.of(backup()).isEmpty)
        assertTrue(BackupSummary.of(backup(money = listOf(MoneyEntry(kind = MoneyKind.SPENT, cents = 250, day = day)))).budget)
        assertTrue(BackupSummary.of(backup(bills = listOf(Bill(name = "Rent", icon = "🏠", cents = 1000, first = day)))).budget)
        assertTrue(BackupSummary.of(backup(settings = mapOf(PayPlan.SETTING to "x"))).budget)
        assertFalse(BackupSummary.of(backup(settings = mapOf(PayPlan.SETTING to ""))).budget)
        assertTrue(BackupSummary.of(backup(moods = listOf(Mood(day, 4)))).moods)
        val focus = BackupSummary.of(backup(focus = listOf(FocusSession(goalId = 1, day = day, startedAt = 0, minutes = 25))))
        assertTrue(focus.focus)
        assertFalse(focus.isEmpty)
    }

    @Test
    fun itWarnsWhenTheFileHasNoneOfWhatThisDeviceHas() {
        val device = BackupSummary.of(
            backup(
                moods = listOf(Mood(day, 4)),
                money = listOf(MoneyEntry(kind = MoneyKind.SPENT, cents = 250, day = day)),
                focus = listOf(FocusSession(goalId = 1, day = day, startedAt = 0, minutes = 25)),
            ),
        )
        val old = BackupSummary.of(backup(goals = listOf(habit("Water"))))
        assertEquals(listOf("the budget", "the mood journal", "focus time"), ImportCheck.missing(old, device))
        assertEquals(
            "This file has nothing for the budget, the mood journal and focus time, but this phone does. Importing removes them from this phone.",
            ImportCheck.warning(old, device, "phone"),
        )
        // A file with moods of its own only lacks the rest.
        val withMoods = BackupSummary.of(backup(moods = listOf(Mood(day, 2)), money = listOf(MoneyEntry(kind = MoneyKind.SPENT, cents = 1, day = day))))
        assertEquals(
            "This file has nothing for focus time, but this computer does. Importing removes it from this computer.",
            ImportCheck.warning(withMoods, device, "computer"),
        )
        // Nothing to warn about when the device has none of them, or the file has them all.
        assertNull(ImportCheck.warning(old, BackupSummary.of(backup(goals = listOf(habit("Read")))), "phone"))
        assertNull(ImportCheck.warning(device, device, "phone"))
    }
}
