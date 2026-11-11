package io.github.randomtools3377.checklist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Confetti = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFC145), Color(0xFF4ECDC4), Color(0xFF5B8DEF),
    Color(0xFFB57EDC), Color(0xFF7BD389), Color(0xFFFF9F68),
)

/** A big celebration that fills the screen. */
sealed interface BigCelebration {
    data class ProjectFinished(val icon: String, val name: String, val points: Int, val gif: String? = null) : BigCelebration
    /** A reward bought; for a mystery reward, [name] is what came out of it. */
    data class Bought(val icon: String, val name: String, val mystery: Boolean = false) : BigCelebration

    /** A habit finished for its day, week, month or year, with its own GIF. */
    data class GoalFinished(val icon: String, val name: String, val points: Int, val gif: String) : BigCelebration
}

private data class Burst(val id: Long, val at: Offset, val label: String, val big: Boolean)

/**
 * The celebrations on top of the app. They scale with the win: a quick burst for a tick, a
 * bigger one for finishing a counted habit, the full screen for finishing a project, and its
 * own for buying a reward.
 */
@Stable
class Celebrations {
    private var nextId = 0L
    private val bursts = mutableStateListOf<Burst>()
    private var big by mutableStateOf<BigCelebration?>(null)

    /** Sound, buzz, how much confetti and a GIF for every goal, from Settings → Appearance. */
    var style by mutableStateOf(CelebrationStyle())

    /** The sounds, once the app has loaded them. */
    var sounds: Sounds? = null

    /** A burst at [at] (in window coordinates) showing [label], like "+10". */
    fun burst(at: Offset, label: String, big: Boolean = false) {
        bursts += Burst(nextId++, at, label, big)
        if (style.sound) sounds?.tick()
    }

    fun show(celebration: BigCelebration) {
        big = celebration
        if (style.sound) sounds?.big()
    }

    /** A buzz, unless buzzing is off. */
    fun buzz(haptics: androidx.compose.ui.hapticfeedback.HapticFeedback) {
        if (style.buzz) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }

    @Composable
    fun Overlay() {
        Box(Modifier.fillMaxSize()) {
            for (burst in bursts.toList()) {
                key(burst.id) {
                    BurstEffect(burst, style.confetti.amount) { bursts.remove(burst) }
                }
            }
            big?.let { celebration ->
                key(celebration) {
                    BigEffect(celebration, style.confetti.amount) { big = null }
                }
            }
        }
    }
}

private class Particle(val angle: Float, val distance: Float, val radius: Float, val color: Color)

@Composable
private fun BurstEffect(burst: Burst, amount: Float, onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val particles = remember {
        val count = ((if (burst.big) 36 else 16) * amount).toInt()
        List(count) {
            Particle(
                angle = (2 * PI * it / count).toFloat() + Random.nextFloat() * 0.4f,
                distance = (if (burst.big) 220f else 120f) * (0.6f + Random.nextFloat() * 0.6f),
                radius = (if (burst.big) 9f else 6f) * (0.6f + Random.nextFloat() * 0.8f),
                color = Confetti[it % Confetti.size],
            )
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = if (burst.big) 1100 else 700, easing = LinearEasing))
        onEnd()
    }
    val t = progress.value
    val eased = FastOutSlowInEasing.transform(t)
    Canvas(Modifier.fillMaxSize()) {
        for (p in particles) {
            val d = p.distance * eased
            drawCircle(
                color = p.color.copy(alpha = 1f - t),
                radius = p.radius * (1f - t * 0.5f),
                center = Offset(burst.at.x + cos(p.angle) * d, burst.at.y + sin(p.angle) * d),
            )
        }
    }
    Text(
        text = burst.label,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 1f - t),
        fontWeight = FontWeight.Bold,
        fontSize = if (burst.big) 30.sp else 22.sp,
        modifier = Modifier.offsetTo(burst.at, rise = 80f * eased),
    )
}

/** Places a small label centred above [at], raised by [rise] pixels. */
private fun Modifier.offsetTo(at: Offset, rise: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(
                    (at.x - placeable.width / 2f).toInt(),
                    (at.y - placeable.height * 1.5f - rise).toInt(),
                )
            }
        },
    )

private class Piece(val x: Float, val delay: Float, val speed: Float, val sway: Float, val spin: Float, val w: Float, val h: Float, val color: Color)

@Composable
private fun BigEffect(celebration: BigCelebration, amount: Float, onEnd: () -> Unit) {
    val fall = remember { Animatable(0f) }
    val pop = remember { Animatable(0.3f) }
    val pieces = remember {
        List(((if (celebration is BigCelebration.ProjectFinished) 120 else 60) * amount).toInt()) {
            Piece(
                x = Random.nextFloat(),
                delay = Random.nextFloat() * 0.35f,
                speed = 0.8f + Random.nextFloat() * 0.7f,
                sway = 20f + Random.nextFloat() * 40f,
                spin = Random.nextFloat() * 720f,
                w = 10f + Random.nextFloat() * 10f,
                h = 6f + Random.nextFloat() * 8f,
                color = Confetti[it % Confetti.size],
            )
        }
    }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        fall.animateTo(1f, tween(durationMillis = 3200, easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        // Closes by itself, or sooner with a tap.
        delay(if (celebration is BigCelebration.Bought) 3000L else 4500L)
        onEnd()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEnd)
            .testTag("celebration"),
        contentAlignment = Alignment.Center,
    ) {
        val t = fall.value
        Canvas(Modifier.fillMaxSize()) {
            for (p in pieces) {
                val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                if (local <= 0f) continue
                val y = -40f + (size.height + 80f) * local * p.speed
                val x = p.x * size.width + sin(local * 10f + p.x * 6f) * p.sway
                rotate(degrees = p.spin * local, pivot = Offset(x, y)) {
                    drawRect(color = p.color, topLeft = Offset(x - p.w / 2, y - p.h / 2), size = Size(p.w, p.h))
                }
            }
        }
        // Smaller on a short screen, like a phone on its side, so the words still fit.
        val gifSize = minOf(240.dp, screenHeight() * 0.4f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp).scale(pop.value),
        ) {
            when (celebration) {
                is BigCelebration.ProjectFinished -> {
                    val gif = celebration.gif
                    if (gif != null) {
                        CelebrationGif(gif, Modifier.size(gifSize).testTag("celebration-gif"))
                    } else {
                        Text(celebration.icon, fontSize = 96.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Project finished!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(celebration.name, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                    if (celebration.points > 0) {
                        Spacer(Modifier.height(16.dp))
                        Text("+" + pointsText(celebration.points), color = Color(0xFFFFC145), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
                is BigCelebration.GoalFinished -> {
                    CelebrationGif(celebration.gif, Modifier.size(gifSize).testTag("celebration-gif"))
                    Spacer(Modifier.height(16.dp))
                    Text("${celebration.icon} ${celebration.name}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (celebration.points > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text("+" + pointsText(celebration.points), color = Color(0xFFFFC145), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                is BigCelebration.Bought -> {
                    Text("🎁", fontSize = 72.sp)
                    Text(celebration.icon, fontSize = 72.sp)
                    Spacer(Modifier.height(16.dp))
                    if (celebration.mystery) {
                        Text("Out of the mystery…", color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                    }
                    Text("Enjoy it!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(celebration.name, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("bought-name"))
                    Spacer(Modifier.height(8.dp))
                    Text("It's waiting under Bought until you use it.", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
