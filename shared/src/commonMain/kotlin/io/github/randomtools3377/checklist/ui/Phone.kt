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

/** The user closed the phone's "Save to" dialog without saving, so the file went nowhere. */
class NotSavedException : Exception("Not saved.")

/**
 * What the shared screens ask of the phone they run on: the parts every phone shares, and what
 * only a phone can do, each phone in its own way (Android in the app, the iPhone in iosMain, and
 * a Windows computer in jvmMain, as DesktopPhone).
 * Something a phone can't do yet says so ([hasCalendar], [hasAi], [canPickFolder],
 * [hasPlanNotifications], [canSnooze]), and the screens leave it out or say it isn't there yet.
 */
interface Phone {
    val parts: SharedParts

    /**
     * The device's own word for itself, for the app's wording: "phone" on Android, "iPhone" on
     * an iPhone and "computer" on Windows, as in "Everything stays on this computer".
     */
    val deviceWord: String get() = "phone"

    /** Whether the device's own backups leave the app out, so the export file is the only copy (not said on a computer). */
    val outOfDeviceBackups: Boolean get() = true

    /** Whether the privacy lock can ask for the device's own lock (a computer's can't, so it opens with a note). */
    val canAskForLock: Boolean get() = true

    /** Whether the device has a recent-apps screen showing the app's preview (a computer doesn't). */
    val hasRecentsPreview: Boolean get() = true

    /** Whether the app is running under its own tests, where first-time tips would get in the way. */
    val underTest: Boolean get() = false

    /** The app's version, like "0.55.0". */
    val appVersion: String

    // Reminders

    /** Sets the next reminders again, after goals or settings changed. */
    suspend fun rescheduleReminders()

    /** A goal is done: its reminders for now go, and the goals after it in a habit chain are reminded. */
    fun afterDone(goalId: Long)

    /** Drops snoozed reminders, after a restore, when a goal's id can mean another goal. */
    fun forgetSnoozes() {}

    /** Whether the app may show notifications. */
    fun canNotify(): Boolean

    /** Opens the phone's notification settings for this app. */
    fun openNotificationSettings()

    /** Whether the morning plan and the weekly summary come as notifications here. */
    val hasPlanNotifications: Boolean get() = false

    /** Whether a reminder can be snoozed from its notification here. */
    val canSnooze: Boolean get() = false

    // A computer: carrying on by the clock with the window closed, so reminders still come.

    /** Whether the app can keep running with its window closed (a computer with a notification area). */
    val canKeepRunning: Boolean get() = false
    fun keepsRunning(): Boolean = false
    fun setKeepsRunning(on: Boolean) {}

    /** Whether the app can be set to start when the user signs in to the computer. */
    val canStartAtSignIn: Boolean get() = false
    fun startsAtSignIn(): Boolean = false

    /** Turns starting at sign-in on or off; whether it is now as asked. It can take a moment. */
    fun setStartsAtSignIn(on: Boolean): Boolean = false

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

    /** Starts a break after a focus session: the same countdown with no goal (see [FocusState.isBreak]). */
    fun startBreak(minutes: Int) = startFocus(FocusState.BREAK_GOAL, minutes)

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

    /**
     * Writes [text] to [file], replacing what was there. Throws if it can't, and throws
     * [NotSavedException] where the phone asks where to save only now and the user says nowhere.
     */
    suspend fun writeText(file: PickedFile, text: String)

    /** Reads [file] as text. Throws if it can't. */
    suspend fun readText(file: PickedFile): String

    /** Copies a picked photo in, to go behind Today. False if it couldn't be read. */
    suspend fun addBackgroundPhoto(file: PickedFile): Boolean

    /** The photo behind Today, if there is one. */
    suspend fun backgroundPhoto(): ByteArray? = parts.photo?.read()

    /** Copies a picked GIF in for a goal's celebration; its file name, or why not. */
    suspend fun addGif(file: PickedFile): Result<String>

    /**
     * Copies a picked sound file in for celebrations; its file name, or why not: it is over
     * 5 MB, or this device can't play it.
     */
    suspend fun addSound(file: PickedFile): Result<String> =
        Result.failure(Exception(io.github.randomtools3377.checklist.core.CelebrationSounds.CANT_PLAY))

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

    /** Whether this device saves a "before import" copy in the automatic backup's folder. */
    val canSaveBeforeImport: Boolean get() = false

    /**
     * Saves [text] as [name] in the automatic backup's folder, before an import replaces
     * everything, and trims older "before import" copies (see AutoBackupFiles). Whether it was
     * saved. Only asked where [canSaveBeforeImport].
     */
    suspend fun saveBeforeImport(name: String, text: String): Boolean = false

    /** The automatic backups in the folder, newest first: each one's name and file. */
    suspend fun autoBackups(): List<Pair<String, PickedFile>> = emptyList()

    // Looks

    /** Whether the app's home-screen icon can be changed. */
    val canChangeIcon: Boolean get() = false
    fun setAppIcon(icon: AppIcon) {}

    /** Whether there are home-screen widgets to style. */
    val hasWidgets: Boolean get() = false

    /** Whether the device can buzz, for celebrations (a computer can't). */
    val canBuzz: Boolean get() = true

    /**
     * A buzz of [kind] in the device's own way, where it has one for each kind; whether it took
     * it. False (a computer, or a phone without its own) leaves the buzz to the app's two usual ones.
     */
    fun buzz(kind: io.github.randomtools3377.checklist.core.BuzzKind): Boolean = false

    /**
     * Whether the device asks for less motion, where that is known. "Less motion" in Appearance
     * starts on it (see LocalCalm), and every animation in the app follows that.
     */
    fun reducedMotion(): Boolean = false

    /**
     * Whether a notification can light up the screen and set off the phone's own edge lighting
     * (Android, where the phone has it): for reminders, and for a tick made away from the app.
     */
    val canLightEdges: Boolean get() = false

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

/** [Phone.deviceWord] to start a sentence: "Phone", "iPhone" or "Computer". */
val Phone.deviceTitle: String get() = startOfSentence(deviceWord)

/** [word] with a capital, to start a sentence, unless it has its own already inside, like "iPhone". */
internal fun startOfSentence(word: String): String =
    if (word.length > 1 && word[1].isUpperCase()) word else word.replaceFirstChar { it.uppercase() }
