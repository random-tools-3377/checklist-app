package io.github.randomtools3377.checklist

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.randomtools3377.checklist.calendar.PhoneCalendar
import io.github.randomtools3377.checklist.calendar.PhoneCalendarInfo
import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.data.AiClient
import io.github.randomtools3377.checklist.data.AiStatus
import io.github.randomtools3377.checklist.data.AutoBackupResult
import io.github.randomtools3377.checklist.data.Backups
import io.github.randomtools3377.checklist.data.CelebrationFiles
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.reminders.Focus
import io.github.randomtools3377.checklist.reminders.FocusState
import io.github.randomtools3377.checklist.reminders.Reminders
import io.github.randomtools3377.checklist.ui.Phone
import io.github.randomtools3377.checklist.ui.PickedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.lang.ref.WeakReference

/**
 * Android's side of what the shared screens ask of the phone: reminders and the focus timer,
 * the phone's calendar, Checklist AI, files through the system dialogs, the automatic backup
 * folder, the privacy switch and messages. All of it is the app's existing Android code.
 */
class AndroidPhone(private val app: ChecklistApplication) : Phone {
    private val container get() = app.container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The activity on screen now, for what needs one (the recent-apps preview). */
    var activity: WeakReference<Activity>? = null

    override val parts: SharedParts get() = container.parts

    // The phone tests bring their own runner into the app; a real install never has it.
    override val underTest: Boolean = runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess
    override val appVersion: String = BuildConfig.VERSION_NAME

    override suspend fun rescheduleReminders() = Reminders.reschedule(app)

    override fun afterDone(goalId: Long) {
        scope.launch { Reminders.afterDone(app, goalId) }
    }

    override fun canNotify(): Boolean = Reminders.canNotify(app)

    override fun openNotificationSettings() = start(
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, app.packageName),
    )

    override fun exactAlarmsAllowed(): Boolean = Reminders.exactAlarmsAllowed(app)

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) start(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
    }

    override fun focus(): FocusState? = Focus.current(app)
    override fun startFocus(goalId: Long, minutes: Int) = Focus.start(app, goalId, minutes)
    override fun pauseFocus() = Focus.pause(app)
    override fun resumeFocus() = Focus.resume(app)
    override fun stopFocus() = Focus.stop(app)

    override val hasCalendar: Boolean = true
    override fun calendarAllowed(): Boolean = PhoneCalendar.allowed(app)
    override suspend fun calendarEvents(ids: Set<Long>, from: LocalDate, days: Int): List<Busy> =
        withContext(Dispatchers.IO) { PhoneCalendar.events(app, ids, from, days) }
    override suspend fun calendars(): List<PhoneCalendarInfo> = withContext(Dispatchers.IO) { PhoneCalendar.calendars(app) }

    override val hasAi: Boolean = true
    override suspend fun aiStatus(): AiStatus = AiClient.status(app)
    override suspend fun askAi(system: String, prompt: String, schema: String?, maxTokens: Int): Result<String> =
        AiClient.ask(app, system, prompt, schema, maxTokens)
    override fun openAiApp() = AiClient.open(app)
    override fun openReleases() = AiClient.openReleases(app)

    override suspend fun writeText(file: PickedFile, text: String) = withContext(Dispatchers.IO) {
        val stream = app.contentResolver.openOutputStream(Uri.parse(file), "wt") ?: error("no stream")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun readText(file: PickedFile): String = withContext(Dispatchers.IO) {
        val stream = app.contentResolver.openInputStream(Uri.parse(file)) ?: error("no stream")
        stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override suspend fun addBackgroundPhoto(file: PickedFile): Boolean =
        withContext(Dispatchers.IO) { container.backgroundPhoto.add(app, Uri.parse(file)) }

    override suspend fun addGif(file: PickedFile): Result<String> = withContext(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(Uri.parse(file))?.let { container.celebrationFiles.add(it) }
                ?: Result.failure(CelebrationFiles.AddError("That file couldn't be opened."))
        } catch (e: Exception) {
            Result.failure(CelebrationFiles.AddError("That file couldn't be opened."))
        }
    }

    override fun lastBackup(): Long? = Backups.lastBackup(app)
    override fun markBackedUp() = Backups.markBackedUp(app)
    override fun firstSeen(): Long = Backups.firstSeen(app)
    override fun backupReminderOn(): Boolean = Backups.reminderOn(app)
    override fun setBackupReminderOn(on: Boolean) = Backups.setReminderOn(app, on)
    override fun backupLater(until: Long) = Backups.later(app, until)
    override fun backupLaterUntil(): Long = Backups.laterUntil(app)

    override val canPickFolder: Boolean = true
    override fun backupFolder(): PickedFile? = Backups.folder(app)?.toString()
    override fun backupFolderName(): String? = Backups.folderName(app)
    override fun setBackupFolder(folder: PickedFile?) = Backups.setFolder(app, folder?.let(Uri::parse))
    override suspend fun autoBackupIfDue(today: LocalDate, text: suspend () -> String): AutoBackupResult =
        withContext(Dispatchers.IO) { Backups.autoBackupIfDue(app, today, text) }
    override suspend fun autoBackups(): List<Pair<String, PickedFile>> =
        withContext(Dispatchers.IO) { Backups.autoBackups(app).map { (name, uri) -> name to uri.toString() } }

    override val canChangeIcon: Boolean = true
    override val hasWidgets: Boolean = true

    override fun localValue(file: String, key: String): String? {
        val prefs = app.getSharedPreferences(file, Context.MODE_PRIVATE)
        // Older versions kept some of these as numbers.
        return try {
            prefs.getString(key, null)
        } catch (e: ClassCastException) {
            prefs.all[key]?.toString()
        }
    }

    override fun setLocalValue(file: String, key: String, value: String) {
        app.getSharedPreferences(file, Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }

    // Before Android 13, hiding the preview also blocks screenshots, so it is a switch there.
    override val previewIsAChoice: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    override fun hidesPreview(): Boolean = Privacy.hideInRecents(app)
    override fun setHidesPreview(on: Boolean) {
        Privacy.setHideInRecents(app, on)
        activity?.get()?.let { Privacy.apply(it) }
    }

    override fun toast(text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(app, text, Toast.LENGTH_LONG).show() }
    }

    private fun start(intent: Intent) {
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
