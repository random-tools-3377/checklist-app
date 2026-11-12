package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Rules

/** The screen on show, by its route, so its top bar's ⓘ opens the right guide. */
object ShownRoute {
    var route by mutableStateOf("today")
}

/** Which guide goes with each screen, by the first part of its route. */
fun helpTopicFor(route: String): HelpTopic? = when (route.substringBefore("/")) {
    "today" -> HelpTopic.TODAY
    "plan" -> HelpTopic.PLAN
    "goals" -> HelpTopic.GOALS
    "shop" -> HelpTopic.SHOP
    "review" -> HelpTopic.REVIEW
    "budget" -> HelpTopic.BUDGET
    "settings" -> HelpTopic.SETTINGS
    "features" -> HelpTopic.FEATURES
    "rules" -> HelpTopic.RULES
    "appearance", "colour" -> HelpTopic.APPEARANCE
    "habit" -> HelpTopic.HABIT_EDITOR
    "project" -> HelpTopic.PROJECT_EDITOR
    "todo" -> HelpTopic.TODO_EDITOR
    "reward" -> HelpTopic.REWARD_EDITOR
    "balance-check" -> HelpTopic.BALANCE_CHECK
    "history" -> HelpTopic.HISTORY
    "character" -> HelpTopic.CHARACTER
    "ideas" -> HelpTopic.IDEAS
    "badges" -> HelpTopic.BADGES
    "mood-goals" -> HelpTopic.MOOD_GOALS
    "mood-journal" -> HelpTopic.MOOD_JOURNAL
    "timeline" -> HelpTopic.TIMELINE
    "plan-week" -> HelpTopic.WEEK_PLAN
    "done" -> HelpTopic.DONE_LIST
    "removed" -> HelpTopic.REMOVED_GOALS
    "budget-pay" -> HelpTopic.PAY_AND_HOURS
    "work-day" -> HelpTopic.WORK_DAY
    "money" -> HelpTopic.MONEY_ENTRY
    "budget-categories" -> HelpTopic.CATEGORIES
    "budget-bills" -> HelpTopic.BILLS
    "bill" -> HelpTopic.BILL_EDITOR
    "budget-report" -> HelpTopic.BUDGET_REPORT
    "focus" -> HelpTopic.FOCUS
    else -> null
}

/**
 * The guides already shown as tips, and the switch that shows a screen's guide as a tip the
 * first time it opens. Tips start off, for everyone: guides don't pop up by themselves.
 */
const val SETTING_TIPS_SEEN = "tips_seen"
const val SETTING_TIPS_ON = "tips_first_time"

/** What a device can do, for the guides whose steps differ from one device to another. */
internal data class GuideDevice(
    /** Reminders with Done, Skip and Snooze buttons, and exact alarms to allow (Android). */
    val buttons: Boolean = true,
    /** The morning plan and the weekly summary come as notifications. */
    val plan: Boolean = true,
    val folder: Boolean = true,
    val widgets: Boolean = true,
    val ai: Boolean = true,
    /** A computer, where reminders come only while the app is open. */
    val computer: Boolean = false,
    /** The notifications this device can't give yet, like "Morning plan, weekly summary and snooze". */
    val missing: String? = null,
)

internal fun Phone.guideDevice(): GuideDevice = GuideDevice(
    buttons = canSnooze,
    plan = hasPlanNotifications,
    folder = canPickFolder,
    widgets = hasWidgets,
    ai = hasAi,
    computer = !hasRecentsPreview,
    missing = notificationsNotHere(this),
)

/** Whether this guide is for [device]: none for widgets or the AI where there are none. */
internal fun HelpTopic.shownOn(device: GuideDevice): Boolean = when (this) {
    HelpTopic.WIDGETS -> device.widgets
    HelpTopic.AI -> device.ai
    else -> true
}

internal fun HelpTopic.shownOn(phone: Phone): Boolean = shownOn(phone.guideDevice())

