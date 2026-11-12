package io.github.randomtools3377.checklist.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * The parts of the screens each phone does its own way: dates in the phone's style, the
 * wallpaper's colours, the back button, the system dialogs for files and photos, permissions, the
 * phone's lock, sounds, GIFs and voice. Android's are in androidMain, the iPhone's in iosMain,
 * and the Windows app's in jvmMain (which the jvm tests use too).
 */

/** "Wed 11 Nov", in the phone's language. */
expect fun shortDate(date: LocalDate): String

/** "Wednesday 11 November", in the phone's language. */
expect fun longDate(date: LocalDate): String

/** "6:00 PM" or "18:00", as the phone is set. */
expect fun timeText(time: LocalTime): String

/** Whether the phone shows times on a 24-hour clock. */
@Composable
expect fun is24HourClock(): Boolean

/** Whether the phone can give the app its wallpaper's colours. */
expect val canMatchWallpaper: Boolean

/** The wallpaper's colours, light or dark, where the phone has them. */
@Composable
expect fun wallpaperColors(dark: Boolean): ColorScheme?

/** Whether this phone can draw [emoji] (newer emoji are left out on older phones). */
expect fun canShowEmoji(emoji: String): Boolean

/** The symbol of the phone's own currency, like "$" or "£", or empty if it has none. */
expect fun moneySymbol(): String

/** The phone's back button or gesture, while [enabled]. Phones without one ignore it. */
@Composable
expect fun BackButton(enabled: Boolean = true, onBack: () -> Unit)

/** Opens the phone's dialog to save a new file of [mimeType]; it is given the name to suggest. */
@Composable
expect fun rememberSaveFile(mimeType: String, onPicked: (PickedFile?) -> Unit): (suggestedName: String) -> Unit

/** Opens the phone's dialog to choose a file of one of [mimeTypes]. */
@Composable
expect fun rememberOpenFile(mimeTypes: List<String>, onPicked: (PickedFile?) -> Unit): () -> Unit

/** Opens the phone's dialog to choose a folder (where [Phone.canPickFolder]). */
@Composable
expect fun rememberPickFolder(onPicked: (PickedFile?) -> Unit): () -> Unit

/** Opens the phone's photo picker, for one photo, or one GIF if [gifOnly]. No storage permission. */
@Composable
expect fun rememberPickImage(gifOnly: Boolean, onPicked: (PickedFile?) -> Unit): () -> Unit

/** What the app may ask the phone for. */
enum class Ask { NOTIFICATIONS, CALENDAR }

/** Asks the phone for [what]; [onAnswer] says whether it is allowed now. */
@Composable
expect fun rememberAsk(what: Ask, onAnswer: (Boolean) -> Unit): () -> Unit

/** Unlocks with the phone's own lock (fingerprint, face, PIN), then calls [MainViewModel.markUnlocked]. */
@Composable
expect fun rememberUnlock(vm: MainViewModel): () -> Unit

/** The two celebration sounds, and the user's own in their place. */
interface Sounds {
    fun tick()
    fun big()

    /**
     * Plays a sound the user picked, by its file name in the app's own storage, in place of the
     * app's small sound or its [big] one. One of these plays at a time, and if it can't be
     * played the app's own sound plays instead.
     */
    fun own(file: String, big: Boolean) {
        if (big) this.big() else tick()
    }

    fun release()
}

/** The celebration sounds, ready to play; null where the phone can't play them yet. */
@Composable
expect fun rememberSounds(): Sounds?

/** Plays a celebration GIF, over and over while it is on screen. */
@Composable
expect fun CelebrationGif(celebration: String, modifier: Modifier = Modifier)

/**
 * Listens for one voice command (through Checklist AI on Android): [onHeard] gets the words,
 * [onProblem] why there are none. Null where the phone has no voice.
 */
@Composable
expect fun rememberVoice(onHeard: (String) -> Unit, onProblem: (String) -> Unit): (() -> Unit)?
