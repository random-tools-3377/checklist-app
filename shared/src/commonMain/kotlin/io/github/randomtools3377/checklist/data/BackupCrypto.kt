package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.BackupError
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// What each device's own crypto does for a protected backup file: random bytes, PBKDF2 with
// HMAC-SHA256, and AES-256 in GCM mode. Android and Windows use javax.crypto; the iPhone uses
// CommonCrypto (see GcmOverCtr). Nothing here is written by hand except how they are put together.

/** [count] random bytes from the device's secure random source. */
internal expect fun secureRandomBytes(count: Int): ByteArray

/** PBKDF2 with HMAC-SHA256 over the passphrase as UTF-8. */
internal expect fun pbkdf2Sha256(passphrase: String, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray

/** AES-GCM with a 12-byte nonce and no extra data: the ciphertext with its 16-byte tag after it. */
internal expect fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray

/** The reverse of [aesGcmSeal], or null when the tag doesn't match: a wrong key, or a changed file. */
internal expect fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray?

/**
 * The key made from the user's passphrase, kept on this device so exports and automatic backups
 * can be protected without asking each time. The passphrase itself is never kept.
 */
class BackupKey(val iterations: Int, val salt: ByteArray, val key: ByteArray) {
    @OptIn(ExperimentalEncodingApi::class)
    fun encode(): String = "$iterations:${Base64.Default.encode(salt)}:${Base64.Default.encode(key)}"

    companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun decode(text: String?): BackupKey? {
            val parts = text?.split(":") ?: return null
            if (parts.size != 3) return null
            return runCatching {
                val key = Base64.Default.decode(parts[2])
                val salt = Base64.Default.decode(parts[1])
                BackupKey(parts[0].toInt(), salt, key).takeIf { key.size == BackupCrypto.KEY_BYTES && salt.isNotEmpty() && it.iterations > 0 }
            }.getOrNull()
        }
    }
}

/** The passphrase typed for a protected backup file doesn't open it. */
class WrongPassphraseException : Exception(BackupCrypto.WRONG_PASSPHRASE)

/**
 * Backup files protected with a passphrase. The file is plain text: a small header that says it
 * is encrypted and how, then the backup itself, encrypted with AES-256-GCM under a key made from
 * the passphrase with PBKDF2 (HMAC-SHA256). The same file opens on Android, iPhone and Windows.
 * What is inside is the usual backup file, unchanged.
 */
@OptIn(ExperimentalEncodingApi::class)
object BackupCrypto {
    /** The first words of a protected file, so the app (and anyone opening it) can tell. */
    const val MAGIC = "CHECKLIST-ENCRYPTED-BACKUP"
    const val VERSION = 1
    const val ITERATIONS = 600_000
    const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16

    /** A file asking for more work than this isn't one of ours, and isn't tried. */
    private const val MAX_ITERATIONS = 10_000_000

    /** The shortest passphrase taken. */
    const val MIN_PASSPHRASE = 8

    const val WRONG_PASSPHRASE = "That passphrase doesn't open this file. Check it and try again."
    private const val DAMAGED = "This protected backup is damaged, so it can't be opened."

    /** Spaces typed by accident around a passphrase don't count. */
    fun tidy(passphrase: String): String = passphrase.trim()

    /** Why [passphrase] can't be used, or null when it can. */
    fun problem(passphrase: String, again: String): String? = when {
        tidy(passphrase).length < MIN_PASSPHRASE -> "Use at least $MIN_PASSPHRASE characters."
        tidy(passphrase) != tidy(again) -> "The two don't match yet."
        else -> null
    }

    /** A new key from [passphrase], with its own random salt. Slow on purpose: call it off the main thread. */
    fun newKey(passphrase: String, iterations: Int = ITERATIONS): BackupKey {
        val salt = secureRandomBytes(SALT_BYTES)
        return BackupKey(iterations, salt, pbkdf2Sha256(tidy(passphrase), salt, iterations, KEY_BYTES))
    }

