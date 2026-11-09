package io.github.randomtools3377.checklist

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** With goals and no backup for over a week, Today reminds; "Later" puts it off. */
@RunWith(AndroidJUnit4::class)
class BackupReminderTest {
    class NoBackupForAWeek : ExternalResource() {
        override fun before() = runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val container = (context.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            context.getSharedPreferences("backups", Context.MODE_PRIVATE).edit().clear()
                .putLong("first_seen", System.currentTimeMillis() - 8 * 86_400_000L)
                .commit()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 2, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }

        override fun after() {
            InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("backups", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(NoBackupForAWeek()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    @Test
    fun todayRemindsUntilLater() {
        app.waitFor { !gone("backup-reminder") }
        app.tap("backup-later")
        app.waitFor { gone("backup-reminder") }
    }
}
