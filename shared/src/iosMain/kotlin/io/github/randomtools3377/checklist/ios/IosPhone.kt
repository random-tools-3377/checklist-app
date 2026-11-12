@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.randomtools3377.checklist.ios

import io.github.randomtools3377.checklist.core.CelebrationSounds
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.data.nextInChain
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.data.openDatabase
import io.github.randomtools3377.checklist.reminders.FocusState
import io.github.randomtools3377.checklist.ui.NotSavedException
import io.github.randomtools3377.checklist.ui.Phone
import io.github.randomtools3377.checklist.ui.PickedFile
import kotlinx.cinterop.useContents
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioPlayer
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.random.Random

/** Runs [block] on the iPhone's main thread, where the screen is drawn. */
internal fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

/** The screen on top now, to show the iPhone's own dialogs over it. */
internal fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/** Says whether the "Save to" dialog saved the file somewhere or was cancelled. */
private class SavePickerDelegate(private val onAnswer: (Boolean) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onAnswer(true)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onAnswer(false)
}

/**
 * The iPhone's side of what the shared screens ask of the phone. The iPhone app has no phone
 * calendar, AI, automatic backup folder, home-screen icon choice, widgets, morning plan, weekly
 * summary or snooze yet; it says so and the screens leave those out.
 */
class IosPhone : Phone {
    override val parts: SharedParts by lazy { SharedParts(openDatabase(), IosGifs(), IosPhoto()) }

    override val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

    /** The app's wording calls it an iPhone. */
    override val deviceWord: String = "iPhone"

    private val scope = MainScope()

    /** A short message to show at the bottom of the screen; the iPhone has no toasts of its own. */
    val messages = MutableStateFlow<String?>(null)

    /** A file the "Save to" dialog is waiting for: it opens once the file is written. */
    private var pendingExport: String? = null

    /** The open "Save to" dialog's delegate, held here as the dialog only holds it weakly. */
    private var saveDelegate: NSObject? = null

    init {
        // Reminders are set again whenever goals, ticks or settings change.
        scope.launch {
            parts.db.invalidationTracker.createFlow("goals", "ticks", "settings", "steps", "lapses", "entries", "checks")
                .conflate()
                .collect {
                    runCatching { IosReminders.reschedule(parts, focus()) }
                    delay(500)
                }
        }
        IosReminders.showWhileOpen()
        IosReminders.refreshAllowed()
    }

    override suspend fun rescheduleReminders() = IosReminders.reschedule(parts, focus())

    override fun afterDone(goalId: Long) {
        scope.launch {
            runCatching { IosReminders.reschedule(parts, focus()) }
            // The goals after it in a habit chain, shown at the bottom of the screen.
            val next = runCatching { nextInChain(parts.db.dao(), goalId) }.getOrDefault(emptyList())
            if (next.isNotEmpty()) toast("Next up: " + next.joinToString(", ") { it.title })
        }
    }

    override fun canNotify(): Boolean = IosReminders.allowed

