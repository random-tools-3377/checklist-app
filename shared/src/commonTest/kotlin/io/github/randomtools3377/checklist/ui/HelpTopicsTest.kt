package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Rules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The guides in Help: their groups, search, links, and the words they use on each device. */
class HelpTopicsTest {
    private val android = GuideDevice()
    private val iphone = GuideDevice(buttons = false, plan = false, folder = false, widgets = false, ai = false, missing = "Morning plan, weekly summary and snooze")
    private val computer = GuideDevice(buttons = false, plan = false, folder = false, widgets = false, ai = false, computer = true, missing = "Morning plan, weekly summary and snooze")
    private val devices = listOf(android, iphone, computer)

    private fun listed(device: GuideDevice, query: String = "", onlyOn: Set<Feature>? = null) =
        helpList(device, query, onlyOn).flatMap { it.second }

    @Test
    fun everyGuideIsInExactlyOneGroup() {
        val inGroups = HelpGroup.entries.flatMap { it.topics }
        assertEquals(inGroups.size, inGroups.toSet().size, "a guide is in two groups")
        assertEquals(HelpTopic.entries.toSet(), inGroups.toSet())
        assertEquals(
            listOf("Basics", "Goals", "Today and planning", "Shop and points", "Extras", "Money", "Mood", "Settings and data"),
            HelpGroup.entries.map { it.title },
        )
    }

    @Test
    fun everyGuideEndsWithLinksToOthers() {
        for (topic in HelpTopic.entries) {
            assertTrue(topic.seeAlso.isNotEmpty(), "${topic.name} has no See also")
            assertFalse(topic in topic.seeAlso, "${topic.name} links to itself")
            assertEquals(topic.seeAlso.size, topic.seeAlso.toSet().size, "${topic.name} links twice")
        }
        assertTrue(HelpTopic.CATEGORIES in HelpTopic.SHOP.seeAlso)
        assertTrue(HelpTopic.MOOD_GOALS in HelpTopic.REVIEW.seeAlso)
        assertTrue(HelpTopic.MOOD_JOURNAL in HelpTopic.PRIVACY.seeAlso && HelpTopic.BUDGET in HelpTopic.PRIVACY.seeAlso)
        assertTrue(HelpTopic.PENALTY in HelpTopic.SKIPS_AND_MISSES.seeAlso && HelpTopic.GAPS in HelpTopic.SKIPS_AND_MISSES.seeAlso)
    }

    @Test
    fun everyGameExtraHasAGuide() {
        val guided = HelpTopic.entries.flatMap { it.extras }.toSet()
        listOf(
            Feature.QUESTS, Feature.RACE, Feature.BOSSES, Feature.LUCKY, Feature.COMBOS, Feature.SHIELDS,
            Feature.BUDGET_GAME, Feature.CHARACTER,
        ).forEach { assertTrue(it in guided, "no guide for ${it.title}") }
        assertEquals(setOf(Feature.CHARACTER), HelpTopic.PATHS.extras)
    }

    @Test
    fun noGuideMentionsBets() {
        val bet = Regex("\\bbet(s|ting)?\\b", RegexOption.IGNORE_CASE)
        for (device in devices) {
            for (topic in HelpTopic.entries) {
                assertFalse(bet.containsMatchIn(topic.text(device)), "${topic.name} mentions bets")
            }
        }
    }

    @Test
    fun everyWordInBracesIsFilledIn() {
        for (device in devices) {
            for (topic in HelpTopic.entries) {
                val text = topic.text(device)
                assertFalse('{' in text || '}' in text, "${topic.name}: $text")
            }
        }
    }

    @Test
    fun theGuidesUseTheNumbersSetInPointsAndRules() {
        assertEquals("Each one pays 7 points once it's done.", guideWords("Each one pays {quest} {points} once it's done.", Rules(questReward = 7)))
        assertEquals("Each one pays 1 point once it's done.", guideWords("Each one pays {quest} {points} once it's done.", Rules(questReward = 1)))
        assertEquals("costs 30% of the goal's points", guideWords("costs {penalty}% of the goal's {points}", Rules(penaltyPercent = 30)))
        // Combos pay a little more each day, up to the most days.
        assertEquals("2, then 4, up to 20", guideWords("{comboDay}, then {comboDay2}, up to {comboMost}", Rules()))
    }

