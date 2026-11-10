package io.github.randomtools3377.checklist.ai

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.github.randomtools3377.checklist.core.AiContract

/**
 * Where the checklist app asks the AI. Only an app holding [AiContract.PERMISSION], which only
 * apps signed with the same key can, gets an answer. Nothing is stored: each question is
 * answered and forgotten.
 */
class AiProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context ?: return Bundle()
        // The framework doesn't check permissions for call(), so this does.
        if (context.checkCallingPermission(AiContract.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Only the checklist app may ask Checklist AI.")
        }
        return when (method) {
            AiContract.METHOD_STATUS -> Bundle().apply {
                putString(AiContract.KEY_VERSION, BuildConfig.VERSION_NAME)
                putBoolean(AiContract.KEY_READY, AiFileStore.ready(context))
                putBoolean(AiContract.KEY_SPEECH_READY, SpeechStore.ready(context))
            }
            AiContract.METHOD_GENERATE -> {
                val asked = extras ?: Bundle()
                try {
                    val text = Brain.answer(
                        context,
                        system = asked.getString(AiContract.KEY_SYSTEM).orEmpty(),
                        prompt = asked.getString(AiContract.KEY_PROMPT).orEmpty(),
                        schema = asked.getString(AiContract.KEY_SCHEMA),
                        maxTokens = asked.getInt(AiContract.KEY_MAX_TOKENS, 256).coerceIn(16, 1024),
                    )
                    Bundle().apply { putString(AiContract.KEY_TEXT, text) }
                } catch (e: Throwable) {
                    Bundle().apply { putString(AiContract.KEY_ERROR, e.message ?: e.javaClass.simpleName) }
                }
            }
            else -> Bundle()
        }
    }

    // Not a table of anything: only call() is used.
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
