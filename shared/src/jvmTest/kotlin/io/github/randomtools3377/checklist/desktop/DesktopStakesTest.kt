package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SettingEntity
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reminders on the computer say what is at stake today in the same words as on a phone. */
class DesktopStakesTest {
    private val folder: File = Files.createTempDirectory("checklist-desktop-stakes-test").toFile()
    private val phone = DesktopPhone(appVersion = "0.0.0", folder = folder)
    private val dao = phone.parts.db.dao()

    @AfterTest
    fun close() {
        phone.parts.db.close()
        folder.deleteRecursively()
    }

    private suspend fun habit(name: String, at: LocalTime, private: Boolean = false, why: String? = null, minutes: Int? = null) = phone.parts.repository.saveHabit(
        HabitDraft(
            id = 0, name = name, icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date.minusDays(1)), target = 1, bonus = 0,
            minutes = minutes, at = at, finishBy = null, finishDay = null, private = private, why = why,
        ),
    )

    private suspend fun at(day: LocalDate, hour: Int) =
        DesktopReminders.due(phone.parts, LocalDateTime(day, LocalTime(hour, 0)), LocalDateTime(day, LocalTime(hour, 1))).map { it.title to it.text }

    @Test
    fun todaysReminderSaysWhatIsAtStakeAndTomorrowsDoesNot() = runTest {
        val today = Days.dayOf(nowLocal(), 0)
        habit("Reading", LocalTime(9, 0))
        // The only goal today: doing it makes today count.
        assertEquals(listOf("📖 Reading" to "Now · This one makes today count for your streak"), at(today, 9))
        // Tomorrow isn't known yet.
        assertEquals(listOf("📖 Reading" to "Now"), at(today.plusDays(1), 9))

        // A second goal: neither is the last one, and one alone doesn't reach 90%.
        habit("Journal", LocalTime(10, 0), private = true)
        assertEquals(listOf("📖 Reading" to "Now"), at(today, 9))
        // A locked private goal keeps its plain words.
        dao.putSetting(SettingEntity(LOCK_PRIVATE, "1"))
        assertEquals(listOf("🔒 A private goal" to "Due now"), at(today, 10).filter { "late" !in it.first })
    }

    @Test
    fun aReminderLeadsWithTheGoalsOwnReasonAndNeverItsPoints() = runTest {
        val tomorrow = Days.dayOf(nowLocal(), 0).plusDays(1)
        habit("Reading", LocalTime(9, 0), why = "so I fall asleep easier", minutes = 20)
        assertEquals(listOf("📖 Reading" to "so I fall asleep easier · Now · 20 min"), at(tomorrow, 9))
        // A locked private goal names nothing, its reason included.
        habit("Journal", LocalTime(10, 0), private = true, why = "so my head is clearer")
        dao.putSetting(SettingEntity(LOCK_PRIVATE, "1"))
        assertEquals(listOf("🔒 A private goal" to "Due now"), at(tomorrow, 10).filter { "late" !in it.first })
    }
}
