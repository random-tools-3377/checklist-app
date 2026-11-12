package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.Feature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Who sees the first-run setup, its steps, and what its choices turn on. */
class FirstRunTest {
    @Test
    fun onlySomeoneBrandNewSeesIt() {
        assertEquals(SetupStep.PARTS, FirstRun.step(setting = null, guide = null, nothingYet = true, underTest = false))
        // Someone with goals, or goals removed, or who answered the old welcome card, never does.
        assertNull(FirstRun.step(null, null, nothingYet = false, underTest = false))
        assertNull(FirstRun.step(null, "done", nothingYet = true, underTest = false))
        // Finished or skipped.
        assertNull(FirstRun.step(FirstRun.DONE, null, nothingYet = true, underTest = false))
    }

    @Test
    fun onceStartedItCarriesOnWhereItWas() {
        // A first goal added at the second step doesn't end it.
        assertEquals(SetupStep.DAY, FirstRun.step(SetupStep.DAY.name, "done", nothingYet = false, underTest = false))
        assertEquals(SetupStep.GOAL, FirstRun.step(SetupStep.GOAL.name, null, nothingYet = false, underTest = false))
    }

    @Test
    fun underTestItShowsOnlyWhenATestStartsIt() {
        assertNull(FirstRun.step(null, null, nothingYet = true, underTest = true))
        assertEquals(SetupStep.PARTS, FirstRun.step(SetupStep.PARTS.name, null, nothingYet = true, underTest = true))
    }

    @Test
    fun theStepsGoInOrder() {
        assertEquals(SetupStep.GOAL, FirstRun.next(SetupStep.PARTS))
        assertEquals(SetupStep.DAY, FirstRun.next(SetupStep.GOAL))
        assertNull(FirstRun.next(SetupStep.DAY))
        assertNull(FirstRun.before(SetupStep.PARTS))
        assertEquals(SetupStep.GOAL, FirstRun.before(SetupStep.DAY))
    }

    @Test
    fun eachPartTurnsOnItsExtras() {
        assertEquals(listOf(Feature.TODOS, Feature.MOOD), FirstRun.features(listOf(SetupPart.MOOD, SetupPart.TODOS)))
        assertEquals(listOf(Feature.BUDGET), FirstRun.features(listOf(SetupPart.BUDGET)))
        val game = FirstRun.features(listOf(SetupPart.GAME))
        listOf(Feature.CHARACTER, Feature.QUESTS, Feature.COMBOS, Feature.RACE, Feature.BOSSES, Feature.LUCKY, Feature.SHIELDS).forEach {
            assertTrue(it in game, it.title)
        }
        assertTrue(game.all { it.group == "Game" })
        assertFalse(Feature.BUDGET in game || Feature.BUDGET_GAME in game)
        assertTrue(FirstRun.features(emptyList()).isEmpty())
    }

    @Test
    fun comingBackToThePartsShowsWhatIsOnAndCanTurnItOff() {
        // Nothing on yet: "Next" turns on only what was picked.
        assertEquals(mapOf(Feature.MOOD to true), FirstRun.changes(listOf(SetupPart.MOOD), emptySet()))
        // Back with the back arrow: the part picked before shows on.
        val on = setOf(Feature.MOOD)
        assertEquals(listOf(SetupPart.MOOD), FirstRun.picked(on))
        // Left on, nothing changes; switched off, its extra goes off again.
        assertTrue(FirstRun.changes(listOf(SetupPart.MOOD), on).isEmpty())
        assertEquals(mapOf(Feature.MOOD to false, Feature.TODOS to true), FirstRun.changes(listOf(SetupPart.TODOS), on))
        // The game shows on only with all its extras on.
        assertFalse(SetupPart.GAME in FirstRun.picked(setOf(Feature.CHARACTER)))
        assertTrue(SetupPart.GAME in FirstRun.picked(SetupPart.GAME.features.toSet()))
    }

    @Test
    fun theTourSaysWhatOverTheLimitCosts() {
        assertEquals("half", penaltyShare(50))
        assertEquals("30% of", penaltyShare(30))
    }

    @Test
    fun theTourSaysWhatTheExamplesAdd() {
        assertEquals("This adds 5 example goals, a to-do and 2 rewards to your lists.", examplesText())
    }
}
