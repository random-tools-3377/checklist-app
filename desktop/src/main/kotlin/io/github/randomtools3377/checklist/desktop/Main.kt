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
 * or closed. Only one copy runs at a time, since both would use the same database.
 */
fun main() {
    val phone = DesktopPhone(appVersion = appVersion())
    if (!phone.holdFolder()) {
        JOptionPane.showMessageDialog(null, "Checklist is already open on this computer.", "Checklist", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }
    phone.start()
    application {
        val vm = remember { MainViewModel(phone) }
        val state = rememberWindowState(position = WindowPosition(Alignment.Center), size = remember { startSize() })
        val icon = remember { BitmapPainter(checklistIcon(256).toComposeImageBitmap()) }
        Window(
            onCloseRequest = {
                vm.lockAgain()
                phone.close()
                exitApplication()
            },
            state = state,
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
            // The app can stay open past the end of the day, or the computer can sleep through
            // it: misses are looked for again whenever the hour has changed.
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
            Box(Modifier.fillMaxSize()) {
                ChecklistRoot(phone, vm)
                Message(phone)
            }
        }
    }
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
