package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.SettingsSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the Settings search finds on the Features page, and the small settings words. */
class SettingsFindTest {
    private fun featuresFound(query: String): List<Feature> =
        Feature.entries.filter { SettingsSearch.matches(query, "features extras ${featureWords(it)}") }

    @Test
    fun theSearchFindsTheExtrasByWhatPeopleType() {
        assertTrue(Feature.QUESTS in featuresFound("quests"))
        assertTrue(Feature.QUESTS in featuresFound("quest"))
        assertTrue(Feature.CHARACTER in featuresFound("character"))
        assertTrue(Feature.AI in featuresFound("AI"))
        assertTrue(Feature.CHAINS in featuresFound("habit chains"))
        assertTrue(Feature.MOOD in featuresFound("mood"))
        assertTrue(Feature.MOOD in featuresFound("journal"))
        assertTrue(Feature.BUDGET in featuresFound("budget"))
        assertTrue(Feature.BUDGET_GAME in featuresFound("budget"))
        assertTrue(Feature.BOSSES in featuresFound("boss"))
        assertTrue(Feature.TODOS in featuresFound("todos"))
        assertTrue(Feature.TODOS in featuresFound("to-do"))
        // Nothing on the Features page is about privacy.
        assertEquals(emptyList(), featuresFound("privacy"))
        // "ai" is a word of its own, not part of "chains" or "again".
        assertEquals(listOf(Feature.AI), featuresFound("ai"))
    }

    @Test
    fun theTreatBudgetShowsWithSuggestedPricesOrOnceSet() {
        assertFalse(treatBudgetShown(emptySet(), null))
        assertTrue(treatBudgetShown(setOf(Feature.SUGGESTIONS), null))
        assertTrue(treatBudgetShown(emptySet(), 20.0))
    }

    @Test
    fun theDevicesWordStartsASentence() {
        assertEquals("Phone", startOfSentence("phone"))
        assertEquals("Computer", startOfSentence("computer"))
        assertEquals("iPhone", startOfSentence("iPhone"))
    }
}
