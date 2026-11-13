package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import org.jetbrains.compose.resources.Font
import io.github.randomtools3377.checklist.ui.resources.Res
import io.github.randomtools3377.checklist.ui.resources.atkinson_bold
import io.github.randomtools3377.checklist.ui.resources.atkinson_regular
import io.github.randomtools3377.checklist.ui.resources.lora_bold
import io.github.randomtools3377.checklist.ui.resources.lora_regular
import io.github.randomtools3377.checklist.ui.resources.nunito_bold
import io.github.randomtools3377.checklist.ui.resources.nunito_regular
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Brightness
import io.github.randomtools3377.checklist.core.Colors
import io.github.randomtools3377.checklist.core.Schemes
import io.github.randomtools3377.checklist.core.SectionChoice
import io.github.randomtools3377.checklist.core.Sections
import io.github.randomtools3377.checklist.core.TabChoice

/** Light, dark, following the phone, or pure black. */
enum class ThemeMode(val label: String) { LIGHT("Light"), DARK("Dark"), PHONE("Phone"), BLACK("Black") }

/** How roomy goal cards are. */
enum class CardSize(val label: String, val padding: Dp, val icon: TextUnit, val tile: Dp) {
    COMPACT("Compact", 8.dp, 24.sp, 120.dp),
    COMFORTABLE("Comfortable", 12.dp, 30.sp, 150.dp),
    LARGE("Large", 16.dp, 38.sp, 180.dp),
}

/** The app's own text size, on top of the phone's. */
enum class TextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.9f),
    NORMAL("Normal", 1f),
    LARGE("Large", 1.15f),
    LARGER("Larger", 1.3f),
}

/** The app's font: the phone's own, or one of three that come with the app. */
enum class FontChoice(val label: String) { PHONE("Phone's"), ROUNDED("Rounded"), CLEAR("Easy to read"), SERIF("Serif") }

/** How much confetti a celebration throws. */
enum class ConfettiAmount(val label: String, val amount: Float) { NONE("None", 0f), SOME("Some", 1f), LOTS("Lots", 2.5f) }

/** Celebration settings: sound, buzz, how much confetti, and a GIF for every goal without its own. */
data class CelebrationStyle(
    val sound: Boolean = false,
    val buzz: Boolean = true,
    val confetti: ConfettiAmount = ConfettiAmount.SOME,
    /** A built-in GIF name (see Celebration in the shared code), or null for the usual burst. */
    val defaultGif: String? = null,
)

/** Today as a list of cards, or a grid of big tiles. */
enum class TodayLayout(val label: String) { LIST("List"), GRID("Grid") }

/** What is behind Today: nothing, a gradient of the app's colour, or the user's own photo, dimmed. */
enum class Backdrop(val label: String) { NONE("None"), GRADIENT("Gradient"), PHOTO("My photo") }

/** How the app looks, from Settings → Appearance. */
data class Appearance(
    val theme: ThemeMode = ThemeMode.PHONE,
    /** The colour the app is built from. Null matches the wallpaper where the phone can (Android 12 and later). */
    val color: Int? = null,
    /** Colours picked lately, newest first, offered again in the colour picker. */
    val recent: List<Int> = emptyList(),
    val textSize: TextSize = TextSize.NORMAL,
    val cardSize: CardSize = CardSize.COMFORTABLE,
    val layout: TodayLayout = TodayLayout.LIST,
    val backdrop: Backdrop = Backdrop.NONE,
    /** How much the photo is faded toward the background colour, so text on it stays readable, in percent. */
    val dim: Int = DEFAULT_DIM,
    /** Changes when a new photo is picked, so it is read again. */
    val photoStamp: String = "",
    /** Today's sections in the user's order, each shown or hidden. */
    val sections: List<SectionChoice> = Sections.DEFAULT,
    /** Which tabs show, and the one the app opens on. */
    val tabs: TabChoice = TabChoice(),
    val font: FontChoice = FontChoice.PHONE,
    val celebrate: CelebrationStyle = CelebrationStyle(),
    val icon: io.github.randomtools3377.checklist.AppIcon = io.github.randomtools3377.checklist.AppIcon.GARDEN,
    val widgetStyle: io.github.randomtools3377.checklist.widget.WidgetStyle = io.github.randomtools3377.checklist.widget.WidgetStyle.SOLID,
    /** Blue for good and vermillion for bad, apart for every common kind of colour blindness. */
    val colourBlind: Boolean = false,
    /** "Less motion": the user's choice, or null while the device's own setting decides (see LessMotion in the rules). */
    val lessMotion: Boolean? = null,
    /** Whether sliding a goal's card to the right on Today ticks it. */
    val swipeTick: Boolean = true,
    /** False until the settings have been read, so the first tab isn't picked from the defaults. */
    val loaded: Boolean = false,
) {
    companion object {
        const val DEFAULT_DIM = 70
        const val MIN_DIM = 40
        const val MAX_DIM = 90
    }
}

