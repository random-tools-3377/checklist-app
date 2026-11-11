package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/*
 * Tablets and phones on their side. A wide window puts the tabs in a rail down the side and keeps
 * each screen to a readable width in the middle; a short one lays the focus timer out sideways
 * and makes the big celebrations smaller so they fit.
 */

/** The widest a screen's content grows: about a phone and a half. */
val READABLE_WIDTH: Dp = 720.dp

/**
 * The size of the app's whole window, measured once at the top of the app. It is the window,
 * system bars included, as Material's own size classes use, so a small phone on its side
 * counts as wide the same way on every version of Android.
 */
val LocalWindowSize = compositionLocalOf<DpSize?> { null }

/** Measures the window for [isWide], [isShort] and [screenHeight]. */
@Composable
fun WindowSized(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindowSize provides DpSize(maxWidth, maxHeight)) { content() }
    }
}

/** Wide enough for the tabs to go down the side: a tablet, or a phone on its side. */
@Composable
fun isWide(): Boolean = (LocalWindowSize.current?.width ?: 0.dp) >= 600.dp

/** Short, like a phone on its side: big things are laid out side by side to fit. */
@Composable
fun isShort(): Boolean = screenHeight() < 480.dp

/** The window's height, to size big pictures so they fit. */
@Composable
fun screenHeight(): Dp = LocalWindowSize.current?.height ?: 640.dp

/** Holds content to [READABLE_WIDTH], in the middle of anything wider. */
fun Modifier.readable(max: Dp = READABLE_WIDTH): Modifier =
    this.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()

/** A whole screen with its own top bar, kept to a readable width on a wide window. */
@Composable
fun Page(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).readable(), content = content)
}
