package io.github.randomtools3377.checklist.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.randomtools3377.checklist.BuildConfig
import io.github.randomtools3377.checklist.core.AiContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What this app knows about Checklist AI, the separate app that runs the AI. [version] is its
 * version when it answered; [ready] whether its AI file is added; [problem] why it didn't answer.
 */
data class AiStatus(
    val installed: Boolean,
    val version: String? = null,
    val ready: Boolean = false,
    val problem: String? = null,
    /** Whether Checklist AI's speech file is added, for voice. */
    val speechReady: Boolean = false,
) {
    /** Whether the AI can be asked now. */
    val usable: Boolean get() = installed && ready && problem == null

    /** Whether the two apps are different versions, which they shouldn't be. */
    val outOfStep: Boolean get() = version != null && version != BuildConfig.VERSION_NAME
}

/** Asks Checklist AI, through the door only apps signed with the same key can use. */
object AiClient {
    private val uri: Uri = Uri.parse("content://${AiContract.AUTHORITY}")

    /** Where both apps are downloaded. */
    const val RELEASES = "https://github.com/random-tools-3377/checklist-app/releases/latest"

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(AiContract.PACKAGE, 0) }.isSuccess

    suspend fun status(context: Context): AiStatus = withContext(Dispatchers.IO) {
        if (!installed(context)) return@withContext AiStatus(installed = false)
        try {
            val answer = context.contentResolver.call(uri, AiContract.METHOD_STATUS, null, null)
                ?: return@withContext AiStatus(installed = true, problem = "no answer")
            AiStatus(
                installed = true,
                version = answer.getString(AiContract.KEY_VERSION),
                ready = answer.getBoolean(AiContract.KEY_READY),
                speechReady = answer.getBoolean(AiContract.KEY_SPEECH_READY),
            )
        } catch (e: Exception) {
            AiStatus(installed = true, problem = e.message ?: e.javaClass.simpleName)
        }
    }

    /** The AI's answer to [prompt], or why there isn't one. Can take a while: call it off the main thread. */
    suspend fun ask(context: Context, system: String, prompt: String, schema: String?, maxTokens: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val extras = Bundle().apply {
                    putString(AiContract.KEY_SYSTEM, system)
                    putString(AiContract.KEY_PROMPT, prompt)
                    putString(AiContract.KEY_SCHEMA, schema)
                    putInt(AiContract.KEY_MAX_TOKENS, maxTokens)
                }
                val answer = context.contentResolver.call(uri, AiContract.METHOD_GENERATE, null, extras)
                answer?.getString(AiContract.KEY_TEXT)?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException(answer?.getString(AiContract.KEY_ERROR) ?: "Checklist AI didn't answer."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Checklist AI's listening screen, for one voice command; it answers with the words heard. */
    fun listenIntent(): Intent = Intent(AiContract.ACTION_LISTEN).setPackage(AiContract.PACKAGE)

    /** Opens Checklist AI, to add or remove its AI file. */
    fun open(context: Context) {
        context.packageManager.getLaunchIntentForPackage(AiContract.PACKAGE)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Opens the Releases page in the browser, where Checklist AI is downloaded. */
    fun openReleases(context: Context) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
