package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Bosses
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Items and bosses: a tick earns an item, and using it on the week's boss does damage. */
@RunWith(AndroidJUnit4::class)
class BossTest {
    class ARunTicked : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.db.dao().putSetting(SettingEntity(Feature.BOSSES.key, Feature.BOSSES.onValue))
            c.db.dao().putSetting(SettingEntity(Bosses.ITEMS_FROM, "0"))
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Morning run", icon = "🏃", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            c.book.tickHabit(c.db.dao().activeGoals().single().id)
            Unit
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ARunTicked()).around(app)

    private val dao get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container.db.dao()

    private fun text(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun aTicksItemHitsTheBoss() {
        app.waitFor { app.shownIn("today-list", "boss") }
        app.waitFor { app.shownIn("today-list", "boss-attack") }
        app.tap("boss-attack")
        app.waitFor { text("boss-said").contains("damage") }
        app.waitFor { Bosses.damageIn(runBlocking { dao.setting(Bosses.FIGHT) }, Bosses.monday(nowLocal().date)) >= Bosses.HIT }
    }
}
