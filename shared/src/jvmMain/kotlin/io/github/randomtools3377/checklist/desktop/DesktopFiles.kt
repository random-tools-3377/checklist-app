package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.data.GifStore
import io.github.randomtools3377.checklist.data.PhotoStore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Writes [bytes] to [file] through a temporary file beside it, so a half-written file never replaces a whole one. */
internal fun writeWhole(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, file.name + ".part")
    temporary.writeBytes(bytes)
    try {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/** The GIFs the user picked for celebrations, in the app's folder under celebrations. */
internal class DesktopGifs(private val folder: File) : GifStore {
    private fun file(name: String): File? = if (Celebration.isSafeName(name)) File(folder, name) else null

    override fun read(name: String): ByteArray? = file(name)?.takeIf { it.isFile }?.readBytes()

    override fun write(name: String, bytes: ByteArray) {
        file(name)?.let { writeWhole(it, bytes) }
    }

    override fun keepOnly(names: Set<String>) {
        folder.listFiles().orEmpty().filter { it.isFile && it.name !in names }.forEach { it.delete() }
    }
}

/** The photo behind Today, in the app's folder as background.jpg. */
internal class DesktopPhoto(private val file: File) : PhotoStore {
    override fun read(): ByteArray? = file.takeIf { it.isFile }?.readBytes()

    override fun write(bytes: ByteArray) = writeWhole(file, bytes)

    override fun delete() {
        file.delete()
    }
}

/** Small values kept on this computer only, like the emoji picked lately, in one properties file. */
internal class LocalValues(private val file: File) {
    private val values = Properties().apply {
        if (file.isFile) runCatching { file.reader(Charsets.UTF_8).use { load(it) } }
    }

    @Synchronized
    fun string(key: String): String? = values.getProperty(key)

    @Synchronized
    fun setString(key: String, value: String?) {
        if (value == null) values.remove(key) else values.setProperty(key, value)
        val text = java.io.StringWriter().also { values.store(it, "Checklist: small values kept on this computer only") }.toString()
        runCatching { writeWhole(file, text.toByteArray(Charsets.UTF_8)) }
    }

    fun long(key: String): Long? = string(key)?.toLongOrNull()

    fun setLong(key: String, value: Long?) = setString(key, value?.toString())
}
