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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.collectAsState
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
import io.github.randomtools3377.checklist.core.AppTabKind
import io.github.randomtools3377.checklist.core.PointsName
import io.github.randomtools3377.checklist.core.Schemes
import io.github.randomtools3377.checklist.core.SectionChoice
import io.github.randomtools3377.checklist.core.Sections
import io.github.randomtools3377.checklist.core.TabChoice

private val Rainbow = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** Settings → Appearance: light, dark, phone or black, and the colour the app is built from. */
@Composable
fun AppearanceScreen(appearance: Appearance, vm: MainViewModel, onAnyColour: () -> Unit, onBack: () -> Unit) {
    val phone = LocalPhone.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Title("Theme")
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
            Title("Colour")
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
            SwitchRow(
                "Colour-blind friendly",
                appearance.colourBlind,
                "switch:colour-blind",
                hint = "Good things in blue and bad things in orange, like points earned and lost, whatever the app's colour. " +
                    "The review calendar marks each day with ✓ or ✕ too, and goals get colours that stay apart.",
                onChange = vm::setColourBlind,
            )

            Spacer(Modifier.height(4.dp))
            Title("Today")
            Choice("Layout", TodayLayout.entries, appearance.layout, { it.label }, "layout") { vm.setTodayLayout(it) }
            Text(
                if (appearance.layout == TodayLayout.GRID) "Big tiles, two to a row. Tap a tile to tick it; hold it for its details, skip or focus." else "A card for each goal, with its buttons.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Choice("Card size", CardSize.entries, appearance.cardSize, { it.label }, "card-size") { vm.setCardSize(it) }
            Choice("Text size", TextSize.entries, appearance.textSize, { it.label }, "text-size") { vm.setTextSize(it) }
            Text(
                "On top of the phone's own text size.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BackdropSetting(appearance, vm)

            Spacer(Modifier.height(4.dp))
            Title("Today's sections")
            SectionsEditor(appearance.sections) { change -> vm.changeSections(change) }
            Spacer(Modifier.height(4.dp))
            Title("Tabs")
            TabsEditor(appearance.tabs, vm)

            Spacer(Modifier.height(4.dp))
            Title("Personal")
            PointsNameSetting(vm)
            Choice("Font", FontChoice.entries, appearance.font, { it.label }, "font") { vm.setFont(it) }
            // The app's icon and the widgets' look only where there are icon choices and widgets.
            if (phone.canChangeIcon) AppIconSetting(appearance.icon) { vm.setAppIcon(it) }
            if (phone.hasWidgets) {
                Choice("Widgets", io.github.randomtools3377.checklist.widget.WidgetStyle.entries, appearance.widgetStyle, { it.label }, "widget-style") { vm.setWidgetStyle(it) }
                Text(
                    if (appearance.widgetStyle == io.github.randomtools3377.checklist.widget.WidgetStyle.SEE_THROUGH) {
                        "White text on a see-through background, over your wallpaper."
                    } else {
                        "In the app's colours, light or dark with the phone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CelebrationSettings(appearance.celebrate, vm)
        }
    }
}

/** What points are called: points, coins 🪙, stars ⭐, XP, gems 💎, or the user's own words. */
@Composable
private fun PointsNameSetting(vm: MainViewModel) {
    val current = PointsLabel.name
    var writing by remember { mutableStateOf(false) }
    Text("${pointsTitle()} are called", style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PointsName.PRESETS.forEach { preset ->
            androidx.compose.material3.FilterChip(
                selected = current == preset,
                onClick = { vm.setPointsName(preset) },
                label = { Text(listOf(preset.title, preset.emoji).filter { it.isNotEmpty() }.joinToString(" ")) },
                modifier = Modifier.testTag("points-name:${preset.many}"),
            )
        }
        androidx.compose.material3.FilterChip(
            selected = current !in PointsName.PRESETS,
            onClick = { writing = true },
            label = { Text(if (current !in PointsName.PRESETS) "${current.title} ${current.emoji}".trim() else "Your own…") },
            modifier = Modifier.testTag("points-name:own"),
        )
    }
    if (writing) {
        var one by remember { mutableStateOf(current.one) }
        var many by remember { mutableStateOf(current.many) }
        var emoji by remember { mutableStateOf(current.emoji) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text("Your own name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(one, { one = it.take(PointsName.LENGTH) }, label = { Text("For one, like \"bean\"") }, singleLine = true, modifier = Modifier.testTag("field:points-one"))
                    OutlinedTextField(many, { many = it.take(PointsName.LENGTH) }, label = { Text("For more, like \"beans\"") }, singleLine = true, modifier = Modifier.testTag("field:points-many"))
                    OutlinedTextField(emoji, { emoji = it.take(8) }, label = { Text("An emoji, if you like") }, singleLine = true, modifier = Modifier.testTag("field:points-emoji"))
                    Text("For example: ${PointsName.of(one, many, emoji).amount(126)}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setPointsName(PointsName.of(one, many, emoji))
                    writing = false
                }, modifier = Modifier.testTag("save-points-name")) { Text("Use it") }
            },
            dismissButton = { TextButton(onClick = { writing = false }) { Text("Cancel") } },
        )
    }
}

