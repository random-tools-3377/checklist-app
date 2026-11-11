@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.github.randomtools3377.checklist.ios

import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.data.GifStore
import io.github.randomtools3377.checklist.data.PhotoStore
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/** The app's own folder, left out of iCloud backups (see appDataFolder). */
internal fun documents(): String = io.github.randomtools3377.checklist.data.appDataFolder()

/** A file in the app's temporary folder, for handing to and from the iPhone's dialogs. */
internal fun temporary(name: String): String = NSTemporaryDirectory().trimEnd('/') + "/" + name

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

internal fun readFile(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()

internal fun writeFile(path: String, bytes: ByteArray): Boolean = bytes.toNSData().writeToFile(path, atomically = true)

internal fun removeFile(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, null)
}

/** The GIFs the user picked for celebrations, in Documents/celebrations. */
internal class IosGifs : GifStore {
    private val folder = documents() + "/celebrations"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(folder, true, null, null)
    }

    private fun path(name: String): String? = if (Celebration.isSafeName(name)) "$folder/$name" else null

    override fun read(name: String): ByteArray? = path(name)?.let(::readFile)

    override fun write(name: String, bytes: ByteArray) {
        path(name)?.let { writeFile(it, bytes) }
    }

    override fun keepOnly(names: Set<String>) {
        val present = NSFileManager.defaultManager.contentsOfDirectoryAtPath(folder, null).orEmpty().map { it.toString() }
        present.filter { it !in names }.forEach { removeFile("$folder/$it") }
    }
}

/** The photo behind Today, in Documents/background.jpg. */
internal class IosPhoto : PhotoStore {
    private val path = documents() + "/background.jpg"
    override fun read(): ByteArray? = readFile(path)
    override fun write(bytes: ByteArray) {
        writeFile(path, bytes)
    }
    override fun delete() = removeFile(path)
}

/** Small values kept on this iPhone only, like the emoji picked lately. */
internal object Local {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    fun string(key: String): String? = defaults.stringForKey(key)
    fun setString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }

    fun long(key: String): Long? = string(key)?.toLongOrNull()
    fun setLong(key: String, value: Long?) = setString(key, value?.toString())
}
