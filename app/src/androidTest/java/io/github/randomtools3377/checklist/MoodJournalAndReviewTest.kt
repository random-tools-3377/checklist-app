package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.MoodInsights
import io.github.randomtools3377.checklist.core.MoodReminder
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.LOCK_REASONS
import io.github.randomtools3377.checklist.data.SETTING_MOOD_MORE_CLOSED
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.data.toEntity
import io.github.randomtools3377.checklist.data.toEpochDay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The review sums the moods up, with the notes a tap away in the journal; the journal can be
 * narrowed and searched, a day deleted and a missed one added; "Mood and your goals" raises a
 * goal's points once, with Undo, and can mark what comes only from days something was noted; and
 * the mood reminder is off until a time is picked.
 */
@RunWith(AndroidJUnit4::class)
class MoodJournalAndReviewTest {
    /**
     * A goal done on the three low days (4 to 6 days ago, "Work" noted) and not on the three
     * better ones (1 to 3 days ago, "Outdoors" noted), and today's mood already in.
     */
    class AWeekOfMoodsAndNotes : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.setSetting(Feature.MOOD.key, Feature.MOOD.onValue)
            val today = c.book.today()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Tidy", icon = "⭐", points = 5, repeat = Repeat.everyDay(today.minusDays(10)), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            val tidy = c.db.dao().allGoals().single()
            val dayMillis = 24L * 60 * 60 * 1000
            for (ago in 1..6) {
                val day = today.minusDays(ago)
                val at = nowMillis() - ago * dayMillis
                val low = ago >= 4
                val note = when (ago) {
                    1 -> "Sunny walk"
                    4 -> "Long meeting"
                    else -> null
                }
                c.db.dao().putMood(Mood(day, if (low) 2 else 4, note = note, at = at, factors = listOf(if (low) "Work" else "Outdoors")).toEntity())
                if (low) c.db.dao().insertTicks(listOf(Tick(goalId = tidy.id, stepId = null, at = at, day = day, period = Period.DAILY, periodStart = day).toEntity()))
            }
            c.db.dao().putMood(Mood(today, 3, note = "Today note", at = nowMillis(), factors = listOf("Outdoors")).toEntity())
            // The notes under today's mood were closed, so Today shows its one line.
            c.repository.setSetting(SETTING_MOOD_MORE_CLOSED, today.toString())
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AWeekOfMoodsAndNotes()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private val dao get() = container.db.dao()
    private val today get() = runBlocking { container.book.today() }

    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    private fun todaysMood() = runBlocking { dao.mood(today.toEpochDay()) }

    private fun points() = runBlocking { dao.allGoals() }.single().points

    private fun type(tag: String, text: String) {
        val node = app.onNodeWithTag(tag)
        try {
            node.performScrollTo()
        } catch (e: Throwable) {
            // Not inside anything that scrolls.
        }
        node.performTextInput(text)
    }

    private fun openWeekReview() {
        app.waitFor { shown("nav:review") }
        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { shown("review:Week") }
        app.tap("review:Week")
    }

    @Test
    fun theReviewSumsMoodsUpAndTheJournalHoldsTheNotes() {
        openWeekReview()
        // A summary, not the list of days.
        app.waitFor { app.shownIn("review", "review-mood-summary") }
        assert(text("review-mood-average").contains("Average mood"))
        assert(shown("review-mood-compare"))
        assert(shown("review-mood-week"))
        assert(!shown("review-moods")) { "the day-by-day list is only in the Day view" }

        // "See the notes" opens the journal on the week's days.
        app.tap("review-mood-notes")
        app.waitFor { shown("mood-journal") && shown("journal-stretch") }
        app.waitFor { shown("journal-day:$today") }
        app.tap("journal-all-days")
        app.waitFor { !shown("journal-stretch") }

        // Search the notes.
        type("journal-search", "meeting")
        app.waitFor { shown("journal-day:${today.minusDays(4)}") && !shown("journal-day:$today") }
        app.tap("journal-search-clear")
        app.waitFor { shown("journal-day:$today") }
        // Only the days with one chip, and back.
        app.tap("journal-filter:Work")
        app.waitFor { shown("journal-day:${today.minusDays(4)}") && !shown("journal-day:$today") }
        app.tap("journal-filter:Work")
        app.waitFor { shown("journal-day:$today") }

        // Delete a day: it asks first.
        app.tap("journal-day:$today")
        app.waitFor { shown("journal-dialog") && shown("journal-delete") }
        app.tap("journal-delete")
        app.waitFor { shown("journal-delete-yes") }
        assert(todaysMood() != null) { "nothing is deleted before the answer" }
        app.tap("journal-delete-yes")
        app.waitFor { todaysMood() == null }

        // Add a day: the calendar opens on today, then the day is filled in.
        app.waitFor { shown("journal-add-day") }
        app.tap("journal-add-day")
        app.waitFor { shown("journal-add-day-ok") }
        app.tap("journal-add-day-ok")
        app.waitFor { shown("journal-dialog") && shown("journal-mood:5") }
        app.tap("journal-mood:5")
        type("journal-note", "Filled in later")
        app.tap("journal-save")
        app.waitFor { todaysMood()?.score == 5 && todaysMood()?.note == "Filled in later" }
        // A later mood can be added to a day that has none.
        app.waitFor { shown("journal-day:$today") }
        app.tap("journal-day:$today")
        app.waitFor { shown("journal-after-add") }
        app.tap("journal-after-add")
        app.waitFor { shown("journal-after:4") }
        app.tap("journal-after:4")
        app.tap("journal-save")
        app.waitFor { todaysMood()?.afterScore == 4 }
    }

