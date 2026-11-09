package io.github.randomtools3377.checklist.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.unit.ColorProvider
import io.github.randomtools3377.checklist.core.Brightness
import io.github.randomtools3377.checklist.core.Schemes

/** How the widgets look (Settings → Appearance): solid in the app's colours, or see-through over the wallpaper. */
enum class WidgetStyle(val label: String) { SOLID("Solid"), SEE_THROUGH("See-through") }

/** The widget's style and the app's colour (null for the phone's), read when it is drawn. */
data class WidgetLook(val style: WidgetStyle = WidgetStyle.SOLID, val color: Int? = null)

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
    if (look.style == WidgetStyle.SEE_THROUGH) {
        return WidgetColors(
            widgetBackground = fixed(0x61000000), onSurface = fixed(0xFFFFFFFF), onSurfaceVariant = fixed(0xD9FFFFFF),
            primary = fixed(0xFFFFFFFF), primaryContainer = fixed(0x33FFFFFF), secondaryContainer = fixed(0x40FFFFFF),
            error = fixed(0xFFFFB4AB),
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
        )
    }
    val t = GlanceTheme.colors
    return WidgetColors(t.widgetBackground, t.onSurface, t.onSurfaceVariant, t.primary, t.primaryContainer, t.secondaryContainer, t.error)
}
