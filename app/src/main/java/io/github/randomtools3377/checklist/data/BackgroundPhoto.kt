package io.github.randomtools3377.checklist.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * The user's own photo behind Today, copied into the app's own storage and made smaller. The
 * Photo Picker only lends the photo for a moment, so a copy is kept; it goes in the backup file.
 */
class BackgroundPhoto(filesDir: File) : PhotoStore {
    val file = File(filesDir, "background.jpg")

    fun exists(): Boolean = file.isFile

    override fun read(): ByteArray? = file.takeIf { it.isFile }?.readBytes()

    override fun write(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override fun delete() {
        file.delete()
    }

    /** Copies a picked photo in, at most [MAX_SIDE] pixels on its longer side. False if it isn't a photo. */
    fun add(context: Context, uri: Uri): Boolean {
        val bitmap = try {
            decode(context, uri)
        } catch (e: Exception) {
            null
        } ?: return false
        val tmp = File(file.parentFile, "background.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        return tmp.renameTo(file)
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Turns the photo the right way up, as the camera recorded it.
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longer = maxOf(info.size.width, info.size.height)
                if (longer > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longer
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        return if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    }

    companion object {
        const val MAX_SIDE = 1600
        private const val QUALITY = 85
    }
}
