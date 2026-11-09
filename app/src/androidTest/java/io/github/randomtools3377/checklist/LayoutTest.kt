package io.github.randomtools3377.checklist

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_BACKDROP
import io.github.randomtools3377.checklist.data.SETTING_CARD_SIZE
import io.github.randomtools3377.checklist.data.SETTING_TEXT_SIZE
import io.github.randomtools3377.checklist.data.SETTING_TODAY_LAYOUT
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** Today as a grid of tiles, the sizes and the background behind Today. */
@RunWith(AndroidJUnit4::class)
class LayoutTest {
    class TwoGoalsInAGrid : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.backgroundPhoto.delete()
            listOf(Triple("Read", 4, 1), Triple("Water", 1, 3)).forEach { (name, points, target) ->
                container.repository.saveHabit(
                    HabitDraft(
                        id = 0, name = name, icon = "⭐", points = points, repeat = Repeat.everyDay(nowLocal().date), target = target, bonus = 0,
                        minutes = null, at = null, finishBy = null, finishDay = null,
                    ),
                )
            }
            container.repository.setSetting(SETTING_TODAY_LAYOUT, "GRID")
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(TwoGoalsInAGrid()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    private fun gone(tag: String) = app.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    private fun setting(name: String): String? = runBlocking { container.db.dao().setting(name) }

    private fun description(tag: String): String = app.onAllNodesWithTag(tag).fetchSemanticsNodes()
        .joinToString(" ") { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ") }

    @Test
    fun aTileTicksWithATapAndOpensItsCardWhenHeld() {
        app.waitFor { app.shownIn("today-list", "tile:Read") }
        app.tap("tile:Read")
        app.waitFor { description("tile:Read").contains("Done") }
        app.waitFor { runBlocking { container.book.balance() } == 4 }

        app.waitFor { app.shownIn("today-list", "tile:Water") }
        app.tap("tile:Water")
        app.waitFor { description("tile:Water").contains("1 of 3") }

        app.onNodeWithTag("tile:Water").performSemanticsAction(SemanticsActions.OnLongClick)
        app.waitFor { !gone("close-details") }
        app.onNodeWithTag("tick:Water").performClick()
        app.waitFor { description("tile:Water").contains("2 of 3") }
        app.onNodeWithTag("close-details").performClick()
        app.waitFor { gone("close-details") }
    }

    @Test
    fun theSizesAndBackgroundAreChosenInAppearance() {
        app.onNodeWithTag("settings").performClick()
        app.waitFor { !gone("setting:appearance") }
        app.tap("setting:appearance")
        app.waitFor { !gone("layout:List") }
        app.tap("card-size:Large")
        app.waitFor { setting(SETTING_CARD_SIZE) == "LARGE" }
        app.tap("text-size:Large")
        app.waitFor { setting(SETTING_TEXT_SIZE) == "LARGE" }
        app.tap("backdrop:Gradient")
        app.waitFor { setting(SETTING_BACKDROP) == "GRADIENT" }
        app.tap("layout:List")
        app.waitFor { setting(SETTING_TODAY_LAYOUT) == "LIST" }
    }

    @Test
    fun aPickedPhotoIsMadeSmallerAndGoesInTheBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val big = File(context.cacheDir, "big-photo.jpg")
        Bitmap.createBitmap(3200, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2F80C0.toInt()) }
            .let { bitmap -> big.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }

        assertTrue(container.backgroundPhoto.add(context, Uri.fromFile(big)))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(container.backgroundPhoto.file.path, bounds)
        assertEquals(1600, maxOf(bounds.outWidth, bounds.outHeight))

        val backup = Backup.decode(container.repository.exportBackup())
        assertNotNull(backup.background)
        container.repository.restore(backup.copy(background = null))
        assertFalse(container.backgroundPhoto.exists())
        container.repository.restore(backup)
        assertTrue(container.backgroundPhoto.exists())
    }
}
