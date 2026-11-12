package io.github.randomtools3377.checklist.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.ui.ChecklistRoot
import io.github.randomtools3377.checklist.ui.MainViewModel
import kotlinx.coroutines.delay
import java.awt.GraphicsEnvironment
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/** The version, from the build (see desktop/build.gradle.kts), or from the installer's launcher. */
private fun appVersion(): String =
    System.getProperty("checklist.version") ?: System.getProperty("jpackage.app-version") ?: ""

/** About the size of a phone, but never taller than the screen has room for. */
private fun startSize(): DpSize {
    val room = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.height }.getOrNull()
    val height = room?.let { minOf(820, it - 16) } ?: 820
    return DpSize(420.dp, height.coerceAtLeast(480).dp)
}

/**
 * The Windows app: the shared screens in a window on this computer, with misses looked for when
 * the window comes back and as the day ends, and the privacy lock closing when it is minimised
 * or closed. Closing the window keeps the app running in the notification area, so reminders
 * still come, until "Quit" on its icon there (unless that is turned off in Settings, or the
 * computer has no notification area: then closing quits). Started with --hidden, as Windows does
 * at sign-in when that is turned on, it opens there with no window. Only one copy runs at a
 * time, since both would use the same database: opening Checklist again brings back the window
 * of the copy that is running.
 */
fun main(args: Array<String>) {
    val hidden = StartupShortcut.HIDDEN in args
    val phone = DesktopPhone(appVersion = appVersion())
    if (!phone.holdFolder()) {
        // Windows starting it again at sign-in has nothing to show; opened by hand, the running copy's window comes back.
        if (!hidden && !phone.askToShow()) {
            JOptionPane.showMessageDialog(null, "Checklist is already open on this computer.", "Checklist", JOptionPane.INFORMATION_MESSAGE)
        }
        exitProcess(0)
    }
    // With no notification area there would be no way to reach a hidden app, so its window opens.
    if (hidden && DesktopReminders.canShow()) phone.windowShown.value = false
    phone.start()
    application {
        val vm = remember { MainViewModel(phone) }
        val state = rememberWindowState(position = WindowPosition(Alignment.Center), size = remember { startSize() })
        val icon = remember { BitmapPainter(checklistIcon(256).toComposeImageBitmap()) }
        val shown by phone.windowShown.collectAsState()
        val quit = {
            vm.lockAgain()
            phone.close()
            exitApplication()
        }
        DisposableEffect(Unit) {
            phone.onQuit = quit
            onDispose { phone.onQuit = null }
        }
        // Started without its window, or back from the notification area: misses are looked
        // for, as when the app is opened. (Opened with its window, the window does this itself.)
        val startedHidden = remember { !shown }
        val wasHidden = remember { booleanArrayOf(false) }
        LaunchedEffect(shown) {
            if (!shown) {
                if (startedHidden && !wasHidden[0]) vm.closeDays()
                wasHidden[0] = true
            } else if (wasHidden[0]) {
                wasHidden[0] = false
                vm.closeDays()
            }
        }
        // The app can stay running past the end of the day, with or without its window, or the
        // computer can sleep through it: misses are looked for again whenever the hour has changed.
        LaunchedEffect(Unit) {
            var hour = nowLocal().let { it.date to it.hour }
            while (true) {
                delay(60_000)
                val now = nowLocal().let { it.date to it.hour }
                if (now != hour) {
                    hour = now
                    vm.closeDays()
                }
            }
        }
        Window(
            onCloseRequest = {
                vm.lockAgain()
                if (!phone.hideWindow()) quit()
            },
            state = state,
            visible = shown,
            title = "Checklist",
            icon = icon,
            resizable = true,
        ) {
            DisposableEffect(window) {
                phone.window = window
                onDispose { phone.window = null }
            }
            LifecycleStartEffect(Unit) {
                vm.closeDays()
                onStopOrDispose { vm.lockAgain() }
            }
            Box(Modifier.fillMaxSize()) {
                ChecklistRoot(phone, vm)
                Message(phone)
            }
        }
    }
    // Quit means gone: nothing is left running out of sight, holding the app's folder.
    exitProcess(0)
}

/** A short message at the bottom, for a few seconds: a computer has no toasts of its own. */
@Composable
private fun Message(phone: DesktopPhone) {
    val text by phone.messages.collectAsState()
    LaunchedEffect(text) {
        if (text != null) {
            delay(3_500)
            phone.messages.value = null
        }
    }
    text?.let { shown ->
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 6.dp,
            ) {
                Text(shown, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
