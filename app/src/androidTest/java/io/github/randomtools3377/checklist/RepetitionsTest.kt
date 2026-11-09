package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_DAY_START
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Without set times, a habit done twice a day is spread over the day, and Today says when the next one is due. */
@RunWith(AndroidJUnit4::class)
class SpreadOverTheDayTest {
    class TwiceADay : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.db.dao().putSetting(SettingEntity(SETTING_DAY_START, "480")) // 8 am
            container.db.dao().putSetting(SettingEntity(SETTING_DEFAULT_FINISH, "1260")) // 9 pm
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Pills", icon = "💊", points = 2, repeat = Repeat.everyDay(nowLocal().date.minusDays(1)), target = 2, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwiceADay()).around(app)

    @Test
    fun todaySaysWhenTheNextOneIsDue() {
        app.waitFor { app.shownIn("today-list", "next:Pills") }
        app.onNodeWithTag("next:Pills").assertTextContains("Next one by", substring = true)
        app.onNodeWithTag("count:Pills").assertTextEquals("0 of 2")

        // The button says what it does; one tick moves it on.
        app.tap("tick:Pills")
        app.waitFor {
            try {
                app.onNodeWithTag("count:Pills").assertTextEquals("1 of 2")
                true
            } catch (e: AssertionError) {
                false
            }
        }
        app.onNodeWithTag("next:Pills").assertTextContains("Next one by", substring = true)
    }
}

/** In the editor: an emoji from the full set by search, and a time for each one of a habit done twice a day. */
@RunWith(AndroidJUnit4::class)
class TimeForEachOneTest {
    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabase()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun aTimeForEachOneAndAnyEmoji() {
        app.onNodeWithTag("nav:goals").performClick()
        app.onNodeWithTag("add-goal").performClick()
        app.onNodeWithTag("new-habit").performClick()

        // Any emoji, found by name.
        app.tap("icon")
        app.waitFor { !gone("emoji-search") }
        app.onNodeWithTag("emoji-search").performTextInput("rocket")
        app.waitFor { !gone("emoji:rocket") }
        app.tap("emoji:rocket")
        app.waitFor { gone("emoji-picker") }
        app.onNodeWithTag("icon").assertTextEquals("🚀")

        app.onNodeWithTag("field:name").performTextInput("Stretch")
        app.onNodeWithTag("field:points").performTextInput("3")
        // "1" is there to start with.
        app.onNodeWithTag("field:target").performTextClearance()
        app.onNodeWithTag("field:target").performTextInput("2")

        // A time for the first one: the clock opens at 6 pm.
        app.tap("field:each-time")
        app.waitFor { !gone("field:time-1") }
        app.tap("field:time-1")
        app.waitFor { !gone("field:time-1:ok") }
        app.tap("field:time-1:ok")
        app.onNodeWithTag("save").performClick()
        app.waitFor { !gone("goal:Stretch") }

        app.onNodeWithTag("nav:today").performClick()
        app.waitFor { app.shownIn("today-list", "next:Stretch") }
        app.onNodeWithTag("next:Stretch").assertTextContains("the 1st at", substring = true)
    }
}