/** How roomy goal cards are, from Settings → Appearance. */
val LocalCardSize = staticCompositionLocalOf { CardSize.COMFORTABLE }


/** The brightness the app is showing now, for colours worked out by hand (like a goal's strip). */
val LocalBrightness = staticCompositionLocalOf { Brightness.LIGHT }

/** Whether the colour-blind friendly look is on (Settings → Appearance). */
val LocalColourBlind = staticCompositionLocalOf { false }

/** The colour for good things, like points earned or being on time: the app's colour, or blue in the colour-blind friendly look. */
@Composable
fun goodColor(): Color =
    if (LocalColourBlind.current) Color(io.github.randomtools3377.checklist.core.ColourBlind.good(LocalBrightness.current)) else MaterialTheme.colorScheme.primary

/** The colour-blind friendly warning colours: vermillion in place of red. */
private fun ColorScheme.colourBlind(brightness: Brightness): ColorScheme {
    val (error, onError, container, onContainer) = io.github.randomtools3377.checklist.core.ColourBlind.warnings(brightness)
    return copy(error = Color(error), onError = Color(onError), errorContainer = Color(container), onErrorContainer = Color(onContainer))
}

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

/** The fonts that come with the app (see fonts-NOTICE.txt). */
@Composable
private fun fontFamily(font: FontChoice): FontFamily? = when (font) {
    FontChoice.PHONE -> null
    FontChoice.ROUNDED -> FontFamily(Font(Res.font.nunito_regular), Font(Res.font.nunito_bold, FontWeight.Bold))
    FontChoice.CLEAR -> FontFamily(Font(Res.font.atkinson_regular), Font(Res.font.atkinson_bold, FontWeight.Bold))
    FontChoice.SERIF -> FontFamily(Font(Res.font.lora_regular), Font(Res.font.lora_bold, FontWeight.Bold))
}

/** Every text style in the chosen font. */
private fun typographyOf(family: FontFamily?): Typography {
    val base = Typography()
    if (family == null) return base
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
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
    val chosen = appearance.color
    val wallpaper = if (chosen == null) wallpaperColors(dark = brightness != Brightness.LIGHT) else null
    val built = when {
        wallpaper != null && brightness == Brightness.BLACK -> wallpaper.onBlack()
        wallpaper != null -> wallpaper
        else -> schemeOf(chosen ?: Colors.GARDEN, brightness)
    }
    val colors = if (appearance.colourBlind) built.colourBlind(brightness) else built
    val density = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBrightness provides brightness,
        LocalColourBlind provides appearance.colourBlind,
        LocalCardSize provides appearance.cardSize,
        LocalDensity provides Density(density.density, density.fontScale * appearance.textSize.scale),
    ) {
        MaterialTheme(colorScheme = colors, typography = typographyOf(fontFamily(appearance.font)), content = content)
    }
}