    @Test
    fun todaysMoodOpensInFullWithTheJournalATapAway() {
        app.waitFor { shown("mood-today") }
        app.tap("mood-today")
        app.waitFor { shown("journal-dialog") && shown("mood-day-journal") }
        // Change the face and take a chip off.
        app.tap("journal-mood:4")
        app.tap("journal-factor:Outdoors")
        app.tap("journal-save")
        app.waitFor { todaysMood()?.score == 4 && todaysMood()?.factors == null }
        assert(todaysMood()?.note == "Today note")
        // The journal, from the same box.
        app.waitFor { shown("mood-today") }
        app.tap("mood-today")
        app.waitFor { shown("mood-day-journal") }
        app.tap("mood-day-journal")
        app.waitFor { shown("mood-journal") }
    }

    @Test
    fun aRaiseFromMoodAndYourGoalsIsOfferedOnceWithUndo() {
        openWeekReview()
        app.waitFor { app.shownIn("review", "review-mood-goals") }
        app.tap("review-mood-goals")
        app.waitFor { shown("mood-goals") }
        app.waitFor { app.shownIn("mood-goals", "way-pay:Tidy") }
        val more = MoodInsights.payMore(5)
        app.tap("way-pay:Tidy")
        app.waitFor { points() == more }
        // Now it says what it pays, with Undo, and offers no further raise.
        app.waitFor { app.shownIn("mood-goals", "way-paid:Tidy") && !shown("way-pay:Tidy") }
        assert(text("way-paid-line:Tidy").contains("Now pays $more"))
        app.tap("way-pay-undo:Tidy")
        app.waitFor { points() == 5 }
        app.waitFor { app.shownIn("mood-goals", "way-pay:Tidy") }

        // The highlight marks what is compared only among days something was noted on.
        app.waitFor { app.shownIn("mood-goals", "mood-highlight") }
        assert(!shown("mood-highlight-days"))
        app.tap("mood-highlight")
        app.waitFor { shown("mood-highlight-days") }
        assert(text("mood-highlight-days").contains("6 of 6 days"))
        app.waitFor { app.shownIn("mood-goals", "mood-factor-marked:Outdoors") }
    }

    @Test
    fun lockedMoodsShowALockedCardInTheReview() {
        runBlocking { dao.putSetting(SettingEntity(LOCK_REASONS, "1")) }
        openWeekReview()
        app.waitFor { app.shownIn("review", "review-moods-locked") }
        assert(shown("review-mood-unlock"))
        assert(!shown("review-mood-summary"))
    }

    @Test
    fun theMoodReminderIsOffUntilATimeIsPicked() {
        fun saved() = runBlocking { dao.setting(MoodReminder.SETTING) }
        app.waitFor { shown("settings") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("setting:mood-reminder") }
        assert(text("mood-reminder-time") == "Off")
        app.tap("setting:mood-reminder")
        app.waitFor { shown("setting:mood-reminder:ok") }
        app.tap("setting:mood-reminder:ok")
        app.waitFor { saved() == MoodReminder.encode(LocalTime(9, 0)) }
        app.waitFor { shown("setting:mood-reminder:off") }
        assert(text("mood-reminder-hint").contains("never shows a mood or a note"))
        app.tap("setting:mood-reminder:off")
        app.waitFor { saved() == "" }
    }
}
