package io.github.randomtools3377.checklist.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.randomtools3377.checklist.AppIcon
import io.github.randomtools3377.checklist.calendar.PhoneCalendarInfo
import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.data.AiStatus
import io.github.randomtools3377.checklist.data.AutoBackupResult
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.reminders.FocusState
import kotlinx.datetime.LocalDate

/** A file the user picked with the phone's own dialog, by the phone's own name for it. */
typealias PickedFile = String

/**
 * What the shared screens ask of the phone they run on: the parts every phone shares, and what
 * only a phone can do, each phone in its own way (Android in the app, the iPhone in iosMain, and
 * a Windows computer in jvmMain, as DesktopPhone).
 * Something a phone can't do yet says so ([hasCalendar], [hasAi], [canPickFolder]).
 */
interface Phone {
    val parts: SharedParts

    /** Whether the device has a recent-apps screen showing the app's preview (a computer doesn't). */
    val hasRecentsPreview: Boolean get() = true

    /** Whether the app is running under its own tests, where first-time tips would get in the way. */
    val underTest: Boolean get() = false

    /** The app's version, like "0.55.0". */
    val appVersion: String

    // Reminders

    /** Sets the next reminders again, after goals or settings changed. */
    suspend fun rescheduleReminders()

    /** A goal is done: its reminders for now go. */
    fun afterDone(goalId: Long)

    /** Whether the app may show notifications. */
    fun canNotify(): Boolean

    /** Opens the phone's notification settings for this app. */
    fun openNotificationSettings()

    // The focus timer, kept by the phone so it carries on while the app is closed.

    fun focus(): FocusState?
    fun startFocus(goalId: Long, minutes: Int)
    fun pauseFocus()
    fun resumeFocus()
    fun stopFocus()

    /**
     * Ends the focus timer and hands it over, once: null when it was already ended elsewhere
     * (on Android, by the alarm at its end, which ticks the goal itself).
     */
    fun takeFocus(): FocusState? = focus()?.also { stopFocus() }

    // The phone's calendar

    val hasCalendar: Boolean get() = false
    suspend fun calendarEvents(ids: Set<Long>, from: LocalDate, days: Int): List<Busy> = emptyList()
    suspend fun calendars(): List<PhoneCalendarInfo> = emptyList()

    // The optional AI, in the separate Checklist AI app

    val hasAi: Boolean get() = false
    suspend fun aiStatus(): AiStatus = AiStatus(installed = false, appVersion = appVersion)
    suspend fun askAi(system: String, prompt: String, schema: String?, maxTokens: Int): Result<String> =
        Result.failure(IllegalStateException("No AI on this phone."))
    fun openAiApp() {}
    fun openReleases() {}

    // Files

    /** Writes [text] to [file], replacing what was there. Throws if it can't. */
    suspend fun writeText(file: PickedFile, text: String)

    /** Reads [file] as text. Throws if it can't. */
    suspend fun readText(file: PickedFile): String

    /** Copies a picked photo in, to go behind Today. False if it couldn't be read. */
    suspend fun addBackgroundPhoto(file: PickedFile): Boolean

    /** The photo behind Today, if there is one. */
    suspend fun backgroundPhoto(): ByteArray? = parts.photo?.read()

    /** Copies a picked GIF in for a goal's celebration; its file name, or why not. */
    suspend fun addGif(file: PickedFile): Result<String>

    // Backups: the reminder, and the automatic backup to a folder the user picks.

    fun lastBackup(): Long?
    fun markBackedUp()
    fun firstSeen(): Long
    fun backupReminderOn(): Boolean
    fun setBackupReminderOn(on: Boolean)
    fun backupLater(until: Long)
    fun backupLaterUntil(): Long

    /** Whether this phone can keep automatic backups in a folder. */
    val canPickFolder: Boolean get() = false
    fun backupFolder(): PickedFile? = null
    fun backupFolderName(): String? = null
    fun setBackupFolder(folder: PickedFile?) {}
    suspend fun autoBackupIfDue(today: LocalDate, text: suspend () -> String): AutoBackupResult = AutoBackupResult.NotDue

    /** The automatic backups in the folder, newest first: each one's name and file. */
    suspend fun autoBackups(): List<Pair<String, PickedFile>> = emptyList()

    // Looks

    /** Whether the app's home-screen icon can be changed. */
    val canChangeIcon: Boolean get() = false
    fun setAppIcon(icon: AppIcon) {}

    /** Whether there are home-screen widgets to style. */
    val hasWidgets: Boolean get() = false

    // Small things kept on this phone only, not in the backup (like the emoji picked lately).

    fun localValue(file: String, key: String): String?
    fun setLocalValue(file: String, key: String, value: String)

    // Privacy: hiding the app's preview among the recent apps.

    /** Whether hiding the preview is a choice here (where it also blocks screenshots). */
    val previewIsAChoice: Boolean get() = false
    fun hidesPreview(): Boolean = true
    fun setHidesPreview(on: Boolean) {}

    // More about reminders: exact alarms, where the phone asks for them.

    fun exactAlarmsAllowed(): Boolean = true
    fun openExactAlarmSettings() {}

    /** Whether the user has let the app read the phone's calendar. */
    fun calendarAllowed(): Boolean = false

    // Messages

    /** A short message at the bottom of the screen. */
    fun toast(text: String)
}

/** The phone the screens run on. */
val LocalPhone = staticCompositionLocalOf<Phone> { error("No phone given") }
