package io.github.randomtools3377.checklist.ai

import android.content.Context
import android.net.Uri
import io.github.randomtools3377.checklist.core.AiFile
import java.io.File
import java.security.MessageDigest

/**
 * The AI file in this app's own storage. It is copied in from the file the user picked, and
 * checked as it is copied: it must be exactly [AiFile.SIZE] bytes with the checksum
 * [AiFile.SHA256], so a broken or different download is never used.
 */
object AiFileStore {
    private fun folder(context: Context) = File(context.noBackupFilesDir, "ai").apply { mkdirs() }

    fun file(context: Context): File = File(folder(context), AiFile.FILE)

    /** The checksum it was checked against when copied in, kept beside it. */
    private fun mark(context: Context) = File(folder(context), AiFile.FILE + ".checked")

    /** Whether the right AI file is here, checked when it was copied in. */
    fun ready(context: Context): Boolean {
        val file = file(context)
        return file.isFile && file.length() == AiFile.SIZE &&
            runCatching { mark(context).readText().trim() }.getOrNull() == AiFile.SHA256
    }

    /** How copying in a picked file ended. */
    sealed interface Result {
        data object Ready : Result
        data class WrongSize(val size: Long) : Result
        data object WrongFile : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Copies the file at [uri] in, checking it on the way, and reports how far along it is
     * from 0 to 1. Anything but the right file is deleted again.
     */
    fun copyIn(context: Context, uri: Uri, progress: (Float) -> Unit): Result {
        val part = File(folder(context), AiFile.FILE + ".part")
        try {
            val resolver = context.contentResolver
            val declared = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (declared >= 0 && declared != AiFile.SIZE) return Result.WrongSize(declared)
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            val input = resolver.openInputStream(uri) ?: return Result.Failed("The file couldn't be opened.")
            input.use { stream ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(1 shl 20)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > AiFile.SIZE) return Result.WrongSize(copied)
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        progress(copied.toFloat() / AiFile.SIZE)
                    }
                }
            }
            if (copied != AiFile.SIZE) return Result.WrongSize(copied)
            val sum = digest.digest().joinToString("") { "%02x".format(it) }
            if (sum != AiFile.SHA256) return Result.WrongFile
            val target = file(context)
            target.delete()
            if (!part.renameTo(target)) return Result.Failed("The file couldn't be stored. Is the phone nearly full?")
            mark(context).writeText(sum)
            return Result.Ready
        } catch (e: Exception) {
            return Result.Failed("The file couldn't be read: ${e.message ?: "unknown error"}.")
        } finally {
            part.delete()
        }
    }

    /** Removes the AI file to free the space. */
    fun remove(context: Context) {
        Brain.close()
        file(context).delete()
        mark(context).delete()
    }
}
