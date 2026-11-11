package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** While the lock is on for private goals, the review leaves them out and the history doesn't name them. */
class PrivateGoalsTest {
    private val start = LocalDate(2026, 1, 1)
    private val monday = LocalDate(2026, 11, 9)
    private val days = (0..6).map { monday.plusDays(it) }

    private val walk = Goal(id = 1, kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 10, repeat = Repeat.everyDay(start), category = "Health")
    private val secret = Goal(id = 2, kind = GoalKind.HABIT, name = "Secret", icon = "🤫", points = 6, repeat = Repeat.everyDay(start), private = true, category = "Hidden")

    private fun tick(goalId: Long, day: LocalDate) = Tick(goalId = goalId, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day)
    private fun entry(kind: EntryKind, amount: Int, day: LocalDate, goalId: Long? = null, label: String = "x", tickId: Long? = null) =
        Entry(at = 0, day = day, kind = kind, amount = amount, goalId = goalId, label = label, tickId = tickId)

    private val ticks = days.map { tick(1, it) } + days.take(2).map { tick(2, it) }
    private val lapses = listOf(
        Lapse(goalId = 2, kind = LapseKind.MISS, day = days[3], period = Period.DAILY, periodStart = days[3], at = 0, overLimit = true, cost = 3, reason = Reason.ILL, label = "Secret"),
    )
    private val entries = listOf(
        entry(EntryKind.TICK, 70, monday, goalId = 1, label = "Walk"),
        entry(EntryKind.TICK, 12, monday, goalId = 2, label = "Secret"),
        entry(EntryKind.PENALTY, -3, days[3], goalId = 2, label = "Secret"),
        entry(EntryKind.PURCHASE, -20, days[1], label = "Film"),
    )

    private fun summary(hidePrivate: Boolean) = Review.summarize(
        monday, days.last(), today = days.last(), goals = listOf(walk, secret), ticks = ticks, lapses = lapses,
        entries = entries, gaps = emptyList(), hidePrivate = hidePrivate,
    )

    @Test
    fun theReviewLeavesPrivateGoalsOutWhileLocked() {
        val locked = summary(hidePrivate = true)
        assertEquals(listOf("Walk"), locked.goals.map { it.goal.name })
        assertNull(locked.mostMissed)
        assertEquals(emptyList(), locked.reasons)
        assertEquals(listOf(70, 20, 0), listOf(locked.earned, locked.spent, locked.lost))
        assertEquals(listOf("Health"), locked.categories.map { it.name })
        assertEquals(7, locked.days.sumOf { it.due })

        val open = summary(hidePrivate = false)
        assertEquals(listOf("Walk", "Secret"), open.goals.map { it.goal.name })
        assertEquals("Secret", open.mostMissed?.goal?.name)
        assertEquals(listOf(82, 20, 3), listOf(open.earned, open.spent, open.lost))
    }

    @Test
    fun theHistoryKeepsTheAmountsButNotTheNames() {
        val withTick = entries + entry(EntryKind.TICK, 6, days[1], goalId = 2, label = "Secret", tickId = 9)
        val shown = PrivateGoals.unnamed(withTick, PrivateGoals.ids(listOf(walk, secret)))
        assertEquals(listOf("Walk", PrivateGoals.NAME, PrivateGoals.NAME, "Film", PrivateGoals.NAME), shown.map { it.label })
        assertEquals(withTick.map { it.amount }, shown.map { it.amount })
        assertNull(shown.last().tickId)
        assertEquals(withTick, PrivateGoals.unnamed(withTick, emptySet()))
    }
}