    @Test
    fun theSearchLooksInTitlesAndText() {
        assertTrue(HelpTopic.REMINDERS in listed(android, "snooze"))
        assertTrue(HelpTopic.QUESTS in listed(android, "daily QUESTS"))
        // Every word has to be there, in any order.
        assertEquals(listOf(HelpTopic.LUCKY), listed(android, "chest lucky"))
        assertTrue(listed(android, "zzzz").isEmpty())
        assertEquals(HelpTopic.entries.size, listed(android).size)
    }

    @Test
    fun onlyExtrasThatAreOnLeavesTheOthersOut() {
        val none = listed(android, onlyOn = emptySet())
        assertTrue(HelpTopic.TODAY in none && HelpTopic.REMINDERS in none && HelpTopic.FEATURES in none)
        assertFalse(HelpTopic.QUESTS in none || HelpTopic.BUDGET in none || HelpTopic.MOOD_JOURNAL in none || HelpTopic.TODO_EDITOR in none)
        assertFalse(helpList(android, "", emptySet()).any { it.first == HelpGroup.MONEY || it.first == HelpGroup.MOOD })

        assertTrue(HelpTopic.QUESTS in listed(android, onlyOn = setOf(Feature.QUESTS)))
        // The budget game needs Budget on too.
        assertFalse(HelpTopic.BUDGET_GAME in listed(android, onlyOn = setOf(Feature.BUDGET_GAME)))
        assertTrue(HelpTopic.BUDGET_GAME in listed(android, onlyOn = setOf(Feature.BUDGET, Feature.BUDGET_GAME)))
    }

    @Test
    fun guidesForWhatADeviceHasNotAreLeftOut() {
        assertFalse(HelpTopic.WIDGETS in listed(iphone) || HelpTopic.AI in listed(iphone))
        assertTrue(HelpTopic.WIDGETS in listed(android) && HelpTopic.AI in listed(android))
        assertFalse(HelpTopic.BACKUP.stepsOn(iphone).any { "Choose a folder" in it })
        assertFalse("widgets" in HelpTopic.PRIVACY.text(iphone))
        assertTrue("A private goal" in HelpTopic.REMINDERS.text(iphone))
    }

    @Test
    fun theRemindersGuideSaysWhatEachDeviceCanDo() {
        val onAndroid = HelpTopic.REMINDERS.text(android)
        listOf("\"Done\"", "\"+1\"", "\"Skip\"", "\"Snooze\"", "Morning plan", "Weekly summary", "Allow exact times", "Keep reminding").forEach {
            assertTrue(it in onAndroid, "Android: $it")
        }
        val onIphone = HelpTopic.REMINDERS.text(iphone)
        assertTrue("no buttons yet" in onIphone)
        assertTrue("Morning plan, weekly summary and snooze aren't on this device yet." in onIphone)
        assertFalse("Allow exact times" in onIphone || "\"Snooze\"" in onIphone)
        assertTrue("while Checklist is running" in HelpTopic.REMINDERS.text(computer))
        assertFalse("while Checklist is running" in onIphone)
    }

    @Test
    fun thePrivacyGuideCoversMoodsAndTheBudget() {
        val text = HelpTopic.PRIVACY.text(android)
        assertTrue("mood journal" in text && "moods" in text && "The budget" in text)
        assertTrue("can't ask for its own lock" in HelpTopic.PRIVACY.text(computer))
    }

    @Test
    fun eachScreenStillOpensItsOwnGuide() {
        assertEquals(HelpTopic.TODAY, helpTopicFor("today"))
        assertEquals(HelpTopic.HABIT_EDITOR, helpTopicFor("habit/12"))
        assertEquals(HelpTopic.MOOD_JOURNAL, helpTopicFor("mood-journal"))
        assertEquals(null, helpTopicFor("help"))
        // A screen reader names the guide an ⓘ opens.
        assertEquals("How it works: Skips and misses", guideLabel(HelpTopic.SKIPS_AND_MISSES))
    }

    @Test
    fun theRaceGuideShowsTheCardsOwnWords() {
        assertTrue("like \"12 points ahead of last week\"" in HelpTopic.RACE.text(android))
    }
}