    fun isEncrypted(text: String): Boolean = text.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith(MAGIC)

    /** [text] as a protected file under [key]. Every file gets its own random nonce. */
    fun seal(text: String, key: BackupKey): String = seal(text, key, secureRandomBytes(NONCE_BYTES))

    internal fun seal(text: String, key: BackupKey, nonce: ByteArray): String {
        val sealed = aesGcmSeal(key.key, nonce, text.encodeToByteArray())
        return buildString {
            append("$MAGIC $VERSION\n")
            append("This is a Checklist backup protected with a passphrase.\n")
            append("To open it: in the app, Settings, Backup, \"Import a backup\", then type the passphrase.\n")
            append("cipher: AES-256-GCM\n")
            append("kdf: PBKDF2-HMAC-SHA256\n")
            append("iterations: ${key.iterations}\n")
            append("salt: ${Base64.Default.encode(key.salt)}\n")
            append("nonce: ${Base64.Default.encode(nonce)}\n")
            append("\n")
            // In lines, so the file opens in any text editor.
            Base64.Default.encode(sealed).chunked(76).forEach { append(it).append('\n') }
        }
    }

    /**
     * The backup text inside the protected file [text], opened with [passphrase]. Slow on
     * purpose: call it off the main thread.
     * @throws WrongPassphraseException when the passphrase doesn't open it
     * @throws BackupError when the file is damaged, or from a newer version of the app
     */
    fun open(text: String, passphrase: String): String {
        val lines = text.trimStart('﻿', ' ', '\n', '\r', '\t').lines().map { it.trim() }
        val first = lines.firstOrNull().orEmpty()
        if (!first.startsWith(MAGIC)) throw BackupError("This file isn't a protected Checklist backup.")
        val version = first.removePrefix(MAGIC).trim().toIntOrNull() ?: throw BackupError(DAMAGED)
        if (version > VERSION) throw BackupError("This backup was made by a newer version of the app. Update the app first.")
        val gap = lines.indexOf("")
        if (gap < 0) throw BackupError(DAMAGED)
        val header = lines.subList(1, gap).mapNotNull { line ->
            val at = line.indexOf(": ")
            if (at > 0) line.substring(0, at) to line.substring(at + 2).trim() else null
        }.toMap()
        val iterations = header["iterations"]?.toIntOrNull()?.takeIf { it in 1..MAX_ITERATIONS } ?: throw BackupError(DAMAGED)
        val salt = bytes(header["salt"])?.takeIf { it.size in 8..64 } ?: throw BackupError(DAMAGED)
        val nonce = bytes(header["nonce"])?.takeIf { it.size == NONCE_BYTES } ?: throw BackupError(DAMAGED)
        val sealed = bytes(lines.drop(gap + 1).joinToString(""))?.takeIf { it.size >= TAG_BYTES } ?: throw BackupError(DAMAGED)
        // Nothing typed opens nothing, and not every device's crypto takes an empty passphrase.
        if (tidy(passphrase).isEmpty()) throw WrongPassphraseException()
        val key = pbkdf2Sha256(tidy(passphrase), salt, iterations, KEY_BYTES)
        val plain = aesGcmOpen(key, nonce, sealed) ?: throw WrongPassphraseException()
        return plain.decodeToString()
    }

    /**
     * Whether this device opens what it seals: a small file under a quickly made key, sealed and
     * opened again with [passphrase]. Checked when the passphrase is turned on, so a device that
     * couldn't says so then rather than leaving backups no one can open.
     */
    fun works(passphrase: String): Boolean = runCatching {
        open(seal("checklist", newKey(passphrase, iterations = 1)), passphrase) == "checklist"
    }.getOrDefault(false)

    private fun bytes(base64: String?): ByteArray? = base64?.let { runCatching { Base64.Default.decode(it) }.getOrNull() }
}

/**
 * AES-GCM put together from AES in counter mode, for a device whose own crypto has counter mode
 * but no GCM to call (the iPhone's CommonCrypto). [ctr] encrypts data with AES in counter mode
 * from a 16-byte big-endian starting counter. Only the tag's arithmetic (GHASH) is done here; it
 * is checked against javax.crypto's AES-GCM in the tests.
 */
