package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Bet
import io.github.randomtools3377.checklist.core.BetState
import io.github.randomtools3377.checklist.core.Bets
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_TODAY_SECTIONS
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The streak shields line on Today, and a bet still open from before bets were removed giving its stake back. */
@RunWith(AndroidJUnit4::class)
class StakesTest {
    class AGoalAnOpenBetAndSomePoints : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.db.dao().putSetting(SettingEntity(Feature.SHIELDS.key, Feature.SHIELDS.onValue))
            // As an older version left it: bets on, Today's order with its Bets section, and a bet still open.
            c.db.dao().putSetting(SettingEntity("feature_bets", "1"))
            c.db.dao().putSetting(
                SettingEntity(SETTING_TODAY_SECTIONS, "MOOD,BALANCE,CHARACTER,QUESTS,RACE,BETS,BOSS,MONEY,WEEKLY_TARGET,STREAKS,PACE,DUE_TODAY,TODOS,THIS_WEEK,THIS_MONTH,THIS_YEAR,PROJECTS"),
            )
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Gym", icon = "🏋️", points = 10, repeat = Repeat.timesPer(Period.WEEKLY, nowLocal().date), target = 5, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            val id = c.db.dao().activeGoals().single().id
            c.db.dao().insertEntry(Entry(at = 0, day = nowLocal().date, kind = EntryKind.TICK, amount = 100, label = "Start").toEntity())
            c.book.tickHabit(id)
            c.db.dao().insertEntry(Entry(at = 1, day = nowLocal().date, kind = EntryKind.BET, amount = -25, goalId = id, label = "Bet: Gym 3 times this week").toEntity())
            c.db.dao().putSetting(SettingEntity(Bets.SETTING, Bets.encode(listOf(Bet(id, nowLocal().date, 3, 25)))))
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AGoalAnOpenBetAndSomePoints()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun anOpenBetGivesItsStakeBackAndTodayHasNoBets() {
        // Opening the app gives the stake back, once.
        app.waitFor { runBlocking { container.book.balance() } == 100 + 10 }
        app.waitFor { Bets.decode(runBlocking { container.db.dao().setting(Bets.SETTING) }).singleOrNull()?.state == BetState.CANCELLED }
        // The shields line still shows; there is nothing about bets on Today.
        app.waitFor { app.shownIn("today-list", "shields") }
        assertTrue(!shown("bets") && !shown("bet-place"))
        assertEquals(1, runBlocking { container.db.dao().allEntries() }.count { it.label == Bets.STAKE_BACK })
        assertEquals(100 + 10, runBlocking { container.book.balance() })
    }
}
