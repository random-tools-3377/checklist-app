package io.github.randomtools3377.checklist

import android.Manifest
import android.app.NotificationManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Ticking a goal reminds of the one that comes after it in a habit chain. */
@RunWith(AndroidJUnit4::class)
class ChainTest {
    class CoffeeThenJournal : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            fun draft(name: String, icon: String, after: Long? = null) = HabitDraft(
                id = 0, name = name, icon = icon, points = 2, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null, after = after,
            )
            c.repository.saveHabit(draft("Coffee", "☕"))
            c.repository.saveHabit(draft("Journal", "📓", after = c.db.dao().allGoals().single().id))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(CoffeeThenJournal())
        .around(app)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun reminded(name: String) = context.getSystemService(NotificationManager::class.java).activeNotifications.any {
        it.notification.extras.getCharSequence("android.title")?.contains(name) == true
    }

    @Test
    fun tickingCoffeeRemindsOfJournal() {
        app.waitFor { app.shownIn("today-list", "tick:Coffee") }
        app.tap("tick:Coffee")
        app.waitFor { reminded("Journal") }
    }
}
