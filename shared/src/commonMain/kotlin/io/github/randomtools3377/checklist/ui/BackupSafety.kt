package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.BackupSummary
import io.github.randomtools3377.checklist.core.ImportCheck
import io.github.randomtools3377.checklist.core.dateText
import io.github.randomtools3377.checklist.data.AutoBackupFiles
import io.github.randomtools3377.checklist.data.BackupCrypto
import io.github.randomtools3377.checklist.data.toLocalDateTime

// Backup safety: what an import replaces, said before it does; a passphrase for backup files;
// and "Erase everything and start fresh".

/** The words for what goes when everything is replaced or erased, beyond the counts. */
internal object BackupWords {
    /** What the "Replace everything?" question says about [file], the backup read, on a device holding [device]. */
    fun importText(exportedOn: String, file: BackupSummary, device: BackupSummary, balance: String, deviceWord: String, copyFolder: String?): String =
        "This backup is from $exportedOn. It has ${file.counts()}, with a balance of $balance.\n\n" +
            "Importing replaces everything on this $deviceWord with it: goals, rewards, points and history, and also " +
            "the budget (spending, bills, savings and pay), the mood journal and focus time.\n\n" +
            when {
                device.isEmpty -> "There is nothing on this $deviceWord yet, so nothing is lost."
                copyFolder != null -> "What is here now (${device.counts()}) is saved in $copyFolder first, as a \"Before import\" copy. " +
                    "\"Restore an automatic backup\" brings it back."
                else -> "What is here now (${device.counts()}) will be gone. Tap \"Export first\" to keep a copy."
            }

    const val ERASE = "This removes your goals, rewards, points, history, the budget, moods, focus time and settings, " +
        "and the app starts fresh."

    const val ERASE_KEEPS = "Backup files you already saved stay where they are."

    const val FORGOTTEN = "If you forget the passphrase, it can't be recovered, and a file protected with it can't be opened: " +
        "not by you, not by this app, not by anyone. Write it down somewhere safe."

    /** How a backup in the automatic folder is listed to restore: its day, and for a "before import" copy its time too. */
    fun listed(name: String): String {
        val day = AutoBackupFiles.dayOf(name) ?: return name
        if (!AutoBackupFiles.isBeforeImport(name)) return dateText(day)
        val time = AutoBackupFiles.timeOf(name)
        return "Before import · " + dateText(day) + (time?.let { ", " + timeText(it) } ?: "")
    }
}

