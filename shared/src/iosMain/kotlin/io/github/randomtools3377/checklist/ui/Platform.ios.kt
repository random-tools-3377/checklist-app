@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.randomtools3377.checklist.ios.IosPhone
import io.github.randomtools3377.checklist.ios.IosReminders
import io.github.randomtools3377.checklist.ios.onMain
import io.github.randomtools3377.checklist.ios.temporary
import io.github.randomtools3377.checklist.ios.toNSData
import io.github.randomtools3377.checklist.ios.topViewController
import io.github.randomtools3377.checklist.ui.resources.Res
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCurrencySymbol
import platform.Foundation.NSURL
import platform.Foundation.currentLocale
import platform.Foundation.writeToFile
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject
import kotlin.random.Random

/** [date] at [time] as the iPhone's own date, in its current calendar and time zone. */
private fun nsDate(date: LocalDate, time: LocalTime = LocalTime(12, 0)): NSDate {
    val parts = NSDateComponents()
    parts.year = date.year.toLong()
    parts.month = date.monthNumber.toLong()
    parts.day = date.dayOfMonth.toLong()
    parts.hour = time.hour.toLong()
    parts.minute = time.minute.toLong()
    return NSCalendar.currentCalendar.dateFromComponents(parts) ?: NSDate()
}

private fun fromTemplate(template: String): NSDateFormatter =
    NSDateFormatter().apply { setLocalizedDateFormatFromTemplate(template) }

private val ShortDate = fromTemplate("EEEdMMM")
private val LongDate = fromTemplate("EEEEdMMMM")
private val ShortTime = NSDateFormatter().apply {
    dateStyle = NSDateFormatterNoStyle
    timeStyle = NSDateFormatterShortStyle
}

actual fun shortDate(date: LocalDate): String = ShortDate.stringFromDate(nsDate(date))
actual fun longDate(date: LocalDate): String = LongDate.stringFromDate(nsDate(date))
actual fun timeText(time: LocalTime): String = ShortTime.stringFromDate(nsDate(LocalDate(2000, 1, 1), time))

/** The iPhone shows "am" and "pm" on a 12-hour clock; without them it is a 24-hour one. */
@Composable
actual fun is24HourClock(): Boolean =
    NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains("a") != true

actual val canMatchWallpaper: Boolean get() = false

@Composable
actual fun wallpaperColors(dark: Boolean): ColorScheme? = null

/** The iPhone's emoji font, as Skia finds it for a smiley; null if it has none. */
private val emojiFont: Typeface? by lazy {
    runCatching { FontMgr.default.matchFamilyStyleCharacter(null, FontStyle.NORMAL, null, 0x1F600) }.getOrNull()
}

/** Whether [font] draws [point] itself, rather than the stand-in box of a last-resort font. */
private fun draws(font: Typeface?, point: Int): Boolean =
    font != null && font.getUTF32Glyph(point).toInt() != 0 && !font.familyName.contains("LastResort")

/**
 * Whether the iPhone can draw [emoji]: each part of it is in its emoji font, or in another font
 * Skia finds for it, as the screens draw with Skia. Shown if the fonts can't be asked.
 */
actual fun canShowEmoji(emoji: String): Boolean = runCatching {
    val parts = emojiParts(emoji)
    parts.isNotEmpty() && parts.all { point ->
        draws(emojiFont, point) || draws(FontMgr.default.matchFamilyStyleCharacter(null, FontStyle.NORMAL, null, point), point)
    }
}.getOrDefault(true)

actual fun moneySymbol(): String = NSLocale.currentLocale.objectForKey(NSLocaleCurrencySymbol) as? String ?: ""

/** The iPhone has no back button; screens have their own back arrow. */
@Composable
actual fun BackButton(enabled: Boolean, onBack: () -> Unit) {}

/** Hands what the iPhone's file dialog picked back, then lets go of itself. */
private class FilePickerDelegate(private val onPicked: (PickedFile?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.path)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onPicked(null)
}

/**
 * Saving: the file is made in the app's temporary folder; once the app has written it, the
 * iPhone's "Save to" dialog puts a copy where the user chooses (see IosPhone.writeText).
 */
@Composable
actual fun rememberSaveFile(mimeType: String, onPicked: (PickedFile?) -> Unit): (suggestedName: String) -> Unit {
    val phone = LocalPhone.current as? IosPhone
    return { name ->
        val path = temporary(name)
        phone?.expectExport(path)
        onPicked(path)
    }
}

@Composable
actual fun rememberOpenFile(mimeTypes: List<String>, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val delegate = remember { arrayOfNulls<FilePickerDelegate>(1) }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText, UTTypeData), asCopy = true)
        val handler = FilePickerDelegate(onPicked)
        delegate[0] = handler
        picker.delegate = handler
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

/** The iPhone app keeps no automatic backups in a folder. */
@Composable
actual fun rememberPickFolder(onPicked: (PickedFile?) -> Unit): () -> Unit = { onPicked(null) }

