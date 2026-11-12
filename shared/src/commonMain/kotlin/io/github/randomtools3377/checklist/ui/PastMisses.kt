package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.reasonLabel

/** A skip or miss opened later, to change its reason or note, or to say it was a forgotten tick. */
data class PastMiss(
    val lapse: Lapse,
    /** Its goal is marked private. */
    val private: Boolean,
    /** It came from "I slipped" on a goal to quit. */
    val slip: Boolean,
    /** "I forgot to tick it" is still offered: a habit's miss from the last two weeks. */
    val canForget: Boolean,
    /** It is a miss of an "at most" goal: the day went over its limit. */
    val over: Boolean = false,
)

/** What the box says first about a past skip or miss: what happened, and when. */
internal fun pastMissLine(miss: PastMiss, date: String): String = when {
    miss.lapse.kind == LapseKind.SKIP -> "Skipped $date"
    miss.over -> "Went over on $date"
    miss.slip -> "You slipped on $date"
    miss.lapse.period == null -> "Its deadline passed on $date"
    else -> "Missed $date"
}

/** What the box says about one already put right: a forgotten tick, or a slip that wasn't one. */
internal fun pastMissPutRight(miss: PastMiss): String =
    (if (miss.slip) "You said you didn't slip, so it counts as avoided" else "You said you forgot to tick it, so it counts as done") +
        if (miss.lapse.cost > 0) ", and what it cost came back." else "."

/**
 * Whether a past skip or miss asks to unlock before it opens: while reasons and notes are
 * behind the lock, or its goal is [private] and private goals are.
 */
internal fun pastMissLocked(locks: Locks, unlocked: Boolean, private: Boolean): Boolean =
    !unlocked && (locks.reasons || (locks.privateGoals && private))

/** A miss's reason and note in one line, as the review lists it. */
internal fun missSaid(lapse: Lapse): String =
    listOfNotNull(reasonLabel(lapse.reason, lapse.reasonText), lapse.note?.let { "“$it”" }).joinToString(" · ").ifEmpty { "No reason yet" }

/**
 * A past skip or miss, opened from the review or from a penalty's line in the points history:
 * its reason and note can be changed, and a habit's miss from the last two weeks can still be
 * said to be a forgotten tick, with the same effect as on the misses screen. A project's missed
 * deadline has no tick to forget. While reasons and notes are locked, or its goal is private
 * and locked, it asks to unlock first.
 */
@Composable
fun PastMissDialog(lapseId: Long, vm: MainViewModel, onUnlock: () -> Unit, onDone: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    var loaded by remember(lapseId) { mutableStateOf<PastMiss?>(null) }
    LaunchedEffect(lapseId) {
        val found = vm.loadMiss(lapseId)
        if (found == null) onDone() else loaded = found
    }
    val miss = loaded ?: return
    val lapse = miss.lapse
    if (pastMissLocked(settings.locks, unlocked, miss.private)) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("🔒 Locked") },
            text = {
                Text(
                    if (settings.locks.reasons) "Reasons and notes are behind the privacy lock. Unlock to open this one."
                    else "This is a private goal. Unlock to open it.",
                    modifier = Modifier.testTag("past-miss-locked"),
                )
            },
            confirmButton = { TextButton(onClick = onUnlock, modifier = Modifier.testTag("past-miss-unlock")) { Text("Unlock") } },
            dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        )
        return
    }
    var reason by remember(lapse.id) { mutableStateOf(lapse.reason?.let { ReasonChoice(it, lapse.reasonText) }) }
    var note by remember(lapse.id) { mutableStateOf(lapse.note ?: "") }
    val own by vm.ownReasons.collectAsStateWithLifecycle()
    // Its own reason stays on offer, even after it was taken off the user's list.
    val offered = (own + listOfNotNull(lapse.reasonText?.takeIf { lapse.reason == Reason.OTHER && it.isNotBlank() })).distinct()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(lapse.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("past-miss"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pastMissLine(miss, longDate(lapse.day)) + if (lapse.cost > 0) " · over the limit: −${pointsText(lapse.cost)}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lapse.cost > 0 && !lapse.forgotten) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lapse.forgotten) {
                    Text(pastMissPutRight(miss), style = MaterialTheme.typography.bodyMedium)
                    return@Column
                }
                ReasonChips(reason, "past-reason", offered) { reason = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("past-note"),
                )
                when {
                    miss.canForget -> {
                        TextButton(
                            onClick = {
                                vm.forgotToTick(lapse.id)
                                onDone()
                            },
                            modifier = Modifier.testTag("past-forgot"),
                        ) { Text(if (miss.slip) "Undo, I didn't slip" else "I did it, I forgot to tick it") }
                        Text(
                            (if (miss.slip) "It's ticked as avoided for that day" else "It's ticked for that day") +
                                if (lapse.cost > 0) ", and what the miss cost comes back." else ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // A habit's miss from longer ago: its reason can still be changed.
                    // More was added than an "at most" goal's limit: nothing to tick.
                    miss.over -> Unit
                    lapse.kind == LapseKind.MISS && lapse.period != null -> Text(
                        "It's more than ${Lapses.FORGET_DAYS} days ago, so it can't be ${if (miss.slip) "undone" else "ticked"} from here any more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (lapse.forgotten) {
                TextButton(onClick = onDone) { Text("Close") }
            } else {
                TextButton(
                    onClick = {
                        reason?.let { vm.explain(lapse.id, it.reason, note, it.text) }
                        onDone()
                    },
                    enabled = reason != null,
                    modifier = Modifier.testTag("past-save"),
                ) { Text("Save") }
            }
        },
        dismissButton = { if (!lapse.forgotten) TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/**
 * The review's day view: the goals missed that day. Each opens to change its reason or note, or
 * to say it was a forgotten tick. With [hideReasons], while reasons and notes are locked, what
 * was said isn't shown, and opening one asks to unlock first.
 */
@Composable
fun MissedCard(misses: List<Lapse>, hideReasons: Boolean, onMiss: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("review-missed")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Missed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            misses.forEach { miss -> MissRow(miss, title = miss.label, hideReasons = hideReasons, onClick = { onMiss(miss.id) }) }
            Text(
                "Tap one to change its reason or note, or to say you forgot to tick it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MissRow(miss: Lapse, title: String, hideReasons: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp).testTag("review-miss:${miss.label}:${miss.day}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (hideReasons) "🔒 Behind the privacy lock" else missSaid(miss),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * One goal's misses in the stretch being reviewed, newest first, opened from its row when it
 * has more than one: picking a day opens that miss.
 */
@Composable
fun GoalMissesDialog(goalName: String, misses: List<Lapse>, hideReasons: Boolean, onMiss: (Long) -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(goalName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Missed ${misses.size} times in this stretch. Pick one to change its reason or note, or to say you forgot to tick it.")
                LazyColumn(Modifier.heightIn(max = 320.dp).testTag("goal-misses")) {
                    items(misses.sortedByDescending { it.day }, key = { it.id }) { miss ->
                        MissRow(miss, title = longDate(miss.day), hideReasons = hideReasons, onClick = { onMiss(miss.id) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Close") } },
    )
}
