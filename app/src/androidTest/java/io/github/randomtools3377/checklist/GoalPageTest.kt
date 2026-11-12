package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toEntity
import io.github.randomtools3377.checklist.ui.longDate
import io.github.randomtools3377.checklist.ui.shortDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.atStartOfDayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * A goal's own page, opened from the Goals list, from "History" on Today and from the review;
 * and a day opened from the review's month calendar, with the date kept from view to view.
 */
@RunWith(AndroidJUnit4::class)
class GoalPageTest {
    /** Before the app starts: a daily habit added five days ago, done yesterday and today, missed the day before with a reason. */
    class AGoalWithSomeHistory : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            val dao = container.db.dao()
            val today = nowLocal().date
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(today.minusDays(5)), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            val added = today.minusDays(5).atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds()
            dao.observeGoals().first().forEach { dao.updateGoal(it.copy(createdAt = added)) }
            val id = dao.activeGoals().single().id
            val yesterday = today.minusDays(1)
            dao.insertTick(Tick(goalId = id, stepId = null, at = 1, day = yesterday, period = Period.DAILY, periodStart = yesterday, note = "two chapters").toEntity())
            val missed = today.minusDays(2)
            dao.insertLapse(
                Lapse(goalId = id, kind = LapseKind.MISS, day = missed, period = Period.DAILY, periodStart = missed, at = 2, overLimit = false, reason = Reason.NO_TIME, label = "Read").toEntity(),
            )
            container.book.tickHabit(id)
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AGoalWithSomeHistory()).around(app)

    private val today get() = nowLocal().date

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    private fun back() {
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.waitForIdle()
    }

    @Test
    fun aGoalOpensItsPageFromTheGoalsListAndEditIsOneStepOn() {
        app.waitFor { !gone("nav:goals") }
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal:Read") }
        app.tap("goal:Read")

        // Its page: the calendar with today marked done, and its numbers.
        app.waitFor { !gone("goal-page") }
        app.waitFor { textOf("goal-day-mark:${today.dayOfMonth}") == "✓" }
        app.waitFor { app.shownIn("goal-page", "goal-page:ticks") && textOf("goal-page:ticks") == "2" }
        // Two done in a row, after the miss; and of the days since it was added, two were done.
        assertEquals("2 times in a row", textOf("goal-page:run"))
        assertEquals("+4", textOf("goal-page:points"))
        assertTrue(textOf("goal-page:30").startsWith("2 of 6"))
        // The miss with its reason, and the note on a tick.
        app.waitFor { app.shownIn("goal-page", "goal-page-lapse:${today.minusDays(2)}") }
        app.waitFor { app.shownIn("goal-page", "goal-page-note:${today.minusDays(1)}") }

        // Edit opens the editor, and leaving it lands on the list again.
        app.tap("goal-page-edit")
        app.waitFor { !gone("field:name") }
        app.onNodeWithTag("editor-back").performClick()
        app.waitFor { gone("field:name") && !gone("goal:Read") }
    }

    @Test
    fun historyOnItsTodayCardAndItsRowInTheReviewOpenThePageToo() {
        app.waitFor { app.shownIn("today-list", "history:Read") }
        app.tap("history:Read")
        app.waitFor { !gone("goal-page") && !gone("goal-page-edit") }
        back()
        app.waitFor { gone("goal-page") && !gone("nav:review") }

        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { app.shownIn("review", "review-goal:Read") }
        app.tap("review-goal:Read")
        app.waitFor { !gone("goal-page") }
    }

    @Test
    fun aDayOnTheMonthCalendarOpensThatDayAndTheDateIsKept() {
        // Yesterday, unless that was last month.
        val day = if (today.dayOfMonth > 1) today.minusDays(1) else today
        app.waitFor { !gone("nav:review") }
        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { !gone("review:Month") }
        app.tap("review:Month")
        app.waitFor { app.shownIn("review", "review-day:${day.dayOfMonth}") }
        app.tap("review-day:${day.dayOfMonth}")

        // The Day view, on that date.
        app.waitFor { gone("review-day:${day.dayOfMonth}") && textOf("review:range") == longDate(day) }

        // Week keeps the date being looked at: the week that day is in.
        // The view chips are at the top of the list, which was scrolled down to the calendar.
        app.waitFor { app.shownIn("review", "review:Week") }
        app.tap("review:Week")
        val monday = Days.periodStart(day, Period.WEEKLY)
        val sunday = Days.periodEnd(day, Period.WEEKLY)
        app.waitFor { textOf("review:range") == "${shortDate(monday)} to ${shortDate(sunday)}" }
        // And Day again is still that day.
        app.waitFor { app.shownIn("review", "review:Day") }
        app.tap("review:Day")
        app.waitFor { textOf("review:range") == longDate(day) }
    }
}
