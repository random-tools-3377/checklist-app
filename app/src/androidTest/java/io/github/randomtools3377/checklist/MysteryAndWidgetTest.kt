package io.github.randomtools3377.checklist

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.randomtools3377.checklist.core.Checkins
import io.github.randomtools3377.checklist.core.Expression
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Looks
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.ui.headImage
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.widget.StatsWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A mystery reward draws one of its prizes when bought; the balance and streaks widget adds up today. */
@RunWith(AndroidJUnit4::class)
class MysteryAndWidgetTest {
    class PointsAndAMysteryBox : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Run", icon = "🏃", points = 30, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            val run = container.db.dao().allGoals().first { it.name == "Run" }
            container.book.tickHabit(run.id)
            container.repository.saveReward(RewardDraft(id = 0, name = "Mystery box", icon = "🎁", price = 10, prizes = listOf("Coffee out", "Film night")))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(PointsAndAMysteryBox()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    // The whole tree: a label inside a card that can be tapped is merged into the card otherwise.
    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun buyingTheMysteryBoxDrawsOneOfItsPrizes() {
        app.onNodeWithTag("nav:shop").performClick()
        app.waitFor { !gone("mystery:Mystery box") }
        app.tap("buy:Mystery box")
        app.onNodeWithTag("confirm-buy").performClick()
        // The reveal closes by itself, and the test clock can run past it, so the purchase is what's checked.
        app.waitFor { runBlocking { container.db.dao().allPurchases().size } == 1 }
        val purchase = runBlocking { container.db.dao().allPurchases().single().toCore() }
        assertEquals(true, purchase.name in listOf("Coffee out", "Film night"))
        assertEquals(10, purchase.price)
        assertEquals(20, runBlocking { container.book.balance() })
    }

    @Test
    fun aMysteryRewardIsMadeInTheEditor() {
        app.onNodeWithTag("nav:shop").performClick()
        app.onNodeWithTag("add-reward").performClick()
        app.onNodeWithTag("field:name").performTextInput("Surprise")
        app.onNodeWithTag("field:price").performTextInput("5")
        app.tap("field:mystery")
        app.waitFor { !gone("field:prize-1") }
        app.onNodeWithTag("field:prize-0").performTextInput("Bath")
        // Only one written: it needs at least two.
        app.tap("save")
        app.waitFor { !gone("field:prize-1") }
        app.onNodeWithTag("field:prize-1").performTextInput("Walk in the park")
        app.tap("save")
        app.waitFor { !gone("mystery:Surprise") }
        val surprise = runBlocking { container.db.dao().allRewards().first { it.name == "Surprise" }.toCore() }
        assertEquals(listOf("Bath", "Walk in the park"), surprise.prizes)
    }

    @Test
    fun theWidgetAddsUpTheBalanceAndToday() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = runBlocking { StatsWidget.load(context) }
        assertEquals(30, data.balance)
        assertEquals(2, data.due)
        assertEquals(1, data.done)
        // The character's face is off to start.
        assertEquals(null, data.face)
    }

    @Test
    fun theWidgetShowsTheCharactersFaceOnceItsSwitchIsOn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = container.db.dao()
        // The switch alone does nothing while "Character and skills" is off.
        runBlocking { dao.putSetting(SettingEntity(Checkins.WIDGET_FACE, "1")) }
        assertEquals(null, runBlocking { StatsWidget.load(context) }.face)

        // With the character on: 1 of 2 done today, so it looks happy.
        runBlocking { dao.putSetting(SettingEntity(Feature.CHARACTER.key, Feature.CHARACTER.onValue)) }
        val face = runBlocking { StatsWidget.load(context) }.face!!
        assertEquals(Expression.HAPPY, face.expression)

        // Its head is drawn as a picture: clear in the corners, the skin colour on the cheek.
        val bitmap = headImage(face.look, face.outfit, face.expression, 152).asAndroidBitmap()
        assertEquals(152, bitmap.width)
        assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 151)))
        val cheek = bitmap.getPixel(36, 104)
        val skin = Looks.SKINS[face.look.skin]
        assertEquals(255, android.graphics.Color.alpha(cheek))
        assertEquals(android.graphics.Color.red(skin).toFloat(), android.graphics.Color.red(cheek).toFloat(), 4f)
        assertEquals(android.graphics.Color.blue(skin).toFloat(), android.graphics.Color.blue(cheek).toFloat(), 4f)
    }
}
