package io.github.randomtools3377.checklist.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.unit.ColorProvider
import io.github.randomtools3377.checklist.core.Brightness
import io.github.randomtools3377.checklist.core.ColourBlind
import io.github.randomtools3377.checklist.core.Schemes

/** The widget's style and the app's colour (null for the phone's), read when it is drawn. */
data class WidgetLook(val style: WidgetStyle = WidgetStyle.SOLID, val color: Int? = null, val colourBlind: Boolean = false)

val LocalWidgetLook = staticCompositionLocalOf { WidgetLook() }

/** The colours a widget draws with, by role. */
class WidgetColors(
    val widgetBackground: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val primary: ColorProvider,
    val primaryContainer: ColorProvider,
    val secondaryContainer: ColorProvider,
    val error: ColorProvider,
    /** Text on [primary], for the widgets' main buttons. */
    val onPrimary: ColorProvider,
)

private fun fixed(argb: Long) = ColorProvider(Color(argb))

private fun dayNight(day: Int, night: Int) = androidx.glance.color.ColorProvider(day = Color(day), night = Color(night))

/**
 * See-through: white text on a dark film over the wallpaper. Solid: the app's own colours if one
 * is chosen, light or dark with the phone, or the phone's wallpaper colours.
 */
@Composable
fun widgetColors(): WidgetColors {
    val look = LocalWidgetLook.current
    val colours = coloursFor(look)
    if (!look.colourBlind) return colours
    // Colour-blind friendly: vermillion in place of red.
    val bad = if (look.style == WidgetStyle.SEE_THROUGH) fixed(ColourBlind.bad(Brightness.DARK).toLong() and 0xFFFFFFFFL)
    else dayNight(ColourBlind.bad(Brightness.LIGHT), ColourBlind.bad(Brightness.DARK))
    return WidgetColors(
        colours.widgetBackground, colours.onSurface, colours.onSurfaceVariant, colours.primary,
        colours.primaryContainer, colours.secondaryContainer, bad, colours.onPrimary,
    )
}

@Composable
private fun coloursFor(look: WidgetLook): WidgetColors {
    if (look.style == WidgetStyle.SEE_THROUGH) {
        return WidgetColors(
            widgetBackground = fixed(0x61000000), onSurface = fixed(0xFFFFFFFF), onSurfaceVariant = fixed(0xD9FFFFFF),
            primary = fixed(0xFFFFFFFF), primaryContainer = fixed(0x33FFFFFF), secondaryContainer = fixed(0x40FFFFFF),
            error = fixed(0xFFFFB4AB),
            onPrimary = fixed(0xFF1B1B1F),
        )
    }
    val color = look.color
    if (color != null) {
        val light = Schemes.of(color, Brightness.LIGHT)
        val dark = Schemes.of(color, Brightness.DARK)
        return WidgetColors(
            widgetBackground = dayNight(light.surfaceContainer, dark.surfaceContainer),
            onSurface = dayNight(light.onSurface, dark.onSurface),
            onSurfaceVariant = dayNight(light.onSurfaceVariant, dark.onSurfaceVariant),
            primary = dayNight(light.primary, dark.primary),
            primaryContainer = dayNight(light.primaryContainer, dark.primaryContainer),
            secondaryContainer = dayNight(light.secondaryContainer, dark.secondaryContainer),
            error = dayNight(light.error, dark.error),
            onPrimary = dayNight(light.onPrimary, dark.onPrimary),
        )
    }
    val t = GlanceTheme.colors
    return WidgetColors(t.widgetBackground, t.onSurface, t.onSurfaceVariant, t.primary, t.primaryContainer, t.secondaryContainer, t.error, t.onPrimary)
}
