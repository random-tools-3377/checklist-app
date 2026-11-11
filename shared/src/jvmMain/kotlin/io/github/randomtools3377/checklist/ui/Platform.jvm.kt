package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Stand-ins, only for building and testing the shared code on any machine.

actual fun shortDate(date: LocalDate): String = date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
actual fun longDate(date: LocalDate): String = date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
actual fun timeText(time: LocalTime): String = time.toJavaLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
actual fun is24HourClock(): Boolean = false

actual val canMatchWallpaper: Boolean get() = false

@Composable
actual fun wallpaperColors(dark: Boolean): ColorScheme? = null

actual fun canShowEmoji(emoji: String): Boolean = true

@Composable
actual fun BackButton(enabled: Boolean, onBack: () -> Unit) {}

@Composable
actual fun rememberSaveFile(mimeType: String, onPicked: (PickedFile?) -> Unit): (suggestedName: String) -> Unit = { onPicked(null) }

@Composable
actual fun rememberOpenFile(mimeTypes: List<String>, onPicked: (PickedFile?) -> Unit): () -> Unit = { onPicked(null) }

@Composable
actual fun rememberPickFolder(onPicked: (PickedFile?) -> Unit): () -> Unit = { onPicked(null) }

@Composable
actual fun rememberPickImage(gifOnly: Boolean, onPicked: (PickedFile?) -> Unit): () -> Unit = { onPicked(null) }

@Composable
actual fun rememberAsk(what: Ask, onAnswer: (Boolean) -> Unit): () -> Unit = { onAnswer(true) }

@Composable
actual fun rememberUnlock(vm: MainViewModel): () -> Unit = { vm.markUnlocked() }

@Composable
actual fun rememberSounds(): Sounds? = null

@Composable
actual fun CelebrationGif(celebration: String, modifier: Modifier) = Box(modifier)

@Composable
actual fun rememberVoice(onHeard: (String) -> Unit, onProblem: (String) -> Unit): (() -> Unit)? = null
