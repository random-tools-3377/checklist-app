@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.randomtools3377.checklist.BuildConfig
import io.github.randomtools3377.checklist.Privacy
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.GapSettings
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.dateText
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.reminders.Reminders
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

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingsScreen(
    settings: Settings,
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var choosingHour by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Backup?>(null) }
    var hideInRecents by remember { mutableStateOf(Privacy.hideInRecents(context)) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri) { toast(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.readBackup(uri) { backup, error ->
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
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
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
                    label = "Finish each day's goals by",
                    time = settings.defaultFinish,
                    onChange = { vm.setDefaultFinish(it) },
                    tag = "setting:finish",
                    emptyText = "Not set, so today's pace is off",
                    hint = "Today shows how fast to go to be done by then. A goal can have its own finish time instead.",
                )
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Streaks")
            StreakSettingsRow(settings.streak) { vm.setStreakSettings(it) }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Penalty tasks")
            PenaltyTaskList(vm)

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Gaps")
            GapSettingsRow(settings.gaps) { vm.setGapSettings(it) }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Notifications")
            NotificationSettings()

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Backup")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Phone backup is off for this app, so this file is your backup. Keep it somewhere safe. " +
                        "It also moves your data to another phone, Android or iPhone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    FilledTonalButton(
                        onClick = { exportLauncher.launch("checklist-backup-${nowLocal().date}.json") },
                        modifier = Modifier.testTag("export"),
                    ) { Text("Export everything") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.testTag("import"),
                    ) { Text("Import a backup") }
                }
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Privacy")
            Text(
                "Pick what sits behind the lock. Unlocking uses your phone's own lock, and stays open until you leave the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LockSwitch("Points history", settings.locks.history, "lock:history") { vm.setLock(LOCK_HISTORY, it) }
            LockSwitch("The shop and bought rewards", settings.locks.shop, "lock:shop") { vm.setLock(LOCK_SHOP, it) }
            LockSwitch("Goals marked private", settings.locks.privateGoals, "lock:private") { vm.setLock(LOCK_PRIVATE, it) }
            LockSwitch("Reasons and notes for skips and misses", settings.locks.reasons, "lock:reasons") { vm.setLock(LOCK_REASONS, it) }
            LockSwitch("Gap check-ins", settings.locks.gaps, "lock:gaps") { vm.setLock(LOCK_GAPS, it) }
            LockSwitch("The review and stats", settings.locks.review, "lock:review") { vm.setLock(LOCK_REVIEW, it) }
            if (settings.locks.privateGoals) {
                Text(
                    "While locked, private goals are left off the widget, and their notifications don't name them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    "The app's preview is hidden in the recent-apps screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Hide in recent apps", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "On this phone, hiding the preview also blocks screenshots of the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = hideInRecents,
                        onCheckedChange = {
                            hideInRecents = it
                            Privacy.setHideInRecents(context, it)
                            context.findActivity()?.let { activity -> Privacy.apply(activity) }
                        },
                    )
                }
            }
            Text(
                "This app has no internet access and is left out of phone backups. Everything stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
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
                        "Importing replaces everything on this phone with it. Export first if you want to keep what is here now.",
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
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Reminders.canNotify(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = Reminders.canNotify(context) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (allowed) "Reminders are on. Each habit sets its own in its editor."
            else "Reminders are off for this app, so habits with a set time or a reminder can't notify you.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!allowed) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
                modifier = Modifier.testTag("allow-notifications"),
            ) { Text("Turn on reminders") }
        }
        if (!Reminders.exactAlarmsAllowed(context)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "This phone lets reminders arrive only roughly on time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }) {
                    Text("Allow exact times")
                }
            }
        }
        Text(
            "Add the Checklist calendar to your home screen from the phone's widget list. Make it wider for the week, taller for the month.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LockSwitch(label: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
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
            hint = "At ${current.describe()}, the rest can be missed or skipped without breaking it. Other streaks count days with no misses, no skips, nothing over the limit, and everything done.",
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
            label = "Or points lost within a week",
            value = points,
            onValueChange = {
                points = it
                it.toIntOrNull()?.takeIf { n -> n >= 1 }?.let { n -> onChange(current.copy(points = n)) }
            },
            tag = "setting:gap-points",
            hint = "Points taken off for going over the limit, within any 7 days in a row.",
        )
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
