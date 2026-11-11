package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Bets
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Betting on yourself from Today, and the streak shields line. */
@RunWith(AndroidJUnit4::class)
class StakesTest {
    class AGoalAndSomePoints : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            listOf(Feature.BETS, Feature.SHIELDS).forEach { c.db.dao().putSetting(SettingEntity(it.key, it.onValue)) }
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Gym", icon = "🏋️", points = 10, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            c.db.dao().insertEntry(Entry(at = 0, day = nowLocal().date, kind = EntryKind.TICK, amount = 100, label = "Start").toEntity())
            c.book.tickHabit(c.db.dao().activeGoals().single().id)
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AGoalAndSomePoints()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aBetIsPlacedFromToday() {
        app.waitFor { app.shownIn("today-list", "shields") }
        app.waitFor { app.shownIn("today-list", "bet-place") }
        app.tap("bet-place")
        app.waitFor { shown("bet-goal:Gym") }
        app.tap("bet-times:3")
        app.tap("bet-stake:25")
        app.tap("bet-ok")
        app.waitFor { Bets.decode(runBlocking { container.db.dao().setting(Bets.SETTING) }).singleOrNull()?.stake == 25 }
        app.waitFor { runBlocking { container.book.balance() } == 100 + 10 - 25 }
        app.waitFor { app.shownIn("today-list", "bet:${runBlocking { container.db.dao().activeGoals().single().id }}") }
    }
}
