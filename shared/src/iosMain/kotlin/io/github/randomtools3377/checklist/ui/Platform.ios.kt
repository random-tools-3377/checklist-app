@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

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

actual fun canShowEmoji(emoji: String): Boolean = true

/** The iPhone has no back button; screens have their own back arrow. */
@Composable
actual fun BackButton(enabled: Boolean, onBack: () -> Unit) {}

// The system dialogs, the lock, sounds, GIFs and permissions come with the iPhone app itself
// (stage 3 of the iPhone work); until then these do nothing.

@Composable
actual fun rememberSaveFile(mimeType: String, onPicked: (PickedFile?) -> Unit): (suggestedName: String) -> Unit = { onPicked(null) }

@Composable
actual fun rememberOpenFile(mimeTypes: List<String>, onPicked: (PickedFile?) -> Unit): () -> Unit = { onPicked(null) }

@Composable
actual fun rememberPickFolder(onPicked: (PickedFile?) -> Unit): () -> Unit = { onPicked(null) }

@Composable
actual fun rememberPickImage(gifOnly: Boolean, onPicked: (PickedFile?) -> Unit): () -> Unit = { onPicked(null) }

@Composable
actual fun rememberAsk(what: Ask, onAnswer: (Boolean) -> Unit): () -> Unit = { onAnswer(false) }

@Composable
actual fun rememberUnlock(vm: MainViewModel): () -> Unit = { vm.markUnlocked() }

@Composable
actual fun rememberSounds(): Sounds? = null

@Composable
actual fun CelebrationGif(celebration: String, modifier: Modifier) = Box(modifier)

@Composable
actual fun rememberVoice(onHeard: (String) -> Unit, onProblem: (String) -> Unit): (() -> Unit)? = null