internal object GcmOverCtr {
    private const val TAG = 16

    fun seal(key: ByteArray, nonce: ByteArray, plain: ByteArray, ctr: (key: ByteArray, counter: ByteArray, data: ByteArray) -> ByteArray): ByteArray {
        require(nonce.size == 12) { "The nonce is 12 bytes." }
        val cipher = ctr(key, counter(nonce, 2), plain)
        return cipher + tag(key, nonce, cipher, ctr)
    }

    fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray, ctr: (key: ByteArray, counter: ByteArray, data: ByteArray) -> ByteArray): ByteArray? {
        require(nonce.size == 12) { "The nonce is 12 bytes." }
        if (sealed.size < TAG) return null
        val cipher = sealed.copyOfRange(0, sealed.size - TAG)
        val expected = tag(key, nonce, cipher, ctr)
        // Compared without stopping at the first difference.
        var difference = 0
        for (i in 0 until TAG) difference = difference or (expected[i].toInt() xor sealed[cipher.size + i].toInt())
        if (difference != 0) return null
        return ctr(key, counter(nonce, 2), cipher)
    }

    private fun counter(nonce: ByteArray, count: Int): ByteArray = nonce + byteArrayOf(0, 0, 0, count.toByte())

    private fun tag(key: ByteArray, nonce: ByteArray, cipher: ByteArray, ctr: (ByteArray, ByteArray, ByteArray) -> ByteArray): ByteArray {
        // Counter mode over zeros gives the block cipher's output for that counter.
        val h = ctr(key, ByteArray(16), ByteArray(16))
        val mask = ctr(key, counter(nonce, 1), ByteArray(16))
        val hash = ghash(h, cipher)
        return ByteArray(TAG) { (hash[it].toInt() xor mask[it].toInt()).toByte() }
    }

    /** GHASH of [cipher] with no extra data, under the hash key [h]. */
    private fun ghash(h: ByteArray, cipher: ByteArray): ByteArray {
        val hHigh = long(h, 0)
        val hLow = long(h, 8)
        var yHigh = 0L
        var yLow = 0L
        fun absorb(blockHigh: Long, blockLow: Long) {
            val xHigh = yHigh xor blockHigh
            val xLow = yLow xor blockLow
            // Multiplies x by h in GF(2^128), a bit at a time.
            var zHigh = 0L
            var zLow = 0L
            var vHigh = hHigh
            var vLow = hLow
            for (i in 0 until 128) {
                val bit = if (i < 64) (xHigh ushr (63 - i)) and 1L else (xLow ushr (127 - i)) and 1L
                if (bit != 0L) {
                    zHigh = zHigh xor vHigh
                    zLow = zLow xor vLow
                }
                val carry = vLow and 1L
                vLow = (vLow ushr 1) or (vHigh shl 63)
                vHigh = vHigh ushr 1
                if (carry != 0L) vHigh = vHigh xor (0xE1L shl 56)
            }
            yHigh = zHigh
            yLow = zLow
        }
        val whole = cipher.size / 16
        for (block in 0 until whole) absorb(long(cipher, block * 16), long(cipher, block * 16 + 8))
        if (cipher.size % 16 != 0) {
            val last = cipher.copyOfRange(whole * 16, cipher.size).copyOf(16)
            absorb(long(last, 0), long(last, 8))
        }
        // The lengths in bits: no extra data, then the ciphertext.
        absorb(0L, cipher.size.toLong() * 8)
        val out = ByteArray(16)
        for (i in 0 until 8) {
            out[i] = (yHigh ushr (56 - 8 * i)).toByte()
            out[8 + i] = (yLow ushr (56 - 8 * i)).toByte()
        }
        return out
    }

    private fun long(bytes: ByteArray, from: Int): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (bytes[from + i].toLong() and 0xFF)
        return value
    }
}
