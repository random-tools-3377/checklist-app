package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.StepDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A project's deadline reminder on the computer: shown in its minute, like a goal's reminder. */
class DesktopDeadlineTest {
    private val folder: File = Files.createTempDirectory("checklist-desktop-deadline-test").toFile()
    private val phone = DesktopPhone(appVersion = "0.0.0", folder = folder)
    private val dao = phone.parts.db.dao()

    @AfterTest
    fun close() {
        phone.parts.db.close()
        folder.deleteRecursively()
    }

    @Test
    fun theDeadlineReminderShowsTheDayBeforeUntilTheProjectIsFinished() = runTest {
        // Two days ahead, so its day-before reminder is tomorrow whatever the time is now.
        val deadline = nowLocal().date.plus(DatePeriod(days = 2))
        val dayBefore = nowLocal().date.plus(DatePeriod(days = 1))
        phone.parts.repository.saveProject(
            ProjectDraft(
                id = 0, name = "Paint the shed", icon = "🎨", steps = listOf(StepDraft(0, "Sand it", 2), StepDraft(0, "Paint it", 2)), bonus = 5,
                deadline = deadline, finishBy = null, private = true, reminder = Reminder(at = LocalTime(9, 0)),
            ),
        )
        val from = LocalDateTime(dayBefore, LocalTime(9, 0))
        val to = LocalDateTime(dayBefore, LocalTime(9, 1))
        val due = DesktopReminders.due(phone.parts, from, to).single()
        assertEquals("🎨 Paint the shed", due.title)
        assertEquals("Due tomorrow, 2 steps left", due.text)
        assertTrue(DesktopReminders.due(phone.parts, to, LocalDateTime(dayBefore, LocalTime(9, 2))).isEmpty())

        // Behind the lock it isn't named.
        phone.parts.repository.setSetting(LOCK_PRIVATE, "1")
        assertEquals("🔒 A private goal" to "Due tomorrow", DesktopReminders.due(phone.parts, from, to).single().let { it.title to it.text })

        // Finished: nothing comes.
        val project = dao.activeGoals().single().id
        dao.activeSteps(project).forEach { assertTrue(phone.parts.book.tickStep(it.id) is TickResult.Ticked) }
        assertTrue(DesktopReminders.due(phone.parts, from, to).isEmpty())
    }
}
