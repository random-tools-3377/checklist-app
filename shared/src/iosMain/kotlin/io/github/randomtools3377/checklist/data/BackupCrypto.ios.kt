@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.randomtools3377.checklist.data

import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCryptorCreateWithMode
import platform.CoreCrypto.CCCryptorRefVar
import platform.CoreCrypto.CCCryptorRelease
import platform.CoreCrypto.CCCryptorUpdate
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.ccNoPadding
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCModeCTR
import platform.CoreCrypto.kCCModeOptionCTR_BE
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

// Protected backup files, with the iPhone's own CommonCrypto: PBKDF2 and AES in counter mode.
// CommonCrypto has no GCM an app may call, so GcmOverCtr puts it together from counter mode.

internal actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    if (count == 0) return bytes
    val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, count.convert(), it.addressOf(0)) }
    check(status == 0) { "No random bytes." }
    return bytes
}

internal actual fun pbkdf2Sha256(passphrase: String, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
    require(salt.isNotEmpty() && keyBytes > 0)
    val key = ByteArray(keyBytes)
    val status = salt.usePinned { saltPin ->
        key.usePinned { keyPin ->
            CCKeyDerivationPBKDF(
                kCCPBKDF2,
                passphrase,
                passphrase.encodeToByteArray().size.convert(),
                saltPin.addressOf(0).reinterpret<UByteVar>(),
                salt.size.convert(),
                kCCPRFHmacAlgSHA256,
                iterations.convert(),
                keyPin.addressOf(0).reinterpret<UByteVar>(),
                keyBytes.convert(),
            )
        }
    }
    check(status == 0) { "The key couldn't be made." }
    return key
}

/** AES in counter mode from the 16-byte big-endian [counter]. Encrypting and decrypting are the same. */
private fun aesCtr(key: ByteArray, counter: ByteArray, data: ByteArray): ByteArray {
    if (data.isEmpty()) return ByteArray(0)
    val out = ByteArray(data.size)
    memScoped {
        val cryptor = alloc<CCCryptorRefVar>()
        val created = key.usePinned { keyPin ->
            counter.usePinned { counterPin ->
                CCCryptorCreateWithMode(
                    kCCEncrypt,
                    kCCModeCTR,
                    kCCAlgorithmAES,
                    ccNoPadding,
                    counterPin.addressOf(0),
                    keyPin.addressOf(0),
                    key.size.convert(),
                    null,
                    0.convert(),
                    0,
                    kCCModeOptionCTR_BE,
                    cryptor.ptr,
                )
            }
        }
        check(created == 0) { "The cipher couldn't be set up." }
        try {
            val moved = alloc<ULongVar>()
            val status = data.usePinned { dataPin ->
                out.usePinned { outPin ->
                    CCCryptorUpdate(cryptor.value, dataPin.addressOf(0), data.size.convert(), outPin.addressOf(0), out.size.convert(), moved.ptr)
                }
            }
            check(status == 0 && moved.value.toInt() == data.size) { "The cipher stopped early." }
        } finally {
            CCCryptorRelease(cryptor.value)
        }
    }
    return out
}

internal actual fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray = GcmOverCtr.seal(key, nonce, plain, ::aesCtr)

internal actual fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray? = GcmOverCtr.open(key, nonce, sealed, ::aesCtr)
