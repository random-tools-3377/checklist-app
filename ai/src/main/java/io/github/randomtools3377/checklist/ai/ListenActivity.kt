package io.github.randomtools3377.checklist.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.randomtools3377.checklist.core.AiContract
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import kotlin.concurrent.thread

/**
 * Listens for one short command and gives the words back to the checklist app, which works out
 * what they mean and asks before doing anything. Only apps signed with the same key can open it.
 * The sound is never stored: Vosk turns it into words on the phone as it arrives.
 */
class ListenActivity : ComponentActivity(), RecognitionListener {
    private var heard by mutableStateOf("")
    private var status by mutableStateOf("Getting ready…")
    private var listening by mutableStateOf(false)
    private var model: Model? = null
    private var service: SpeechService? = null

    private val askMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else finishWith(null, "Voice needs the microphone. You can allow it in the phone's settings for Checklist AI.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFB9C3FF)) else lightColorScheme(primary = Color(0xFF3F4A8A))) {
                Surface {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🎙 " + status, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Say \"done\" and a goal, like \"done reading\", or \"add\" and a to-do, like \"add buy milk\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (listening) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (heard.isNotEmpty()) Text("\"$heard\"", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { finishWith(null, null) }) { Text("Cancel") }
                    }
                }
            }
        }
        if (!SpeechStore.ready(this)) {
            finishWith(null, "Voice needs its speech file. Open Checklist AI to add it.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun start() {
        status = "Getting ready…"
        thread(name = "speech") {
            try {
                val loaded = Model(SpeechStore.model(this).path)
                val recognizer = Recognizer(loaded, SAMPLE_RATE)
                val speech = SpeechService(recognizer, SAMPLE_RATE)
                runOnUiThread {
                    if (isFinishing) {
                        speech.shutdown()
                        loaded.close()
                        return@runOnUiThread
                    }
                    model = loaded
                    service = speech
                    listening = true
                    status = "Listening…"
                    speech.startListening(this, LISTEN_MILLIS)
                }
            } catch (e: Exception) {
                runOnUiThread { finishWith(null, "Voice couldn't start: ${e.message ?: "unknown error"}.") }
            }
        }
    }

    private fun words(json: String?, key: String): String =
        runCatching { JSONObject(json ?: "{}").optString(key) }.getOrDefault("").trim()

    override fun onPartialResult(hypothesis: String?) {
        heard = words(hypothesis, "partial")
    }

    override fun onResult(hypothesis: String?) {
        val text = words(hypothesis, "text")
        if (text.isNotEmpty()) finishWith(text, null)
    }

    override fun onFinalResult(hypothesis: String?) {
        val text = words(hypothesis, "text")
        finishWith(text.ifEmpty { null }, if (text.isEmpty()) "Nothing heard." else null)
    }

    override fun onError(exception: Exception?) {
        finishWith(null, "Voice stopped: ${exception?.message ?: "unknown error"}.")
    }

    override fun onTimeout() {
        finishWith(heard.ifEmpty { null }, if (heard.isEmpty()) "Nothing heard." else null)
    }

    private var finished = false

    /** Gives [text] back, or [problem] to show, once. */
    private fun finishWith(text: String?, problem: String?) {
        if (finished) return
        finished = true
        listening = false
        val result = Intent()
        if (text != null) result.putExtra(AiContract.KEY_TEXT, text)
        if (problem != null) result.putExtra(AiContract.KEY_ERROR, problem)
        setResult(if (text != null) RESULT_OK else RESULT_CANCELED, result)
        finish()
    }

    override fun onDestroy() {
        service?.stop()
        service?.shutdown()
        model?.close()
        super.onDestroy()
    }

    private companion object {
        const val SAMPLE_RATE = 16000f
        const val LISTEN_MILLIS = 8000
    }
}
