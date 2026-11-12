package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.CelebrationSounds
import io.github.randomtools3377.checklist.data.AutoBackupResult
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.data.appDataFolder
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.data.openDatabase
import io.github.randomtools3377.checklist.reminders.FocusState
import io.github.randomtools3377.checklist.ui.Phone
import io.github.randomtools3377.checklist.ui.PickedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.EventQueue
import java.awt.Frame
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The computer's side of what the shared screens ask of the phone, for the Windows app (see the
 * desktop module). Everything is kept in the app's own folder ([appDataFolder]). A computer has
 * no phone calendar, AI, icon choice, widgets, buzz, morning plan, weekly summary or snooze here;
 * it says so and the screens leave those out. Reminders show while the app is running (see
 * [DesktopReminders]), with "Next up" for a habit chain; closing the window keeps the app in the
 * notification area for them, unless that is turned off (see [hideWindow]). The automatic backup
 * saves into a folder the user picks (see [DesktopBackups]).
 */
class DesktopPhone(
    override val appVersion: String,
    private val folder: File = appDataFolder(),
    private val startup: StartupShortcut = StartupShortcut(),
) : Phone {
    override val parts: SharedParts by lazy {
        SharedParts(openDatabase(folder), DesktopGifs(File(folder, "celebrations")), DesktopPhoto(File(folder, "background.jpg")))
    }

    private val local = LocalValues(File(folder, "local.properties"))

    private val backups = DesktopBackups(local)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val reminders = DesktopReminders(this)

    /** The app's window, to put the file dialogs over and to bring back from a reminder. */
    @Volatile
    var window: Frame? = null

    /** Whether the window shows; false while the app carries on in the notification area only. */
    val windowShown = MutableStateFlow(true)

    /** Ends the app, from "Quit" on the icon in the notification area; set by the app's window. */
    @Volatile
    var onQuit: (() -> Unit)? = null

    /** A short message to show at the bottom of the window; a computer has no toasts of its own. */
    val messages = MutableStateFlow<String?>(null)

    /** Held while this copy of the app runs, so a second copy can't open the same database. */
    private var folderLock: FileLock? = null

    /** Holds the app's folder for this copy of the app; false if another copy is open already. */
    fun holdFolder(): Boolean {
        val channel = runCatching {
            FileChannel.open(File(folder, "app.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        }.getOrNull() ?: return true
        val lock = runCatching { channel.tryLock() }.getOrNull()
        if (lock == null) {
            channel.close()
            return false
        }
        folderLock = lock
        return true
    }

    /**
     * From a second copy of the app, which can't open the database too: asks the copy that is
     * running to show its window, through a small file in the app's folder that it looks for.
     * False if nothing took it up within [waitMillis].
     */
    fun askToShow(waitMillis: Long = 3_000): Boolean {
        val request = File(folder, SHOW_REQUEST)
        if (runCatching { request.writeText("show") }.isFailure) return false
        val until = System.currentTimeMillis() + waitMillis
        while (request.exists()) {
            if (System.currentTimeMillis() >= until) {
                request.delete()
                return false
            }
            Thread.sleep(100)
        }
        return true
    }

    /** Whether another copy asked for the window since this was last looked at. */
    internal fun takeShowRequest(): Boolean = File(folder, SHOW_REQUEST).delete()

    /**
     * Starts looking for reminders, once a minute while the app runs, and for another copy of
     * the app asking for the window (opening Checklist again while it sits in the notification area).
     */
    fun start() {
        takeShowRequest()
        reminders.start(scope)
        scope.launch {
            while (isActive) {
                delay(500)
                if (runCatching { takeShowRequest() }.getOrDefault(false)) bringToFront()
            }
        }
    }

    /**
     * The window was closed. True if the app carries on in the notification area with its window
     * hidden, so reminders still come: "Keep running when the window is closed" is on and the
     * icon is there. The first time, a message from the icon says so. False if the app should quit.
     */
    fun hideWindow(): Boolean {
        if (!keepsRunning() || !reminders.hasIcon()) return false
        windowShown.value = false
        if (local.string(TRAY_TOLD) != "1") {
            local.setString(TRAY_TOLD, "1")
            reminders.say("Checklist", STILL_RUNNING)
        }
        return true
    }

    /** "Quit" on the icon in the notification area. */
    fun quit() {
        EventQueue.invokeLater { onQuit?.invoke() }
    }

    /** Stops the reminders and takes the icon away, when the app closes. */
    fun close() {
        reminders.stop()
        scope.cancel()
        runCatching { folderLock?.channel()?.close() }
        folderLock = null
    }

    /** Brings the window back to the front, from a reminder or the icon in the notification area. */
    fun bringToFront() {
        windowShown.value = true
        val frame = window ?: return
        EventQueue.invokeLater {
            if (frame.extendedState and Frame.ICONIFIED != 0) frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
            frame.isVisible = true
            frame.toFront()
            frame.requestFocus()
        }
    }

    // Reminders are looked for each minute from the database as it is, so there is nothing to set.

    override suspend fun rescheduleReminders() {}

    override fun afterDone(goalId: Long) {
        scope.launch { runCatching { reminders.showNext(goalId) } }
    }

    override fun canNotify(): Boolean = DesktopReminders.canShow()

    // Carrying on in the notification area with the window closed, and starting with Windows.
    // Both are about this computer, so they are kept here and not in the backup file.

    override val canKeepRunning: Boolean get() = DesktopReminders.canShow()
    override fun keepsRunning(): Boolean = local.string(KEEP_RUNNING_OFF) != "1"
    override fun setKeepsRunning(on: Boolean) = local.setString(KEEP_RUNNING_OFF, if (on) null else "1")

    override val canStartAtSignIn: Boolean get() = startup.available
    override fun startsAtSignIn(): Boolean = startup.isOn()
    override fun setStartsAtSignIn(on: Boolean): Boolean = startup.set(on)

    /** Opens Windows' notification settings; other computers have no one place for them. */
    override fun openNotificationSettings() {
        if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
            runCatching { ProcessBuilder("explorer.exe", "ms-settings:notifications").start() }
        }
    }

    // The focus timer, kept in the local values so it carries on if the app is closed and opened again.

    override fun focus(): FocusState? {
        val goal = local.long(FOCUS_GOAL) ?: return null
        return FocusState(goal, local.long(FOCUS_ENDS) ?: 0, local.long(FOCUS_PAUSED), local.long(FOCUS_TOTAL) ?: 0, local.long(FOCUS_STARTED) ?: 0)
    }

    override fun startFocus(goalId: Long, minutes: Int) {
        val total = minutes * 60_000L
        local.setLong(FOCUS_GOAL, goalId)
        local.setLong(FOCUS_ENDS, nowMillis() + total)
        local.setLong(FOCUS_PAUSED, null)
        local.setLong(FOCUS_TOTAL, total)
        local.setLong(FOCUS_STARTED, nowMillis())
    }

    override fun pauseFocus() {
        val state = focus()?.takeIf { it.running } ?: return
        local.setLong(FOCUS_PAUSED, state.left(nowMillis()))
    }

    override fun resumeFocus() {
        val state = focus()?.takeIf { !it.running } ?: return
        local.setLong(FOCUS_ENDS, nowMillis() + (state.pausedLeft ?: 0))
        local.setLong(FOCUS_PAUSED, null)
    }

    override fun stopFocus() {
        listOf(FOCUS_GOAL, FOCUS_ENDS, FOCUS_PAUSED, FOCUS_TOTAL, FOCUS_STARTED).forEach { local.setString(it, null) }
    }

    // Files: a picked file is its full path on the computer. Picked photos and GIFs are copied in;
    // the user's own file is left where it was.

    override suspend fun writeText(file: PickedFile, text: String) = withContext(Dispatchers.IO) {
        writeWhole(File(file), text.toByteArray(Charsets.UTF_8))
    }

    override suspend fun readText(file: PickedFile): String = withContext(Dispatchers.IO) {
        File(file).readText(Charsets.UTF_8)
    }

    override suspend fun addBackgroundPhoto(file: PickedFile): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching { File(file).readBytes() }.getOrNull() ?: return@withContext false
        val jpeg = smallerJpeg(bytes) ?: return@withContext false
        parts.photo?.write(jpeg)
        true
    }

    override suspend fun addGif(file: PickedFile): Result<String> = withContext(Dispatchers.IO) {
        val picked = File(file)
        if (!picked.isFile) return@withContext Result.failure(Exception("That file couldn't be opened."))
        if (picked.length() > MAX_GIF_BYTES) {
            return@withContext Result.failure(Exception("That GIF is too big. Pick one under ${MAX_GIF_BYTES / 1_048_576} MB."))
        }
        val bytes = runCatching { picked.readBytes() }.getOrNull()
            ?: return@withContext Result.failure(Exception("That file couldn't be opened."))
        val header = bytes.copyOf(minOf(6, bytes.size)).decodeToString()
        if (header != "GIF87a" && header != "GIF89a") return@withContext Result.failure(Exception("That file isn't a GIF."))
        val name = "own-" + Random.nextBytes(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') } + ".gif"
        parts.gifs.write(name, bytes)
        Result.success(name)
    }

    /** A picked sound, kept if Java can play it here: a WAV, AIFF or AU file. */
    override suspend fun addSound(file: PickedFile): Result<String> = withContext(Dispatchers.IO) {
        val picked = File(file)
        if (!picked.isFile) return@withContext Result.failure(Exception(CelebrationSounds.CANT_OPEN))
        if (picked.length() > CelebrationSounds.MAX_BYTES) return@withContext Result.failure(Exception(CelebrationSounds.TOO_BIG))
        val bytes = runCatching { picked.readBytes() }.getOrNull()
            ?: return@withContext Result.failure(Exception(CelebrationSounds.CANT_OPEN))
        val plays = runCatching { javax.sound.sampled.AudioSystem.getAudioInputStream(java.io.ByteArrayInputStream(bytes)).close() }.isSuccess
        if (!plays) return@withContext Result.failure(Exception(CANT_PLAY_HERE))
        val name = CelebrationSounds.newName(Random.nextBytes(8))
        parts.gifs.write(name, bytes)
        Result.success(name)
    }

    // Backups: the reminder, and the automatic backup to a folder the user picks. Apart from
    // that folder, the computer app keeps no copy of its own anywhere else.

    override fun lastBackup(): Long? = local.long(BACKUP_LAST)
    override fun markBackedUp() = local.setLong(BACKUP_LAST, nowMillis())
    override fun firstSeen(): Long = local.long(BACKUP_FIRST) ?: nowMillis().also { local.setLong(BACKUP_FIRST, it) }
    override fun backupReminderOn(): Boolean = local.string(BACKUP_OFF) != "1"
    override fun setBackupReminderOn(on: Boolean) = local.setString(BACKUP_OFF, if (on) null else "1")
    override fun backupLater(until: Long) = local.setLong(BACKUP_LATER, until)
    override fun backupLaterUntil(): Long = local.long(BACKUP_LATER) ?: 0

    override val canPickFolder: Boolean = true
    override fun backupFolder(): PickedFile? = backups.folder()

    /** The folder as the computer names it: its full path, like C:\Users\Me\Documents\Backups. */
    override fun backupFolderName(): String? = backups.folder()
    override fun setBackupFolder(folder: PickedFile?) = backups.setFolder(folder)
    override suspend fun autoBackupIfDue(today: LocalDate, text: suspend () -> String): AutoBackupResult =
        withContext(Dispatchers.IO) { backups.saveIfDue(today, text) }
    override suspend fun autoBackups(): List<Pair<String, PickedFile>> = withContext(Dispatchers.IO) { backups.list() }

    override fun localValue(file: String, key: String): String? = local.string("$file.$key")
    override fun setLocalValue(file: String, key: String, value: String) = local.setString("$file.$key", value)

    // A computer has no recent-apps screen showing the app's preview, and nothing to buzz.
    override val hasRecentsPreview: Boolean = false
    override val canBuzz: Boolean = false

    // It is a computer, whose own backups are its owner's business, and whose lock the app can't ask for.
    override val deviceWord: String = "computer"
    override val outOfDeviceBackups: Boolean = false
    override val canAskForLock: Boolean = false

    override fun toast(text: String) {
        messages.value = text
    }

    companion object {
        private const val FOCUS_GOAL = "focus.goal"
        private const val FOCUS_ENDS = "focus.ends_at"
        private const val FOCUS_PAUSED = "focus.paused_left"
        private const val FOCUS_TOTAL = "focus.total"
        private const val FOCUS_STARTED = "focus.started_at"
        private const val BACKUP_LAST = DesktopBackups.LAST
        private const val KEEP_RUNNING_OFF = "window.keep_running_off"
        private const val TRAY_TOLD = "window.told_still_running"
        private const val SHOW_REQUEST = "show.request"

        /** Said once from the icon, the first time the window is closed and the app carries on. */
        const val STILL_RUNNING = "Checklist is still running so reminders can come. Quit from the icon by the clock."
        private const val BACKUP_FIRST = "backup.first_seen"
        private const val BACKUP_OFF = "backup.reminder_off"
        private const val BACKUP_LATER = "backup.later_until"
        private const val MAX_GIF_BYTES = 10 * 1_048_576
        internal const val CANT_PLAY_HERE = "That sound can't be played on this computer. Pick a WAV file."

        /**
         * A photo no bigger than the screen needs, as a JPEG, so the app and the backup stay
         * small; null if it isn't a photo Skia can read.
         */
        internal fun smallerJpeg(bytes: ByteArray, longest: Int = 2048): ByteArray? = runCatching {
            val image = Image.makeFromEncoded(bytes)
            val scale = minOf(1.0, longest.toDouble() / maxOf(image.width, image.height))
            val width = (image.width * scale).roundToInt().coerceAtLeast(1)
            val height = (image.height * scale).roundToInt().coerceAtLeast(1)
            val surface = Surface.makeRasterN32Premul(width, height)
            // JPEG has no see-through parts: those go black, as Today dims the photo anyway.
            surface.canvas.clear(0xFF000000.toInt())
            surface.canvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(width.toFloat(), height.toFloat()),
                FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                null,
                true,
            )
            surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 85)?.bytes
        }.getOrNull()
    }
}
