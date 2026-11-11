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
import io.github.randomtools3377.checklist.desktop.DesktopPhone
import io.github.randomtools3377.checklist.desktop.DesktopReminders
import io.github.randomtools3377.checklist.ui.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.awt.FileDialog
import java.awt.GraphicsEnvironment
import java.io.ByteArrayInputStream
import java.io.File
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/*
 * The computer's side of the screens (the Windows app, see the desktop module). The jvm tests use
 * the same code; nothing here runs unless a screen asks for it.
 */

actual fun shortDate(date: LocalDate): String = date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
actual fun longDate(date: LocalDate): String = date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
actual fun timeText(time: LocalTime): String = time.toJavaLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** Whether the computer's short time pattern counts hours to 23 rather than showing am and pm. */
internal fun usesTwentyFourHours(locale: Locale = Locale.getDefault(Locale.Category.FORMAT)): Boolean {
    val pattern = runCatching {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)
    }.getOrNull() ?: return false
    // Quoted words are text, not pattern letters.
    return pattern.replace(Regex("'[^']*'"), "").any { it == 'H' || it == 'k' }
}

@Composable
actual fun is24HourClock(): Boolean = remember { usesTwentyFourHours() }

actual val canMatchWallpaper: Boolean get() = false

@Composable
actual fun wallpaperColors(dark: Boolean): ColorScheme? = null

actual fun canShowEmoji(emoji: String): Boolean = true

actual fun moneySymbol(): String = runCatching { java.util.Currency.getInstance(java.util.Locale.getDefault()).symbol }.getOrDefault("")

/** A computer has no back button; screens have their own back arrow, as on the iPhone. */
@Composable
actual fun BackButton(enabled: Boolean, onBack: () -> Unit) {}

private val onWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/**
 * The computer's own file dialog, over the app's window: the file picked, as a full path, or
 * null if the user cancelled. [extensions] limits what it shows, where the computer allows.
 */
private fun chooseFile(phone: Phone, title: String, mode: Int, suggestedName: String? = null, extensions: List<String> = emptyList()): PickedFile? {
    if (GraphicsEnvironment.isHeadless()) return null
    return runCatching {
        val dialog = FileDialog((phone as? DesktopPhone)?.window, title, mode)
        if (extensions.isNotEmpty()) {
            dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        }
        when {
            suggestedName != null -> dialog.file = suggestedName
            // Windows ignores the filter above; a pattern in the name box does the same there.
            extensions.isNotEmpty() && onWindows -> dialog.file = extensions.joinToString(";") { "*.$it" }
        }
        dialog.isVisible = true
        val name = dialog.file
        val folder = dialog.directory
        if (name == null || folder == null) null else File(folder, name).absolutePath
    }.getOrNull()
}

@Composable
actual fun rememberSaveFile(mimeType: String, onPicked: (PickedFile?) -> Unit): (suggestedName: String) -> Unit {
    val phone = LocalPhone.current
    return { name -> onPicked(chooseFile(phone, "Save", FileDialog.SAVE, suggestedName = name)) }
}

@Composable
actual fun rememberOpenFile(mimeTypes: List<String>, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val phone = LocalPhone.current
    return { onPicked(chooseFile(phone, "Open", FileDialog.LOAD)) }
}

/** The computer app keeps no automatic backups in a folder. */
@Composable
actual fun rememberPickFolder(onPicked: (PickedFile?) -> Unit): () -> Unit = { onPicked(null) }

private val PhotoExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

/** One photo or GIF from the computer, picked with its file dialog. */
@Composable
actual fun rememberPickImage(gifOnly: Boolean, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val phone = LocalPhone.current
    return {
        val path = if (gifOnly) {
            chooseFile(phone, "Choose a GIF", FileDialog.LOAD, extensions = listOf("gif"))
        } else {
            chooseFile(phone, "Choose a photo", FileDialog.LOAD, extensions = PhotoExtensions)
        }
        onPicked(path)
    }
}

/** Reminders show while the app is open, wherever the computer has a notification area. */
@Composable
actual fun rememberAsk(what: Ask, onAnswer: (Boolean) -> Unit): () -> Unit = {
    when (what) {
        Ask.NOTIFICATIONS -> onAnswer(DesktopReminders.canShow())
        Ask.CALENDAR -> onAnswer(false)
    }
}

/**
 * A computer can't be asked for its own lock from here, so the privacy lock simply opens, with a
 * note saying why, as on a phone with no screen lock.
 */
@Composable
actual fun rememberUnlock(vm: MainViewModel): () -> Unit {
    val phone = LocalPhone.current
    return {
        phone.toast("This computer can't ask for its own lock, so the privacy lock opens here.")
        vm.markUnlocked()
    }
}

/** The two celebration sounds, each ready to play from the start. */
private class DesktopSounds(tick: ByteArray, big: ByteArray) : Sounds {
    private val tickClip = clip(tick)
    private val bigClip = clip(big)

    private fun clip(bytes: ByteArray): Clip =
        AudioSystem.getClip().apply { open(AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))) }

    private fun play(clip: Clip) {
        clip.stop()
        clip.framePosition = 0
        clip.start()
    }

    override fun tick() = play(tickClip)

    override fun big() = play(bigClip)

    override fun release() {
        tickClip.close()
        bigClip.close()
    }
}

/** The celebration sounds; none on a computer with no sound output. */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
actual fun rememberSounds(): Sounds? {
    val sounds by produceState<Sounds?>(null) {
        val tick = runCatching { Res.readBytes("files/sounds/celebrate_tick.wav") }.getOrNull()
        val big = runCatching { Res.readBytes("files/sounds/celebrate_big.wav") }.getOrNull()
        if (tick != null && big != null) {
            value = withContext(Dispatchers.IO) { runCatching { DesktopSounds(tick, big) }.getOrNull() }
        }
    }
    return sounds
}

/** A GIF's frames and how long each shows, in milliseconds. */
private class GifFrames(val images: List<ImageBitmap>, val durations: List<Int>)

/** Decoded with Skia, as on the iPhone. */
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
    val frames by produceState<GifFrames?>(null, celebration) {
        value = celebrationBytes(phone, celebration)?.let { bytes -> withContext(Dispatchers.Default) { decodeGif(bytes) } }
    }
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
