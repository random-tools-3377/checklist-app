package io.github.randomtools3377.checklist.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.randomtools3377.checklist.BuildConfig
import io.github.randomtools3377.checklist.core.AiContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Asks Checklist AI, through the door only apps signed with the same key can use. */
object AiClient {
    private val uri: Uri = Uri.parse("content://${AiContract.AUTHORITY}")

    /** Where both apps are downloaded. */
    const val RELEASES = "https://github.com/random-tools-3377/checklist-app/releases/latest"

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(AiContract.PACKAGE, 0) }.isSuccess

    suspend fun status(context: Context): AiStatus = withContext(Dispatchers.IO) {
        if (!installed(context)) return@withContext AiStatus(installed = false, appVersion = BuildConfig.VERSION_NAME)
        try {
            val answer = context.contentResolver.call(uri, AiContract.METHOD_STATUS, null, null)
                ?: return@withContext AiStatus(installed = true, problem = "no answer", appVersion = BuildConfig.VERSION_NAME)
            AiStatus(
                installed = true,
                appVersion = BuildConfig.VERSION_NAME,
                version = answer.getString(AiContract.KEY_VERSION),
                ready = answer.getBoolean(AiContract.KEY_READY),
                speechReady = answer.getBoolean(AiContract.KEY_SPEECH_READY),
            )
        } catch (e: Exception) {
            AiStatus(installed = true, problem = e.message ?: e.javaClass.simpleName, appVersion = BuildConfig.VERSION_NAME)
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
