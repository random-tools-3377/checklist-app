package io.github.randomtools3377.checklist.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.randomtools3377.checklist.core.Brightness
import io.github.randomtools3377.checklist.core.Colors
import io.github.randomtools3377.checklist.core.Schemes

/** Light, dark, following the phone, or pure black. */
enum class ThemeMode(val label: String) { LIGHT("Light"), DARK("Dark"), PHONE("Phone"), BLACK("Black") }

/** How the app looks, from Settings → Appearance. */
data class Appearance(
    val theme: ThemeMode = ThemeMode.PHONE,
    /** The colour the app is built from. Null matches the wallpaper where the phone can (Android 12 and later). */
    val color: Int? = null,
    /** Colours picked lately, newest first, offered again in the colour picker. */
    val recent: List<Int> = emptyList(),
)

/** Whether this phone can match the app's colours to the wallpaper. */
val canMatchWallpaper: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The brightness the app is showing now, for colours worked out by hand (like a goal's strip). */
val LocalBrightness = staticCompositionLocalOf { Brightness.LIGHT }

private fun Int.color() = Color(this)

private fun schemeOf(seed: Int, brightness: Brightness): ColorScheme {
    val s = Schemes.of(seed, brightness)
    val base = if (brightness == Brightness.LIGHT) androidx.compose.material3.lightColorScheme() else androidx.compose.material3.darkColorScheme()
    return base.copy(
        primary = s.primary.color(), onPrimary = s.onPrimary.color(), primaryContainer = s.primaryContainer.color(),
        onPrimaryContainer = s.onPrimaryContainer.color(), inversePrimary = s.inversePrimary.color(),
        secondary = s.secondary.color(), onSecondary = s.onSecondary.color(), secondaryContainer = s.secondaryContainer.color(),
        onSecondaryContainer = s.onSecondaryContainer.color(),
        tertiary = s.tertiary.color(), onTertiary = s.onTertiary.color(), tertiaryContainer = s.tertiaryContainer.color(),
        onTertiaryContainer = s.onTertiaryContainer.color(),
        error = s.error.color(), onError = s.onError.color(), errorContainer = s.errorContainer.color(), onErrorContainer = s.onErrorContainer.color(),
        background = s.background.color(), onBackground = s.onBackground.color(),
        surface = s.surface.color(), onSurface = s.onSurface.color(), surfaceVariant = s.surfaceVariant.color(),
        onSurfaceVariant = s.onSurfaceVariant.color(), surfaceTint = s.primary.color(),
        inverseSurface = s.inverseSurface.color(), inverseOnSurface = s.inverseOnSurface.color(),
        outline = s.outline.color(), outlineVariant = s.outlineVariant.color(), scrim = s.scrim.color(),
        surfaceDim = s.surfaceDim.color(), surfaceBright = s.surfaceBright.color(),
        surfaceContainerLowest = s.surfaceContainerLowest.color(), surfaceContainerLow = s.surfaceContainerLow.color(),
        surfaceContainer = s.surfaceContainer.color(), surfaceContainerHigh = s.surfaceContainerHigh.color(),
        surfaceContainerHighest = s.surfaceContainerHighest.color(),
    )
}

/** The wallpaper's dark colours on pure black. */
private fun ColorScheme.onBlack(): ColorScheme = copy(
    background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0B0B0B), surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818), surfaceContainerHighest = Color(0xFF202020),
)

/**
 * The app's colours: built from the chosen colour, or matched to the wallpaper on Android 12 and
 * later when no colour is chosen. Pure black is the dark colours on a black background.
 */
@Composable
fun ChecklistTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val brightness = when (appearance.theme) {
        ThemeMode.LIGHT -> Brightness.LIGHT
        ThemeMode.DARK -> Brightness.DARK
        ThemeMode.BLACK -> Brightness.BLACK
        ThemeMode.PHONE -> if (systemDark) Brightness.DARK else Brightness.LIGHT
    }
    val context = LocalContext.current
    val chosen = appearance.color
    val colors = if (chosen == null && canMatchWallpaper) {
        when (brightness) {
            Brightness.LIGHT -> dynamicLightColorScheme(context)
            Brightness.DARK -> dynamicDarkColorScheme(context)
            Brightness.BLACK -> dynamicDarkColorScheme(context).onBlack()
        }
    } else {
        schemeOf(chosen ?: Colors.GARDEN, brightness)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBrightness provides brightness) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