internal fun HelpTopic.stepsOn(phone: Phone): List<String> = stepsOn(phone.guideDevice())

/** This guide's steps on [device], with only what the device can do. */
internal fun HelpTopic.stepsOn(device: GuideDevice): List<String> = when (this) {
    HelpTopic.BACKUP -> if (device.folder) steps else steps.filterNot { "Choose a folder" in it || "automatic backup" in it }
    HelpTopic.PRIVACY -> steps.map { step ->
        when {
            device.computer && step.startsWith("Tap \"Unlock\"") ->
                "A computer can't ask for its own lock, so here \"Unlock\" opens it straight away."
            !device.widgets && step.startsWith("While locked, private goals") -> "While locked, private goals' reminders don't name them."
            else -> step
        }
    }
    HelpTopic.REMINDERS -> reminderSteps(device)
    else -> steps
}

/** The reminders guide: how to set them, then what the reminders on [device] can do. */
private fun reminderSteps(device: GuideDevice): List<String> {
    val (setting, rest) = HelpTopic.REMINDERS.steps.let { it.take(4) to it.drop(4) }
    return buildList {
        addAll(setting)
        if (device.buttons) {
            add("Tap \"Done\" on a reminder to tick the goal, or \"+1\" for a goal done several times a day.")
            add("Tap \"Skip\" to skip it. A skip that would go over the limit opens the app instead, to warn you and ask why.")
            add("Tap \"Snooze\" and it comes back later: after 10 minutes, 30 minutes or an hour, as set in Settings → Notifications.")
        } else {
            add("Reminders here have no buttons yet: tap one to open the app, then tick the goal or skip it on Today.")
        }
        if (device.plan) {
            add("\"Morning plan\" comes when your day starts, with today's list and the pace. \"Weekly summary\" comes on Sunday at 6 pm. Turn each on or off in Settings → Notifications.")
        }
        if (device.buttons) add("If reminders come late, tap \"Allow exact times\" in Settings → Notifications, when it shows there.")
        if (device.computer) add("On a computer, reminders come only while the app is open.")
        device.missing?.let { add(if (" and " in it) "$it aren't on this device yet." else "$it isn't on this device yet.") }
        addAll(rest)
    }
}

/**
 * A guide's text in the user's words: "{points}" is what they call points, and the other words
 * in braces the numbers set in Points and rules, like "{quest}" for what a quest pays. A number
 * followed by " {points}" says "point" for 1.
 */
internal fun guideWords(text: String, rules: Rules = Rules.current): String {
    if ('{' !in text) return text
    var out = text
    for ((key, value) in ruleNumbers(rules)) {
        out = out.replace("{$key} {points}", "$value ${pointsWord(value)}").replace("{$key}", value.toString())
    }
    return out.replace("{points}", pointsWord())
}

private fun ruleNumbers(rules: Rules): Map<String, Int> = mapOf(
    "penalty" to rules.penaltyPercent,
    "quest" to rules.questReward,
    "questBig" to rules.questBigReward,
    "comboDay" to rules.comboPerDay,
    "comboDay2" to rules.comboPerDay * 2,
    "comboMost" to rules.comboPerDay * rules.comboMostDays,
    "bossWeak" to rules.bossWeakPercent,
    "bossReward" to rules.bossReward,
    "luckyOneIn" to rules.luckyOneIn,
    "chestMin" to rules.luckyChestMin,
    "chestMax" to maxOf(rules.luckyChestMin, rules.luckyChestMax),
    "shieldEvery" to rules.shieldEveryDays,
    "shieldsMost" to rules.shieldsMost,
    "pathBonus" to rules.pathBonusPercent,
    "goodMoneyDay" to rules.goodMoneyDay,
    "noSpendDay" to rules.noSpendDay,
    "monthBonus" to rules.moneyMonthBonus,
    "savedUp" to rules.savedUp,
)

