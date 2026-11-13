package io.github.randomtools3377.checklist

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.EveningCheck
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_TICK_ANSWER
import io.github.randomtools3377.checklist.data.eveningList
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toEntity
import io.github.randomtools3377.checklist.reminders.EdgeLights
import io.github.randomtools3377.checklist.reminders.Reminders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** "Evening check" in Settings is off until a time is picked, and the clock says it must be before the day ends. */
@RunWith(AndroidJUnit4::class)
class EveningCheckSettingTest {
    class NothingYet : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(NothingYet()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    private fun saved() = runBlocking { dao.setting(EveningCheck.SETTING) }

    @Test
    fun itIsOffUntilATimeIsPickedAndTheClockSaysWhen() {
        app.waitFor { !gone("settings") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:evening-check") }
        assertEquals("Off", textOf("evening-check-time"))
        assertTrue(gone("setting:evening-check:off"))

        // The clock opens at 9 pm and says the time must be before the day ends.
        app.tap("setting:evening-check")
        app.waitFor { !gone("evening-check-rule") }
        assertTrue(textOf("evening-check-rule").contains("before your day ends at midnight"))
        assertTrue(gone("evening-check-not-allowed"))
        app.tap("setting:evening-check:ok")
        app.waitFor { saved() == EveningCheck.encode(LocalTime(21, 0)) }

        // On now, with a way to turn it off again.
        app.waitFor { !gone("setting:evening-check:off") }
        assertTrue(textOf("evening-check-hint").contains("still open today"))
        app.tap("setting:evening-check:off")
        app.waitFor { saved() == "" }
        app.waitFor { textOf("evening-check-time") == "Off" }
    }
}

/**
 * The evening check's notification: one, listing what is still open today, with "Open Today" and
 * a button for the first open goal. It keeps up with what gets done and goes once nothing is open.
 */
@RunWith(AndroidJUnit4::class)
class EveningCheckNotificationTest {
    @get:Rule
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as ChecklistApplication
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val dao get() = app.container.db.dao()

    private fun forgetLastAlarm() =
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()

    private fun evening() = manager.activeNotifications.firstOrNull { it.id == Reminders.EVENING_ID }?.notification

    private fun title() = evening()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun body() = evening()?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

    /** Posting and taking away a notification reach the system a moment later, so look for a few seconds. */
    private fun within(millis: Long = 5_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    private suspend fun addHabit(name: String, icon: String, start: LocalDate, private: Boolean = false): Long {
        app.container.repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = icon, points = 5, repeat = Repeat.everyDay(start), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null, private = private,
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    @Test
    fun itListsWhatIsOpenKeepsUpAndGoesOnceEverythingIsDone() = runBlocking {
        app.container.db.clearAllTables()
        manager.cancelAll()
        val day = app.container.book.today()
        val stretch = addHabit("Stretch", "🧘", day.minusDays(2))
        val read = addHabit("Read", "📖", day.minusDays(2))
        val journal = addHabit("Journal", "📓", day.minusDays(2), private = true)
        // The lock covers private goals: a notification can't be unlocked, so the journal isn't named.
        app.container.repository.setSetting(LOCK_PRIVATE, "1")

        Reminders.showEvening(context, eveningList(dao, day)!!, dayEnd = 0)
        assertTrue("The evening check should show", within { evening() != null })
        assertEquals("3 still open today", title())
        // What is at stake comes first: at the usual 90%, all three are needed for today to count.
        assertEquals("3 more and today counts for your streak\n🧘 Stretch\n📖 Read\n🔒 A private goal", body())
        assertFalse(body()!!.contains("Journal"))
        assertEquals(listOf("Open Today", "Done: 🧘 Stretch"), evening()!!.actions.map { it.title.toString() })

        // One done, in the app or anywhere: the list is brought up to date.
        app.container.book.tickHabit(stretch)
        Reminders.refreshEvening(context)
        assertTrue("The list should lose what is done", within { title() == "2 still open today" })
        assertEquals("2 more and today counts for your streak\n📖 Read\n🔒 A private goal", body())
        assertEquals(listOf("Open Today", "Done: 📖 Read"), evening()!!.actions.map { it.title.toString() })

        // Only the private goal left: no button names or ticks it.
        app.container.book.tickHabit(read)
        Reminders.refreshEvening(context)
        assertTrue(within { title() == "1 still open today" })
        assertEquals(listOf("Open Today"), evening()!!.actions.map { it.title.toString() })

        // Everything done: it goes.
        app.container.book.tickHabit(journal)
        Reminders.refreshEvening(context)
        assertTrue("With everything done it should go", within { evening() == null })
    }

    @Test
    fun atItsTimeTheAlarmShowsItAndNothingOnADoneDay() = runBlocking {
        app.container.db.clearAllTables()
        manager.cancelAll()
        // Not right at the end of a minute, so the alarm below still falls in this one.
        if (nowLocal().second >= 50) Thread.sleep(11_000)
        val now = nowLocal()
        // The check comes from noon until the day ends. In the morning, a day that ends after
        // this hour makes now a late night, which is before the day ends.
        val dayEnd = if (now.hour >= EveningCheck.FROM_HOUR) 0 else now.hour + 1
        assumeTrue("At 11 am no end of the day makes this minute an evening", dayEnd <= Days.MAX_DAY_END_HOUR)
        app.container.repository.setDayEndHour(dayEnd)
        app.container.repository.setSetting(EveningCheck.SETTING, EveningCheck.encode(LocalTime(now.hour, now.minute)))
        val stretch = addHabit("Stretch", "🧘", now.date.minusDays(3))

        forgetLastAlarm()
        Reminders.fire(context)
        assertTrue("The evening check should show at its time", within { evening() != null })
        assertEquals("1 still open today", title())

        // Done, and the time comes round again: nothing is sent.
        app.container.book.tickHabit(stretch)
        manager.cancelAll()
        forgetLastAlarm()
        Reminders.fire(context)
        assertFalse("Nothing should come on a day when everything is done", within(1_500) { evening() != null })
    }

    /** The short note after a tick made on a reminder or a widget (its notification id is EdgeLights' own). */
    private fun answer() = manager.activeNotifications.firstOrNull { it.id == 940 }?.notification

    @Test
    fun aTickMadeOutsideTheAppAnswersBackUnlessSwitchedOff() = runBlocking {
        app.container.db.clearAllTables()
        manager.cancelAll()
        val day = app.container.book.today()
        val stretch = addHabit("Stretch", "🧘", day.minusDays(2))
        val read = addHabit("Read", "📖", day.minusDays(2))

        // As "Done" on a reminder and a tick on a widget do: tick, then answer.
        EdgeLights.celebrate(context, stretch, app.container.book.tickHabit(stretch))
        assertTrue("The answer should show", within { answer() != null })
        assertEquals("Done: 🧘 Stretch, +5", answer()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("1 left today", answer()?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString())

        // Switched off in Settings → Notifications: nothing answers.
        manager.cancelAll()
        app.container.repository.setSetting(SETTING_TICK_ANSWER, "0")
        EdgeLights.celebrate(context, read, app.container.book.tickHabit(read))
        assertFalse("Nothing should answer once it is switched off", within(1_500) { answer() != null })
    }
}

/**
 * The review's stats show the best main streak beside the current one, and for a past week the
 * longest streak within that week instead.
 */
@RunWith(AndroidJUnit4::class)
class BestStreaksReviewTest {
    /**
     * A daily habit, added long ago: done ten days in a row up to last week's Wednesday, not on
     * the Thursday, then every day from the Friday to yesterday.
     */
    class TenDaysThenABreak : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            val today = c.book.today()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(today.minusDays(40)), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            // Added forty days ago, so the days before today count for it.
            val added = today.minusDays(40).atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds()
            c.db.dao().observeGoals().first().forEach { c.db.dao().updateGoal(it.copy(createdAt = added)) }
            val read = c.db.dao().activeGoals().single().id
            val monday = Days.periodStart(today, Period.WEEKLY)
            val days = (14 downTo 5).map { monday.minusDays(it) } + generateSequence(monday.minusDays(3)) { it.plusDays(1) }.takeWhile { it < today }
            c.db.dao().insertTicks(
                days.map { day ->
                    val at = day.atStartOfDayIn(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 60 * 60 * 1000L
                    Tick(goalId = read, stepId = null, at = at, day = day, period = Period.DAILY, periodStart = day).toEntity()
                },
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TenDaysThenABreak()).around(app)

    private val today get() = runBlocking {
        (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.book.today()
    }

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun theBestStreakShowsBesideTheCurrentOneAndAPastWeekShowsItsOwn() {
        // From last week's Friday to yesterday: three days, and each day of this week before today.
        val current = 3 + (today.dayOfWeek.isoDayNumber - 1)
        app.waitFor { !gone("nav:review") }
        app.onNodeWithTag("nav:review").performClick()
        app.waitFor { !gone("review:Week") }
        app.waitFor(20_000) { app.shownIn("review", "review-streak:perfect") && textOf("review-streak:main") == "$current days · best 10 days" }
        assertEquals("10 days", textOf("review-streak:perfect"))

        // Last week: Monday to Wednesday, a day not done, then Friday to Sunday.
        app.onNodeWithTag("review").performScrollToIndex(0)
        app.waitFor { !gone("review:back") }
        app.tap("review:back")
        app.waitFor(20_000) { app.shownIn("review", "review-streak:perfect") && textOf("review-streak:main") == "3 days" }
        assertEquals("3 days", textOf("review-streak:perfect"))
        assertEquals("7 days", textOf("review-streak:miss-free"))
    }
}
