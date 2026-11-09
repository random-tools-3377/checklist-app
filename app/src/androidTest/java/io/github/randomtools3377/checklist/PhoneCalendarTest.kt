package io.github.randomtools3377.checklist

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.randomtools3377.checklist.calendar.PhoneCalendar
import io.github.randomtools3377.checklist.data.SETTING_CALENDAR
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** With the calendar turned on and allowed, the app reads it and the planner opens without trouble. */
@RunWith(AndroidJUnit4::class)
class PhoneCalendarTest {
    class CalendarOn : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.db.dao().putSetting(SettingEntity(SETTING_CALENDAR, "1"))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR))
        .around(CalendarOn())
        .around(app)

    @Test
    fun readingTheCalendarWorks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(true, PhoneCalendar.allowed(context))
        // An emulator may have no calendars at all; reading must still work and give a list.
        val today = nowLocal().date
        PhoneCalendar.events(context, emptySet(), today, 14).forEach { assertEquals(true, it.end >= it.start) }
        app.onNodeWithTag("nav:plan").performClick()
        app.waitFor { app.onAllNodesWithTag("plan").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("plan-day:1").performClick()
    }
}