    override fun openNotificationSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }

    // The focus timer, kept in the iPhone's settings so it carries on while the app is closed.

    override fun focus(): FocusState? {
        val goal = Local.long(FOCUS_GOAL) ?: return null
        return FocusState(goal, Local.long(FOCUS_ENDS) ?: 0, Local.long(FOCUS_PAUSED), Local.long(FOCUS_TOTAL) ?: 0)
    }

    override fun startFocus(goalId: Long, minutes: Int) {
        val total = minutes * 60_000L
        Local.setLong(FOCUS_GOAL, goalId)
        Local.setLong(FOCUS_ENDS, nowMillis() + total)
        Local.setLong(FOCUS_PAUSED, null)
        Local.setLong(FOCUS_TOTAL, total)
    }

    override fun pauseFocus() {
        val state = focus()?.takeIf { it.running } ?: return
        Local.setLong(FOCUS_PAUSED, state.left(nowMillis()))
    }

    override fun resumeFocus() {
        val state = focus()?.takeIf { !it.running } ?: return
        Local.setLong(FOCUS_ENDS, nowMillis() + (state.pausedLeft ?: 0))
        Local.setLong(FOCUS_PAUSED, null)
    }

    override fun stopFocus() {
        listOf(FOCUS_GOAL, FOCUS_ENDS, FOCUS_PAUSED, FOCUS_TOTAL).forEach { Local.setString(it, null) }
    }

    // Files. Saving works the iPhone's way round: the file is written in the app's temporary
    // folder first, then the "Save to" dialog puts a copy where the user chooses. The app's own
    // copy goes once the dialog answers, so no copy stays behind.

    internal fun expectExport(path: String) {
        pendingExport = path
    }

    override suspend fun writeText(file: PickedFile, text: String) {
        check(writeFile(file, text.encodeToByteArray())) { "Couldn't write the file." }
        if (file != pendingExport) return
        pendingExport = null
        val saved = try {
            suspendCancellableCoroutine<Boolean> { answer ->
                onMain {
                    val top = topViewController()
                    if (top == null) {
                        if (answer.isActive) answer.resume(false)
                    } else {
                        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(file)), asCopy = true)
                        val handler = SavePickerDelegate { picked ->
                            saveDelegate = null
                            if (answer.isActive) answer.resume(picked)
                        }
                        saveDelegate = handler
                        picker.delegate = handler
                        top.presentViewController(picker, animated = true, completion = null)
                    }
                }
            }
        } finally {
            removeFile(file)
        }
        if (!saved) throw NotSavedException()
    }

    /** Reads a file the file dialog copied in for the app (see rememberOpenFile), then deletes that copy. */
    override suspend fun readText(file: PickedFile): String {
        val bytes = readFile(file)
        removeFile(file)
        return requireNotNull(bytes) { "Couldn't read the file." }.decodeToString()
    }

    override suspend fun addBackgroundPhoto(file: PickedFile): Boolean {
        val bytes = readFile(file) ?: return false
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return false
        val jpeg = UIImageJPEGRepresentation(smaller(image), 0.85) ?: return false
        parts.photo?.write(jpeg.toByteArray())
        removeFile(file)
        return true
    }

    /** A photo no bigger than the screen needs, so the app and the backup stay small. */
    private fun smaller(image: UIImage, longest: Double = 2048.0): UIImage {
        val (width, height) = image.size.useContents { width to height }
        val scale = longest / maxOf(width, height)
        if (scale >= 1.0) return image
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width * scale, height * scale), false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
        val drawn = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return drawn ?: image
    }

    override suspend fun addGif(file: PickedFile): Result<String> {
        val bytes = readFile(file) ?: return Result.failure(Exception("That file couldn't be opened."))
        removeFile(file)
        // The photo picker can't show only GIFs, so a still photo can come here too.
        val header = bytes.copyOf(minOf(6, bytes.size)).decodeToString()
        if (header != "GIF87a" && header != "GIF89a") return Result.failure(Exception("That photo isn't a GIF. Pick a GIF."))
        if (bytes.size > MAX_GIF_BYTES) return Result.failure(Exception("That GIF is too big. Pick one under ${MAX_GIF_BYTES / 1_048_576} MB."))
        val name = "own-" + Random.nextBytes(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') } + ".gif"
        parts.gifs.write(name, bytes)
        return Result.success(name)
    }

    /** A picked sound, kept if the iPhone can play it. The file dialog handed over a copy, which goes. */
    override suspend fun addSound(file: PickedFile): Result<String> {
        val bytes = readFile(file) ?: return Result.failure(Exception(CelebrationSounds.CANT_OPEN))
        removeFile(file)
        if (bytes.size > CelebrationSounds.MAX_BYTES) return Result.failure(Exception(CelebrationSounds.TOO_BIG))
        // A file the iPhone can't play gives no player, which comes here as an exception.
        val plays = bytes.isNotEmpty() && runCatching { AVAudioPlayer(data = bytes.toNSData(), error = null).prepareToPlay() }.getOrDefault(false)
        if (!plays) return Result.failure(Exception(CelebrationSounds.CANT_PLAY))
        val name = CelebrationSounds.newName(Random.nextBytes(8))
        parts.gifs.write(name, bytes)
        return Result.success(name)
    }

    /** "Reduce Motion" in the iPhone's Accessibility settings. */
    override fun reducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()

    // Backups: the reminder only; the iPhone app keeps no copy of its own anywhere else.

    override fun lastBackup(): Long? = Local.long(BACKUP_LAST)
    override fun markBackedUp() = Local.setLong(BACKUP_LAST, nowMillis())
    override fun firstSeen(): Long = Local.long(BACKUP_FIRST) ?: nowMillis().also { Local.setLong(BACKUP_FIRST, it) }
    override fun backupReminderOn(): Boolean = Local.string(BACKUP_OFF) != "1"
    override fun setBackupReminderOn(on: Boolean) = Local.setString(BACKUP_OFF, if (on) null else "1")
    override fun backupLater(until: Long) = Local.setLong(BACKUP_LATER, until)
    override fun backupLaterUntil(): Long = Local.long(BACKUP_LATER) ?: 0

    override fun localValue(file: String, key: String): String? = Local.string("$file.$key")
    override fun setLocalValue(file: String, key: String, value: String) = Local.setString("$file.$key", value)

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
    }
}
