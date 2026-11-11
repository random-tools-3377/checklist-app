package io.github.randomtools3377.checklist.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.randomtools3377.checklist.ui.ChecklistRoot
import io.github.randomtools3377.checklist.ui.MainViewModel
import kotlinx.coroutines.delay
import platform.UIKit.UIViewController

/** The one phone this app runs on, made once. */
private val phone by lazy { IosPhone() }

/**
 * The whole iPhone app, for the Swift side to show: the shared screens on this iPhone, with
 * misses looked for whenever the app comes to the front, and the privacy lock closing when it
 * goes away.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    val vm = remember { MainViewModel(phone) }
    LifecycleStartEffect(Unit) {
        IosReminders.refreshAllowed()
        vm.closeDays()
        onStopOrDispose { vm.lockAgain() }
    }
    Box(Modifier.fillMaxSize()) {
        ChecklistRoot(phone, vm)
        Message(phone)
    }
}

/** A short message at the bottom, for a few seconds: the iPhone has no toasts of its own. */
@Composable
private fun Message(phone: IosPhone) {
    val text by phone.messages.collectAsState()
    LaunchedEffect(text) {
        if (text != null) {
            delay(3_500)
            phone.messages.value = null
        }
    }
    text?.let { shown ->
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
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
