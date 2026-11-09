package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GoalSearchTest {
    private val goals = listOf(
        Goal(id = 1, kind = GoalKind.HABIT, name = "Drink water", icon = "💧", points = 1, repeat = null, category = "Health"),
        Goal(id = 2, kind = GoalKind.HABIT, name = "Read a book", icon = "📖", points = 5, repeat = null),
        Goal(id = 3, kind = GoalKind.TODO, name = "Water the plants", icon = "🪴", points = 2, repeat = null, category = "Home"),
    )

    private fun ids(query: String) = GoalSearch.filter(goals, query).map { it.id }

    @Test
    fun everyWordIsFoundInTheNameOrCategory() {
        assertEquals(listOf(1L, 2L, 3L), ids("  "))
        assertEquals(listOf(1L, 3L), ids("WATER"))
        assertEquals(listOf(1L), ids("water health"))
        assertEquals(listOf(3L), ids("home wat"))
        assertEquals(emptyList(), ids("water book"))
    }
}