/** The app's icon on the home screen. */
@Composable
private fun AppIconSetting(current: io.github.randomtools3377.checklist.AppIcon, onPick: (io.github.randomtools3377.checklist.AppIcon) -> Unit) {
    Text("App icon", style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        io.github.randomtools3377.checklist.AppIcon.entries.forEach { icon ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onPick(icon) }
                    .semantics { selected = icon == current }
                    .testTag("app-icon:${icon.name}")
                    .padding(4.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .then(if (icon == current) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp)).padding(4.dp) else Modifier)
                        .background(Color(icon.color), RoundedCornerShape(12.dp)),
                ) { Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Text(icon.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Text(
        "Some home screens take a moment to show the new icon, or move it to the app list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Celebrations: sound, buzz, how much confetti, and a GIF for every goal without its own. */
@Composable
private fun CelebrationSettings(style: CelebrationStyle, vm: MainViewModel) {
    Text("Celebrations", style = MaterialTheme.typography.bodyLarge)
    SwitchRow("Sound", style.sound, "celebrate:sound") { vm.setCelebrationSound(it) }
    // A computer has nothing to buzz.
    if (LocalPhone.current.canBuzz) SwitchRow("Buzz", style.buzz, "celebrate:buzz") { vm.setCelebrationBuzz(it) }
    Choice("Confetti", ConfettiAmount.entries, style.confetti, { it.label }, "confetti") { vm.setConfetti(it) }
    Text("A GIF for every goal", style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilterChip(
            selected = style.defaultGif == null,
            onClick = { vm.setDefaultGif(null) },
            label = { Text("None") },
            modifier = Modifier.testTag("default-gif:none"),
        )
        io.github.randomtools3377.checklist.core.Celebration.BUILT_IN.forEach { name ->
            androidx.compose.material3.FilterChip(
                selected = style.defaultGif == name,
                onClick = { vm.setDefaultGif(name) },
                label = { Text(builtInLabel(name)) },
                modifier = Modifier.testTag("default-gif:$name"),
            )
        }
    }
    Text(
        "With a GIF chosen, finishing any goal without its own fills the screen with it. Sound plays at your phone's sound volume.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Today's sections: tick to show, arrows to move. */
@Composable
private fun SectionsEditor(sections: List<SectionChoice>, onChange: ((List<SectionChoice>) -> List<SectionChoice>) -> Unit) {
    sections.forEachIndexed { index, choice ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TickBoxRow(
                checked = choice.shown,
                tag = "section:${choice.section.name}",
                modifier = Modifier.weight(1f),
                onChange = { onChange { now -> Sections.toggle(now, choice.section) } },
            ) { Text(choice.section.label) }
            IconButton(onClick = { onChange { now -> Sections.move(now, now.indexOfFirst { it.section == choice.section }, -1) } }, enabled = index > 0, modifier = Modifier.testTag("section-up:${choice.section.name}")) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${choice.section.label} up")
            }
            IconButton(onClick = { onChange { now -> Sections.move(now, now.indexOfFirst { it.section == choice.section }, 1) } }, enabled = index < sections.lastIndex, modifier = Modifier.testTag("section-down:${choice.section.name}")) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${choice.section.label} down")
            }
        }
    }
}

/** Which tabs show along the bottom, and the one the app opens on. Today always shows. */
@Composable
private fun TabsEditor(tabs: TabChoice, vm: MainViewModel) {
    val features by vm.features.collectAsState()
    // The Budget tab is there only while Budget is on.
    fun offered(tab: AppTabKind) = tab != AppTabKind.BUDGET || io.github.randomtools3377.checklist.core.Feature.BUDGET in features
    // Every tab in the user's order: shown or hidden (Today always shows), and moved up or down.
    val listed = tabs.order.filter { offered(it) }
    listed.forEachIndexed { index, tab ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TickBoxRow(
                checked = tab !in tabs.hidden,
                tag = "tab:${tab.name}",
                modifier = Modifier.weight(1f),
                enabled = tab != AppTabKind.TODAY,
                onChange = { shown -> vm.setTabHidden(tab, hidden = !shown) },
            ) { Text(tab.label) }
            IconButton(onClick = { vm.moveTab(tab, -1) }, enabled = index > 0, modifier = Modifier.testTag("tab-up:${tab.name}")) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${tab.label} earlier")
            }
            IconButton(onClick = { vm.moveTab(tab, 1) }, enabled = index < listed.lastIndex, modifier = Modifier.testTag("tab-down:${tab.name}")) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${tab.label} later")
            }
        }
    }
    Text(
        "A hidden tab can still be opened from Settings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Opens on", style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.shown.filter { offered(it) }.forEach { tab ->
            androidx.compose.material3.FilterChip(
                selected = tabs.opensOn(budgetOn = offered(AppTabKind.BUDGET)) == tab,
                onClick = { vm.setFirstTab(tab) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("first-tab:${tab.name}"),
            )
        }
    }
}