/** "Replace everything?": what the file holds, what goes, and a way to keep what is here first. */
@Composable
internal fun ImportConfirmDialog(
    read: MainViewModel.BackupRead.Ready,
    /** The automatic backup's folder, when a "before import" copy will be saved there. */
    copyFolder: String?,
    onExportFirst: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val phone = LocalPhone.current
    val warning = ImportCheck.warning(read.file, read.device, phone.deviceWord)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace everything?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    BackupWords.importText(
                        dateText(read.backup.exportedAt.toLocalDateTime().date), read.file, read.device,
                        pointsText(read.file.balance), phone.deviceWord, copyFolder,
                    ),
                    modifier = Modifier.testTag("import-summary"),
                )
                if (warning != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(warning, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import-warning"))
                }
                if (!read.device.isEmpty) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onExportFirst, modifier = Modifier.testTag("import-export-first")) { Text("Export first") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-import")) {
                Text("Replace everything", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PassphraseField(value: String, label: String, shown: Boolean, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(200)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** Turning the passphrase on, or changing it: typed twice, with the warning that it can't be recovered. */
@Composable
internal fun SetPassphraseDialog(changing: Boolean, working: Boolean, onSet: (String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    val problem = BackupCrypto.problem(passphrase, again)
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(if (changing) "Change the passphrase" else "Protect backup files") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Every backup file saved from now on, exports and automatic backups, is encrypted. " +
                        "Importing one asks for the passphrase." +
                        if (changing) " Files saved before keep the passphrase they were saved with." else "",
                )
                Spacer(Modifier.height(8.dp))
                Text(BackupWords.FORGOTTEN, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("passphrase-warning"))
                Spacer(Modifier.height(8.dp))
                PassphraseField(passphrase, "Passphrase", shown, "passphrase") { passphrase = it }
                PassphraseField(again, "Type it again", shown, "passphrase-again") { again = it }
                TextButton(onClick = { shown = !shown }) { Text(if (shown) "Hide" else "Show") }
                if (working) Text("Turning it on…", style = MaterialTheme.typography.bodySmall)
                else if (problem != null && passphrase.isNotEmpty()) {
                    Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("passphrase-problem"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(passphrase) }, enabled = problem == null && !working, modifier = Modifier.testTag("passphrase-set")) {
                Text(if (changing) "Change" else "Turn on")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}

/** A protected file was picked: asks for its passphrase, and says plainly when it is wrong. */
@Composable
internal fun AskPassphraseDialog(working: Boolean, error: String?, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("This backup is protected") },
        text = {
            Column {
                Text("Type the passphrase it was saved with. Nothing changes until you have seen what's in it.")
                Spacer(Modifier.height(8.dp))
                PassphraseField(passphrase, "Passphrase", shown, "import-passphrase") { passphrase = it }
                TextButton(onClick = { shown = !shown }) { Text(if (shown) "Hide" else "Show") }
                if (working) Text("Opening…", style = MaterialTheme.typography.bodySmall)
                else if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("import-passphrase-error"))
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpen(passphrase) }, enabled = passphrase.isNotBlank() && !working, modifier = Modifier.testTag("import-passphrase-open")) {
                Text("Open")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}

/**
 * "Protect backup files with a passphrase" in Settings → Backup: off unless turned on. Turning it
 * on or off, or changing it, asks to unlock first while anything is locked ([unlockFirst]), so
 * no one else can set a passphrase the user doesn't know.
 */
@Composable
internal fun BackupPassphraseSetting(vm: MainViewModel, on: Boolean, unlockFirst: (() -> Unit) -> Unit) {
    val phone = LocalPhone.current
    var setting by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var turningOff by remember { mutableStateOf(false) }
    SwitchRow(
        "Protect backup files with a passphrase",
        on,
        "setting:backup-passphrase",
        hint = if (on) "On: exports and automatic backups are encrypted. Importing one asks for the passphrase. " +
            "The spreadsheets are not protected."
        else "Off: a backup file can be read by anything that can open the folder it is saved in.",
        padding = PaddingValues(top = 4.dp),
    ) { wanted -> unlockFirst { if (wanted) setting = true else turningOff = true } }
    if (on) {
        TextButton(onClick = { unlockFirst { setting = true } }, modifier = Modifier.testTag("backup-passphrase-change")) { Text("Change the passphrase") }
    }
    if (setting) {
        SetPassphraseDialog(
            changing = on,
            working = working,
            onSet = { passphrase ->
                working = true
                vm.setBackupPassphrase(passphrase) { ok ->
                    working = false
                    setting = false
                    phone.toast(if (ok) "On. Backup files saved from now on need your passphrase." else "Couldn't turn it on, so backup files stay as they were.")
                }
            },
            onDismiss = { setting = false },
        )
    }
    if (turningOff) {
        AlertDialog(
            onDismissRequest = { turningOff = false },
            title = { Text("Stop protecting backup files?") },
            text = { Text("Files saved from now on can be read without a passphrase. Files already saved still need theirs.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        turningOff = false
                        vm.clearBackupPassphrase()
                    },
                    modifier = Modifier.testTag("backup-passphrase-off"),
                ) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { turningOff = false }) { Text("Keep it on") } },
        )
    }
}

/**
 * "Erase everything and start fresh", at the bottom of Settings → Backup: says what goes, offers
 * "Export first", and asks once more before erasing. While anything is locked it asks to unlock
 * first ([unlockFirst]).
 */
@Composable
internal fun EraseEverything(vm: MainViewModel, unlockFirst: (() -> Unit) -> Unit, onExportFirst: () -> Unit) {
    val phone = LocalPhone.current
    var asking by remember { mutableStateOf(false) }
    var sure by remember { mutableStateOf(false) }
    Spacer(Modifier.height(12.dp))
    Text("Start fresh", style = MaterialTheme.typography.bodyLarge)
    Text(
        "Removes everything from this ${phone.deviceWord} and starts the app as new. ${BackupWords.ERASE_KEEPS}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { unlockFirst { asking = true } }, modifier = Modifier.testTag("erase")) {
        Text("Erase everything and start fresh", color = MaterialTheme.colorScheme.error)
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Erase everything?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(BackupWords.ERASE, modifier = Modifier.testTag("erase-what"))
                    Spacer(Modifier.height(8.dp))
                    Text("${BackupWords.ERASE_KEEPS} To keep a copy of what is here now, tap \"Export first\".")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onExportFirst, modifier = Modifier.testTag("erase-export-first")) { Text("Export first") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        sure = true
                    },
                    modifier = Modifier.testTag("erase-continue"),
                ) { Text("Erase everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
    if (sure) {
        AlertDialog(
            onDismissRequest = { sure = false },
            title = { Text("Are you sure?") },
            text = { Text("This can't be undone from inside the app. Only a backup file can bring your data back.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        sure = false
                        vm.eraseEverything { phone.toast(it) }
                    },
                    modifier = Modifier.testTag("erase-confirm"),
                ) { Text("Yes, erase everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { sure = false }, modifier = Modifier.testTag("erase-keep")) { Text("Keep everything") } },
        )
    }
}
