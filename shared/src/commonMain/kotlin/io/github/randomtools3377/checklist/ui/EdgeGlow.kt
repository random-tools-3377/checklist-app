package io.github.randomtools3377.checklist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.CelebrationSounds
import io.github.randomtools3377.checklist.core.EdgeGlow
import io.github.randomtools3377.checklist.core.GlowSize
import kotlinx.coroutines.launch

/**
 * More celebration settings, from Settings → Appearance: the glow round the screen's edges, in
 * the app and (on Android) away from it, and the user's own sounds for each kind of celebration
 * and for goals of their own.
 */
data class CelebrationExtras(
    val glow: Boolean = true,
    val glowAway: Boolean = false,
    val sounds: Map<CelebrationSounds.Kind, String> = emptyMap(),
    val goalSounds: Map<Long, String> = emptyMap(),
) {
    companion object {
        /** From the settings as saved: the glow is on unless turned off, the rest is off or empty until chosen. */
        fun of(glow: String?, glowAway: String?, sounds: Map<CelebrationSounds.Kind, String?>, goalSounds: String?) = CelebrationExtras(
            glow = glow != "0",
            glowAway = glowAway == "1",
            sounds = sounds.mapNotNull { (kind, value) -> CelebrationSounds.file(value)?.let { kind to it } }.toMap(),
            goalSounds = CelebrationSounds.goals(goalSounds),
        )
    }
}

/** One glow round the edges: how big the win was, and the goal's colour if it has one. */
internal data class Glow(val id: Long, val size: GlowSize, val goalColor: Int?)

/**
 * A soft glow that runs round the edges of the screen and fades. It only draws: touches go
 * straight through to what is under it.
 */
@Composable
internal fun EdgeGlowEffect(glow: Glow, onEnd: () -> Unit) {
    val spec = remember(glow.size) { EdgeGlow.spec(glow.size) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = spec.millis, easing = LinearEasing))
        onEnd()
    }
    val color = glow.goalColor
        ?.let { Color(io.github.randomtools3377.checklist.core.Schemes.goalStrip(it, LocalBrightness.current)) }
        ?: MaterialTheme.colorScheme.primary
    val t = progress.value
    Canvas(Modifier.fillMaxSize().testTag("edge-glow")) {
        val strength = EdgeGlow.strength(t) * spec.alpha
        if (strength <= 0f) return@Canvas
        val head = EdgeGlow.head(spec, t)
        // Round the screen from its middle: brightest where the bright part is, fading behind it.
        val stops = List(GLOW_STOPS + 1) { i ->
            val at = i.toFloat() / GLOW_STOPS
            color.copy(alpha = (strength * EdgeGlow.brightness(at, head)).coerceIn(0f, 1f))
        }
        val brush = Brush.sweepGradient(stops, center = Offset(size.width / 2, size.height / 2))
        val width = spec.width.dp.toPx()
        // Wide and faint under narrow and bright, so it looks soft. Half of each line is off the screen.
        for ((times, alpha) in GLOW_LAYERS) {
            drawRect(brush = brush, topLeft = Offset.Zero, size = Size(size.width, size.height), alpha = alpha, style = Stroke(width = width * times))
        }
    }
}

private const val GLOW_STOPS = 48
private val GLOW_LAYERS = listOf(6f to 0.18f, 3.5f to 0.3f, 2f to 0.55f, 1f to 1f)

/** The celebration sounds for trying one out in a settings row; let go of when the row leaves the screen. */
@Composable
internal fun rememberTrySounds(): Sounds? {
    val sounds = rememberSounds()
    DisposableEffect(sounds) {
        onDispose { sounds?.release() }
    }
    return sounds
}

/**
 * One celebration's sound: the app's own, or a sound file the user picks with the phone's file
 * dialog, which hands over just that one file and needs no permission. ▶ plays it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SoundPicker(
    title: String,
    value: String?,
    big: Boolean,
    tag: String,
    sounds: Sounds?,
    modifier: Modifier = Modifier,
    onChange: (String?) -> Unit,
) {
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    val pick = rememberOpenFile(listOf("audio/*")) { file ->
        if (file == null) return@rememberOpenFile
        scope.launch {
            phone.addSound(file)
                .onSuccess { onChange(it) }
                .onFailure { phone.toast(it.message ?: CelebrationSounds.CANT_OPEN) }
        }
    }
    val own = CelebrationSounds.file(value)
    Column(modifier.fillMaxWidth().testTag("sound:$tag")) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (own != null) "Your own sound" else "The app's sound",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("sound:$tag:now"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sounds != null) {
                OutlinedButton(
                    onClick = { if (own != null) sounds.own(own, big) else if (big) sounds.big() else sounds.tick() },
                    modifier = Modifier.testTag("sound:$tag:play"),
                ) { Text("▶ Play") }
            }
            OutlinedButton(onClick = pick, modifier = Modifier.testTag("sound:$tag:pick")) { Text("Choose a sound…") }
            if (own != null) {
                TextButton(onClick = { onChange(null) }, modifier = Modifier.testTag("sound:$tag:reset")) { Text("Use the app's sound") }
            }
        }
    }
}

/** A goal's own celebration sound, in its editor, under its own GIF. */
@Composable
internal fun GoalSoundPicker(value: String?, onChange: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        SoundPicker("Sound when it's ticked", value, big = false, tag = "goal", sounds = rememberTrySounds(), onChange = onChange)
        Text(
            "It plays in place of the usual sound when celebration sound is on (Settings → Appearance). Your own sound (up to 5 MB) is copied into the app, and goes in your backup file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
