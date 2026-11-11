package io.github.randomtools3377.checklist.desktop

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
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
 * no phone calendar, AI, automatic backup folder, icon choice or widgets here; it says so and the
 * screens leave those out. Reminders show only while the app is open (see [DesktopReminders]).
 */
class DesktopPhone(
    override val appVersion: String,
    private val folder: File = appDataFolder(),
) : Phone {
    override val parts: SharedParts by lazy {
        SharedParts(openDatabase(folder), DesktopGifs(File(folder, "celebrations")), DesktopPhoto(File(folder, "background.jpg")))
    }

    private val local = LocalValues(File(folder, "local.properties"))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val reminders = DesktopReminders(this)

    /** The app's window, to put the file dialogs over and to bring back from a reminder. */
    @Volatile
    var window: Frame? = null

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

    /** Starts looking for reminders, once a minute while the app is open. */
    fun start() {
        reminders.start(scope)
    }

    /** Stops the reminders and takes the icon away, when the app closes. */
    fun close() {
        reminders.stop()
        scope.cancel()
        runCatching { folderLock?.channel()?.close() }
        folderLock = null
    }

    /** Brings the window back to the front, from a reminder. */
    fun bringToFront() {
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

    override fun afterDone(goalId: Long) {}

    override fun canNotify(): Boolean = DesktopReminders.canShow()

    /** Opens Windows' notification settings; other computers have no one place for them. */
    override fun openNotificationSettings() {
        if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
            runCatching { ProcessBuilder("explorer.exe", "ms-settings:notifications").start() }
        }
    }

    // The focus timer, kept in the local values so it carries on if the app is closed and opened again.

    override fun focus(): FocusState? {
        val goal = local.long(FOCUS_GOAL) ?: return null
        return FocusState(goal, local.long(FOCUS_ENDS) ?: 0, local.long(FOCUS_PAUSED), local.long(FOCUS_TOTAL) ?: 0)
    }

    override fun startFocus(goalId: Long, minutes: Int) {
        val total = minutes * 60_000L
        local.setLong(FOCUS_GOAL, goalId)
        local.setLong(FOCUS_ENDS, nowMillis() + total)
        local.setLong(FOCUS_PAUSED, null)
        local.setLong(FOCUS_TOTAL, total)
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
        listOf(FOCUS_GOAL, FOCUS_ENDS, FOCUS_PAUSED, FOCUS_TOTAL).forEach { local.setString(it, null) }
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

    // Backups: the reminder only; the computer app keeps no copy of its own anywhere else.

    override fun lastBackup(): Long? = local.long(BACKUP_LAST)
    override fun markBackedUp() = local.setLong(BACKUP_LAST, nowMillis())
    override fun firstSeen(): Long = local.long(BACKUP_FIRST) ?: nowMillis().also { local.setLong(BACKUP_FIRST, it) }
    override fun backupReminderOn(): Boolean = local.string(BACKUP_OFF) != "1"
    override fun setBackupReminderOn(on: Boolean) = local.setString(BACKUP_OFF, if (on) null else "1")
    override fun backupLater(until: Long) = local.setLong(BACKUP_LATER, until)
    override fun backupLaterUntil(): Long = local.long(BACKUP_LATER) ?: 0

    override fun localValue(file: String, key: String): String? = local.string("$file.$key")
    override fun setLocalValue(file: String, key: String, value: String) = local.setString("$file.$key", value)

    // A computer has no recent-apps screen showing the app's preview.
    override val hasRecentsPreview: Boolean = false

    override fun toast(text: String) {
        messages.value = text
    }

    companion object {
        private const val FOCUS_GOAL = "focus.goal"
        private const val FOCUS_ENDS = "focus.ends_at"
        private const val FOCUS_PAUSED = "focus.paused_left"
        private const val FOCUS_TOTAL = "focus.total"
        private const val BACKUP_LAST = "backup.last"
        private const val BACKUP_FIRST = "backup.first_seen"
        private const val BACKUP_OFF = "backup.reminder_off"
        private const val BACKUP_LATER = "backup.later_until"
        private const val MAX_GIF_BYTES = 10 * 1_048_576

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
