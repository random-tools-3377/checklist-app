package io.github.randomtools3377.checklist

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.BackupCrypto
import io.github.randomtools3377.checklist.data.BackupKey
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.SETTING_BACKUP_KEY
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Settings → Backup: "Erase everything and start fresh" asks twice and then leaves nothing, and
 * "Protect backup files with a passphrase" warns, takes a passphrase typed twice, and from then
 * on the phone's own crypto seals a backup that only the passphrase opens.
 */
@RunWith(AndroidJUnit4::class)
class BackupSafetyTest {
    class OneHabit : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(OneHabit()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun goals() = runBlocking { container.db.dao().allGoals().size }
    private fun key() = runBlocking { BackupKey.decode(container.db.dao().setting(SETTING_BACKUP_KEY)) }

    /** Opens Settings with only the parts about [words] showing, so the Backup section is in reach. */
    private fun settingsAbout(words: String) {
        app.waitFor { app.shownIn("today-list", "tick:Read") }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { shown("settings-search") }
        app.onNodeWithTag("settings-search").performTextClearance()
        app.onNodeWithTag("settings-search").performTextInput(words)
    }

    @Test
    fun eraseEverythingAsksTwiceThenLeavesNothing() {
        settingsAbout("erase")
        app.waitFor { shown("erase") }

        // The first question says what goes and offers a copy; the second can still be backed out of.
        app.tap("erase")
        app.waitFor { shown("erase-what") && shown("erase-export-first") }
        app.onNodeWithTag("erase-continue").performClick()
        app.waitFor { shown("erase-keep") }
        app.onNodeWithTag("erase-keep").performClick()
        app.waitFor { !shown("erase-keep") }
        assertEquals(1, goals())

        app.tap("erase")
        app.waitFor { shown("erase-continue") }
        app.onNodeWithTag("erase-continue").performClick()
        app.waitFor { shown("erase-confirm") }
        app.onNodeWithTag("erase-confirm").performClick()
        app.waitFor { goals() == 0 }
        assertTrue(runBlocking { container.repository.summary().isEmpty })
    }

    @Test
    fun aPassphraseIsTypedTwiceAndThenProtectsBackupFiles() {
        settingsAbout("passphrase")
        // Privacy says plainly what a backup file holds and who can read it.
        app.waitFor { shown("setting:backup-passphrase") && shown("privacy-backup-files") }

        app.tap("setting:backup-passphrase")
        app.waitFor { shown("passphrase-warning") }
        // Too short, then not the same twice: it can't be turned on yet.
        app.onNodeWithTag("passphrase").performTextInput("short")
        app.onNodeWithTag("passphrase-again").performTextInput("short")
        app.onNodeWithTag("passphrase-set").assertIsNotEnabled()
        app.onNodeWithTag("passphrase").performTextClearance()
        app.onNodeWithTag("passphrase").performTextInput("correct horse battery")
        app.onNodeWithTag("passphrase-set").assertIsNotEnabled()
        app.onNodeWithTag("passphrase-again").performTextClearance()
        app.onNodeWithTag("passphrase-again").performTextInput("correct horse battery")
        app.onNodeWithTag("passphrase-set").performClick()

        // Making the key is slow on purpose.
        app.waitFor(60_000) { key() != null }
        app.waitFor { shown("backup-passphrase-change") }

        // With the key the phone kept, its own crypto seals a backup only the passphrase opens.
        val plain = runBlocking { container.repository.exportBackup() }
        val file = BackupCrypto.seal(plain, key()!!)
        assertTrue(BackupCrypto.isEncrypted(file))
        assertTrue("Read" !in file)
        assertEquals(listOf("Read"), Backup.decode(BackupCrypto.open(file, "correct horse battery")).goals.map { it.name })
        assertTrue(runCatching { BackupCrypto.open(file, "correct horse batterx") }.isFailure)
        // The key stays on the phone: it is not in the backup.
        assertTrue(SETTING_BACKUP_KEY !in Backup.decode(plain).settings)

        // Turning it off asks first.
        app.tap("setting:backup-passphrase")
        app.waitFor { shown("backup-passphrase-off") }
        app.onNodeWithTag("backup-passphrase-off").performClick()
        app.waitFor { key() == null }
    }
}