/** A section's title on the Appearance page, marked as a heading for screen readers. */
@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.asHeading())
}

/** A labelled row of choices, one of which is picked. */
@Composable
private fun <T> Choice(label: String, options: List<T>, selected: T, name: (T) -> String, tag: String, onPick: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onPick(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.testTag("$tag:${name(option)}"),
            ) { Text(name(option), maxLines = 1) }
        }
    }
}

/** What is behind Today: nothing, a gradient, or the user's own photo, dimmed so text stays readable. */
@Composable
private fun BackdropSetting(appearance: Appearance, vm: MainViewModel) {
    val phone = LocalPhone.current
    val picker = rememberPickImage(gifOnly = false) { file ->
        if (file != null) vm.pickPhoto(file) { phone.toast(it) }
    }
    fun pickPhoto() = picker()
    Choice("Background", Backdrop.entries, appearance.backdrop, { it.label }, "backdrop") { choice ->
        if (choice == Backdrop.PHOTO && appearance.photoStamp.isEmpty()) pickPhoto() else vm.setBackdrop(choice)
    }
    if (appearance.backdrop == Backdrop.PHOTO) {
        TextButton(onClick = { pickPhoto() }, modifier = Modifier.testTag("pick-photo")) {
            Text(if (appearance.photoStamp.isEmpty()) "Pick a photo" else "Pick another photo")
        }
        Text("Dimmed ${appearance.dim}%", style = MaterialTheme.typography.bodyMedium)
        var dim by remember(appearance.dim) { mutableStateOf(appearance.dim.toFloat()) }
        androidx.compose.material3.Slider(
            value = dim,
            onValueChange = { dim = it },
            onValueChangeFinished = { vm.setBackdropDim(dim.toInt()) },
            valueRange = Appearance.MIN_DIM.toFloat()..Appearance.MAX_DIM.toFloat(),
            steps = (Appearance.MAX_DIM - Appearance.MIN_DIM) / 10 - 1,
            modifier = Modifier.testTag("backdrop-dim"),
        )
        Text(
            "The photo is faded toward the background colour so text on it stays readable. It is copied into the app and goes in your backup file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                actions = { HelpAction()
 TextButton(onClick = { onUse(color) }, modifier = Modifier.testTag("use-colour")) { Text("Use it") } },
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
                    Text(pointsText(126), color = Color(s.onPrimaryContainer), fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
