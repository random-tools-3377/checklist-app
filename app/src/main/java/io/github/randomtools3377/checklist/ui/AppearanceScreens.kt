@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.checklist.core.Colors
import io.github.randomtools3377.checklist.core.Schemes

private val Rainbow = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** Settings → Appearance: light, dark, phone or black, and the colour the app is built from. */
@Composable
fun AppearanceScreen(appearance: Appearance, vm: MainViewModel, onAnyColour: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Theme", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = appearance.theme == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        modifier = Modifier.testTag("theme:${mode.name}"),
                    ) { Text(mode.label) }
                }
            }
            Text(
                "Phone follows the phone's own dark mode. Black is dark on a pure black background, which saves power on many screens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Text("Colour", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Colors.PRESETS.forEach { (name, color) ->
                    Swatch(name, appearance.color == color, Modifier.background(Color(color), CircleShape)) { vm.setColor(color) }
                }
                appearance.color?.takeIf { chosen -> Colors.PRESETS.none { it.second == chosen } }?.let { own ->
                    Swatch("Yours", true, Modifier.background(Color(own), CircleShape)) { onAnyColour() }
                }
                if (canMatchWallpaper) {
                    Swatch("Wallpaper", appearance.color == null, Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), inside = "🖼") {
                        vm.setColor(null)
                    }
                }
                Swatch("Any colour…", false, Modifier.background(Brush.sweepGradient(Rainbow), CircleShape)) { onAnyColour() }
            }
            Text(
                "The whole app takes its colours from the one you pick, and keeps text readable." +
                    if (canMatchWallpaper) " Wallpaper matches the colours of your phone's wallpaper." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Swatch(label: String, selected: Boolean, fill: Modifier, inside: String? = null, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("colour:$label")
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(5.dp) else Modifier.padding(5.dp))
                .then(fill),
        ) { if (inside != null) Text(inside, fontSize = 20.sp) }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/**
 * Any colour: a colour square (more colourful to the right, darker towards the bottom), a hue
 * slider and a hex code box, plus the colours picked lately.
 */
@Composable
fun ColorPicker(color: Int, recent: List<Int>, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Hue, saturation and value are kept apart, so dragging to grey or black keeps the hue.
    var hsv by remember { mutableStateOf(Colors.toHsv(color)) }
    var hexText by remember { mutableStateOf(Colors.hex(color)) }
    if (Colors.fromHsv(hsv.first, hsv.second, hsv.third) != color && Colors.parseHex(hexText) != color) {
        hsv = Colors.toHsv(color)
        hexText = Colors.hex(color)
    }

    fun pick(h: Float, s: Float, v: Float) {
        hsv = Triple(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        val picked = Colors.fromHsv(hsv.first, hsv.second, hsv.third)
        hexText = Colors.hex(picked)
        onChange(picked)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, Color(Colors.fromHsv(hsv.first, 1f, 1f)))))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .pointerInput(Unit) {
                    detectTapGestures { p -> pick(hsv.first, p.x / size.width, 1f - p.y / size.height) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> pick(hsv.first, change.position.x / size.width, 1f - change.position.y / size.height) }
                }
                .semantics { contentDescription = "Colour square: more colourful to the right, darker towards the bottom" }
                .testTag("colour-square"),
        ) {
            Box(
                Modifier
                    .offset(x = maxWidth * hsv.second - 13.dp, y = maxHeight * (1f - hsv.third) - 13.dp)
                    .size(26.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .background(Color(color), CircleShape),
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(Rainbow))
                .pointerInput(Unit) { detectTapGestures { p -> pick(360f * (p.x / size.width).coerceIn(0f, 1f), hsv.second, hsv.third) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> pick(360f * (change.position.x / size.width).coerceIn(0f, 0.999f), hsv.second, hsv.third) }
                }
                .semantics { contentDescription = "Hue slider" }
                .testTag("hue-slider"),
        ) {
            Box(
                Modifier
                    .offset(x = maxWidth * (hsv.first / 360f) - 14.dp)
                    .size(28.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .background(Color(Colors.fromHsv(hsv.first, 1f, 1f)), CircleShape),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Color(color), RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = hexText,
                onValueChange = { text ->
                    hexText = text.take(7)
                    Colors.parseHex(text)?.let { picked ->
                        hsv = Colors.toHsv(picked)
                        onChange(picked)
                    }
                },
                label = { Text("Hex code") },
                singleLine = true,
                isError = Colors.parseHex(hexText) == null,
                modifier = Modifier.weight(1f).testTag("field:hex"),
            )
        }
        if (recent.isNotEmpty()) {
            Text("Recent colours", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                recent.forEach { past ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color(past), CircleShape)
                            .clickable {
                                hsv = Colors.toHsv(past)
                                hexText = Colors.hex(past)
                                onChange(past)
                            }
                            .semantics { contentDescription = "Recent colour ${Colors.hex(past)}" }
                            .testTag("recent:${Colors.hex(past)}"),
                    )
                }
            }
        }
    }
}

/** The colour picker for the app's colour, with a preview of how the app will look. */
@Composable
fun AppColourScreen(appearance: Appearance, onUse: (Int) -> Unit, onBack: () -> Unit) {
    var color by rememberSaveable { mutableStateOf(appearance.color ?: Colors.GARDEN) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App colour") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = { onUse(color) }, modifier = Modifier.testTag("use-colour")) { Text("Use it") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ColorPicker(color, appearance.recent, onChange = { color = it })
            Text("How it looks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            LookPreview(color)
            Text(
                "The whole app takes its colours from the one you pick, and keeps text readable. Each goal's colour can come from the same picker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LookPreview(seed: Int) {
    val s = Schemes.of(seed, LocalBrightness.current)
    Column(
        Modifier.fillMaxWidth().background(Color(s.background), RoundedCornerShape(16.dp)).padding(12.dp).testTag("look-preview"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(s.primaryContainer))) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Balance", color = Color(s.onPrimaryContainer), style = MaterialTheme.typography.labelLarge)
                    Text("126 points", color = Color(s.onPrimaryContainer), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text("12-day streak", color = Color(s.onPrimaryContainer), fontWeight = FontWeight.Bold)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(s.surfaceContainerLow))) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 26.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                Column(Modifier.weight(1f)) {
                    Text("Read", color = Color(s.onSurface), style = MaterialTheme.typography.titleMedium)
                    Text("+4 · 20 min", color = Color(s.onSurfaceVariant), style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = Color(s.primary), contentColor = Color(s.onPrimary)),
                ) { Text("Done") }
            }
        }
    }
}
