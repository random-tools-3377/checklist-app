package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoriesAndChartsTest {
    private val start = LocalDate(2026, 1, 1)
    private val monday = LocalDate(2026, 11, 9)

    private fun habit(id: Long, name: String, category: String?, position: Int = id.toInt()) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(start), category = category, position = position)

    private fun tick(goalId: Long, day: LocalDate) = Tick(goalId = goalId, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day)
    private fun entry(kind: EntryKind, amount: Int, day: LocalDate, goalId: Long? = null) =
        Entry(at = 0, day = day, kind = kind, amount = amount, goalId = goalId, label = "x")

    @Test
    fun aCategoryNameIsTidiedAndJoinsOneAlreadyInUse() {
        assertEquals("Health", Categories.tidy("  health ", listOf("Health", "Home")))
        assertEquals("Garden work", Categories.tidy("Garden   work", listOf("Health")))
        assertNull(Categories.tidy("   ", listOf("Health")))
        assertNull(Categories.tidy(null, emptyList()))
        assertEquals(Categories.LENGTH, Categories.tidy("x".repeat(50), emptyList())!!.length)
    }

    @Test
    fun categoriesComeInTheOrderOfTheirFirstGoalAndFilterToday() {
        val goals = listOf(
            habit(1, "Run", "Health", position = 2),
            habit(2, "Dishes", "Home", position = 0),
            habit(3, "Read", null, position = 1),
            habit(4, "Water", "Health", position = 3),
            habit(5, "Old", "Gone", position = 4).copy(archived = true),
        )
        assertEquals(listOf("Home", "Health"), Categories.of(goals))
        assertEquals(listOf("Run", "Water"), goals.filter { Categories.shows("Health", it) }.map { it.name })
        assertEquals(5, goals.count { Categories.shows(null, it) })
    }

    @Test
    fun theReviewSumsUpEachCategoryAndEachDaysPoints() {
        val goals = listOf(habit(1, "Run", "Health"), habit(2, "Water", "Health"), habit(3, "Dishes", "Home"), habit(4, "Read", null))
        val days = (0..2).map { monday.plusDays(it) }
        val ticks = days.map { tick(1, it) } + listOf(tick(2, monday), tick(3, days[1]))
        val entries = listOf(
            entry(EntryKind.TICK, 5, monday, 1), entry(EntryKind.TICK, 5, days[1], 1), entry(EntryKind.TICK, 5, days[2], 1),
            entry(EntryKind.TICK, 5, monday, 2),
            entry(EntryKind.TICK, 5, days[1], 3),
            entry(EntryKind.PENALTY, -2, days[2], 3),
            entry(EntryKind.PURCHASE, -20, days[2]),
        )

        val summary = Review.summarize(monday, days.last(), today = days.last(), goals = goals, ticks = ticks, lapses = emptyList(), entries = entries, gaps = emptyList())

        val health = summary.categories.first { it.name == "Health" }
        assertEquals(listOf(6, 4, 20), listOf(health.due, health.done, health.points))
        val home = summary.categories.first { it.name == "Home" }
        assertEquals(listOf(3, 1, 5), listOf(home.due, home.done, home.points))
        val none = summary.categories.last()
        assertNull(none.name) // goals without a category come last
        assertEquals(listOf(3, 0, 0), listOf(none.due, none.done, none.points))
        assertEquals(listOf("Health", "Home", null), summary.categories.map { it.name })

        assertEquals(mapOf(monday to 10, days[1] to 10, days[2] to 5), summary.earnedByDay)
        assertEquals(mapOf(days[2] to 2), summary.lostByDay)
    }

    @Test
    fun withoutCategoriesTheReviewHasNone() {
        val summary = Review.summarize(monday, monday, monday, listOf(habit(1, "Run", null)), emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(summary.categories.isEmpty())
    }

    @Test
    fun aGoalsCategoryGoesInTheBackupFile() {
        val backup = Backup(
            exportedAt = 0, goals = listOf(habit(1, "Run", "Health")), steps = emptyList(), ticks = emptyList(),
            entries = emptyList(), rewards = emptyList(), purchases = emptyList(), settings = emptyMap(),
        )
        val back = Backup.decode(Backup.encode(backup))
        assertEquals("Health", back.goals.single().category)
        assertEquals(7, back.format)
        // An older file, with no categories, still reads.
        val old = Backup.encode(backup.copy(goals = listOf(habit(1, "Run", null)))).replace("\"category\": null,", "")
        assertNull(Backup.decode(old).goals.single().category)
    }
}
