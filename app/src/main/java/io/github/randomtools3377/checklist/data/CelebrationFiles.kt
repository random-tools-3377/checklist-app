package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.core.CelebrationSounds
import java.io.File
import java.io.InputStream
import kotlin.random.Random

/**
 * The celebration GIFs and sounds the user picked, copied into the app's own storage. The
 * pickers only lend a file for a moment, so a copy is kept. Nothing else can read this folder.
 */
class CelebrationFiles(filesDir: File) : GifStore {
    private val dir = File(filesDir, "celebrations")

    /** The file for [name], or null if [name] isn't a plain GIF or sound file name. */
    fun file(name: String): File? = if (Celebration.isStoredName(name)) File(dir, name) else null

    override fun read(name: String): ByteArray? = file(name)?.takeIf { it.isFile }?.readBytes()

    override fun write(name: String, bytes: ByteArray) {
        val file = file(name) ?: return
        dir.mkdirs()
        file.writeBytes(bytes)
    }

    /** Deletes the files no goal uses any more. */
    override fun keepOnly(names: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in names) it.delete() }
    }

    /**
     * Copies a picked GIF in and returns its new file name, or an [AddError] when it isn't a GIF
     * or is too big.
     */
    fun add(input: InputStream): Result<String> {
        val bytes = readUpTo(input, MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) return Result.failure(AddError("That GIF is too big. Pick one under ${MAX_BYTES / 1_048_576} MB."))
        val header = bytes.copyOf(minOf(6, bytes.size)).decodeToString()
        if (header != "GIF87a" && header != "GIF89a") return Result.failure(AddError("That file isn't a GIF."))
        val name = "own-" + Random.nextBytes(8).joinToString("") { "%02x".format(it) } + ".gif"
        write(name, bytes)
        return Result.success(name)
    }

    /**
     * Copies a picked sound in and returns its new file name, or an [AddError] when it is too
     * big or [plays] says the phone can't play the copy.
     */
    fun addSound(input: InputStream, plays: (File) -> Boolean): Result<String> {
        val bytes = readUpTo(input, CelebrationSounds.MAX_BYTES + 1)
        if (bytes.size > CelebrationSounds.MAX_BYTES) return Result.failure(AddError(CelebrationSounds.TOO_BIG))
        val name = CelebrationSounds.newName(Random.nextBytes(8))
        val file = file(name) ?: return Result.failure(AddError(CelebrationSounds.CANT_OPEN))
        write(name, bytes)
        if (bytes.isEmpty() || !plays(file)) {
            file.delete()
            return Result.failure(AddError(CelebrationSounds.CANT_PLAY))
        }
        return Result.success(name)
    }

    /** The first [limit] bytes of [input], which is closed. */
    private fun readUpTo(input: InputStream, limit: Int): ByteArray = input.use { stream ->
        val buffer = ByteArray(limit)
        var read = 0
        while (read < buffer.size) {
            val n = stream.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        buffer.copyOf(read)
    }

    class AddError(message: String) : Exception(message)

    companion object {
        const val MAX_BYTES = 10 * 1_048_576
    }
}
