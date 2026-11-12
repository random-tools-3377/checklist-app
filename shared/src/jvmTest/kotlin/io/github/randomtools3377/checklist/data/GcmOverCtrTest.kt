package io.github.randomtools3377.checklist.data

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * The iPhone has no AES-GCM an app may call, so its files are made from AES in counter mode and
 * the tag's arithmetic in shared code (GcmOverCtr). Here that is checked against javax.crypto's
 * own AES-GCM, byte for byte, so a file made on one kind of device opens on another.
 */
class GcmOverCtrTest {
    private fun ctr(key: ByteArray, counter: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(counter))
        return cipher.doFinal(data)
    }

    private fun bytes(count: Int, seed: Int) = ByteArray(count) { (it * 31 + seed).toByte() }

    @Test
    fun itMakesTheSameBytesAsTheComputersOwnGcm() {
        val key = bytes(32, 5)
        val nonce = bytes(12, 9)
        for (size in listOf(0, 1, 15, 16, 17, 31, 32, 33, 255, 256, 1000, 70_000)) {
            val plain = bytes(size, size)
            val sealed = GcmOverCtr.seal(key, nonce, plain, ::ctr)
            assertContentEquals(aesGcmSeal(key, nonce, plain), sealed, "size $size")
            assertContentEquals(plain, GcmOverCtr.open(key, nonce, sealed, ::ctr), "size $size")
            assertContentEquals(plain, aesGcmOpen(key, nonce, sealed), "size $size")
        }
    }

    @Test
    fun aChangedByteOrAnotherKeyOpensNothing() {
        val key = bytes(32, 5)
        val nonce = bytes(12, 9)
        val sealed = GcmOverCtr.seal(key, nonce, bytes(100, 1), ::ctr)
        for (at in listOf(0, 50, 99, 100, sealed.size - 1)) {
            val changed = sealed.copyOf().also { it[at] = (it[at].toInt() xor 1).toByte() }
            assertNull(GcmOverCtr.open(key, nonce, changed, ::ctr), "byte $at")
            assertNull(aesGcmOpen(key, nonce, changed), "byte $at")
        }
        assertNull(GcmOverCtr.open(bytes(32, 6), nonce, sealed, ::ctr))
        assertNull(GcmOverCtr.open(key, nonce, ByteArray(3), ::ctr))
    }

    /** PBKDF2 as its definition gives it, over the passphrase as UTF-8, to check the device's own. */
    @Test
    fun theKeyComesFromThePassphraseAsUtf8() {
        val passphrase = "pässwörd ✓ twelve"
        val salt = bytes(16, 3)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(passphrase.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val expected = u.copyOf()
        repeat(49) {
            u = mac.doFinal(u)
            for (i in expected.indices) expected[i] = (expected[i].toInt() xor u[i].toInt()).toByte()
        }
        assertContentEquals(expected, pbkdf2Sha256(passphrase, salt, 50, 32))
    }
}