/** Hands the photo the iPhone's photo picker picked back, as a file in the temporary folder. */
private class PhotoPickerDelegate(
    private val gifOnly: Boolean,
    private val onPicked: (PickedFile?) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onPicked(null)
            return
        }
        // A still photo picked for a GIF comes back as it is, so adding it can say it isn't a GIF.
        val gif = gifOnly && result.itemProvider.hasItemConformingToTypeIdentifier("com.compuserve.gif")
        val type = if (gif) "com.compuserve.gif" else "public.image"
        result.itemProvider.loadDataRepresentationForTypeIdentifier(type) { data, _ ->
            val path = data?.let { picked ->
                temporary("picked-" + Random.nextLong().toULong().toString(16) + if (gifOnly) ".gif" else ".image")
                    .takeIf { picked.writeToFile(it, atomically = true) }
            }
            onMain { onPicked(path) }
        }
    }
}

/** The iPhone's photo picker: one photo, no permission needed. */
@Composable
actual fun rememberPickImage(gifOnly: Boolean, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val delegate = remember { arrayOfNulls<PhotoPickerDelegate>(1) }
    return {
        val configuration = PHPickerConfiguration()
        configuration.filter = PHPickerFilter.imagesFilter
        configuration.selectionLimit = 1
        val picker = PHPickerViewController(configuration)
        val handler = PhotoPickerDelegate(gifOnly, onPicked)
        delegate[0] = handler
        picker.delegate = handler
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberAsk(what: Ask, onAnswer: (Boolean) -> Unit): () -> Unit = {
    when (what) {
        Ask.NOTIFICATIONS -> IosReminders.ask { granted -> onMain { onAnswer(granted) } }
        Ask.CALENDAR -> onAnswer(false)
    }
}

/**
 * Unlocks with the iPhone's own lock: Face ID, Touch ID or the passcode. An iPhone with no
 * passcode can't be asked, so there the lock simply opens, with a note saying why.
 */
@Composable
actual fun rememberUnlock(vm: MainViewModel): () -> Unit {
    val phone = LocalPhone.current
    return {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)) {
            phone.toast("This iPhone has no passcode, so the privacy lock can't ask for one.")
            vm.markUnlocked()
        } else {
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = "Unlock what's behind the privacy lock") { ok, _ ->
                if (ok) onMain { vm.markUnlocked() }
            }
        }
    }
}

/** The two celebration sounds, played quietly alongside other sound and silenced by the switch. */
private class IosSounds(tick: ByteArray, big: ByteArray) : Sounds {
    private val tickPlayer = AVAudioPlayer(data = tick.toNSData(), error = null).apply { prepareToPlay() }
    private val bigPlayer = AVAudioPlayer(data = big.toNSData(), error = null).apply { prepareToPlay() }

    override fun tick() {
        tickPlayer.currentTime = 0.0
        tickPlayer.play()
    }

    override fun big() {
        bigPlayer.currentTime = 0.0
        bigPlayer.play()
    }

    override fun release() {
        tickPlayer.stop()
        bigPlayer.stop()
    }
}

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
actual fun rememberSounds(): Sounds? {
    val sounds by produceState<Sounds?>(null) {
        value = runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, error = null)
            IosSounds(Res.readBytes("files/sounds/celebrate_tick.wav"), Res.readBytes("files/sounds/celebrate_big.wav"))
        }.getOrNull()
    }
    return sounds
}

/** A GIF's frames and how long each shows, in milliseconds. */
private class GifFrames(val images: List<ImageBitmap>, val durations: List<Int>)

private fun decodeGif(bytes: ByteArray): GifFrames? = runCatching {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val images = (0 until codec.frameCount.coerceAtLeast(1)).map { frame ->
        val bitmap = Bitmap()
        bitmap.allocPixels(codec.imageInfo)
        codec.readPixels(bitmap, frame)
        bitmap.asComposeImageBitmap()
    }
    val durations = codec.framesInfo.map { it.duration }
    GifFrames(images, if (durations.size == images.size) durations else List(images.size) { 100 })
}.getOrNull()

@Composable
actual fun CelebrationGif(celebration: String, modifier: Modifier) {
    val phone = LocalPhone.current
    val frames by produceState<GifFrames?>(null, celebration) { value = celebrationBytes(phone, celebration)?.let(::decodeGif) }
    val shown = frames
    if (shown == null || shown.images.isEmpty()) {
        Box(modifier)
        return
    }
    var index by remember(shown) { mutableIntStateOf(0) }
    LaunchedEffect(shown) {
        if (shown.images.size > 1) {
            while (true) {
                delay(shown.durations[index].coerceAtLeast(20).toLong())
                index = (index + 1) % shown.images.size
            }
        }
    }
    Image(shown.images[index], contentDescription = "Celebration GIF", modifier = modifier, contentScale = ContentScale.Fit)
}

/** Voice needs Checklist AI, which is Android only. */
@Composable
actual fun rememberVoice(onHeard: (String) -> Unit, onProblem: (String) -> Unit): (() -> Unit)? = null
