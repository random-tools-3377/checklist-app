package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

/** The setting holding the guides already shown as tips, and the one that turns tips off. */
const val SETTING_TIPS_SEEN = "tips_seen"
const val SETTING_TIPS_OFF = "tips_off"

/** A guide's text, with the user's own word for points. */
private fun words(text: String) = text.replace("{points}", pointsWord())

/** A "?" in a top bar that opens the screen's guide, if it has one. */
@Composable
fun HelpAction() {
    val topic = helpTopicFor(ShownRoute.route) ?: return
    var open by remember(topic) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.testTag("help")) {
        Icon(Icons.Filled.Info, contentDescription = "How ${topic.title.lowercase()} works")
    }
    if (open) HelpDialog(topic, onDismiss = { open = false })
}

/** A guide: what the part is for, then how to use it. */
@Composable
fun HelpDialog(topic: HelpTopic, onDismiss: () -> Unit, firstTime: Boolean = false, onNoMoreTips: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (firstTime) "💡 " else "") + topic.title) },
        text = { HelpText(topic) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("help-ok")) { Text("Got it") } },
        dismissButton = onNoMoreTips?.let { stop ->
            { TextButton(onClick = stop, modifier = Modifier.testTag("tips-off")) { Text("No more tips") } }
        },
        modifier = Modifier.testTag(if (firstTime) "tip:${topic.name}" else "help:${topic.name}"),
    )
}

@Composable
private fun HelpText(topic: HelpTopic) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(words(topic.intro), style = MaterialTheme.typography.bodyLarge)
        topic.steps.forEach { step ->
            Row {
                Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                Text(words(step), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Tap ⓘ at the top of this screen to see this again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The first time each part of the app opens, its guide shows as a tip, unless tips are off.
 * Tips aren't shown while the app is being tested on its own.
 */
@Composable
fun FirstTimeTip(route: String, vm: MainViewModel) {
    val topic = helpTopicFor(route) ?: return
    val tips by vm.tips.collectAsStateWithLifecycle()
    val state = tips ?: return
    if (!state.on || topic.name in state.seen || LocalPhone.current.underTest) return
    HelpDialog(topic, onDismiss = { vm.tipSeen(topic) }, firstTime = true, onNoMoreTips = { vm.setTipsOn(false) })
}

/** Every guide, to read any time, with the switch for tips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(vm: MainViewModel, onBack: () -> Unit) {
    val tips by vm.tips.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
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
            modifier = Modifier.fillMaxSize().testTag("help-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Tips the first time", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "A short guide the first time each part of the app opens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = tips?.on ?: true, onCheckedChange = { vm.setTipsOn(it) }, modifier = Modifier.testTag("tips-on"))
                }
                TextButton(onClick = vm::showTipsAgain, enabled = tips?.seen?.isNotEmpty() == true, modifier = Modifier.testTag("tips-again")) {
                    Text("Show every tip again")
                }
                Text(
                    "Every screen with ⓘ at the top has its guide there. They're all here too:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(HelpTopic.entries.toList(), key = { it.name }) { topic ->
                Column(Modifier.fillMaxWidth().clickable { open = if (open == topic.name) null else topic.name }.padding(vertical = 10.dp).testTag("help-topic:${topic.name}")) {
                    Text(topic.title, style = MaterialTheme.typography.titleMedium)
                    if (open == topic.name) {
                        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(words(topic.intro), style = MaterialTheme.typography.bodyMedium)
                            topic.steps.forEach { step ->
                                Row {
                                    Text("•", modifier = Modifier.width(16.dp))
                                    Text(words(step), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else {
                        Text(words(topic.intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}
