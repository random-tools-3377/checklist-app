package io.github.randomtools3377.checklist.ai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.AiContract
import io.github.randomtools3377.checklist.core.AiFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Checklist AI's only screen: get the AI file in the browser, pick it here to check and store
 * it, or remove it. The AI itself is used from the checklist app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme(primary = Color(0xFFB9C3FF)) else lightColorScheme(primary = Color(0xFF3F4A8A)),
            ) {
                Surface(Modifier.fillMaxSize()) { AiScreen() }
            }
        }
    }
}

/** Where picking the file has got to. */
private sealed interface Picking {
    data object Idle : Picking
    data class Checking(val share: Float) : Picking
    data class Done(val message: String, val good: Boolean) : Picking
}

@Composable
private fun AiScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(AiFileStore.ready(context)) }
    var picking by remember { mutableStateOf<Picking>(Picking.Idle) }
    var share by remember { mutableFloatStateOf(0f) }
    val checklistInstalled = remember {
        runCatching { context.packageManager.getPackageInfo(AiContract.PACKAGE.removeSuffix(".ai"), 0) }.isSuccess
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        picking = Picking.Checking(0f)
        scope.launch {
            val result = withContext(Dispatchers.IO) { AiFileStore.copyIn(context, uri) { share = it } }
            ready = AiFileStore.ready(context)
            picking = when (result) {
                AiFileStore.Result.Ready -> Picking.Done("Done: the AI file is checked and ready. You can delete the download from your Downloads folder to free the space.", true)
                is AiFileStore.Result.WrongSize -> Picking.Done(
                    "That isn't the AI file: it's ${result.size / 1_000_000} MB, and the AI file is ${AiFile.sizeText}. If the download stopped part way, download it again.",
                    false,
                )
                AiFileStore.Result.WrongFile -> Picking.Done("That file is the right size but not the right file, or it was damaged. Download it again.", false)
                is AiFileStore.Result.Failed -> Picking.Done(result.reason, false)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .wrapContentWidth()
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("✨ Checklist AI", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This app runs a small AI on your phone for the Checklist app: filling in a goal from a sentence you type, and more to come. " +
                "It has no internet access, so nothing you write leaves the phone.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (ready) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✅ Ready", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The AI file (${AiFile.NAME}, ${AiFile.sizeText}) is checked and stored in this app. " +
                            "Turn on \"AI helper\" in the Checklist app: Settings → Features.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = {
                        AiFileStore.remove(context)
                        ready = false
                        picking = Picking.Idle
                    }) { Text("Remove the AI file") }
                    Text(
                        "Removing it frees ${AiFile.sizeText}. The AI stops working until you add it again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Download the AI file", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${AiFile.NAME}, ${AiFile.sizeText}, ${AiFile.LICENCE} licence. Your browser downloads it once, from Hugging Face. " +
                            "Wi-Fi is best: it's a big file.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AiFile.URL)))
                        } catch (e: ActivityNotFoundException) {
                            picking = Picking.Done("There's no browser on this phone to download it with.", false)
                        }
                    }) { Text("Download in the browser") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Pick the downloaded file", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "When the download has finished, pick ${AiFile.FILE} from your Downloads. This app checks it is exactly the right file, then keeps its own copy.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { pick.launch(arrayOf("*/*")) },
                        enabled = picking !is Picking.Checking,
                    ) { Text("Pick the file") }
                }
            }
        }

        when (val now = picking) {
            Picking.Idle -> Unit
            is Picking.Checking -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Checking and copying… ${(share * 100).toInt()}%. Keep this screen open.", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
            }
            is Picking.Done -> Text(
                now.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (now.good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        if (!checklistInstalled) {
            Text(
                "The Checklist app isn't on this phone. Install it from the same Releases page; this app does nothing on its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = {
            context.packageManager.getLaunchIntentForPackage(AiContract.PACKAGE.removeSuffix(".ai"))?.let(context::startActivity)
        }, enabled = checklistInstalled) { Text("Open Checklist") }
        Text(
            "Checklist AI ${BuildConfig.VERSION_NAME}. It is released with the Checklist app, with the same version: update both together.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
