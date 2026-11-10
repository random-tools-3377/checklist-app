package io.github.randomtools3377.checklist.ai

import android.content.Context
import android.net.Uri
import io.github.randomtools3377.checklist.core.SpeechFile
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The speech file for voice, in this app's own storage: the zip the user picked, checked by
 * size and checksum as it is read, then unpacked. Like [AiFileStore], a wrong or broken file is
 * never used.
 */
object SpeechStore {
    private fun folder(context: Context) = File(context.noBackupFilesDir, "speech").apply { mkdirs() }

    /** The unpacked model, for Vosk. */
    fun model(context: Context): File = File(folder(context), SpeechFile.FOLDER)

    private fun mark(context: Context) = File(folder(context), SpeechFile.FOLDER + ".checked")

    fun ready(context: Context): Boolean =
        model(context).isDirectory && runCatching { mark(context).readText().trim() }.getOrNull() == SpeechFile.SHA256

    /** Checks the zip at [uri] and unpacks it, reporting how far along it is from 0 to 1. */
    fun copyIn(context: Context, uri: Uri, progress: (Float) -> Unit): AiFileStore.Result {
        val zip = File(folder(context), SpeechFile.FILE + ".part")
        val unpacking = File(folder(context), SpeechFile.FOLDER + ".part")
        try {
            val resolver = context.contentResolver
            val declared = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (declared >= 0 && declared != SpeechFile.SIZE) return AiFileStore.Result.WrongSize(declared)
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            val input = resolver.openInputStream(uri) ?: return AiFileStore.Result.Failed("The file couldn't be opened.")
            input.use { stream ->
                zip.outputStream().use { out ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > SpeechFile.SIZE) return AiFileStore.Result.WrongSize(copied)
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        progress(copied.toFloat() / SpeechFile.SIZE * 0.5f)
                    }
                }
            }
            if (copied != SpeechFile.SIZE) return AiFileStore.Result.WrongSize(copied)
            val sum = digest.digest().joinToString("") { "%02x".format(it) }
            if (sum != SpeechFile.SHA256) return AiFileStore.Result.WrongFile
            unpacking.deleteRecursively()
            unzip(zip, unpacking) { progress(0.5f + it * 0.5f) }
            // The zip holds one folder, the model; it goes where Vosk looks.
            val inside = File(unpacking, SpeechFile.FOLDER).takeIf { it.isDirectory } ?: unpacking
            val target = model(context)
            target.deleteRecursively()
            if (!inside.renameTo(target)) return AiFileStore.Result.Failed("The speech file couldn't be stored. Is the phone nearly full?")
            mark(context).writeText(sum)
            return AiFileStore.Result.Ready
        } catch (e: Exception) {
            return AiFileStore.Result.Failed("The file couldn't be read: ${e.message ?: "unknown error"}.")
        } finally {
            zip.delete()
            unpacking.deleteRecursively()
        }
    }

    /** Unpacks [zip] into [into], refusing any entry that would land outside it. */
    private fun unzip(zip: File, into: File, progress: (Float) -> Unit) {
        val root = into.canonicalFile.apply { mkdirs() }
        val total = zip.length().coerceAtLeast(1)
        var read = 0L
        ZipInputStream(zip.inputStream().buffered()).use { entries ->
            while (true) {
                val entry = entries.nextEntry ?: break
                val out = File(root, entry.name).canonicalFile
                require(out.path.startsWith(root.path + File.separator)) { "The speech file has an entry outside its folder." }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { read += entries.copyTo(it) }
                    progress((read.toFloat() / (total * 2)).coerceAtMost(1f))
                }
            }
        }
    }

    fun remove(context: Context) {
        model(context).deleteRecursively()
        mark(context).delete()
    }
}