/** All of a guide's words on [device], for the search in Help. */
internal fun HelpTopic.text(device: GuideDevice): String =
    (listOf(title, intro) + stepsOn(device)).joinToString("\n") { guideWords(it) }

/** Whether every word of [query] is in the guide's title or text, ignoring case. A blank query matches. */
internal fun HelpTopic.matches(query: String, device: GuideDevice): Boolean {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val text = text(device).lowercase()
    return words.all { it in text }
}

/**
 * The guides Help lists, group by group: those for [device] that match [query], and with
 * [onlyOn] (the extras that are on) only guides whose extras are all on. Groups left empty go.
 */
internal fun helpList(device: GuideDevice, query: String, onlyOn: Set<Feature>?): List<Pair<HelpGroup, List<HelpTopic>>> =
    HelpGroup.entries.map { group ->
        group to group.topics.filter { topic ->
            topic.shownOn(device) && (onlyOn == null || onlyOn.containsAll(topic.extras)) && topic.matches(query, device)
        }
    }.filter { it.second.isNotEmpty() }

/** What a screen reader says for an ⓘ, like "How it works: Daily quests". */
internal fun guideLabel(topic: HelpTopic): String = "How it works: ${topic.title}"

/** A "?" in a top bar that opens the screen's guide, if it has one. */
@Composable
fun HelpAction() {
    val topic = helpTopicFor(ShownRoute.route) ?: return
    var open by remember(topic) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.testTag("help")) {
        Icon(Icons.Filled.Info, contentDescription = guideLabel(topic))
    }
    if (open) HelpDialog(topic, onDismiss = { open = false })
}

/** A small ⓘ that opens [topic]'s guide, for a card on Today or a screen with no top bar. */
@Composable
fun GuideButton(topic: HelpTopic, modifier: Modifier = Modifier) {
    var open by remember(topic) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(36.dp).testTag("guide:${topic.name}")) {
        Icon(
            Icons.Filled.Info,
            contentDescription = guideLabel(topic),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
    if (open) HelpDialog(topic, onDismiss = { open = false })
}

/** A screen's big title with its guide's ⓘ beside it, for the screens that fill the app. */
@Composable
fun GuideTitle(text: String, topic: HelpTopic) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        GuideButton(topic)
    }
}

/**
 * A guide: what the part is for, how to use it, and the guides to read next. A "See also" link
 * shows that guide here, and "Back" the one before.
 */
@Composable
fun HelpDialog(topic: HelpTopic, onDismiss: () -> Unit, firstTime: Boolean = false, onNoMoreTips: (() -> Unit)? = null) {
    val trail = remember(topic) { mutableStateListOf(topic) }
    val shown = trail.last()
    val first = trail.size == 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (firstTime && first) "💡 " else "") + shown.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GuideText(shown, onOpen = { trail.add(it) })
                if (firstTime && first) {
                    Text(
                        "Tap ⓘ at the top of the screen to see this again. Every guide is in Settings → Help and tips.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("help-ok")) { Text("Got it") } },
        dismissButton = when {
            !first -> { { TextButton(onClick = { trail.removeAt(trail.lastIndex) }, modifier = Modifier.testTag("help-back")) { Text("Back") } } }
            onNoMoreTips != null -> { { TextButton(onClick = onNoMoreTips, modifier = Modifier.testTag("tips-off")) { Text("No more tips") } } }
            else -> null
        },
        modifier = Modifier.testTag(if (firstTime && first) "tip:${topic.name}" else "help:${shown.name}"),
    )
}

