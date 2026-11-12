@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.aboutNow
import io.github.randomtools3377.checklist.core.Offered
import io.github.randomtools3377.checklist.core.Rules
import io.github.randomtools3377.checklist.core.SettingsSearch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import kotlinx.datetime.LocalTime
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.foundation.horizontalScroll
import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.Difficulty
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.GapSettings
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.dateText
import io.github.randomtools3377.checklist.data.AUTO_BACKUPS_KEPT
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.StreakTarget
import io.github.randomtools3377.checklist.data.LOCK_HISTORY
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.LOCK_REVIEW
import io.github.randomtools3377.checklist.data.LOCK_GAPS
import io.github.randomtools3377.checklist.data.LOCK_REASONS
import io.github.randomtools3377.checklist.data.LOCK_SHOP
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.collectAsState

fun hourText(hour: Int): String = when (hour) {
    0 -> "Midnight"
    12 -> "Noon"
    else -> "$hour am"
}

/**
 * How easy rewards are to earn: a level by hand, or getting harder (or easier) a level at a time
 * on a schedule. Also, with suggested prices on, the weekly treat budget, which links money and
 * points for rewards priced in money (see MoneyPrices).
 */
@Composable
private fun DifficultySettings(vm: MainViewModel) {
    val setting by vm.difficulty.collectAsState()
    val now by vm.currentDifficulty.collectAsState()
    val budget by vm.treatBudget.collectAsState()
    val moneyRate by vm.moneyRate.collectAsState()
    val features by vm.features.collectAsState()
    val treatShown = treatBudgetShown(features, budget)
    var rampFrom by rememberSaveable { mutableStateOf(Difficulty.EASY) }
    var rampTo by rememberSaveable { mutableStateOf(Difficulty.NORMAL) }
    var weeks by rememberSaveable { mutableStateOf("6") }
    var planning by rememberSaveable { mutableStateOf(false) }
    var budgetText by rememberSaveable(budget) { mutableStateOf(budget?.let { Amounts.format(it) }.orEmpty()) }
    val ramp = setting.ramp
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Now: ${now.label}. Goals pay ${now.percent}% of their ${pointsWord()}" + run {
                // What this level does to free skips and misses, as the rules set it.
                val extra = now.allowed(1000) - 1000
                when {
                    now.allowed(1000) == 0 -> ", and no skip or miss is free."
                    extra > 0 -> ", and $extra more skip${if (extra == 1) " or miss is" else "s or misses are"} free."
                    extra < 0 -> ", and ${-extra} fewer skip${if (extra == -1) " or miss is" else "s or misses are"} free."
                    else -> ", and skips and misses are as each goal sets."
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("difficulty-now"),
        )
        Text(
            "Penalties stay ${penaltyShare()} a goal's own ${pointsWord()}, and prices don't change.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DifficultyChips(selected = if (ramp == null) setting.level else null, tagPrefix = "difficulty") { vm.setDifficulty(it) }
        if (ramp != null) {
            val next = ramp.nextChange(vm.currentDay())
            Text(
                "Getting ${if (ramp.to.ordinal > ramp.from.ordinal) "harder" else "easier"}: from ${ramp.from.label} to ${ramp.to.label} over " +
                    "${ramp.weeks} weeks." + (next?.let { " Next step on ${longDate(it)}." } ?: " It has got there."),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("ramp-status"),
            )
            TextButton(onClick = vm::stopRamp, modifier = Modifier.testTag("stop-ramp")) { Text("Stop, and stay at ${now.label}") }
        } else if (!planning) {
            TextButton(onClick = { planning = true }, modifier = Modifier.testTag("plan-ramp")) { Text("Get harder over time…") }
        } else {
            Text("Start at", style = MaterialTheme.typography.bodyLarge)
            DifficultyChips(selected = rampFrom, tagPrefix = "ramp-from") { rampFrom = it }
            Text("End up at", style = MaterialTheme.typography.bodyLarge)
            DifficultyChips(selected = rampTo, tagPrefix = "ramp-to") { rampTo = it }
            NumberField(label = "Over how many weeks", value = weeks, onValueChange = { weeks = it.take(2) }, tag = "field:ramp-weeks")
            Row {
                Button(
                    onClick = {
                        vm.startRamp(rampFrom, rampTo, weeks.toIntOrNull() ?: 6)
                        planning = false
                    },
                    enabled = rampFrom != rampTo && (weeks.toIntOrNull() ?: 0) in 1..52,
                    modifier = Modifier.testTag("start-ramp"),
                ) { Text("Start") }
                TextButton(onClick = { planning = false }) { Text("Cancel") }
            }
            Text(
                "It moves a level at a time on Mondays, spread over the weeks. Picking a level above stops it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The treat budget is for suggested prices: shown while they're on, or once it is set.
        if (treatShown) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = budgetText,
                onValueChange = { text -> budgetText = text.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                label = { Text("Weekly treat budget, in money") },
                prefix = moneySymbol().takeIf { it.isNotEmpty() }?.let { symbol -> { Text(symbol) } },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("field:treat-budget"),
            )
            TextButton(onClick = { vm.setTreatBudget(Amounts.parse(budgetText)) }, modifier = Modifier.testTag("save-treat-budget")) { Text("Set") }
        }
        if (treatShown) Text(
            "Links money and ${pointsWord()} for rewards with a price in money: doing everything due in a week earns this much in rewards." +
                (moneyRate?.let { " Now ${moneyText(1.0)} is about ${io.github.randomtools3377.checklist.core.Suggest.nice(it.pointsPerUnit)} ${pointsWord()}." } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Whether Settings → Difficulty shows the weekly treat budget: with suggested prices on, or once it is set. */
internal fun treatBudgetShown(features: Set<Feature>, budget: Double?): Boolean = Feature.SUGGESTIONS in features || budget != null

/**
 * Showing the phone's own calendar in the planner and on Today, read on the phone only. Turning
 * it on asks for the calendar permission; then the user picks which calendars.
 */
@Composable
private fun CalendarSettings(vm: MainViewModel) {
    val phone = LocalPhone.current
    if (!phone.hasCalendar) {
        NotOnThisDevice(Feature.CALENDAR.title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        return
    }
    val choice by vm.calendarChoice.collectAsState()
    var calendars by remember { mutableStateOf<List<io.github.randomtools3377.checklist.calendar.PhoneCalendarInfo>>(emptyList()) }
    var allowed by remember { mutableStateOf(phone.calendarAllowed()) }
    val ask = rememberAsk(Ask.CALENDAR) { granted ->
        allowed = granted
        if (granted) vm.setCalendarOn(true)
    }
    LaunchedEffect(choice.first, allowed) {
        calendars = if (choice.first && allowed) vm.phoneCalendars() else emptyList()
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchLine(
            "Show my calendar",
            "Events from your ${phone.deviceWord}'s calendar in the planner, the timeline and Today, and pacing leaves their time out. It is only read, on the ${phone.deviceWord}.",
            choice.first && allowed,
            "calendar",
        ) { on ->
            if (!on) vm.setCalendarOn(false) else if (allowed) vm.setCalendarOn(true) else ask()
        }
        if (choice.first && allowed && calendars.size > 1) {
            Text("Which calendars", style = MaterialTheme.typography.bodyLarge)
            calendars.forEach { cal ->
                val shown = choice.second.isEmpty() || cal.id in choice.second
                TickBoxRow(
                    checked = shown,
                    tag = "calendar:${cal.name}",
                    modifier = Modifier.fillMaxWidth(),
                    onChange = { on ->
                        val current = if (choice.second.isEmpty()) calendars.map { it.id }.toSet() else choice.second
                        vm.setCalendarIds(if (on) current + cal.id else current - cal.id)
                    },
                ) {
                    Column {
                        Text(cal.name, style = MaterialTheme.typography.bodyMedium)
                        if (cal.account.isNotBlank() && cal.account != cal.name) {
                            Text(cal.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchLine(title: String, hint: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    SwitchRow(title, on, "switch:$tag", hint = hint, onChange = onChange)
}

/** The words a Settings search finds the Notifications part by. */
internal fun notificationWords(phone: Phone): String =
    "Notifications notification reminders reminder alarm exact weekly summary snooze morning plan evening check still open light up screen edge lighting" +
        if (phone.hasWidgets) " widget widgets home screen calendar" else ""

/** The notifications [phone] can't give yet, like "Morning plan, weekly summary and snooze"; null if none. */
internal fun notificationsNotHere(phone: Phone): String? {
    val missing = listOfNotNull(
        "morning plan".takeIf { !phone.hasPlanNotifications },
        "weekly summary".takeIf { !phone.hasPlanNotifications },
        "snooze".takeIf { !phone.canSnooze },
    )
    if (missing.isEmpty()) return null
    val names = if (missing.size == 1) missing[0] else missing.dropLast(1).joinToString(", ") + " and " + missing.last()
    return names.replaceFirstChar { it.uppercase() }
}

/** Something this device doesn't have: its name, and that it isn't here yet. */
@Composable
internal fun NotOnThisDevice(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("not-here:$title")) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text("Not on this device yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The Features page: every optional feature with a switch. They start off so the app stays
 * simple; one already in use stays shown wherever it is used, even when turned off here.
 * Opened from a Settings search, it scrolls to [focus], the name of the feature found.
 */
@Composable
fun FeaturesScreen(vm: MainViewModel, onAppearance: () -> Unit, onBack: () -> Unit, focus: String? = null) {
    val phone = LocalPhone.current
    val scroll = rememberScrollState()
    val target = rememberScrollTarget(focus, scroll)
    val features by vm.features.collectAsState()
    val interest by vm.interestPercent.collectAsState()
    val boosts by vm.boosts.collectAsState()
    // Each extra's line says the numbers set in Points and rules.
    val rules by vm.rules.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Features") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .testTag("features"),
        ) {
            Text(
                "Extras start off, to keep the app simple. Turn on what you'd like to use; turn off what you don't. " +
                    "Anything already in use stays where it is.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // The AI helper is left out where there is no Checklist AI to use.
            featuresOn(phone).groupBy { it.group }.forEach { (group, inGroup) ->
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section(group)
                inGroup.forEach { feature ->
                    if (feature == Feature.CALENDAR) {
                        Column(Modifier.scrollPlace(target, feature.name)) { CalendarSettings(vm) }
                        return@forEach
                    }
                    Column(
                        Modifier.scrollPlace(target, feature.name).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SwitchLine(feature.title, feature.aboutNow(rules), feature in features, "feature:${feature.name}") { vm.setFeature(feature, it) }
                        if (feature == Feature.AI && feature in features) AiStatusPanel(vm)
                        // How much, for the two that pay an amount.
                        if (feature == Feature.INTEREST && feature in features) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 5).forEach { choice ->
                                    androidx.compose.material3.FilterChip(
                                        selected = interest == choice,
                                        onClick = { vm.setInterest(choice) },
                                        label = { Text("$choice% a week") },
                                        modifier = Modifier.testTag("interest:$choice"),
                                    )
                                }
                            }
                        }
                        if (feature == Feature.PERFECT_DAY && feature in features) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(5, 10, 20).forEach { choice ->
                                    androidx.compose.material3.FilterChip(
                                        selected = boosts.perfectDayBonus == choice,
                                        onClick = { vm.setPerfectDayBonus(choice) },
                                        label = { Text("+$choice") },
                                        modifier = Modifier.testTag("perfect-bonus:$choice"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            TextButton(onClick = onAppearance, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).testTag("features-tabs")) {
                Text("Choose which tabs show, like Plan, in Appearance")
            }
        }
    }
}

@Composable
private fun DifficultyChips(selected: Difficulty?, tagPrefix: String, onPick: (Difficulty) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
    ) {
        Difficulty.entries.forEach { level ->
            androidx.compose.material3.FilterChip(
                selected = selected == level,
                onClick = { onPick(level) },
                label = { Text(level.label) },
                modifier = Modifier.testTag("$tagPrefix:${level.name}"),
            )
        }
    }
}

/** What a Settings search found on another page: a chip for each, which opens that page at it. */
@Composable
private fun <T> SearchHits(hits: List<T>, label: (T) -> String, tag: (T) -> String, onOpen: (T) -> Unit) {
    if (hits.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        hits.forEach { hit ->
            AssistChip(onClick = { onOpen(hit) }, label = { Text(label(hit)) }, modifier = Modifier.testTag(tag(hit)))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).asHeading(),
    )
}

@Composable
fun SettingsScreen(
    settings: Settings,
    vm: MainViewModel,
    onAppearance: () -> Unit,
    onGuide: () -> Unit,
    onFeatures: () -> Unit = {},
    onRules: () -> Unit = {},
    onHelp: () -> Unit = {},
    onOpenTab: (io.github.randomtools3377.checklist.core.AppTabKind) -> Unit,
    onBack: () -> Unit,
    /** Opens Appearance at a place a search found (see LookPlace). */
    onLook: (String) -> Unit = { onAppearance() },
    /** Opens Features at an extra a search found. */
    onFeature: (Feature) -> Unit = { onFeatures() },
) {
    val phone = LocalPhone.current
    var choosingHour by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Backup?>(null) }
    var hideInRecents by remember { mutableStateOf(phone.hidesPreview()) }

    fun toast(message: String) = phone.toast(message)

    // Search: every word typed has to start a word of a part's name or contents (see SettingsSearch).
    var query by rememberSaveable { mutableStateOf("") }
    val searched = query.trim()
    val searching = searched.isNotEmpty()
    fun shows(words: String): Boolean = !searching || SettingsSearch.matches(searched, words)
    val features by vm.features.collectAsState()
    val rules by vm.rules.collectAsState()
    val treatBudget by vm.treatBudget.collectAsState()
    val look by vm.appearance.collectAsState()
    val budgetLockShown = Feature.BUDGET in features || settings.locks.budget
    // What it finds on the Appearance and Features pages, each opening that page at it.
    val lookHits = if (searching) lookParts(phone, canMatchWallpaper).filter { SettingsSearch.matches(searched, "appearance look ${it.title} ${it.words}") } else emptyList()
    val featureHits = if (searching) featuresOn(phone).filter { SettingsSearch.matches(searched, "features extras ${featureWords(it)}") } else emptyList()
    val showAppearance = shows("Appearance look") || lookHits.isNotEmpty()
    val showFeatures = shows("Features extras optional") || featureHits.isNotEmpty()
    // Points and rules is found by the groups it shows, which leave out extras that are off.
    val ruleGroups = Offered.rules(Rules.FIELDS, features, rules).map { it.group }.distinct()
    val showRules = shows("Points and rules numbers penalty penalties bonus bonuses percent quest combo boss lucky shield path budget game prices balance check suggested difficulty " + ruleGroups.joinToString(" "))
    val showHelp = shows("Help and tips guides how to what does info search reminders game")
    val showGuide = shows("How the app works guide tour examples help")
    // Tabs hidden from the bottom bar are still here.
    val hiddenTabs = look.tabs.hidden.filter { it != io.github.randomtools3377.checklist.core.AppTabKind.BUDGET || Feature.BUDGET in features }.sortedBy { it.ordinal }
    val showTabs = hiddenTabs.isNotEmpty() && shows("open hidden tabs tab " + hiddenTabs.joinToString(" ") { it.label })
    val showDifficulty = shows("Difficulty easy hard harder easier level levels ramp pay" + if (treatBudgetShown(features, treatBudget)) " treat budget money" else "")
    val showDay = shows("Your day day ends starts start finish time late night pace morning")
    val showStreaks = shows("Streaks streak target percent days in a row")
    val showWeekly = shows("Weekly target weekly points week")
    val showPenalties = shows("Penalty tasks penalty limit")
    val showReasons = shows("Reasons for skips and misses reason skip miss own")
    val showGaps = shows("Gaps gap stopped break days loss")
    val showFocus = shows("Focus timer break after a focus session rest pause countdown")
    val showNotifications = shows(notificationWords(phone))
    val showBackup = shows(
        "Backup back up backups export import file restore reminder spreadsheet csv excel sheets history move ${phone.deviceWord}" +
            if (phone.canPickFolder) " automatic folder" else "",
    )
    val showPrivacy = shows(
        "Privacy lock locked unlock fingerprint private hide screenshots reasons notes skips misses moods mood journal review stats " +
            "history shop rewards gap check-ins internet" +
            (if (phone.canAskForLock) " face pin pattern passcode" else "") +
            (if (phone.hasRecentsPreview) " recents recent apps preview" else "") +
            if (budgetLockShown) " budget money" else "",
    )
    val anything = showAppearance || showFeatures || showRules || showHelp || showGuide || showTabs || showDifficulty || showDay ||
        showStreaks || showWeekly || showPenalties || showReasons || showGaps || showFocus || showNotifications || showBackup || showPrivacy
    val backups by vm.backupState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshBackup() }
    val folderLauncher = rememberPickFolder { file ->
        if (file != null) vm.setBackupFolder(file)
    }
    val exportLauncher = rememberSaveFile("application/json") { file ->
        if (file != null) vm.exportTo(file) { toast(it) }
    }
    val spreadsheetLauncher = rememberSaveFile("text/csv") { file ->
        if (file != null) vm.exportSpreadsheet(file) { toast(it) }
    }
    var autoBackups by remember { mutableStateOf<List<Pair<String, PickedFile>>?>(null) }
    // While anything is locked, turning a lock off or exporting asks for the phone's own lock
    // first, then goes ahead.
    val unlocked by vm.unlocked.collectAsState()
    val unlock = rememberUnlock(vm)
    var afterUnlock by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(unlocked) {
        val waiting = afterUnlock
        if (unlocked && waiting != null) {
            afterUnlock = null
            waiting()
        }
    }
    fun unlockFirst(action: () -> Unit) {
        if (!settings.locks.anyOn || vm.unlocked.value) {
            action()
            return
        }
        afterUnlock = action
        unlock()
        // Where there is no lock to ask for, it opens straight away.
        if (vm.unlocked.value) {
            afterUnlock = null
            action()
        }
    }
    val importLauncher = rememberOpenFile(listOf("application/json", "text/plain", "application/octet-stream")) { file ->
        if (file != null) {
            vm.readBackup(file) { backup, error ->
                if (backup != null) pendingImport = backup else toast(error ?: "Couldn't read that file.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Search: only the sections whose name or contents match stay.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("settings-search"),
            )
            if (showAppearance) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAppearance)
                        .testTag("setting:appearance")
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Appearance", style = MaterialTheme.typography.bodyLarge)
                    Text("Colours, text size, Today's layout and sections, and the tabs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                SearchHits(lookHits, { it.title }, { "search-hit:look:${it.title}" }) { onLook(it.key) }
            }
            if (showFeatures) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onFeatures)
                        .testTag("setting:features")
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Features", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Turn extras on or off: to-dos, the timeline, bonuses and more. They start off, to keep the app simple.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SearchHits(featureHits, { it.title }, { "search-hit:feature:${it.name}" }) { onFeature(it) }
            }
            if (showRules)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRules)
                    .testTag("setting:rules")
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Points and rules", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Every number for ${pointsWord()}, penalties, bonuses, the game and prices, to change as you like.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (showHelp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHelp)
                    .testTag("setting:help")
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Help and tips", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "A guide to every part of the app, in groups, with search. Tips the first time are here too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (showGuide)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGuide)
                    .testTag("setting:guide")
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("How the app works", style = MaterialTheme.typography.bodyLarge)
                Text("A short guide, and example goals to try", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            // Tabs hidden from the bottom bar, each a button that wraps onto the next line when they don't fit.
            if (showTabs) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    hiddenTabs.forEach { tab ->
                        TextButton(onClick = { onOpenTab(tab) }, modifier = Modifier.testTag("open-tab:${tab.name}")) { Text("Open ${tab.label}") }
                    }
                }
            }
            // Nothing matches: a way on to Help and tips, which has its own guides.
            if (searching && !anything) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).testTag("settings-nothing-found"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Nothing found", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Try another word, or look in Help and tips.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onHelp, modifier = Modifier.testTag("settings-nothing-help")) { Text("Help and tips") }
                }
            }
            if (showDifficulty) {
                HorizontalDivider()
                Section("Difficulty")
                DifficultySettings(vm)
            }
            if (showDay) {
                HorizontalDivider()
                Section("Your day")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { choosingHour = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("My day ends at", style = MaterialTheme.typography.bodyLarge)
                    Text(hourText(settings.dayEndHour), color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Ticks before this time count for the day before, so a late night still counts as that day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TimeSetting(
                        label = "My day starts at",
                        time = settings.dayStart,
                        onChange = { vm.setDayStart(it) },
                        tag = "setting:day-start",
                        emptyText = "Not set",
                        pickFrom = LocalTime(7, 0),
                        hint = "Goals done several times a day are spread out from then to the finish time" +
                            if (phone.hasPlanNotifications) ", and the morning plan comes then." else ".",
                    )
                    TimeSetting(
                        label = "Finish each day's goals by",
                        time = settings.defaultFinish,
                        onChange = { vm.setDefaultFinish(it) },
                        tag = "setting:finish",
                        emptyText = "Not set, so today's pace is off",
                        hint = "Today shows how fast to go to be done by then. A goal can have its own finish time instead.",
                    )
                }
            }
            if (showStreaks) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Streaks")
                StreakSettingsRow(settings.streak) { vm.setStreakSettings(it) }
            }
            if (showWeekly) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Weekly target")
                WeeklyTargetSetting(vm)
            }
            if (showPenalties) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Penalty tasks")
                PenaltyTaskList(vm)
            }
            if (showReasons) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Reasons for skips and misses")
                OwnReasons(vm)
            }
            if (showGaps) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Gaps")
                GapSettingsRow(settings.gaps) { vm.setGapSettings(it) }
            }
            if (showFocus) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Focus timer")
                val focusBreak by vm.focusBreak.collectAsState()
                FocusBreakSetting(focusBreak, vm::setFocusBreak, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (showNotifications) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Notifications")
                NotificationSettings()
                if (phone.hasPlanNotifications) {
                    val weekly by vm.weeklySummary.collectAsState()
                    SwitchRow(
                        "Weekly summary",
                        weekly,
                        "setting:weekly",
                        hint = "Sunday at 6 pm: how much got done, the ${pointsWord()}, and your best goal. It opens the review.",
                        padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) { vm.setWeeklySummary(it) }
                }
                if (phone.canSnooze) {
                    val snooze by vm.snoozeMinutes.collectAsState()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("Snooze for", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            listOf(10 to "10 min", 30 to "30 min", 60 to "1 hour").forEach { (minutes, label) ->
                                androidx.compose.material3.FilterChip(
                                    selected = snooze == minutes,
                                    onClick = { vm.setSnoozeMinutes(minutes) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("snooze:$minutes"),
                                )
                            }
                        }
                        Text(
                            "Snooze on a reminder brings it back after this long, unless it's done by then.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (phone.hasPlanNotifications) {
                    SwitchRow(
                        "Morning plan",
                        settings.morningPlan,
                        "setting:morning-plan",
                        hint = if (settings.dayStart == null) "Set when your day starts, above, and it comes then: today's list, what to do first, and the pace."
                        else "When your day starts: today's list, what to do first, and the pace.",
                        padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) { vm.setMorningPlan(it) }
                }
                if (phone.canLightEdges) {
                    val light by vm.reminderLight.collectAsState()
                    SwitchRow(
                        "Light up the screen for reminders",
                        light,
                        "setting:reminder-light",
                        hint = "A goal's reminder then comes like a message: it pops up, with the light. " +
                            "Your phone's own \"Edge lighting\" or \"Wake screen for notifications\" setting decides what that looks like.",
                        padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) { vm.setReminderLight(it) }
                    if (light) {
                        TextButton(
                            onClick = { phone.openNotificationSettings() },
                            modifier = Modifier.padding(horizontal = 8.dp).testTag("reminder-light:settings"),
                        ) { Text("Open notification settings") }
                    }
                } else {
                    NotOnThisDevice("Light up the screen for reminders", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                val evening by vm.eveningCheck.collectAsState()
                EveningCheckSetting(evening, settings.dayEndHour, vm::setEveningCheck, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                // What this device's notifications can't do yet, said once.
                val missing = notificationsNotHere(phone)
                if (missing != null) NotOnThisDevice(missing, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (showBackup) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Backup")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        (if (phone.outOfDeviceBackups) "${phone.deviceTitle} backup is off for this app, so this file is your backup. " else "This file is your backup. ") +
                            "Keep it somewhere safe. It also moves your data to another phone or computer: Android, iPhone or Windows.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        FilledTonalButton(
                            onClick = { unlockFirst { exportLauncher("checklist-backup-${nowLocal().date}.json") } },
                            modifier = Modifier.testTag("export"),
                        ) { Text("Export everything") }
                        Spacer(Modifier.padding(4.dp))
                        OutlinedButton(
                            onClick = { importLauncher() },
                            modifier = Modifier.testTag("import"),
                        ) { Text("Import a backup") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        backups.lastBackup?.let { "Last backup: ${momentText(it.toLocalDateTime(), nowLocal().date)}" } ?: "No backup yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("last-backup"),
                    )
    
                    // The automatic backup, where the phone can keep files in a folder.
                    if (phone.canPickFolder) {
                    Spacer(Modifier.height(12.dp))
                    Text("Automatic backup", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            backups.folderGone -> "The folder can't be reached any more. Choose it again."
                            backups.saveFailed -> "Today's backup couldn't be saved in ${backups.folderName ?: "your folder"}. " +
                                "It tries again the next time you open the app."
                            backups.folderSet -> "On: each day you use the app, a dated backup is saved in ${backups.folderName ?: "your folder"}. The last ${AUTO_BACKUPS_KEPT} are kept."
                            else -> "Off. Choose a folder and each day you use the app, a dated backup is saved there. The last ${AUTO_BACKUPS_KEPT} are kept."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backups.folderGone || backups.saveFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = { unlockFirst { folderLauncher() } }, modifier = Modifier.testTag("auto-backup-folder")) {
                            Text(if (backups.folderSet) "Change folder" else "Choose a folder")
                        }
                        if (backups.folderSet) {
                            TextButton(onClick = { vm.setBackupFolder(null) }, modifier = Modifier.testTag("auto-backup-off")) { Text("Turn off") }
                        }
                    }
                    if (backups.folderSet && !backups.folderGone) {
                        TextButton(
                            onClick = { vm.listAutoBackups { autoBackups = it } },
                            modifier = Modifier.testTag("auto-backup-restore"),
                        ) { Text("Restore an automatic backup") }
                    }
                    }

                    // The spreadsheet
                    Spacer(Modifier.height(12.dp))
                    Text("Points history as a spreadsheet", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Every change to your balance, with the notes on your ticks, for Sheets, Excel or Numbers. " +
                            "It can't be imported back: for that, use Export everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { unlockFirst { spreadsheetLauncher("checklist-history-${nowLocal().date}.csv") } },
                        modifier = Modifier.testTag("export-spreadsheet"),
                    ) { Text("Export history as a spreadsheet") }
    
                    // The reminder
                    SwitchRow(
                        "Backup reminder",
                        backups.reminderOn,
                        "setting:backup-reminder",
                        hint = "Today reminds you when your last backup is over a week old.",
                        padding = PaddingValues(top = 4.dp),
                    ) { vm.setBackupReminder(it) }
                }
            }
            if (showPrivacy) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Section("Privacy")
                Text(
                    if (phone.canAskForLock) "Pick what sits behind the lock. Unlocking uses your ${phone.deviceWord}'s own lock, and stays open until you leave the app."
                    else "Pick what sits behind the lock. Once open, it stays open until you leave the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                // A computer has no lock of its own for the app to ask for.
                if (!phone.canAskForLock) {
                    Text(
                        "On a computer the lock can't ask for a fingerprint, so it opens with a note.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("privacy-no-own-lock"),
                    )
                }
                // Turning a lock on hides more, so it needs no unlock; turning one off asks first.
                fun setLock(name: String, on: Boolean) = if (on) vm.setLock(name, true) else unlockFirst { vm.setLock(name, false) }
                LockSwitch("Points history", settings.locks.history, "lock:history") { setLock(LOCK_HISTORY, it) }
                LockSwitch("The shop and bought rewards", settings.locks.shop, "lock:shop") { setLock(LOCK_SHOP, it) }
                LockSwitch("Goals marked private", settings.locks.privateGoals, "lock:private") { setLock(LOCK_PRIVATE, it) }
                LockSwitch("Reasons and notes for skips, misses and moods", settings.locks.reasons, "lock:reasons") { setLock(LOCK_REASONS, it) }
                LockSwitch("Gap check-ins", settings.locks.gaps, "lock:gaps") { setLock(LOCK_GAPS, it) }
                LockSwitch("The review and stats", settings.locks.review, "lock:review") { setLock(LOCK_REVIEW, it) }
                if (budgetLockShown) {
                    LockSwitch("The budget", settings.locks.budget, "lock:budget") { setLock(io.github.randomtools3377.checklist.data.LOCK_BUDGET, it) }
                }
                // What the moods and the budget sit behind.
                Text(
                    "The mood journal is locked by \"Reasons and notes for skips, misses and moods\" or by \"The review and stats\"" +
                        if (budgetLockShown) ", and the budget by its own switch." else ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("privacy-moods"),
                )
                if (settings.locks.privateGoals) {
                    Text(
                        "While locked, private goals are left off the widget, and their notifications don't name them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                // Nothing on a computer, which has no recent-apps screen; a switch only where hiding it is a choice.
                if (phone.hasRecentsPreview && !phone.previewIsAChoice) {
                    Text(
                        "The app's preview is hidden in the recent-apps screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else if (phone.hasRecentsPreview) {
                    SwitchRow(
                        "Hide in recent apps",
                        hideInRecents,
                        "setting:hide-recents",
                        hint = "On this ${phone.deviceWord}, hiding the preview also blocks screenshots of the app.",
                        padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        hideInRecents = it
                        phone.setHidesPreview(it)
                    }
                }
                Text(
                    "This app has no internet access" + (if (phone.outOfDeviceBackups) " and is left out of ${phone.deviceWord} backups" else "") +
                        ". Everything stays on this ${phone.deviceWord}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                "Version ${phone.appVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (choosingHour) {
        AlertDialog(
            onDismissRequest = { choosingHour = false },
            title = { Text("My day ends at") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items((0..Days.MAX_DAY_END_HOUR).toList()) { hour ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setDayEndHour(hour)
                                    choosingHour = false
                                },
                        ) {
                            RadioButton(selected = hour == settings.dayEndHour, onClick = null)
                            Text(hourText(hour), modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingHour = false }) { Text("Close") } },
        )
    }

    autoBackups?.let { list ->
        AlertDialog(
            onDismissRequest = { autoBackups = null },
            title = { Text("Restore an automatic backup") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (list.isEmpty()) Text("There are no automatic backups in the folder yet.")
                    else Text("Pick one. You'll see what's in it before anything changes.", style = MaterialTheme.typography.bodySmall)
                    list.forEach { (name, uri) ->
                        val day = Regex("""\d{4}-\d{2}-\d{2}""").find(name)?.value
                            ?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }
                        TextButton(
                            onClick = {
                                autoBackups = null
                                vm.readBackup(uri) { backup, error ->
                                    if (backup != null) pendingImport = backup else toast(error ?: "Couldn't read that file.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("auto-backup:$name"),
                        ) { Text(day?.let { dateText(it) } ?: name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { autoBackups = null }) { Text("Cancel") } },
        )
    }
    pendingImport?.let { backup ->
        val habits = backup.goals.count { !it.archived && it.repeat != null }
        val projects = backup.goals.count { !it.archived && it.repeat == null }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace everything?") },
            text = {
                Text(
                    "This backup is from ${dateText(backup.exportedAt.toLocalDateTime().date)}. It has $habits habits, " +
                        "$projects projects, ${backup.rewards.count { !it.archived }} rewards and ${backup.ticks.size} ticks, " +
                        "with a balance of ${pointsText(backup.entries.sumOf { it.amount })}.\n\n" +
                        "Importing replaces everything on this ${phone.deviceWord} with it. Export first if you want to keep what is here now.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        vm.restore(backup) { toast(it) }
                    },
                    modifier = Modifier.testTag("confirm-import"),
                ) { Text("Replace everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}


/** Whether reminders can show, with a way to turn them on, and whether they arrive exactly on time. */
@Composable
private fun NotificationSettings() {
    val phone = LocalPhone.current
    var allowed by remember { mutableStateOf(phone.canNotify()) }
    // Asked first; if the phone still says no (turned off in its settings), its settings open.
    val ask = rememberAsk(Ask.NOTIFICATIONS) {
        allowed = phone.canNotify()
        if (!allowed) phone.openNotificationSettings()
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (allowed) "Reminders are on. Each habit sets its own in its editor."
            else "Reminders are off for this app, so habits with a set time or a reminder can't notify you.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!allowed) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { ask() },
                modifier = Modifier.testTag("allow-notifications"),
            ) { Text("Turn on reminders") }
        }
        if (!phone.exactAlarmsAllowed()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "This ${phone.deviceWord} lets reminders arrive only roughly on time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { phone.openExactAlarmSettings() }) {
                Text("Allow exact times")
            }
        }
        if (phone.hasWidgets) Text(
            "Add the Checklist calendar to your home screen from the ${phone.deviceWord}'s widget list. Make it wider for the days ahead, taller for the month.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LockSwitch(label: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    SwitchRow(label, on, tag, padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), onChange = onChange)
}

/** What a day needs for the main streak: a number of goals, or a percentage of those due. */
@Composable
private fun StreakSettingsRow(current: StreakSettings, onChange: (StreakSettings) -> Unit) {
    var value by remember(current) { mutableStateOf(current.value.toString()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text("A day keeps the main streak going when:", style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(StreakTarget.PERCENT to "A % of the day's goals", StreakTarget.COUNT to "A number of goals").forEachIndexed { index, (target, label) ->
                SegmentedButton(
                    selected = current.target == target,
                    onClick = {
                        val default = if (target == StreakTarget.PERCENT) 90 else 3
                        onChange(StreakSettings(target, if (target == current.target) current.value else default))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) { Text(label) }
            }
        }
        NumberField(
            label = if (current.target == StreakTarget.PERCENT) "Percent done" else "Goals done",
            value = value,
            onValueChange = {
                value = it
                val number = it.toIntOrNull()
                if (number != null && number > 0 && (current.target == StreakTarget.COUNT || number <= 100)) {
                    onChange(current.copy(value = number))
                }
            },
            tag = "setting:streak-value",
            hint = "At ${current.describe()}, the rest can be missed without breaking it. A skipped goal isn't counted that day: it is never done, and it doesn't break this streak. Other streaks count days with no misses, no skips, nothing over the limit, and perfect days: everything done, nothing skipped.",
        )
    }
}

/** When a stretch counts as a gap: days in a row with nothing ticked, or points lost in a week. */
@Composable
private fun GapSettingsRow(current: GapSettings, onChange: (GapSettings) -> Unit) {
    var days by remember(current) { mutableStateOf(current.days.toString()) }
    var points by remember(current) { mutableStateOf(current.points.toString()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text(
            "When it looks like you stopped for a while, the app asks what happened. One reason and one penalty task cover the whole stretch; points still come off for each miss in it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField(
            label = "Days in a row with nothing ticked",
            value = days,
            onValueChange = {
                days = it
                it.toIntOrNull()?.takeIf { n -> n in 2..60 }?.let { n -> onChange(current.copy(days = n)) }
            },
            tag = "setting:gap-days",
            hint = "From 2 to 60. Days with nothing due don't count.",
        )
        NumberField(
            label = "Or ${pointsWord()} lost within a week",
            value = points,
            onValueChange = {
                points = it
                it.toIntOrNull()?.takeIf { n -> n >= 1 }?.let { n -> onChange(current.copy(points = n)) }
            },
            tag = "setting:gap-points",
            hint = "${pointsTitle()} taken off for going over the limit, within any 7 days in a row.",
        )
    }
}

/** The user's own reasons, offered after forgot, no time, ill and chose not to. */
@Composable
private fun OwnReasons(vm: MainViewModel) {
    val own by vm.ownReasons.collectAsState()
    var text by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Forgot, no time, ill and chose not to are always there. Add your own, like \"Travelling\" or \"Raining\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        own.forEach { reason ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• $reason", modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removeOwnReason(reason) }, modifier = Modifier.testTag("remove-reason:$reason")) { Text("Remove") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                label = { Text("Your own reason") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("setting:new-reason"),
            )
            TextButton(
                onClick = {
                    vm.addOwnReason(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("add-reason"),
            ) { Text("Add") }
        }
    }
}

/** The user's own penalty tasks, one of which is drawn at random when a penalty comes up. */
@Composable
private fun PenaltyTaskList(vm: MainViewModel) {
    val tasks by vm.penaltyTasks.collectAsState()
    var newTask by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Going over the limit means a penalty task right away: make up a missed goal, or draw one of these at random. They pay nothing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tasks.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• ${task.name}", modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removePenaltyTask(task.id) }) { Text("Remove") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTask,
                onValueChange = { newTask = it.take(80) },
                label = { Text("New penalty task") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("setting:new-penalty-task"),
            )
            TextButton(
                onClick = {
                    vm.addPenaltyTask(newTask)
                    newTask = ""
                },
                enabled = newTask.isNotBlank(),
            ) { Text("Add") }
        }
    }
}

/** Points to earn each week, Monday to Sunday, shown on Today. Empty for none. */
@Composable
private fun WeeklyTargetSetting(vm: MainViewModel) {
    val target by vm.weeklyTarget.collectAsState()
    var text by remember(target) { mutableStateOf(if (target > 0) target.toString() else "") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "${pointsTitle()} to earn each week, Monday to Sunday. Today shows how far along you are. Leave it empty for none.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                label = "${pointsTitle()} a week",
                value = text,
                onValueChange = { text = it.take(5) },
                tag = "setting:weekly-target",
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { vm.setWeeklyTarget(text.toIntOrNull() ?: 0) },
                enabled = text != (if (target > 0) target.toString() else ""),
                modifier = Modifier.testTag("save-weekly-target"),
            ) { Text("Set") }
        }
    }
}
