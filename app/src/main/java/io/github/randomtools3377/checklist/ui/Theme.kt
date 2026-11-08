package io.github.randomtools3377.checklist.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF2E6B5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F0DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A5100),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF2C1600),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF96D4C3),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0F5144),
    onPrimaryContainer = Color(0xFFB2F0DF),
    secondary = Color(0xFFFFB870),
    secondaryContainer = Color(0xFF693C00),
    onSecondaryContainer = Color(0xFFFFDCBE),
)

/** Uses the phone's wallpaper colours on Android 12 and later. */
@Composable
fun ChecklistTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
