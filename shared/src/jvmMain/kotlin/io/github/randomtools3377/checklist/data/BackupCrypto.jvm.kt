package io.github.randomtools3377.checklist.data

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Protected backup files, with javax.crypto.

internal actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

internal actual fun pbkdf2Sha256(passphrase: String, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, keyBytes * 8)
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}

internal actual fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    return cipher.doFinal(plain)
}

internal actual fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray? {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    return try {
        cipher.doFinal(sealed)
    } catch (e: AEADBadTagException) {
        null
    }
}