/** A guide's intro and steps, then its "See also" links, which [onOpen] opens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideText(topic: HelpTopic, onOpen: (HelpTopic) -> Unit, small: Boolean = false) {
    val device = LocalPhone.current.guideDevice()
    Column(verticalArrangement = Arrangement.spacedBy(if (small) 6.dp else 8.dp)) {
        Text(guideWords(topic.intro), style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
        topic.stepsOn(device).forEach { step ->
            Row {
                Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                Text(guideWords(step), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val links = topic.seeAlso.filter { it.shownOn(device) }
        if (links.isNotEmpty()) {
            Text("See also", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            FlowRow {
                links.forEach { link ->
                    TextButton(onClick = { onOpen(link) }, modifier = Modifier.testTag("see-also:${link.name}")) { Text(link.title) }
                }
            }
        }
    }
}

/**
 * When the switch in Help is on, the first time each part of the app opens its guide shows as a
 * tip. Tips start off, and never show while the app is being tested on its own.
 */
@Composable
fun FirstTimeTip(route: String, vm: MainViewModel) {
    val topic = helpTopicFor(route) ?: return
    val tips by vm.tips.collectAsStateWithLifecycle()
    val state = tips ?: return
    if (!state.on || topic.name in state.seen || LocalPhone.current.underTest) return
    HelpDialog(topic, onDismiss = { vm.tipSeen(topic) }, firstTime = true, onNoMoreTips = { vm.setTipsOn(false) })
}

/**
 * Every guide, to read any time: in groups, with a search box that looks in their titles and
 * text, a switch to list only the extras that are on, and the switch for first-time tips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(vm: MainViewModel, onBack: () -> Unit) {
    val device = LocalPhone.current.guideDevice()
    val tips by vm.tips.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyOn by rememberSaveable { mutableStateOf(false) }
    // A "See also" link to scroll to, once the list shows it.
    var goTo by remember { mutableStateOf<HelpTopic?>(null) }
    val list = helpList(device, query, if (onlyOn) features else null)
    val listState = rememberLazyListState()
    LaunchedEffect(goTo) {
        val topic = goTo ?: return@LaunchedEffect
        // Its place: the top item, then each group's title and guides.
        var index = 1
        for ((_, topics) in list) {
            index++
            val at = topics.indexOf(topic)
            if (at >= 0) {
                listState.animateScrollToItem(index + at)
                break
            }
            index += topics.size
        }
        goTo = null
    }
    fun follow(topic: HelpTopic) {
        // A guide left out by the search or the switch shows again.
        if (list.none { topic in it.second }) {
            query = ""
            onlyOn = false
        }
        open = topic.name
        goTo = topic
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction() },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("help-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item(key = "top") {
                Column {
                    SwitchRow(
                        "Show a tip the first time each screen opens",
                        tips?.on ?: false,
                        "tips-on",
                        hint = "Off to start. Each screen's guide is always behind ⓘ at the top.",
                    ) { vm.setTipsOn(it) }
                    if (tips?.on == true && tips?.seen?.isNotEmpty() == true) {
                        TextButton(onClick = vm::showTipsAgain, modifier = Modifier.testTag("tips-again")) { Text("Show every tip again") }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(40) },
                        placeholder = { Text("Search the guides") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear the search") } }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("help-search"),
                    )
                    SwitchRow("Only extras that are on", onlyOn, "help-only-on", hint = "Leaves out the guides to extras you haven't turned on.") { onlyOn = it }
                }
            }
            if (list.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (query.isBlank()) "No guides to show." else "No guide matches \"${query.trim()}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp).testTag("help-no-match"),
                    )
                }
            }
            list.forEach { (group, topics) ->
                item(key = "group:${group.name}") {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.asHeading().padding(top = 16.dp, bottom = 2.dp).testTag("help-group:${group.name}"),
                    )
                }
                items(topics, key = { "topic:${it.name}" }) { topic ->
                    val opened = open == topic.name
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { open = if (opened) null else topic.name }
                            .padding(vertical = 10.dp)
                            .testTag("help-topic:${topic.name}"),
                    ) {
                        Text(topic.title, style = MaterialTheme.typography.titleMedium)
                        if (opened) {
                            Column(Modifier.padding(top = 6.dp)) { GuideText(topic, onOpen = ::follow, small = true) }
                        } else {
                            Text(
                                guideWords(topic.intro),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
